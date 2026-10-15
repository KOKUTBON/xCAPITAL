# xCAPITAL Security Policy

## Supported Versions

Security fixes are applied to the default branch (`main`) and latest deployable Docker stack.

## Reporting a Vulnerability

Do **not** open public issues for vulnerabilities.

Report responsibly by sharing:
- Affected component(s)
- Reproduction steps / proof of concept
- Impact assessment
- Suggested remediation (if available)

Use GitHub private vulnerability reporting for this repository when available. If it is unavailable, use a private maintainer contact channel. Do not include credentials, personal data, or sensitive production records beyond what is necessary to demonstrate the issue.

Reports should include:

- affected commit, version, service, route, or component;
- prerequisites and reproducible steps;
- expected and observed behavior;
- likely impact and attack conditions;
- proof of concept with sensitive values removed;
- suggested mitigation, if known.

Maintainers will acknowledge a complete report when it is reviewed, investigate its impact, coordinate a correction, and publish information appropriate to the risk. Response and resolution times depend on severity and maintainer availability; this project does not promise a fixed service-level agreement.

## Security Controls in This Repository

- JWT-based gateway authentication with role propagation.
- Organization scoping and tenant context propagation (`X-Org-Id`).
- MFA support in auth flows.
- Audit event persistence and export.
- SCIM token hashing and SSO session expiry controls.

## Hardening Checklist

- Rotate JWT and SCIM secrets regularly.
- Enforce HTTPS and secure cookies in production.
- Restrict CORS origins and broker adapter credentials.
- Run dependency and container image vulnerability scans in CI.
- Enable database backups and encryption at rest.

## Local Development Defaults

`docker-compose.yml` binds ports to localhost but includes known development passwords and signing secrets. It is for a trusted local machine only. For deployment, provide unique external secrets, TLS, restricted origins, and provider-specific credentials; do not reuse the Compose values. Ensure you conduct an independent security review and dependency audit before production deployment.

## Scope Limitations

The presence of security controls in source code is not a production security certification. Provider integrations, authorization boundaries, dependency advisories, infrastructure, logging, backups, incident response, and regulatory obligations must be reviewed for each deployment. Never use the local stack for real funds or sensitive customer information without a separate security assessment.

## Private reporting contact

Email [kokutbon@gmail.com](mailto:kokutbon@gmail.com) with subject `[xCAPITAL] Security report`. Include affected version/commit, reproduction using synthetic data, preconditions and impact. Do not include live credentials or personal data. This mailbox is the fallback while the repository is private or GitHub's reporting form is unavailable.

After publication, prefer [GitHub private vulnerability reporting](https://github.com/KOKUTBON/xCAPITAL/security/advisories/new) when enabled. Acknowledgment and remediation depend on maintainer availability; no response deadline is guaranteed. Coordinate disclosure and fixes privately.
