# API and web materialization decision

Date: 2026-09-01

Materialize the released responsibility-status semantics using the architecture
established by WNT Community Lab: a Go service with standard-library HTTP and
explicit domain/application/infrastructure/runtime boundaries, plus a
SolidStart application with a same-origin BFF. The API owns network contracts,
state, validation, and transition authority. The web surface owns interaction,
responsive presentation, accessibility, and visible failure behavior.

The first increment established profile identity, status, responsibility list,
creation, completion, and timeline reads. The completed local materialization
now covers all five released slices: recurrence and responsibility lifecycle,
profile and typed care history, notification/deferral, provisional triage with
veterinarian review and follow-up, and export/deletion. Development persistence
is in-memory, matching the reference repository's refinement-first local
adapter. It intentionally does not copy or import the simulation implementation.

Local development is unauthenticated. Production authentication is blocked on a
repository-owned issuer, audience, browser client, redirect, scope, session, and
permission mapping.

An MCP project is deferred because this change has an explicit owner-browser
consumer but no accepted agent-facing intentions, permission model, or MCP auth
forwarding contract. Revisit that divergence before classifying the monorepo's
application surfaces as complete.
