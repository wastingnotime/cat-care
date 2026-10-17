# WNT-145 baseline and production evidence

Assessed 2026-10-16 (America/Sao_Paulo) against
`3924d52d986e2ccd837746c533b7afd49e1db343`. No runtime implementation changes
are part of this assessment. [Scope ADR](../adr/0001-owner-mvp.md).

## Architecture at the baseline

| Boundary | Source | Observed behavior |
| --- | --- | --- |
| Go domain | `apps/api/internal/domain/{care,records}.go` | Responsibility transitions, derived status, profile validation and record types |
| Go application | `apps/api/internal/application/{service,features}.go` | Use cases; Repository Load/Save, Clock and IDs ports; per-request cat context |
| Memory | `apps/api/internal/infrastructure/memory.go` | Per-cat state, ownership, invitations, assignments; process-local mutexes and IDs |
| HTTP | `apps/api/internal/httpapi/{server,features,auth,veterinarians}.go` | `/v1` reads/commands, fixture sessions and current ownership/assignment checks |
| Runtime | `apps/api/internal/runtime/runtime.go`, `cmd/api/main.go` | One Service and memory repository, reset/seed, standard Go HTTP process |
| Web/BFF | `apps/web/src/routes/api/[...path].ts`, `src/lib/api.ts` | Same-origin `/api` maps to `/v1`, forwards cookie/content-type and Set-Cookie; upstream failure becomes 503 |
| Presentation | `apps/web/src/routes/index.tsx`, `account.tsx`, `cats/`, `triage.tsx` | Today combines explicit per-cat reads; refresh after commands; owner and vet workspaces |
| Model | `sandboxes/simulation/src/app/simulation/`, `interfaces/` | Independent Python semantics and deterministic provider adapters; no production Go dependency |
| Local composition | `Makefile`, `sandboxes/runtime/tools/run-local.py` | Air + Go + SolidStart development processes; loopback launcher defaults |

The Go runtime defaults to `:8080` if started directly without configuration;
the local launcher supplies loopback. This is not a production network policy.
The BFF allows GET/POST/PUT/DELETE and forwards only selected headers. Mobile
auth, idempotency headers, version headers and response metadata will require an
explicit end-to-end contract if introduced.

## External service evidence register

| Item | Evidence available | Finding / evidence needed |
| --- | --- | --- |
| Hosting, DNS, TLS and deployment | Local runtime configuration only; no tracked deployment/IaC/workflow definition found | Live host, environment, region, artifact and operator unknown; obtain deployment record/control-plane resource reference |
| Database, backups and migrations | Runtime constructs MemoryRepository and ResetSeed | Local storage is volatile. External database and restore capability unknown; obtain resource/schema and restore evidence |
| Identity | `auth.go` fixed owner/vet users and in-memory opaque sessions | External issuer, tenant, audience, client and account owner unknown; obtain registration and lifecycle policy |
| Notification provider | `RecordNotification`, provider `local-fake` | No actual delivery, scheduler or provider account proven; obtain provider registration and delivery receipt |
| Clinical provider | `RequestTriage`, provider `local-triage-fake`, version `development-1` | No clinical service or validation proven; provider adoption deferred |
| Bills and cost ownership | No attributed invoice, billing export or Cat Care resource allocation supplied | Amount, currency, billing period, payer and budget unknown; obtain redacted bill plus resource allocation, including shared costs |

Absence of repository configuration is not evidence that external services do
not exist. No cloud inventory or billing account was queried in this assessment.
A request for external service/evidence locations was made during WNT-145;
without attributable evidence these entries remain unknown. Do not record zero
cost or create duplicate infrastructure from this register.

## API schema and compatibility

The route inventory is [HTTP v1](../../contracts/api/cat-care-v1.md). Exact JSON
fields come from Go HTTP command structs, `application.State`, domain structs
and their JSON tags. `apps/web/src/lib/contracts.ts` is a manually maintained
consumer projection, not a generated authoritative schema. No OpenAPI schema,
generated client, schema compatibility check or version negotiation is present.

