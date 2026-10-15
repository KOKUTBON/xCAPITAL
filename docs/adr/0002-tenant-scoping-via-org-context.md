# ADR 0002: Tenant Scoping via Org Context and Repository Filters

- Status: ACCEPTED
- Date: 2026-03-13
- Deciders: platform-architecture
- Related docs/issues: `docs/ARCHITECTURE.md`, `docs/TEST_STRATEGY.md`

## Context
xCAPITAL must enforce org isolation across portfolio, execution, reporting, notifications, and audit data. Multi-tenant leakage is a critical security and compliance risk.

## Decision
Adopt org-scoped access as a mandatory data-plane constraint:
1. Gateway forwards `X-Org-Id` from JWT claims.
2. Services resolve org context in request filters and store it in request-local context.
3. Domain reads/writes use org-aware repository queries where entities include `org_id`.
4. Audit and notification records include org scope to preserve tenant-bound traceability.

## Consequences
### Positive
- Strong tenant isolation default for runtime reads/writes
- Consistent scoping model across services and modules
- Better auditability and incident investigation by org

### Negative
- Additional query variants and validation logic in repositories/services
- Higher risk of regressions if new endpoints omit org-aware paths

## Alternatives Considered
1. User-only scoping without org context: rejected because users can belong to multiple orgs.
2. Separate database per tenant: rejected for current stage due operational complexity and cost.

## Follow-up
- Expand org-scope regression tests for repositories and critical APIs.
- Keep org context mandatory for new persistence entities.
