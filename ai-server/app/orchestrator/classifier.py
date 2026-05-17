"""
classifier.py
data/jobs_geocoded.csv를 AI 직종 분류로 보강해 data/jobs_classified.csv를 출력한다.

실행 방법 (ai-server/ 루트에서):
  python -m app.orchestrator.classifier
"""

import csv
import json
import os
import shutil
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from dotenv import load_dotenv

# ai-server/.env — 실행 cwd와 무관하게 로드
_AI_SERVER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
load_dotenv(os.path.join(_AI_SERVER_ROOT, ".env"))

from google import genai

DATA_DIR = os.path.join(_AI_SERVER_ROOT, "data")
INPUT_CSV = os.path.join(DATA_DIR, "jobs_geocoded.csv")
SNAPSHOT_CSV = os.path.join(DATA_DIR, "jobs_geocoded_snapshot.csv")
OUTPUT_CSV = os.path.join(DATA_DIR, "jobs_classified.csv")
KSCO_JSON = os.path.join(_AI_SERVER_ROOT, "resources", "ksco_2025_reverse.json")

BATCH_SIZE = 20
MAX_WORKERS = 4

# Gemma RPD 1500 : Flash Lite RPD 500 = 3:1 비율
MODEL_CYCLE = [
    "gemma-4-26b-a4b-it",
    "gemma-4-26b-a4b-it",
    "gemma-4-26b-a4b-it",
    "gemini-3.1-flash-lite",
]

NEW_COLS = ["job_type_major", "job_type_mid", "job_type_minor", "job_type_detail"]


def _load_ksco() -> dict:
    with open(KSCO_JSON, encoding="utf-8") as f:
        return json.load(f)


