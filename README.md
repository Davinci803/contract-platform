# Contract Platform

Contract Platform is a VKR-focused automation platform for service contracts.
It supports OpenAPI and AsyncAPI pipelines with compatibility checks, Java code
generation, and publication metadata for Maven and Schema Registry.

## MVP Scope

- OpenAPI upload, validation, compatibility analysis and Java client generation
- AsyncAPI upload, validation, compatibility analysis and Kafka library generation
- Contract versioning with SemVer recommendation
- Publication status tracking (Maven + Schema Registry)
- Demo-ready web UI for end-to-end flow visibility

## Tech Stack

- Java 21, Spring Boot 3, Maven
- PostgreSQL
- Kafka + Schema Registry
- Nexus (in docker-compose)
- React + Vite

## Local Start

1. Start infra:
   - `docker compose -f infra/docker-compose.yml up -d`
2. Run backend API:
   - `mvn -f backend/pom.xml -pl api spring-boot:run`
3. Run frontend:
   - `cd frontend && npm install && npm run dev`

## Repository Layout

- `backend/` - API, worker, shared modules
- `frontend/` - React UI
- `infra/` - Docker Compose infrastructure
- `templates/` - generation templates placeholders
- `docs/` - ADR, architecture and thesis materials
