"""
stages/geocode.py
jobs_raw.csv에서 geocoding 미완료 행을 Kakao API로 enrichment하여
jobs_geocoded.csv에 append한다.
"""
import os

from dotenv import load_dotenv

_AI_SERVER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
load_dotenv(os.path.join(_AI_SERVER_ROOT, ".env"))

from app.geocoder.kakao_geocoder import geocode
from app.orchestrator.shared import csv_io

DATA_DIR = os.path.join(_AI_SERVER_ROOT, "data")
RAW_CSV = os.path.join(DATA_DIR, "jobs_raw.csv")
GEOCODED_CSV = os.path.join(DATA_DIR, "jobs_geocoded.csv")


def main() -> int:
    if not os.path.exists(RAW_CSV):
        print(f"[geocode] 입력 파일 없음: {RAW_CSV}")
        return 0

    _, raw_rows = csv_io.load_rows_deduped(RAW_CSV)
    geocoded_urls = csv_io.load_seen_urls(GEOCODED_CSV)
    region_cache: dict = {}
    count = 0

    for row in raw_rows:
        url = row.get("external_url", "").strip()
        if not url or url in geocoded_urls:
            continue

        region = row.get("region", "")
        if region not in region_cache:
            region_cache[region] = geocode(region)
        coord = region_cache[region]

        if coord:
            row["latitude"], row["longitude"], row["region_sido"], row["region_sigungu"] = coord
        else:
            row.setdefault("latitude", "")
            row.setdefault("longitude", "")
            row.setdefault("region_sido", "")
            row.setdefault("region_sigungu", "")

        csv_io.append_row(GEOCODED_CSV, row)
        geocoded_urls.add(url)
        count += 1

    print(f"[geocode] {count}건 geocoding 완료")
    return count


if __name__ == "__main__":
    import time
    while True:
        result = main()
        interval = 5 * 60 if result > 0 else 15 * 60
        print(f"[geocode] {interval // 60}분 후 재실행")
        time.sleep(interval)
