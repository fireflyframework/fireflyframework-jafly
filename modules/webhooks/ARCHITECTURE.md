# Architecture Documentation

## Table of Contents

- [Overview](#overview)
- [Architectural Patterns](#architectural-patterns)
- [System Architecture](#system-architecture)
- [Module Architecture](#module-architecture)
- [Data Flow](#data-flow)
- [Design Decisions](#design-decisions)
- [Scalability & Performance](#scalability--performance)
- [Security](#security)
- [Error Handling & Resilience](#error-handling--resilience)

## Overview

The Firefly Framework Webhooks Library is built using a **microservices-oriented architecture** with clear separation between webhook ingestion (producer) and webhook processing (consumer). The platform follows **reactive programming principles** using Spring WebFlux and Project Reactor for non-blocking, high-throughput operations.

### Core Architectural Principles

1. **Separation of Concerns**: Ingestion logic is completely decoupled from business logic
2. **Reactive Programming**: Non-blocking I/O throughout the stack
3. **Event-Driven Architecture**: Asynchronous communication via message queues
4. **Hexagonal Architecture**: Ports and adapters pattern in the processor module
5. **Idempotency**: Exactly-once processing semantics
6. **Scalability**: Horizontal scaling for both ingestion and processing

## Architectural Patterns

### 1. Producer-Consumer Pattern

The platform implements a classic producer-consumer pattern:

```
┌──────────────────┐         ┌─────────────┐         ┌──────────────────┐
│  HTTP Webhooks   │────────>│   Kafka     │────────>│  Worker Apps     │
│   (Producers)    │         │   Topics    │         │  (Consumers)     │
└──────────────────┘         └─────────────┘         └──────────────────┘
```

**Benefits**:
- **Decoupling**: Producers and consumers evolve independently
- **Buffering**: Kafka acts as a buffer during traffic spikes
- **Scalability**: Scale producers and consumers independently
- **Reliability**: Messages are persisted in Kafka

### 2. Hexagonal Architecture (Ports & Adapters)

The processor module follows hexagonal architecture:

```
┌─────────────────────────────────────────────────────┐
│                  Application Core                   │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │         WebhookProcessor (Port)             │    │
│  │  - Business logic interface                 │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
└─────────────────────────────────────────────────────┘
         ▲                                    ▲
         │                                    │
┌────────┴────────┐                  ┌────────┴────────┐
│  Input Adapter  │                  │ Output Adapter  │
│                 │                  │                 │
│  Kafka Consumer │                  │ Redis Cache     │
│  (EventListener)│                  │ (Idempotency)   │
└─────────────────┘                  └─────────────────┘
```

**Benefits**:
- **Testability**: Core logic can be tested without infrastructure
- **Flexibility**: Swap adapters without changing core logic
- **Maintainability**: Clear boundaries between layers

### 3. Event-Driven Architecture

All communication between components is event-driven:

```
HTTP Request → Domain Event → Kafka Event → Processing Event → Business Logic
```

**Benefits**:
- **Asynchronous Processing**: Non-blocking operations
- **Loose Coupling**: Components communicate via events
- **Audit Trail**: All events are logged and traceable
- **Replay Capability**: Events can be replayed for recovery

### 4. CQRS (Command Query Responsibility Segregation)

The platform separates write operations (commands) from read operations (queries):

- **Commands**: Webhook ingestion (write to Kafka)
- **Queries**: Health checks, status queries (read from cache/state)

**Benefits**:
- **Performance**: Optimize reads and writes independently
- **Scalability**: Scale read and write paths separately
- **Simplicity**: Clear separation of concerns

## System Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          External Webhook Providers                      │
│                    (Stripe, PayPal, GitHub, Custom, etc.)                │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ HTTPS POST
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Load Balancer / API Gateway                      │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    Webhook Platform (Multiple Instances)                 │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  WebhookController                                                 │  │
│  │  - Receives HTTP POST                                              │  │
│  │  - Extracts headers, payload, metadata                             │  │
│  │  - Returns 200 OK immediately                                      │  │
│  └────────────────────────┬───────────────────────────────────────────┘  │
│                           │                                              │
│  ┌────────────────────────▼───────────────────────────────────────────┐  │
│  │  WebhookProcessingService                                          │  │
│  │  - Maps DTO to Domain Event                                        │  │
│  │  - Determines destination topic                                    │  │
│  └────────────────────────┬───────────────────────────────────────────┘  │
│                           │                                              │
│  ┌────────────────────────▼───────────────────────────────────────────┐  │
│  │  EventPublisherFactory (lib-common-eda)                            │  │
│  │  - Publishes to Kafka/RabbitMQ                                     │  │
│  │  - Sets message headers                                            │  │
│  └────────────────────────┬───────────────────────────────────────────┘  │
└───────────────────────────┼──────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Kafka Cluster (3+ brokers)                       │
│  Topics: stripe, paypal, github, custom-provider, etc.                   │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    Worker Applications (Multiple Instances)              │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  AbstractWebhookEventListener                                      │  │
│  │  - Consumes from Kafka                                             │  │
│  │  - Checks idempotency (Redis)                                      │  │
│  │  - Validates signature                                             │  │
│  └────────────────────────┬───────────────────────────────────────────┘  │
│                           │                                              │
│  ┌────────────────────────▼───────────────────────────────────────────┐  │
│  │  WebhookProcessor (Implementation)                                 │  │
│  │  - Executes business logic                                         │  │
│  │  - Calls external APIs                                             │  │
│  │  - Updates database                                                │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Redis Cluster (Idempotency)                      │
│  Keys: webhook:processing:{eventId}, webhook:processed:{eventId}         │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

#### Webhook Platform (Producer)
- **Responsibility**: Receive webhooks and publish to Kafka
- **Scaling**: Horizontal (stateless)
- **Technology**: Spring Boot WebFlux, Netty
- **Performance**: ~10,000 requests/second per instance

#### Kafka Cluster
- **Responsibility**: Message broker and event log
- **Scaling**: Horizontal (add brokers and partitions)
- **Technology**: Apache Kafka 3.6.1
- **Performance**: Millions of messages/second

#### Worker Applications (Consumers)
- **Responsibility**: Process webhooks with business logic
- **Scaling**: Horizontal (consumer groups)
- **Technology**: Spring Boot, Spring Kafka
- **Performance**: Depends on business logic complexity

#### Redis Cluster
- **Responsibility**: Distributed idempotency and caching
- **Scaling**: Horizontal (Redis Cluster mode)
- **Technology**: Redis 7+
- **Performance**: Sub-millisecond latency

## Module Architecture

### Module Dependency Graph

```
┌──────────────────────────────────────────────────────────────┐
│                    interfaces (DTOs)                         │
│  - WebhookEventDTO                                           │
│  - WebhookResponseDTO                                        │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         │ depends on
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                    core (Business Logic)                     │
│  - WebhookProcessingService                                  │
│  - WebhookProcessingServiceImpl                              │
│  - WebhookReceivedEvent                                      │
│  - WebhookEventMapper                                        │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         │ depends on
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                    web (REST API)                            │
│  - WebhookManagementApplication                              │
│  - WebhookController                                         │
│  - HealthCheckController                                     │
│  - application.yml                                           │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                processor (Worker Framework)                  │
│  - WebhookProcessor (Port)                                   │
│  - AbstractWebhookEventListener (Adapter)                    │
│  - WebhookIdempotencyService (Port)                          │
│  - CacheBasedWebhookIdempotencyService (Adapter)             │
│  - WebhookSignatureValidator (Port)                          │
│  - WebhookProcessingContext                                  │
│  - WebhookProcessingResult                                   │
└──────────────────────────────────────────────────────────────┘
```

### Module Details

#### interfaces Module
**Purpose**: Shared contracts and DTOs

**Design Pattern**: Data Transfer Object (DTO)

**Key Classes**:
- `WebhookEventDTO`: Immutable DTO with all webhook data (eventId, providerName, payload, headers, etc.)
- `WebhookResponseDTO`: Enhanced HTTP response DTO with:
  - Event tracking (eventId, status, message)
  - Timestamps (receivedAt, processedAt)
  - Payload echo (receivedPayload)
  - Processing metadata (destination, sourceIp, payloadSize, headerCount)

**Dependencies**: None (pure POJOs with Jackson annotations)

**Design Decisions**:
- Immutable objects for thread safety
- Jackson annotations for JSON serialization
- Lombok for boilerplate reduction
- Validation annotations for input validation

#### core Module
**Purpose**: Business logic, services, configuration, and infrastructure

**Design Patterns**:
- Domain-Driven Design (DDD)
- Decorator Pattern (Resilience)
- Strategy Pattern (Validation)

**Package Structure**:
```
org.fireflyframework.webhooks.core/
├── config/                    # Configuration classes
│   ├── ResilienceConfig       # Resilience4j configuration
│   └── WebhookSecurityProperties  # Security properties
├── domain/events/             # Domain events
│   └── WebhookReceivedEvent   # Webhook received event
├── filter/                    # Web filters
│   └── TracingWebFilter       # Distributed tracing
├── health/                    # Health indicators
│   └── WebhookCircuitBreakerHealthIndicator
├── idempotency/              # (Removed - now using lib-common-web)
│   # HTTP-level idempotency moved to lib-common-web IdempotencyWebFilter
├── mappers/                   # MapStruct mappers
│   └── WebhookEventMapper     # DTO ↔ Event mapping
├── metrics/                   # Metrics services
│   └── WebhookMetricsService  # Custom metrics
├── ratelimit/                # Rate limiting
│   └── WebhookRateLimitService
├── resilience/               # Resilience patterns
│   └── ResilientWebhookProcessingService
├── services/                  # Business services
│   ├── WebhookProcessingService
│   └── impl/WebhookProcessingServiceImpl
└── validation/               # Validation services
    └── WebhookValidator       # Request validation
```

**Key Features**:
- **Resilience Patterns**: Circuit breaker, rate limiter, timeout, bulkhead
- **Security**: Payload validation, rate limiting, IP whitelisting
- **Observability**: Custom metrics, distributed tracing, health indicators
- **HTTP Idempotency**: Handled by lib-common-web IdempotencyWebFilter (transparent)
- **Event Idempotency**: Worker-level via CacheBasedWebhookIdempotencyService (processor module)

**Dependencies**:
- `lib-common-eda` - Event publishing
- `lib-common-cache` - Redis/Caffeine caching
- `lib-common-core` - Core utilities
- Resilience4j - Resilience patterns
- Micrometer - Metrics
- MapStruct - DTO mapping
- Apache Commons Net - CIDR notation IP matching

**Design Decisions**:
- **Decorator Pattern**: `ResilientWebhookProcessingService` decorates `WebhookProcessingServiceImpl`
- **Environment Variables**: All properties support environment variable configuration
- **Reactive Programming**: All services return Mono/Flux for non-blocking operations
- **Separation of Concerns**: Each package has a single responsibility

#### web Module
**Purpose**: Main Spring Boot application and REST controllers **ONLY**

**Design Pattern**: MVC (Model-View-Controller)

**Key Classes**:
- `WebhookManagementApplication`: Main application class
- `controllers/WebhookController`: REST controller for webhook ingestion

**Note**: All business logic, services, configuration, and infrastructure code has been moved to the `-core` module. This module contains only the application entry point and REST controllers.

**Dependencies**:
- `fireflyframework-webhooks-core` - Core business logic
- Spring Boot WebFlux
- Spring Boot Actuator
- SpringDoc OpenAPI
- `lib-common-web`
- `lib-common-eda`
- `lib-common-cache`

**Design Decisions**:
- Reactive controllers for non-blocking I/O
- OpenAPI for API documentation
- Actuator for health checks and metrics
- Minimal business logic (delegated to core)

#### processor Module
**Purpose**: Worker framework for webhook processing

**Design Pattern**: Hexagonal Architecture (Ports & Adapters)

**Key Classes**:
- **Ports** (Interfaces):
  - `WebhookProcessor` - Business logic interface
  - `WebhookIdempotencyService` - Idempotency interface
  - `WebhookSignatureValidator` - Signature validation interface

- **Adapters** (Implementations):
  - `AbstractWebhookEventListener` - Kafka consumer adapter
  - `CacheBasedWebhookIdempotencyService` - Redis adapter

- **Models**:
  - `WebhookProcessingContext` - Context object
  - `WebhookProcessingResult` - Result object

**Dependencies**:
- `lib-common-eda` - Event consumption
- `lib-common-cache` - Caching
- Spring Kafka

**Design Decisions**:
- Abstract base class for common logic
- Template method pattern for lifecycle hooks
- Strategy pattern for signature validation
- Reactive processing with Mono/Flux

## Data Flow

### Webhook Ingestion Flow

```
1. HTTP POST /api/v1/webhook/{providerName}
   │
   ├─> WebhookController.receiveWebhook()
   │   │
   │   ├─> Extract headers, payload, metadata
   │   ├─> Create WebhookEventDTO
   │   │
   │   └─> WebhookProcessingService.processWebhook()
   │       │
   │       ├─> WebhookEventMapper.toDomainEvent()
   │       │   └─> Create WebhookReceivedEvent
   │       │
   │       ├─> Determine destination topic
   │       │   └─> Apply routing strategy
   │       │
   │       └─> EventPublisherFactory.publish()
   │           │
   │           ├─> Serialize to JSON
   │           ├─> Set Kafka headers
   │           └─> Send to Kafka topic
   │
   └─> Return 202 ACCEPTED with WebhookResponseDTO
       - eventId, status, message
       - receivedAt, processedAt timestamps
       - receivedPayload (echo for verification)
       - metadata (destination, sourceIp, payloadSize, etc.)
```

### Webhook Processing Flow

```
1. Kafka Consumer polls topic
   │
   ├─> AbstractWebhookEventListener.onEvent()
   │   │
   │   ├─> Deserialize JSON to WebhookReceivedEvent
   │   ├─> Create WebhookProcessingContext
   │   │
   │   ├─> beforeProcess() hook
   │   │
   │   ├─> WebhookIdempotencyService.tryAcquireProcessingLock()
   │   │   │
   │   │   ├─> Check Redis: webhook:processing:{eventId}
   │   │   ├─> If exists → Skip (already processing)
   │   │   └─> If not exists → Acquire lock (SET NX EX)
   │   │
   │   ├─> WebhookIdempotencyService.isAlreadyProcessed()
   │   │   │
   │   │   └─> Check Redis: webhook:processed:{eventId}
   │   │       ├─> If exists → Skip (already processed)
   │   │       └─> If not exists → Continue
   │   │
   │   ├─> WebhookSignatureValidator.validate()
   │   │   │
   │   │   ├─> Extract signature from headers
   │   │   ├─> Compute expected signature
   │   │   └─> Compare signatures
   │   │       ├─> If invalid → Reject
   │   │       └─> If valid → Continue
   │   │
   │   ├─> WebhookProcessor.process()
   │   │   │
   │   │   └─> Execute business logic
   │   │       ├─> Parse payload
   │   │       ├─> Call external APIs
   │   │       ├─> Update database
   │   │       └─> Return WebhookProcessingResult
   │   │
   │   ├─> WebhookIdempotencyService.markAsProcessed()
   │   │   │
   │   │   └─> Set Redis: webhook:processed:{eventId} (TTL: 24h)
   │   │
   │   ├─> WebhookIdempotencyService.releaseProcessingLock()
   │   │   │
   │   │   └─> Delete Redis: webhook:processing:{eventId}
   │   │
   │   ├─> afterProcess() hook
   │   │
   │   └─> Commit Kafka offset
   │
   └─> If error → onError() hook
       │
       ├─> WebhookIdempotencyService.recordProcessingFailure()
       │   │
       │   └─> Increment Redis: webhook:failures:{eventId}
       │
       └─> Retry or DLQ (Dead Letter Queue)

## Design Decisions

### 1. Why Reactive Programming?

**Decision**: Use Spring WebFlux and Project Reactor instead of traditional blocking I/O

**Rationale**:
- **High Concurrency**: Handle thousands of concurrent webhook requests with fewer threads
- **Non-Blocking I/O**: Kafka publishing is non-blocking, improving throughput
- **Backpressure**: Reactor provides built-in backpressure handling
- **Resource Efficiency**: Lower memory footprint compared to thread-per-request model

**Trade-offs**:
- **Complexity**: Reactive code is harder to debug and understand
- **Learning Curve**: Team needs to learn reactive programming
- **Ecosystem**: Some libraries don't support reactive patterns

**Outcome**: The benefits outweigh the costs for a high-throughput webhook platform

### 2. Why Separate Ingestion from Processing?

**Decision**: Split webhook ingestion (producer) and processing (consumer) into separate applications

**Rationale**:
- **Scalability**: Scale ingestion and processing independently based on load
- **Reliability**: Kafka provides buffering during processing delays
- **Flexibility**: Multiple consumers can process the same webhook differently
- **Fault Isolation**: Processing failures don't affect ingestion

**Trade-offs**:
- **Latency**: Additional hop through Kafka adds latency (~10-50ms)
- **Complexity**: More moving parts to deploy and monitor
- **Eventual Consistency**: Processing is asynchronous

**Outcome**: The architecture is more scalable and resilient

### 3. Why Dynamic Provider Support?

**Decision**: Use path parameter `{providerName}` instead of enum or hardcoded providers

**Rationale**:
- **Zero Deployment**: Add new providers without code changes or deployments
- **Flexibility**: Support custom/internal webhooks easily
- **Simplicity**: No need to maintain provider registry
- **Scalability**: Handle unlimited providers

**Trade-offs**:
- **Validation**: No compile-time validation of provider names
- **Documentation**: Harder to document all supported providers
- **Type Safety**: Lose type safety for provider-specific logic

**Outcome**: Flexibility and ease of use outweigh type safety concerns

### 4. Why AS-IS Payload Preservation?

**Decision**: Store webhook payloads exactly as received without transformation

**Rationale**:
- **Signature Validation**: Headers needed for downstream signature verification
- **No Data Loss**: Complete information preserved for consumers
- **Provider Flexibility**: Each provider has unique payload structures
- **Simplicity**: No complex transformations at ingestion

**Trade-offs**:
- **Storage**: Larger payloads consume more Kafka storage
- **Parsing**: Consumers must parse JSON payloads
- **Validation**: No schema validation at ingestion

**Outcome**: Preserving original data is more valuable than transformation

### 5. Why Redis for Idempotency?

**Decision**: Use Redis instead of database for idempotency tracking

**Rationale**:
- **Performance**: Sub-millisecond latency for idempotency checks
- **TTL Support**: Automatic expiration of old idempotency keys
- **Atomic Operations**: SET NX EX for distributed locking
- **Scalability**: Redis Cluster for horizontal scaling

**Trade-offs**:
- **Persistence**: Redis is not as durable as a database
- **Cost**: Additional infrastructure component
- **Complexity**: Need to manage Redis cluster

**Outcome**: Performance benefits justify the additional complexity

### 6. Why Hexagonal Architecture for Processor?

**Decision**: Use ports and adapters pattern in processor module

**Rationale**:
- **Testability**: Core logic can be tested without infrastructure
- **Flexibility**: Swap Kafka for RabbitMQ without changing core logic
- **Maintainability**: Clear boundaries between layers
- **Reusability**: Core logic can be reused across different adapters

**Trade-offs**:
- **Boilerplate**: More interfaces and abstractions
- **Complexity**: Harder for junior developers to understand
- **Indirection**: More layers to navigate

**Outcome**: Long-term maintainability justifies the upfront complexity

## Scalability & Performance

### Horizontal Scaling

#### Webhook Platform (Producer)
- **Stateless**: No session state, can scale horizontally
- **Load Balancing**: Use round-robin or least-connections
- **Auto-Scaling**: Scale based on CPU, memory, or request rate
- **Recommended**: 3+ instances for high availability

```
┌─────────────┐
│Load Balancer│
└──────┬──────┘
       │
   ┌───┴───┬───────┬───────┐
   │       │       │       │
   ▼       ▼       ▼       ▼
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ Pod1│ │ Pod2│ │ Pod3│ │ PodN│
└─────┘ └─────┘ └─────┘ └─────┘
```

#### Worker Applications (Consumer)
- **Consumer Groups**: Kafka distributes partitions across consumers
- **Partition-Based**: Each partition processed by one consumer
- **Auto-Scaling**: Scale based on consumer lag
- **Recommended**: Number of consumers ≤ number of partitions

```
Topic: stripe (6 partitions)
┌────┬────┬────┬────┬────┬────┐
│ P0 │ P1 │ P2 │ P3 │ P4 │ P5 │
└─┬──┴─┬──┴─┬──┴─┬──┴─┬──┴─┬──┘
  │    │    │    │    │    │
  ▼    ▼    ▼    ▼    ▼    ▼
┌───┐┌───┐┌───┐┌───┐┌───┐┌───┐
│C1 ││C2 ││C3 ││C4 ││C5 ││C6 │
└───┘└───┘└───┘└───┘└───┘└───┘
Consumer Group: webhook-worker
```

#### Kafka Cluster
- **Brokers**: 3+ brokers for high availability
- **Partitions**: More partitions = more parallelism
- **Replication**: Replication factor of 3 for durability
- **Recommended**: 6-12 partitions per topic

#### Redis Cluster
- **Cluster Mode**: 3+ master nodes with replicas
- **Sharding**: Data distributed across nodes
- **Replication**: Each master has 1+ replicas
- **Recommended**: 3 masters + 3 replicas

### Performance Characteristics

#### Webhook Platform
- **Throughput**: ~10,000 requests/second per instance
- **Latency**: P50: 5ms, P95: 15ms, P99: 50ms
- **Memory**: ~512MB per instance
- **CPU**: ~1 core per instance

#### Worker Applications
- **Throughput**: Depends on business logic complexity
- **Latency**: P50: 50ms, P95: 200ms, P99: 500ms
- **Memory**: ~1GB per instance
- **CPU**: ~2 cores per instance

#### Kafka
- **Throughput**: Millions of messages/second
- **Latency**: P50: 2ms, P95: 10ms, P99: 50ms
- **Storage**: Depends on retention policy (default: 7 days)

#### Redis
- **Throughput**: 100,000+ operations/second per node
- **Latency**: P50: <1ms, P95: 2ms, P99: 5ms
- **Memory**: Depends on idempotency key count

### Performance Optimization

#### Webhook Platform
1. **Connection Pooling**: Reuse Kafka producer connections
2. **Batch Publishing**: Batch multiple events (if applicable)
3. **Compression**: Use Snappy compression for Kafka messages
4. **Async Processing**: Use reactive chains, avoid blocking

#### Worker Applications
1. **Parallel Processing**: Process multiple events concurrently
2. **Batch Operations**: Batch database operations
3. **Caching**: Cache frequently accessed data
4. **Circuit Breakers**: Prevent cascading failures

#### Kafka
1. **Partitioning**: More partitions for higher parallelism
2. **Compression**: Enable compression (Snappy or LZ4)
3. **Batching**: Increase batch size for higher throughput
4. **Replication**: Balance replication factor vs. performance

#### Redis
1. **Pipelining**: Batch multiple commands
2. **Connection Pooling**: Reuse connections
3. **Cluster Mode**: Distribute load across nodes
4. **TTL**: Set appropriate TTL for idempotency keys

## Security

### Security Features

The platform implements multiple layers of security:

#### 1. Payload Size Validation

**Purpose**: Prevent DoS attacks via large payloads

**Configuration**:
```yaml
firefly:
  webhooks:
    security:
      max-payload-size: 1048576        # 1MB (default)
      validate-payload-size: true
```

**Environment Variable**:
```bash
FIREFLY_WEBHOOKS_SECURITY_MAX_PAYLOAD_SIZE=1048576
FIREFLY_WEBHOOKS_SECURITY_VALIDATE_PAYLOAD_SIZE=true
```

**Behavior**: Returns HTTP 413 Payload Too Large if exceeded

#### 2. Provider Name Validation

**Purpose**: Prevent injection attacks via malicious provider names

**Configuration**:
```yaml
firefly:
  webhooks:
    security:
      validate-provider-name: true
      provider-name-pattern: "^[a-z0-9-]+$"  # Alphanumeric and hyphens only
```

**Environment Variable**:
```bash
FIREFLY_WEBHOOKS_SECURITY_VALIDATE_PROVIDER_NAME=true
FIREFLY_WEBHOOKS_SECURITY_PROVIDER_NAME_PATTERN="^[a-z0-9-]+$"
```

**Behavior**: Returns HTTP 400 Bad Request if invalid

#### 3. IP Whitelisting

**Purpose**: Restrict access to known provider IPs

**Supported Formats**:
- **Exact IP addresses**: `54.187.174.169`
- **CIDR notation**: `192.30.252.0/22` (matches entire IP ranges)

**Implementation**:
- Uses Apache Commons Net library (`SubnetUtils`) for CIDR matching
- Validates both exact IP matches and CIDR range matches
- Logs warnings for invalid IP addresses or CIDR notations
- Configured per provider for granular control

**Configuration**:
```yaml
firefly:
  webhooks:
    security:
      enable-ip-whitelist: true
      ip-whitelist:
        stripe:
          - "54.187.174.169"      # Exact IP
          - "54.187.205.235"      # Exact IP
        github:
          - "192.30.252.0/22"     # CIDR notation (192.30.252.0 - 192.30.255.255)
          - "185.199.108.0/22"    # CIDR notation (185.199.108.0 - 185.199.111.255)
        paypal:
          - "173.0.82.0/24"       # CIDR notation (173.0.82.0 - 173.0.82.255)
```

**Environment Variable**:
```bash
FIREFLY_WEBHOOKS_SECURITY_ENABLE_IP_WHITELIST=true
# Supports both exact IPs and CIDR notation
FIREFLY_WEBHOOKS_SECURITY_IP_WHITELIST='{"stripe":["54.187.174.169","54.187.205.235"],"github":["192.30.252.0/22","185.199.108.0/22"]}'
```

**Behavior**: Returns HTTP 403 Forbidden if IP not whitelisted

**Example CIDR Ranges**:
- `/32` - Single IP (e.g., `192.168.1.1/32` = only `192.168.1.1`)
- `/24` - 256 IPs (e.g., `192.168.1.0/24` = `192.168.1.0` - `192.168.1.255`)
- `/22` - 1024 IPs (e.g., `192.30.252.0/22` = `192.30.252.0` - `192.30.255.255`)
- `/16` - 65,536 IPs (e.g., `10.0.0.0/16` = `10.0.0.0` - `10.0.255.255`)

#### 4. Rate Limiting

**Purpose**: Prevent abuse and DoS attacks

**Per-Provider Rate Limiting**:
- Default: 100 requests/second per provider
- Configurable per provider via Resilience4j

**Per-IP Rate Limiting**:
- Default: 100 requests/second per IP address
- Prevents single IP from overwhelming the system

**Behavior**: Returns HTTP 429 Too Many Requests if exceeded

#### 5. HTTP-level Idempotency

**Purpose**: Prevent duplicate processing of the same webhook

**Configuration**:
```yaml
firefly:
  webhooks:
    security:
      enable-http-idempotency: true
      http-idempotency-ttl-seconds: 86400  # 24 hours
```

**Environment Variable**:
```bash
FIREFLY_WEBHOOKS_SECURITY_ENABLE_HTTP_IDEMPOTENCY=true
FIREFLY_WEBHOOKS_SECURITY_HTTP_IDEMPOTENCY_TTL_SECONDS=86400
```

**Usage**:
```bash
curl -X POST http://localhost:8080/api/v1/webhook/stripe \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: unique-key-123" \
  -d '{"event": "payment.succeeded"}'
```

**Behavior**:
- First request: Processes webhook and caches response
- Duplicate requests (within 24h): Returns cached response (HTTP 200)

#### 6. Content-Type Validation

**Purpose**: Ensure proper content type headers

**Configuration**:
```yaml
firefly:
  webhooks:
    security:
      enable-request-validation: true
      require-content-type: true
```

**Environment Variable**:
```bash
FIREFLY_WEBHOOKS_SECURITY_ENABLE_REQUEST_VALIDATION=true
FIREFLY_WEBHOOKS_SECURITY_REQUIRE_CONTENT_TYPE=true
```

**Behavior**: Returns HTTP 400 Bad Request if Content-Type header missing

### Authentication & Authorization

#### Webhook Platform (Ingestion)
- **No Authentication**: Webhook providers don't support OAuth/JWT
- **IP Whitelisting**: Restrict access to known provider IPs (optional, configurable)
- **Rate Limiting**: Prevent abuse (per-provider and per-IP)
- **Payload Validation**: Validate payload size, provider name, content-type

#### Worker Applications (Processing)
- **Signature Validation**: Verify webhook signatures (provider-specific)
- **HMAC SHA256**: Most providers use HMAC SHA256
- **Timestamp Validation**: Reject old webhooks (prevent replay attacks)
- **Idempotency**: Prevent duplicate processing using Redis locks (7 days TTL)

### Signature Validation

Each provider has a unique signature algorithm:

#### Stripe
```java
String signature = headers.get("Stripe-Signature");
String payload = rawBody;
String secret = "whsec_...";

// Stripe uses HMAC SHA256
String expectedSignature = HmacUtils.hmacSha256Hex(secret, timestamp + "." + payload);
boolean valid = signature.contains("v1=" + expectedSignature);
```

#### GitHub
```java
String signature = headers.get("X-Hub-Signature-256");
String payload = rawBody;
String secret = "secret";

// GitHub uses HMAC SHA256
String expectedSignature = "sha256=" + HmacUtils.hmacSha256Hex(secret, payload);
boolean valid = signature.equals(expectedSignature);
```

### Data Protection

#### In Transit
- **TLS 1.2+**: All HTTP traffic uses TLS
- **Kafka TLS**: Enable TLS for Kafka (optional)
- **Redis TLS**: Enable TLS for Redis (optional)

#### At Rest
- **Kafka Encryption**: Enable encryption at rest (optional)
- **Redis Encryption**: Enable encryption at rest (optional)
- **Secrets Management**: Use Kubernetes Secrets or Vault

### Compliance

- **GDPR**: Webhook payloads may contain PII
- **PCI DSS**: Payment webhooks may contain sensitive data
- **Data Retention**: Configure Kafka retention policy
- **Audit Logging**: All webhooks are logged with correlation IDs

## Error Handling & Resilience

### Resilience Patterns Implementation

The platform implements comprehensive resilience patterns using **Resilience4j**:

#### 1. Circuit Breaker

**Purpose**: Prevents cascading failures when Kafka is down

**Configuration**:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      webhookKafkaPublisher:
        failure-rate-threshold: 50          # Open circuit if 50% of calls fail
        slow-call-rate-threshold: 50        # Open circuit if 50% of calls are slow
        slow-call-duration-threshold: 5s    # Call is slow if > 5 seconds
        wait-duration-in-open-state: 30s    # Wait 30s before trying half-open
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
```

**Implementation**:
```java
@Service
@Primary
public class ResilientWebhookProcessingService implements WebhookProcessingService {

    private final WebhookProcessingService delegate;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    @Override
    public Mono<WebhookResponseDTO> processWebhook(WebhookEventDTO webhookEvent) {
        return Mono.fromCallable(() ->
            circuitBreaker.decorateSupplier(() ->
                timeLimiter.executeFutureSupplier(() ->
                    delegate.processWebhook(webhookEvent).toFuture()
                )
            ).get()
        ).flatMap(Mono::fromFuture);
    }
}
```

**Behavior**:
- **CLOSED**: Normal operation, all requests pass through
- **OPEN**: Circuit is open, requests fail immediately (no fallback, relies on lib-common-eda)
- **HALF_OPEN**: Testing if service recovered, limited requests allowed

**Health Check**:
```json
{
  "webhookCircuitBreaker": {
    "status": "UP",
    "details": {
      "circuitBreakerName": "webhookKafkaPublisher",
      "state": "CLOSED",
      "failureRate": "0.0%",
      "slowCallRate": "0.0%"
    }
  }
}
```

#### 2. Rate Limiting

**Purpose**: Protects against abuse and DoS attacks

**Per-Provider Rate Limiting**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      webhook-provider-default:
        limit-for-period: 100        # 100 requests
        limit-refresh-period: 1s     # per second
        timeout-duration: 0s         # Fail immediately if limit exceeded
```

**Per-IP Rate Limiting**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      webhook-ip-default:
        limit-for-period: 100        # 100 requests
        limit-refresh-period: 1s     # per second
        timeout-duration: 0s
```

**Implementation**:
```java
@Service
public class WebhookRateLimitService {

    public Mono<Void> checkRateLimit(String providerName, String ipAddress) {
        return Mono.fromRunnable(() -> {
            // Check provider rate limit
            RateLimiter providerLimiter = getRateLimiter("webhook-provider-" + providerName);
            if (!providerLimiter.acquirePermission()) {
                throw new RateLimitExceededException("Provider rate limit exceeded");
            }

            // Check IP rate limit
            RateLimiter ipLimiter = getRateLimiter("webhook-ip-" + ipAddress);
            if (!ipLimiter.acquirePermission()) {
                throw new RateLimitExceededException("IP rate limit exceeded");
            }
        });
    }
}
```

**Response**: HTTP 429 Too Many Requests

#### 3. Timeout Protection

**Purpose**: Prevents hanging operations

**Configuration**:
```yaml
resilience4j:
  timelimiter:
    instances:
      webhookKafkaPublisher:
        timeout-duration: 10s        # Timeout after 10 seconds
        cancel-running-future: true  # Cancel the future on timeout
```

**Behavior**:
- If Kafka publishing takes > 10 seconds, the operation times out
- Returns HTTP 500 to webhook sender
- Webhook sender should retry

#### 4. Bulkhead Pattern

**Purpose**: Resource isolation to prevent thread pool exhaustion

**Configuration** (configured but not yet fully implemented):
```yaml
resilience4j:
  bulkhead:
    instances:
      webhookProcessing:
        max-concurrent-calls: 100    # Max 100 concurrent webhook processing calls
        max-wait-duration: 0s        # Don't wait if limit reached
```

### Error Handling Strategy

#### Webhook Platform (Ingestion)
- **Circuit Breaker**: Fails fast if Kafka is unavailable (no fallback)
- **Timeout**: Returns 500 if publishing takes > 10 seconds
- **Rate Limiting**: Returns 429 if rate limit exceeded
- **Validation Errors**: Returns 400 for invalid payloads
- **Logging**: All errors logged with correlation IDs (traceId, spanId, transactionId)

#### Worker Applications (Processing)
- **Retry Logic**: Retry transient failures with exponential backoff
- **Dead Letter Queue**: Move failed messages to DLQ after max retries
- **Circuit Breaker**: Stop processing if downstream service is down
- **Graceful Degradation**: Continue processing other events
- **Idempotency**: Prevent duplicate processing using Redis locks

### Retry Strategy (Worker Applications)

```java
public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
    return executeBusinessLogic(context)
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .filter(throwable -> isTransientError(throwable))
            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                new MaxRetriesExceededException("Max retries exceeded")));
}
```

### Idempotency

Idempotency ensures exactly-once processing:

1. **Acquire Lock**: Try to acquire processing lock in Redis
2. **Check Processed**: Check if event already processed
3. **Process**: Execute business logic
4. **Mark Processed**: Mark event as processed in Redis
5. **Release Lock**: Release processing lock

```java
public Mono<WebhookProcessingResult> processWithIdempotency(WebhookProcessingContext context) {
    return idempotencyService.tryAcquireProcessingLock(context.getEventId())
        .flatMap(acquired -> {
            if (!acquired) {
                return Mono.just(WebhookProcessingResult.skipped("Already processing"));
            }

            return idempotencyService.isAlreadyProcessed(context.getEventId())
                .flatMap(processed -> {
                    if (processed) {
                        return Mono.just(WebhookProcessingResult.skipped("Already processed"));
                    }

                    return processor.process(context)
                        .flatMap(result ->
                            idempotencyService.markAsProcessed(context.getEventId())
                                .thenReturn(result));
                })
                .doFinally(signal ->
                    idempotencyService.releaseProcessingLock(context.getEventId()).subscribe());
        });
}
```

### Monitoring & Alerting

#### Key Metrics
- **Ingestion Rate**: Webhooks received per second
- **Processing Rate**: Webhooks processed per second
- **Error Rate**: Failed webhooks per second
- **Consumer Lag**: Kafka consumer lag
- **Idempotency Hit Rate**: Duplicate webhook rate

#### Alerts
- **High Error Rate**: Alert if error rate > 5%
- **High Consumer Lag**: Alert if lag > 10,000 messages
- **Kafka Down**: Alert if Kafka is unavailable
- **Redis Down**: Alert if Redis is unavailable
- **High Latency**: Alert if P99 latency > 1 second

## Conclusion

The Firefly Framework Webhooks Library is designed for **high throughput**, **scalability**, and **reliability**. The architecture follows industry best practices including reactive programming, event-driven architecture, hexagonal architecture, and idempotency. The platform can handle millions of webhooks per day while maintaining low latency and high availability.
```
