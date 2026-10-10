# High-Level Design (HLD)

## Related Documents
- `docs/ARCHITECTURE.md`
- `docs/SOFTWARE_REQUIREMENTS.md`
- `docs/SOFTWARE_DESIGN.md`
- `docs/UML.md`
- `docs/DATABASE_STRATEGY.md`

## Logical Components
- **Client Layer**: Angular web + Flutter mobile.
- **Edge Layer**: Spring Cloud Gateway, auth guard, rate limiter.
- **Identity Layer**: Auth service (login/register, MFA, orgs, SSO, SCIM).
- **Portfolio Domain Layer**: Portfolio service bounded contexts:
  - Trade + execution
  - Auto-invest + wealth planning
  - Funding + banking
  - Research + screeners + market data policy
  - Reporting + reconciliation + statements
  - Compliance + surveillance + best execution
  - Rewards + notifications + audit
- **Simulation Layer**: Async backtest jobs/results.
- **Data Layer**: PostgreSQL + Redis.

## Domain Capability Map
```mermaid
flowchart TB
  subgraph Identity
    I1["Login / MFA"]
    I2["SSO (SAML/OIDC)"]
    I3["SCIM Provisioning"]
  end

  subgraph PortfolioDomain["Portfolio Service Domains"]
    T["Trade + Execution"]
    W["Auto-Invest + Wealth Planning"]
    F["Funding + Banking"]
    R["Research + Screeners"]
    M["Market Data Policy"]
    S["Statements + Reconciliation"]
    C["Compliance + Surveillance + Best Execution"]
    N["Notifications + Audit + Rewards"]
  end

  subgraph Simulation
    B["Backtests + Quotas + Results"]
  end

  I1 --> T
  I2 --> T
  I3 --> I1
  T --> C
  T --> N
  W --> T
  F --> T
  R --> T
  M --> R
  S --> T
  B --> W
```

## External Integrations
- Broker adapters (account/order/position sync).
- Market data providers (quotes/history/backfill).
- Notification providers (email/webhook/SMS/push abstractions).
- Enterprise IdPs via SAML/OIDC and provisioning via SCIM.

## Key Interfaces
- REST API under `/api/v1/**` exposed through gateway.
- Tokenized auth boundary with propagated claims:
  - `X-User-Id`, `X-User-Roles`, `X-Org-Id`, `X-Org-Roles`, `X-User-Mfa`.

## Critical Workflows
- **Enterprise access**: SSO login -> federation mapping -> org membership check -> JWT issue.
- **Provisioning**: SCIM token auth -> create/update/deactivate users and memberships.
- **Automated investing**: scheduled plan run -> optimization -> proposal -> optional execution.
- **Org reporting**: admin endpoints aggregate counts, balances, and recent audit events.

## Enterprise Identity Flow
```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant APP as Client App
  participant GW as Gateway
  participant AUTH as Auth Service
  participant IDP as Enterprise IdP
  participant SCIM as SCIM Client

  U->>APP: Start SSO
  APP->>GW: GET /api/v1/auth/sso/*/start
  GW->>AUTH: Forward
  AUTH-->>APP: Redirect URL + state
  APP->>IDP: Authenticate
  IDP-->>AUTH: SAMLResponse / OIDC code
  AUTH->>AUTH: Validate signature/claims/session
  AUTH-->>APP: JWT + org claims

  SCIM->>GW: SCIM user/group provisioning calls
  GW->>AUTH: /api/v1/scim/v2/*
  AUTH->>AUTH: Token hash validation + org scope checks
  AUTH-->>SCIM: Created/updated/deactivated members
```

## Deployment Topology
- Containerized services orchestrated with Docker Compose for local stack.
- Independent horizontal scaling possible per service based on workload profile.
- Target production topology includes per-domain data stores and event-driven integration.
