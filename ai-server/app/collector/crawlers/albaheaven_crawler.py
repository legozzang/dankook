# albaHeaven_crawler.py
import re
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin
from typing import Optional
from app.collector.base.crawler import BaseCrawler
from app.collector.base.collector import CrawlJob, SourceType


class AlbaHeavenCrawler(BaseCrawler):
    """
    알바천국 채용공고 크롤러.
    목록 페이지 HTML에서 adid를 수집하고,
    상세 페이지 HTML + iframe 요청으로 본문을 수집한다.
    """

    SOURCE_TYPE = SourceType.ALBAHEAVEN
    NAME        = SOURCE_TYPE.label

    BASE_URL      = "https://www.alba.co.kr"
    BASE_LIST_URL = "https://www.alba.co.kr/job/main"
    DETAIL_URL    = "https://www.alba.co.kr/job/Detail?adid="

    LIST_PARAMS = {
        "page":          1,
        "pagesize":      50,
        "hidsort":       "FREEORDER",
        "hidsortfilter": "Y",
    }

    PAY_TYPE_MAP = {
        "hour":  "시급",
        "day":   "일급",
        "week":  "주급",
        "month": "월급",
        "year":  "연봉",
        "per":   "건별",
    }

    # ── 목록 페이지 파싱 ──────────────────────────────────────────────────────

    def _get_page_ids(self, page: int) -> list:
        """
        목록 페이지에서 adid 목록을 추출하여 반환한다.
        요청 실패 시 빈 리스트를 반환한다.
        """
        params = {**self.LIST_PARAMS, "page": page}
        try:
            response = requests.get(
                self.BASE_LIST_URL, params=params,
                headers=self.HEADERS, timeout=10,
            )
            response.raise_for_status()
        except Exception as e:
            print(f"[WARN] 목록 페이지 요청 실패 - page={page} / 원인: {e}")
            return []

        soup  = BeautifulSoup(response.text, "html.parser")
        adids = []
        for tag in soup.select("[data-imid]"):
            try:
                adids.append(int(tag["data-imid"]))
            except (ValueError, KeyError):
                pass

        return adids

    # ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    def _build_item_url(self, item_id: int) -> str:
        """adid로 상세 페이지 URL을 생성한다."""
        return f"{self.DETAIL_URL}{item_id}"

    def _parse_id_from_url(self, url: str):
        """URL의 adid 쿼리 파라미터에서 id를 추출한다."""
        m = re.search(r"adid=(\d+)", url)
        return int(m.group(1)) if m else None

    def parse(self, html: str) -> Optional[CrawlJob]:
        """
        HTML을 파싱하여 CrawlJob으로 변환한다.
        네트워크 요청 없이 전달받은 html만 사용한다.
        필수 항목(제목, 회사명) 파싱 실패 시 None을 반환한다.
        """
        soup = BeautifulSoup(html, "html.parser")

        # ── 필수 항목 ──────────────────────────────────────────
        try:
            title   = soup.select_one("h2.detail-primary__title").text.strip()
            company = soup.select_one("div.detail-primary__company").text.strip()
            region  = soup.select_one("p.workplace-addr__text").text.strip()
        except AttributeError:
            return None

        # ── 직무 (복수 가능) ────────────────────────────────────
        job_type = ", ".join(
            t.text.strip() for t in soup.select("a.jobKind3")
        )

        # ── 급여 ───────────────────────────────────────────────
        pay_type   = ""
        pay_amount = 0

        pay_type_tag = soup.select_one("span[class*='detail-pay__type--']")
        if pay_type_tag:
            for cls in pay_type_tag.get("class", []):
                if cls.startswith("detail-pay__type--"):
                    key      = cls[len("detail-pay__type--"):]
                    pay_type = self.PAY_TYPE_MAP.get(key, "")
                    break

        pay_amount_tag = soup.select_one("div.detail-pay strong")
        if pay_amount_tag:
            raw = pay_amount_tag.text.strip().replace(",", "")
            pay_amount = int(raw) if raw.isdigit() else 0

        # ── dt/dd 기반 항목 파싱 헬퍼 ──────────────────────────
        def get_dd(label: str) -> str:
            for dt in soup.select("dl.detail-def__item dt"):
                if dt.text.strip() == label:
                    dd = dt.find_next_sibling("dd")
                    return dd.get_text(" ", strip=True) if dd else ""
            return ""

        # ── 근무 조건 ───────────────────────────────────────────
        work_period = get_dd("근무기간")
        work_time   = get_dd("근무시간")
        work_days   = get_dd("근무요일")
        employ_type = get_dd("고용형태")
        welfare     = get_dd("복리후생")

        # ── 모집 조건 ───────────────────────────────────────────
        education = get_dd("학력")
        preferred = get_dd("우대조건")
        headcount = get_dd("모집인원")

        deadline_tag = soup.select_one("dl.detail-def__item dd .detail-period")
        deadline = deadline_tag.get_text(" ", strip=True) if deadline_tag else get_dd("모집마감")

        return CrawlJob(
            company=company,
            title=title,
            content="",
            region=region,
            job_type=job_type,
            external_url="",
            source_type=SourceType.ALBAHEAVEN,
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
        공고 본문은 iframe으로 별도 로드된다.
        전달받은 html에서 iframe src를 추출한 뒤,
        fetch_with_retry로 요청하여 텍스트를 반환한다.
        """
        soup   = BeautifulSoup(html, "html.parser")
        iframe = soup.select_one("iframe#DetailContentIframe")

        if iframe is None or not iframe.get("src"):
            return ""

        iframe_url  = urljoin(self.BASE_URL, iframe["src"])
        iframe_html = self.fetch_with_retry(iframe_url)

        if iframe_html is None:
            return ""

        return BeautifulSoup(iframe_html, "html.parser").get_text(strip=True)
