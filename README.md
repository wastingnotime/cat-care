# Cat Care

Cat Care is a calm, web-first care companion for individual cat owners.

The domain is being discovered through the repository-owned simulation under
[`sandboxes/simulation/`](sandboxes/simulation/). The simulation is separate
from the future production web application and is intended to validate care
states, transitions, uncertainty, and timelines before implementation.

The released model is materialized as a locally runnable API and web client:

```bash
python3 -m venv .venv
.venv/bin/pip install -e 'apps/api[test]'
.venv/bin/python sandboxes/runtime/tools/run-local.py
```

Then open `http://127.0.0.1:5173`. Local data is stored in
`.local/cat-care.db`. See [`sandboxes/runtime/`](sandboxes/runtime/) for runtime
configuration and validation commands.
