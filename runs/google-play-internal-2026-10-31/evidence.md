# Cat Care 0.1.1 testing release

Source revision: `935428e` (product changes through `4111877`). Package:
`org.wastingnotime.catcare`, version name 0.1.1, version code 3.

The requested update targets the existing Internal testing track. Play Console
shows that Closed testing has not been configured for this app.

## Changes

- Cat cards contain registration information and open editing directly.
- Notes unifies observations and direct care records with visual category tiles.
- Planned care suggests editable titles and opens time selection on the clock.
- History has selectable type/period chips and defaults to the last seven days.

## Validation and artifact

`testReleaseUnitTest lintRelease bundleRelease` passed: 10 unit tests passed;
lint reported 0 errors and 56 warnings. The bundle signature verified with the
existing upload key, retained outside Git.

Signed bundle (ignored):
`apps/mobile/app/build/outputs/bundle/release/cat-care-0.1.1-3-signed.aab`.
SHA-256: `4915fc792822f7f9a8bdf1d1b63157964141e094fc86979d145b449e8ba7d0cd`.

Debug 0.1.1 (3) installed and launched on the connected test phone using
`make mobile-update-device`, preserving data. This sideload uses the local debug
key; Google Play distributes an APK signed by Play App Signing.

## Delivery

Play Console confirms **Available to internal testers**, active track, one version
code: release `3 (0.1.1) UX improvements`, released October 31, 2026.
The app remains unreviewed. Play reports the same two non-blocking warnings for
missing deobfuscation mapping and native debug symbols.

- Console: https://play.google.com/console/u/0/developers/8460748010140158851/app/4974446923927300781/tracks/4701027572839862558?tab=releases
- Tester opt-in: https://play.google.com/apps/internaltest/4701027572839862558
- Confirmation: [published.jpg](published.jpg)

Google says changes usually appear on Play within one hour, occasionally longer.

