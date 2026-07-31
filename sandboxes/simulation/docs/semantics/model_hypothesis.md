# Model hypothesis

## Current vocabulary

- **Cat**: the animal whose care is being recorded.
- **Responsibility**: something the owner expects to happen by a relevant time.
- **Care event**: something that happened, recorded in the past or present.
- **Note**: a lightweight observation, never a diagnosis.
- **Calm status**: a derived summary of responsibilities and known gaps.
- **Status snapshot**: a stable kind, sentence, and optional nearest
  responsibility identity for technology adapters.

## First boundary

The first slice evaluates responsibility status for one cat. `planned`,
`completed`, and `cancelled` are persisted facts; `due soon` and `overdue` are
derived from due date, current time, and a configured threshold.

## Hypotheses to test

1. A single status sentence is more useful than a dashboard.
2. Unknown or missing care information must prevent a claim that everything is
   fine.
3. Completing a responsibility removes it from active urgency and creates a
   traceable care event.
4. Recurrence must be explicit before a future occurrence is created, with the
   next occurrence anchored to the planned due date.
5. All care times are timezone-aware so status and timeline comparisons are
   deterministic across owner locations.
6. Responsibilities that refer to the same real-world action must share an
   explicit action key and cannot create duplicate completion events.
7. Creating and editing responsibilities are traceable events; completed
   responsibilities are not silently rewritten.
8. Edit evidence must include the previous and new values needed to explain a
   correction.
9. A direct care event may be linked to a responsibility, but remains its own
   timeline record.
10. Cancellation history, like all care events, cannot be recorded in the
   future.
11. Owner export includes current responsibility state and chronological event
   history in a deterministic, portable representation, including uncertainty
   inputs that affect calm status.
12. Cat deletion is terminal for the simulation environment and removes owned
   responsibilities and events without leaving orphaned records.
13. An uncertainty status identifies the responsibility whose missing timing
   information needs attention when one exists.
14. Responsibility category is explicit required domain vocabulary and is
   preserved through edits, recurrence, and export.
15. Cat profile metadata is optional, validated, and included in owner export;
   it does not become a responsibility requirement.
16. When both are known, adoption date cannot precede birth date.
17. Notification delivery outcome is separate from owner action; a failed or
   delivered notification never silently completes a responsibility.
18. Notification outcomes must be explicit domain values rather than
   unvalidated channel strings.
19. An owner deferral is an explicit decision that reschedules a planned
   responsibility to a later date and remains distinct from notification
   failure.
20. Technology adapters expose released status and command contracts without
   reimplementing domain transitions.
21. Technology adapters expose timeline reads using the domain's newest-first
   ordering.
22. Technology adapters expose notification, deferral, export, and deletion
   contracts without adding channel-specific domain behavior.

## Open questions

- What due-soon threshold feels useful without creating urgency inflation?
- Which missing information is important enough to show as uncertainty?
- Which recurrence policies beyond a fixed day interval are needed by real
  owners?
- Does an undated responsibility need a separate “needs scheduling” state, or
  is the current uncertainty status sufficient?
