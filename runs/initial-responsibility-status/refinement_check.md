# Refinement check: initial responsibility status

Date: 2026-07-31

## Evidence

- Deterministic tests: 7 passed.
- Runtime supervision: passed on `127.0.0.1:8876`.
- Scenario observations: planned status, due-soon status, overdue status,
  completion event, calm status after completion, and a passing invariant.

## Finding

The first hypothesis is coherent enough to continue: urgency can be derived
from planned state, due time, current simulated time, and a threshold. The
model also distinguishes incomplete future information from an all-clear state.

## Refinement

The initial wording “Nothing important is pending soon” was too awkward and
could imply that no future responsibility exists. It was changed to
“Nothing important is due soon. Next: …”.

## Next questions

- Add explicit cancellation observations and verify history preservation.
- Add a timeline slice for care events and lightweight notes.
- Decide whether recurring responsibilities need a policy value beyond a fixed
  day interval.

## Refinement result

Cancellation now emits a traceable care event while removing the responsibility
from active urgency. The runtime scenario covers both completion and
cancellation in the same shared environment.

## Further refinement

An active responsibility without a due date now produces an explicit unknown
status instead of falling through to “Nothing important is pending”. This
preserves the product rule that incomplete information must not create false
confidence.

## Recurrence build

Recurrence is now represented by an explicit positive interval policy. Future
occurrences remain anchored to the planned due date, avoiding silent drift when
an owner completes a responsibility late.

## Time refinement

The domain now rejects naive timestamps at responsibility, care-event, note, and
status boundaries. This makes timezone handling an explicit invariant instead
of relying on runtime comparison errors.

## Status contract build

The calm-status projection now exposes a stable kind and optional nearest
responsibility identity alongside its sentence. This preserves calm language
for owners while giving future web/API adapters an explicit contract.

## Duplicate-action refinement

Responsibilities may now carry an explicit action key. The simulation rejects
a second completion event for the same key while leaving the second
responsibility planned, preventing silent duplicate care history.

## Responsibility lifecycle build

The shared environment now supports adding and correcting planned
responsibilities with traceable creation and edit events. Completed
responsibilities remain immutable through the edit command.

## Edit-history refinement

Edit events now preserve previous and new title and due-date values, making a
correction auditable instead of recording only the final state.

## Care-event build

The shared environment now accepts direct care events such as measurements or
exams. They remain independent timeline records while optionally linking to an
existing responsibility.

## Cancellation-time refinement

Cancellation now accepts an explicit current-time boundary and rejects future
history, aligning it with completion, notes, and direct care-event recording.

## Export build

The simulation now defines a deterministic owner export containing current
responsibilities and chronological event history, with JSON serialization
available to a future web adapter.
