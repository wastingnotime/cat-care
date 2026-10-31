# Cat Care offline Android

Native Android owner application written in Kotlin with Jetpack Compose and
Material 3. SQLite persistence, min API 26, compile/target API 36, JVM target 17.
AndroidX and Kotlin coroutines supply UI/lifecycle support. No account, server,
internet permission or veterinarian workspace.

See [the mobile technology stack](docs/technology-stack.md) for the layer-by-layer
choices and rationale, including manual injection and the offline networking boundary.

## Build and install

Install JDK 21 and Android SDK platform 36/build tools 35.0.0. Set `ANDROID_HOME`
(or use Android Studio's generated, ignored `local.properties`). Open this
folder in Android Studio or use the checked-in Gradle 8.11.1 wrapper:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.wastingnotime.catcare/.MainActivity
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

To build and update your connected test phone, run from the repository root:

```bash
make mobile-update-device
```

Enable USB debugging and authorize this computer on the phone first. The command
selects the single connected physical device, builds the debug APK, updates it
with `adb install -r`, and opens Cat Care. Existing app data is retained. If more
than one phone is connected, select its ADB serial explicitly:

```bash
make mobile-update-device CAT_CARE_DEVICE=ZF5254WKJ8
```

Emulators are ignored during automatic selection. An explicit `CAT_CARE_DEVICE`
can select any attached ADB device. SDK discovery is shared with `mobile-run`;
only Platform-Tools are required for this command, alongside the build SDK/JDK.

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
Compose UI tests reset app data and check cat cards, avatar selection, observation
comments, draft restoration, task confirmation and data scope/deletion. Storage tests also cover additive schema migration and
comment persistence, validation, scoped export and cascading deletion.
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

Hoje groups care by Overdue, Today, Upcoming (collapsed initially) and No date,
sorted by deadline. Cards open details, offer Mark done with confirmation, and put
Edit/Reschedule/Cancel in overflow. Floating actions keep planning, adding cats and
recording history available while scrolling. Gatos cards show the cat name and coat avatar and open registration details
with editing, birth and adoption dates. Care activity stays in Today and History. History has compact
date groups, cat/type/period filters and a detail screen for comments and sharing.
Full-screen editors use compact cat chips, inline validation and discard protection;
records allow past occurrence times and retain the actual entry timestamp separately.
Dados exports or deletes one cat/all local care data after a scope preview. Dates use calendar pickers and times use a numeric/clock dialog. Repetition
offers none, daily, weekly, monthly or a custom days/months interval and a next-date preview. Export is versioned JSON via
Android's document picker. Full-data version 3 files can be restored after a scope preview and replacement
confirmation. Scoped cat exports are for reference. Cloud sync is not included. Android OS backup/device transfer of app data is disabled by app
policy. Uninstall, storage clearing or device loss may permanently remove data;
exported files remain in the destination chosen by the owner.

See `contracts/mobile.md` for persistence, timezone, recurrence, export and
error semantics, and ADR 0002 for scope. Existing web/API fixtures stay separate.

The authorized legacy schema 1 reset remains for coat selection. Schema 2 → 3
adds observation comments without clearing existing data. JSON export version 3
includes comments with their history-entry IDs under the retained `observation_id` field. Future upgrades require explicit migrations.

## Kotlin and Compose structure

Application and test sources live under `app/src/{main,test,androidTest}/kotlin`;
the instrumentation document provider and grant activity are Java fixtures in
`app/src/androidTest/java` so they can run in the test APK’s own process.
`MainActivity` hosts Compose; `CatCareApp` renders screens and editors; `CatArtwork`
draws avatars and onboarding art with Compose Canvas. `CareViewModel` exposes
immutable snapshots through StateFlow, runs SQLite work on the IO dispatcher, and
keeps form fields, target IDs, selected cats and navigation in SavedStateHandle.
`CareStore` retains the same database filename, atomic mutations and JSON export
version 3. Schema 4 added nullable metadata for localized automatic history;
upgrading schema 3 preserves all existing cats, care records and comments.

The build pins Kotlin/compiler plugin 2.2.21, Compose BOM 2025.04.01, Activity
1.10.1 and Lifecycle 2.9.0 alongside the existing AGP/Gradle toolchain.
These are compatible stable dependencies rather than a simultaneous SDK upgrade.
See [ADR 0003](../../docs/adr/0003-kotlin-compose-android.md) for the migration decision.

## Language and date/time display

Choose Português (Brasil), English (US), or the device language in Settings
under the ellipsis menu.
Android 13+ also supports the app-language setting in Android Settings. Both
translations are bundled for offline use. App text and new automatic history
follow the selected language; owner-entered text and old history prose are kept
exactly as recorded.

Dates use the selected locale’s order and times follow the device’s 12/24-hour
setting. Due dates, history and recurrence previews show device-local date/time without
a zone ID or UTC suffix. Scheduling retains its stored zone; profile dates have
no timezone conversion. Care and history cards identify their cat with an
avatar/name badge. All history records offer comment threads.

Scheduling and localization verification: [evidence](../../runs/android-care-ux-2026-10-23/evidence.md).

Settings offers system/light/dark appearance and local notification settings.
Reminders default to one hour before and at the due time; allow notification
permission using the button in Settings. Android may delay alarms to save battery.
History cat filters have avatars, every card accepts comments, and Share opens
Android’s chooser with formatted text for that card and its replies.

SQLite schema 5 upgrades existing zone-aware deadlines to UTC instants without
changing their meaning. The named recurrence zone remains separate so routines
keep their local wall time across DST. Editors and cards display device-local
datetime. JSON export version 3 carries UTC deadlines and the recurrence zone.

Theme, reminders, UTC storage and sharing verification: [evidence](../../runs/android-reminders-theme-2026-10-23/evidence.md).


Imported historical records may retain a reported calendar date without a known
time. Optional date-precision metadata makes cards and shared text display only
that date, with an explicit “time not provided” label; the stored UTC instant is
then a chronological anchor. Ordinary entries retain their full local timestamp.

## Navigation, backups and privacy

Three labeled tabs open Today, Cats and History; wider windows use a navigation
rail. The compact header identifies the screen. Child screens have an Up action,
and Back restores the originating screen and its list state. The ellipsis menu
opens Data, Settings and About. Settings contains language, appearance and reminders.
About describes Wasting No Time and the app’s actual local-data privacy behavior.

Reminders offer setup after the first dated plan and open the notified task.
An unfinished editor is protected if a notification is opened during editing.
See [ADR 0005](../../docs/adr/0005-daily-care-ux.md) and
[UX validation](../../runs/android-daily-ux-2026-10-23/evidence.md).
Data retains scoped export and deletion and adds backup/restore. Choose a folder
to enable a first backup and roughly daily automatic saving through Android’s
persisted JobScheduler. Android can defer background execution. Manual backup
uses the same folder, and the seven newest completed app backups are kept.
Disabling automatic saving leaves existing files. Folder access and settings
belong to the installation and are not restored from a file. OS device backup
and device transfer remain disabled.

Restore accepts full-data `cat-care-offline` version 3 JSON up to 16 MiB, validates
all rows and relationships, previews counts and requires confirmation to replace
local care data atomically. Invalid files and failed writes leave existing data
intact. Backups/exports can contain private information, and a selected document
provider may sync them. Deleting app data does not delete those external files.

Navigation and backup verification: [evidence](../../runs/android-menu-backup-2026-10-23/evidence.md).

## WNT application identity

Namespace and Play package: `org.wastingnotime.catcare`. The initial
`care.cat.offline` internal test is a separate app installation. Export full data
from that installation and restore it in the WNT app to transfer existing records.
