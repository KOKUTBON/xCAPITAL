# OO Design

See also: `docs/SOFTWARE_DESIGN.md`.

## Core Principles Used
- **Single Responsibility**: each service class owns a narrow use-case slice.
- **Dependency Inversion**: controllers depend on service abstractions, services depend on repositories/interfaces.
- **Open/Closed**: adapters/providers are swappable without changing domain flows.
- **Composition over Inheritance**: behavior assembled from injected collaborators.

## Responsibility Decomposition
```mermaid
flowchart LR
  CTRL["Controller"] --> SVC["Application Service"]
  SVC --> POLICY["Domain Policy/Rules"]
  SVC --> REPO["Repository"]
  SVC --> EXT["External Adapter"]
  SVC --> AUD["Audit/Notification"]
```

## Domain Modeling
- Rich enums capture domain constraints (order types, statuses, roles, triggers).
- Domain DTOs represent workflow intent, persistence entities represent storage shape.
- Conversion methods (`toDto`/`toEntity`) isolate mapping concerns.

## Encapsulation Boundaries
- Controllers do not perform business logic; they delegate.
- Repositories do not contain policy; they provide scoped query capabilities.
- Tenant context is centralized, not duplicated in every controller.

## Error Handling Strategy
- Validation and domain guardrails throw explicit HTTP exceptions with actionable messages.
- Fail-fast approach for malformed inputs and authorization boundary violations.

## Extensibility Points
- Broker and market-data providers are pluggable strategy-style components.
- Notification channels and delivery integrations are adapter-based.
- SSO/SCIM support extends identity capabilities without replacing core auth.
