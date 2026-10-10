# Enterprise Features (Baseline)

This document lists enterprise-oriented capabilities implemented in xCAPITAL.

## Governance + Audit
- Tamper-evident audit log with hash chaining (`prev_hash`, `event_hash`).
- Exportable audit events.

## Compliance + Surveillance
- Pre-trade policy checks (risk limits, account eligibility, broker link checks).
- Trade surveillance alerts for large orders and excessive order counts.
- Best-execution tracking with slippage vs. market quote at execution time.

## Security + Controls
- JWT roles with optional MFA/RBAC enforcement toggles.
- Notification preferences + delivery audit trails.
- Organization (tenant) model with memberships and role-based access (OWNER/ADMIN/MEMBER/READ_ONLY).
- JWT org claims (`org_id`, `org_roles`) propagated through gateway headers (`X-Org-Id`, `X-Org-Roles`).

## Operations
- Scheduled auto-invest runs and advisory fee charging.
- Rewards/bonus evaluation scheduler.

## Data Governance
- Market data licensing + entitlement enforcement toggles.

## Identity Provisioning (Scaffolded)
- Organization-level SSO configuration storage (SAML/OIDC metadata, certificates, client identifiers).
- SCIM provisioning configuration + token rotation endpoints (token hash stored server-side).

## Notes
- Data remains primarily user-scoped; org identifiers are captured in audit logs and key account tables to enable future cross-user/tenant analytics.
