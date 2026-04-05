# albaHeaven_crawler.py
import re
import requests
from bs4 import BeautifulSoup
from typing import Optional
from base_crawler import BaseCrawler, CrawlJob, SourceType


class AlbaHeavenCrawler(BaseCrawler):
    """
    알바천국 채용공고 크롤러.
    목록 페이지에서 최신 adid를 수집하고,
    Orchestrator가 넘긴 start_id부터 순차 크롤링한다.
    """

    BASE_URL = "https://www.alba.co.kr/job/Detail?adid="
    LIST_URL = (
        "https://www.alba.co.kr/job/main"
        "?page=1&pagesize=50&hidsort=FREEORDER&hidsortfilter=Y"
    )
    HEADERS = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                      "AppleWebKit/537.36 (KHTML, like Gecko) "
                      "Chrome/120.0.0.0 Safari/537.36"
    }

    def get_latest_id(self) -> int:
        """
        목록 페이지 첫 번째 공고의 adid를 최신 id로 반환한다.
        정렬 기준: FREEORDER (최신순)
        """
        response = requests.get(self.LIST_URL, headers=self.HEADERS, timeout=10)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")

        # 목록 공고 링크: class="blankView info", href="jobDetail?adid=숫자&..."
        first_link = soup.select_one("a.info[href*='adid=']")
        if first_link is None:
            raise Exception("[ERROR] 목록 페이지에서 최신 adid를 찾을 수 없음")

        match = re.search(r"adid=(\d+)", first_link["href"])
        if match is None:
            raise Exception("[ERROR] adid 파싱 실패")

        return int(match.group(1))

    def crawl(self, start_id: int, end_id: int):
        """
        start_id ~ end_id 범위의 adid를 순회하며 CrawlJob을 yield한다.
        삭제된 공고 등 유효하지 않은 페이지는 스킵한다.
        """
        for adid in range(start_id, end_id + 1):
            url = f"{self.BASE_URL}{adid}"
            html = self.fetch_with_retry(url)

            if html is None:
                continue

            job = self.parse(html)

            if job is None:
                print(f"[SKIP] 유효하지 않은 공고 - adid={adid}")
                continue

            job.external_url = url
            yield job

    def _fetch(self, url: str) -> str:
        """HTTP GET 요청으로 HTML을 가져온다."""
        response = requests.get(url, headers=self.HEADERS, timeout=10)
        response.raise_for_status()
        return response.text

    def parse(self, html: str) -> Optional[CrawlJob]:
        """
        HTML을 파싱하여 CrawlJob으로 변환한다.
        필수 항목(제목, 회사명) 파싱 실패 시 None을 반환한다.
        """
        soup = BeautifulSoup(html, "html.parser")

        try:
            title   = soup.select_one("h2.detail-primarytitle").text.strip()
            company = soup.select_one("div.company-infoname").text.strip()
            region  = soup.select_one("p.workplace-addrtext").text.strip()

            job_type_tag = soup.select_one("a.jobKind3")
            job_type = job_type_tag.text.strip() if job_type_tag else ""

            content = self._fetch_content(soup)

        except AttributeError:
            return None

        return CrawlJob(
            company=company,
            title=title,
            content=content,
            region=region,
            job_type=job_type,
            external_url="",  # crawl()에서 주입
            source_type=SourceType.ALBAHEAVEN,
        )

    def _fetch_content(self, soup: BeautifulSoup) -> str:
        """
        공고 내용은 iframe으로 별도 로드된다.
        iframe src에서 URL을 추출해 별도 요청으로 가져온다.
        """
        iframe = soup.select_one("iframe#iframeDetail")
        if iframe is None or not iframe.get("src"):
            return ""
        try:
            iframe_url = "https://www.alba.co.kr/" + iframe["src"]
            response = requests.get(iframe_url, headers=self.HEADERS, timeout=10)
            iframe_soup = BeautifulSoup(response.text, "html.parser")
            return iframe_soup.get_text(strip=True)
        except Exception:
            return ""