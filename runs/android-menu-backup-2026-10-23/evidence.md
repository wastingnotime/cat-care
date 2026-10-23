# Mobile navigation, local dates and backup validation

Date: 2026-10-23. Device: disposable API 35 `cat-care-offline-35` emulator,
started with `-read-only -no-snapshot`; airplane mode enabled, Wi-Fi/data off.
The user’s phone and persistent emulator data were not used for instrumentation.

## Delivered behavior

- Three icon-only tabs, accessible names, activity title and ellipsis menu for
  Data, Settings and About. Today places “Care for today.” before the local date.
- Local date/time presentation without UTC offsets or zone IDs, retaining UTC
  persistence and zone-aware recurrence.
- Data retains export/delete and adds user-selected daily backups, manual backup,
  seven-copy retention and confirmed full-data restore.
- Language, appearance and reminders live in Settings. About includes WNT
  attribution and localized privacy explanations matching the shipped behavior.

## Automated verification

`apps/mobile/gradlew -p apps/mobile testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest`
passed: 8 JVM tests, 38 Android tests, lint and debug APK assembly.

New/updated tests cover:

- Three accessible icon tabs, overflow destinations, settings separation,
  local dates, localization, sharing without UTC/zone suffixes and theme recreation.
- Full backup round trip across database reopen, preserving IDs, comments,
  recurrence zones and timestamps.
- Rejection of scoped/malformed/unknown-version/missing-array/duplicate-ID/
  orphaned/invalid-date/diagnosis snapshots without changing existing care data.
- Transaction rollback after an injected insertion failure during replacement.
- Restore count preview, cancellation and confirmation across activity recreation.
- Actual ContentResolver streams against a disposable DocumentsProvider, grants
  persisted through the production configure path, capability probes, restorable
  copies, seven-copy retention and unrelated-file preservation.
- Actual JobScheduler service execution forced through Android’s test command;
  persisted periodic scheduling and cancellation when backups are disabled.
- A failed document rename preserves the last successful copy/timestamp, reports
  failure and cleans the unfinished document.

The standalone test document provider/grant activity are Java fixtures because
Android launches the test APK in its own process without the target Kotlin runtime.
They are absent from the application APK. An initial obsolete language-navigation
expectation and the test-provider setup were corrected before the passing run.

## Visual verification

Installed the debug APK on the same disposable emulator and inspected these
rendered screens at 1080×1920:

- [Today](today.png)
- [Overflow menu](menu.png)
- [Data](data.png)
- [Settings](settings.png)
- [About and privacy](about.png)

Headers and three icon-only tabs are legible; long Data/Settings/About content
scrolls above the fixed navigation. Both resource languages are exercised by
instrumentation. This pass did not repeat TalkBack/hardware/API 26 checks or wait
24 hours for a naturally scheduled backup. Android controls background timing;
the app explains delays. System-picker/provider behavior outside the disposable
test provider was not exhaustively checked. No production release is claimed.

Debug APK SHA-256: `ea9691d3512f78076ee6b714636c407209b7d605d98bee61079d3e9a7c169ccf`.
