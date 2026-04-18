# albaHeaven_crawler.py
import csv
import re
import os
import time
import random
import requests
from bs4 import BeautifulSoup
from datetime import datetime
from urllib.parse import urljoin
from typing import Optional
from app.collector.base.crawler import BaseCrawler, CrawlJob, SourceType


class AlbaHeavenCrawler(BaseCrawler):
    """
    알바천국 채용공고 크롤러.
    목록 페이지를 한 페이지씩 순회하며 공고를 수집한다.

    resume 전략:
      - last_crawled_adid를 기준으로 이진탐색해 마지막으로 멈춘 위치를 찾는다.
      - 해당 adid가 삭제됐을 수 있으므로 "last_crawled_adid보다 큰 가장 작은 adid"를
        실제 재시작 지점으로 사용한다.
      - Phase 1: 새 공고 수집 (resume 페이지 이전의 모든 페이지)
      - Phase 2: resume 위치부터 이어서 크롤링

    크롤링한 HTML은 raw_dir 하위에 저장하고,
    다운로드 로그는 raw_dir/download_log.csv에 기록한다.
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

    # 기본 저장 경로 (raw_dir 미지정 시 사용)
    _DEFAULT_RAW_DIR = os.path.join(
        os.path.dirname(          # ai-server/app/
            os.path.dirname(      # ai-server/app/collector/
                os.path.dirname(  # ai-server/app/collector/crawlers/
                    os.path.abspath(__file__)
                )
            )
        ),
        "data", "raw", "albaheaven",
    )

    LOG_HEADER = ["adid", "file_path", "posted_at", "downloaded_at"]

    def __init__(self, crawl_max_retries: int = 3, crawl_delay: float = 1.5, crawl_jitter: float = 1.0, raw_dir: str = None):
        super().__init__(crawl_max_retries)
        self.crawl_delay  = crawl_delay   # 기본 대기 시간 (초)
        self.crawl_jitter = crawl_jitter  # 랜덤 추가 대기 상한 (초), 실제 추가량 = random(0, jitter)
        self.raw_dir  = raw_dir if raw_dir is not None else self._DEFAULT_RAW_DIR
        self.log_path = os.path.join(self.raw_dir, "download_log.csv")
        self._init_storage()

    # ── 저장소 초기화 ─────────────────────────────────────────────────────────

    def _init_storage(self):
        """저장 디렉토리와 CSV 로그 파일을 초기화한다."""
        os.makedirs(self.raw_dir, exist_ok=True)
        if not os.path.exists(self.log_path):
            with open(self.log_path, "w", newline="", encoding="utf-8") as f:
                csv.writer(f).writerow(self.LOG_HEADER)

    def _save_html(self, adid: int, html: str, posted_at: str):
        """
        HTML을 파일로 저장하고 CSV 로그에 기록한다.
        저장 실패 시 경고 로그만 출력하고 계속 진행한다.
        """
        file_name = f"ALBAHEAVEN_{adid}.html"
        file_path = os.path.join(self.raw_dir, file_name)

        try:
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(html)

            with open(self.log_path, "a", newline="", encoding="utf-8") as f:
                csv.writer(f).writerow([
                    adid,
                    file_path,
                    posted_at,
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                ])

        except Exception as e:
            print(f"[WARN] HTML 저장 실패 - adid={adid} / 원인: {e}")

    # ── 목록 페이지 파싱 ──────────────────────────────────────────────────────

    def _get_page_adids(self, page: int) -> list:
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

    def _get_total_pages(self) -> int:
        """
        목록의 총 페이지 수를 반환한다.
        페이지네이션 링크에서 최대 page 파라미터를 읽고,
        파싱에 실패하면 지수 탐색으로 추정한다.
        """
        params = {**self.LIST_PARAMS, "page": 1}
        try:
            response = requests.get(
                self.BASE_LIST_URL, params=params,
                headers=self.HEADERS, timeout=10,
            )
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")

            last_page = 1
            for a_tag in soup.select("a[href*='page=']"):
                m = re.search(r"[?&]page=(\d+)", a_tag.get("href", ""))
                if m:
                    last_page = max(last_page, int(m.group(1)))

            if last_page > 1:
                return last_page

        except Exception as e:
            print(f"[WARN] 총 페이지 파싱 실패 - 지수 탐색으로 전환 / 원인: {e}")

        # 파싱 실패 시 지수 탐색으로 상한 추정
        upper = 1
        while self._get_page_adids(upper):
            upper *= 2

        # 이진탐색으로 실제 마지막 페이지 확정
        lo, hi = upper // 2, upper
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self._get_page_adids(mid):
                lo = mid
            else:
                hi = mid - 1

        return lo

    # ── 이진탐색 resume ───────────────────────────────────────────────────────

    def _find_start_page(self, last_adid: int, hi: int) -> int:
        """
        이진탐색으로 last_adid가 위치한 페이지 번호를 반환한다.

        목록은 adid 내림차순 (page 1 = 최신, 마지막 page = 가장 오래된 공고).
        hi = (newest_adid - last_adid) // 50 + buffer 로 추정한 상한값.

        빈 페이지(hi가 실제 마지막 페이지보다 큰 경우)는 hi를 줄여 처리한다.
        """
        lo = 1
        target_page = hi

        while lo <= hi:
            mid   = (lo + hi) // 2
            adids = self._get_page_adids(mid)

            if not adids:
                # 페이지 없음 → hi가 너무 큼
                hi = mid - 1
                continue

            page_max = max(adids)
            page_min = min(adids)

            if last_adid > page_max:
                # last_adid가 더 앞(번호 작은) 페이지에 있음
                hi = mid - 1
            elif last_adid < page_min:
                # last_adid가 더 뒤(번호 큰) 페이지에 있음
                lo = mid + 1
            else:
                # 이 페이지에 last_adid가 있거나 인접
                target_page = mid
                break
        else:
            target_page = min(lo, hi) if hi >= lo else hi

        return max(target_page, 1)

    # ── 단건 크롤링 ───────────────────────────────────────────────────────────

    def _crawl_adid(self, adid: int) -> Optional[CrawlJob]:
        """단일 adid의 상세 페이지를 크롤링하여 CrawlJob을 반환한다."""
        time.sleep(self.crawl_delay + random.uniform(0, self.crawl_jitter))
        url  = f"{self.DETAIL_URL}{adid}"
        html = self.fetch_with_retry(url)

        if html is None:
            return None

        posted_at = self._parse_posted_at(html)
        self._save_html(adid, html, posted_at)

        job = self.parse(html)
        if job is None:
            print(f"[SKIP] 유효하지 않은 공고 - adid={adid}")
            return None

        job.content      = self._fetch_iframe_content(html)
        job.external_url = url
        return job

    # ── 수집 진입점 ───────────────────────────────────────────────────────────

    def get_latest_id(self) -> int:
        """목록 페이지 첫 번째 공고의 adid를 최신 id로 반환한다."""
        adids = self._get_page_adids(1)
        if not adids:
            raise Exception("[ERROR] 목록 페이지에서 최신 adid를 찾을 수 없음")
        return max(adids)

    def collect(self, **kwargs):
        """
        오래된 공고부터 최신 공고 방향으로 (adid 오름차순) CrawlJob을 yield한다.

        kwargs:
            last_crawled_adid (int | None): 이전 세션의 마지막 수집 adid.
                None이면 첫 실행 → _get_total_pages()로 마지막 페이지부터 시작.
                지정 시 이진탐색으로 해당 페이지를 찾아 이어서 크롤링.
            run_target_adid (int): 이번 run의 목표 adid (이 값 초과 시 종료).
            newest_adid (int): 이진탐색 hi 계산용 현재 최신 adid.

        수집 흐름:
            start_page (마지막 페이지 방향) → page 1 방향으로 역순 순회
            각 페이지 내 adid는 오름차순 정렬하여 수집
            adid <= last_crawled_adid 는 건너뜀 (이미 수집한 것)
            adid > run_target_adid 도달 시 종료
        """
        last_adid      = kwargs.get("last_crawled_adid")   # None = 첫 실행
        run_target_adid = kwargs.get("run_target_adid")
        newest_adid    = kwargs.get("newest_adid")

        if last_adid is None:
            # 첫 실행: 마지막 페이지부터 시작
            start_page = self._get_total_pages()
            print(f"[알바천국] 첫 실행 - 마지막 페이지({start_page})부터 크롤링 시작")
        else:
            # resume: (newest - last) / 50 을 hi 상한으로 이진탐색
            hi = max((newest_adid - last_adid) // 50 + 10, 20)
            start_page = self._find_start_page(last_adid, hi)
            print(
                f"[알바천국] 재시작 - start_page={start_page}, "
                f"last_adid={last_adid}, target={run_target_adid}"
            )

        # 마지막 페이지 → page 1 방향으로 순회 (adid 오름차순)
        # last_seen을 동적으로 갱신 → 오름차순 특성상 중복 자동 방지
        last_seen = last_adid  # None이면 처음부터 수집
        for page in range(start_page, 0, -1):
            adids = self._get_page_adids(page)
            if not adids:
                continue

            for adid in sorted(adids):
                if last_seen is not None and adid <= last_seen:
                    continue  # 이미 수집했거나 중복

                if run_target_adid is not None and adid > run_target_adid:
                    print(f"[알바천국] run_target({run_target_adid}) 도달 → 종료")
                    return

                job = self._crawl_adid(adid)
                if job:
                    last_seen = adid
                    yield job

    # ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

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
