# Data stewardship

## Contract

Provide a deterministic owner export and terminal deletion of all records owned
by the simulated cat-care environment.

## Rules

- Export includes current profile and responsibility state, typed collections,
  chronological event history, and uncertainty inputs.
- Deletion clears profile data, responsibilities, events, notifications, notes,
  direct care, triage assessments, veterinarian reviews, and information
  requests.
- A deletion receipt preserves the timezone-aware deletion instant and removed
  collection counts.
- Post-deletion export exposes deletion metadata but no owned care data.

## Evidence and done criteria

- Domain and adapter tests cover deterministic export, complete deletion counts,
  mutation rejection, and orphan-free post-deletion state.
- Runtime observations expose export totals and typed deletion counts.

## Out of scope

Authentication, retention periods, backups, legal-policy selection, production
storage erasure, and data portability formats beyond the canonical projection.
