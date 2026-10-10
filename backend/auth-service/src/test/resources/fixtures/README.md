# SSO and SCIM Contract Fixtures

This folder defines request/response fixtures used by auth-service API contract tests.

## SSO fixtures
- `sso/oidc-start-response.json`:
  expected JSON shape for `GET /api/v1/auth/sso/oidc/start` (non-redirect mode).
- `sso/oidc-callback-response.json`:
  expected JSON shape for `GET /api/v1/auth/sso/oidc/callback`.
- `sso/saml-start-response.json`:
  expected JSON shape for `GET /api/v1/auth/sso/saml/start` (non-redirect mode).

## SCIM fixtures
- `scim/user-create-request.json`:
  provisioning payload shape for `POST /api/v1/scim/v2/Users`.
- `scim/user-create-response.json`:
  expected SCIM user resource shape returned by user provisioning.
- `scim/group-patch-request.json`:
  group membership patch payload shape for `PATCH /api/v1/scim/v2/Groups/{id}`.
- `scim/group-patch-response.json`:
  expected SCIM group resource shape returned by group provisioning patch.
