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

The logon screen offers local owner (`owner@cat.care` / `owner`) and
veterinarian (`vet@cat.care` / `vet`) identities. Owners can maintain separate
care records for multiple cats; veterinarians can switch among available cats
in a role-limited review workspace. This local fixture is not production auth.
