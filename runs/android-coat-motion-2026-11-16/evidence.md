# Coat selector prototype — 2026-11-16

Implemented only the approved first experiment: 0.97 press compression with a
restrained return, 180 ms selected-background transition and one 220 ms blink
when choosing a different coat. Checkmark and selected semantics stay immediate.
The native selectable interaction source handles cancelled presses, the touch
target remains fixed, and ripple feedback stays within the rounded card.

Avatar eye openness defaults to fully open. The picker owns transient reaction
state; other screens do not animate. Deselection cancels the old reaction,
reselection does not retrigger it, and editor restoration starts at rest. Compose
motion duration scaling controls animations; zero scale explicitly suppresses
the blink and visual compression. No dependencies or storage changes.

## Validation

- JDK 21: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`
  passed on the final code; 10 JVM tests passed.
- Disposable API 35 `cat-care-visual-35`: `cardsCreateEditAndRestoreCoat` and
  `coatSelectionSurvivesRapidChangesAndCancelledPress` passed with normal system
  animation settings. These check persisted selection, immediate radio state,
  rapid changes before animations finish, cancelled press, same-coat activation
  and draft restoration.
- The same two focused tests passed on the final code with
  `animator_duration_scale=0`. The original setting was restored afterward.
- An initial normal-motion run failed the existing calico restoration assertion;
  an isolated rerun passed. The test now explicitly asserts selected state after
  activation and before recreation. Both final normal-motion tests passed.
- Visually inspected the [final selector screenshot](screenshots/coat-selector.png):
  selected label/checkmark, original artwork and rounded card layout remain clear.
  This inspection caught and corrected an initially unclipped outer ripple.
- `git diff --check` passed. No physical phone or production data was used.

## Remaining evaluation

This is a working prototype, not a completed usability study. Manual TalkBack
and keyboard traversal, the full light/dark/large-text matrix, all-coat blink
legibility and frame profiling remain for acceptance before broader rollout.
The tests establish functional behavior with animation settings on/off; they do
not measure subjective timing or prove frame performance. Owner feedback on
whether the motion feels calm and pleasant remains pending.
