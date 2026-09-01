# Cat Care model expectation-gap detection

Date: 2026-09-01

## Compared evidence

- Initial product-context capture and simulation request.
- Current semantic background and 101 model hypotheses.
- Five reconciled slice contracts and their exclusions.
- Domain, adapter, integration-port, and runtime test suite.
- Replayable scenario receipt under `runs/model-release-2026-09-01/`.
- Candidate adapter boundary and observatory graph.

## Coherence findings

1. Responsibility state, derived urgency, notification outcome, and owner action
   remain separate. No observed delivery outcome silently completes care.
2. Unknown timing prevents false reassurance and identifies an undated
   responsibility when possible.
3. Direct care, notes, notifications, triage, and responsibility changes retain
   typed records while contributing to one deterministic history.
4. Provisional AI urgency cannot become authoritative without a veterinarian
   decision. The model contains no diagnosis or treatment transition.
5. Export and terminal deletion cover all typed owned collections represented in
   the shared state and produce inspectable runtime evidence.
6. The thin inbound adapter delegates transitions to the shared domain; typed
   outbound ports do not receive mutable domain state.

## Expectation gaps and dispositions

| Gap | Evidence | Disposition |
|---|---|---|
| Timeline invariant briefly failed for equal-time events. | Initial EGD scenario run. | Refined monitor to use the canonical deterministic tie-break key; strengthened runtime test; rerun passed throughout. |
| Implemented behavior exceeded the two documented slices. | Profile, notification, care records, and deletion code/tests. | Reconciled into five explicit slice contracts and this slice map. |
| Four semantic questions remained open. | Prior `model_hypothesis.md`. | Resolved as bounded release decisions: injected threshold, explicit uncertainty inputs, two recurrence families, and no separate scheduling state yet. |
| Simulation run instructions did not expose the WNT runtime package. | README command failed at test collection. | Corrected the command to include the user-space runtime path. |
| The implementation is not fully decomposed into the preferred event-sourced application/infrastructure folder shape. | Mutable shared state and append-only history are concentrated in `domain.py`; use-case orchestration is split between adapter and runtime scenario. | Accepted as recorded simulation-structure debt for this model release. No claim is made that production persistence or event-stream rehydration is validated. Refactor before using simulation storage shape as a technology blueprint. |
| Product evidence is repository-curated context rather than owner/clinical validation. | `work/sources/cat-care-web-first-context.md`. | Release remains a model hypothesis for product testing, with all clinical and lived-interaction claims excluded. |

## EGD result

No remaining gap contradicts the bounded semantic release intent. The model is
coherent for deterministic simulation and adapter-contract synchronization, but
it is not evidence for clinical safety, user desirability, production storage,
browser behavior, or deployment readiness.
