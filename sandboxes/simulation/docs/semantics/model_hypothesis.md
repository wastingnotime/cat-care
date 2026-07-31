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

## Open questions

- What due-soon threshold feels useful without creating urgency inflation?
- Which missing information is important enough to show as uncertainty?
- Which recurrence policies beyond a fixed day interval are needed by real
  owners?
- Does an undated responsibility need a separate “needs scheduling” state, or
  is the current uncertainty status sufficient?
