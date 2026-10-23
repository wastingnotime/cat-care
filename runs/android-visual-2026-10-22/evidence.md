# Android visual refresh evidence

Branch: `docs/offline-owner-android`. The implementation and this evidence are
in the same visual-refresh commit, following `e8c700a`.

Built and checked with `apps/mobile/gradlew -p apps/mobile lintDebug assembleDebug`.
Build passed; Android lint reported no issues. Domain/storage code was not
modified, so its automated tests were not repeated for this presentation change.

Visual checks used a separate disposable `cat-care-visual-35` emulator (Android
15/API 35, Pixel 2 profile, 1080x1920, 420 dpi). Screenshots contain synthetic
Mimi/Luna data only. The owner's existing emulator data was not used for these
artifacts. The updated APK was then installed/launched on the existing visible
`cat-care-offline-35` emulator through `make mobile-run`.

Observed interactions:

- Created two cats through the styled fields and save button.
- Switched named cat chips, created one dated daily care task for Mimi and one
  undated task for Luna. Today retained both cat identities and status labels.
- Recorded a measurement for Mimi and inspected her history.
- Navigated all four tabs; inspected selected navigation and cat states.
- Opened the profile editor and dismissed it without saving.
- Opened/dismissed the storage-policy dialog from Dados. Export and deletion
  actions keep their existing scope confirmations.
- Checked 100% and 200% font scale. Entry actions stack at 200%, navigation labels
  stay readable on this viewport, and the scrollable content remains reachable.
- Inspected foreground/background contrast for supporting text on sage/cream,
  white text on forest, and rust text on its pale background: all exceed 4.5:1.

Screenshots:

- [Welcome](welcome.png)
- [Today](today.png)
- [Cat profile](profile.png)
- [Care history](history.png)
- [Data controls](data.png)
- [Profile form](form.png)
- [200% text size](large-text.png)

TalkBack, physical devices and other screen sizes were not tested in this pass.
The temporary visual-check emulator was stopped after capture.

Final debug APK SHA-256: `06feed122e4430d4282c1d5b52357074cecbe28d3d820376b8ade6e67ae273d7`.
