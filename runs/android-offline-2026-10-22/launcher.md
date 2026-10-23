# Emulator launcher validation

Command: `make mobile-run` on `docs/offline-owner-android`.

- With no emulator running, `CAT_CARE_EMULATOR_HEADLESS=1 make mobile-run`
  started the existing `cat-care-offline-35` AVD, waited for boot, built the
  debug APK, installed it successfully and launched `care.cat.offline/.MainActivity`.
- A subsequent `make mobile-run` printed `Using running emulator emulator-5554`,
  installed the APK and launched the activity successfully. `adb devices`
  listed one emulator; no duplicate AVD was started.
- A visible emulator session also completed build/install/launch with activity
  status `ok`. The emulator was left running with Cat Care open.
- SDK discovery used the ignored `apps/mobile/local.properties`. Emulator
  startup logs were written to ignored `apps/mobile/.local/emulator.log`.
- `git diff --check` passed. App domain/storage tests were not repeated because
  this change only adds the development launcher and Makefile/documentation.

Prerequisites and overrides are documented in `apps/mobile/README.md`.
