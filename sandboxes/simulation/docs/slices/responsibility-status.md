# Responsibility status

## Contract

Given one cat, responsibilities, and a deterministic current time, derive a
calm status and the nearest actionable responsibility. Expose both a human
sentence and a stable status kind for future adapters.

## Rules

- A planned responsibility is `due soon` inside the configured threshold.
- A planned responsibility is `overdue` after its due time.
- Completed and cancelled responsibilities are not active urgency.
- Missing future responsibility information produces uncertainty, not “all
  clear”.
- Completing a responsibility records a care event at the current time.
- Cancelling a responsibility records a cancellation event and preserves its
  history.
- Notes are timeline observations and are not diagnoses.
- A recurring responsibility requires a positive explicit interval policy; its
  next occurrence stays anchored to the planned due date even when completed
  late.
- Responsibility, care-event, note, and current-time values must be
  timezone-aware.
- An explicit shared action key prevents two responsibilities from silently
  recording the same real-world completion twice.
- Creating and editing a planned responsibility emits history events; completed
  responsibilities cannot be edited through this command.
- Edit events include previous and new title and due-date values.
- Direct care events can be recorded in the past or present and optionally
  linked to an existing responsibility.
- Cancellation events cannot be future-dated.
- Export includes responsibility state and chronological event history without
  depending on persistence or web infrastructure, including uncertainty inputs
  used by the calm-status projection.
- Cat deletion clears owned responsibilities and events, returns a deletion
  receipt, and exposes no personal records in later export.
- Unknown status snapshots identify an active undated responsibility when
  possible.
- Responsibility category is explicit and required at construction, and is carried through lifecycle events,
  recurrence, and export.
- Cat birth date, adoption date, and photo reference are optional profile data
  and are included in export when present; known dates must remain coherent.
- Notification outcome is recorded separately from responsibility completion;
  delivery failure does not become owner inaction or completion.
- Notification outcome values are explicit and validated.
- Owner deferral records a decision and future due date; it does not masquerade
  as completion or notification delivery.

## Scenario

Mimi has a vaccine planned for 2026-01-04. The simulation observes its planned,
due-soon, and overdue states, then completes it and records the care event.

## Done criteria

- Derived status is deterministic and timezone-aware.
- Invalid future care events are rejected.
- Runtime observations contain semantic status and transition evidence.
- Unit tests cover due-soon, overdue, completion, cancellation, recurrence
  boundary behavior, and incomplete information.

## Out of scope

Authentication, web UI, persistence, notifications, attachments, medical
advice, and production API design.
