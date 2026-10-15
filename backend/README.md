# Backend (Spring Boot microservices)

All services are independent Docker builds to avoid requiring Gradle installed locally.

Services:
- gateway (8080)
- auth-service (8081)
- portfolio-service (8082) — portfolio, risk, trade, funding, compliance, execution, market-data
- simulation-service (8083)

Shared:
- common library (DTOs, errors, conventions)

Security:
- auth-service issues JWT (HMAC secret)
- gateway validates JWT for protected routes
- downstream services optionally validate too (portfolio does validate)
