# Today and history navigation evidence

Implementation and evidence share this commit, based on `dc5ff9b`.
Scope: Hoje planning entry, form avatar selection, and comments on observations in Histórico.

Validation on 2026-10-23:

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed.
  All 5 JVM tests passed. Lint has no errors and two existing warnings
  (`OldTargetApi` and the design-tool constructor for `CatAvatar`).
- All 17 instrumentation tests passed on a disposable Android 15/API 35 emulator:
  12 storage tests and 5 UI tests.
- UI coverage includes requiring an explicit cat choice with multiple cats,
  preselecting a sole cat, saving plans and records to the chosen cat, restoring
  form selections and comment drafts after activity recreation, and keeping
  registration/observations out of Hoje. Existing cat-card tests also pass.
- Storage coverage includes comment validation, persistence after reopen, scoped
  export, cascading deletion, unchanged observation counts and a schema 2 → 3
  migration preserving profiles/tasks/history. Existing task lifecycle tests pass.
- Visually inspected Hoje, both avatar forms, the observation thread and the
  narrow form with larger text. Manually submitted a comment and confirmed the
  saved observation was brought into view.

Device: disposable read-only `cat-care-visual-35`, `emulator-5562`, Pixel 2 profile,
America/Sao_Paulo timezone. Airplane mode enabled; Wi-Fi and mobile data disabled.
Normal viewport: 1080×1920 at 420 dpi, 100% text. Narrow viewport: 840×1600 at
420 dpi, 130% text. Synthetic Mimi/Luna records only. The existing user emulator
was not modified; the temporary emulator was stopped after validation.

Screenshots:

- [Hoje with only the planning entry](today.png)
- [Plan care with avatar choice](plan.png)
- [Registration in Histórico with avatar choice](register.png)
- [Comment form with the original observation](comment-form.png)
- [Saved observation comment](history-comments.png)
- [Narrow form with larger text](narrow-plan.png)

Screenshots precede the final copy clarification in export/delete previews and
resource extraction of the identical selected-avatar label; the final APK passed
the full checks above. TalkBack, physical devices and full release acceptance
were not tested in this change.

Debug APK SHA-256: `08da6239ae778760a1e2d204f3350687b63e346cef36e75ead7fe9e63d9bdec2`.