def _load_input() -> tuple[list[str], list[dict]]:
    with open(SNAPSHOT_CSV, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        fieldnames = list(reader.fieldnames or [])
        rows = []
        for row in reader:
            try:
                rows.append(dict(row))
            except Exception:
                pass
    return fieldnames, rows


def _is_classified(row: dict) -> bool:
    return bool(row.get("job_type_detail", "").strip())


def _load_existing_results() -> dict[str, dict]:
    """external_url → row (job_type_detail이 있는 행만)"""
    if not os.path.exists(OUTPUT_CSV):
        return {}

    result = {}
    with open(OUTPUT_CSV, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                url = row.get("external_url", "").strip()
                if url and row.get("job_type_detail", "").strip():
                    result[url] = dict(row)
            except Exception:
                pass
    return result


def _empty_classification() -> dict:
    return {
        "job_type_detail": "",
        "job_type_minor": "",
        "job_type_mid": "",
        "job_type_major": "",
    }


def _classify_batch(client, ksco: dict, batch: list[dict], model_name: str, batch_idx: int) -> tuple[int, list[dict]]:
    names_str = json.dumps(list(ksco.keys()), ensure_ascii=False)
    items = "\n".join(
        f"{i + 1}. 제목: {row.get('title', '')}, 직종: {row.get('job_type', '')}"
        for i, row in enumerate(batch)
    )

    prompt = f"""다음 채용공고 목록의 직업을 아래 세분류 목록 중 가장 적합한 것 하나씩 골라 JSON으로만 반환하세요.
반드시 목록에 있는 이름 그대로 사용하고, 설명 없이 JSON만 출력하세요.

세분류 목록:
{names_str}

공고 목록:
{items}

응답 형식 (JSON만):
{{"1": "세분류명", "2": "세분류명"}}"""

    empty_results = [_empty_classification() for _ in batch]

    for attempt in range(5):
        try:
            response = client.models.generate_content(model=model_name, contents=prompt)
            text = response.text.strip()
            if "```" in text:
                text = text.split("```")[1]
                if text.startswith("json"):
                    text = text[4:]
            result_map = json.loads(text.strip())
            break
        except Exception as e:
            err = str(e)
            if "429" in err or "rate_limit" in err.lower() or "quota" in err.lower():
                wait = 60 * (attempt + 1)
                print(f"  [RateLimit:{model_name}] {wait}초 대기 ({attempt + 1}/5)...")
                time.sleep(wait)
            else:
                print(f"  [오류:{model_name}] batch={batch_idx} {e} — 건너뜀")
                return batch_idx, empty_results
    else:
        print(f"  [오류:{model_name}] batch={batch_idx} 최대 재시도 초과 — 건너뜀")
        return batch_idx, empty_results

    results = []
    for i in range(len(batch)):
        detail = result_map.get(str(i + 1), "").strip()
        if detail and detail in ksco:
            parent = ksco[detail]
            results.append({
                "job_type_detail": detail,
                "job_type_minor": parent["소분류"],
                "job_type_mid": parent["중분류"],
                "job_type_major": parent["대분류"],
            })
        else:
            results.append({
                "job_type_detail": detail,
                "job_type_minor": "",
                "job_type_mid": "",
                "job_type_major": "",
            })

    return batch_idx, results


def _write_output(fieldnames: list[str], rows: list[dict]) -> None:
    os.makedirs(os.path.dirname(OUTPUT_CSV), exist_ok=True)
    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def main() -> str | None:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("[ERROR] GEMINI_API_KEY 환경변수 없음", file=sys.stderr)
        sys.exit(1)
    if not os.path.exists(INPUT_CSV):
        print(f"[INFO] 입력 파일 없음, 대기 중: {INPUT_CSV}", file=sys.stderr)
        return "no_work"
    if os.path.exists(SNAPSHOT_CSV):
        os.remove(SNAPSHOT_CSV)
    shutil.copy2(INPUT_CSV, SNAPSHOT_CSV)
    if not os.path.exists(KSCO_JSON):
        print(f"[ERROR] KSCO 파일 없음: {KSCO_JSON}", file=sys.stderr)
        sys.exit(1)

    client = genai.Client(api_key=api_key)
    ksco = _load_ksco()

    print("입력 CSV 로딩 중...")
    fieldnames_in, input_rows = _load_input()
    fieldnames = fieldnames_in + [col for col in NEW_COLS if col not in fieldnames_in]
    total_rows = len(input_rows)

    existing = _load_existing_results()
    rows = []
    for row in input_rows:
        url = row.get("external_url", "").strip()
        if url and url in existing:
            merged = {**row, **{col: existing[url].get(col, "") for col in NEW_COLS}}
        else:
            merged = {**row, **{col: row.get(col, "") for col in NEW_COLS}}
        rows.append(merged)

    pending_indices = [idx for idx, row in enumerate(rows) if not _is_classified(row)]
    done_count = total_rows - len(pending_indices)
    print(f"전체: {total_rows}행 / 완료: {done_count}행 / 미완료: {len(pending_indices)}행")

    if not pending_indices:
        print("모든 행 분류 완료.")
        return "no_work"

    batches = [
        pending_indices[i:i + BATCH_SIZE]
        for i in range(0, len(pending_indices), BATCH_SIZE)
    ]

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {
            executor.submit(
                _classify_batch,
                client,
                ksco,
                [rows[idx] for idx in batch_indices],
                MODEL_CYCLE[batch_idx % len(MODEL_CYCLE)],
                batch_idx,
            ): (batch_idx, batch_indices)
            for batch_idx, batch_indices in enumerate(batches)
        }

        completed = 0
        success_total = done_count
        for future in as_completed(futures):
            batch_idx, batch_indices = futures[future]
            _, results = future.result()
            for row_idx, cls in zip(batch_indices, results):
                if cls.get("job_type_detail"):
                    rows[row_idx].update(cls)
                    success_total += 1

            completed += 1
            if completed % 5 == 0 or completed == len(batches):
                _write_output(fieldnames, rows)
            print(f"  batch={batch_idx:4d} 완료 ({completed}/{len(batches)}) 누적성공={success_total}")

    _write_output(fieldnames, rows)
    print(f"\n완료: {total_rows}행, 분류 성공 {sum(1 for row in rows if _is_classified(row))}건")
    print(f"출력: {OUTPUT_CSV}")
    return None


if __name__ == "__main__":
    IDLE_INTERVAL = 30 * 60
    while True:
        result = main()
        if result == "no_work":
            print(f"[분류기] 새 데이터 없음 — {IDLE_INTERVAL // 60}분 후 재시도")
            time.sleep(IDLE_INTERVAL)
