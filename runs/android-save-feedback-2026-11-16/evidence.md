# Editor Save feedback — 2026-11-16

Editable forms now show a 20dp progress indicator beside Save during the existing
ViewModel busy state. A reserved slot keeps the action width stable. Save retains
its label and gains a localized saving state with polite live-region semantics;
the decorative indicator has no separate accessibility announcement or percentage.
System-disabled motion uses a static open ring. No artificial delay or changes to
save transactions, validation, navigation or confirmation actions were added.

## Validation

- JDK 21 build, lint, debug/instrumentation APK assembly and 10 JVM tests passed.
- On disposable API 35 `cat-care-visual-35`, `EditorSaveButtonTest` passed:
  stable bounds across idle/busy, visible indicator, localized saving semantics,
  disabled duplicate taps, and immediate return to idle/retry in a controlled
  busy-state fixture. This fixture does not simulate a storage failure.
- The same focused test passed with system animator duration scale zero; the
  original setting was restored afterward.
- Existing profile persistence/restoration, note save/prompt and large-text
  editor tests passed. The last covers saving at 200% text size in English/dark
  appearance; the other flows use Portuguese/light appearance.
- Visually inspected the [large-text action layout](screenshots/plan-large-text.png).
  Save and Cancel remain visible and separated.
- Initial component-test visibility assertions exposed a cleared test tag on the
  decorative indicator. Its semantics now preserve the test tag while suppressing
  redundant progress announcements; the corrected focused test passed.
- `git diff --check` passed. No physical phone or production data was used.

Manual screen-reader announcement review remains pending. Automated semantics
checks do not establish actual TalkBack announcement timing.
