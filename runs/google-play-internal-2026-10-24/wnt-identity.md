# WNT identity internal release

2026-10-24: Google Play confirms `org.wastingnotime.catcare` version 0.1.0,
version code 2, is available to internal testers. This is the WNT app entry
and supersedes the earlier package for future delivery.

- Namespace, application ID, Kotlin/Java packages, test authorities, reminder action,
  and device/emulator launch scripts now use the WNT identity.
- Compile/target API 36, minimum API 26.
- Release unit tests, release lint, bundle and instrumentation APK assembly passed.
- All 44 instrumentation tests passed on the disposable API 35 emulator.
- `git diff --check` passed. Android 16 device testing was not performed.
- Tester audience: existing “Sliding Tasks internal testers” list (4 users).
- Google Play signing and automatic protection remain enabled.
- Non-blocking Play warnings: missing deobfuscation mapping and native debug symbols.
- Bundle: `apps/mobile/app/build/outputs/bundle/release/cat-care-wnt-0.1.0-2-signed.aab`.
- SHA-256: `57e103023a99917b294e06de56bc7836639220fd9a6d834d68142043cca4034a`.
- Console: https://play.google.com/console/u/0/developers/8460748010140158851/app/4974446923927300781/tracks/4701027572839862558?tab=releases
- Opt-in: https://play.google.com/apps/internaltest/4701027572839862558
- Screenshot: `.local/play-release/published-wnt.png` (ignored).

The earlier `care.cat.offline` app remains a separate Play entry and installation.
Export full data there and restore it in the WNT app to transfer records. No database
or export-format change is involved. See ADR 0006. The WNT entry currently displays
`org.wastingnotime.catcare (unreviewed)` until listing setup and review are complete.
The upload key remains outside Git under `~/.local/share/cat-care/signing/`.
