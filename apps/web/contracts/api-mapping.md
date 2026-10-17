# Web-to-API mapping

The browser reads `/cat`, `/status`, `/responsibilities`, and `/timeline` on
load. After creating or completing a responsibility it repeats all four reads.
This refresh behavior prevents browser state from becoming an alternative
source of domain truth and makes completion/status changes visible without a
full-page navigation.

Interactive entry controls remain disabled until SolidStart hydration completes
so an SSR-visible control cannot accept and lose an early browser interaction.

The SolidStart BFF maps same-origin `/api/*` calls to `/v1/*` at
`CAT_CARE_API_URL`, which defaults to `http://127.0.0.1:8080`. The BFF forwards the local session cookie and upstream Set-Cookie header.
The runtime authenticates fixed demo identities; this is not production auth.
Today reads status, responsibilities, timeline, notes and triage for each
accessible cat with explicit `cat_id`; account export/deletion currently use
the active cat. See [ADR 0001](../../../docs/adr/0001-owner-mvp.md).
