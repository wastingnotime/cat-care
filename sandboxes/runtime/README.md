# Cat Care local runtime

This sandbox composes the API and web source projects for local development. It
does not own production deployment or promotion.

## Start

Install web dependencies once:

```bash
cd apps/web
npm install
```

Then run:

```bash
python3 sandboxes/runtime/tools/run-local.py
```

Endpoints:

- Web: `http://127.0.0.1:5173`
- API: `http://127.0.0.1:8080`
- API readiness: `http://127.0.0.1:8080/healthz`

Configuration:

- `CAT_CARE_API_ADDR` defaults to `127.0.0.1:8080` in the local launcher.
- `CAT_CARE_API_URL` defaults to `http://127.0.0.1:8080` for the SolidStart BFF.
- `CAT_CARE_WEB_PORT` defaults to `5173`.

Stop with `Ctrl-C`. Development state is in-memory and starts fresh with the Go
service.

## Validate

```bash
cd apps/api && go test ./...
cd ../web && npm run typecheck && npm run build && npm run test:e2e
```

The browser test must run while the local composition is available and uses the
real API rather than a browser mock. It uses `/usr/bin/chromium` when available;
set `CAT_CARE_CHROMIUM_PATH` to select another installed Chromium executable.
