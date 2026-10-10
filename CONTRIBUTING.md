# Contributing to xCAPITAL

Thank you for helping improve xCAPITAL. Contributions are welcome across the backend, Angular web client, Flutter application, Python AI service, math library, tests, security controls, accessibility, and documentation.

Before participating, read the [Code of Conduct](CODE_OF_CONDUCT.md) and [Security Policy](SECURITY.md). Report suspected vulnerabilities privately rather than through issues or pull requests.

## Before You Start

- Search existing issues and pull requests for related work.
- Open an issue before a substantial architecture, API, database, or product change.
- Keep a pull request focused on one problem.
- Do not include credentials, private datasets, customer data, licensed market data, or generated build output.

## Development Principles
- Keep changes small, testable, and reversible.
- Preserve tenant/org isolation, auditability, and deterministic behavior.
- Prefer explicit contracts (DTOs, enums, validation) over implicit assumptions.
- Keep backwards compatibility for public API routes unless explicitly versioned.

## Local Setup

1. Start infrastructure and services:

```bash
make up
```

2. Run backend tests:

```bash
cd backend/auth-service && mvn test
cd ../portfolio-service && mvn test
cd ../simulation-service && mvn test
cd ../gateway && mvn test
```

3. Build web app:

```bash
cd frontend/web && npm ci && npm run build
```

4. Run mobile tests when Flutter is installed:

```bash
cd mobile/xcapital_app
flutter pub get
flutter test
```

## Pull Request Expectations

- Explain **what** changed and **why**.
- Include test evidence (command + result).
- Call out config/migration changes explicitly.
- Update documentation when behavior, configuration, or public APIs change.
- Describe security, data, accessibility, and compatibility implications when relevant.
- Avoid unrelated formatting churn.

## Coding Standards

- Java: Spring Boot style, constructor injection, explicit validation and HTTP errors.
- Flutter/Angular: keep state handling clear and API failures user-visible.
- SQL/Flyway: additive-first migrations with safe defaults.
- Tests: cover material behavior and failure paths rather than mirroring implementation details.

## Commit Guidance

- Use meaningful, scoped commit messages.
- Group related code, tests, and docs in the same commit.
- Do not rewrite shared history in this project.

## Brand and UI

- Follow the brand guide in [branding/BRAND_GUIDE.md](branding/BRAND_GUIDE.md).
- Use design tokens (`frontend/web/src/styles/tokens.css`, mobile `xcapital_theme.dart`); never hardcode brand hex values in feature code.

## Licensing of Contributions

Unless explicitly agreed otherwise, contributions are submitted under the repository's current [Apache License 2.0](LICENSE), consistent with section 5 of that license. Do not submit code, data, documentation, or assets that you do not have the right to contribute.

## Community participation

Follow the [communication guidelines](COMMUNICATION.md) and [organizational governance](GOVERNANCE.md). Keep security and conduct reports confidential and use a verified private channel; never include sensitive details in a public issue. Maintainers verify the reporting contacts and channels listed in the [readiness checklist](RELEASE_CHECKLIST.md).

## KOKUTBON contribution principles

Contribute under the existing **Apache-2.0** license in [LICENSE](LICENSE). You retain copyright in your work. Submit only work you are entitled to share, identify third-party sources and preserve their notices. No copyright assignment or paid membership is required.

Open an issue for substantial proposals, then submit a focused pull request from a branch or fork. Describe the problem, implementation, relevant checks and any privacy, accessibility, migration or safety impact. Use synthetic fixtures and sanitized logs. Maintainer review and passing relevant CI checks are expected before merging. A sole maintainer may merge their own change after checks pass and must record the rationale; no fictitious second reviewer is required.

Small documentation, translation, accessibility and reproducibility improvements are welcome. Funding does not determine review priority. Follow [communication guidelines](COMMUNICATION.md) and [the sponsorship policy](SPONSORSHIP.md).
