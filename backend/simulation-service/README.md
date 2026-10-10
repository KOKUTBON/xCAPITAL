# Simulation Service

Minimal backtest service (async + persisted). The API supports simple BUY_AND_HOLD and DCA runs.

Production upgrades:
- Accept richer strategy definitions (momentum, mean reversion, factor tilts).
- Run backtests on historical datasets (market data store).
- Persist results + risk metrics with auditability.
- Use async jobs (Spring Batch / Quartz / Redis streams).
- Enforce per-user quotas + tenant isolation (baseline limits now available).
