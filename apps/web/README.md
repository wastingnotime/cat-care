# Cat Care web

SolidStart browser application following the WNT pattern used by Community Lab.
It owns presentation, interaction, accessibility, and a same-origin BFF; the Go
API remains authoritative for care state.

## Run

With the API listening on port `8080`:

```bash
npm install
npm run dev -- --port 5173
```

Override the BFF upstream with `CAT_CARE_API_URL`. The client reloads status,
responsibilities, and history after every successful command, so no domain
transition is inferred in browser state.

The local experience includes profile editing, responsibility recurrence and
lifecycle controls, notes, direct care, notification outcomes, provisional
triage and veterinarian review, follow-up creation, export, and terminal local
deletion. Triage remains visibly non-diagnostic.