| Surface | Current request / response |
| --- | --- |
| Profile | name, nullable birth_date/adoption_date (`YYYY-MM-DD`), optional photo_ref; response is Profile |
| Responsibility create/edit | title, category, nullable due_at (timezone-aware RFC3339), recurrence_days/recurrence_months; response includes ID, state, derived_state, timestamps and optional recurrence/action_key |
| Complete/cancel | ID in path; response is responsibility view; completion may create the next recurrence |
| Defer | due_at; must be later than now and existing due date; remains planned |
| Notes | description; generated ID/time, is_diagnosis=false |
| Direct care | type, description, optional responsibility_id; generated ID/time; baseline accepts any nonempty type, not an enforced enum |
| Export | State: cat, optional veterinarian_id, responsibilities, events, notes, direct_care, notifications, triage_assessments, veterinarian_reviews, information_requests, deleted/deleted_at |
| Delete | deleted_at and per-record removal counts; no account deletion receipt |

JSON decoding rejects unknown fields in command handlers. Decoder helpers read
one JSON value; do not assume exhaustive body validation or payload limits.
Empty collections can serialize as null after slice copies, so clients must
not assume every collection is always `[]` without a compatibility decision.

Errors generally use `{code,message}`: 400 invalid_request; 401
unauthenticated/invalid_credentials; 403 forbidden; 404 not_found; 409
conflict/cat_required; 500 internal_error. BFF transport failures use 503
api_unavailable. Profile validation currently maps invalid transitions to 409,
so broad prose claiming all malformed field values yield 400 is insufficient.
Unknown routes/methods can use standard Go responses, not the JSON envelope.
`POST /session/workspace` and local-only `POST /dev/reset-seed` are additional
baseline routes. Reset/login fixtures must not be available in production.

## Atomicity and retries

Most state-mutating care methods acquire `Service.mu` across Load/Save. Two
such commands through the same Service are serialized. The earlier assessment's
blanket claim that all local care writes lack a command lock is too broad.

The repository still has no version, compare-and-swap or transactional command
contract. AssignVeterinarian and ResetSeed bypass Service.mu. Example:
UpdateProfile loads cat A; AssignVeterinarian saves a new assignment under the
repository mutex; UpdateProfile saves its old whole State and overwrites the
assignment. A second Service sharing a repository also has a different mutex.
Separate locks on Load and Save do not protect these interleavings. Production
must coordinate assignment, deletion and care mutations in the database.

CreateResponsibilityWithPolicy performs create then edit in separate locked
commands. Invalid recurrence can leave a newly created responsibility even
when the request fails. Reads can observe that intermediate state. Require one
atomic create with policy before promising all-or-nothing commands.

There is no request idempotency key/deduplication store. A lost create response
and retry can duplicate responsibilities, notes, direct care or follow-up.
A repeated completion conflicts rather than replaying success; recurrence
must not be regenerated by retry. PUT edits add events and are not audit-neutral
on repetition. Deletion tombstones the cat; a subsequent request may select
another active cat, making an implicit-cat retry particularly unsafe.

Production gate: explicit cat scope, atomic command+history+recurrence, a
persisted request identity scoped to account/cat/operation, payload mismatch
rejection, safe replay after timeout/restart, and documented retention and
conflict behavior. Do not blindly retry ambiguous writes. Refresh/read and show
uncertainty until a command identity can resolve the outcome.

## Session lifetime and release gates

`auth.go` generates a 24-byte random opaque token and stores Session in memory.
Cookie is HttpOnly, SameSite=Lax, Path=/; it has no Secure, MaxAge or Expires.
The browser treats it as a session cookie; server entries have no idle/absolute
expiry. Logout removes one token, restart removes all tokens. No production
refresh, issuer validation, revocation service or mobile token contract exists.
Assignment visibility is refreshed per authenticated request, but transactional
rechecking with writes remains a production requirement.

Before production: choose web/mobile identity flows and expiry/revocation;
enforce secure transport/cookies and relevant request protections; remove demo
identities/reset; prove authorization, durable migrations/backup/restore and
concurrency; define account export/deletion and backup expiry; verify real
notification delivery; provide observability, billing alerts, immutable builds,
release rollback and a PT-BR/accessibility journey against the real API.

Historical September 1 evidence records Go/Python/web/build/browser success;
it predates current invitation changes and is not a fresh release certificate.
No deployment/promotion pipeline or immutable production release evidence is
tracked at this baseline. Current assessment validation is recorded in the
WNT-145 change record.

## Documentation corrections

At the baseline, `apps/api/README.md` calls local development unauthenticated;
`apps/web/contracts/api-mapping.md` says authentication is absent. Both are stale:
the runtime uses NewLocalServer and fixture sessions. They are corrected with
this ADR. The HTTP contract's complete-owner export/all-record deletion wording
is corrected to the observed per-cat scope. Broader Account UI wording remains
an implementation gap covered by the acceptance fixtures.
