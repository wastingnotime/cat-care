# Cat Care local runtime

This sandbox composes the API and web source projects for local development. It
does not own production deployment or promotion.

## Start

Install API dependencies once if they are not already available:

```bash
python3 -m venv .venv
.venv/bin/pip install -e 'apps/api[test]'
```

Then run:

```bash
.venv/bin/python sandboxes/runtime/tools/run-local.py
```

Endpoints:

- Web: `http://127.0.0.1:5173`
- API: `http://127.0.0.1:8000`
- API readiness: `http://127.0.0.1:8000/healthz`

Configuration:

- `CAT_CARE_DB_PATH` defaults to `.local/cat-care.db`.
- `CAT_CARE_API_PORT` defaults to `8000`.
- `CAT_CARE_WEB_PORT` defaults to `5173`.
- `CAT_CARE_WEB_ORIGINS` defaults to both localhost forms on port `5173`.

Stop with `Ctrl-C`. SQLite state survives restarts. Remove the explicit local
database file when a fresh local state is desired.

## Validate

```bash
PYTHONPATH=apps/api/src python3 -m pytest apps/api/tests -q
cd apps/web && npm install && npm run test:e2e
```

The browser test must run while the local composition is available and uses the
real API rather than a browser mock. It uses `/usr/bin/chromium` when available;
set `CAT_CARE_CHROMIUM_PATH` to select another installed Chromium executable.
