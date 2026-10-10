# ADR 0003: Auth-Service Ownership of SSO and SCIM Identity Flows

- Status: ACCEPTED
- Date: 2026-03-13
- Deciders: platform-architecture
- Related docs/issues: `docs/ARCHITECTURE.md`, `docs/API_DESIGN_SECURITY.md`

## Context
Enterprise customers need SAML/OIDC SSO and SCIM provisioning while preserving a single identity lifecycle and org membership model.

## Decision
Centralize SSO and SCIM in `auth-service`:
1. `auth-service` owns IdP config, SSO sessions, federated identity links, and SCIM token validation.
2. Successful SSO results in standard platform access tokens with org/user claims.
3. SCIM create/update/deactivate and group/role mapping update the same underlying user/org membership entities.
4. Other services consume identity context via gateway headers and do not implement separate SSO/SCIM logic.

## Consequences
### Positive
- Single identity authority for local auth and federated auth
- Reduced duplication and fewer policy inconsistencies
- Clear boundary between identity lifecycle and domain services

### Negative
- Auth-service complexity increases (protocol and provisioning edge cases)
- Reliability of enterprise sign-in and provisioning depends on auth-service availability

## Alternatives Considered
1. Embed SSO adapters per service: rejected due inconsistent policy enforcement and duplicated protocol logic.
2. Externalize all identity handling to a separate standalone IAM service now: deferred for current stage; existing auth-service already contains identity domain and migration cost is high.

## Follow-up
- Expand edge-case coverage for SSO token/assertion validation and SCIM authorization.
- Keep role/org claim contract stable with gateway and downstream services.
