# Android cat cards evidence

The implementation and this evidence are in the same commit, based on `ee2fa02`.
Scope: Gatos card list, avatar/coat selection, profile form and supporting storage.

Validation on 2026-10-23:

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed.
  All 5 JVM tests passed; lint reported no issues.
- Installed the application and test APKs on the disposable read-only
  `cat-care-visual-35` emulator (`emulator-5562`) and ran the instrumentation runner.
  All 11 Android tests passed: 9 storage tests and 2 cat-card UI tests.
- Storage tests cover coat creation/editing/reopen/export, invalid-coat rollback,
  totals versus pending/overdue counts, observation-only counts, isolation across
  cats, the authorized schema 1 → 2 reset, and existing lifecycle/storage behavior.
- UI tests cover per-cat avatars/counters, hiding the pending badge after completion,
  creating/editing a cat, and restoring the name and coat draft after recreation.
- Visually inspected the list, profile form, native date picker and all eight
  coat options with the selected marker. Checked 320dp width at 130% text size.

Device: Android 15/API 35, Pixel 2 profile, America/Sao_Paulo timezone.
Normal viewport: 1080×1920 at 420 dpi, 100% text size. Narrow viewport:
840×1600 at 420 dpi, 130% text size. Data is synthetic Mimi/Luna/Nina test data.

Screenshots:

- [Cat cards](cats.png)
- [Edit profile](edit.png)
- [Native date picker](date-picker.png)
- [Eight coat choices](coats.png)
- [Narrow layout and larger text](narrow.png)

This is scoped validation, not complete release acceptance. Airplane mode was off;
TalkBack and physical devices were not tested. The existing user emulator was not
modified. The temporary emulator was stopped after capture.

As explicitly authorized, opening an existing schema-1 database with this revision
recreates cats, tasks and history under schema 2. Fresh profiles include a coat ID.
This reset is restricted to version 1 → 2; future upgrades require explicit migration.

Debug APK SHA-256: `6b7571b84a84b6008769be12b9a082dbe051e38fedf02f153a9469a52d210890`.
