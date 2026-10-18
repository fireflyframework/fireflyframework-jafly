# Firefly Framework - Starter Data

[![CI](https://github.com/fireflyframework/fireflyframework-starter-data/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-starter-data/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Opinionated starter for building data-processing microservices with job orchestration, data enrichment, quality gates, lineage tracking, and event-driven capabilities.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Starter Data provides a production-ready architecture for building data-processing microservices. It combines job orchestration for batch and async data processing with a data enrichment framework for third-party provider integration, all built on hexagonal architecture principles.

This starter is designed for data-tier microservices that need to:
- Execute complex, orchestrated data processing workflows
- Integrate with external data providers (credit bureaus, financial data, business intelligence)
- Enforce data quality rules and track data lineage across enrichment pipelines
- Implement CQRS patterns for command/query separation
- Build event-driven architectures with automatic event publishing
- Support distributed transactions using Saga, TCC, and Workflow patterns

## Features

**Job Orchestration**
- Async and sync execution modes with abstract controller and service base classes
- Standardized RESTful endpoints with job execution tracking and audit trails
- Configurable timeout enforcement per job stage

**Data Enrichment**
- Pluggable `DataEnricher` and `EnricherOperation` framework with tenant isolation
- Enrichment discovery controller for runtime operation catalog
- Smart enrichment controller with strategy-based routing
- Fallback chains with primary/secondary provider failover
- Per-provider resiliency configuration (circuit breaker, retry, rate limiter, bulkhead)
- Enrichment caching with configurable key generation and TTL
- Cost tracking and estimation per enrichment request

**Data Quality**
- Rule-based validation engine with configurable severity levels
- Fail-fast and collect-all evaluation strategies
- Built-in rules for null checks, range validation, pattern matching, and custom logic
- Quality gate integration for enrichment pipelines

**Data Lineage**
- Automatic lineage tracking across enrichment operations
- Pluggable `LineageTracker` with in-memory default implementation
- Lineage records with source, destination, timestamp, and metadata

**Data Transformation**
- Reactive `DataTransformer` interface with `TransformationChain` composition
- Built-in transformers for field mapping and computed fields
- Custom transformer support via functional interface

**Infrastructure**
- CQRS auto-configuration for command/query integration
- EDA auto-configuration for event-driven data processing
- Orchestration engine integration (Saga, TCC, Workflow)
- Observability with Micrometer metrics and distributed tracing
- Persistence auto-configuration for job results and audit entries

## Requirements

- Java 25
- Spring Boot 3.x
- Maven 3.9+
- PostgreSQL database (for job persistence)

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-data</artifactId>
    <version>26.02.07</version>
</dependency>
```

## Quick Start

```java
import org.fireflyframework.data.service.AbstractResilientDataJobService;
import org.fireflyframework.data.controller.AbstractDataJobController;

@RestController
@RequestMapping("/api/data-jobs")
public class MyDataJobController extends AbstractDataJobController<MyRequest, MyResponse> {

    public MyDataJobController(DataJobService<MyRequest, MyResponse> service) {
        super(service);
    }
}

@Service
public class MyDataJobService extends AbstractResilientDataJobService {

    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        return processData(request)
            .map(result -> JobStageResponse.success(
                JobStage.START, request.getExecutionId(), "Job completed"));
    }

    @Override
    protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
        return Mono.just(JobStageResponse.success(
            JobStage.CHECK, request.getExecutionId(), "Job is running"));
    }

    @Override
    protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
        return Mono.just(JobStageResponse.success(
            JobStage.COLLECT, request.getExecutionId(), "Results collected"));
    }

    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        return Mono.just(JobStageResponse.success(
            JobStage.RESULT, request.getExecutionId(), "Result retrieved"));
    }

    @Override
    protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) {
        return Mono.just(JobStageResponse.success(
            JobStage.STOP, request.getExecutionId(), "Job stopped"));
    }
}
```

## Configuration

```yaml
firefly:
  data:
    job-orchestration:
      enabled: true
      max-concurrent-jobs: 10
    enrichment:
      cache-enabled: true
      cache-ttl-seconds: 300
      operations:
        cache-enabled: true
        cache-ttl-seconds: 600
    quality:
      enabled: true
      fail-fast: false
    lineage:
      enabled: true
    persistence:
      enabled: true
    observability:
      metrics-enabled: true
      tracing-enabled: true
```

## Documentation

Comprehensive documentation is available in the [docs/](docs/) directory:

- **[Documentation Index](docs/README.md)** - Full table of contents and guide overview
- **[Architecture](docs/common/architecture.md)** - Hexagonal architecture and design patterns
- **[Getting Started](docs/common/getting-started.md)** - Step-by-step setup guide
- **[Configuration](docs/common/configuration.md)** - All configuration properties
- **[Data Enrichers Guide](docs/data-enrichers/guide.md)** - Building enrichers and operations
- **[Data Jobs Guide](docs/data-jobs/guide.md)** - Job orchestration and lifecycle
- **[Data Quality](docs/common/data-quality.md)** - Quality rules and validation engine
- **[Data Lineage](docs/common/data-lineage.md)** - Lineage tracking across pipelines
- **[Data Transformation](docs/common/data-transformation.md)** - Transformation chains and field mapping
- **[Resiliency](docs/common/resiliency.md)** - Circuit breaker, retry, and per-provider configuration
- **[API Reference](docs/common/api-reference.md)** - REST endpoint specifications
- **[GenAI Bridge](docs/common/genai-bridge.md)** - Integration with fireflyframework-genai

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
