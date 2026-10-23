# Care scheduling, language and card UX verification

Implementation and evidence share this commit, based on `dacab1e`.

## Automated checks

- Gradle `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`
  succeeded. Seven JVM tests passed; lint reports 0 errors and 11 existing
  SDK/dependency update warnings.
- Android API 35: 24 tests exercised (13 SQLite, 9 Compose UI, 2 compact-calendar).
  The complete run passed 23 tests. The remaining comment test initially clicked
  behind the save snackbar; after explicitly advancing its animation clock,
  that test passed in a targeted rerun (5.661 seconds). Application code was
  unchanged by this test synchronization fix.
- Coverage includes schema 3 → 4 additive migration preserving observations and
  comments, lifecycle metadata, scoped export, recurrence transactions, draft
  restoration, calendar/time entry, clock-mode toggle, weekly preset, language
  switching, cat badges, observation comments and calendar selection restoration.
- Date tests cover Brazilian/US order, 12/24-hour presentation, cross-day timezone
  conversion, daylight-saving offsets, invalid local times and month-end recurrence.
- Both resource catalogs contain the same 198 keys. Persisted dates remain ISO;
  existing prose and user-entered text are never translated or rewritten.

## Visual checks

Disposable `cat-care-visual-35` emulator, Android 15/API 35, America/Sao_Paulo.
Normal viewport 1080×1920 at 420 dpi and 100% text. Narrow viewport 840×1600
at 420 dpi (320dp width) and 130% text. Inspected calendar and time actions,
recurrence preview, translated labels, timezone labels, cat badges and comments.
The compact calendar addresses clipping found in Material's fixed-width dialog.

- [Portuguese calendar](calendar-pt.png)
- [Weekly recurrence and next occurrence](repeat-pt.png)
- [Today in Portuguese](today-pt.png)
- [History badge and observation comment](history-pt.png)
- [Language selection](language-en.png)
- [Today in English](today-en.png)
- [Narrow calendar with larger text](calendar-en-narrow.png)
- [Narrow numeric time entry](time-en-narrow.png)

Screenshots contain synthetic data. API 26–32 language fallback and TalkBack
were reviewed in code but not exercised on devices in this change. Existing
legacy history prose remains in its original language; new generated entries
render from structured metadata in the selected language.

Debug APK SHA-256:
`c6542832cd4f971fd984c0dda2f604fc38d6bb0e1f67665cc522ceb8d1af64a0`.
