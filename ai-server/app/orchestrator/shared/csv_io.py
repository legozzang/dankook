"""공통 CSV 입출력 유틸"""
import csv
import os


def load_seen_urls(csv_path: str, url_col: str = "external_url") -> set[str]:
    if not os.path.exists(csv_path):
        return set()
    with open(csv_path, "r", newline="", encoding="utf-8") as f:
        return {row[url_col] for row in csv.DictReader(f) if row.get(url_col)}


def append_row(csv_path: str, row: dict) -> None:
    os.makedirs(os.path.dirname(csv_path) or ".", exist_ok=True)
    write_header = not os.path.exists(csv_path)
    with open(csv_path, "a", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(row.keys()), extrasaction="ignore")
        if write_header:
            writer.writeheader()
        writer.writerow(row)


def load_rows_deduped(
    csv_path: str, url_col: str = "external_url"
) -> tuple[list[str], list[dict]]:
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        fieldnames = list(reader.fieldnames or [])
        seen: set[str] = set()
        rows: list[dict] = []
        total = 0
        for row in reader:
            total += 1
            row = dict(row)
            url = row.get(url_col, "").strip()
            if url and url in seen:
                continue
            if url:
                seen.add(url)
            rows.append(row)
    if total != len(rows):
        print(f"[csv_io] dedup: {total}행 -> {len(rows)}행 (중복 {total - len(rows)}건 제거)")
    return fieldnames, rows


def write_rows(csv_path: str, fieldnames: list[str], rows: list[dict]) -> None:
    os.makedirs(os.path.dirname(csv_path) or ".", exist_ok=True)
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
