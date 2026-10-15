SHELL := /bin/sh
COMPOSE ?= docker compose
MVN_IMAGE ?= maven:3.9-eclipse-temurin-21
SERVICES := auth-service gateway portfolio-service simulation-service

.PHONY: up down logs ps build restart test smoke-gateway smoke-gateway-ci tenant-isolation-matrix vendor-register-check

up:
	$(COMPOSE) up --build

down:
	$(COMPOSE) down -v --remove-orphans

logs:
	$(COMPOSE) logs -f --tail=200

ps:
	$(COMPOSE) ps

build:
	$(COMPOSE) build

restart:
	$(COMPOSE) restart

test:
	@set -e; for svc in $(SERVICES); do \
		echo "==> $$svc"; \
		docker run --rm -v "$(CURDIR)/backend/$$svc":/ws -w /ws $(MVN_IMAGE) mvn -q test; \
	done

smoke-gateway:
	@bash scripts/smoke/gateway_e2e_smoke.sh

smoke-gateway-ci:
	@bash scripts/smoke/gateway_e2e_smoke.sh --start-stack --cleanup

tenant-isolation-matrix:
	@bash scripts/ci/tenant_isolation_matrix.sh

vendor-register-check:
	@bash scripts/ci/vendor_register_check.sh
