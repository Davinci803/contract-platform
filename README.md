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
- `infra/` docker-compose stack (infra + app services)
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
- API: `http://localhost:8080`
- UI: `http://localhost:5173`

### 1a) Single-command containerized startup (recommended)

```bash
docker compose -f infra/docker-compose.yml up -d --build
```

Smoke checks:

```bash
curl http://localhost:8080/actuator/health
curl -I http://localhost:5173
```

Expected:

- API health returns `{"status":"UP"}`.
- Frontend responds with `200 OK`.

### 2) Run Backend API

Skip this step if you started `api` via docker-compose.

```bash
mvn -f backend/pom.xml -pl api spring-boot:run
```

API default URL: `http://localhost:8080`

### 3) Run Frontend

Skip this step if you started `frontend` via docker-compose.

```bash
cp frontend/.env.example frontend/.env.local
cd frontend
npm install
npm run dev
```

UI default URL: `http://localhost:5173`

Frontend requires env-based API configuration:

- `VITE_API_BASE_URL` (example: `http://localhost:8080`)
- `VITE_API_USERNAME`
- `VITE_API_PASSWORD`
- `VITE_DEV_SERVER_PORT` (optional, default `5173`)

## API Overview

All `/api/**` endpoints require HTTP Basic authentication.

### Local users (dev profile defaults)

- `admin / admin123`
- `developer / dev123`
- `viewer / view123`

Credentials are loaded from environment variables in backend config:
`SECURITY_ADMIN_*`, `SECURITY_DEVELOPER_*`, `SECURITY_VIEWER_*`.

### Endpoints

- `POST /api/contracts/versions`
  - upload a contract version
- `GET /api/contracts`
  - list available contracts
- `GET /api/contracts/{contractId}/versions`
  - list version history for a contract
- `POST /api/generation-jobs`
  - create async generation job for a contract version
- `GET /api/generation-jobs/{jobId}`
  - get current job status and job log
- `GET /api/generation-jobs?correlationId={id}`
  - find latest job by correlation id
- `GET /api/compatibility-reports`
  - list compatibility reports
- `GET /api/read-model/summary`
  - get simple counters (`artifacts`, `publicationLogs`)
- `GET /api/read-model/artifacts`
  - list latest generated artifacts with coordinates/publication URLs
  - optional query param: `correlationId`
- `GET /api/read-model/publication-logs`
  - list recent pipeline/publication events
  - optional query param: `correlationId`

### Integration Reference Commands

```bash
# Artifacts read-model fields:
# - coordinates
# - publicationUrl
# - schemaSubject
curl -u viewer:view123 "http://localhost:8080/api/read-model/artifacts?limit=10"

# Publication-log read-model fields:
# - eventType
# - status
# - errorCategory
# - correlationId
curl -u viewer:view123 "http://localhost:8080/api/read-model/publication-logs?limit=20"

# Correlation trace helpers
curl -u viewer:view123 "http://localhost:8080/api/generation-jobs?correlationId=<corr-id>"
curl -u viewer:view123 "http://localhost:8080/api/read-model/publication-logs?limit=20&correlationId=<corr-id>"
curl -u viewer:view123 "http://localhost:8080/api/read-model/artifacts?limit=10&correlationId=<corr-id>"

# Actuator metrics reference
curl "http://localhost:8080/actuator/metrics/generation.pipeline.duration"
curl "http://localhost:8080/actuator/metrics/generation.pipeline.outcome"
curl "http://localhost:8080/actuator/metrics/generation.pipeline.retry_needed"

# Schema Registry reference
curl "http://localhost:8085/subjects"
curl "http://localhost:8085/subjects/{subject}/versions"
curl "http://localhost:8085/schemas/ids/{id}"
```

### API documentation

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

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

### Example responses

Successful upload (`201 Created`):

```json
{
  "contractId": 1,
  "contractName": "Payment Contract",
  "contractType": "OPENAPI",
  "contractVersionId": 10,
  "version": "1.0.0"
}
```

Unified error response (`4xx/5xx`):

```json
{
  "timestamp": "2026-05-07T20:25:41.443Z",
  "status": 400,
  "error": "Contract version not found: 999999",
  "path": "/api/generation-jobs"
}
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
- Security users:
  - `SECURITY_ADMIN_USERNAME`, `SECURITY_ADMIN_PASSWORD`
  - `SECURITY_DEVELOPER_USERNAME`, `SECURITY_DEVELOPER_PASSWORD`
  - `SECURITY_VIEWER_USERNAME`, `SECURITY_VIEWER_PASSWORD`
- Frontend (Vite):
  - `VITE_API_BASE_URL`
  - `VITE_API_USERNAME`, `VITE_API_PASSWORD`
  - `VITE_DEV_SERVER_PORT`

Security policy by profile:

- non-`prod`: health/info are public, API is role-protected.
- `prod`: HTTPS is required, `/actuator/info` is admin-only, API keeps role restrictions.

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

- Frontend reads API URL/auth from `VITE_*` env variables.
- Security users are in-memory, but loaded from env/profile config.
- Observability metrics/tracing guide: `docs/metrics.md`.
- The project is actively evolving according to `VKR-backlog.md`.
