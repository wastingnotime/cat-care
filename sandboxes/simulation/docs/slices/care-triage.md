# Care triage

## Contract

Given one or more owner note references, an AI service may produce a
provisional urgency assessment. A veterinarian can accept, modify, or reject
that assessment. The simulation does not model diagnosis, treatment, or
medication recommendations.

## Rules

- An assessment must reference existing notes.
- Urgency is one of `urgent`, `needs_attention`, `monitor`, or
  `insufficient_information`.
- AI assessments remain `pending` until veterinarian review.
- Every assessment records provider, model version, rationale, uncertainty, and
  timezone-aware assessment time.
- A veterinarian review records reviewer identity, decision, rationale, and
  timezone-aware review time.
- A modified review must provide final urgency.
- Review decisions cannot be changed through the same assessment.
- AI urgency is not a diagnosis and does not authorize treatment.

## Done criteria

- Assessment and review records are exported deterministically.
- Unknown notes and future assessment/review timestamps are rejected.
- Terminal deletion removes assessments and reviews.
- Note references remain stable across domain, adapter, and export surfaces.
- The deterministic scenario demonstrates pending AI assessment followed by
  veterinarian modification.
- Runtime observations preserve AI uncertainty and veterinarian rationale for
  auditability.
- Deletion receipts report removed assessments and veterinarian reviews.
- External triage, notification, profile, and recurrence systems are expressed
  as replaceable ports; they cannot mutate domain state directly.

## Out of scope

Veterinary diagnosis, treatment recommendations, medication instructions,
provider integration, model selection, clinical validation, and emergency
communications.
