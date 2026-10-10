# Tenant Isolation Security Regression Matrix

## Purpose
Institutionalize tenant-isolation verification as a release gate and prevent cross-tenant data leakage regressions.

## Matrix Coverage

| Category | Representative endpoint/workflow | Isolation invariant | Automated coverage |
| --- | --- | --- | --- |
| `READ` | Statements/read paths (`/api/v1/statements/**`) | Reads must resolve org-scoped repositories when org context is present. | `ReportingServiceOrgScopeTest#listStatementsUsesOrgScopedRepositoryWhenOrgContextExists` |
| `WRITE` | Entitlement upsert (`/api/v1/market-data/entitlements`) | Writes must reject updates across mismatched org ownership. | `MarketDataEntitlementServiceOrgScopeTest#upsertEntitlementRejectsOrgMismatches` |
| `LIST` | Audit list (`/api/v1/audit/events`) | List queries must use user+org scoped repository filters. | `AuditServiceOrgScopeTest#listUsesOrgScopedEventQueryWhenOrgContextExists` |
| `EXPORT` | Audit export (`/api/v1/audit/events/export`) | Export must inherit org-scoped list filtering and never downgrade to user-only scope when org context exists. | `AuditServiceOrgScopeTest#exportCsvUsesOrgScopedListQueryWhenOrgContextExists` |
| `ADMIN` | Org admin reporting (`/api/v1/admin/org/**`) | Admin reads require trusted org roles; spoofed headers must not bypass authz. | `OrgAdminAuthorizationMatrixTest` |

Additional tenant-boundary regression tests in the same matrix suite:

- `ExecutionApprovalBoundaryTest` (persisted terms, same-user wrong/missing org, state/replay and uncertainty)
- `BrokerExecutionBoundaryTest` (placement, preview, cancel, refresh and list)
- `FundingExecutionBoundaryTest` (sources/destinations and blocked live adapters)
- `TradeServiceDecisionLifecycleTest` (all decision actions and paper account ownership)
- `SecurityGuardTrustedClaimsTest` (signed MFA/roles and spoofed headers)
- `ExecutionServiceOrgScopeTest`
- `NotificationServiceOrgScopeTest`
- `ResearchServiceOrgScopeTest`
- `TenantContextSecurityContextTest`
- `JwtAuthFilterContextTest`

## CI Gate
- Workflow: `.github/workflows/ci-required-checks.yml`
- Required job/check: `CI Required Checks / tenant-isolation-matrix`
- Script entrypoint: `scripts/ci/tenant_isolation_matrix.sh`

The script executes the matrix test set directly and exits non-zero on any failure, failing the PR gate.

## Local Run
```bash
make tenant-isolation-matrix
```

## Release Checklist Requirement
Release readiness notes must include latest tenant-isolation matrix result:
- `PASS` or `FAIL`
- commit SHA
- run timestamp (UTC)
- CI run URL (or explicit local-run note)

Suggested snippet:
```text
Tenant Isolation Matrix: PASS
Run: https://github.com/<org>/<repo>/actions/runs/<id>
Commit: <sha>
Timestamp (UTC): <time>
```
