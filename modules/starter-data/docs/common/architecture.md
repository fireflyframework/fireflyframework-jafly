# Architecture

This document provides a comprehensive overview of the `fireflyframework-starter-data` architecture, design patterns, and integration points.

## Table of Contents

- [Overview](#overview)
- [Hexagonal Architecture](#hexagonal-architecture)
- [Core Components](#core-components)
- [Integration Architecture](#integration-architecture)
- [Data Flow](#data-flow)
- [Design Patterns](#design-patterns)
- [Component Interactions](#component-interactions)

---

## Overview

The `fireflyframework-starter-data` starter is built on **Hexagonal Architecture** (also known as Ports and Adapters pattern), which provides:

- **Clean separation** between business logic and infrastructure
- **Pluggable adapters** for different orchestration platforms
- **Testability** through dependency inversion
- **Flexibility** to swap implementations without changing core logic

### Architectural Principles

1. **Dependency Inversion** - Core domain depends on abstractions, not implementations
2. **Interface Segregation** - Small, focused interfaces for specific purposes
3. **Single Responsibility** - Each component has one clear purpose
4. **Open/Closed** - Open for extension, closed for modification
5. **Reactive Programming** - Non-blocking, event-driven operations

---

## Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        External World                           │
│  ┌──────────────┐  ┌───────────────┐  ┌───────────────┐         │
│  │ REST Clients │  │ Airflow/AWS   │  │ Kafka/RabbitMQ│         │
│  └──────┬───────┘  └───────┬───────┘  └───────┬───────┘         │
└─────────┼──────────────────┼──────────────────┼─────────────────┘
          │                  │                  │
          │ HTTP             │ REST API         │ Events
          │                  │                  │
┌─────────┼──────────────────┼──────────────────┼─────────────────┐
│         ▼                  ▼                  ▼                 │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐          │
│  │   Adapter    │  │   Adapter    │  │   Adapter     │          │
│  │ (Controller) │  │(Orchestrator)│  │(EDA Publisher)│          │
│  └──────┬───────┘  └──────┬───────┘  └────────┬──────┘          │
│         │                  │                  │                 │
│         │                  │                  │                 │
│  ┌──────┴──────────────────┴──────────────────┴───────┐         │
│  │                                                    │         │
│  │              PORT INTERFACES                       │         │
│  │  ┌─────────────────────────────────────────────┐   │         │
│  │  │ DataJobController (REST API Port)           │   │         │
│  │  │ JobOrchestrator (Orchestration Port)        │   │         │
│  │  │ EventPublisher (EDA Port)                   │   │         │
│  │  │ OrchestrationEventPublisher (Orch. Port)    │   │         │
│  │  └─────────────────────────────────────────────┘   │         │
│  │                                                    │         │
│  │              DOMAIN CORE                           │         │
│  │  ┌─────────────────────────────────────────────┐   │         │
│  │  │ DataJobService (Business Logic)             │   │         │
│  │  │ JobResultMapperRegistry (Transformation)    │   │         │
│  │  │ Job Models (Domain Objects)                 │   │         │
│  │  │ Job Lifecycle (START→CHECK→COLLECT→RESULT)  │   │         │
│  │  └─────────────────────────────────────────────┘   │         │
│  │                                                    │         │
│  └────────────────────────────────────────────────────┘         │
│                                                                 │
│                  fireflyframework-starter-data                  │
└─────────────────────────────────────────────────────────────────┘
```

### Port Types

#### Inbound Ports (Driving)
- **DataJobController** - REST API interface for external clients
- Defines what the application can do
- Implemented by adapters (controllers)

#### Outbound Ports (Driven)
- **JobOrchestrator** - Interface for workflow orchestration
- **EventPublisher** - Interface for event publishing
- **OrchestrationEventPublisher** - Interface for orchestration events
- Defines what the application needs
- Implemented by infrastructure adapters

---

## Core Components

### 1. Configuration Layer

Auto-configuration classes that enable seamless integration:

```
config/
├── CqrsAutoConfiguration          # CQRS pattern support
├── EdaAutoConfiguration            # Event-driven architecture
├── JobOrchestrationAutoConfiguration  # Job orchestration setup
├── JobOrchestrationProperties      # Configuration properties
└── OrchestrationAutoConfiguration  # Orchestration engine support
```

**Key Features:**
- Conditional bean creation based on classpath and properties
- Zero-configuration defaults with override capability
- Spring Boot auto-configuration mechanism
- Component scanning for automatic discovery

### 2. Controller Layer

REST API interface definitions:

#### Asynchronous Jobs (Multi-Stage)

```java
@Tag(name = "Data Jobs")
@RequestMapping("/api/v1/jobs")
public interface DataJobController {
    @PostMapping("/start")
    Mono<JobStageResponse> startJob(@Valid @RequestBody JobStageRequest request);

    @GetMapping("/{executionId}/check")
    Mono<JobStageResponse> checkJob(@PathVariable String executionId, ...);

    @GetMapping("/{executionId}/collect")
    Mono<JobStageResponse> collectJobResults(@PathVariable String executionId, ...);

    @GetMapping("/{executionId}/result")
    Mono<JobStageResponse> getJobResult(@PathVariable String executionId, ...);
}
```

#### Synchronous Jobs (Single-Stage)

```java
@Tag(name = "Sync Data Jobs")
@RequestMapping("/api/v1")
public interface SyncDataJobController {
    @PostMapping("/execute")
    Mono<JobStageResponse> execute(@RequestParam Map<String, Object> parameters, ...);
}
```

**Responsibilities:**
- Define REST API contract
- OpenAPI/Swagger documentation
- Request validation
- Delegate to service layer

### 3. Service Layer

Business logic interfaces:

#### Asynchronous Jobs (Multi-Stage)

```java
public interface DataJobService {
    Mono<JobStageResponse> startJob(JobStageRequest request);
    Mono<JobStageResponse> checkJob(JobStageRequest request);
    Mono<JobStageResponse> collectJobResults(JobStageRequest request);
    Mono<JobStageResponse> getJobResult(JobStageRequest request);
}
```

#### Synchronous Jobs (Single-Stage)

```java
public interface SyncDataJobService {
    Mono<JobStageResponse> execute(JobStageRequest request);
}
```

**Responsibilities:**
- Implement job lifecycle logic
- Coordinate with orchestrator (async) or execute directly (sync)
- Handle transformations via mappers
- Publish events for observability
- Provide observability, resiliency, and persistence (via abstract base classes)

### 4. Model Layer

Domain models representing job concepts:

```
model/
├── JobStage                # Enum: START, CHECK, COLLECT, RESULT
├── JobStageRequest         # Request DTO with parameters
└── JobStageResponse        # Response DTO with results
```

### 5. Orchestration Layer

Port/adapter for workflow orchestration:

```
orchestration/
├── port/
│   └── JobOrchestrator     # Port interface
└── model/
    ├── JobExecution        # Execution details
    ├── JobExecutionRequest # Start request
    └── JobExecutionStatus  # Status enum
```

**Port Interface:**
```java
public interface JobOrchestrator {
    Mono<JobExecution> startJob(JobExecutionRequest request);
    Mono<JobExecutionStatus> checkJobStatus(String executionId);
    Mono<JobExecutionStatus> stopJob(String executionId, String reason);
    Mono<JobExecution> getJobExecution(String executionId);
    String getOrchestratorType();
}
```

**Orchestrator Support:**

The library provides the `JobOrchestrator` port interface that can be implemented for any workflow orchestrator:

1. **Apache Airflow** (Recommended)
   - Open-source workflow orchestration platform
   - REST API integration
   - Flexible DAG-based workflows
   - Supports complex data pipelines
   - Configuration: `orchestrator-type: APACHE_AIRFLOW`
   - **Note**: Adapter implementation required in your application

2. **AWS Step Functions**
   - Serverless orchestration service
   - Native AWS integration
   - Visual workflow designer
   - Pay-per-use pricing
   - Configuration: `orchestrator-type: AWS_STEP_FUNCTIONS`
   - **Note**: Adapter implementation required in your application

3. **Custom Orchestrators**
   - Implement the `JobOrchestrator` interface
   - Full control over orchestration logic
   - Integration with proprietary systems

> **Important**: This library provides the port interface (`JobOrchestrator`) and configuration support. You must implement the adapter for your chosen orchestrator in your application. See the [Getting Started](getting-started.md) guide for implementation examples.

### 6. Mapper Layer

MapStruct integration for result transformation:

```
mapper/
├── JobResultMapper         # Generic mapper interface
└── JobResultMapperRegistry # Auto-discovery registry
```

**Generic Interface:**
```java
public interface JobResultMapper<S, T> {
    T mapToTarget(S source);
    default Class<S> getSourceType() { ... }
    default Class<T> getTargetType() { ... }
}
```

### 7. Orchestration Events Layer

Orchestration engine integration:

```
orchestration/
└── EventGateway  # Bridges orchestration events to EDA (provided by fireflyframework-orchestration)
```

### 8. Data Quality Layer

Rule-based validation framework:

```
quality/
├── DataQualityRule<T>        # Port interface for validation rules
├── DataQualityEngine         # Evaluates rules with strategy (FAIL_FAST / COLLECT_ALL)
├── QualityResult             # Individual rule evaluation result
├── QualityReport             # Aggregated report with pass/fail counts
├── QualitySeverity           # INFO, WARNING, CRITICAL
├── QualityStrategy           # FAIL_FAST, COLLECT_ALL
└── rules/
    ├── NotNullRule            # Built-in: required field validation
    ├── PatternRule            # Built-in: regex pattern validation
    └── RangeRule              # Built-in: numeric range validation
```

### 9. Data Lineage Layer

Provenance tracking for data operations:

```
lineage/
├── LineageTracker            # Port interface
├── LineageRecord             # Immutable lineage entry
└── InMemoryLineageTracker    # Default in-memory implementation
```

### 10. Data Transformation Layer

Composable post-enrichment transformations:

```
transform/
├── DataTransformer<S, T>     # Functional interface
├── TransformationChain<S, T> # Composable chain with then()
├── TransformContext           # Request context carrier
├── FieldMappingTransformer   # Rename map keys
└── ComputedFieldTransformer  # Add derived fields
```

### 11. Enrichment Fallback Layer

Provider failover with chain support:

```
enrichment/
├── @EnricherFallback         # Annotation: fallbackTo, strategy, maxFallbacks
├── FallbackStrategy          # ON_ERROR, ON_EMPTY, ON_ERROR_OR_EMPTY
└── FallbackEnrichmentExecutor # Recursive fallback chain executor
```

### 12. Cost Tracking Layer

Per-provider enrichment cost accounting:

```
cost/
├── EnrichmentCostTracker     # Thread-safe call counting
├── CostReport                # Per-provider breakdown and totals
└── EnrichmentCostController  # REST API: GET /api/v1/enrichment/costs
```

---

## Integration Architecture

### Integration with fireflyframework-eda

```
┌─────────────────────────────────────────────────────────┐
│              fireflyframework-starter-data               │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  Job Events                                │         │
│  │  - Job started                             │         │
│  │  - Job completed                           │         │
│  │  - Job failed                              │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│  ┌────────────▼───────────────────────────────┐         │
│  │  Orchestration EventGateway                │         │
│  │  - Orchestration step events               │         │
│  │  - Metadata enrichment                     │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              fireflyframework-eda                        │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  EventPublisher                            │         │
│  │  - Multi-platform support                  │         │
│  │  - Resilience patterns                     │         │
│  │  - Metrics & monitoring                    │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│         Message Brokers                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │  Kafka   │  │ RabbitMQ │  │   SQS    │               │
│  └──────────┘  └──────────┘  └──────────┘               │
└─────────────────────────────────────────────────────────┘
```

**Integration Points:**
- Job lifecycle events published to EDA
- Orchestration step events bridged to EDA
- Configurable topics and routing
- Automatic metadata enrichment

### Integration with fireflyframework-cqrs

```
┌─────────────────────────────────────────────────────────┐
│              fireflyframework-starter-data               │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  DataJobService                            │         │
│  │  - Write operations (Commands)             │         │
│  │  - Read operations (Queries)               │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              fireflyframework-cqrs                       │
│                                                         │
│  ┌────────────────────┐  ┌────────────────────┐         │
│  │  CommandHandler    │  │  QueryHandler      │         │
│  │  - Start job       │  │  - Get job status  │         │
│  │  - Stop job        │  │  - Get results     │         │
│  └────────────────────┘  └────────────────────┘         │
└─────────────────────────────────────────────────────────┘
```

**Separation:**
- **Commands** - Modify state (START, STOP)
- **Queries** - Read state (CHECK, COLLECT, RESULT)
- Enables independent scaling of read/write operations

### Integration with Orchestration Engine

```
┌─────────────────────────────────────────────────────────┐
│         fireflyframework-orchestration                  │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  Orchestration Engine (Saga, TCC, Workflow)│         │
│  │  - Step execution                          │         │
│  │  - Compensation logic                      │         │
│  │  - Transaction coordination                │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│               │ Orchestration Events                    │
│               ▼                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  OrchestrationEventPublisher (interface)   │         │
│  │  EventGateway (bridges to EDA)             │         │
│  └────────────────────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              fireflyframework-starter-data               │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  Consumes orchestration events             │         │
│  │  (EventGateway bridges to EDA)             │         │
│  │  - Enriches with data context              │         │
│  │  - Routes to EDA infrastructure            │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
         (to fireflyframework-eda)
```

---

## Data Flow

### Complete Job Lifecycle Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. START Stage                                                  │
│                                                                 │
│  Client Request                                                 │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.startJob()                                   │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.startJob()                                      │
│       │                                                         │
│       ▼                                                         │
│  JobOrchestrator.startJob()  ──────► Airflow/AWS Step Functions │
│       │                                                         │
│       ▼                                                         │
│  Return: executionId, status=RUNNING                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 2. CHECK Stage                                                  │
│                                                                 │
│  Client Request (with executionId)                              │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.checkJob()                                   │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.checkJob()                                      │
│       │                                                         │
│       ▼                                                         │
│  JobOrchestrator.checkJobStatus()  ──────► Query orchestrator   │
│       │                                                         │
│       ▼                                                         │
│  Return: status, progress                                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 3. COLLECT Stage (Raw Data)                                     │
│                                                                 │
│  Client Request (with executionId)                              │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.collectJobResults()                          │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.collectJobResults()                             │
│       │                                                         │
│       ▼                                                         │
│  JobOrchestrator.getJobExecution()  ──────► Get raw output      │
│       │                                                         │
│       ▼                                                         │
│  Return: Map<String, Object> (RAW DATA - no transformation)     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 4. RESULT Stage (Mapped Data)                                   │
│                                                                 │
│  Client Request (with executionId, targetDtoClass)              │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.getJobResult()                               │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.getJobResult()                                  │
│       │                                                         │
│       ├──► 1. Call collectJobResults() ──► Get raw data         │
│       │                                                         │
│       ├──► 2. JobResultMapperRegistry.getMapper(targetClass)    │
│       │                                                         │
│       ├──► 3. mapper.mapToTarget(rawData) ──► MapStruct         │
│       │                                                         │
│       └──► 4. Return: Mapped DTO (TRANSFORMED DATA)             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Design Patterns

### 1. Port/Adapter (Hexagonal Architecture)

**Purpose:** Decouple business logic from infrastructure

**Implementation:**
- `JobOrchestrator` is a port (interface) provided by this library
- Adapters for Apache Airflow, AWS Step Functions, or custom orchestrators must be implemented in your application
- Easy to add custom adapters for other orchestrators

### 2. Strategy Pattern

**Purpose:** Pluggable orchestrator implementations

**Implementation:**
- Different orchestrator strategies (Apache Airflow, AWS Step Functions, Custom)
- Selected via configuration property
- Runtime strategy selection

### 3. Registry Pattern

**Purpose:** Manage and discover mappers

**Implementation:**
- `JobResultMapperRegistry` auto-discovers all mappers
- Type-safe lookup by target class
- Reflection-based generic type extraction

### 4. Builder Pattern

**Purpose:** Fluent API for complex objects

**Implementation:**
- All request/response models use Lombok `@Builder`
- Readable, maintainable code
- Immutable objects

### 5. Template Method Pattern

**Purpose:** Define algorithm skeleton

**Implementation:**
- Job lifecycle stages define the template
- Implementations fill in specific steps
- Consistent flow across all services

### 6. Bridge Pattern

**Purpose:** Decouple abstraction from implementation

**Implementation:**
- `EventGateway` bridges orchestration events to EDA
- Allows independent evolution of both sides
- Transparent integration

### 7. Chain of Responsibility Pattern

**Purpose:** Enrichment fallback chains

**Implementation:**
- `@EnricherFallback` annotation defines fallback provider
- `FallbackEnrichmentExecutor` traverses the chain recursively
- Configurable strategy (on-error, on-empty, or both)
- Depth limiting prevents infinite chains

### 8. Pipe and Filter Pattern

**Purpose:** Data transformation pipelines

**Implementation:**
- `DataTransformer<S, T>` functional interface
- `TransformationChain` composes transformers via `then()`
- Each transformer is a filter that transforms data independently
- Reactive execution via `Mono<T>`

### 9. Observer Pattern (Event-Driven Quality)

**Purpose:** Quality gate event publishing

**Implementation:**
- `DataQualityEngine` publishes `DataQualityEvent` on evaluation
- `InMemoryLineageTracker` publishes `LineageEvent` on record
- Spring's `ApplicationEventPublisher` for decoupled notifications

---

## Component Interactions

### Sequence Diagram: Complete Job Flow

```
Client          Controller      Service         Orchestrator    Mapper          EDA
  │                 │              │                 │            │              │
  │─START──────────>│              │                 │            │              │
  │                 │───startJob──>│                 │            │              │
  │                 │              │──startJob──────>│            │              │
  │                 │              │<─execution──────│            │              │
  │                 │              │─────────────────────────────────publish────>│
  │                 │<─response────│                 │            │              │
  │<────────────────│              │                 │            │              │
  │                 │              │                 │            │              │
  │─CHECK──────────>│              │                 │            │              │
  │                 │──checkJob───>│                 │            │              │
  │                 │              │──checkStatus───>│            │              │
  │                 │              │<─status─────────│            │              │
  │                 │<─response────│                 │            │              │
  │<────────────────│              │                 │            │              │
  │                 │              │                 │            │              │
  │─COLLECT────────>│              │                 │            │              │
  │                 │──collect────>│                 │            │              │
  │                 │              │──getExecution──>│            │              │
  │                 │              │<─rawData────────│            │              │
  │                 │<─rawData─────│                 │            │              │
  │<────────────────│              │                 │            │              │
  │                 │              │                 │            │              │
  │─RESULT─────────>│              │                 │            │              │
  │                 │───getResult─>│                 │            │              │
  │                 │              │──collect───────>│            │              │
  │                 │              │<─rawData────────│            │              │
  │                 │              │──getMapper──────────────────>│              │
  │                 │              │<─mapper──────────────────────│              │
  │                 │              │──mapToTarget────────────────>│              │
  │                 │              │<─mappedDTO───────────────────│              │
  │                 │<─mappedDTO───│                 │            │              │
  │<────────────────│              │                 │            │              │
```

---

## Summary

The `fireflyframework-starter-data` architecture provides:

- **Clean Architecture** - Hexagonal design with clear boundaries
- **Flexibility** - Pluggable adapters for different platforms
- **Dual Job Types** - Asynchronous (multi-stage) and Synchronous (single-stage) jobs
- **Testability** - Dependency inversion enables easy mocking
- **Scalability** - Reactive programming and CQRS support
- **Observability** - Built-in event publishing and tracing
- **Reliability** - SAGA pattern for distributed transactions
- **Data Quality** - Rule-based validation with configurable strategies
- **Data Lineage** - Provenance tracking for all data operations
- **Transformation Pipelines** - Composable post-enrichment processing
- **Fallback Chains** - Automatic provider failover
- **GenAI Bridge** - Integration with fireflyframework-genai

For more details, see:
- [Job Lifecycle](../data-jobs/guide.md#job-lifecycle-async) - Detailed stage documentation for async jobs
- [Synchronous Jobs](../data-jobs/guide.md#quick-start-sync) - Complete guide for synchronous jobs
- [Configuration](configuration.md) - Configuration options
- [Examples](examples.md) - Real-world usage patterns

