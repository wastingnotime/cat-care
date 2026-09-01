# API and web local validation

Date: 2026-09-01

## Scope

- Semantic slice: `responsibility-status`
- API source project with SQLite persistence
- Thin web client against the real local API
- Runtime composition on loopback ports `8000` and `5173`

## Results

- API tests: 5 passed.
- Simulation regression tests: 93 passed.
- API readiness: `GET /healthz` returned `200` and `{"status":"ok"}`.
- Web readiness: `GET /` returned `200`.
- Chromium E2E: 1 passed in 1.2 seconds.

The browser flow loaded Mimi's status, created an undated `Annual exam`
responsibility, observed the domain's uncertainty sentence, completed the
responsibility, observed the all-clear sentence, and found the completion in
the rendered timeline. Network logs confirmed the browser refreshed cat,
status, responsibilities, and timeline from the API after both commands.

## Boundaries

This validates local source composition, not built immutable artifacts,
authentication, production persistence, provider registration, deployment, or
promotion eligibility.
