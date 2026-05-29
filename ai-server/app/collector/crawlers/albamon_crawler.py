# albamon_crawler.py
import json
import re
import requests
from bs4 import BeautifulSoup
from typing import Optional
from app.collector.base.crawler import BaseCrawler
from app.collector.base.collector import CrawlJob, SourceType


class AlbamonCrawler(BaseCrawler):
    """
    알바몬 채용공고 크롤러.
    목록 페이지 __NEXT_DATA__ JSON에서 recruitNo를 수집하고,
    상세 페이지 __NEXT_DATA__ JSON에서 전체 필드 및 본문을 수집한다.
    """

    SOURCE_TYPE = SourceType.ALBAMON
    NAME        = SOURCE_TYPE.label

    LIST_URL   = "https://www.albamon.com/jobs/total"
    DETAIL_URL = "https://www.albamon.com/jobs/detail/{recruit_no}"

    LIST_PARAMS = {
        "size":     50,
        "page":     1,
        "sortType": "POSTED_DATE",
    }

    PAY_TYPE_MAP = {
        "HOURLY":  "시급",
        "DAILY":   "일급",
        "WEEKLY":  "주급",
        "MONTHLY": "월급",
        "YEARLY":  "연봉",
        "PER":     "건별",
    }

    # ── 목록 페이지 파싱 ──────────────────────────────────────────────────────

    def _get_page_ids(self, page: int) -> list:
        """
        목록 페이지 __NEXT_DATA__ JSON에서 recruitNo 목록을 추출하여 반환한다.
        요청 실패 또는 파싱 실패 시 빈 리스트를 반환한다.
        """
        params = {**self.LIST_PARAMS, "page": page}
        try:
            html = requests.get(self.LIST_URL, params=params, headers=self.HEADERS, timeout=10).text
            data = self._extract_next_data(html)
        except Exception as e:
            print(f"[WARN] 목록 페이지 요청 실패 - page={page} / 원인: {e}")
            return []

        if data is None:
            return []

        try:
            queries = data["props"]["pageProps"]["dehydratedState"]["queries"]
            for q in queries:
                collection = (
                    q.get("state", {})
                     .get("data", {})
                     .get("base", {})
                     .get("normal", {})
                     .get("collection", [])
                )
                if collection:
                    return [int(item["recruitNo"]) for item in collection if "recruitNo" in item]
        except (KeyError, TypeError, ValueError):
            pass

        return []

    # ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    def _build_item_url(self, item_id: int) -> str:
        """recruitNo로 상세 페이지 URL을 생성한다."""
        return self.DETAIL_URL.format(recruit_no=item_id)

    def _parse_id_from_url(self, url: str):
        """URL의 경로 마지막 숫자 세그먼트에서 id를 추출한다."""
        m = re.search(r"/detail/(\d+)", url)
        return int(m.group(1)) if m else None

    @staticmethod
    def _extract_next_data(html: str) -> Optional[dict]:
        """
        HTML에서 <script id="__NEXT_DATA__"> 태그의 JSON을 추출한다.
        태그가 없거나 파싱에 실패하면 None을 반환한다.
        """
        soup = BeautifulSoup(html, "html.parser")
        tag  = soup.find("script", {"id": "__NEXT_DATA__"})
        if not tag:
            return None
        try:
            return json.loads(tag.string)
        except (json.JSONDecodeError, TypeError):
            return None

    def parse(self, html: str) -> Optional[CrawlJob]:
        """
        상세 페이지 HTML의 __NEXT_DATA__ JSON을 파싱하여 CrawlJob으로 변환한다.
        네트워크 요청 없이 전달받은 html만 사용한다.
        필수 항목(제목, 회사명) 파싱 실패 시 None을 반환한다.
        """
        data = self._extract_next_data(html)
        if data is None:
            return None

        try:
            view = data["props"]["pageProps"]["data"]["viewData"]
        except (KeyError, TypeError):
            return None

        # ── 필수 항목 ──────────────────────────────────────────
        title   = view.get("recruitTitle", "").strip()
        company = view.get("recruitCompanyName", "").strip()
        region  = view.get("workingAddress", "").strip()

        if not title or not company:
            return None

        # ── 직무 ───────────────────────────────────────────────
        job_type = view.get("jobField", "") or ", ".join(
            p.get("description", "") for p in view.get("part", [])
        )

        # ── 급여 ───────────────────────────────────────────────
        salary_key = view.get("salaryType", {}).get("key", "")
        pay_type   = self.PAY_TYPE_MAP.get(salary_key, view.get("salaryDescription", ""))
        pay_amount = int(view.get("salary", 0) or 0)

        # ── 근무 조건 ───────────────────────────────────────────
        work_period = view.get("workPeriod", {}).get("description", "")
        work_time   = view.get("workTimeDetail") or ""
        if view.get("workTimeOption"):
            work_time += f" ({view['workTimeOption']})"
        work_days   = view.get("workDays", {}).get("description", "")
        employ_type = ", ".join(
            e.get("description", "") for e in (view.get("employmentType") or [])
        )
        welfare = ", ".join(
            w.get("description", "") for w in (view.get("welfareBenefits") or [])
        )

        # ── 모집 조건 ───────────────────────────────────────────
        education = ", ".join(
            e.get("description", "") for e in view.get("educationLevels", [])
        )
        preferred = view.get("preferences", "")
        deadline  = view.get("deadlineDate", "") or view.get("closingDateTime", "")
        headcount = view.get("numberOfApplicants", "")

        return CrawlJob(
            company=company,
            title=title,
            content="",
            region=region,
            job_type=job_type,
            external_url="",
            source_type=SourceType.ALBAMON,
            pay_type=pay_type,
            pay_amount=pay_amount,
            work_period=work_period,
            work_time=work_time,
            work_days=work_days,
            employ_type=employ_type,
            welfare=welfare,
            education=education,
            preferred=preferred,
            deadline=deadline,
            headcount=headcount,
        )

    def _fetch_content(self, html: str) -> str:
        """
        상세 페이지 __NEXT_DATA__ JSON의 content 필드를 추출하여 텍스트로 반환한다.
        content는 HTML이므로 BeautifulSoup으로 텍스트만 추출한다.
        """
        data = self._extract_next_data(html)
        if data is None:
            return ""

        try:
            content_html = data["props"]["pageProps"]["data"]["viewData"].get("content", "")
        except (KeyError, TypeError):
            return ""

        if not content_html:
            return ""

        return BeautifulSoup(content_html, "html.parser").get_text(separator="\n", strip=True)
