"""
uploader.py
data/jobs_classified.csv에서 전송 가능한 공고만 백엔드로 POST하고
성공한 external_url을 data/sent_ids.txt에 기록한다.

실행 방법 (ai-server/ 루트에서):
  python -m app.orchestrator.uploader
"""

import csv
import os
import shutil
import sys
import time

import requests
from dotenv import load_dotenv

# ai-server/.env — 실행 cwd와 무관하게 로드
_AI_SERVER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
load_dotenv(os.path.join(_AI_SERVER_ROOT, ".env"))

DATA_DIR = os.path.join(_AI_SERVER_ROOT, "data")
CLASSIFIED_CSV_PATH = os.path.join(DATA_DIR, "jobs_classified.csv")
SNAPSHOT_PATH = os.path.join(DATA_DIR, "jobs_classified_snapshot.csv")
SENT_IDS_PATH = os.path.join(DATA_DIR, "sent_ids.txt")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080/api/job-postings")


def _load_sent_ids() -> set[str]:
    if not os.path.exists(SENT_IDS_PATH):
        return set()

    with open(SENT_IDS_PATH, "r", encoding="utf-8") as f:
        return {line.strip() for line in f if line.strip()}


def _is_ready_to_send(row: dict) -> bool:
    return bool(
        row.get("latitude", "").strip()
        and row.get("longitude", "").strip()
        and row.get("job_type_detail", "").strip()
    )


def _post_job(row: dict) -> bool:
    try:
        res = requests.post(BACKEND_URL, json=row, timeout=10)
        res.raise_for_status()
        return True
    except requests.RequestException as e:
        print(f"  [전송 실패] {row.get('title', '')} - {e}")
        return False


def main() -> None:
    if not os.path.exists(CLASSIFIED_CSV_PATH):
        print(f"[ERROR] 입력 파일 없음: {CLASSIFIED_CSV_PATH}", file=sys.stderr)
        return
    if os.path.exists(SNAPSHOT_PATH):
        os.remove(SNAPSHOT_PATH)
    shutil.copy2(CLASSIFIED_CSV_PATH, SNAPSHOT_PATH)

    os.makedirs(DATA_DIR, exist_ok=True)
    sent_ids = _load_sent_ids()

    total = 0
    skipped = 0
    sent = 0
    failed = 0

    with open(SNAPSHOT_PATH, "r", newline="", encoding="utf-8") as input_file:
        reader = csv.DictReader(input_file)

        with open(SENT_IDS_PATH, "a", encoding="utf-8") as sent_file:
            for raw_row in reader:
                try:
                    row = dict(raw_row)
                    total += 1
                    external_url = row.get("external_url", "").strip()

                    if not external_url or external_url in sent_ids or not _is_ready_to_send(row):
                        skipped += 1
                    elif _post_job(row):
                        sent_file.write(f"{external_url}\n")
                        sent_file.flush()
                        sent_ids.add(external_url)
                        sent += 1
                    else:
                        failed += 1

                    if total % 100 == 0:
                        print(f"  진행: {total}행 확인 ({sent}건 전송, {skipped}건 스킵, {failed}건 실패)")
                except Exception:
                    skipped += 1

    print(f"완료: {total}행 확인, {sent}건 전송, {skipped}건 스킵, {failed}건 실패")
    print(f"전송 기록: {SENT_IDS_PATH}")


if __name__ == "__main__":
    POLL_INTERVAL = 10 * 60
    while True:
        main()
        print(f"[업로더] {POLL_INTERVAL // 60}분 후 재실행")
        time.sleep(POLL_INTERVAL)
