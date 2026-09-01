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
| `PUT` | `/responsibilities/{id}` | Correct a planned responsibility and recurrence policy. |
| `POST` | `/responsibilities/{id}/cancel` | Cancel a planned responsibility with history. |
| `POST` | `/responsibilities/{id}/defer` | Record an owner decision and move the due time later. |
| `GET` | `/timeline` | Read newest-first responsibility history. |
| `PUT` | `/cat` | Edit validated profile metadata. |
| `GET`, `POST` | `/notes` | Read and record lightweight non-diagnosis observations. |
| `GET`, `POST` | `/care-events` | Read and record typed direct care. |
| `GET`, `POST` | `/notifications` | Read and record delivered/failed outcomes without changing care state. |
| `GET`, `POST` | `/triage` | Read or request provisional triage from note references. |
| `GET` | `/triage-reviews` | Read veterinarian decisions separately from provisional assessments. |
| `POST` | `/triage/{id}/review` | Record a veterinarian accept/modify/reject decision. |
| `POST` | `/triage/{id}/information-requests` | Ask the owner for more observations while review is pending. |
| `POST` | `/triage/{id}/follow-up` | Create a veterinarian-linked follow-up after accepted/modified review. |
| `GET` | `/export` | Export the complete current owner record. |
| `DELETE` | `/data` | Terminally delete all locally owned care records and return counts. |

`GET /healthz` is the local readiness endpoint. Go request/response types and
HTTP contract tests are the executable field and error schema. The synchronized
`0.1.0` surface covers all five released simulation slices. External provider,
production persistence, authentication, and clinical-validation adapters remain
outside this local contract.

## Errors

- `400`: malformed fields, missing RFC 3339 timezone, or invalid query policy.
- `404`: responsibility identity is unknown.
- `409`: the requested transition conflicts with current state.

## Authentication

Local mode is explicitly unauthenticated and binds to loopback by default. No
production issuer, audience, roles, permissions, or browser session contract has
been accepted. Production exposure is blocked until those mappings exist and
are enforced by the API and web channel.
