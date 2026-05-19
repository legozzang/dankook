"""크롤러 상태(last_id) CSV 관리"""
import csv
import os
from datetime import datetime

STATE_HEADER = ["source", "last_id", "updated_at"]


def load_state(state_csv: str, source: str, default: int) -> int:
    if not os.path.exists(state_csv):
        return default
    with open(state_csv, "r", newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["source"] == source:
                try:
                    return int(row["last_id"])
                except (ValueError, KeyError):
                    return default
    return default


def save_state(state_csv: str, source: str, last_id: int) -> None:
    os.makedirs(os.path.dirname(state_csv), exist_ok=True)
    rows: list[dict] = []
    updated = False
    if os.path.exists(state_csv):
        with open(state_csv, "r", newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row["source"] == source:
                    row["last_id"] = last_id
                    row["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    updated = True
                rows.append(row)
    if not updated:
        rows.append({
            "source": source,
            "last_id": last_id,
            "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        })
    with open(state_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=STATE_HEADER, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
