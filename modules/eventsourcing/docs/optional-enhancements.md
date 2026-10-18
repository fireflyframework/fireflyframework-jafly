# Optional Enhancements

All features described in this document are **disabled by default** or activate automatically when their dependencies are on the classpath. None of them are required for basic event sourcing.

## Circuit Breakers (Resilience4j)

Circuit breakers prevent cascading failures by halting calls to a struggling subsystem and allowing it to recover.

### Activation

Requires Resilience4j on the classpath **and** an explicit opt-in:

```yaml
firefly:
  eventsourcing:
    resilience:
      circuit-breaker:
        enabled: true   # default: false
```

The auto-configuration class is `CircuitBreakerAutoConfiguration`. It uses `@ConditionalOnClass(CircuitBreaker.class)` and `@ConditionalOnProperty(..., matchIfMissing = false)`, so it does nothing unless both conditions are met.

### Three Named Circuit Breakers

The library creates three circuit breakers, each tuned for its workload:

| Name | Bean Name | Failure Threshold | Wait Duration | Sliding Window | Min Calls |
|------|-----------|-------------------|---------------|----------------|-----------|
| `eventStore` | `eventStoreCircuitBreaker` | 50% | 60s | COUNT_BASED (100) | 20 |
| `outbox` | `outboxCircuitBreaker` | 60% | 30s | TIME_BASED (120s) | 10 |
| `projection` | `projectionCircuitBreaker` | 70% | 45s | TIME_BASED (300s) | 10 |

The event store breaker is the strictest because it protects the critical write path. The projection breaker is the most tolerant because projections are eventually consistent and brief failures are acceptable.

All three breakers ignore `IllegalArgumentException` and `IllegalStateException` (validation errors that are not infrastructure failures).

### Event Store Circuit Breaker Details

```java
CircuitBreakerConfig.custom()
        .failureRateThreshold(50)
        .slowCallRateThreshold(50)
        .slowCallDurationThreshold(Duration.ofSeconds(5))
        .waitDurationInOpenState(Duration.ofSeconds(60))
        .permittedNumberOfCallsInHalfOpenState(10)
        .minimumNumberOfCalls(20)
        .slidingWindowType(SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(100)
        .recordExceptions(Exception.class)
        .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
        .build();
```

Each breaker logs state transitions at `WARN` level and errors at `ERROR` level via its event publisher.

### Dependency

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <optional>true</optional>
</dependency>
```

## Metrics (Micrometer)

`EventStoreMetrics` extends `FireflyMetricsSupport` (from `fireflyframework-observability`) and registers the following metrics when a `MeterRegistry` is on the classpath.

### Metrics Reference

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `firefly.eventsourcing.operations.duration` | Timer | `operation` = `append`, `load`, `query` | Duration of event store operations |
| `firefly.eventsourcing.events.appended` | Counter | | Total events appended |
| `firefly.eventsourcing.events.loaded` | Counter | | Total events loaded |
| `firefly.eventsourcing.errors` | Counter | `type` | Errors by category |
| `firefly.eventsourcing.concurrency.conflicts` | Counter | | Optimistic concurrency conflicts |
| `firefly.eventsourcing.connection.pool.active` | Gauge | | Active R2DBC connections |
| `firefly.eventsourcing.batch.size` | DistributionSummary | | Batch sizes for append operations |
| `firefly.eventsourcing.aggregates.total` | Gauge | | Total distinct aggregates |
| `firefly.eventsourcing.events.total` | Gauge | | Total events in the store |

### Activation

Metrics are enabled by default when Spring Boot Actuator and Micrometer are on the classpath:

```yaml
firefly:
  eventsourcing:
    performance:
      metrics-enabled: true   # default: true
```

The auto-configuration class is `EventSourcingMetricsAutoConfiguration` (`@ConditionalOnClass(MeterRegistry.class)`).

### PerformanceSummary

`EventStoreMetrics` provides a `getPerformanceSummary()` method that returns a `PerformanceSummary` object with all current metric values. This is useful for custom monitoring dashboards:

```java
EventStoreMetrics.PerformanceSummary summary = eventStoreMetrics.getPerformanceSummary();
log.info("Average append time: {}ms, events appended: {}, conflicts: {}",
        summary.getAverageAppendTime(),
        summary.getEventsAppended(),
        summary.getConcurrencyConflicts());
