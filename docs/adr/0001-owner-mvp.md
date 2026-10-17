# ADR 0001: Freeze the owner MVP and retain the existing service boundaries

Status: accepted for scope; production release remains gated by the evidence below.
Date: 2026-10-16 (America/Sao_Paulo).
Task: [WNT-145](https://linear.app/wastingnotime/issue/WNT-145).
Source baseline: `3924d52d986e2ccd837746c533b7afd49e1db343`.

## Context

Cat Care helps ordinary owners maintain calm, accurate daily care records and
useful veterinary continuity. The baseline implements a local prototype. It does
not establish an operating cloud service. The October 13 Linear assessment is
background; repository source governs statements about current behavior.

## Decision

Retain the Go domain/application API as the authority for care state, SolidStart
as the existing owner and veterinarian web adapter, and Python simulation as an
independent model and regression reference. Android will be another client of
the cloud API. Failed writes must remain visible and retryable; stale cached
reads must be identified. Offline command queues and synchronization are deferred.
Android technology selection belongs to WNT-147.

The owner MVP contains:

- Multiple cat profiles with name, birth/adoption dates and per-cat selection.
- Today across owned cats, with explicit cat identity and calm scheduling status.
- Create, edit, complete, cancel and defer responsibilities; retain recurrence
  semantics and history. Notification delivery never completes a responsibility.
- Explicitly typed direct care and non-diagnosis observations/history.
- Deliberate export and deletion with the scope shown before confirmation.
- PT-BR presentation and accessible interactions before an owner pilot.

Preserve the assigned-veterinarian web workflow: owner invitation, veterinarian
acceptance, one assigned veterinarian per cat, assignment removal, evidence
reading, review decisions, information requests, owner replies and reviewed
follow-up. Invitation acceptance alone grants no cat access. A veterinarian
may also own cats; workspace permissions remain distinct.

Defer a clinical triage provider, AI diagnosis, clinic/business administration,
veterinarian payments, iOS and autonomous care mutations. Existing fake triage
is synthetic workflow evidence only. Production manual review must not depend
on presenting fake urgency as clinical output. Notification provider delivery
and limitations require explicit proof before any reminder promise.

## Data stewardship boundary

Current `/export` and `/data` operate on one request cat (`cat_id`, otherwise
session active cat). They do not export/delete an account. Account-level text
in the baseline is misleading. Current deletion clears the cat state, writes a
tombstone and hides that cat from listings; ownership maps, invitations, other
cats and sessions remain. Restart seeds the demo again.

For the MVP, cat export/deletion must name the cat explicitly. Account export
must enumerate all owned cats; account deletion must cover those cats,
relationships and identity/session revocation, with a documented backup expiry
policy and receipt. These account capabilities are requirements, not existing
endpoints. WNT-149 must settle their API and persistence semantics before release.
Assigned-veterinarian access is not authority to export an entire owner account.

## Consequences and gates

The interaction POC (WNT-146) uses the same synthetic two-cat scenarios and
commands across GUI, guided dialogue and hybrid modes. No model may bypass
schema validation, authorization or owner confirmation. Acceptance fixtures
are in [the fixture contract](../../contracts/acceptance/owner-mvp.md).

WNT-149 must provide production identity, persistence, atomic updates and retry
semantics plus a restart/restore proof. WNT-148 must confirm operating costs and
provider/account ownership. Unknown external infrastructure stays unknown until
attributed evidence is available; it is not a reason to provision a replacement.
See [baseline and production gaps](../assessment/wnt-145-production-gaps.md).

This ADR freezes scope and records release gates; it does not certify deployment,
clinical safety, real provider delivery or pilot readiness. Re-estimate at each
preceding milestone gate within one substantial task in progress.
