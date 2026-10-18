# fireflyframework-starter-data Documentation

An opinionated Spring Boot starter for building data-processing microservices with job orchestration, data enrichment, quality gates, lineage tracking, and event-driven capabilities.

---

## Table of Contents

| Section | Description |
|---------|-------------|
| [Getting Started](#getting-started) | Installation, prerequisites, and first steps |
| [Core Capabilities](#core-capabilities) | Data Jobs, Enrichers, Quality, Lineage, Transformation |
| [Infrastructure](#infrastructure) | Resiliency, observability, persistence, events |
| [Reference](#reference) | API docs, configuration, architecture, examples |
| [GenAI Integration](#genai-integration) | Python bridge for fireflyframework-genai |

---

## Getting Started

### Prerequisites

- Java 25
- Maven 3.9+
- Spring Boot 3.x
- Familiarity with reactive programming (Project Reactor)

### Installation

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.02.06</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-starter-data</artifactId>
        <version>26.02.06</version>
    </dependency>
</dependencies>
```

### First Steps

| Goal | Guide |
|------|-------|
| Understand the architecture | [Architecture Overview](common/architecture.md) |
| Build a data job microservice | [Data Jobs Guide](data-jobs/guide.md) |
| Build a data enricher microservice | [Data Enrichers Guide](data-enrichers/guide.md) |
| See working code examples | [Examples](common/examples.md) |
| Step-by-step walkthrough | [Getting Started](common/getting-started.md) |

---

## Core Capabilities

### Data Jobs

Orchestrated workflows for batch and async data processing with lifecycle management (start, check, collect, result, stop).

| Topic | Link |
|-------|------|
| Complete guide (async and sync) | [Data Jobs Guide](data-jobs/guide.md) |
| Overview and concepts | [Data Jobs Overview](data-jobs/README.md) |

Key features:
- Abstract base classes (`AbstractResilientDataJobService`, `AbstractResilientSyncDataJobService`)
- Standardized REST endpoints via `AbstractDataJobController`
- Configurable per-stage timeout enforcement
- Job execution result persistence and audit trails

### Data Enrichers

Third-party provider integration for fetching and enriching data from external sources (credit bureaus, financial data, business intelligence).

| Topic | Link |
|-------|------|
| Step-by-step guide | [Data Enrichers Guide](data-enrichers/guide.md) |
| Overview and concepts | [Data Enrichers Overview](data-enrichers/README.md) |

Key features:
- Pluggable `DataEnricher` and `EnricherOperation` framework with tenant isolation
- Fallback chains with primary/secondary provider failover (`@EnricherFallback`)
- Smart enrichment controller with strategy-based routing
- Enrichment discovery controller for runtime operation catalog
- Per-provider resiliency configuration (circuit breaker, retry, rate limiter, bulkhead)
- Enrichment caching with configurable key generation and TTL
- Cost tracking, estimation, and preview/dry-run support
- SSE streaming for real-time batch enrichment results

### Data Quality

Rule-based validation engine with configurable severity levels and evaluation strategies.

| Topic | Link |
|-------|------|
| Framework guide | [Data Quality](common/data-quality.md) |

Key features:
- Fail-fast and collect-all evaluation strategies
- Built-in rules: null checks, range validation, pattern matching, custom logic
- Quality gate integration for enrichment pipelines

### Data Lineage

Provenance tracking across enrichment operations and transformation pipelines.

| Topic | Link |
|-------|------|
| Tracking guide | [Data Lineage](common/data-lineage.md) |

Key features:
- Automatic lineage recording across enrichment operations
- Pluggable `LineageTracker` with in-memory default implementation
- Records with source, destination, timestamp, and metadata

### Data Transformation

Composable, reactive transformation chains for post-enrichment data processing.

| Topic | Link |
|-------|------|
| Transformation guide | [Data Transformation](common/data-transformation.md) |

Key features:
- Reactive `DataTransformer` interface with `TransformationChain` composition
- Built-in transformers: `FieldMappingTransformer`, `ComputedFieldTransformer`
- Custom transformer support via functional interface

---

## Infrastructure

### Resiliency

Fault tolerance patterns applied automatically and configurable per provider.

| Topic | Link |
|-------|------|
| Patterns and configuration | [Resiliency](common/resiliency.md) |

Includes circuit breaker, retry with exponential backoff, rate limiting, and bulkhead isolation. Supports global defaults and per-provider overrides.

### Observability

Monitoring, metrics, distributed tracing, and health checks.

| Topic | Link |
|-------|------|
| Observability guide | [Observability](common/observability.md) |
| Logging guide | [Logging](common/logging.md) |

Includes Micrometer integration with OpenTelemetry, automatic metrics for all operations, health check endpoints, and structured JSON logging.

### Persistence

Job execution results and audit trail storage.

| Topic | Link |
|-------|------|
| Persistence guide | [Persistence](common/persistence.md) |

### Event-Driven Architecture

Automatic event publishing for job and enrichment lifecycle events.

| Topic | Link |
|-------|------|
| Architecture overview | [Architecture](common/architecture.md) |
| Configuration | [Configuration](common/configuration.md) |

Includes CQRS integration, EDA auto-configuration, and orchestration engine support (Saga, TCC, Workflow).

---

## Reference

| Document | Description |
|----------|-------------|
| [Architecture](common/architecture.md) | Hexagonal architecture, design patterns, component diagram |
| [Configuration](common/configuration.md) | All configuration properties with defaults and examples |
| [API Reference](common/api-reference.md) | REST endpoint specifications for all controllers |
| [MapStruct Mappers](common/mappers.md) | Data mapping conventions and mapper configuration |
| [Testing](common/testing.md) | Testing strategies, utilities, and examples |
| [Examples](common/examples.md) | Real-world usage patterns and recipes |

---

## GenAI Integration

Python bridge package (`fireflyframework-genai-data`) for native integration with `fireflyframework-genai`.

| Topic | Link |
|-------|------|
| Integration guide | [GenAI Bridge](common/genai-bridge.md) |

Provides:
- `DataStarterClient` for HTTP communication with Java data services
- Agent tools (`DataEnrichmentTool`, `DataJobTool`, `DataOperationsTool`) and `DataToolKit`
- Pipeline steps (`EnrichmentStep`, `QualityGateStep`) for GenAI pipelines
- `DataLineageMiddleware` for automatic lineage tracking in agent runs
- Pre-built agent template (`create_data_analyst_agent`)

---

## Architecture Overview

```
+------------------------------------------------------------------+
|                        Your Application                          |
|                                                                  |
|  +----------------+  +----------------+  +----------------+      |
|  |   Data Jobs    |  | Data Enrichers |  | Quality &      |      |
|  |                |  |                |  | Lineage        |      |
|  |  - Async       |  |  - Credit      |  |  - Rules       |      |
|  |  - Sync        |  |  - Company     |  |  - Tracking    |      |
|  +-------+--------+  +-------+--------+  +-------+--------+      |
|          |                    |                    |               |
|  +-------+--------------------+--------------------+--------+     |
|  |            fireflyframework-starter-data (Core)           |     |
|  |                                                           |     |
|  |  - Abstract base classes       - Fallback chains          |     |
|  |  - Observability (automatic)   - Cost tracking            |     |
|  |  - Resiliency (per-provider)   - Transformation chains    |     |
|  |  - Event publishing            - Preview & SSE            |     |
|  +-------+--------------------+--------------------+--------+     |
|          |                    |                    |               |
|  +-------+--------+  +-------+--------+  +-------+--------+      |
|  | Orchestrators  |  |   Providers    |  |  GenAI Bridge  |      |
|  |                |  |                |  |                |      |
|  |  - Airflow     |  |  - REST APIs   |  |  - Tools       |      |
|  |  - AWS SF      |  |  - SOAP APIs   |  |  - Steps       |      |
|  |  - Mock        |  |  - gRPC APIs   |  |  - Agents      |      |
|  +----------------+  +----------------+  +----------------+      |
+------------------------------------------------------------------+
```

See [Architecture](common/architecture.md) for detailed design patterns and component documentation.

---

Copyright 2024-2026 Firefly Software Foundation. All rights reserved.
