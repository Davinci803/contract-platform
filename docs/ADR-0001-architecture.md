# ADR-0001: Architecture Baseline

## Status
Accepted

## Context
The VKR project needs fast delivery with a clear path to extension.

## Decision
- Use modular monolith architecture on Spring Boot.
- Split backend into modules: `api`, `worker`, `shared`.
- Keep generation and publication as async jobs.
- Use PostgreSQL for metadata and contract history.

## MVP vs Prod-Ready Trade-offs

### Chosen for MVP (implemented)

- Run worker pipelines in-process in API runtime (no separate worker deployment unit).
- Use Basic Auth with in-memory users for deterministic local/demo setup.
- Keep retry/compensation logic inside application code and publication logs.
- Use Docker Compose as reproducible demo environment for defense.

### Deferred for Prod-Ready (explicitly out of current scope)

- External queue/broker-backed orchestration (dedicated worker scaling and isolation).
- Centralized IAM/SSO and secret manager integration.
- Separate observability stack (Prometheus/Grafana/Loki) as first-class deployment artifact.
- Multi-region/high-availability publication infrastructure and stronger delivery guarantees.

## Evidence and Validation

- Runtime topology and containers: `infra/docker-compose.yml`
- Async job orchestration and retry policy: `backend/api/src/main/java/ru/vkr/contracts/api/service/GenerationJobProcessor.java`
- Compensation behavior (schema rollback marker): `backend/worker/src/main/java/ru/vkr/contracts/worker/generation/asyncapi/AsyncApiPublicationService.java`
- Security boundary and roles: `backend/api/src/main/java/ru/vkr/contracts/api/config/SecurityConfig.java`
- CI verification entrypoint: `.github/workflows/ci.yml`

## Consequences
- Faster MVP implementation and simpler deployment for defense.
- Clear module boundaries for possible future service extraction.
