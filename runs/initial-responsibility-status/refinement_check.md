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

## Export completeness refinement

The export now includes `future_information_known`, preserving the uncertainty
input required to interpret whether a calm status can be trusted.

## Deletion build

Cat deletion is now a terminal operation with a receipt. Owned responsibilities
and events are cleared, subsequent export contains only deletion metadata, and
the domain rejects further mutation.

## Uncertainty refinement

Unknown status snapshots now include the active responsibility ID that lacks a
due date, allowing a future adapter to guide correction without weakening the
uncertainty message.

## Category build

Responsibilities now require non-empty category vocabulary, and category is
preserved through edits, recurring occurrences, and export.

The category is now required at construction rather than silently defaulting to
`general`, preventing incomplete responsibility records from entering the
model.

## Cat-profile build

The simulation now carries optional birth date, adoption date, and photo
reference metadata for the cat profile, with privacy-safe export behavior.

## Profile consistency refinement

When both profile dates are known, the domain now rejects an adoption date
before the birth date.

## Notification build

The simulation now records delivered or failed notification outcomes separately
from owner action. A failed notification leaves the responsibility planned or
overdue rather than changing its care state.

## Notification validation refinement

Delivered and failed outcomes are both covered, and arbitrary channel strings
are rejected before they can enter the care history.

## Owner-deferral build

Owners can now explicitly defer a planned or overdue responsibility to a future
date. The decision is recorded separately from notification delivery and
completion.

## Deferral consistency refinement

Deferral now requires a later date when the previous due date was known; an
undated responsibility may still be scheduled for its first future date.

## Adapter-contract build

A thin Python adapter now exposes status, create, complete, cancel, and note
commands as plain records while delegating behavior to the shared domain. It is
ready to inform a future web adapter without importing a web framework.

## Adapter timeline refinement

The adapter now exposes the domain timeline as a newest-first read-only
response, keeping web consumers inside the released contract.

## Adapter-surface build

The adapter contract now covers notification outcome, owner deferral, data
export, and deletion in addition to the original status and lifecycle commands.

## Adapter-boundary refinement

Responsibility creation now accepts plain ID, title, category, due date, and
recurrence fields. Callers no longer need to construct simulation domain
objects to use the adapter contract.

## Responsibility-list build

The adapter now exposes sorted responsibility views with category, due date,
and domain-derived state, keeping urgency calculation out of future clients.

## Adapter-edit build

The adapter now exposes planned-responsibility edits using plain fields and
returns the domain's previous/new value details for history-aware clients.

## Adapter-event refinement

Adapter event records now preserve action keys, keeping duplicate-care
protection visible to technology clients.

## Profile-adapter build

The adapter now exposes cat name, optional dates, and photo reference as plain
profile data for future web surfaces.

## Profile-projection refinement

Profile reads now reuse the domain's canonical export projection rather than
duplicating date and photo serialization in the adapter.

## Profile-edit build

Cat profile edits are now validated against known date relationships and recorded
with previous/new details through the domain and adapter contracts.

## Profile-edit time refinement

Future profile edits are now covered and rejected without mutating the existing
profile.

## Responsibility-order refinement

Equal due dates now use responsibility ID as an explicit tie-breaker, making the
adapter response stable independent of insertion order.
