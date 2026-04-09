# albaHeaven_crawler.py
import csv
import re
import os
import requests
from bs4 import BeautifulSoup
from datetime import datetime
from urllib.parse import urljoin
from typing import Optional
from app.collector.base.crawler import BaseCrawler, CrawlJob, SourceType


class AlbaHeavenCrawler(BaseCrawler):
    """
    알바천국 채용공고 크롤러.
    목록 페이지에서 최신 adid를 수집하고,
    Orchestrator가 넘긴 start_id부터 순차 크롤링한다.

    크롤링한 HTML은 data/raw/albaheaven/ 에 저장하고,
    다운로드 로그는 data/raw/albaheaven/download_log.csv 에 기록한다.
    """

    BASE_URL   = "https://www.alba.co.kr"
    DETAIL_URL = "https://www.alba.co.kr/job/Detail?adid="

    BASE_LIST_URL = "https://www.alba.co.kr/job/main"
    LIST_PARAMS   = {
        "page":          1,
        "pagesize":      50,
        "hidsort":       "FREEORDER",
        "hidsortfilter": "Y",
    }

    HEADERS = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                      "AppleWebKit/537.36 (KHTML, like Gecko) "
                      "Chrome/120.0.0.0 Safari/537.36"
    }

    PAY_TYPE_MAP = {
        "hour":  "시급",
        "day":   "일급",
        "week":  "주급",
        "month": "월급",
        "year":  "연봉",
        "per":   "건별",
    }

    # HTML 저장 루트 및 CSV 로그 경로
    BASE_DIR = os.path.dirname(  # ai-server/
        os.path.dirname(          # ai-server/app/
            os.path.dirname(      # ai-server/app/crawler/
                os.path.abspath(__file__)
            )
        )
    )

    RAW_DIR  = os.path.join(BASE_DIR, "data", "raw", "albaheaven")
    LOG_PATH = os.path.join(RAW_DIR, "download_log.csv")
    LOG_HEADER = ["adid", "file_path", "posted_at", "downloaded_at"]

    def __init__(self, crawl_max_retries: int = 3):
        super().__init__(crawl_max_retries)
        self._init_storage()

    def _init_storage(self):
        """저장 디렉토리와 CSV 로그 파일을 초기화한다."""
        os.makedirs(self.RAW_DIR, exist_ok=True)

        # CSV가 없을 때만 헤더 작성
        if not os.path.exists(self.LOG_PATH):
            with open(self.LOG_PATH, "w", newline="", encoding="utf-8") as f:
                csv.writer(f).writerow(self.LOG_HEADER)

    def _save_html(self, adid: int, html: str, posted_at: str):
        """
        HTML을 파일로 저장하고 CSV 로그에 기록한다.
        저장 실패 시 경고 로그만 출력하고 계속 진행한다.
        """
        file_name = f"ALBAHEAVEN_{adid}.html"
        file_path = os.path.join(self.RAW_DIR, file_name)

        try:
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(html)

            with open(self.LOG_PATH, "a", newline="", encoding="utf-8") as f:
                csv.writer(f).writerow([
                    adid,
                    file_path,
                    posted_at,
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                ])

        except Exception as e:
            print(f"[WARN] HTML 저장 실패 - adid={adid} / 원인: {e}")

    def get_latest_id(self) -> int:
        """
        목록 페이지 첫 번째 공고의 adid를 최신 id로 반환한다.
        정렬 기준: FREEORDER (최신순)
        """
        response = requests.get(
            self.BASE_LIST_URL,
            params=self.LIST_PARAMS,
            headers=self.HEADERS,
            timeout=10,
        )
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")

        first_link = soup.select_one("a.info[href*='adid=']")
        if first_link is None:
            raise Exception("[ERROR] 목록 페이지에서 최신 adid를 찾을 수 없음")

        match = re.search(r"adid=(\d+)", first_link["href"])
        if match is None:
            raise Exception("[ERROR] adid 파싱 실패")

        return int(match.group(1))

    def collect(self, **kwargs):
        """
        start_id ~ end_id 범위의 adid를 순회하며 CrawlJob을 yield한다.
        삭제된 공고 등 유효하지 않은 페이지는 스킵한다.
        """
        start_id = kwargs["start_id"]
        end_id   = self.get_latest_id()

        for adid in range(start_id, end_id + 1):
            url  = f"{self.DETAIL_URL}{adid}"
            html = self.fetch_with_retry(url)

            if html is None:
                continue

            # 파싱 전에 HTML 저장 (파싱 실패한 경우도 남기기 위해)
            posted_at = self._parse_posted_at(html)
            self._save_html(adid, html, posted_at)

            job = self.parse(html)

            if job is None:
                print(f"[SKIP] 유효하지 않은 공고 - adid={adid}")
                continue

            job.content      = self._fetch_iframe_content(html)
            job.external_url = url
            yield job

    def _fetch(self, url: str) -> str:
        """HTTP GET 요청으로 HTML을 가져온다."""
        response = requests.get(url, headers=self.HEADERS, timeout=10)
        response.raise_for_status()
        return response.text

    def _parse_posted_at(self, html: str) -> str:
        """HTML에서 공고 작성 시각을 추출한다. 없으면 빈 문자열을 반환한다."""
        soup = BeautifulSoup(html, "html.parser")
        tag  = soup.select_one("div.detail-date")
        return tag.text.strip() if tag else ""

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

    def _fetch_iframe_content(self, html: str) -> str:
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

        iframe_soup = BeautifulSoup(iframe_html, "html.parser")
        return iframe_soup.get_text(strip=True)