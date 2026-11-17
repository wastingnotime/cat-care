# Category selection feedback — 2026-11-16

Plan care and Add note now share the coat selector's 0.97 press compression,
restrained spring return and 180 ms background transition. A small shared
`SelectionCard` owns fixed touch targets, rounded ripple feedback and radio
selection semantics. Category borders and label weights update immediately;
category icons remain static. The coat blink stays local to `CoatSelectionCard`.
System-disabled animations render the final background and unscaled surface.

## Validation

- JDK 21: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`
  passed; 10 JVM tests passed. Final test-source updates also passed lint and
  instrumentation APK assembly.
- Disposable API 35 `cat-care-visual-35`: existing category title customization,
  note prompts/save, coat persistence/restoration and coat rapid-selection tests
  passed (four tests).
- New category interaction test passed: rapid choices before motion finishes,
  cancelled press, immediate selected semantics, keyboard activation, draft
  restoration and the resulting note prompt.
- Initial keyboard assertions failed because the instrumentation window remained
  in touch mode. The test now explicitly exits touch mode before requesting and
  asserting keyboard focus. The corrected test passed without production changes.
- Category interaction and care title/restoration tests also passed with system
  animator duration scale zero. The original setting was restored afterward.
- Visually inspected [category selection](screenshots/category-selection.png),
  including the focused selected tile, labels, borders and unchanged icon layout.
- `git diff --check` passed. No physical phone or production data was used.

Manual TalkBack review, subjective motion evaluation and frame profiling remain
pending. Keyboard activation is covered by instrumentation; it does not establish
full keyboard traversal or screen-reader usability.
