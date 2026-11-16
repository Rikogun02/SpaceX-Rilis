# SpaceX Rilis — implementation status

## What the current build does
- Fetches latest Solana token profiles from DexScreener and resolves market pairs.
- Refreshes on a 30-second foreground UI polling loop while the app is open.
- Opens a best-effort PumpPortal WebSocket stream with reconnect backoff; REST polling is the fallback.
- Calculates a transparent heuristic score from liquidity, one-hour volume, five-minute buy/sell balance and short-term price movement.
- Lets the user request a RugCheck summary. Missing RugCheck data is shown as unverified, never treated as safe.
- Provides a local, persistent paper-trading balance and positions. It does not sign or submit blockchain transactions.

## Important limitations
- DexScreener latest token profiles are discovery signals, not a complete Pump.fun feed. Pair data may be absent for brand-new tokens.
- PumpPortal message schemas and access limits may change; WebSocket events are currently used as connection/event status, while token enrichment comes from DexScreener polling.
- The heuristic score is not a probability of profit and is not a RugCheck score. It is not calibrated against historical outcomes.
- RugCheck raw score is displayed as supplied; the app does not assume score direction or invent a normalized risk score.
- Holder concentration, wallet clustering, creator history, LP lock/burn verification and bundle detection are not yet verified by this build.
- Polling every 30 seconds occurs while the app process is active. Android may suspend background activity; this is not a guaranteed always-on background service.
- Paper positions are stored locally on the device. Price snapshots are not continuously marked to market.

## Safety
- Simulation only. No private key, seed phrase, signing, swap or live-buy path is implemented.
- Use independent research and verify contract/mint addresses before any real-world decision.
