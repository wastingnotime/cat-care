# API and web materialization decision

Date: 2026-09-01

Materialize the released responsibility-status semantics as a FastAPI service
with SQLite persistence and a framework-free browser client. The API owns
network contracts, persistence, validation, and transition authority. The web
surface owns interaction, responsive presentation, accessibility, and visible
failure behavior.

The first vertical path includes profile identity, status, responsibility list,
creation, completion, and timeline reads. It intentionally does not copy or
import the simulation implementation. Remaining simulation slices will be
synchronized incrementally after this local composition proves the boundary.

Local development is unauthenticated. Production authentication is blocked on a
repository-owned issuer, audience, browser client, redirect, scope, session, and
permission mapping.

An MCP project is deferred because this change has an explicit owner-browser
consumer but no accepted agent-facing intentions, permission model, or MCP auth
forwarding contract. Revisit that divergence before classifying the monorepo's
application surfaces as complete.
