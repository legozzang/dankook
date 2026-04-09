# tests/collector/test_collectors_live.py
#
# BaseCollector를 상속받는 모든 수집기의 동작을 확인하는 범용 live test.
# 새 수집기 추가 시 COLLECTOR_CASES에 항목만 추가하면 된다.
#
# 사용법:
#   python tests/collector/test_collectors_live.py
#

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../.."))

from app.collector.base.collector import CrawlJob, SourceType
from app.collector.crawlers.albaheaven_crawler import AlbaHeavenCrawler


# ── 수집기 케이스 등록 ─────────────────────────────────────────────────────────
#
# 새 수집기 추가 시 아래 리스트에 딕셔너리만 추가하면 된다.
#
#   label     : 출력용 이름
#   instance  : 수집기 인스턴스
#   params    : collect()에 넘길 파라미터
#   limit     : 최대 몇 개까지 수집할지 (테스트용)

def get_albaheaven_params():
    crawler   = AlbaHeavenCrawler()
    latest_id = crawler.get_latest_id()
    return crawler, {"start_id": latest_id - 2}

_albaheaven, _albaheaven_params = get_albaheaven_params()

COLLECTOR_CASES = [
    {
        "label":    "AlbaHeavenCrawler",
        "instance": _albaheaven,
        "params":   _albaheaven_params,
        "limit":    3,
    },
    # 새 수집기 추가 예시:
    # {
    #     "label":    "AlbamonCrawler",
    #     "instance": AlbamonCrawler(),
    #     "params":   {"start_id": ...},
    #     "limit":    3,
    # },
]


# ── 검증 ───────────────────────────────────────────────────────────────────────

def validate_job(job: CrawlJob) -> list[str]:
    """CrawlJob 필수 필드 검증. 문제 있는 항목 목록을 반환한다."""
    errors = []

    if not isinstance(job, CrawlJob):
        errors.append("CrawlJob 타입이 아님")
        return errors

    if not job.title:
        errors.append("title 비어있음")
    if not job.company:
        errors.append("company 비어있음")
    if not job.region:
        errors.append("region 비어있음")
    if not job.external_url:
        errors.append("external_url 비어있음")
    if not isinstance(job.source_type, SourceType):
        errors.append("source_type이 SourceType 아님")
    if job.pay_amount < 0:
        errors.append("pay_amount 음수")

    return errors


# ── 실행 ───────────────────────────────────────────────────────────────────────

def run_case(case: dict):
    label    = case["label"]
    instance = case["instance"]
    params   = case["params"]
    limit    = case["limit"]

    print(f"\n{'=' * 50}")
    print(f"[{label}]")
    print(f"{'=' * 50}")

    collected = 0
    passed    = 0
    failed    = 0

    try:
        for job in instance.collect(**params):
            collected += 1
            errors = validate_job(job)

            if errors:
                failed += 1
                print(f"\n  [FAIL] #{collected}")
                for e in errors:
                    print(f"    - {e}")
            else:
                passed += 1
                print(f"\n  [PASS] #{collected}")

            print(f"    제목:          {job.title}")
            print(f"    회사:          {job.company}")
            print(f"    지역:          {job.region}")
            print(f"    직무:          {job.job_type}")
            print(f"    급여:          {job.pay_type} {job.pay_amount:,}원")
            print(f"    고용형태:      {job.employ_type}")
            print(f"    근무시간:      {job.work_time}")
            print(f"    학력:          {job.education}")
            print(f"    마감:          {job.deadline}")
            print(f"    출처:          {job.source_type}")
            print(f"    URL:           {job.external_url}")
            print(f"    본문(앞 50자): {job.content[:50]}")

            if collected >= limit:
                break

    except Exception as e:
        print(f"\n  [ERROR] 수집 중 예외 발생: {e}")
        return

    print(f"\n  결과: 총 {collected}건 | PASS {passed} | FAIL {failed}")


def main():
    for case in COLLECTOR_CASES:
        run_case(case)

    print(f"\n{'=' * 50}")
    print("완료")
    print(f"{'=' * 50}")


if __name__ == "__main__":
    main()