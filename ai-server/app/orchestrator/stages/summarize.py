"""
stages/summarize.py
data/jobs_classified.csv의 각 공고에 추천사유(recommendation_reason)를 생성한다.

실행 방법 (ai-server/ 루트에서):
  python -m app.orchestrator.stages.summarize
"""

import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from dotenv import load_dotenv

# ai-server/.env — 실행 cwd와 무관하게 로드
_AI_SERVER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
load_dotenv(os.path.join(_AI_SERVER_ROOT, ".env"))

from google import genai
from app.orchestrator.shared import csv_io, retry

DATA_DIR = os.path.join(_AI_SERVER_ROOT, "data")
INPUT_CSV = os.path.join(DATA_DIR, "jobs_classified.csv")

BATCH_SIZE = 20
MAX_WORKERS = 4

# Gemma RPD 1500 : Flash Lite RPD 500 = 3:1 비율
MODEL_CYCLE = [
    "gemma-4-26b-a4b-it",
    "gemma-4-26b-a4b-it",
    "gemma-4-26b-a4b-it",
    "gemini-3.1-flash-lite",
]


def _is_classified(row: dict) -> bool:
    return bool(row.get("job_type_detail", "").strip())


def _is_summarized(row: dict) -> bool:
    return bool(row.get("recommendation_reason", "").strip())


def _call_gemini(client, model_name: str, prompt: str) -> str:
    def _do():
        response = client.models.generate_content(model=model_name, contents=prompt)
        return response.text.strip()

    return retry.call_with_retry(_do, attempts=5, base_wait=60)


def _summarize_batch(client, batch: list[dict], model_name: str, batch_idx: int) -> tuple[int, list[str]]:
    items = "\n".join(
        f"{i + 1}. 제목: {row.get('title', '')}, 직종: {row.get('job_type_detail', '')}, "
        f"급여: {row.get('pay_type', '')} {row.get('pay_amount', '')}, "
        f"복지: {row.get('welfare', '')}, 지역: {row.get('region_sido', '')}"
        for i, row in enumerate(batch)
    )

    prompt = f"""다음 채용공고 각각에 대해 구직자에게 추천하는 이유를 30자 이내로 작성하세요.
설명 없이 JSON만 출력하세요.

공고 목록:
{items}

응답 형식 (JSON만):
{{"1": "추천 사유", "2": "추천 사유"}}"""

    empty_results = ["" for _ in batch]

    try:
        text = _call_gemini(client, model_name, prompt)
        if "```" in text:
            text = text.split("```")[1]
            if text.startswith("json"):
                text = text[4:]
        result_map = json.loads(text.strip())
    except Exception as e:
        print(f"  [오류:{model_name}] summary batch={batch_idx} {e} -> 건너뜀")
        return batch_idx, empty_results
    results = []
    for i in range(len(batch)):
        results.append(result_map.get(str(i + 1), "").strip())

    return batch_idx, results


def main() -> str | None:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("[ERROR] GEMINI_API_KEY 환경변수 없음", file=sys.stderr)
        sys.exit(1)
    if not os.path.exists(INPUT_CSV):
        print(f"[INFO] 입력 파일 없음: {INPUT_CSV}", file=sys.stderr)
        return "no_work"

    client = genai.Client(api_key=api_key)

    print("분류된 CSV 로딩 중...")
    fieldnames, rows = csv_io.load_rows_deduped(INPUT_CSV)
    if "recommendation_reason" not in fieldnames:
        fieldnames.append("recommendation_reason")

    summary_indices = [
        idx for idx, row in enumerate(rows)
        if _is_classified(row) and not _is_summarized(row)
    ]
    print(f"추천 사유 생성 대상: {len(summary_indices)}행")

    if not summary_indices:
        print("추천 사유 생성 완료 (또는 대상 없음).")
        return "no_work"

    summary_batches = [
        summary_indices[i:i + BATCH_SIZE]
        for i in range(0, len(summary_indices), BATCH_SIZE)
    ]

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {
            executor.submit(
                _summarize_batch,
                client,
                [rows[idx] for idx in batch_indices],
                MODEL_CYCLE[batch_idx % len(MODEL_CYCLE)],
                batch_idx,
            ): (batch_idx, batch_indices)
            for batch_idx, batch_indices in enumerate(summary_batches)
        }

        completed = 0
        for future in as_completed(futures):
            batch_idx, batch_indices = futures[future]
            _, reasons = future.result()
            for row_idx, reason in zip(batch_indices, reasons):
                if reason:
                    rows[row_idx]["recommendation_reason"] = reason

            completed += 1
            if completed % 5 == 0 or completed == len(summary_batches):
                csv_io.write_rows(INPUT_CSV, fieldnames, rows)
            print(f"  summary batch={batch_idx:4d} 완료 ({completed}/{len(summary_batches)})")

    csv_io.write_rows(INPUT_CSV, fieldnames, rows)
    print(f"\n추천 사유 생성 완료: {sum(1 for row in rows if _is_summarized(row))}건")
    print(f"출력: {INPUT_CSV}")
    return None


if __name__ == "__main__":
    IDLE_INTERVAL = 30 * 60
    while True:
        result = main()
        if result == "no_work":
            print(f"[요약기] 새 데이터 없음 — {IDLE_INTERVAL // 60}분 후 재시도")
            time.sleep(IDLE_INTERVAL)
