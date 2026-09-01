from __future__ import annotations

import os
import signal
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[3]


def wait_until_ready(url: str, process: subprocess.Popen[bytes], timeout: float = 15) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"process exited with status {process.returncode} before {url} was ready")
        try:
            with urllib.request.urlopen(url, timeout=0.5) as response:
                if response.status == 200:
                    return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError(f"timed out waiting for {url}")


def main() -> int:
    environment = os.environ.copy()
    api_address = environment.get("CAT_CARE_API_ADDR", "127.0.0.1:8080")
    api_port = api_address.rsplit(":", 1)[-1]
    web_port = environment.get("CAT_CARE_WEB_PORT", "5173")
    environment["CAT_CARE_API_ADDR"] = api_address
    environment.setdefault("CAT_CARE_API_URL", f"http://127.0.0.1:{api_port}")

    api = subprocess.Popen(
        [
            "go",
            "run",
            "./cmd/api",
        ],
        cwd=REPOSITORY / "apps" / "api",
        env=environment,
    )
    web = subprocess.Popen(
        [
            "npm",
            "run",
            "dev",
            "--",
            "--host",
            "127.0.0.1",
            "--port",
            web_port,
        ],
        cwd=REPOSITORY / "apps" / "web",
        env=environment,
    )
    processes = (api, web)

    def stop(_signum: int | None = None, _frame: object | None = None) -> None:
        for process in processes:
            if process.poll() is None:
                process.terminate()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    try:
        wait_until_ready(f"http://127.0.0.1:{api_port}/healthz", api)
        wait_until_ready(f"http://127.0.0.1:{web_port}/", web)
        print(f"Cat Care is ready: http://127.0.0.1:{web_port}", flush=True)
        while all(process.poll() is None for process in processes):
            time.sleep(0.25)
        return next((process.returncode for process in processes if process.returncode), 1)
    except (KeyboardInterrupt, RuntimeError) as error:
        if isinstance(error, RuntimeError):
            print(error, file=sys.stderr)
        return 0 if isinstance(error, KeyboardInterrupt) else 1
    finally:
        stop()
        for process in processes:
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()


if __name__ == "__main__":
    raise SystemExit(main())
