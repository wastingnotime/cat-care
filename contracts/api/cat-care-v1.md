# Cat Care HTTP API v1

The API is authoritative for care state. The web application consumes
this contract and must refresh reads after commands rather than deriving domain
transitions locally.

API base path: `/v1`. The SolidStart BFF maps browser `/api/*` requests to this
surface.

| Method | Path | Meaning |
|---|---|---|
| `POST`, `GET`, `DELETE` | `/session` | Log on, read the current session, or log out. |
| `GET`, `POST` | `/cats` | List accessible cats or add a cat to the owner account. |
| `POST` | `/cats/{id}/select` | Select the cat that scopes subsequent care requests. |
| `GET` | `/cat` | Read the selected cat identity. |
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

Local mode provides deterministic password identities and opaque, HTTP-only,
same-site sessions. `owner@cat.care` / `owner` opens owner mode and
`vet@cat.care` / `vet` opens veterinarian mode. These are development fixtures,
not production credentials.

Every protected request is scoped to the session's selected cat. Owners list
and create cats only in their own account, operate care records, and may request
provisional triage. Veterinarians may list all locally available cats and read
their records, but only veterinarian mode may review triage, request more
information, or define a reviewed follow-up. The API derives veterinarian
identity from the session rather than trusting a command field.

No production issuer, discovery URL, audience, client registration, redirect
URI, token validation, or refresh policy has been accepted. Production exposure
remains blocked until those mappings replace the local identity fixture.
