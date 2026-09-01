# Local development runtime contract

- Both processes bind to loopback by default.
- API readiness is `GET /healthz` returning `{"status":"ok"}`.
- Web readiness is an HTTP `200` response for `/`.
- The web origin is allowed explicitly by API CORS configuration.
- Local SQLite state is mutable developer data under `.local/` and is ignored by
  Git.
- Local mode has no authentication and must not be exposed as a production
  configuration.
- Browser commands are followed by authoritative API refreshes; observed local
  refresh behavior is part of the web adapter contract.
