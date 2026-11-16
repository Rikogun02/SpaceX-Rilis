# SpaceX Rilis

Android Solana memecoin scanner prototype. Built with Kotlin, Jetpack Compose, Retrofit, OkHttp and coroutines.

## Features in the current debug build
- Solana token discovery from DexScreener latest profiles.
- Market-pair enrichment and 30-second foreground polling.
- Best-effort PumpPortal new-token and migration WebSocket subscriptions.
- On-demand RugCheck summary with raw and normalized values shown as returned by the API.
- Heuristic market-activity score.
- Local paper-trading balance and positions (default virtual balance: USD 1,000).

## Build
Open this repository in Android Studio or run:

```bash
gradle assembleDebug
```

APK CI artifacts are published from GitHub Actions:
https://github.com/Rikogun02/SpaceX-Rilis/actions

## Important
- The APK is a debug build, not a Play Store release.
- No live trading, wallet connection, transaction signing, seed phrase, or private-key collection exists.
- Paper trading is a local simulation only.
- PumpPortal currently documents a websocket URL with an API-key query parameter. This app intentionally does not embed any trading-capable key. If the unauthenticated connection is refused, use the DexScreener discovery fallback; coverage of brand-new Pump.fun tokens will be limited.
- Scanner polling is only guaranteed while the app remains active. Android background suspension can pause it.
- Holder concentration, creator-wallet history, bundle detection, LP lock verification, durable background alerts, and automated exit rules are not included in this build.
- Read [implementation status](docs/IMPLEMENTATION_STATUS.md) before relying on the displayed score.
