# Org Admin Authorization Matrix

## Endpoints

| Method | Endpoint | Allowed org roles | Denied org roles | Notes |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/admin/org/summary` | `OWNER`, `ADMIN` | `MEMBER`, `READ_ONLY`, missing roles | Returns org-scoped operational summary counts/balances. |
| `GET` | `/api/v1/admin/org/audit/events` | `OWNER`, `ADMIN` | `MEMBER`, `READ_ONLY`, missing roles | Returns org-scoped recent audit event feed. |

## Trust Source and Parsing Rules

- Authorization for `/api/v1/admin/org/**` uses trusted JWT claims parsed in `portfolio-service`:
  - `org_roles` for role checks
  - `org_id` for tenant scope selection
- Incoming `X-Org-Roles`/`X-Org-Id` headers are not treated as authoritative for org-admin authorization.
- Role values are normalized to uppercase and parsed as discrete role tokens.

## Expected Response Behavior

- Missing/invalid bearer token: `401 Unauthorized`.
- Valid token but missing required org role: `403 Forbidden`.
- Valid token with required org role but missing org context: `400 Bad Request` from org-scoped reporting service.
