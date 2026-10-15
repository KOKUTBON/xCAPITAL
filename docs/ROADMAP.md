# Maintenance priorities

Prioritize execution and tenant boundaries before expanding live integrations. See [operating limitations](OPERATING_LIMITATIONS.md) for the current deployment requirements.

1. Complete authenticated ownership and worker context for simulation jobs; extend negative-path tests.
2. Harden interrupted broker submission recovery, durable per-order handling and reconciliation.
3. Verify exact-money contracts, currency rounding, atomic ledger behavior and safe migration procedures.
4. Validate federation MFA semantics, step-up authentication, organization membership revocation and deployment access controls.
5. Maintain supported runtime and dependency versions, improve cross-client integration coverage and rehearse backup/restore procedures.
6. Expand accessibility, documentation and internationalization based on reproducible community feedback.

Contributions should be scoped, testable and reviewed through the project contribution process. These priorities do not promise delivery dates or production readiness.
