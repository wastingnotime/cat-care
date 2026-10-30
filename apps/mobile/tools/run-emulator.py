#!/usr/bin/env python3
"""Build and launch Cat Care on an existing or newly started Android emulator."""

import os
import subprocess
import sys
import time

from android_sdk import MOBILE, run, sdk_path


def emulators(adb):
    result = []
    for line in run([adb, "devices"]).splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[0].startswith("emulator-") and fields[1] in ("device", "offline"):
            result.append(fields[0])
    return sorted(result)


def avd_name(adb, serial):
    try:
        return run([adb, "-s", serial, "emu", "avd", "name"], timeout=5).splitlines()[0].strip()
    except (subprocess.SubprocessError, IndexError):
        return None


def main():
    sdk = sdk_path()
    adb = sdk / "platform-tools" / "adb"
    emulator = sdk / "emulator" / "emulator"
    if not adb.is_file() or not emulator.is_file():
        raise RuntimeError(f"Install Android SDK Platform-Tools and Emulator in {sdk}.")
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    requested = os.environ.get("CAT_CARE_AVD")
    timeout = int(os.environ.get("CAT_CARE_EMULATOR_TIMEOUT", "180"))
    if timeout <= 0:
        raise RuntimeError("CAT_CARE_EMULATOR_TIMEOUT must be a positive number of seconds.")
    run([adb, "start-server"])
    running = emulators(adb)
    serial = next((item for item in running if not requested or avd_name(adb, item) == requested), None)
    process = None
    log_path = MOBILE / ".local" / "emulator.log"
    if serial:
        print(f"Using running emulator {serial}.", flush=True)
    else:
        available = sorted(filter(None, (line.strip() for line in run([emulator, "-list-avds"], env=env).splitlines())))
        if not available:
            raise RuntimeError("No Android virtual device exists. Create an API 26+ device in Android Studio Device Manager first.")
        name = requested or ("cat-care-offline-35" if "cat-care-offline-35" in available else available[0])
        if name not in available:
            raise RuntimeError(f"Unknown AVD {name!r}. Available: {', '.join(available)}")
        print(f"Starting emulator {name}. Log: {log_path}", flush=True)
        log_path.parent.mkdir(parents=True, exist_ok=True)
        command = [emulator, "-avd", name, "-no-snapshot-load"]
        if os.environ.get("CAT_CARE_EMULATOR_HEADLESS") == "1":
            command += ["-no-window", "-no-audio", "-gpu", "swiftshader"]
        with log_path.open("w") as log:
            process = subprocess.Popen([str(part) for part in command], stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError(f"Emulator exited during startup. See {log_path}.")
            serial = next((item for item in emulators(adb) if item not in running and avd_name(adb, item) == name), None)
            if serial:
                break
            time.sleep(1)
        if not serial:
            raise RuntimeError(f"Emulator did not connect within {timeout}s. See {log_path}.")
    print(f"Waiting for {serial} to finish booting…", flush=True)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError(f"Emulator exited during boot. See {log_path}.")
        try:
            if run([adb, "-s", serial, "shell", "getprop", "sys.boot_completed"], timeout=5).strip() == "1":
                break
        except subprocess.SubprocessError:
            pass
        time.sleep(1)
    else:
        raise RuntimeError(f"Emulator did not finish booting within {timeout}s. Check it and try again.")
    run([MOBILE / "gradlew", "-p", MOBILE, "assembleDebug"], timeout=None, capture=False, env=env)
    run([adb, "-s", serial, "install", "-r", MOBILE / "app/build/outputs/apk/debug/app-debug.apk"], timeout=120, capture=False)
    run([adb, "-s", serial, "shell", "am", "start", "-W", "-n", "org.wastingnotime.catcare/.MainActivity"], capture=False)
    print(f"Cat Care launched on {serial}. The emulator stays running.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"mobile-run: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        sys.exit(1)
