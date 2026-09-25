# Sandbox store delivery

The website's membership and credit checkout uses a separate authenticated delivery bridge. This release accepts **sandbox receipts only**; it does not enable live payments.

Set `SMASH_STORE_SANDBOX=true` in the deployment environment to enable the lobby's private `/store/delivery` route. It is disabled by default. The proxy exposes the same route only on its authenticated admin listener, which stays bound to loopback on the host. Never expose that port publicly.

Each receipt contains an external ID, authenticated Java UUID, credit amount, paid-through membership timestamp and `mode: test`. `memberUntil: -1` leaves membership unchanged. A SQLite transaction applies the credit and stores the receipt together. Retrying an already committed ID returns its current wallet balance without crediting again; a conflicting player or amount is rejected.

Ringshift Plus temporarily grants access to the five existing alternate skins and adds a `[PLUS]` badge to the lobby player list. Leased skin access expires at the paid-through time, including when the website is unavailable. Permanent cosmetic ownership is stored separately and survives expiration. Paid credits use the existing Points balance and do not count as matches or wins.

The local website repository includes `scripts/store-bridge.py`. It polls the website using its separate bridge secret, submits receipts to this private admin endpoint, and acknowledges website delivery only after the wallet commits. Use an SSH forward for testing from another machine. The website receipt shows pending until that acknowledgment arrives.

Refunds and disputes stop pending credit delivery and queue membership revocation. Already delivered credits require operator reconciliation. The bridge is currently a local sandbox helper, not a continuously deployed payment service. Production checkout requires a reachable signed Stripe webhook and supervised delivery bridge before taking real money.

`StoreDeliveryTest` covers restart/retry deduplication, conflicting receipts, membership expiry and preservation of permanent skins. Existing match and economy tests cover the shared wallet.
