# Configuration Reference

All configuration is under the `firefly.eventsourcing` prefix. Properties are bound via Spring Boot's `@ConfigurationProperties`.

## EventSourcingProperties

Prefix: `firefly.eventsourcing`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Master switch for event sourcing auto-configuration |
| `event-scan-packages` | `String` | `"org.fireflyframework"` | Comma-separated packages for `EventTypeRegistry` classpath scanning |

### Store Properties

Prefix: `firefly.eventsourcing.store`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | `String` | `"r2dbc"` | Event store implementation type |
| `batch-size` | `int` | `100` | Batch size for event operations |
| `connection-timeout` | `Duration` | `30s` | Connection timeout |
| `query-timeout` | `Duration` | `30s` | Query timeout |
| `validate-schemas` | `boolean` | `true` | Validate event schemas |
| `max-events-per-load` | `int` | `1000` | Maximum events to load at once |
| `properties` | `Map<String, Object>` | `{}` | Store-specific properties |

### Snapshot Properties

Prefix: `firefly.eventsourcing.snapshot`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enable snapshotting |
| `threshold` | `int` | `50` | Event count threshold for snapshot creation |
| `check-interval` | `Duration` | `5m` | How often to check for snapshot opportunities |
| `keep-count` | `int` | `3` | Snapshots to keep per aggregate |
| `max-age` | `Duration` | `30d` | Maximum snapshot age before cleanup |
| `compression` | `boolean` | `true` | Enable snapshot compression |
| `store-type` | `String` | `"same"` | Snapshot store type |
| `caching` | `boolean` | `true` | Enable snapshot caching |
| `cache-ttl` | `Duration` | `1h` | Cache time-to-live |

### Publisher Properties

Prefix: `firefly.eventsourcing.publisher`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enable event publishing |
| `type` | `PublisherType` | `AUTO` | Publisher type (AUTO, KAFKA, RABBITMQ, etc.) |
| `destination-prefix` | `String` | `"events"` | Prefix for published event destinations |
| `destination-mappings` | `Map<String, String>` | `{}` | Custom destination per event type |
| `async` | `boolean` | `true` | Publish events asynchronously |
| `batch-size` | `int` | `10` | Batch size for publishing |
| `publish-timeout` | `Duration` | `10s` | Publishing timeout |
| `continue-on-failure` | `boolean` | `true` | Continue on individual publish failures |

#### Publisher Retry

Prefix: `firefly.eventsourcing.publisher.retry`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enable retry on publish failure |
| `max-attempts` | `int` | `3` | Maximum retry attempts |
| `initial-delay` | `Duration` | `1s` | Initial retry delay |
| `max-delay` | `Duration` | `10s` | Maximum retry delay |
| `backoff-multiplier` | `double` | `2.0` | Backoff multiplier |

### Performance Properties

Prefix: `firefly.eventsourcing.performance`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `thread-pool-size` | `int` | `Runtime.availableProcessors()` | Thread pool size |
| `buffer-size` | `int` | `1000` | Reactive stream buffer size |
| `metrics-enabled` | `boolean` | `true` | Enable metrics collection |
| `health-checks-enabled` | `boolean` | `true` | Enable health checks |
| `statistics-interval` | `Duration` | `1m` | Statistics collection interval |
| `tracing-enabled` | `boolean` | `true` | Enable distributed tracing |

#### Circuit Breaker

Prefix: `firefly.eventsourcing.performance.circuit-breaker`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Enable circuit breaker (off by default) |
| `failure-rate-threshold` | `float` | `50.0` | Failure rate percentage to open circuit |
| `minimum-number-of-calls` | `int` | `10` | Minimum calls before evaluating |
| `sliding-window-size` | `Duration` | `60s` | Time window for rate calculation |
| `wait-duration-in-open-state` | `Duration` | `30s` | Wait before half-open |

## EventSourcingProjectionProperties

Prefix: `firefly.eventsourcing.projection`

### Batch Processing

Prefix: `firefly.eventsourcing.projection.batch-processing`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `default-batch-size` | `int` | `100` | Default batch size |
| `default-interval` | `Duration` | `5s` | Default processing interval |
| `max-batch-size` | `int` | `1000` | Maximum batch size |
| `min-interval` | `Duration` | `100ms` | Minimum processing interval |

### Health Check

Prefix: `firefly.eventsourcing.projection.health-check`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `timeout` | `Duration` | `5s` | Health check timeout |
| `max-acceptable-lag` | `long` | `1000` | Max lag for healthy status |
| `include-details` | `boolean` | `true` | Include details in health response |
| `fail-on-unhealthy-projection` | `boolean` | `true` | Fail overall health if any projection unhealthy |

