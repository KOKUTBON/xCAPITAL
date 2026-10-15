# Execution approval boundary

Implemented: 4 October 2026. This is the focused XC-001 safety follow-up, before new financial capabilities. Gate 0 remains open. See [ADR 0004](adr/0004-persisted-execution-approval-boundary.md).

## Enforced workflow

1. Create a proposal; it remains `PENDING_USER`.
2. Decide `APPROVE` within the proposal's user and organization. A row lock serializes decisions. Live approval records actor/time/hash, checks compliance and a linked account, then creates one bound intent.
3. Submit the `AUTHORIZED` intent explicitly. Revalidate persisted approval, approved terms and tenant; atomically commit a `SUBMITTING` claim before broker placement.
4. Place stored approved orders using the bound account and connection with stable per-order client IDs. Save observed broker outcomes. Synthetic fill mutation is disabled.
5. Reject repeats. Investigate `SUBMISSION_UNKNOWN` and interrupted `SUBMITTING` outcomes; automatic resubmission is prohibited.

The existing proposal `SUBMITTED` label means an intent has been created. The intent starts `AUTHORIZED`; broker submission requires the separate submit call. Paper approval executes internally and checks ownership of the actual cash account before mutation.

## Route contract

| Route | Boundary/result |
| --- | --- |
| `POST /api/v1/trade/proposals/{id}/decision` | Exact user/org; locked pending-state decision. Live approval evidence is persisted before intent creation. |
| `POST /api/v1/execution/intents/{proposalId}` | Stored live approval and unchanged terms required; 409 for pending/declined/paper/legacy evidence. Repeat creation returns the bound intent. |
| `POST /api/v1/execution/intents/{id}/submit` | Approval binding, current compliance and atomic authorized-state claim. Repeat/concurrent losing request returns 409. |
| `POST /api/v1/brokers/accounts/{id}/orders` | 409: use an approved execution intent. |
| `POST /api/v1/execution/intents/{id}/simulate-fill` | 409: execution fills must come from broker outcomes. |
| Broker preview/review/refresh/cancel and account/order/position reads | Exact owner/org; account connection must belong to the same scope and provider. Preview/review create no order. Cancellation remains available for owned orders. |
| Funding deposit/withdraw/transfer | Exact source/target scope. Live adapters return 409 pending a funding approval workflow; simulation remains available. |
| Banking transfers and paper cash mutations | Exact cash account scope; these remain existing internal simulation behavior. |

Other-user and other-org record access returns 404. A null organization is not a wildcard. In authenticated requests, neither `X-Org-Id`, `X-User-Roles` nor `X-User-Mfa` can override missing or different signed context. The MFA/RBAC enforcement configuration still defaults to disabled; signed MFA evidence is not a fresh approval challenge.

## Migration and compatibility

Flyway V20 adds proposal approval actor/time/hash and intent hash/account binding, plus a unique partial index on proposal ID for intents with approval evidence. No historical approval or intent is granted evidence automatically. Historical rows remain for investigation and scoped reads. Create and approve a new reviewed proposal if the old intent has never reached an external provider; reconcile any possible external activity first.

Rolling deployment must prevent old application instances from accepting execution traffic after V20 is applied: old code does not enforce these rules. Use a coordinated deployment or maintenance window. Do not drop the migration or downgrade execution handlers to restore old bypass behavior. Application rollback requires execution to remain disabled until an operator has reviewed outstanding outcomes.

## Uncertain submission handling

For `SUBMISSION_UNKNOWN` or a stale `SUBMITTING` intent:

1. Preserve the intent, approval, bound account, order sequence and audit evidence.
2. Query the provider using the stable client IDs and inspect persisted broker orders. Earlier orders in the same intent may have succeeded before a later order failed.
3. Reconcile acceptance, rejection, fills and cash effects with provider evidence. Keep the intent blocked while the outcome is unclear.
4. Do not reset to `AUTHORIZED` or resubmit this intent. Automated recovery and an audited operator resolution API are future work.

No live provider was exercised in the validation below. Exactly-once effects, durable per-order reservations/outbox, provider recovery, authenticated callbacks and settlement reconciliation remain outstanding.

## Verification

Portfolio regression tests cover pending/declined/paper/legacy authorization, modified quantities/accounts/intent payloads, one-intent creation from persisted terms despite a forged DTO, lost claims, repeat submission, uncertain outcomes, synthetic fill rejection, same-user wrong/missing organizations, funding adapter blocking, paper-account ownership, and spoofed MFA/role headers.

The expanded tenant matrix includes trade, execution, broker, funding and trusted-claims tests. The Docker gateway smoke uses PostgreSQL and the simulated broker: pending intent creation/direct order placement/synthetic fill return 409; repeat intent creation returns the same ID; two concurrent submissions yield one 200 and one 409; repeat submission returns 409; stored order count equals the approved order count; another organization receives 404 on proposal, intent and broker routes. It also retains registration/login/compliance/reporting checks and isolated stack cleanup.

Web production build and mobile widget tests validate client compatibility. These checks do not establish production readiness or full browser interaction coverage. Deployment constraints are described in [operating limitations](OPERATING_LIMITATIONS.md).

Recorded local results (4 October 2026; working tree, no CI run URL):

| Check | Result |
| --- | --- |
| Java 21 portfolio Maven tests | PASS: 62 tests, zero failures/errors/skips |
| Expanded tenant isolation matrix | PASS: 14 suites |
| Docker gateway smoke, isolated PostgreSQL stack with simulated broker | PASS, including concurrent claims, order reads/count, approval bypass rejection and other-org rejection; stack/volume removed |
| Web production build | PASS |
| Flutter widget tests | PASS: 3 tests |
| Diff whitespace and shell syntax | PASS |

V20 was exercised on a fresh PostgreSQL database. A populated production migration rehearsal remains required before deployment. Existing baseline dependency vulnerabilities and Flutter analyzer deprecations are unchanged by this follow-up.
