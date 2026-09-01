# Web-to-API mapping

The browser reads `/cat`, `/status`, `/responsibilities`, and `/timeline` on
load. After creating or completing a responsibility it repeats all four reads.
This refresh behavior prevents browser state from becoming an alternative
source of domain truth and makes completion/status changes visible without a
full-page navigation.

The SolidStart BFF maps same-origin `/api/*` calls to `/v1/*` at
`CAT_CARE_API_URL`, which defaults to `http://127.0.0.1:8080`. Authentication is
intentionally absent in local mode and is not a production precedent.