```

## Health Indicators

When Spring Boot Actuator is on the classpath, the library registers four health indicators under `/actuator/health`.

### Health Indicator Reference

| Indicator | Bean | Condition | Checks |
|-----------|------|-----------|--------|
| `EventStoreHealthIndicator` | `eventStoreHealthIndicator` | `EventStore` bean exists | Event store connectivity |
| `OutboxHealthIndicator` | `outboxHealthIndicator` | `EventOutboxService` bean exists | Outbox processing status |
| `SnapshotStoreHealthIndicator` | `snapshotStoreHealthIndicator` | `SnapshotStore` bean exists | Snapshot store connectivity |
| `ProjectionHealthIndicator` | `projectionHealthIndicator` | `ProjectionService<?>` beans exist | Projection lag and status |

### Activation

Health indicators are enabled by default when Actuator is on the classpath:

```yaml
firefly:
  eventsourcing:
    performance:
      health-checks-enabled: true   # default: true
```

The auto-configuration class is `EventSourcingHealthAutoConfiguration` (`@ConditionalOnClass(name = "org.springframework.boot.actuator.health.HealthIndicator")`).

### Projection Health Configuration

The projection health indicator uses additional properties:

```yaml
firefly:
  eventsourcing:
    projection:
      health-check:
        timeout: 5s
        max-acceptable-lag: 1000       # events behind before "unhealthy"
        include-details: true
        fail-on-unhealthy-projection: true
```

## Structured Logging

`EventSourcingLoggingContext` is a utility class that manages SLF4J MDC (Mapped Diagnostic Context) values for event sourcing operations.

### 16 MDC Keys

| Key | Type | Description |
|-----|------|-------------|
| `correlationId` | `String` | Distributed tracing correlation ID |
| `causationId` | `String` | Event causation chain |
| `aggregateId` | `String` | UUID of the aggregate |
| `aggregateType` | `String` | Aggregate type name |
| `eventType` | `String` | Event type identifier |
| `tenantId` | `String` | Multi-tenancy tenant ID |
| `userId` | `String` | User who triggered the operation |
| `operation` | `String` | Operation name (e.g., `deposit`, `withdraw`) |
| `duration` | `String` | Operation duration in milliseconds |
| `version` | `String` | Aggregate version |
| `globalSequence` | `String` | Global event sequence number |
| `outboxId` | `String` | UUID of the outbox entry |
| `status` | `String` | Operation or entry status |
| `retryCount` | `String` | Current retry attempt |
| `priority` | `String` | Outbox entry priority |
| `destination` | `String` | Event publishing destination |

### Usage

Set context before operations:

```java
EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
EventSourcingLoggingContext.setUserId(userId);
EventSourcingLoggingContext.setOperation("deposit");

log.info("Processing deposit");
// Log output includes: aggregateId=..., aggregateType=AccountLedger, userId=..., operation=deposit
```

Use scoped execution to automatically clean up context:

```java
EventSourcingLoggingContext.withAggregateContext(accountId, "AccountLedger", () -> {
    log.info("All logs here include aggregate context");
});
// MDC is cleared after the block
```

### Reactive Context Propagation

In reactive pipelines, MDC does not propagate across thread boundaries by default. Use `withMdcContext` to bridge MDC values into the Reactor Context:

```java
Mono<Void> result = EventSourcingLoggingContext.withMdcContext(
        eventStore.appendEvents(accountId, "AccountLedger", events, expectedVersion)
                .then()
);
```

With `Hooks.enableAutomaticContextPropagation()` enabled (provided by `fireflyframework-observability`), this writes the current MDC values into the Reactor Context. The automatic propagation mechanism then restores them on each signal.

## OpenTelemetry Tracing (Deprecated)

The `OpenTelemetryConfiguration` class in `org.fireflyframework.eventsourcing.tracing` is deprecated since version `26.02.05` and will be removed in a future release. The class is intentionally empty.

Distributed tracing is now provided by the separate `fireflyframework-observability` module, which uses the Micrometer Observation API with an OpenTelemetry bridge. If you need tracing, add `fireflyframework-observability` to your dependencies instead.

## Multi-Tenancy

Multi-tenancy support provides tenant isolation for event sourcing operations using Reactor Context.

### Activation

```yaml
firefly:
  eventsourcing:
    multitenancy:
      enabled: true   # default: false
