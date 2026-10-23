# Android theme, UTC, reminders and card UX

Implementation and evidence share this commit, based on `d095874`.

## Verification

Final build: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
Eight JVM tests passed. Lint: 0 errors and 11 existing SDK/dependency update
warnings. Both language catalogs contain the same 217 keys.

The final full instrumentation run on disposable Android 15/API 35 passed all
30 tests in 35.788 seconds: 12 Compose UI, 14 SQLite, 2 reminder integration and
2 compact-calendar tests. Coverage includes:

- Additive schema 4 → 5 upgrade preserving due instants and named recurrence zone;
  daily recurrence across New York spring DST remains 09:00 local while its UTC
  hour changes. Older supported schema upgrades and scoped/cascading data remain
  covered. Export version 3 contains UTC due values and explicit recurrence zone.
- Real NotificationManager delivery: advance notice, due notification replacing
  advance notice, duplicate suppression, rescheduling cancellation, deleted and
  cancelled tasks, disabled reminders, and skipping stale advance notice for
  overdue tasks. Tests use explicit injected instants for deterministic delivery;
  they do not assert Android's wall-clock alarm latency.
- A measurement accepts a persisted comment; sharing opens an ACTION_SEND
  text/plain chooser with only that card, its cat, local timestamps and comments.
  The test intercepts the chooser; no external message is sent.
- Persisted dark-mode selection across activity recreation; overdue summary;
  short action labels with task-specific accessibility descriptions; avatar
  targeting/filtering, calendars, recurrence, language switching and restoration.

## Manual review

Disposable `cat-care-visual-35` emulator at 1080×1920, 420 dpi, 100% text,
America/Sao_Paulo. Screenshots use synthetic cats, an overdue blood exam and
comments. Inspected light/dark status colours, right-aligned category/type,
avatar filters, one recurrence choice and Android's formatted text share sheet.
Notifications were enabled from Data and an overdue alert appeared in the system
notification shade. OS share targets depend on installed apps; WhatsApp and
Telegram were not installed in this emulator. Nothing was sent externally.

- [Dark Today and overdue summary](today-dark.png)
- [Light Today and overdue summary](today-light.png)
- [Single recurrence choice](repeat-dark.png)
- [History avatar filters and right-aligned type](history-dark.png)
- [History comments and share action](history-comments-dark.png)
- [Settings](settings-dark.png)
- [Formatted-text share sheet](share-sheet.png)
- [Local notification](notification.png)

This change did not repeat TalkBack or API 26–32 hardware acceptance. Reboot
rescheduling and wall-clock alarm delivery under Doze were reviewed in code but
not exercised by these deterministic tests. Reminders use inexact allow-while-idle
alarms; Android can delay them. The UI explains this limitation and requests
notification permission through an explicit action. Force-stopping suppresses
alarms until reopening. Birth/adoption remain date-only.

## Device delivery

`make mobile-update-device` succeeded on Moto G35 5G (`ZF5254WKJ8`), installing
with `adb install -r` and opening the app. Existing data was retained. No data-reset
instrumentation was run on that phone or the user's persistent emulator.

Debug APK SHA-256:
`9764f088cef26c20740ec0ff4e2916316abb2a6ec9fce9e64ccc6d215d12b661`.
