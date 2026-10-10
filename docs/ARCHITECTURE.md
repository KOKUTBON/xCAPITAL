# Architecture

## Related Documents
- `docs/HLD.md`
- `docs/LLD.md`
- `docs/UML.md`
- `docs/DATABASE_STRATEGY.md`
- `docs/SCALABILITY_PERFORMANCE.md`
- `docs/DISTRIBUTED_SYSTEM_DESIGN.md`
- `docs/API_DESIGN_SECURITY.md`

## System Overview
xCAPITAL is a modular investing platform with service separation by responsibility:
- `gateway`: edge routing, JWT validation, rate limiting, identity header propagation.
- `auth-service`: identity lifecycle, org/membership management, MFA, SSO (SAML/OIDC), SCIM provisioning.
- `portfolio-service`: trading, risk, automation, reporting, research, rewards, compliance, market data policy.
- `simulation-service`: backtesting orchestration and persisted simulation results.
- `ai`: forecasting and model endpoints (proxied by gateway).
- `frontend/web`: Angular UI for operational and portfolio workflows.
- `mobile/xcapital_app`: Flutter client with enterprise feature access.

## Component Graph
```mermaid
flowchart LR
  subgraph Clients
    WEB["Angular Web"]
    MOB["Flutter Mobile"]
  end

  WEB --> GW["Gateway"]
  MOB --> GW

  GW --> AUTH["Auth Service"]
  GW --> PORT["Portfolio Service"]
  GW --> SIM["Simulation Service"]
  GW --> AI["AI Service"]

  AUTH --> PG[("PostgreSQL")]
  PORT --> PG
  SIM --> PG
  AI --> PG

  GW --> REDIS[("Redis")]
  PORT --> REDIS
  SIM --> REDIS

  AUTH -. "SAML / OIDC" .-> IDP["Enterprise IdP"]
  AUTH -. "SCIM" .-> SCIM["Provisioning Client"]
  PORT -. "Broker APIs" .-> BRK["Broker Providers"]
  PORT -. "Market Data APIs" .-> MDP["Market Data Providers"]
  PORT -. "Email / SMS / Webhook" .-> NTF["Notification Providers"]
```

## Architectural Style
- Service-oriented architecture with API gateway at the edge.
- Domain-centric modules inside services (`application`, `domain`, `infrastructure`, `web`).
- Stateful persistence via PostgreSQL; low-latency cache/queue via Redis.
- Event/audit traceability through explicit audit tables.

## Core Quality Attributes
- **Security**: JWT + role propagation, MFA, org scoping, SCIM token hash verification.
- **Auditability**: write-side audit events for security and portfolio actions.
- **Reliability**: idempotent auto-invest runs, deterministic reconciliation and statement generation.
- **Extensibility**: provider abstraction for brokers, market data, notifications.

## Runtime Data Flow (Typical Trade Lifecycle)
1. User authenticates via local auth or enterprise SSO.
2. Gateway validates JWT and forwards user/org context headers.
3. Portfolio service generates trade proposal and policy checks.
4. User decision is recorded; execution intent is created and submitted.
5. Fill/order data updates account state and triggers surveillance/best-execution records.
6. Audit and notifications are written for traceability.

## Trade Lifecycle Sequence
```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant C as Client App
  participant G as Gateway
  participant A as Auth Service
  participant P as Portfolio Service
  participant B as Broker Adapter
  participant D as PostgreSQL

  U->>C: Login or SSO
  C->>G: POST /api/v1/auth/*
  G->>A: Forward request
  A-->>G: JWT (roles + org context)
  G-->>C: Access token

  U->>C: Create trade proposal
  C->>G: POST /api/v1/trade/proposals
  G->>P: Authenticated request + org headers
  P->>D: Persist proposal + policy checks
  P-->>G: Proposal payload
  G-->>C: Proposal summary

  U->>C: Approve proposal
  C->>G: POST /api/v1/trade/proposals/{id}/decision
  G->>P: Decision request
  P->>B: Submit intent/order
  B-->>P: Execution response
  P->>D: Persist orders/fills/audit
  P-->>G: Execution status
  G-->>C: Updated status
```

## Org/Tenant Boundary
- Every org-scoped domain table includes `org_id`.
- Request trace filter resolves `X-Org-Id` into tenant context.
- Repositories/services apply user + org constraints on reads/writes.
- Org-admin reporting aggregates only current-tenant data.
