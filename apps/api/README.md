# Cat Care API

FastAPI service materialized from the Cat Care simulation model `0.1.0`.
It owns the HTTP contract and local SQLite persistence; it does not import the
simulation implementation.

## Run

```bash
python3 -m venv .venv
.venv/bin/pip install -e 'apps/api[test]'
CAT_CARE_DB_PATH=.local/cat-care.db \
  .venv/bin/uvicorn cat_care_api.main:app --app-dir apps/api/src --reload
```

The API listens on `http://127.0.0.1:8000`. OpenAPI is available at `/docs`.
Local development is intentionally unauthenticated; this is not a production
identity contract.
