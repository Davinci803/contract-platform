# Contract Platform

Contract Platform is a VKR-oriented platform for managing service contracts and
automating client/library publication pipelines for two contract types:

- `OPENAPI` (REST)
- `ASYNCAPI` (Kafka/events)

The project accepts contract versions, runs compatibility analysis, launches
asynchronous generation jobs, and stores publication/reporting artifacts.

## What Works Now

- Contract upload and version history (`OPENAPI` / `ASYNCAPI`)
- Asynchronous generation jobs with lifecycle:
  - `PENDING -> RUNNING -> SUCCESS/FAILED`
- OpenAPI pipeline:
  - parse/validate
  - structural diff
  - Java source generation
  - JAR/POM build
  - publish to Nexus
- AsyncAPI pipeline:
  - parse/validate
  - Java source generation
  - schema registration in Schema Registry
  - JAR/POM build
  - publish to Nexus
- Structural compatibility analysis:
  - dedicated rules for REST and Kafka
  - severity levels (`CRITICAL`, `MAJOR`, `MINOR`, `ADVISORY`)
  - SemVer recommendation (`MAJOR`, `MINOR`, `PATCH`)
  - JSON findings stored in compatibility report
- Read model summary (`artifacts`, `publicationLogs`)
- Demo UI (React + Vite) for upload/generation/status/summary flow

## Tech Stack

- Backend: Java 21, Spring Boot 3, Maven
- Data: PostgreSQL + Flyway
- Event infra: Redpanda (Kafka API) + Schema Registry endpoint
- Artifact storage: Nexus3
- Frontend: React 18 + Vite

## Repository Structure

- `backend/`
  - `shared/` common DTOs/models
  - `worker/` generation + compatibility engines
  - `api/` REST API, persistence, async job orchestration
- `frontend/` demo web UI
- `infra/` local docker-compose stack (Postgres, Kafka, Schema Registry, Nexus)
- `docs/` project/VKR documentation
- `templates/` generation templates

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 18+ and npm
- Docker + Docker Compose

## Local Run

### 1) Start Infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d
```

Services:

- PostgreSQL: `localhost:5432`
- Kafka (Redpanda): `localhost:9092`
- Schema Registry proxy: `http://localhost:8085`
- Nexus: `http://localhost:8081`

### 2) Run Backend API

```bash
mvn -f backend/pom.xml -pl api spring-boot:run
```

API default URL: `http://localhost:8080`

### 3) Run Frontend

```bash
cd frontend
npm install
npm run dev
```

UI default URL: `http://localhost:5173`

## API Overview

All `/api/**` endpoints require HTTP Basic authentication.

### Default local users

- `admin / admin123`
- `developer / dev123`
- `viewer / view123`

### Endpoints

- `POST /api/contracts/versions`
  - upload a contract version
- `GET /api/contracts/{contractId}/versions`
  - list version history for a contract
- `POST /api/generation-jobs`
  - create async generation job for a contract version
- `GET /api/generation-jobs/{jobId}`
  - get current job status and job log
- `GET /api/compatibility-reports`
  - list compatibility reports
- `GET /api/read-model/summary`
  - get simple counters (`artifacts`, `publicationLogs`)

## Quick API Example

```bash
# 1) Upload OpenAPI contract version
curl -u developer:dev123 -X POST http://localhost:8080/api/contracts/versions \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Payment Contract",
    "type":"OPENAPI",
    "content":"openapi: 3.0.1\ninfo:\n  title: Payment API\n  version: 1.0.0\npaths:\n  /payments:\n    get:\n      responses:\n        \"200\":\n          description: ok",
    "author":"local-user"
  }'
```

```bash
# 2) Start generation job (replace CONTRACT_VERSION_ID)
curl -u developer:dev123 -X POST http://localhost:8080/api/generation-jobs \
  -H "Content-Type: application/json" \
  -d '{"contractVersionId": CONTRACT_VERSION_ID}'
```

```bash
# 3) Check job status (replace JOB_ID)
curl -u developer:dev123 http://localhost:8080/api/generation-jobs/JOB_ID
```

## Configuration

Main runtime config: `backend/api/src/main/resources/application.yml`.

Key environment variables:

- Database:
  - `DB_URL`, `DB_USER`, `DB_PASSWORD`
- Nexus:
  - `NEXUS_BASE_URL`, `NEXUS_REPOSITORY`, `NEXUS_USERNAME`, `NEXUS_PASSWORD`
- AsyncAPI / Schema Registry:
  - `SCHEMA_REGISTRY_URL`
  - `SCHEMA_REGISTRY_USERNAME`, `SCHEMA_REGISTRY_PASSWORD`
  - `SCHEMA_REGISTRY_COMPATIBILITY`
  - `SCHEMA_REGISTRY_SCHEMA_TYPE`
  - `SCHEMA_REGISTRY_SUBJECT_SUFFIX`
- Generation coordinates:
  - `GEN_OPENAPI_GROUP_ID`, `GEN_OPENAPI_ARTIFACT_SUFFIX`
  - `GEN_ASYNCAPI_GROUP_ID`, `GEN_ASYNCAPI_ARTIFACT_SUFFIX`

## Testing

Run backend tests:

```bash
mvn -f backend/pom.xml test
```

Run only worker tests:

```bash
mvn -f backend/pom.xml -pl worker test
```

The compatibility regression suite includes matrix scenarios for both
`OPENAPI` and `ASYNCAPI`.

## Notes

- Current frontend is demo-oriented and uses hardcoded API URL/credentials.
- Security users are currently in-memory (development profile style).
- The project is actively evolving according to `VKR-backlog.md`.
