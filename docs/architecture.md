# Architecture

```mermaid
flowchart LR
  developer[Developer] --> ui[Frontend]
  ui --> api[BackendAPI]
  api --> db[(PostgreSQL)]
  api --> queue[JobQueue]
  queue --> worker[GeneratorWorker]
  worker --> nexus[(Nexus)]
  worker --> schema[(SchemaRegistry)]
  worker --> kafka[(Kafka)]
```

## Components

- Backend API: uploads contracts, stores versions, starts jobs
- Worker: runs OpenAPI/AsyncAPI pipelines
- Compatibility Engine: classifies breaking vs compatible changes
- Publisher: tracks publication status for Nexus and Registry
