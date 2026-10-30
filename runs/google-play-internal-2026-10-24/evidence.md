# Google Play internal release — 2026-10-24

Cat Care 0.1.0, version code 2 (`care.cat.offline`), is available to internal testers.
Play Console account: wastingnotime. Tester audience: the existing four-person
“Sliding Tasks internal testers” email list, following the requested reference app defaults.
Play App Signing and automatic protection are enabled. Default listing language is en-US;
the app itself bundles English and Brazilian Portuguese.

The release includes the working-tree product changes based on commit
131c8ff, rather than only that committed revision. No commit was created by the publishing workflow.

## Required compatibility change

Play rejected the initial version-code-1 bundle because target API 35 is below
its API 36 minimum. Compile and target SDK were updated to 36; version code was
advanced to 2. Minimum supported API remains 26 and version name remains 0.1.0.
Release JVM tests, lint and bundle assembly passed after this change:
`./gradlew testReleaseUnitTest lintRelease bundleRelease`.
Android 16 device instrumentation was not run by this publishing workflow.

## Artifact and delivery

- Signed bundle: `apps/mobile/app/build/outputs/bundle/release/cat-care-0.1.0-2-signed.aab` (ignored build output).
- SHA-256: `cacd68b16519844e44c6fc7ff226a7be695c0c7734a9dc0b4ccff7dc292a1b8e`.
- Release: `2 (0.1.0) internal test`.
- Console: https://play.google.com/console/u/0/developers/8460748010140158851/app/4974189825752315200/tracks/4699764146893467561?tab=releases
- Tester opt-in: https://play.google.com/apps/internaltest/4699764146893467561
- Console confirmation: “Available to internal testers”, active track, one version code.
- Publication screenshot: `.local/play-release/published.png` (ignored local evidence).

Two non-blocking Play warnings remain: absent deobfuscation mapping and native
debug symbols. The build does not enable R8 obfuscation. The app remains unreviewed;
Play displays `care.cat.offline (unreviewed)` until store setup and review are complete.

The upload key is stored outside this repository under
`~/.local/share/cat-care/signing/`; retain it and its password file for future uploads.
Neither signing material nor passwords are committed.
