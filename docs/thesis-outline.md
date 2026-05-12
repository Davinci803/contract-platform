# VKR Thesis Outline

## Chapter 1: Problem
- Contract drift in microservices
- Cost of manual client/event library maintenance

## Chapter 2: Existing Approaches
- Contract-first development
- OpenAPI/AsyncAPI tooling and gaps

## Chapter 3: Requirements
- Functional and non-functional requirements
- MVP boundaries

## Chapter 4: Design
- Architecture and module decomposition
- Data model and compatibility logic

## Chapter 5: Implementation
- API, worker pipelines, UI
- Publication and observability

## Chapter 6: Evaluation
- Delivery time before/after automation
- Error reduction and compatibility findings

## Defense Evidence Mapping

- Thesis claim: "OpenAPI and AsyncAPI flows are end-to-end automated."
  - Code: `backend/worker/src/main/java/ru/vkr/contracts/worker/generation/openapi/OpenApiPipeline.java`,
    `backend/worker/src/main/java/ru/vkr/contracts/worker/generation/asyncapi/AsyncApiPipeline.java`
  - Tests: `OpenApiPipelineE2EIntegrationTest`, `AsyncApiPipelineE2EIntegrationTest`
- Thesis claim: "Partial failures are handled deterministically."
  - Code: retry policy in `GenerationJobProcessor`, compensation in `AsyncApiPublicationService`
  - Tests: `GenerationJobProcessorIntegrationTest`, `OpenApiPipelineNexusDowntimeE2EIntegrationTest`
- Thesis claim: "Observability is sufficient for operational analysis."
  - Code: `GenerationMetrics`, `CorrelationIdFilter`
  - Artifact: `docs/metrics.md`, actuator outputs, `publication_logs` trace
- Thesis claim: "Security and CI quality gates are in place."
  - Code: `SecurityConfig`
  - Artifact: `.github/workflows/ci.yml`, `SecurityAuthorizationIntegrationTest`

## Chapter 7: Conclusion
- Outcomes and future work
