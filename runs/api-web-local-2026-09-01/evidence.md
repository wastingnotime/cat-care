# API and web local validation

Date: 2026-09-01

## Scope

- Semantic slices: `responsibility-status`, `cat-profile-and-care-history`,
  `notification-and-deferral`, `care-triage`, and `data-stewardship`
- Go API source project with deterministic in-memory persistence
- SolidStart web application and BFF against the real local API
- Runtime composition on loopback ports `8080` and `5173`
- Root `make dev` entrypoint with Air-managed Go reload and SolidStart dev mode

## Results

- Go API tests: 6 passed across domain and HTTP contract packages.
- Simulation regression tests: 93 passed.
- SolidStart TypeScript check: passed.
- SolidStart production build: passed.
- API readiness: `GET /healthz` returned `200` and `{"status":"ok"}`.
- Web readiness: `GET /` returned `200`.
- Chromium E2E: 2 passed in 1.8 seconds.

The browser flow loaded Mimi's status, created an undated `Annual exam`
responsibility, observed the domain's uncertainty sentence, completed the
responsibility, observed the all-clear sentence, and found the completion in
the rendered timeline. The Solid client refreshed status, responsibilities, and
timeline through its same-origin BFF after both commands.

The expanded browser journey also recorded a notification without changing
responsibility state, added a non-diagnosis observation, requested provisional
triage, recorded veterinarian modification to urgent, and created the linked
follow-up responsibility. HTTP contract evidence additionally covers profile
validation, recurrence policy, deferral, export, deletion counts, and rejection
of post-deletion mutation.

Local runtime shutdown was also revalidated at the process-group boundary: Air,
its Go child, npm, and the SolidStart child terminate together, leaving neither
loopback port occupied for the next run.

## Boundaries

This validates local source composition, not built immutable artifacts,
authentication, production persistence, provider registration, deployment, or
promotion eligibility.
