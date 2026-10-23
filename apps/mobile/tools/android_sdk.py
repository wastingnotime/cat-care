"""Android SDK discovery and process helpers shared by local device commands."""

import os
from pathlib import Path
import subprocess

MOBILE = Path(__file__).resolve().parents[1]


def run(command, *, timeout=30, capture=True, env=None):
    return subprocess.run(
        [str(part) for part in command], check=True, timeout=timeout,
        text=True, stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None, env=env,
    ).stdout


def sdk_path():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key]).expanduser().resolve()
    properties = MOBILE / "local.properties"
    if properties.exists():
        for line in properties.read_text().splitlines():
            if line.strip().startswith("sdk.dir="):
                return Path(line.strip().split("=", 1)[1].replace("\\ ", " ").replace("\\:", ":").replace("\\\\", "\\")).expanduser().resolve()
    default = Path.home() / "Android" / "Sdk"
    if default.is_dir():
        return default
    raise RuntimeError("Set ANDROID_HOME or sdk.dir in apps/mobile/local.properties to your Android SDK.")

