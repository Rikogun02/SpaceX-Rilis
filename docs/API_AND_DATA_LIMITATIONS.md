# API and data limitations

## Discovery
- DexScreener latest token profiles are discovery hints, not a complete new-token firehose.
- PumpPortal WebSocket subscribes to new-token and migration events. Reconnect/backoff and polling fallback are required for production.
- DexScreener market endpoints are polled and cached; respect published rate limits.

## RugCheck
- The app requests the token report summary and preserves the raw score and risk findings.
- RugCheck's raw score is not treated as SpaceX quality score. Its scale/direction must be validated against current API documentation before normalization.
- Missing or unavailable risk data is UNVERIFIED and must never be interpreted as safe.
- Fields not explicitly returned by the endpoint remain null; no fabricated LP-lock, insider, authority, or concentration values.

## Solana RPC
- Custom RPC endpoint support belongs in settings. Public RPC may throttle.
- Holder authority counts are not beneficial-owner identity proof.
- Never request, store, or log seed phrases or private keys.

## Trading
- AUTO_BUY_SIMULATION is the only enabled mode in this release.
- No transaction signing, wallet secret storage, or live trade submission is implemented.
