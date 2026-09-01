# Cat Care API

Go service materialized from the Cat Care simulation model `0.1.0`, following
the WNT service shape used by Community Lab. Domain behavior, application use
cases, in-memory infrastructure, HTTP translation, and runtime composition have
explicit package boundaries. It does not import the simulation implementation.

## Run

```bash
cd apps/api
air -c .air.toml
```

The API listens on `http://127.0.0.1:8080`. Override the address with
`CAT_CARE_API_ADDR`. Local state is deterministic and in-memory. Local
development is intentionally unauthenticated; this is not a production identity
contract.

Air rebuilds and restarts the service when Go source changes. Install it with
`go install github.com/air-verse/air@latest` if it is not already available.

The service synchronizes the five released model slices: responsibility status
and recurrence, profile and typed care history, notification/deferral,
provisional triage with veterinarian review, and data export/deletion. The local
triage and notification providers are deterministic fakes; they do not represent
clinical validation or production delivery.

```bash
go test ./...
```
