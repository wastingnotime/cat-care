# Kotlin and Compose migration evidence

Implementation and evidence share this commit, based on `117c6f8`.
Scope: migrate all Android application and test sources from Java to Kotlin,
replace Android View screens/artwork with Compose, and retain offline care semantics.

## Automated validation

On 2026-10-23, the final implementation passed:

```sh
./apps/mobile/gradlew -p apps/mobile testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
adb -s emulator-5562 shell am instrument -w care.cat.offline.test/androidx.test.runner.AndroidJUnitRunner
```

- All 5 Kotlin JVM tests passed (dates, timezone/DST, recurrence and deferral).
- All 18 Android tests passed: 12 Kotlin SQLite tests and 6 Compose UI tests.
  The final instrumentation run completed in 18.59 seconds with no failures.
- Storage coverage retains profile/coat persistence, summary counts, authorized
  legacy reset, additive migration, rollback, stale state, atomic recurrence,
  record/comment validation, scoped export and cascading deletion.
- Compose coverage includes avatar targeting, explicit versus sole-cat selection,
  record/comment drafts after activity recreation, coat editing/restoration,
  task completion confirmation, export scope preview and deletion confirmation.
- Lint reports 0 errors and 11 update warnings: the retained target SDK and newer
  versions available for the intentionally pinned SDK-35-compatible dependencies.
- APK manifest inspection confirms no internet permission. Application and test
  sources contain no `.java` files or custom Android View rendering.

## Java-to-Kotlin data compatibility

The prior Java debug APK was obtained read-only from the existing emulator. Its
SHA-256 matches the previous delivery:
`08da6239ae778760a1e2d204f3350687b63e346cef36e75ead7fe9e63d9bdec2`.
Only the disposable emulator was changed.

Opened synthetic data in that Java APK and added the comment “Escrito na versao
Java.” Then stopped the app, copied the SQLite database, installed the Compose
APK with `adb install -r`, launched it and copied the database again. Comparing
`SELECT * ... ORDER BY id` for every care table yielded identical values and IDs:

| Schema/table | Before and after |
| --- | --- |
| SQLite user_version | 3 |
| cats | 2 |
| tasks | 2 |
| history | 5 |
| observation_comments | 1 |

No schema upgrade, reset or row rewrite occurred. The Java-created observation
comment is visible in the Compose history screenshot.

## Manual checks

- Entered a new-plan title and selected Mimi, backgrounded the app, waited for
  the launcher to become idle, killed the background process with `am kill`, and
  restored the existing task through Recents. The title “Rascunho Kotlin” and
  selected avatar survived. This uses task restoration rather than starting a
  second activity or force-stopping the app.
- Used the Compose date picker to set Mimi's birth date to 23/10/2026 and saved.
- Exported Mimi through the system document picker to local Downloads while
  offline. Parsed JSON verifies format/version 2, one cat, birth `2026-10-23`,
  tasks/history belonging only to that cat, and the comment referencing its
  observation. Other cats were excluded. The UI showed export success.
- Inspected normal layouts and 320dp width at 130% font size. Avatar choices
  become one column; forms scroll and keep save/back actions available.
- Final visual adjustments align Material containers with the cream/green
  palette and add spacing between form actions. Some screenshots precede these
  small styling adjustments; the date-picker screenshot uses the final palette.

Device: disposable read-only `cat-care-visual-35`, Android 15/API 35, Pixel 2,
America/Sao_Paulo, system locale English. App labels are PT-BR; platform/Material
calendar and document-picker strings follow the device locale. Airplane mode
was enabled; Wi-Fi and mobile data disabled. Normal viewport: 1080×1920, 420 dpi,
100% text. Narrow viewport: 840×1600, 420 dpi, 130% text.

Screenshots:

- [Hoje](today.png)
- [Avatar selection in planning](plan.png)
- [Cat cards](cats.png)
- [Profile and selected date](profile.png)
- [Compose date picker](date-picker.png)
- [Observation with Java-created comment](history.png)
- [Draft restored after process death](process-restored-draft.png)
- [Narrow avatar form](narrow-plan.png)
- [Narrow comment form](narrow-comment.png)

This is scoped migration validation, not full release acceptance. Physical
hardware, TalkBack and API 26 were not exercised. The existing emulator was not
modified. The disposable emulator was stopped after validation.

Final debug APK SHA-256:
`4388e7a555971afc22897b124baeb17b3767d1c2ebc31c2195cdc1230389c89b`.
