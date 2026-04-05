from abc import ABC, abstractmethod
from dataclasses import dataclass
from queue import Queue
from typing import Optional
import time


@dataclass
class CrawlJob:
    """크롤링된 채용공고 하나를 나타내는 데이터 클래스"""
    url: str                        # 채용공고 원본 URL
    title: str                      # 공고 제목
    company: str                    # 회사명
    location: str                   # 근무지
    deadline: Optional[str] = None  # 마감일 (없으면 None)
    source: str = ""                # 출처 크롤러 ("alba", "saramin" 등)


class BaseCrawler(ABC):
    """
    모든 크롤러의 추상 기반 클래스.
    각 사이트별 크롤러는 이 클래스를 상속받아
    get_latest_id(), crawl(), _fetch(), parse()를 구현한다.
    """

    def __init__(self, crawl_max_retries: int = 3):
        self.crawl_max_retries = crawl_max_retries  # 크롤링 실패 시 재시도 횟수
        self.queue = Queue()                         # 크롤링 결과를 담는 내부 큐

    def run(self, start_id: int):
        """
        Orchestrator가 호출하는 크롤링 시작 메서드.
        end_id는 크롤러가 직접 목록 페이지에서 수집한다.
        """
        end_id = self.get_latest_id()
        for job in self.crawl(start_id, end_id):
            self.queue.put(job)

    @abstractmethod
    def get_latest_id(self) -> int:
        """
        사이트 목록 페이지에서 최신 공고 id를 수집한다.
        각 크롤러 구현체가 사이트에 맞게 구현한다.
        """
        pass

    @abstractmethod
    def crawl(self, start_id: int, end_id: int):
        """
        start_id ~ end_id 범위를 순회하며 CrawlJob을 yield하는 제너레이터.
        start_id는 Orchestrator가, end_id는 get_latest_id()가 제공한다.
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
        """
        실제 HTTP 요청 로직.
        구현체에서 requests.get 등을 사용해 오버라이드한다.
        """
        pass

    @abstractmethod
    def parse(self, html: str) -> Optional[CrawlJob]:
        """
        HTML을 파싱하여 CrawlJob으로 변환한다.
        파싱 실패 또는 유효하지 않은 페이지면 None을 반환한다.
        """
        pass