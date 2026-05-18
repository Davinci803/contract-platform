# Observability and Metrics Guide

This document describes pipeline metrics and correlation tracing for `contract-platform-api`.

## Where metrics are exposed

- Actuator metrics endpoint: `GET /actuator/metrics`
- Metric detail endpoint: `GET /actuator/metrics/{metricName}`

Example:

```bash
curl http://localhost:8080/actuator/metrics/generation.pipeline.duration
curl http://localhost:8080/actuator/metrics/generation.pipeline.outcome.total
curl http://localhost:8080/actuator/metrics/generation.pipeline.retry_needed.total
```

## Metric catalog

### 1) Pipeline duration by contract type

- Name: `generation.pipeline.duration`
- Type: `Timer`
- Tags:
  - `contract_type`: `openapi | asyncapi`
  - `outcome`: `success | failed`

Interpretation:

- Use average and max duration trends to detect performance regressions.
- Compare `openapi` vs `asyncapi` to identify the slower vertical.

### 2) Pipeline success/failure totals

- Name: `generation.pipeline.outcome.total`
- Type: `Counter`
- Tags:
  - `contract_type`
  - `outcome`: `success | failed`

Interpretation:

- Compute failure rate:
  - `failed / (failed + success)` per contract type.
- Watch spikes after releases or dependency upgrades.

### 3) Retry-needed totals

- Name: `generation.pipeline.retry_needed.total`
- Type: `Counter`
- Tags:
  - `contract_type`
  - `reason`: `retryable_failure | queue_rejected | watchdog_timeout`

Interpretation:

- `retryable_failure`: transient failures returned by pipeline classification.
- `queue_rejected`: executor queue overload at submission time.
- `watchdog_timeout`: stale RUNNING jobs failed by recovery scheduler.

## Correlation tracing methodology

Correlation id is passed through all layers:

1. **Web layer**
   - `CorrelationIdFilter` reads `X-Correlation-Id` or creates one.
   - Value is added to MDC key `correlationId`.
   - Response returns header `X-Correlation-Id`.

2. **Service/Pipeline**
   - `GenerationJob` persists the same `correlationId`.
   - Async executor propagates MDC to worker thread.
   - Pipeline and recovery logs include the same id.

3. **DB logs**
   - `publication_logs.correlation_id` stores id for each pipeline event.

### Trace a single job

1. Capture correlation id from API response header.
2. Resolve job by correlation id:

```bash
curl -u viewer:view123 "http://localhost:8080/api/generation-jobs?correlationId=<id>"
curl -u viewer:view123 "http://localhost:8080/api/read-model/publication-logs?limit=20&correlationId=<id>"
curl -u viewer:view123 "http://localhost:8080/api/read-model/artifacts?limit=10&correlationId=<id>"
```

3. Search application logs by `corr:<id>`.
4. Query DB:

```sql
select j.id as job_id, j.status as job_status, j.correlation_id, j.created_at as job_created_at
from generation_jobs j
where j.correlation_id = '<id>';

select p.id, p.job_id, p.target, p.status, p.event_type, p.error_category, p.correlation_id, p.created_at
from publication_logs p
where p.correlation_id = '<id>'
order by p.created_at asc;
```

This gives a full chain from request to background pipeline to persisted audit trail.

## Metric-to-Artifact Mapping

- `generation.pipeline.duration`
  - Produced by: `backend/api/src/main/java/ru/vkr/contracts/api/config/GenerationMetrics.java`
  - Recorded in: `backend/api/src/main/java/ru/vkr/contracts/api/service/GenerationJobProcessor.java`
  - Demo verification: `GET /actuator/metrics/generation.pipeline.duration`
- `generation.pipeline.outcome.total`
  - Produced by: `GenerationMetrics.incrementPipelineOutcome(...)`
  - Source events: terminal job outcomes in `GenerationJobProcessor`
  - Demo verification: `GET /actuator/metrics/generation.pipeline.outcome.total`
- `generation.pipeline.retry_needed.total`
  - Produced by: retry scheduling / queue rejection / recovery watchdog
  - Sources:
    - `GenerationJobProcessor` (`retryable_failure`)
    - `GenerationJobService` (`queue_rejected`)
    - `GenerationJobRecoveryService` (`watchdog_timeout`)
  - Demo verification: `GET /actuator/metrics/generation.pipeline.retry_needed.total`

## Test Evidence

- Retry behavior and audit markers:
  - `backend/api/src/test/java/ru/vkr/contracts/api/service/GenerationJobProcessorIntegrationTest.java`
- Partial outage handling:
  - `backend/api/src/test/java/ru/vkr/contracts/api/service/OpenApiPipelineNexusDowntimeE2EIntegrationTest.java`

## Before/After Metrics Table (for defense)

Use this table in Chapter 6 and during demo. Fill values from repeatable test runs (same machine/profile).

| Metric | Before automation (manual baseline) | After automation (platform) | Evidence source |
| --- | --- | --- | --- |
| Time to publish OpenAPI contract | _fill from baseline run_ | _fill from `generation.pipeline.duration` + runbook time_ | `GET /actuator/metrics/generation.pipeline.duration`, demo timer |
| Time to publish AsyncAPI contract | _fill from baseline run_ | _fill from `generation.pipeline.duration` + runbook time_ | `GET /actuator/metrics/generation.pipeline.duration` (tag `contract_type=asyncapi`) |
| Failed publication ratio | _manual estimate_ | _from `generation.pipeline.outcome.total` tags_ | `GET /actuator/metrics/generation.pipeline.outcome.total` |
| Retry-needed events | N/A (manual retries not observable) | _from retry counter_ | `GET /actuator/metrics/generation.pipeline.retry_needed.total` |
| Traceability depth | Fragmented logs | End-to-end by correlation id | `publication_logs` query + application logs |

## Data Collection Protocol

1. Run at least 5 OpenAPI and 5 AsyncAPI generation jobs.
2. Record start/end timestamps for manual baseline and platform-assisted flow.
3. Export actuator snapshots for:
   - `generation.pipeline.duration`
   - `generation.pipeline.outcome.total`
   - `generation.pipeline.retry_needed.total`
4. Preserve one correlation-id trace in DB + logs as proof artifact.
5. Include raw snapshots as appendix references in thesis materials.
