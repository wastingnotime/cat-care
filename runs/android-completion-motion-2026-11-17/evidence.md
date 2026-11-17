# Today completion continuity — 2026-11-17

Remaining Today cards and section headers now move into place over 220 ms after
a committed completion refresh. The completed item disappears immediately, with
no lingering action or decorative exit. A transient completion ID is published
atomically with the refreshed UI snapshot; it is not stored in SQLite or saved
navigation state. Loading, recreation, ordinary refreshes, timer regrouping and
Upcoming disclosure snap into place. System-disabled motion also snaps.

The existing confirmation, persistence, recurring successor calculation and
named success message remain intact. No dependency or backup changes.

## Validation

- JDK 21 build, lint, debug APK and instrumentation APK assembly passed.
- 11 JVM tests passed, including completion-cause gating against initial load,
  identical snapshots, unrelated IDs, cancellation and uncommitted state.
- Disposable API 35 `cat-care-visual-35`: the new motion test measures a remaining
  card before completion, during the transition and after it settles. It verifies
  movement over time, immediate removal of the completed action, and persistence.
- Existing confirmation/history, recurring completion/actual next date and
  collapsed Upcoming/history-filter checks passed. The recurring test now also
  opens Upcoming and verifies the actual successor card is reachable.
- The first motion test paused the clock before the confirmation dialog rendered;
  it now asserts the dialog action is visible before pausing, and passes.
- The timed-position and recurring-successor tests passed with motion disabled.
  The former asserts that the remaining card is already at its final position.
  The test rule now supplies the system motion scale explicitly: Compose's test
  recomposer otherwise omits Android's motion context, which initially caused
  the disabled-motion assertion to fail. The original system setting was restored.
- `git diff --check` passed. All instrumentation targeted the disposable emulator;
  the attached physical phone was not used.

Manual TalkBack focus continuity and subjective motion review remain pending.
The timed position test verifies motion, not frame performance or perceived smoothness.
