# ADR 0004: Persisted approval and tenant boundaries for execution

- Status: ACCEPTED
- Date: 2026-10-04
- Scope: existing portfolio execution routes; XC-001 safety follow-up
- Related: [operating limitations](../OPERATING_LIMITATIONS.md), [execution boundary](../EXECUTION_APPROVAL_BOUNDARY.md), [tenant context](0002-tenant-scoping-via-org-context.md)

## Context

A pending proposal could create an intent, direct broker placement accepted arbitrary terms, and intent submission lacked a durable claim. Some mutations compared only the user, allowing the same user to cross organizations. Client headers were accepted as MFA evidence. These paths must be constrained before broadening financial execution.

## Decision

Persist approval actor, timestamp and a versioned SHA-256 digest of the proposal's owner, organization, account, execution mode, routing preferences, order type, time in force and ordered stored trade rows. Approval runs under a proposal row lock. Intent creation reads persisted terms, checks live approval and tenant ownership, and binds one intent to the proposal and a linked broker account. A partial unique index enforces one newly authorized intent per proposal.

At submission and broker placement, verify the persisted approval again and verify the intent's terms, tenant and proposal link. Atomically claim `AUTHORIZED → SUBMITTING` in a separate committed transaction before any adapter placement. Derive each broker request from persisted approved orders and assign `intent-{intentId}-{orderIndex}` as its stable client order ID. A lost claim cannot call a provider. An exception after claiming quarantines a still-submitting intent as `SUBMISSION_UNKNOWN`; process interruption can leave `SUBMITTING`. Neither state permits automatic retry.

Direct broker placement and synthetic intent fills return conflict. Live funding adapters cannot move funds until a persisted funding approval workflow exists; simulated adapters remain available. Account, connection, proposal, intent, funding source and mutation ownership require exact user and organization equality. Authenticated organization context takes precedence over MDC, including an absent organization. Unscoped legacy records are accessible only to their owner in an unscoped context; absence of organization never grants access to an organization-owned record. Background callers still need explicitly established trusted context.

MFA and roles are consumed from the authenticated JWT context. Client headers cannot supply missing evidence. The existing signed MFA flag does not establish fresh transaction authorization; production enforcement, federation semantics, membership revocation and step-up evidence remain separate release blockers.

## Consequences

Legacy status labels are insufficient approval evidence. Migration V20 adds nullable fields without backfilling authority. Existing intents remain inspectable within their tenant but cannot submit without the new binding. Operators must investigate externally uncertain submissions rather than reset status to retry.

Web Auto Invest exposes submission only for authorized intents; manual order placement and synthetic fill controls are removed or explained as disabled. Provider order listing keeps a read transaction open because the current PostgreSQL metadata mapping uses large objects.

This limits duplicate submissions through current application routes. It does not guarantee exactly-once external effects, crash recovery, reconciliation, settlement, approval expiry, maker/checker or immutable database history. Database administrators remain outside the application's authorization boundary. Future workers, payment routes and delegated agents must consume the same persisted authorization rules and explicit tenant context.

## Alternatives considered

- Trust request DTO status or approval headers: rejected because callers can supply them.
- Permit direct broker placement with a client idempotency key: rejected because a key does not establish approval or constrain terms.
- Reset a failed claim automatically: rejected because a timeout may follow external acceptance.
- Grant new authority to all old `APPROVED`/`SUBMITTED` rows during migration: rejected because historical evidence is incomplete.
