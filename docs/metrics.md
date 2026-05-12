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
2. Search application logs by `corr:<id>`.
3. Query DB:

```sql
select id, job_id, target, status, event_type, correlation_id, created_at
from publication_logs
where correlation_id = '<id>'
order by created_at asc;
```

This gives a full chain from request to background pipeline to persisted audit trail.