### Retry

Prefix: `firefly.eventsourcing.projection.retry`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `default-max-attempts` | `int` | `3` | Max retry attempts |
| `default-delay` | `Duration` | `1s` | Retry delay |
| `max-delay` | `Duration` | `5m` | Max retry delay |
| `backoff-multiplier` | `double` | `2.0` | Backoff multiplier |

### Metrics

Prefix: `firefly.eventsourcing.projection.metrics`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enable metrics |
| `include-projection-tags` | `boolean` | `true` | Include projection name tags |
| `track-event-processing-time` | `boolean` | `true` | Track processing time |
| `percentiles` | `double[]` | `[0.5, 0.95, 0.99]` | Tracked percentiles |
| `enable-export` | `boolean` | `true` | Export metrics |

## Complete YAML Example

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/myapp
    username: postgres
    password: postgres
  flyway:
    enabled: true
    url: jdbc:postgresql://localhost:5432/myapp
    user: postgres
    password: postgres
    locations: classpath:db/migration

firefly:
  eventsourcing:
    enabled: true
    event-scan-packages: "com.example.myapp,org.fireflyframework"

    store:
      type: r2dbc
      batch-size: 100
      connection-timeout: 30s
      query-timeout: 30s
      validate-schemas: true
      max-events-per-load: 1000

    snapshot:
      enabled: true
      threshold: 50
      check-interval: 5m
      keep-count: 3
      max-age: 30d
      compression: true
      store-type: same
      caching: true
      cache-ttl: 1h

    publisher:
      enabled: true
      type: AUTO
      destination-prefix: events
      async: true
      batch-size: 10
      publish-timeout: 10s
      continue-on-failure: true
      retry:
        enabled: true
        max-attempts: 3
        initial-delay: 1s
        max-delay: 10s
        backoff-multiplier: 2.0

    performance:
      thread-pool-size: 8
      buffer-size: 1000
      metrics-enabled: true
      health-checks-enabled: true
      statistics-interval: 1m
      tracing-enabled: true
      circuit-breaker:
        enabled: false
        failure-rate-threshold: 50.0
        minimum-number-of-calls: 10
        sliding-window-size: 60s
        wait-duration-in-open-state: 30s

    projection:
      batch-processing:
        default-batch-size: 100
        default-interval: 5s
        max-batch-size: 1000
        min-interval: 100ms
      health-check:
        timeout: 5s
        max-acceptable-lag: 1000
        include-details: true
        fail-on-unhealthy-projection: true
      retry:
        default-max-attempts: 3
        default-delay: 1s
        max-delay: 5m
        backoff-multiplier: 2.0
      metrics:
        enabled: true
        include-projection-tags: true
        track-event-processing-time: true
        percentiles: 0.5,0.95,0.99
        enable-export: true

    # Multi-tenancy (off by default)
    multitenancy:
      enabled: false

    # Resilience4j circuit breakers (off by default)
    resilience:
      circuit-breaker:
        enabled: false

    # Outbox processor (off by default)
    outbox:
      processor:
        enabled: false
```

## Auto-Configuration Conditions

| Configuration Class | Activation Condition |
|-------------------|---------------------|
| `EventSourcingAutoConfiguration` | `firefly.eventsourcing.enabled=true` (default) |
| `R2dbcBeansAutoConfiguration` | `ConnectionFactory` and `R2dbcEntityTemplate` on classpath |
| `EventStoreAutoConfiguration` | `firefly.eventsourcing.store.enabled=true` (default), R2DBC beans present |
| `SnapshotAutoConfiguration` | `firefly.eventsourcing.snapshot.enabled=true` (default) |
| `EventSourcingJacksonConfiguration` | No existing `ObjectMapper` bean |
| `EventSourcingHealthAutoConfiguration` | Spring Actuator `HealthIndicator` on classpath, `firefly.eventsourcing.performance.health-checks-enabled=true` (default) |
| `EventSourcingMetricsAutoConfiguration` | Micrometer `MeterRegistry` on classpath, `firefly.eventsourcing.performance.metrics-enabled=true` (default) |
| `EventSourcingProjectionAutoConfiguration` | `ProjectionService` and `MeterRegistry` on classpath |
| `CircuitBreakerAutoConfiguration` | Resilience4j `CircuitBreaker` on classpath, `firefly.eventsourcing.resilience.circuit-breaker.enabled=true` (default: **false**) |
| `MultiTenancyAutoConfiguration` | `firefly.eventsourcing.multitenancy.enabled=true` (default: **false**) |
