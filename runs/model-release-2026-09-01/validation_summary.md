# Model release validation summary

Date: 2026-09-01

## Deterministic tests

Command:

```bash
PYTHONPATH="$HOME/.wnt/runtime/mrl:sandboxes/simulation/src" \
  python3 -m pytest sandboxes/simulation/tests -q
```

Result: **93 passed in 0.12s**.

## Scenario evidence

- Run ID: `cat-care-first-slice`
- Seed: `7`
- Simulated interval: `2026-01-01T09:00:00+00:00` through
  `2026-01-05T12:00:00+00:00`
- Observations: `175`
- Distinct observation names: `90`
- Scheduled actions: `26`
- Use-case invocations: `27`
- Commands: `77`
- Queries: `10`
- Domain events: `14`
- Semantic observations: `10`
- Rejected commands: `2`
- JSONL SHA-256:
  `0542dc29ba113e9101eac6cf5bc279db11b050602637f25d420c56e29ba5f4d0`
- Replayable evidence: `scenario-observations.jsonl`

Both monitored invariants remained true:

- `completed_responsibility_is_not_overdue`
- `timeline_is_newest_first`

## Refinement during EGD

The first candidate run revealed a false timeline-invariant transition when
events shared a timestamp. The domain deliberately orders equal-time events by
event type and identity fields, while the monitor compared timestamps alone.
The monitor now evaluates the canonical deterministic key, and its test requires
every emitted result—not merely one result—to pass. The evidence above is from
the refined run.
