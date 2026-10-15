# Mobile Integration Tests

## Scope
- Login and token persistence.
- Wealth, banking, rewards, and org-admin summary/audit fetch flows.
- Expired/invalid token failure handling for protected API calls.

## Deterministic Fixtures
- Fixture source: `mobile/xcapital_app/test/fixtures/mobile_integration_fixtures.dart`
- Local fake API server: `mobile/xcapital_app/test/support/fake_mobile_api_server.dart`
- Integration suite: `mobile/xcapital_app/test/integration/mobile_critical_workflows_test.dart`

## Local Run
```bash
cd mobile/xcapital_app
flutter test test/integration
```

## CI Run
- Required check context: `mobile-flutter-quality`
- Workflow: `.github/workflows/ci-required-checks.yml`
- CI runs integration tests with:
```bash
flutter test test/integration
```
