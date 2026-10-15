# Testing Strategy

## 1. Test Objectives
- Prove correctness of financial workflows and tenant boundaries.
- Prevent authz regressions in enterprise org-scoped APIs.
- Validate data integrity across trading, banking, and reporting domains.
- Gate releases with measurable quality criteria.

## 2. Test Pyramid
```mermaid
flowchart TB
  U["Unit Tests"] --> I["Integration Tests"]
  I --> C["API Contract Tests"]
  C --> E["End-to-End Gateway Tests"]
  E --> P["Performance/Security Tests"]
```

## 3. Layered Strategy

### Unit Tests
- Domain policies, calculators, fee logic, validation rules.
- Service-level decision logic (approval, limits, role checks).

### Integration Tests
- Repository behavior with org/user scoping.
- Flyway migration compatibility and rollback checks.
- Redis cache and queue interactions (where applicable).

### API Contract Tests
- Auth login/MFA/SSO/SCIM.
- Trading proposal -> decision -> execution intent.
- Banking transfer atomicity and balance invariants.
- Wealth plan simulation contract and constraints.

### End-to-End Tests
- Cross-service flow through gateway on dockerized stack.
- Critical user journeys on web and mobile clients.
- Execution smoke rejects pending/direct/synthetic bypasses, races two submissions against PostgreSQL, verifies one order set, and rejects another organization across proposal/intent/broker routes. See [boundary evidence](EXECUTION_APPROVAL_BOUNDARY.md).
- Gateway smoke script is versioned at `scripts/smoke/gateway_e2e_smoke.sh` (local + CI runnable).

### Non-Functional Tests
- Load test on read-heavy market and reporting APIs.
- Soak test for scheduler and simulation workers.
- Security tests: broken auth, token replay, tenant escape, rate-limit bypass.

## 4. Minimum Release Gates
- Gate 1: Backend modules build and pass tests.
- Gate 2: Web build passes and smoke tests run.
- Gate 3: Mobile analyze/test pass.
- Gate 4: Tenant isolation regression suite passes.
- Gate 5: Gateway end-to-end smoke suite passes.
- Gate 6: Security regression suite passes.
- Gate 7: Python AI API regression suite passes on Python 3.12 with the pinned service/test dependencies (`python -m unittest discover -s tests -v` in `ai/ai-service`). This verifies API behavior and dependency compatibility; forecasting quality requires separate evaluation.

Branch protection and required check contexts for these gates are versioned in `docs/CI_BRANCH_PROTECTION_POLICY.md`.
Gate 4 tenant-isolation suite definition and evidence mapping are versioned in
`docs/TENANT_ISOLATION_MATRIX.md` and executed by `scripts/ci/tenant_isolation_matrix.sh`.

## 5. Coverage Targets
- Domain and application service lines: >= 80%.
- Critical paths (auth, trade decision, transfer, fee charge): >= 90% branch coverage.
- Contract test coverage: all `/api/v1` critical business endpoints.

## 6. Test Data Strategy
- Seed deterministic fixtures for users, orgs, accounts, plans, and quotes.
- Use tenant-specific fixture sets to validate isolation.
- Use synthetic market scenarios for normal, volatile, and stress regimes.

## 7. Environment Matrix

| Environment | Purpose | Data |
| --- | --- | --- |
| Local Docker | Feature development and smoke tests | Synthetic fixtures |
| CI Ephemeral | PR validation | Seeded deterministic |
| Staging | Release validation and benchmark | Masked non-production |
| Production | Monitoring and canary validation | Real tenant data |

## 8. Example CI Flow
```mermaid
sequenceDiagram
  autonumber
  participant PR as Pull Request
  participant CI as CI
  participant BE as Backend
  participant FE as Web
  participant MOB as Mobile
  participant SEC as Security Suite

  PR->>CI: Open / update PR
  CI->>BE: Build + unit/integration tests
  CI->>FE: Build + smoke checks
  CI->>MOB: Analyze + tests
  CI->>SEC: Authz + tenant + API abuse tests
  SEC-->>CI: Result
  CI-->>PR: Required checks status
```

## 9. Current Gap Notes
- Automated test suites are currently limited in-repo and should be expanded per this strategy.
- Auth-service now includes SSO/SCIM API contract tests with request/response fixtures under
  `backend/auth-service/src/test/resources/fixtures`.
- Baseline org-scope service/repository regression tests are now present in
  `backend/portfolio-service/src/test`, including audit, execution, notification,
  reporting, research, and market-data entitlement paths.
- Mobile integration coverage for critical workflows is now versioned under
  `mobile/xcapital_app/test/integration` with deterministic fixtures and fake API server support.
- Next priority implementation order: trade lifecycle tests, banking invariants.
