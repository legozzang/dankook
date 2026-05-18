"""Gemini API rate-limit 재시도 공통 로직"""
import time
from typing import Callable, TypeVar

T = TypeVar("T")


def call_with_retry(fn: Callable[[], T], *, attempts: int = 5, base_wait: int = 60) -> T:
    """
    fn()을 최대 attempts번 호출한다.
    - 429 / rate_limit / quota 오류: base_wait*(attempt+1)초 대기 후 재시도
    - 그 외 예외: 즉시 raise
    - 최대 재시도 초과: RuntimeError raise
    """
    for attempt in range(attempts):
        try:
            return fn()
        except Exception as e:
            err = str(e)
            if "429" in err or "rate_limit" in err.lower() or "quota" in err.lower():
                wait = base_wait * (attempt + 1)
                print(f"  [RateLimit] {wait}초 대기 ({attempt + 1}/{attempts})...")
                time.sleep(wait)
            else:
                raise
    raise RuntimeError(f"최대 재시도({attempts}회) 초과")
