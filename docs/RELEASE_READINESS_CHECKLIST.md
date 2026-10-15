# Release Readiness Checklist and vNext Acceptance Criteria

## Purpose
Standardize release quality gates and operational readiness across backend, web, mobile, AI/risk workflows, and broker execution controls.

## Usage
- Run this checklist before every production tag.
- Attach evidence links (CI runs, smoke outputs, docs/PR links) for every item.
- A release is blocked until all `Required` items are `PASS`.

## Release Checklist

| Area | Check | Required | Evidence |
| --- | --- | --- | --- |
| CI quality gates | `backend-maven-test`, `tenant-isolation-matrix`, `frontend-web-build`, `mobile-flutter-quality`, `ai-python-test`, `docs-policy-check` are green on release commit | Yes | GitHub Actions run URL |
| Gateway/API smoke | Gateway smoke suite executed and passing | Yes | `scripts/smoke/gateway_e2e_smoke.sh` output |
| Tenant isolation | Tenant isolation matrix result is `PASS` with no critical exceptions | Yes | `docs/TENANT_ISOLATION_MATRIX.md` release note entry |
| Trade safety contract | Human approval contract for execution is unchanged (no bypass path introduced) | Yes | PR diff + reviewer note |
| Risk/explainability | Proposal/risk outputs retain explainability fields and rationale traces | Yes | API response evidence + tests |
| Auditability | Audit events, approval history, and execution intent traces remain queryable | Yes | API evidence + docs references |
| Migration readiness | Migration scripts tested, rollback path documented, and backward compatibility reviewed | Yes | Migration test logs + rollback note |
| Security review | Auth/authz changes reviewed (token handling, org scope, role checks, secrets exposure) | Yes | Security review checklist entry |
| Third-party risk controls | Material vendor register is current and control-review cadence evidence is attached | Yes | `docs/VENDOR_REGISTER.md` + control review evidence |
| Docs readiness | User/developer docs updated for behavior/config/setup changes | Yes | Linked docs PR sections |
| Ops readiness | On-call owner, release window, and incident channel confirmed | Yes | Release notes section |

## vNext Acceptance Criteria (Scope Closeout)

| Domain | Acceptance Criteria | Evidence |
| --- | --- | --- |
| Gateway and auth | Auth flow, org scoping, and role-based access pass tests and no tenant boundary regressions | Backend test run + tenant matrix run |
| Portfolio/risk/proposals | Proposal generation and risk outputs remain explainable, bounded, and policy-compliant | Service tests + API examples |
| Simulation/backtesting | Simulation and backtesting workflows execute with deterministic fixtures and documented assumptions | Test artifacts + docs links |
| AI forecasting/model registry | Forecast and model-registry APIs return expected metadata and traceable model identifiers | AI test/evidence links |
| Market/reference data | Ingestion/query paths validated for freshness, entitlement checks, and query stability | API tests + data checks |
| Broker execution integration | Execution intents keep approval gating and preserve status lifecycle/audit trails | Broker flow tests + audit evidence |
| Notifications/audit | Notification events and audit history remain complete and traceable | Event/audit test evidence |
| Web/mobile parity | Critical user journeys validated on web and mobile (automated + smoke where applicable) | Build/test runs + smoke checklist |
| Docs/config completeness | Setup, runbooks, and release notes updated for shipped changes | Docs diff references |

## Rollback Guidance

1. Stop rollout and declare incident channel if a `Required` gate regresses post-deploy.
2. Revert to the previous stable tag in deployment config (immutable image or commit SHA).
3. Run targeted verification for auth, trade approval, risk, and audit endpoints after rollback.
4. If schema changes are involved:
   - Prefer forward-fix migration if rollback is unsafe.
   - Use tested rollback procedure only when explicitly validated for that migration.
5. Publish incident summary with impact, root cause, and preventive action item.

## Hotfix Guidance

1. Branch from latest production tag using `hotfix/<ticket-or-scope>`.
2. Keep scope minimal to the incident fix; defer refactors to normal release flow.
3. Run required CI checks plus targeted regression tests for impacted domains.
4. Open expedited PR with explicit rollback note and risk assessment.
5. Tag patched release (`PATCH` semver), publish release note delta, and back-merge to `main`.

