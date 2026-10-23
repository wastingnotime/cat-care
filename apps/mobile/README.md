# Cat Care offline Android

Native Android owner application, Java 17 language level and Android views,
SQLite persistence, min API 26, compile/target API 35. No runtime third-party
libraries, account, server, internet permission or veterinarian workspace.

## Build and install

Install JDK 21 and Android SDK platform 35/build tools 35.0.0. Set `ANDROID_HOME`
(or use Android Studio's generated, ignored `local.properties`). Open this
folder in Android Studio or use the checked-in Gradle 8.11.1 wrapper:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n care.cat.offline/.MainActivity
```

Root shortcuts: `make mobile-test`, `make mobile-build`, `make mobile-apk`.
Run `make mobile-run` from the repository root to reuse a running emulator or
start one, wait for boot, build/install the debug APK and launch Cat Care. The
emulator stays running, and installation preserves existing app data. Python 3,
the SDK Emulator/Platform-Tools and an existing API 26+ AVD are required. Create
an AVD in Android Studio Device Manager if needed. SDK discovery uses
`ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties`, then `~/Android/Sdk`.

To choose a device, use `CAT_CARE_AVD=my-device make mobile-run`. Without an
override, the command reuses the first running emulator; otherwise it prefers
`cat-care-offline-35`, then the first available AVD. Physical devices are ignored.
For a headless session use `CAT_CARE_EMULATOR_HEADLESS=1 make mobile-run`.
`CAT_CARE_EMULATOR_TIMEOUT` sets the startup/boot timeout in seconds (default 180).
Startup logs are saved in the ignored `apps/mobile/.local/emulator.log`.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; it is for local
installation, not a signed production release. Dependencies and SDK downloads
require internet during development; installed core app workflows work offline.
AGP 8.9.2 uses Gradle 8.11.1 (see
[Android compatibility notes](https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes)).

## Device checks

With a disposable API 26+ emulator or test device connected, enable airplane
mode, disable Wi-Fi and mobile data, and run:

```bash
./gradlew connectedDebugAndroidTest
```

Use a device with no valuable Cat Care data: Gradle installs and uninstalls the
target app during instrumentation, which can clear its existing local data.
Store tests use a separate disposable database and Android's real SQLite engine.
Cat-card UI tests reset app data and check cards, editing and coat draft restoration.
They cover persistence after close/reopen, atomic recurrence completion, stale
writes, invalid create rollback, scoped export, direct records, deferral,
cancellation and cascading deletion. JVM tests cover dates, DST, recurrence,
status and deferral validation. CI runs JVM tests, lint, debug assembly and API
35 instrumentation before uploading the APK.

Use `contracts/acceptance/android-owner-offline.md` for manual scenarios. Check
first launch in airplane mode, native document-picker export to local Downloads,
force-stop/relaunch and device reboot, small/large font, TalkBack and rotation
with unsaved forms. No simulator account or external API is needed.

## Data and current limits

Hoje shows planned tasks across cats; Gatos lists cards for every cat, with the
chosen coat avatar, pending status, responsibility/observation counters and an
edit button. Create/edit asks for name, optional birth/adoption dates and closest
coat. Histórico shows the selected cat's records and lifecycle; Dados exports or
deletes one cat/all local care data after a scope preview. Dates are entered as
`dd/mm/aaaa` and due times as `dd/mm/aaaa hh:mm`. Export is versioned JSON via
Android's document picker. No import/restore, automatic reminders or cloud sync
is included. Automatic backup/device transfer of app data is disabled by app
policy. Uninstall, storage clearing or device loss may permanently remove data;
exported files remain in the destination chosen by the owner.

See `contracts/mobile.md` for persistence, timezone, recurrence, export and
error semantics, and ADR 0002 for scope. Existing web/API fixtures stay separate.

For this development revision, the authorized schema 1 → 2 upgrade recreates the
local database to add coat selection, clearing existing cats and care records.
Future schema upgrades require explicit migrations.
