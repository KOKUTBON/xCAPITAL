# Operating limitations and deployment requirements

xCAPITAL provides reusable financial application services, synthetic web workflows and integration interfaces. Source availability and passing tests do not certify a deployment for live trading, regulated use or enterprise operation.

## Execution and authorization

Portfolio execution uses persisted approval and terms binding, scoped intents and durable submission claims. Direct arbitrary order placement and synthetic fill mutation are blocked. Live funding requires its own approved workflow. Test these boundaries against the exact deployed revision.

Simulation jobs need a deployment-specific ownership and worker-context review. Interrupted broker submissions require provider recovery, reconciliation and durable per-order handling before safe retries. Federation MFA semantics, fresh step-up, organization membership revocation and access restrictions must be validated with the selected identity provider.

## Financial correctness

Review precision, rounding, currency handling, ledger atomicity and migration behavior for the intended workflow. Do not infer accounting correctness from synthetic UI balances, paper trading or forecast outputs. Exercise reconciliation and restore procedures with controlled test data before live use.

## Deployment prerequisites

- Replace all development credentials and configure TLS, provider authentication and least-privilege access.
- Use privately supplied, appropriately licensed market data and verify entitlements, freshness and failure handling.
- Review supported dependency versions, container vulnerabilities and provider compatibility.
- Run service tests, the tenant isolation matrix, gateway smoke suite, web build and mobile tests. Rehearse migrations and recovery separately for the target environment.
- Maintain actual vendor records and operational evidence outside the source repository; the bundled register is synthetic.

No production provider accounts, real funds, customer records or operational approval evidence are distributed with this project. Consult ARCHITECTURE, EXECUTION_APPROVAL_BOUNDARY, TEST_STRATEGY and RELEASE_READINESS_CHECKLIST for implementation contracts and validation procedures.