```

The auto-configuration class is `MultiTenancyAutoConfiguration` (`@ConditionalOnProperty(..., matchIfMissing = false)`).

### TenantContext

`TenantContext` is a static utility that stores the tenant ID in the Reactor Context. The default tenant is `"default"`.

| Method | Description |
|--------|-------------|
| `getCurrentTenantId()` | Returns `Mono<String>` with the current tenant ID from Reactor Context |
| `getCurrentTenantIdOrDefault()` | Blocking call, returns the tenant ID or `"default"` |
| `withTenantId(String)` | Returns a `Function<Context, Context>` for use with `.contextWrite(...)` |
| `hasTenantId()` | Returns `Mono<Boolean>` indicating whether a tenant ID is set |
| `clear()` | Returns a `Function<Context, Context>` that removes the tenant ID |
| `getDefaultTenant()` | Returns `"default"` |

### Usage

Set the tenant context using Reactor's `contextWrite`:

```java
return eventStore.appendEvents(accountId, "AccountLedger", events, expectedVersion)
        .contextWrite(TenantContext.withTenantId("tenant-abc"));
```

Read the tenant context downstream:

```java
return TenantContext.getCurrentTenantId()
        .flatMap(tenantId -> {
            log.info("Operating as tenant: {}", tenantId);
            return eventStore.loadEventStream(accountId, "AccountLedger");
        });
```

In a web controller, set the tenant from an HTTP header:

```java
@PostMapping("/accounts")
public Mono<AccountLedger> createAccount(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody CreateAccountRequest request) {
    return accountService.openAccount(request)
            .contextWrite(TenantContext.withTenantId(tenantId));
}
```

## Event Upcasting

Event upcasting transforms old event versions to new versions during deserialization. This handles event schema evolution without modifying the immutable event store.

### EventUpcaster Interface

```java
public interface EventUpcaster {
    boolean canUpcast(String eventType, int eventVersion);
    Event upcast(Event event);
    default int getTargetVersion() { return 2; }
    default int getPriority() { return 0; }
}
```

| Method | Description |
|--------|-------------|
| `canUpcast(eventType, eventVersion)` | Returns `true` if this upcaster handles the given event type and version |
| `upcast(event)` | Transforms the event to the target version |
| `getTargetVersion()` | The version this upcaster produces (default: `2`) |
| `getPriority()` | Higher priority runs first (default: `0`) |

### Example

Suppose `MoneyDepositedEvent` originally had no `source` field. After adding it in version 2, you need an upcaster for old events:

```java
@Component
public class MoneyDepositedV1ToV2Upcaster implements EventUpcaster {

    @Override
    public boolean canUpcast(String eventType, int eventVersion) {
        return "money.deposited".equals(eventType) && eventVersion == 1;
    }

    @Override
    public Event upcast(Event event) {
        MoneyDepositedEvent v1 = (MoneyDepositedEvent) event;
        return MoneyDepositedEvent.builder()
                .aggregateId(v1.getAggregateId())
                .amount(v1.getAmount())
                .reference(v1.getReference())
                .depositedBy(v1.getDepositedBy())
                .source("UNKNOWN")  // default for old events
                .build();
    }

    @Override
    public int getTargetVersion() {
        return 2;
    }
}
```

Register the upcaster as a Spring `@Component`. The `EventUpcastingService` (created by `EventSourcingAutoConfiguration`) discovers all `EventUpcaster` beans and applies them in priority order during event deserialization. Multiple upcasters can be chained (V1 to V2 to V3).

## Summary of Activation Conditions

| Enhancement | Property | Default | Classpath Requirement |
|-------------|----------|---------|----------------------|
| Circuit Breakers | `firefly.eventsourcing.resilience.circuit-breaker.enabled` | `false` | Resilience4j `CircuitBreaker` |
| Metrics | `firefly.eventsourcing.performance.metrics-enabled` | `true` | Micrometer `MeterRegistry` |
| Health Indicators | `firefly.eventsourcing.performance.health-checks-enabled` | `true` | Spring Boot Actuator `HealthIndicator` |
| Multi-Tenancy | `firefly.eventsourcing.multitenancy.enabled` | `false` | None |
| Event Upcasting | Automatic | Always on | None |
| OpenTelemetry Tracing | Deprecated | N/A | Use `fireflyframework-observability` instead |

## Next Steps

- [Configuration](configuration.md) -- full property reference for all enhancements
- [Architecture](architecture.md) -- understand the auto-configuration chain
- [API Reference](api-reference.md) -- detailed method signatures
- [Testing](testing.md) -- testing strategies including circuit breaker behavior
