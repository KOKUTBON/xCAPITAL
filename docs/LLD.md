# Low-Level Design (LLD)

## Related Documents
- `docs/HLD.md`
- `docs/UML.md`
- `docs/API_DESIGN_SECURITY.md`
- `docs/TEST_STRATEGY.md`

## Backend Package Structure
Each Spring service follows a layered package model:
- `web`: REST controllers, request/response mapping.
- `application`: use-case services and orchestration.
- `domain`: business models and enums.
- `infrastructure.persistence`: JPA entities and repositories.
- `security` / `web filters`: auth/tenant context helpers.

## Internal Layer Interaction
```mermaid
flowchart LR
  HTTP["HTTP Request"] --> CTRL["web Controller"]
  CTRL --> APP["application Service"]
  APP --> DOM["domain Models / Policies"]
  APP --> REPO["infrastructure.persistence Repository"]
  REPO --> DB[("PostgreSQL")]
  APP --> AUD["Audit Service"]
  APP --> NOTIF["Notification Service"]
  CTRL --> SEC["Security / Tenant Context"]
  SEC --> APP
```

## Persistence Conventions
- UUID string IDs for most domain records.
- `createdAt` and `updatedAt` timestamps on mutable records.
- Explicit `orgId` for multi-tenant boundaries where applicable.
- Flyway migrations under `src/main/resources/db/migration`.

## Auth/Identity Internals
- SSO flows managed via `SsoService`:
  - OIDC: PKCE, nonce/state validation, token exchange, claim verification.
  - SAML: request/response session mapping, signature and audience checks.
- SCIM endpoints under `/api/v1/scim/v2` with bearer token hashing.

## Auth-Service SSO/SCIM Flow Detail
```mermaid
flowchart TD
  START["SSO Start Endpoint"] --> SESSION["Persist sso_login_session"]
  SESSION --> REDIRECT["Redirect to IdP"]
  REDIRECT --> CALLBACK["SAML ACS / OIDC Callback"]
  CALLBACK --> VALIDATE["Validate state, session TTL, signature/claims"]
  VALIDATE --> MAP["Map federated identity -> user + org membership"]
  MAP --> JWT["Issue JWT with user roles + org roles"]

  SCIM_REQ["SCIM API Request"] --> TOKEN["Validate SCIM token hash"]
  TOKEN --> UPSERT["Create/Update/Deactivate User"]
  UPSERT --> MEMBER["Update org membership + role/status"]
```

## Portfolio-Service Internals
- Repositories expose user+org query methods where org scoping is required.
- Services validate ownership and tenant boundary before updates.
- Admin reporting service aggregates repository metrics by `orgId`.

## API Design Rules
- Use clear resource-oriented endpoints and HTTP status semantics.
- Return validation errors as `400` and authorization failures as `403`.
- Keep DTOs stable and append-only for compatibility where possible.

## Mobile/Web Client Integration
- Clients use token store + bearer auth on all protected endpoints.
- UI actions map directly to gateway routes; backend enforces authz/scoping.

## Org-Scoped Read/Write Guard
```mermaid
sequenceDiagram
  participant C as Client
  participant G as Gateway
  participant P as Portfolio Service
  participant R as Repository

  C->>G: Request with Bearer JWT
  G->>G: Validate JWT
  G->>P: Forward with X-User-* and X-Org-* headers
  P->>P: Resolve TenantContext(orgId)
  P->>R: Query/Write by userId + orgId
  R-->>P: Scoped rows only
  P-->>C: Response
```
