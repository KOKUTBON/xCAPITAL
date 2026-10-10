# J.P. Morgan–Style Platform Parity (xCAPITAL)

This document captures the JPM-style requirements we are implementing: tight bank integration, hybrid DIY + robo model, and proprietary research overlays.

## Guiding Principles
- Bank-first UX: instant transfers between Banking and Investing balances (no ACH delay for internal moves).
- Hybrid model: self-directed trading + automated investing with goal-based setup.
- Proprietary research mock layer: analyst ratings, price targets, focus list coverage.

## Feature Parity Matrix

### Self-Directed Investing (DIY)
- Assets: Stocks, ETFs, Mutual Funds, Options, Fixed Income (bonds/treasuries/CDs).
- Pricing:
  - $0 commission for stocks, ETFs, mutual funds.
  - $0.65 per options contract.
- Account minimum: $0.
- Fractional shares: allow dollar-based buys.
- Research & tools:
  - Portfolio Builder (diversification, sector/asset exposure, concentration risk).
  - Screeners (PE, yield, sector, rating, price target).
  - Proprietary research notes (ratings + targets + focus list).
  - Wealth Plan (net worth + goals + probability of success simulations).
- Limitations: no crypto or futures in the JPM profile.

### Automated Investing (Robo)
- Goal-based onboarding: Retirement + General Investing first.
- Model portfolios built from JPM-style ETFs (mocked).
- Automatic rebalancing.
- Fees/minimums:
  - 0.35% annual advisory fee (charged quarterly).
  - $500 minimum to open/activate an automated plan.

### Banking Integration (Chase-like)
- Banking module with cash balance.
- Instant internal transfers between Banking and Investing.
- Unified view of cash + investments + transfers.
- Rewards/bonuses scaffolding for “new money” funding incentives.

## Planned API Surface (Backend)
- Banking
  - GET /api/v1/banking/account
  - POST /api/v1/banking/transfer
  - GET /api/v1/banking/transfers
- Research (proprietary)
  - GET /api/v1/research/coverage
  - GET /api/v1/research/focus-list
- Screeners
  - POST /api/v1/screeners/query
- Portfolio Builder
  - POST /api/v1/portfolio/builder/analyze
- Wealth Plan
  - POST /api/v1/wealth/plan
  - GET /api/v1/wealth/plan
  - POST /api/v1/wealth/plan/{id}/simulate
- Rewards
  - GET /api/v1/rewards/offers
  - POST /api/v1/rewards/enroll
  - POST /api/v1/rewards/evaluate

## Defaults (Config)
- Auto-invest minimum: $500.
- Advisory fee: 35 bps annual, charged every 90 days.
- Wealth-plan simulation count: 1000 runs.
- Proprietary research mock: enabled.

