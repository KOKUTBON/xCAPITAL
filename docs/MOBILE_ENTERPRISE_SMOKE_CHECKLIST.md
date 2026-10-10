# Mobile Enterprise Smoke Checklist

## Scope
Manual smoke checklist for mobile enterprise modules after UX parity updates.

## Run Context
- App: `mobile/xcapital_app`
- API base: `API_BASE` should point to gateway (`/api/v1/**`)
- Build: latest `main`
- Date: `2026-03-13`

## Checklist

| Module | Route | Minimum workflow | Result | Screenshot |
| --- | --- | --- | --- | --- |
| Login/Auth | `/login` | Register/login + MFA prompt/verify flow | Planned | `docs/screenshots/mobile-enterprise/login.png` |
| Portfolio Lab | `/portfolio-lab` | Submit optimize + risk calls and display output | Planned | `docs/screenshots/mobile-enterprise/portfolio-lab.png` |
| Portfolio Builder | `/portfolio-builder` | Analyze holdings and show diagnostics | Planned | `docs/screenshots/mobile-enterprise/portfolio-builder.png` |
| Market Data | `/market-data` | Quotes/history load + license/entitlement management | Planned | `docs/screenshots/mobile-enterprise/market-data.png` |
| Manual Trade | `/manual-trade` | Preview/review/place order workflow | Planned | `docs/screenshots/mobile-enterprise/manual-trade.png` |
| Auto Invest | `/auto-invest` | Create plan + run + notifications panel refresh | Planned | `docs/screenshots/mobile-enterprise/auto-invest.png` |
| Banking | `/banking` | Load balances + submit transfer | Planned | `docs/screenshots/mobile-enterprise/banking.png` |
| Statements | `/statements` | Ledger/tax lot/corporate action CRUD paths | Planned | `docs/screenshots/mobile-enterprise/statements.png` |
| Wealth Plan | `/wealth-plan` | Create plan + simulate | Planned | `docs/screenshots/mobile-enterprise/wealth-plan.png` |
| Screeners | `/screeners` | Query and inspect results | Planned | `docs/screenshots/mobile-enterprise/screeners.png` |
| Research | `/research` | Create/list/AI refresh notes | Planned | `docs/screenshots/mobile-enterprise/research.png` |
| Watchlists | `/watchlists` | Create list + add items + AI insights refresh | Planned | `docs/screenshots/mobile-enterprise/watchlists.png` |
| Alerts | `/alerts` | Create/update/trigger alert | Planned | `docs/screenshots/mobile-enterprise/alerts.png` |
| Rewards | `/rewards` | Offers load + enroll + evaluate | Planned | `docs/screenshots/mobile-enterprise/rewards.png` |
| Best Execution | `/best-execution` | Load best execution records | Planned | `docs/screenshots/mobile-enterprise/best-execution.png` |
| Surveillance | `/surveillance` | Load alerts with limit filter | Planned | `docs/screenshots/mobile-enterprise/surveillance.png` |
| Audit Log | `/audit` | Load recent audit events | Planned | `docs/screenshots/mobile-enterprise/audit-log.png` |
| Simulation | `/simulation` | Submit backtest + poll result + quota check | Planned | `docs/screenshots/mobile-enterprise/simulation.png` |
| AI | `/ai` | Forecast/risk/evaluation/model register flows | Planned | `docs/screenshots/mobile-enterprise/ai.png` |
| Org Admin | `/org-admin` | Summary + audit events load | Planned | `docs/screenshots/mobile-enterprise/org-admin.png` |

## Notes
- Route reachability is enforced by automated test: `mobile/xcapital_app/test/home_route_parity_test.dart`.
- Update `Result` from `Planned` to `Pass`/`Fail` after manual smoke run and attach screenshots at the listed paths.
