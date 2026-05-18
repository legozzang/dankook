"""
supervisor.py
5개 stage를 독립 subprocess로 병렬 실행한다.
각 stage는 자체 while True 루프로 동작하며, supervisor는 프로세스를 감시한다.

실행 방법 (ai-server/ 루트에서):
  python -m app.orchestrator.supervisor
"""
import os
import subprocess
import sys
import time

_AI_SERVER_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

STAGES = [
    "app.orchestrator.stages.crawl",
    "app.orchestrator.stages.geocode",
    "app.orchestrator.stages.classify",
    "app.orchestrator.stages.summarize",
    "app.orchestrator.stages.upload",
]

RESTART_DELAY = 5


def _start(module: str) -> subprocess.Popen:
    print(f"[supervisor] 시작: {module}")
    return subprocess.Popen([sys.executable, "-m", module], cwd=_AI_SERVER_ROOT)


def run():
    procs: dict[str, subprocess.Popen] = {m: _start(m) for m in STAGES}
    try:
        while True:
            time.sleep(10)
            for module, proc in list(procs.items()):
                if proc.poll() is not None:
                    print(f"[supervisor] {module} 종료됨 (코드={proc.returncode}), {RESTART_DELAY}초 후 재시작")
                    time.sleep(RESTART_DELAY)
                    procs[module] = _start(module)
    except KeyboardInterrupt:
        print("\n[supervisor] 종료 중...")
        for proc in procs.values():
            proc.terminate()
        for proc in procs.values():
            proc.wait()
        print("[supervisor] 모든 stage 종료 완료")


if __name__ == "__main__":
    run()
