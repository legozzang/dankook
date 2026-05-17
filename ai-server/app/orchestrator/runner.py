"""
runner.py
등록된 크롤러를 순차적으로 실행하고 수집 결과를 백엔드로 전송한다.

전체 흐름:
  1. CRAWLERS 목록을 순서대로 순회
  2. 각 크롤러마다 crawler_state.csv에서 이전 상태(last_id)를 불러옴
  3. 크롤러의 collect()를 호출해 CrawlJob을 하나씩 yield 받음
  4. yield 받을 때마다 백엔드 POST + 상태 즉시 저장 (중단 시 resume 가능)
  5. 모든 크롤러 완료 후 종료 → OS 스케줄러 또는 오케스트라가 재실행

실행 방법 (ai-server/ 루트에서):
  python -m app.orchestrator.runner
"""

import csv
import os
import requests
from dataclasses import asdict
from datetime import datetime
from dotenv import load_dotenv

load_dotenv()

from app.collector.base.collector import CrawlJob
from app.collector.crawlers.albaheaven_crawler import AlbaHeavenCrawler
from app.collector.crawlers.albamon_crawler import AlbamonCrawler

# ── 설정 ──────────────────────────────────────────────────────────────────────

# 백엔드 API 주소: .env의 BACKEND_URL이 없으면 로컬 기본값 사용
BACKEND_URL    = os.getenv("BACKEND_URL", "http://localhost:8080/api/job-postings")

BASE_DIR       = os.path.dirname(os.path.abspath(__file__))
DATA_DIR       = os.path.join(BASE_DIR, "data")                      # orchestrator/data/
STATE_CSV_PATH = os.path.join(DATA_DIR, "crawler_state.csv")         # 크롤러별 상태 파일

STATE_CSV_HEADER = ["source", "last_id", "updated_at"]

# ── 크롤러 목록 ───────────────────────────────────────────────────────────────
# 크롤러를 추가할 때 이 목록에만 항목을 추가하면 된다.
#   source  : 크롤러 식별자 (상태 CSV의 키, 로그 출력에 사용)
#   cls     : 크롤러 클래스 (BaseCollector를 상속한 클래스)
#   kwargs  : collect() 호출 시 추가로 전달할 파라미터

CRAWLERS = [
    {"source": "ALBAHEAVEN", "cls": AlbaHeavenCrawler, "kwargs": {}},
    {"source": "ALBAMON",    "cls": AlbamonCrawler,    "kwargs": {}},
    # {"source": "WORK24", "cls": Work24Client, "kwargs": {"total_pages": 10}},
]

# ── 상태 관리 ─────────────────────────────────────────────────────────────────

def _load_state(source: str, target_id: int):
    """crawler_state.csv에서 해당 source의 last_id를 읽는다. 없으면 None 반환."""
    if not os.path.exists(STATE_CSV_PATH):
        return target_id
    with open(STATE_CSV_PATH, "r", newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["source"] == source:
                try:
                    return int(row["last_id"])
                except (ValueError, KeyError):
                    return target_id
    return target_id


def _save_state(source: str, last_id: int):
    """crawler_state.csv에 해당 source의 last_id를 저장한다."""
    os.makedirs(os.path.dirname(STATE_CSV_PATH), exist_ok=True)
    rows    = []
    updated = False

    if os.path.exists(STATE_CSV_PATH):
        with open(STATE_CSV_PATH, "r", newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row["source"] == source:
                    row["last_id"]    = last_id
                    row["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    updated = True
                rows.append(row)

    if not updated:
        rows.append({
            "source":     source,
            "last_id":    last_id,
            "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        })

    with open(STATE_CSV_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=STATE_CSV_HEADER)
        writer.writeheader()
        writer.writerows(rows)


# ── 백엔드 전송 ───────────────────────────────────────────────────────────────

def _send_to_backend(job: CrawlJob):
    """
    수집한 CrawlJob 하나를 백엔드 API로 POST 전송한다.

    Enum 필드(source_type, status)는 문자열 값으로 변환해서 전송한다.
    전송 실패 시 해당 공고는 스킵하고 로그를 출력한다 (프로세스는 계속 진행).
    """
    payload = asdict(job)
    payload["source_type"] = job.source_type.value  # Enum → 문자열
    payload["status"]      = job.status.value        # Enum → 문자열
    try:
        res = requests.post(BACKEND_URL, json=payload, timeout=10)
        res.raise_for_status()
    except requests.RequestException as e:
        print(f"  [전송 실패] {job.title} - {e}")


# ── 크롤러 실행 ───────────────────────────────────────────────────────────────

def _run_crawler(source: str, crawler, extra_kwargs: dict):
    """
    크롤러 하나를 실행한다.

    last_id가 없으면 첫 실행, 있으면 해당 id 이후부터 resume.
    target_id는 항상 현재 최신 id로 설정한다.

    공고 하나를 수집할 때마다:
      1. 백엔드로 전송
      2. 상태 저장 (중단 시 이 지점부터 resume)
    """

    target_id = crawler.get_latest_id()
    last_id = _load_state(source, target_id)

    print(f"\n[{source}] {'새 run' if last_id is None else '재시작'} (last={last_id}, target={target_id})")

    count = 0
    for job in crawler.collect(
        last_id=last_id,
        target_id=target_id,
        **extra_kwargs,
    ):
        item_id = crawler._parse_id_from_url(job.external_url)
        if item_id is not None:
            _save_state(source, item_id)
            _send_to_backend(job)

        count += 1
        print(f"  [{count}] {job.company} - {job.title}")

    print(f"[{source}] 완료: {count}건 수집")


# ── 진입점 ────────────────────────────────────────────────────────────────────

def run():
    """
    CRAWLERS 목록의 크롤러를 순서대로 실행한다.
    하나가 완료되면 다음 크롤러를 실행한다 (순차 실행).
    모든 크롤러가 완료되면 종료 → 재실행은 OS 스케줄러 또는 오케스트레이터가 담당.
    """
    os.makedirs(DATA_DIR, exist_ok=True)
    for config in CRAWLERS:
        crawler = config["cls"]()
        _run_crawler(config["source"], crawler, config.get("kwargs", {}))


if __name__ == "__main__":
    import time
    while True:
        run()
        print("\n[오케스트레이터] 모든 크롤러 완료 - 5초 대기 후 재실행")
        time.sleep(5)
