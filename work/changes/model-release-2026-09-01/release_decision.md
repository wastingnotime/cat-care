# Cat Care model release decision

Date: 2026-09-01

## Decision

**Accepted: Cat Care simulation model 0.1.0.**

The five slices in `model_slice_map.md` form a coherent first domain release for
one owner and one cat. The decision is supported by 93 deterministic tests and
the replayable 175-observation scenario receipt in
`runs/model-release-2026-09-01/`.

## Released semantics

- Calm status derived from planned responsibility state, deterministic time, an
  injected due-soon threshold, and explicit uncertainty inputs.
- Traceable responsibility creation, correction, recurrence, completion,
  cancellation, and deferral with duplicate-action protection.
- Validated optional cat profile plus distinct note, direct-care, notification,
  triage, and veterinarian-review records in one deterministic history.
- Notification outcomes that never imply owner action.
- Provisional AI urgency requiring veterinarian review before acceptance or
  modification, with rejection, information request, and follow-up behavior.
- Deterministic owner export and terminal deletion within the simulated store.
- Plain-data inbound adapter semantics and typed outbound integration ports.

## Explicit non-claims

This release is not a production application, clinical validation, diagnosis or
treatment system, persistent event store, privacy-policy certification,
browser validation, deployment decision, or proof of owner desirability.

## Synchronization gate

Technology projects may use the released semantic adapter boundary as input,
combined with their own WNT project shape and local decisions. Before any web
release, they must define authentication and persistence, validate actual
browser timing and accessibility, and preserve the non-diagnosis and uncertainty
contracts. No technology project is created or synchronized by this decision.

## Re-entry triggers

Return to extraction or refinement when owner research, veterinarian review,
runtime feedback, or technology integration produces evidence that changes
threshold policy, uncertainty semantics, recurrence, triage authority, deletion
scope, or the one-owner/one-cat boundary.
