# CI & Branch Protection Policy

## Purpose
Define the mandatory merge quality gate for `main` with explicit, versioned checks and review rules.

## Policy Version

| Version | Date | Notes |
| --- | --- | --- |
| `v1.0` | `2026-03-13` | Initial required checks and branch protection baseline. |
| `v1.1` | `2026-03-13` | Add tenant isolation regression matrix as required check (`Gate 4`). |
| `v1.2` | `2026-03-13` | Align release gate with versioned release readiness checklist and mobile integration CI coverage. |
| `v1.3` | `2026-04-10` | Add gateway end-to-end smoke workflow as required check (`Gate 5`). |
| `v1.4` | `2026-10-04` | Add Python 3.12 AI HTTP regression checks following XC-001 baseline validation. |

## Required Checks (`v1.4`)
All pull requests targeting `main` must pass these check contexts:

1. `CI Required Checks / backend-maven-test`
2. `CI Required Checks / tenant-isolation-matrix`
3. `CI Required Checks / frontend-web-build`
4. `CI Required Checks / mobile-flutter-quality`
5. `CI Required Checks / gateway-e2e-smoke`
6. `CI Required Checks / docs-policy-check`
7. `CI Required Checks / ai-python-test`

These checks are produced by:
- [ci-required-checks.yml](../.github/workflows/ci-required-checks.yml)

`v1.4` check definitions:
- `ai-python-test`: Python 3.12 in-process HTTP regression tests for prediction, risk, evaluation, and allocation using the service's pinned dependencies.
- `backend-maven-test`: Maven test run for `auth-service`, `gateway`, `portfolio-service`, and `simulation-service`.
- `tenant-isolation-matrix`: targeted tenant isolation regression suite execution via `scripts/ci/tenant_isolation_matrix.sh`.
- `frontend-web-build`: dependency install + Angular production build.
- `mobile-flutter-quality`: Flutter dependency resolution + non-integration and integration Flutter test suites.
- `gateway-e2e-smoke`: reusable CI workflow invocation that runs the dockerized gateway end-to-end smoke script.
- `docs-policy-check`: validates policy doc/index/gate references are present.

## Merge Rules (`main`)
1. Pull request only (no direct pushes).
2. Minimum approvals: `1`.
3. Last push must be approved by a reviewer other than the pusher.
4. Stale approvals are dismissed on new commits.
5. Conversation resolution is required before merge.
6. Force-push and branch deletion are disabled.
7. Required checks in this policy must be green.

## Reviewer Expectations
1. Verify behavior, not only style.
2. Confirm no regression to human trade approval, risk controls, or auditability.
3. Ensure tests/docs are updated when behavior changes.
4. Confirm service boundaries remain intact (no parallel architecture introduced).

## Release Readiness Gate
This merge policy must stay aligned to:
- Release gates in [docs/TEST_STRATEGY.md](TEST_STRATEGY.md) (`Gate 1` to `Gate 7`)
- Milestone release readiness reviews in [docs/ROADMAP.md](ROADMAP.md)
- Versioned release checklist and vNext acceptance criteria in
  [docs/RELEASE_READINESS_CHECKLIST.md](RELEASE_READINESS_CHECKLIST.md)

Any change to required checks must update this document version and be reviewed in the same PR.

## Versioned Branch Protection Config
- [main.json](../.github/branch-protection/main.json)
- [apply-main.sh](../.github/branch-protection/apply-main.sh)

Apply policy to a repository:

Updating the versioned JSON does not change GitHub's active settings. Apply it through the repository administration process after the new workflow has been reviewed and run. XC-001 only updates local configuration.

```bash
.github/branch-protection/apply-main.sh KOKUTBON/xCAPITAL
```
