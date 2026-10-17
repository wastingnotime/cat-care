# Owner MVP acceptance fixtures

WNT-145 / ADR 0001. These are reproducible scenario specifications, not claims
that new automated tests or production capabilities already pass. Use synthetic
data only. Each scenario starts from a fresh fixture unless stated otherwise.

## Shared fixture

Clock: `2026-10-16T12:00:00Z` (09:00 America/Sao_Paulo). Owner O owns A/Mimi and
B/Luna; owner P owns C/Nina. V is a veterinarian with an independent owner
workspace. IDs are symbolic: capture generated IDs from responses. No vet is
initially invited/assigned. Use explicit `cat_id` for every cat command. Start
with no responsibilities, observations or care records. Local reset-seed gives
analogous cat-1/cat-2/cat-3 and fixed demo users; an independent owner P requires
a test adapter, not an invented third fixture login.

| ID | Given / action | Required observations | Baseline or delivery gate |
| --- | --- | --- | --- |
| F01 | O creates/edits A profile, then reads A and B | Valid dates persist on A only; adoption before birth rejected; B unchanged; P cannot access A | Existing local semantics; repeat on production identity/storage |
| F02 | A has a responsibility due `2026-10-17T12:00:00Z`; B has an undated one; open Today | Both cat identities visible; A due soon at threshold 2; B scheduling uncertainty; no diagnosis inferred from scheduling | Existing local behavior; GUI/guided/hybrid use identical state |
| F03 | Create A responsibility `Annual exam`, category `veterinary`, due `2026-10-20T12:00:00-03:00` | Planned record and creation history on A; timestamp preserves instant; no B mutation; offsetless time rejected | Existing local schema |
| F04 | Complete A monthly responsibility due Jan 31 at 09:00, with clock before that due date | One completion plus exactly one successor at Feb 28 09:00 with policy retained; B unchanged | Existing calendar-month policy; durable atomicity remains a gate |
| F05 | Defer planned A task from Oct 20 to Oct 22, both at 12:00Z | Remains planned, records deferral; earlier/equal due time or time before now rejected; notification does not complete it | Existing local semantics |
| F06 | Record A direct care `{type:"measurement",description:"Weight 4.2 kg"}` and note `{description:"Ate less this morning"}` | Both appear in A history; note is_diagnosis=false; responsibility remains planned; B unchanged | Existing local semantics; finite care-type schema decision still needed |
| F07 | O invites V; V accepts; O assigns V to A; V reads A/B; O removes assignment | Pending/accepted invite alone exposes no cats; assignment exposes A only; removal removes access using the existing session; owner cannot submit vet decisions | Existing local authorization; production concurrency gate |
| F08 | Synthetic provisional assessment on A; V asks for observation; O replies; V modifies review then defines follow-up | Question/reply attribution and review remain distinct from provisional result; follow-up linked to assessment; unassigned V denied; synthetic provider visibly identified | Preserve web continuity; clinical provider remains deferred |
| F09 | O exports/deletes A with B populated | Export includes A only; confirmation names A; deletion receipt counts match A; A unavailable afterwards; B and its history unchanged | API is per-cat; current Account UI wording fails scope clarity |
| F10 | O requests complete account export/deletion | Enumerate A and B; exclude P/C; clear owned data/relationships, revoke sessions; receipt and backup expiry explained; assigned vet has no account authority | Missing account endpoints/lifecycle; production gate |
| F11 | Hold profile update after Load; assign V to A; release Save; also run two service instances against shared storage | Both profile and assignment preserved or one explicit version conflict; no silent overwrite; revocation checked at commit | Known baseline risk; transaction/version gate |
| F12 | Send create with recurrence_days=-1; inspect state after rejection | No responsibility or history committed | Baseline create-then-edit can leave partial state; atomicity gate |
| F13 | Drop create/complete response after server commit; replay same command identity before and after restart | Same result/ID, one history effect and one recurrence successor; changed payload with same identity rejected; no B mutation | No baseline idempotency contract; production gate |
| F14 | Expire/revoke a web/mobile session and remove vet assignment during an in-flight write | Expired/revoked requests denied; no mutation after revocation commits; restart preserves care but does not resurrect revoked access | Production identity/transaction gate |
| F15 | Lose API connectivity during owner action | Visible failed/uncertain action; no false success or automatic ambiguous duplicate; cached reads identified as stale; manual/guided fallback preserves confirmation | Cloud-connected MVP requirement |
| F16 | Restart deployed service; restore a backup into isolated environment | Owned cats, histories, relationships and deduplication survive; deleted data follows declared retention; release artifact and restore evidence recorded | No baseline database/restore evidence |
| F17 | PT-BR owner completes F02/F03/F05/F06 using keyboard and screen reader | Cat/date/type and pending/error states understandable; no unauthorized/unconfirmed writes; equivalent commands in POC arms | Pilot accessibility/localization gate |

## Evidence protocol

Record source SHA, environment, fixture clock, actor/role, captured symbolic ID
mapping, exact requests/responses, before/after records and pass/fail/unknown for
each scenario. Never substitute a synthetic session for production identity
proof. For concurrency use barriers around Load/Save, not timing sleeps. For
retry tests drop the response after commit, not before delivery. Store redacted
service/billing evidence references with period and resource attribution.

Existing source tests live under `apps/api/internal/{domain,httpapi}`,
`apps/web/tests/care-flow.spec.mjs`, and `sandboxes/simulation/tests`. They cover
parts of these scenarios; this contract does not assert full coverage. POC
completion metrics and participant gates remain in the October 13 Linear
assessment and WNT-146; this fixture set supplies equal domain scope.
