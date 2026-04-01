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

## Consequences
- Faster MVP implementation and simpler deployment for defense.
- Clear module boundaries for possible future service extraction.
