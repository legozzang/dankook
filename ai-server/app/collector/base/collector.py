# base_collector.py
import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class SourceType(Enum):
    """채용공고 출처 구분"""
    INTERNAL    = "INTERNAL"     # 내부 공고
    ALBAMON     = "ALBAMON"      # 알바몬
    ALBAHEAVEN  = "ALBAHEAVEN"   # 알바천국


class JobStatus(Enum):
    """채용공고 모집 상태"""
    OPEN   = "OPEN"    # 모집중
    CLOSED = "CLOSED"  # 마감


@dataclass
class CrawlJob:
    """수집된 채용공고 하나를 나타내는 데이터 클래스"""
    # 필수
    company:      str               # 회사명
    title:        str               # 공고 제목
    content:      str               # 공고 본문
    region:       str               # 근무지 주소
    job_type:     str               # 직무 (여러 개면 콤마 구분)
    external_url: str               # 채용공고 원본 URL
    source_type:  SourceType        # 출처

    # 급여
    pay_type:     str  = ""         # 급여 유형 (시급/일급/주급/월급/연봉/건별)
    pay_amount:   int  = 0          # 급여 금액 (원 단위, 파싱 실패 시 0)

    # 근무 조건
    work_period:  str  = ""         # 근무기간
    work_time:    str  = ""         # 근무시간
    work_days:    str  = ""         # 근무요일
    employ_type:  str  = ""         # 고용형태
    welfare:      str  = ""         # 복리후생

    # 모집 조건
    education:    str  = ""         # 학력
    preferred:    str  = ""         # 우대조건
    deadline:     str  = ""         # 모집마감
    headcount:    str  = ""         # 모집인원

    status: JobStatus = field(default=JobStatus.OPEN)  # 수집 시점엔 항상 모집중

    def __post_init__(self):
        m = re.search(r"(\d{4})[.\-](\d{2})[.\-](\d{2})", self.deadline)
        self.deadline = f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if m else ""


class BaseCollector(ABC):
    """
    모든 수집기의 최상위 추상 클래스.
    크롤러(BaseCrawler)와 API 클라이언트(Work24Client 등)가 이를 상속받는다.
    수집 결과는 항상 CrawlJob으로 반환한다.
    """

    @abstractmethod
    def collect(self, **kwargs):
        """CrawlJob을 yield하는 제너레이터. 구현체에서 수집 방식에 맞게 구현한다."""
        pass
