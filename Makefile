.PHONY: dev dev-api dev-web setup reset-seed test

dev: setup
	@command -v air >/dev/null || { echo "air is required: go install github.com/air-verse/air@latest" >&2; exit 1; }
	@python3 sandboxes/runtime/tools/run-local.py

dev-api:
	@command -v air >/dev/null || { echo "air is required: go install github.com/air-verse/air@latest" >&2; exit 1; }
	@cd apps/api && air -c .air.toml

dev-web: setup
	@cd apps/web && npm run dev -- --host 127.0.0.1 --port $${CAT_CARE_WEB_PORT:-5173}

reset-seed:
	@set -eu; cookie_file=$$(mktemp); trap 'rm -f "$$cookie_file"' EXIT; api_url=$${CAT_CARE_API_URL:-http://127.0.0.1:8080}; \
		curl --fail --silent --show-error -c "$$cookie_file" -H 'Content-Type: application/json' -d '{"email":"owner@cat.care","password":"owner"}' "$$api_url/v1/session" >/dev/null; \
		curl --fail --silent --show-error -b "$$cookie_file" -X POST "$$api_url/v1/dev/reset-seed"; echo

setup: apps/web/node_modules/.package-lock.json

apps/web/node_modules/.package-lock.json: apps/web/package.json apps/web/package-lock.json
	@cd apps/web && npm ci

test:
	@cd apps/api && go test ./...
	@cd apps/api && go vet ./...
	@cd apps/web && npm run typecheck
	@cd apps/web && npm run build
