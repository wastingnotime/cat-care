# Cat Care model slice map

## Release intent

Release the smallest coherent model that helps one owner understand and manage
care for one cat while preserving uncertainty, traceable history, and a strict
non-diagnosis boundary.

## Implemented slices

| Slice | Domain promise | Primary evidence | Status |
|---|---|---|---|
| Responsibility status | Derive calm, explainable status and manage traceable responsibility lifecycle and recurrence. | `responsibility-status.md`; domain, adapter, and runtime tests | Accepted |
| Cat profile and care history | Preserve validated optional profile data and distinct typed care records in one deterministic timeline. | `cat-profile-and-care-history.md`; domain and adapter tests; runtime reads | Accepted |
| Notification and deferral | Separate provider delivery outcomes from owner completion, cancellation, and deferral decisions. | `notification-and-deferral.md`; gateway, adapter, and runtime evidence | Accepted |
| Care triage | Keep AI urgency provisional until explicit veterinarian review and support pending information/follow-up workflow. | `care-triage.md`; domain, adapter, port, actor, and runtime evidence | Accepted with non-clinical boundary |
| Data stewardship | Export owned state deterministically and delete it terminally without orphaned records. | `data-stewardship.md`; deletion/export tests and runtime receipt | Accepted for the simulated store |

All slices extend the same `CatCareState`, thin `CareAdapter`, and runtime
scenario. They are not separate applications.

## Released adapter boundary

The candidate technology-facing boundary is the plain-data behavior exposed by
`app.interfaces.care_adapter.CareAdapter` and the typed outbound ports in
`app.interfaces.integration_ports`. It includes profile, responsibility,
status, timeline and typed-history reads; responsibility, note, direct-care,
notification, triage, export, and deletion commands; and explicit public domain
errors represented by `ValueError` in the simulation.

This is a semantic adapter contract, not a production API schema. Authentication,
HTTP routes, persistence, retry behavior, and browser refresh timing remain
technology-project concerns.

## Deferred candidate slices

- Authentication, permissions, and multiple owners or cats.
- Persistent storage, conflict handling, offline synchronization, and audit
  retention.
- Notification preferences, channels, retries, escalation, and emergency
  communication.
- Attachments and clinical measurements.
- Clinical validation, diagnosis, treatment, and medication guidance.
- Browser interaction, accessibility, refresh timing, and lived owner-language
  validation.
- Recurrence beyond fixed day intervals and calendar months.

These are deliberate exclusions, not evidence that the released behavior covers
the broader product domain.
