# base_crawler.py
import time
import random
import requests
from abc import abstractmethod
from typing import Optional

from app.collector.base.collector import BaseCollector, CrawlJob


class BaseCrawler(BaseCollector):
    """
    HTML 크롤링 기반 수집기의 추상 클래스.
    모든 크롤러가 따르는 수집 흐름을 템플릿 메서드로 정의한다.

    수집 흐름:
      collect()
        └─ _get_page_ids()       목록 페이지에서 ID 목록 추출
        └─ _find_start_page()    이진탐색으로 resume 위치 탐색
        └─ _crawl_item()         단건 수집
              └─ _build_item_url()  ID → 상세 페이지 URL
              └─ _fetch()           HTTP 요청
              └─ parse()            HTML → CrawlJob
              └─ _fetch_content()   본문 수집

    하위 크롤러는 아래 추상 메서드 4개를 구현해야 된다:
      _get_page_ids, _build_item_url, parse, _fetch_content, _parse_id_from_url
    """

    NAME = ""  # SOURCE_TYPE.label로 자동 설정됨

    HEADERS = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                      "AppleWebKit/537.36 (KHTML, like Gecko) "
                      "Chrome/120.0.0.0 Safari/537.36"
    }

    def __init__(self, crawl_max_retries: int = 3, crawl_delay: float = 1.5, crawl_jitter: float = 1.0):
        self.crawl_max_retries = crawl_max_retries
        self.crawl_delay       = crawl_delay   # 기본 대기 시간 (초)
        self.crawl_jitter      = crawl_jitter  # 랜덤 추가 대기 상한 (초)

    # ── 템플릿 메서드 (공통 흐름) ──────────────────────────────────────────────

    def collect(self, last_id, target_id, **kwargs):
        """
        오래된 공고부터 최신 공고 방향으로 (id 오름차순) CrawlJob을 yield한다.

        last_id   : 이전 세션의 마지막 수집 id. None이면 첫 실행.
        target_id : 수집 상한 id (이 값 초과 시 종료, 이진탐색 hi 계산에도 사용).
        """

        # resume: (target - last) / 50 을 hi 상한으로 이진탐색
        hi         = max((target_id - last_id) // 50 + 10, 20)
        start_page = self._find_start_page(last_id, hi)
        print(
            f"[{self.NAME}] 재시작 - start_page={start_page}, "
            f"last_id={last_id}, target={target_id}"
        )

        # 마지막 페이지 → page 1 방향으로 순회 (id 오름차순)
        last_seen = last_id
        for page in range(start_page, 0, -1):
            ids = self._get_page_ids(page)
            if not ids:
                continue

            for item_id in sorted(ids):
                if last_seen is not None and item_id <= last_seen:
                    continue  # 이미 수집했거나 중복

                if item_id > target_id:
                    print(f"[{self.NAME}] target({target_id}) 도달 → 종료")
                    return

                job = self._crawl_item(item_id)
                if job:
                    last_seen = item_id
                    yield job

    def _find_start_page(self, last_id: int, hi: int) -> int:
        """
        이진탐색으로 last_adid가 위치한 페이지 번호를 반환한다.

        목록은 id 내림차순 (page 1 = 최신, 마지막 page = 가장 오래된 공고).
        hi = (target_id - last_id) // 50 + buffer 로 추정한 상한값.

        빈 페이지(hi가 실제 마지막 페이지보다 큰 경우)는 hi를 줄여 처리한다.
        """
        lo          = 1
        target_page = hi

        while lo <= hi:
            mid = (lo + hi) // 2
            ids = self._get_page_ids(mid)

            if not ids:
                # 페이지 없음 → hi가 너무 큼
                hi = mid - 1
                continue

            page_max = max(ids)
            page_min = min(ids)

            if last_id > page_max:
                # last_id가 더 앞(번호 작은) 페이지에 있음
                hi = mid - 1
            elif last_id < page_min:
                # last_id가 더 뒤(번호 큰) 페이지에 있음
                lo = mid + 1
            else:
                # 이 페이지에 last_id가 있거나 인접
                target_page = mid
                break
        else:
            target_page = min(lo, hi) if hi >= lo else hi

        return max(target_page, 1)

    def _crawl_item(self, item_id: int) -> Optional[CrawlJob]:
        """
        단일 item_id의 상세 페이지를 크롤링하여 CrawlJob을 반환한다.
        _build_item_url → _fetch → parse → _fetch_content 순서로 호출한다.
        """
        time.sleep(self.crawl_delay + random.uniform(0, self.crawl_jitter))

        url  = self._build_item_url(item_id)
        html = self.fetch_with_retry(url)
        if html is None:
            return None

        job = self.parse(html)
        if job is None:
            print(f"[SKIP] 유효하지 않은 공고 - id={item_id}")
            return None

        job.content      = self._fetch_content(html)
        job.external_url = url
        return job

    def fetch_with_retry(self, url: str) -> Optional[str]:
        """
        단일 URL을 크롤링하되, 실패 시 최대 crawl_max_retries번 재시도한다.
        모든 시도가 실패하면 None을 반환하고 해당 URL은 스킵한다.
        재시도 간격은 지수 백오프(1초 → 2초 → 4초)를 적용한다.
        """
        for attempt in range(self.crawl_max_retries):
            try:
                return self._fetch(url)
            except Exception as e:
                if attempt == self.crawl_max_retries - 1:
                    print(f"[SKIP] 최대 재시도 초과 - {url} / 원인: {e}")
                    return None
                wait = 2 ** attempt
                print(f"[RETRY] {attempt + 1}번째 실패, {wait}초 후 재시도 - {url}")
                time.sleep(wait)

    def get_latest_id(self) -> int:
        """목록 1페이지에서 가장 큰 id를 최신 id로 반환한다."""
        ids = self._get_page_ids(1)
        if not ids:
            raise RuntimeError(f"[ERROR] {self.NAME} 목록 페이지에서 최신 id를 찾을 수 없음")
        return max(ids)

    def _fetch(self, url: str) -> str:
        """HTTP GET 요청으로 HTML을 반환한다. 필요 시 하위 클래스에서 오버라이드."""
        response = requests.get(url, headers=self.HEADERS, timeout=10)
        response.raise_for_status()
        return response.text

    # ── 추상 메서드 (각 크롤러에서 구현) ──────────────────────────────────────

    @abstractmethod
    def _get_page_ids(self, page: int) -> list:
        """목록 페이지에서 id 목록을 추출하여 반환한다. 실패 시 빈 리스트."""
        pass

    @abstractmethod
    def _build_item_url(self, item_id: int) -> str:
        """item_id로 상세 페이지 URL을 생성하여 반환한다."""
        pass

    @abstractmethod
    def parse(self, html: str) -> Optional[CrawlJob]:
        """
        전달받은 HTML만 사용하는 순수 파싱 메서드.
        content는 빈 문자열로 두고, _fetch_content에서 채운다.
        파싱 실패 시 None을 반환한다.
        """
        pass

    @abstractmethod
    def _fetch_content(self, html: str) -> str:
        """
        공고 본문(content)을 수집하여 반환한다.
        사이트에 따라 iframe 추가 요청 또는 JSON 추출로 구현한다.
        """
        pass

    @abstractmethod
    def _parse_id_from_url(self, url: str) -> Optional[int]:
        """
        상세 페이지 URL에서 공고 id를 추출하여 반환한다.
        추출 실패 시 None을 반환한다.
        """
        pass
