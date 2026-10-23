#!/usr/bin/env python3
"""Build, update and open Cat Care on an attached test device."""

import os
import subprocess
import sys

from android_sdk import MOBILE, run, sdk_path


def main():
    sdk = sdk_path()
    adb = sdk / "platform-tools" / "adb"
    if not adb.is_file():
        raise RuntimeError(f"Install Android SDK Platform-Tools in {sdk}.")
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    run([adb, "start-server"])
    devices = {}
    for line in run([adb, "devices"]).splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2:
            devices[fields[0]] = fields[1]
    serial = os.environ.get("CAT_CARE_DEVICE")
    if not serial:
        phones = [device for device in devices if not device.startswith("emulator-")]
        if not phones:
            raise RuntimeError("Connect your test phone and enable USB debugging, then try again.")
        if len(phones) > 1:
            raise RuntimeError(
                f"Multiple phones are attached: {', '.join(phones)}. "
                "Select one with CAT_CARE_DEVICE=<adb-serial>."
            )
        serial = phones[0]
    status = devices.get(serial)
    if status != "device":
        if status == "unauthorized":
            raise RuntimeError(f"Unlock {serial} and authorize USB debugging on the phone, then try again.")
        raise RuntimeError(f"Device {serial} is {status or 'not connected'}. Reconnect it and try again.")
    print(f"Building Cat Care for {serial}…", flush=True)
    run([MOBILE / "gradlew", "-p", MOBILE, "assembleDebug"], timeout=None, capture=False, env=env)
    run([adb, "-s", serial, "install", "-r", MOBILE / "app/build/outputs/apk/debug/app-debug.apk"], timeout=120, capture=False)
    run([
        adb, "-s", serial, "shell", "am", "start", "-W", "--activity-single-top",
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
        "-n", "care.cat.offline/.MainActivity",
    ], capture=False)
    print(f"Cat Care updated and opened on {serial}. Existing app data was retained.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"mobile-update-device: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        sys.exit(1)
