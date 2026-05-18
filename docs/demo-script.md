# Demo Script (7-10 minutes)

## Goal

Show full contract lifecycle automation with verifiable artifacts (tests, logs, metrics, CI).

## Main Flow (7-10 min)

1. **00:00-01:00** Start stack (`infra/docker-compose.yml`) and show healthy services.
2. **01:00-02:30** Upload OpenAPI contract from UI and show created contract version.
3. **02:30-04:00** Run `Generate & Publish`, observe job lifecycle (`PENDING -> RUNNING -> SUCCESS/FAILED`).
4. **04:00-05:00** Show generated artifact/read-model entries and publication logs.
5. **05:00-06:30** Upload AsyncAPI contract and run generation with schema registration.
6. **06:30-07:30** Show compatibility report and migration advice for breaking-change example.
7. **07:30-08:30** Show observability: correlation-id trace and actuator generation metrics.
8. **08:30-09:30** Show CI evidence (`backend-quality`, `backend-integration-e2e`, `frontend-quality`, `security-scan`).

## Deterministic Runbook (no improvisation)

Use this exact order during defense:

1. Start stack:
   - `docker compose -f infra/docker-compose.yml up -d postgres zookeeper kafka schema-registry nexus api frontend`
2. Verify health:
   - `GET /actuator/health`
   - UI available on `http://localhost:5173`
3. OpenAPI scenario:
   - upload contract in UI
   - run generation
   - wait for terminal state
   - show read-model artifact entry
4. AsyncAPI scenario:
   - upload async contract
   - run generation
   - show schema subject and publication result
5. Compatibility scenario:
   - upload breaking revision
   - show compatibility report and migration advice
6. Observability:
   - copy `correlationId` from Step 2 (Job Status)
   - show `request -> job -> publication logs` trace by correlation id
   - show metrics endpoints (`duration`, `outcome`, `retry_needed`)
7. CI proof:
   - show latest green workflow and artifact reports

## Reference Endpoints (External Consumer)

Use these ready commands during demo and integration checks:

```bash
# Read-model artifacts (coordinates, publicationUrl, schemaSubject)
curl -u viewer:view123 "http://localhost:8080/api/read-model/artifacts?limit=10"

# Read-model publication events (eventType, status, errorCategory, correlationId)
curl -u viewer:view123 "http://localhost:8080/api/read-model/publication-logs?limit=20"

# Find job by correlation id (replace <corr-id>)
curl -u viewer:view123 "http://localhost:8080/api/generation-jobs?correlationId=<corr-id>"

# Filter read-model by the same correlation id
curl -u viewer:view123 "http://localhost:8080/api/read-model/publication-logs?limit=20&correlationId=<corr-id>"
curl -u viewer:view123 "http://localhost:8080/api/read-model/artifacts?limit=10&correlationId=<corr-id>"

# Actuator metrics
curl "http://localhost:8080/actuator/metrics/generation.pipeline.duration"
curl "http://localhost:8080/actuator/metrics/generation.pipeline.outcome"
curl "http://localhost:8080/actuator/metrics/generation.pipeline.retry_needed"

# Schema Registry reference endpoints
curl "http://localhost:8085/subjects"
curl "http://localhost:8085/subjects/{subject}/versions"
curl "http://localhost:8085/schemas/ids/{id}"
```

## Expected Checkpoints

- OpenAPI generation reaches terminal state and has artifact link.
- AsyncAPI generation reaches terminal state and has schema subject.
- Breaking example produces compatibility warning/report.
- Correlation trace contains end-to-end events for one job.
- Actuator metrics return non-empty `generation.pipeline.*` values.
- CI shows all required jobs executed.

## Fallback Plan (if external service is unavailable)

- If Nexus/Schema Registry is unavailable:
  - run failure scenario demo (`FAILED_RETRYABLE`/`RETRYING`) and show deterministic behavior in `PublicationLog`.
  - explain compensation markers in async pipeline logs (`rollback_ok` / `rollback_failed`).
- If UI is unavailable:
  - execute the same flow through API endpoints (`/api/contracts/versions`, `/api/generation-jobs`, `/api/read-model/*`).

### Fallback Procedure A (Nexus down)

1. Keep API running, stop Nexus or use invalid Nexus URL profile.
2. Trigger OpenAPI job.
3. Show:
   - job status `FAILED`
   - publication log events `RETRYING` and `FAILED_RETRYABLE`
   - deterministic max-attempt behavior.

### Fallback Procedure B (Schema Registry down)

1. Stop `schema-registry` service.
2. Trigger AsyncAPI job.
3. Show:
   - failure classification in `PublicationLog` (`RETRYING`, `FAILED_RETRYABLE`, `PIPELINE_FAILED`)
   - compensation marker from async publication flow (`compensation=not_required|rollback_ok|rollback_failed`).

### Fallback Procedure C (UI unavailable)

Run scenario only through API:

- `POST /api/contracts/versions`
- `POST /api/generation-jobs`
- `GET /api/generation-jobs/{id}`
- `GET /api/read-model/artifacts`
- `GET /api/read-model/publication-logs`

## 30-Minute Pre-Defense Checklist

- [ ] Docker services are healthy (`postgres`, `kafka`, `schema-registry`, `nexus`, `api`, `frontend`).
- [ ] Credentials are known and tested (`admin`, `developer`, `viewer`).
- [ ] API docs open (`/swagger-ui/index.html`) and actuator metrics reachable.
- [ ] At least one successful OpenAPI and one successful AsyncAPI job already warmed up.
- [ ] One prepared failure scenario exists (Nexus or Schema Registry unavailable).
- [ ] CI latest run is green and report artifacts are accessible.
- [ ] Demo contracts and payload examples are ready for copy/paste.
- [ ] Local logs are clean enough to show correlation trace quickly.

## Artifact Checklist for Commission

- Runtime setup: `infra/docker-compose.yml`
- Security behavior: `backend/api/src/test/java/ru/vkr/contracts/api/web/SecurityAuthorizationIntegrationTest.java`
- OpenAPI E2E: `backend/api/src/test/java/ru/vkr/contracts/api/service/OpenApiPipelineE2EIntegrationTest.java`
- AsyncAPI E2E: `backend/api/src/test/java/ru/vkr/contracts/api/service/AsyncApiPipelineE2EIntegrationTest.java`
- Partial failure behavior: `backend/api/src/test/java/ru/vkr/contracts/api/service/OpenApiPipelineNexusDowntimeE2EIntegrationTest.java`
- Retry + deterministic re-attempts: `backend/api/src/test/java/ru/vkr/contracts/api/service/GenerationJobProcessorIntegrationTest.java`
- CI workflow: `.github/workflows/ci.yml`
