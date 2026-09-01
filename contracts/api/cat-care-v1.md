# Cat Care HTTP API v1

The API is authoritative for care state. The web application consumes
this contract and must refresh reads after commands rather than deriving domain
transitions locally.

API base path: `/v1`. The SolidStart BFF maps browser `/api/*` requests to this
surface.

| Method | Path | Meaning |
|---|---|---|
| `GET` | `/cat` | Read the current cat identity. |
| `GET` | `/status?due_soon_days=2` | Read calm status using an explicit threshold. |
| `GET` | `/responsibilities` | List responsibilities in deterministic due-date order. |
| `POST` | `/responsibilities` | Create a responsibility from `title`, `category`, and optional timezone-aware `due_at`. |
| `POST` | `/responsibilities/{id}/complete` | Complete one planned responsibility. |
| `GET` | `/timeline` | Read newest-first responsibility history. |

`GET /healthz` is the local readiness endpoint. Go request/response types and
HTTP contract tests are the executable field and error schema. The synchronized
`0.1.0` surface covers the responsibility-status slice only; other released
simulation slices remain candidates for later API increments.

## Errors

- `400`: malformed fields, missing RFC 3339 timezone, or invalid query policy.
- `404`: responsibility identity is unknown.
- `409`: the requested transition conflicts with current state.

## Authentication

Local mode is explicitly unauthenticated and binds to loopback by default. No
production issuer, audience, roles, permissions, or browser session contract has
been accepted. Production exposure is blocked until those mappings exist and
are enforced by the API and web channel.
