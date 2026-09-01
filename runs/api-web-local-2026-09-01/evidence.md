# API and web local validation

Date: 2026-09-01

## Scope

- Semantic slice: `responsibility-status`
- Go API source project with deterministic in-memory persistence
- SolidStart web application and BFF against the real local API
- Runtime composition on loopback ports `8080` and `5173`

## Results

- Go API tests: 5 passed across domain and HTTP contract packages.
- Simulation regression tests: 93 passed.
- SolidStart TypeScript check: passed.
- SolidStart production build: passed.
- API readiness: `GET /healthz` returned `200` and `{"status":"ok"}`.
- Web readiness: `GET /` returned `200`.
- Chromium E2E: 1 passed in 1.0 seconds.

The browser flow loaded Mimi's status, created an undated `Annual exam`
responsibility, observed the domain's uncertainty sentence, completed the
responsibility, observed the all-clear sentence, and found the completion in
the rendered timeline. The Solid client refreshed status, responsibilities, and
timeline through its same-origin BFF after both commands.

## Boundaries

This validates local source composition, not built immutable artifacts,
authentication, production persistence, provider registration, deployment, or
promotion eligibility.
