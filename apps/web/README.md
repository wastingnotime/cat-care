# Cat Care web

Thin browser client for the Cat Care API. It owns presentation, interaction,
accessibility, and browser refresh behavior; the API remains authoritative for
care state.

## Run

With the API listening on port `8000`:

```bash
python3 -m http.server 5173 --directory apps/web/client
```

Open `http://127.0.0.1:5173`. The client reloads status, responsibilities, and
history after every successful command, so no domain transition is inferred in
browser state.
