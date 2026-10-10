# Design Principles

## Core Principles
- Correctness before convenience for financial state transitions.
- Explicit boundaries between domains, teams, and data ownership.
- Secure by default (auth, authz, tenant isolation, auditability).
- Observable by default (logs, metrics, traces, correlation IDs).
- Automatable by default (repeatable builds, migrations, tests, releases).

## Engineering Principles
- Keep domain invariants in application/domain layers, not controllers.
- Prefer additive API/schema evolution over breaking changes.
- Use idempotency for any external side-effect operation.
- Prefer composition over inheritance and small focused classes.
- Keep infrastructure concerns replaceable via adapters.

## Architecture Principles
- One service owns one domain model and write path.
- One domain database per capability in target state.
- Cross-domain data movement via events and contracts.
- Read models are disposable; source-of-truth stores are authoritative.

## Operational Principles
- Define SLOs before scaling decisions.
- Treat incident response as part of design, not afterthought.
- Budget for failure modes (timeouts, retries, compensation).
