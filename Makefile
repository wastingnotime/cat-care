.PHONY: dev dev-api dev-web setup test

dev: setup
	@command -v air >/dev/null || { echo "air is required: go install github.com/air-verse/air@latest" >&2; exit 1; }
	@python3 sandboxes/runtime/tools/run-local.py

dev-api:
	@command -v air >/dev/null || { echo "air is required: go install github.com/air-verse/air@latest" >&2; exit 1; }
	@cd apps/api && air -c .air.toml

dev-web: setup
	@cd apps/web && npm run dev -- --host 127.0.0.1 --port $${CAT_CARE_WEB_PORT:-5173}

setup: apps/web/node_modules/.package-lock.json

apps/web/node_modules/.package-lock.json: apps/web/package.json apps/web/package-lock.json
	@cd apps/web && npm ci

test:
	@cd apps/api && go test ./...
	@cd apps/api && go vet ./...
	@cd apps/web && npm run typecheck
	@cd apps/web && npm run build
