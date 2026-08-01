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
23. Technology create commands use plain fields and do not require callers to
   construct domain objects.
24. Technology adapters expose responsibility lists with domain-derived states
   and deterministic due-date ordering, including an ID tie-breaker.
25. Technology adapters expose planned-responsibility edits with complete
   history details.
26. Adapter event records preserve action keys used for duplicate-care
   protection.
27. Technology adapters expose optional cat profile metadata as plain data
   using the canonical domain export projection.
28. Cat profile edits are validated, traceable, and exposed through the thin
   adapter contract; edits cannot be recorded in the future.
29. Technology adapters expose direct care-event recording separately from
   lightweight notes.
30. Care-event type and description are required for a meaningful timeline
   record.
31. Technology adapters expose notification history newest first without
   requiring clients to filter the full timeline.
32. Technology notification commands accept serialized outcome values and
   validate them at the adapter boundary.
33. The runtime observatory exposes application use cases as explicit nodes and
   records their invocation separately from domain events.
34. Every use-case invocation resolves to a declared observatory node so its
   owner-to-use-case beam remains renderable and cannot silently fall back to a
   hidden observation node.
35. Status observations preserve changes in the derived status kind as explicit
   transition evidence, rather than requiring consumers to diff snapshots.
36. Status transition evidence includes the responsibility context and resulting
   sentence needed to explain the change without reconstructing prior snapshots.
37. The status read path is explicit: reviewing care status queries the calm
   status projection and can be observed as a use-case-to-projection beam.
38. A status projection query carries its nearest responsibility context so the
   read result remains explainable when inspected independently of the timeline.
39. Responsibility-changing use cases expose an explicit command path to the
   responsibility aggregate, making write intent and domain handling inspectable.
40. Use-case command targets are restricted to declared graph nodes so structural
   relationships and runtime command beams cannot drift apart.
41. Cat profile editing targets a distinct cat-profile aggregate rather than
   being modeled as a responsibility mutation.
42. Notification delivery targets a distinct notification aggregate and does not
   imply a responsibility state transition.

## Open questions

- What due-soon threshold feels useful without creating urgency inflation?
- Which missing information is important enough to show as uncertainty?
- Which recurrence policies beyond a fixed day interval are needed by real
  owners?
- Does an undated responsibility need a separate “needs scheduling” state, or
  is the current uncertainty status sufficient?
