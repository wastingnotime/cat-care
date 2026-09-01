# Cat Care local runtime

This sandbox composes the API and web source projects for local development. It
does not own production deployment or promotion.

## Start

Install Air once if needed:

```bash
go install github.com/air-verse/air@latest
```

Then start both apps from the repository root:

```bash
make dev
```

`make dev` installs current web dependencies, runs the Go API under Air, and
runs the SolidStart development server. Use `make dev-api` or `make dev-web`
when only one surface is needed.

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
make test
```

The browser test must run while the local composition is available and uses the
real API rather than a browser mock. It uses `/usr/bin/chromium` when available;
set `CAT_CARE_CHROMIUM_PATH` to select another installed Chromium executable.
