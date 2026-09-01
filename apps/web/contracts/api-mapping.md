# Web-to-API mapping

The browser reads `/cat`, `/status`, `/responsibilities`, and `/timeline` on
load. After creating or completing a responsibility it repeats all four reads.
This refresh behavior prevents browser state from becoming an alternative
source of domain truth and makes completion/status changes visible without a
full-page navigation.

The default local API origin is `http://127.0.0.1:8000`. A developer may set the
`catCareApiUrl` local-storage value for another local endpoint. Authentication
is intentionally absent in local mode and is not a production precedent.
