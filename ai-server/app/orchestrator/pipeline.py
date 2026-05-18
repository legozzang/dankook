"""
pipeline.py
ai-server 전체 파이프라인 단일 진입점.
crawl -> geocode -> classify -> summarize -> upload 순서로 실행한다.

실행 방법 (ai-server/ 루트에서):
  python -m app.orchestrator.pipeline
"""
import time

from app.orchestrator.stages import crawl, geocode, classify, summarize, upload


def run_pipeline() -> None:
    print("\n=== [1/5] 크롤링 ===")
    crawl.main()

    print("\n=== [2/5] Geocoding ===")
    geocode.main()

    print("\n=== [3/5] AI 분류 ===")
    classify.main()

    print("\n=== [4/5] 추천사유 생성 ===")
    summarize.main()

    print("\n=== [5/5] 업로드 ===")
    upload.main()


if __name__ == "__main__":
    INTERVAL = 10 * 60
    while True:
        run_pipeline()
        print(f"\n[Pipeline] 전체 사이클 완료 - {INTERVAL // 60}분 후 재실행")
        time.sleep(INTERVAL)
