# base_crawler.py
from abc import abstractmethod
from typing import Optional
import time

from app.collector.base_collector import BaseCollector, CrawlJob


class BaseCrawler(BaseCollector):
    """
    HTML 크롤링 기반 수집기의 추상 클래스.
    fetch_with_retry 등 크롤러 공통 로직을 제공한다.
    각 사이트별 크롤러는 이 클래스를 상속받아 구현한다.

    parse()는 전달받은 HTML만 사용하는 순수 파싱 메서드여야 한다.
    네트워크 요청이 필요한 추가 작업(iframe 등)은 collect()에서 처리한다.
    """

    def __init__(self, crawl_max_retries: int = 3):
        super().__init__()
        self.crawl_max_retries = crawl_max_retries

    @abstractmethod
    def get_latest_id(self) -> int:
        """사이트 목록 페이지에서 최신 공고 id를 수집한다."""
        pass

    @abstractmethod
    def collect(self, **kwargs):
        """
        수집을 시작하는 메서드. CrawlJob을 yield하는 제너레이터.
        kwargs에 start_id를 전달받아 사용한다.
        end_id는 get_latest_id()로 직접 수집한다.
        """
        pass

    def fetch_with_retry(self, url: str):
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

    @abstractmethod
    def _fetch(self, url: str) -> str:
        """실제 HTTP 요청 로직. 구현체에서 오버라이드한다."""
        pass

    @abstractmethod
    def parse(self, html: str) -> Optional[CrawlJob]:
        """
        전달받은 HTML만 사용하는 순수 파싱 메서드.
        파싱 실패 또는 유효하지 않은 페이지면 None을 반환한다.
        네트워크 요청은 하지 않는다.
        """
        pass