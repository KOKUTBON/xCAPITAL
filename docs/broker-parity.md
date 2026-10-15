# Broker Parity Matrix (Global, Multi-Asset)

This document maps common broker platform capabilities and how xCAPITAL covers them with automation + AI.

## Core Coverage

| Capability | Traditional Platforms | xCAPITAL Coverage |
| --- | --- | --- |
| Accounts (cash/margin/prime) | Yes | Modeled + syncable via broker connections |
| Positions & balances | Yes | Stored + reconciled per broker account |
| Orders (market/limit/stop/stop-limit) | Yes | Multi-asset order routing + order legs |
| Options (multi-leg) | Yes | Leg model + option metadata |
| Futures | Partial | Modeled + routing-ready |
| FX | Partial | FX instruments + rates + routing-ready |
| Crypto | Yes | Broker catalog + custody-ready models |
| Funding rails (ACH/wire) | Yes | Funding service scaffold + audit |
| Statements/tax | Yes | Ledger + tax lots + corporate actions + reconciliation implemented |
| Alerts/watchlists | Yes | Native alerts + watchlists + AI insights |
| Research/news | Yes | Research notes + AI summaries |
| Compliance/AML/KYC | Yes | Compliance profiles + audit logging |

## AI Enhancements Per Feature

- Order routing: broker recommendation scoring by asset class, region, order type, and account constraints.
- Risk-first optimization: volatility + drawdown forecasts used to size positions and constrain turnover.
- Alerts: AI context on volatility regime and downside risk.
- Watchlists: AI risk score and summary based on recent returns history.
- Portfolio monitoring: drift + scenario checks trigger proposals for approval.

## Notes

- Live broker integrations and market-data providers require approvals and credentials.
- The system is designed to run globally; regional routing is enforced by broker catalog + connection capabilities.
