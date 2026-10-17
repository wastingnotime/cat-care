# Cat Care

Cat Care is a calm, web-first care companion for individual cat owners.

The domain is being discovered through the repository-owned simulation under
[`sandboxes/simulation/`](sandboxes/simulation/). The simulation is separate
from the future production web application and is intended to validate care
states, transitions, uncertainty, and timelines before implementation.

The released model is materialized as a locally runnable API and web client:

```bash
make dev
```

Then open `http://127.0.0.1:5173`. The development API uses in-memory state and
resets when the runtime stops. See [`sandboxes/runtime/`](sandboxes/runtime/)
for runtime configuration and validation commands.

The logon screen offers a local owner (`owner@cat.care` / `owner`) and a
dual-role veterinarian (`vet@cat.care` / `vet`). Veterinarians can switch
between their owner workspace and the role-limited review workspace. This local
fixture is not production auth.

To connect a veterinarian in the local demo, the owner sends an invitation from
Account & data to `vet@cat.care`. The veterinarian accepts it in the review
workspace. The owner can then assign Dr. Silva on an individual cat profile.
Only assigned cats appear in the veterinarian workspace.

## Owner MVP scope and production status

[ADR 0001](docs/adr/0001-owner-mvp.md) freezes the owner MVP and preserves
assigned-veterinarian web continuity. The
[production evidence register](docs/assessment/wnt-145-production-gaps.md)
distinguishes the local prototype from unverified external services;
[acceptance fixtures](contracts/acceptance/owner-mvp.md) define delivery gates.
