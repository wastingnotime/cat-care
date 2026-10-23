# Offline Android implementation evidence

Date: 2026-10-22 (America/Sao_Paulo; execution continued into October 23 UTC).
Source: `a97836c` (`feat: implement offline owner-only Android care app`).
Branch: `docs/offline-owner-android`. WNT-145 branch remains at `73d2aeb`.

## Build and automated checks

JDK 21.0.12.1, Gradle 8.11.1, AGP 8.9.2, Android SDK/platform 35, build tools
35.0.0. Emulator: Pixel 2 profile, 1080x1920 at 420 dpi, Android 15/API 35,
Google APIs x86_64 image revision 9, KVM, headless SwiftShader rendering.

Final command (with `ANDROID_HOME` pointing to the local SDK):

```bash
apps/mobile/gradlew -p apps/mobile testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
```

Result: successful. Five JVM tests and six Android SQLite tests passed; lint
reported no issues. See [test summary](test-summary.json) for individual cases.
The SQLite suite includes an injected failure when writing successor history:
completion, successor insertion and history all rolled back, then retry succeeded.
It also verifies close/reopen, cat isolation, stale writes, duplicate completion,
scoped export/deletion and delete-all persistence. Tests use disposable databases;
Gradle's instrumentation installation/uninstallation also clears the target app.

Debug APK: `apps/mobile/app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `73544cab988284562cdd42959ab1728261b99d23da1f8a6b9fcc88f1c2d5b68c`.
The APK is a local build artifact, not a production signing/release claim. CI is
configured but was not run remotely in this session.

## Offline walkthrough

Airplane mode was enabled, Wi-Fi and mobile data disabled before testing. After
reboot, `airplane_mode_on=1`, `wifi_on=0`, API level 35. App manifest has no
requested permissions, including no INTERNET permission. No server ran for these
checks. Fixture: synthetic owner data, Mimi and Luna; device timezone
America/Sao_Paulo, actual device clock October 22, 2026.

Observed through native UI actions:

1. First launch showed owner onboarding with no account or network requirement.
2. Created Mimi and Luna; selected Mimi.
3. Created veterinary-category `Consulta`, January 31, 2027 at 09:00, monthly
   recurrence. Rotated portrait -> landscape -> portrait before saving: title,
   category, deadline and recurrence draft survived.
4. Confirmed completion: one planned successor appeared February 28 at 09:00;
   the completed record remained in history.
5. Recorded `Peso 4.2 kg` as Medição. It appeared in Mimi's history without
   completing the successor. Created Luna's undated `Agua` task. Hoje showed
   both identities and distinguished the undated scheduling state.
6. Dados preview named Mimi. Android's document picker saved JSON to local
   Downloads while offline. Parsed output contained Mimi only, two tasks
   (one completed, one planned February successor) and five history rows,
   including the measurement. [Captured synthetic export](scoped-export.json).
7. Reinstalled the candidate APK with `adb install -r`, rebooted the emulator,
   reopened the app: both cats' tasks and Mimi's measurement/completion history
   remained. See [Hoje](today.png), [both cats](today-all-cats.png) and
   [history](history.png).
8. The final change made navigation/selectors grow with font size. Automated
   checks were repeated on that final source. Installed the final APK after
   instrumentation removed the app and checked first launch at 200% font scale;
   all four navigation labels and onboarding remained operable. See
   [large font](large-font.png). Reset device font scale to 100% afterwards.

The populated screenshots and recurrence/export/reboot walkthrough precede the
final control-sizing adjustment; the 200% font screenshot and final automated
results correspond to the source above. No care/storage semantics changed after
those populated walkthrough checks.

## Acceptance coverage and remaining checks

| Fixture | Evidence / status |
| --- | --- |
| A01 | Passed: first launch and cat creation offline |
| A02 | Passed at local boundary: profile validation, isolation and reopen tests; UI cat creation |
| A03 | Passed: two-cat Today, explicit identity, dated/undated status |
| A04 | Partial: UI creation and SQLite edit/stale-write, deferral and cancellation checks; full manual lifecycle walkthrough remains |
| A05 | Passed: month-end recurrence in JVM, SQLite and UI; duplicate/failure rollback cases |
| A06 | Passed at local boundary: typed measurement UI/history; observation independence and non-diagnosis export assertions |
| A07 | Passed: database close/reopen and actual emulator reboot |
| A08 | Passed at local boundary: manual scoped export; cascading deletion and other-cat preservation tests; manual delete-confirmation walkthrough remains |
| A09 | Partial: all-cat export and delete-all/reopen tests; full manual preview/deletion walkthrough remains |
| A10 | Passed by source and exercised flows: no veterinarian role or collaboration; veterinary appointment category works |
| A11 | Partial: PT-BR UI, native field labels, rotation and 130%/200% font checks; TalkBack and physical-device checks pending |
| A12 | Not applicable: version 0.1 ships no reminders |

Minimum API 26 compatibility is lint-checked, not emulator-tested here. Physical
hardware, TalkBack, other Android versions, OS backup/manufacturer behavior,
export failure/provider edge cases and production signing remain unverified.
Import/restore, reminders and cloud synchronization are explicitly outside this
version. This evidence establishes a working debug implementation, not owner
pilot or production release readiness.
