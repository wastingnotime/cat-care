# Cat Care HTTP API v1

The API is authoritative for care state. The web application consumes
this contract and must refresh reads after commands rather than deriving domain
transitions locally.

API base path: `/v1`. The SolidStart BFF maps browser `/api/*` requests to this
surface.

Authenticated cat-scoped resources accept a `cat_id` query parameter. The API
verifies that the cat is available to the account and scopes only that request;
owners do not need to switch a global cat context.

| Method | Path | Meaning |
|---|---|---|
| `POST`, `GET`, `DELETE` | `/session` | Log on, read the current session, or log out. |
| `GET`, `POST` | `/cats` | List accessible cats or add a cat to the owner account. |
| `POST` | `/cats/{id}/select` | Remember the last-used cat for future action forms. |
| `GET` | `/veterinarians` | Owner lists invited veterinarians and their invitation status. |
| `POST` | `/veterinarians/invitations` | Owner invites a veterinarian by account email. |
| `GET` | `/invitations` | Veterinarian lists invitations addressed to their account. |
| `POST` | `/invitations/{ownerID}/accept` | Veterinarian accepts a pending owner invitation. |
| `PUT` | `/cats/{id}/veterinarian` | Owner assigns one accepted veterinarian to a cat, or clears the assignment. |
| `GET` | `/cat` | Read one cat identity, using `cat_id` or the last-used cat. |
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
| `POST` | `/triage/{id}/comments` | Add an owner reply to the shared triage thread. |
| `POST` | `/triage/{id}/follow-up` | Create a veterinarian-linked follow-up after accepted/modified review. |
| `GET` | `/export` | Export the request cat state; not an account export. |
| `DELETE` | `/data` | Terminally delete the request cat state and return counts; other cats and account remain. |

`GET /healthz` is the local readiness endpoint. Go request/response types and
HTTP contract tests are the executable field and error schema. The synchronized
`0.1.0` surface covers all five released simulation slices. External provider,
production persistence, authentication, and clinical-validation adapters remain
outside this local contract.

## Errors

- `400`: malformed fields, missing RFC 3339 timezone, or invalid query policy.
- `404`: responsibility identity is unknown.
- `409`: the requested transition conflicts with current state, including assignment before invitation acceptance.

## Authentication

Local mode provides deterministic password identities and opaque, HTTP-only,
same-site sessions. `owner@cat.care` / `owner` opens owner mode and
`vet@cat.care` / `vet` opens veterinarian mode. These are development fixtures,
not production credentials.

Every cat-scoped request is checked against current assignments, including requests from an existing session. Owners list
and create cats only in their own account, operate care records, and may request
provisional triage. An owner may invite a veterinarian to their account; acceptance makes that veterinarian available for assignment, but does not itself grant cat access. Each cat has at most one assigned veterinarian. Veterinarians may list and read only assigned cats, and only veterinarian mode may review triage, request more
information, or define a reviewed follow-up. The API derives veterinarian
identity from the session rather than trusting a command field.

The local demo resolves invitations against `vet@cat.care`. Invitations and assignments are in memory. Removing an assignment immediately removes that cat from the veterinarian workspace.

No production issuer, discovery URL, audience, client registration, redirect
URI, token validation, or refresh policy has been accepted. Production exposure
remains blocked until those mappings replace the local identity fixture.

## MVP assessment

Export/deletion use `cat_id` or the session active cat. Account lifecycle,
transactional updates, retry identities and production sessions remain release
gates in [ADR 0001](../../docs/adr/0001-owner-mvp.md) and the
[baseline assessment](../../docs/assessment/wnt-145-production-gaps.md).
The [acceptance fixtures](../acceptance/owner-mvp.md) distinguish current
semantics from delivery requirements.
