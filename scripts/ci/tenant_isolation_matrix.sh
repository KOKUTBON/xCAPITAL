#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORTFOLIO_DIR="${ROOT_DIR}/backend/portfolio-service"

MATRIX_TESTS=(
  "com.alphamath.portfolio.application.execution.ExecutionApprovalBoundaryTest"
  "com.alphamath.portfolio.application.broker.BrokerExecutionBoundaryTest"
  "com.alphamath.portfolio.application.funding.FundingExecutionBoundaryTest"
  "com.alphamath.portfolio.application.trade.TradeServiceDecisionLifecycleTest"
  "com.alphamath.portfolio.web.SecurityGuardTrustedClaimsTest"
  "com.alphamath.portfolio.application.reporting.ReportingServiceOrgScopeTest"
  "com.alphamath.portfolio.application.marketdata.MarketDataEntitlementServiceOrgScopeTest"
  "com.alphamath.portfolio.application.audit.AuditServiceOrgScopeTest"
  "com.alphamath.portfolio.application.execution.ExecutionServiceOrgScopeTest"
  "com.alphamath.portfolio.application.notification.NotificationServiceOrgScopeTest"
  "com.alphamath.portfolio.application.research.ResearchServiceOrgScopeTest"
  "com.alphamath.portfolio.web.OrgAdminAuthorizationMatrixTest"
  "com.alphamath.portfolio.security.TenantContextSecurityContextTest"
  "com.alphamath.portfolio.security.JwtAuthFilterContextTest"
)

tests_csv="$(IFS=,; echo "${MATRIX_TESTS[*]}")"

echo "[tenant-isolation-matrix] Running tenant isolation regression matrix"
echo "[tenant-isolation-matrix] Module: backend/portfolio-service"
echo "[tenant-isolation-matrix] Tests: ${tests_csv}"

(
  cd "${PORTFOLIO_DIR}"
  mvn -B -q -Dtest="${tests_csv}" test
)

echo "[tenant-isolation-matrix] PASS"
