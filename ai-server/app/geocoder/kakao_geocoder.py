import os
import sys

import requests


_DEBUG: bool = os.getenv("GEOCODER_DEBUG", "").lower() in ("1", "true")


def _log(msg: str) -> None:
    if _DEBUG:
        print(f"[GEOCODER] {msg}", file=sys.stderr)


def geocode(region: str) -> tuple[float, float, str, str] | None:
    key = os.getenv("KAKAO_REST_API_KEY")
    if not key:
        _log("REST API 키 미설정 (KAKAO_REST_API_KEY)")
        return None

    try:
        response = requests.get(
            "https://dapi.kakao.com/v2/local/search/address.json",
            params={"query": region, "size": 1},
            headers={"Authorization": f"KakaoAK {key}"},
            timeout=5,
        )
        response.raise_for_status()
        documents = response.json().get("documents", [])
        if not documents:
            _log(f'검색 결과 없음 — region="{region}"')
            return None
        doc = documents[0]
        addr = doc.get("address") or {}
        sido = addr.get("region_1depth_name", "")
        sigungu = addr.get("region_2depth_name", "")
        return float(doc["y"]), float(doc["x"]), sido, sigungu
    except requests.HTTPError as e:
        _log(f'HTTP {e.response.status_code} — region="{region}"')
        return None
    except requests.RequestException as e:
        _log(f'네트워크 오류: {e} — region="{region}"')
        return None
    except (KeyError, IndexError, ValueError, TypeError) as e:
        _log(f'응답 파싱 실패: {e} — region="{region}"')
        return None
