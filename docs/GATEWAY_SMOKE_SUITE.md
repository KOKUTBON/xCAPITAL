# Gateway End-to-End Smoke Suite

## Purpose
Validate critical journeys through the gateway routing/auth boundary with one scripted flow:
- Auth register/login
- Proposal creation and approval
- Execution intent creation/read
- Core reporting endpoints
- Org admin summary/audit endpoints

This suite is intentionally lightweight and diagnostic-focused. It is not a full regression test replacement.

## Script Location
- `scripts/smoke/gateway_e2e_smoke.sh`

## Local Usage
If the stack is already running:
```bash
bash scripts/smoke/gateway_e2e_smoke.sh
```

Start/stop stack automatically:
```bash
bash scripts/smoke/gateway_e2e_smoke.sh --start-stack --cleanup
```

Override settings (example):
```bash
bash scripts/smoke/gateway_e2e_smoke.sh \
  --gateway-url http://localhost:8080 \
  --email smoke.local@example.com \
  --password 'SmokePass123!'
```

## CI Usage
- Workflow: `.github/workflows/gateway-smoke.yml`
- Triggers: `workflow_dispatch`, `workflow_call`
- Job name: `gateway-e2e-smoke`

The workflow builds and starts the docker-compose stack, runs the smoke script, and tears down resources.

## Diagnostics
On failure, the script prints:
- step name
- method + URL
- expected/actual status
- response body

This is designed to make root-cause triage immediate in both local and CI runs.

## Release Note Requirement
Release notes must include the latest gateway smoke result:
- `PASS` or `FAIL`
- commit SHA
- run timestamp
- CI workflow run URL (or local run note)

Suggested release note snippet:
```text
Gateway E2E Smoke: PASS
Run: https://github.com/<org>/<repo>/actions/runs/<id>
Commit: <sha>
Timestamp (UTC): <time>
```
