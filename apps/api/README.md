# Cat Care API

Go service materialized from the Cat Care simulation model `0.1.0`, following
the WNT service shape used by Community Lab. Domain behavior, application use
cases, in-memory infrastructure, HTTP translation, and runtime composition have
explicit package boundaries. It does not import the simulation implementation.

## Run

```bash
cd apps/api
go run ./cmd/api
```

The API listens on `http://127.0.0.1:8080`. Override the address with
`CAT_CARE_API_ADDR`. Local state is deterministic and in-memory. Local
development is intentionally unauthenticated; this is not a production identity
contract.

```bash
go test ./...
```
