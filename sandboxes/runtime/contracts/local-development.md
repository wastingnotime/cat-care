# Local development runtime contract

- Both processes bind to loopback by default.
- Go API readiness is `GET /healthz` returning `{"status":"ok"}`.
- Web readiness is an HTTP `200` response for `/`.
- The SolidStart BFF provides the browser's same-origin `/api` boundary and maps
  it to the Go API's `/v1` routes.
- Local API state is in-memory and resets with the Go service.
- Local mode uses deterministic owner and veterinarian identities with
  in-memory HTTP-only sessions. It must not be exposed as production
  authentication.
- Care reads and writes are scoped to the cat selected in the session.
- Browser commands are followed by authoritative API refreshes; observed local
  refresh behavior is part of the web adapter contract.
