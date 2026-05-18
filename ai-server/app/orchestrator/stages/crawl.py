"""
stages/crawl.py
등록된 크롤러를 순차 실행하고 수집 결과를 jobs_raw.csv에 스테이징한다.
geocoding은 stages/geocode.py에서 별도 수행.
"""
import os
from dataclasses import asdict

from dotenv import load_dotenv

_AI_SERVER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
load_dotenv(os.path.join(_AI_SERVER_ROOT, ".env"))

from app.collector.base.collector import CrawlJob
from app.collector.crawlers.albaheaven_crawler import AlbaHeavenCrawler
from app.collector.crawlers.albamon_crawler import AlbamonCrawler
from app.orchestrator.shared import csv_io, state

DATA_DIR = os.path.join(_AI_SERVER_ROOT, "data")
RAW_CSV = os.path.join(DATA_DIR, "jobs_raw.csv")
STATE_CSV = os.path.join(DATA_DIR, "crawler_state.csv")

CRAWLERS = [
    {"source": "ALBAHEAVEN", "cls": AlbaHeavenCrawler, "kwargs": {}},
    {"source": "ALBAMON", "cls": AlbamonCrawler, "kwargs": {}},
]


def _enum_to_str(value) -> str:
    raw = value.value
    return raw[0] if isinstance(raw, tuple) else raw


def _job_to_row(job: CrawlJob) -> dict:
    row = asdict(job)
    row["source_type"] = _enum_to_str(job.source_type)
    row["status"] = _enum_to_str(job.status)
    return row


def _run_crawler(
    source: str,
    crawler,
    extra_kwargs: dict,
    url_seen: set[str],
) -> int:
    target_id = crawler.get_latest_id()
    last_id = state.load_state(STATE_CSV, source, max(target_id - 100, 0))
    print(f"\n[{source}] last={last_id}, target={target_id}")

    count = 0
    for job in crawler.collect(last_id=last_id, target_id=target_id, **extra_kwargs):
        item_id = crawler._parse_id_from_url(job.external_url)
        if item_id is None:
            continue
        state.save_state(STATE_CSV, source, item_id)
        if job.external_url not in url_seen:
            csv_io.append_row(RAW_CSV, _job_to_row(job))
            url_seen.add(job.external_url)
        count += 1
        print(f"  [{count}] {job.company} - {job.title}")

    print(f"[{source}] 완료: {count}건 수집")
    return count


def main() -> int:
    os.makedirs(DATA_DIR, exist_ok=True)
    url_seen = csv_io.load_seen_urls(RAW_CSV)
    total = 0
    for config in CRAWLERS:
        crawler = config["cls"](crawl_delay=3.0, crawl_jitter=2.0)
        total += _run_crawler(config["source"], crawler, config.get("kwargs", {}), url_seen)
    return total


if __name__ == "__main__":
    import time
    while True:
        main()
        print("\n[크롤러] 60초 대기 후 재실행")
        time.sleep(60)
