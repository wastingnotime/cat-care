# Cat profile and care history

## Contract

Maintain optional validated cat profile metadata and a newest-first care history
whose direct-care records, lightweight notes, notifications, and responsibility
events retain distinct meanings.

## Rules

- Known adoption date cannot precede known birth date.
- Profile edits are timezone-aware and traceable.
- Direct care requires a type and description and may link to a responsibility.
- Notes are observations, never diagnoses.
- Typed record collections remain independently readable and exportable while
  the unified timeline uses deterministic ordering.

## Evidence and done criteria

- Domain tests cover profile validation, traceable edits, direct care, notes,
  responsibility linkage, and deterministic equal-time ordering.
- The thin adapter exposes plain profile data, typed newest-first record reads,
  and a stable timeline summary.
- The runtime scenario exercises profile edit/review and separate history reads.

## Out of scope

Attachments, diagnosis, treatment, clinical measurements, and persistent media
storage.
