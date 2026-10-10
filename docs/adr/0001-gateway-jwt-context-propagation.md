# ADR 0001: Gateway JWT Validation and Identity Context Propagation

- Status: ACCEPTED
- Date: 2026-03-13
- Deciders: platform-architecture
- Related docs/issues: `docs/ARCHITECTURE.md`

## Context
Clients call a single edge entrypoint. Downstream services need authenticated identity (`user`, `org`, `roles`, `mfa`) without duplicating full edge-routing concerns.

## Decision
Validate bearer JWTs at the gateway and propagate user/org context headers to downstream services:
- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`
- `X-User-Mfa`
- `X-Org-Id`
- `X-Org-Roles`

Downstream services may perform additional token checks where needed, but gateway remains the primary auth enforcement boundary.

## Consequences
### Positive
- Centralized edge auth policy and route protection
- Consistent identity context across portfolio/simulation/AI paths
- Simpler downstream authorization logic

### Negative
- Header contract coupling between gateway and downstream services
- Requires strict trust boundary so only gateway can set identity headers

## Alternatives Considered
1. Full JWT verification in every service only: rejected due duplication and drift risk.
2. Opaque session token with centralized introspection on each call: rejected due added latency and availability coupling.

## Follow-up
- Maintain explicit header contract documentation.
- Keep downstream optional defense-in-depth token validation where risk is high.
