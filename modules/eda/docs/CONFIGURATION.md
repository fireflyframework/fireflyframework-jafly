# Configuration Reference

This document provides a comprehensive reference for all configuration options available in the Firefly EDA Library.

---

## ⚠️ CRITICAL - Hexagonal Architecture Configuration Principle

**This library follows hexagonal architecture (ports and adapters) principles.**

### Configuration Namespace Rules

✅ **ALWAYS use `firefly.eda.*` properties exclusively**

❌ **NEVER use Spring-specific properties:**
- `spring.kafka.*` - Will be **IGNORED**
- `spring.rabbitmq.*` - Will be **IGNORED**
- `spring.amqp.*` - Will be **IGNORED**

### Why This Matters

The library provides **complete abstraction** from messaging platform implementations. All provider-specific configurations are managed internally by the library's auto-configuration classes. This ensures:

1. **Portability**: Switch between Kafka, RabbitMQ, or other providers without changing application code
2. **Consistency**: Unified configuration structure across all messaging platforms
3. **Maintainability**: Single source of truth for all EDA-related configuration
4. **Testability**: Easy to mock and test without provider-specific dependencies

### Correct Configuration Examples

✅ **Correct - Kafka:**
```yaml
firefly:
  eda:
    publishers:
      kafka:
        default:
          bootstrap-servers: localhost:9092
```

❌ **Incorrect - Will be IGNORED:**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092  # ❌ IGNORED!
```

✅ **Correct - RabbitMQ:**
```yaml
firefly:
  eda:
    publishers:
      rabbitmq:
        default:
          host: localhost
          port: 5672
```

❌ **Incorrect - Will be IGNORED:**
```yaml
spring:
  rabbitmq:
    host: localhost  # ❌ IGNORED!
```

---

## Table of Contents

- [Basic Configuration](#basic-configuration)
- [Publisher Configuration](#publisher-configuration)
- [Consumer Configuration](#consumer-configuration)
- [Resilience Configuration](#resilience-configuration)
- [Monitoring Configuration](#monitoring-configuration)
- [Examples](#examples)

## Basic Configuration

All configuration properties are prefixed with `firefly.eda`.

### Core Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether the EDA library is enabled |
| `default-publisher-type` | enum | `AUTO` | Default publisher type (`AUTO`, `KAFKA`, `RABBITMQ`, `APPLICATION_EVENT`, `NOOP`) |
| `default-connection-id` | string | `"default"` | Default connection ID to use when none is specified |
| `default-destination` | string | `"events"` | Default destination for events when none is specified |
| `default-serialization-format` | string | `"json"` | Default serialization format (`json`, `avro`, `protobuf`) |
| `default-timeout` | duration | `30s` | Default timeout for publish operations |

### Monitoring Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `metrics-enabled` | boolean | `true` | Whether to enable metrics collection |
| `health-enabled` | boolean | `true` | Whether to enable health checks |
| `tracing-enabled` | boolean | `true` | Whether to enable tracing integration |

## Publisher Configuration

Publishers are configured under `firefly.eda.publishers`.

### Application Event Publisher

```yaml
firefly:
  eda:
    publishers:
      application-event:
        enabled: true
        default-destination: "application-events"
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether Application Event publisher is enabled |
| `default-destination` | string | `"application-events"` | Default destination for application events |

### Kafka Publisher

```yaml
firefly:
  eda:
    publishers:
      kafka:
        default:  # Connection ID
          enabled: true
          bootstrap-servers: "localhost:9092"
          default-topic: "events"
          key-serializer: "org.apache.kafka.common.serialization.StringSerializer"
          value-serializer: "org.apache.kafka.common.serialization.StringSerializer"
          properties:
            acks: "all"
            retries: 3
            batch.size: 16384
            linger.ms: 10
            buffer.memory: 33554432
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether this Kafka connection is enabled |
| `bootstrap-servers` | string | `null` | Kafka bootstrap servers |
| `default-topic` | string | `"events"` | Default topic for events |
| `key-serializer` | string | `StringSerializer` | Kafka key serializer class |
| `value-serializer` | string | `StringSerializer` | Kafka value serializer class |
| `properties` | map | `{}` | Additional Kafka producer properties |

### RabbitMQ Publisher

```yaml
firefly:
  eda:
    publishers:
      rabbitmq:
        default:  # Connection ID
          enabled: true
          host: "localhost"
          port: 5672
          username: "guest"
          password: "guest"
          virtual-host: "/"
          default-exchange: "events"
          default-routing-key: "event"
          properties:
            connection-timeout: 60000
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether this RabbitMQ connection is enabled |
| `host` | string | `"localhost"` | RabbitMQ host |
| `port` | int | `5672` | RabbitMQ port |
| `username` | string | `"guest"` | RabbitMQ username |
| `password` | string | `"guest"` | RabbitMQ password |
| `virtual-host` | string | `"/"` | RabbitMQ virtual host |
| `default-exchange` | string | `"events"` | Default exchange for events |
| `default-routing-key` | string | `"event"` | Default routing key |
| `properties` | map | `{}` | Additional RabbitMQ connection properties |

### PostgreSQL Publisher

The PostgreSQL publisher uses an outbox table together with `pg_notify` to deliver events. Configuration lives under `firefly.eda.publishers.postgres.<connection-id>` -- never under `spring.r2dbc.*`.

```yaml
firefly:
  eda:
    publishers:
      postgres:
        default:  # Connection ID
          enabled: true
          host: "localhost"
          port: 5432
          database: "app"
          username: "app"
          password: "secret"
          schema: "public"
          outbox-table: "firefly_eda_outbox"
          default-destination: "events"
          auto-create-schema: true
          max-pool-size: 10
          properties:
            statement_timeout: "30000"
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether this PostgreSQL publisher connection is enabled |
| `host` | string | `"localhost"` | PostgreSQL host |
| `port` | int | `5432` | PostgreSQL port |
| `database` | string | `null` | Database name |
| `username` | string | `null` | Database username |
| `password` | string | `null` | Database password |
| `schema` | string | `"public"` | Schema containing the outbox table |
| `outbox-table` | string | `"firefly_eda_outbox"` | Name of the outbox table |
| `default-destination` | string | `"events"` | Default destination when none is provided to `publish()` |
| `auto-create-schema` | boolean | `true` | Provision the outbox table, index, NOTIFY function and trigger at startup |
| `max-pool-size` | int | `10` | Maximum R2DBC connection pool size used for outbox inserts |
| `properties` | map | `{}` | Additional R2DBC PostgreSQL startup options (e.g., statement timeout) |

When `auto-create-schema: true`, the publisher provisions:

- `firefly_eda_outbox` table with `id`, `destination`, `channel`, `payload (BYTEA)`, `headers (JSONB)`, `status`, `attempts`, `error_message`, and timestamp columns
- An index on `(status, created_at)` filtered to `status = 'PENDING'`
- An index on `(channel, status)`
- A `firefly_eda_notify_event()` trigger function that calls `pg_notify(NEW.channel, NEW.id::text)`
- An `AFTER INSERT` trigger on the outbox table that runs the function

## Consumer Configuration

Consumers are configured under `firefly.eda.consumer`.

### General Consumer Settings

```yaml
firefly:
  eda:
    consumer:
      enabled: false
      group-id: "firefly-eda"
      concurrency: 1
      retry:
        enabled: true
        max-attempts: 3
        initial-delay: 1s
        max-delay: 5m
        multiplier: 2.0
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether event consumers are enabled |
| `group-id` | string | `"firefly-eda"` | Default consumer group ID |
| `concurrency` | int | `1` | Consumer concurrency level (1-100) |

### Consumer Retry Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `retry.enabled` | boolean | `true` | Whether consumer retry is enabled |
| `retry.max-attempts` | int | `3` | Maximum retry attempts |
| `retry.initial-delay` | duration | `1s` | Initial retry delay |
| `retry.max-delay` | duration | `5m` | Maximum retry delay |
| `retry.multiplier` | double | `2.0` | Backoff multiplier |

### Kafka Consumer

The Kafka consumer supports **dynamic topic subscription** from `@EventListener` annotations. If you have `@EventListener` methods with `consumerType=KAFKA` or `consumerType=AUTO`, the consumer will automatically subscribe to those topics. If no annotations are found, it falls back to the configured topics.

```yaml
firefly:
  eda:
    consumer:
      kafka:
        default:
          enabled: true
          bootstrap-servers: "localhost:9092"
          topics: "events"  # Fallback topics if no @EventListener annotations found
          auto-offset-reset: "earliest"
          key-deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
          value-deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
          properties:
            enable.auto.commit: false
            session.timeout.ms: 30000
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether this Kafka consumer is enabled |
| `bootstrap-servers` | string | `null` | Kafka bootstrap servers |
| `topics` | string | `"events"` | Fallback topics to consume if no @EventListener annotations found (comma-separated, supports regex patterns) |
| `auto-offset-reset` | string | `"earliest"` | Auto offset reset strategy |
| `key-deserializer` | string | `StringDeserializer` | Kafka key deserializer class |
| `value-deserializer` | string | `StringDeserializer` | Kafka value deserializer class |
| `properties` | map | `{}` | Additional Kafka consumer properties |

**Note**: The Kafka consumer will automatically discover topics from `@EventListener` annotations at startup. Wildcard patterns like `*` are converted to `.*` for regex compatibility.

### RabbitMQ Consumer

Unlike Kafka, RabbitMQ consumers subscribe to **specific, pre-declared queues** configured in application properties. The `@EventListener` destinations for RabbitMQ are in the format `"exchange/routing-key"` and are used for message routing and filtering after consumption, not for queue subscription.

```yaml
firefly:
  eda:
    consumer:
      rabbitmq:
        default:
          enabled: true
          host: "localhost"
          port: 5672
          username: "guest"
          password: "guest"
          virtual-host: "/"
          queues: "events-queue"  # Required: specific queue names to subscribe to
          concurrent-consumers: 1
          max-concurrent-consumers: 5
          prefetch-count: 10
          properties:
            acknowledge-mode: "auto"
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether this RabbitMQ consumer is enabled |
| `host` | string | `"localhost"` | RabbitMQ host |
| `port` | int | `5672` | RabbitMQ port |
| `username` | string | `"guest"` | RabbitMQ username |
| `password` | string | `"guest"` | RabbitMQ password |
| `virtual-host` | string | `"/"` | RabbitMQ virtual host |
| `queues` | string | `"events-queue"` | **Required**: Specific queue names to consume (comma-separated, no wildcards) |
| `concurrent-consumers` | int | `1` | Number of concurrent consumers |
| `max-concurrent-consumers` | int | `5` | Maximum concurrent consumers |
| `prefetch-count` | int | `10` | Message prefetch count |
| `properties` | map | `{}` | Additional RabbitMQ consumer properties |

**Important**: RabbitMQ queues must be pre-declared and configured here. The `@EventListener` destinations are used for filtering messages after they are consumed from these queues, not for determining which queues to subscribe to.

### PostgreSQL Consumer

The PostgreSQL consumer holds one long-lived R2DBC connection to receive `NOTIFY` messages and creates short-lived connections to drain the outbox table. Configuration lives under `firefly.eda.consumer.postgres.<connection-id>`. Channels are derived from `@EventListener` annotations with `consumerType=POSTGRES` or `consumerType=AUTO`, and additionally from the `channels` property.

```yaml
firefly:
  eda:
    consumer:
      postgres:
        default:
          enabled: true
          host: "localhost"
          port: 5432
          database: "app"
          username: "app"
          password: "secret"
          schema: "public"
          outbox-table: "firefly_eda_outbox"
          channels: "events,order-events"   # comma-separated destinations to LISTEN on
          polling-interval: 30s             # NOTIFY-loss fallback poll cadence; set to 0s to disable
          max-attempts: 3                   # rows beyond this attempt count are marked DEAD_LETTER
          batch-size: 50                    # max rows fetched per poll cycle
          max-pool-size: 5
          properties: {}
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether this PostgreSQL consumer connection is enabled |
| `host` | string | `"localhost"` | PostgreSQL host |
| `port` | int | `5432` | PostgreSQL port |
| `database` | string | `null` | Database name |
| `username` | string | `null` | Database username |
| `password` | string | `null` | Database password |
| `schema` | string | `"public"` | Schema containing the outbox table |
| `outbox-table` | string | `"firefly_eda_outbox"` | Outbox table the consumer reads from |
| `channels` | string | `"events"` | Comma-separated destinations to LISTEN on (combined with any from `@EventListener`) |
| `polling-interval` | duration | `30s` | Fallback poll cadence for rows missed by NOTIFY; `0s` disables polling |
| `max-attempts` | int | `3` | Failure attempts before a row transitions to `DEAD_LETTER` |
| `batch-size` | int | `50` | Maximum rows fetched per poll cycle |
| `max-pool-size` | int | `5` | Connection pool size for outbox queries; LISTEN uses one connection on top of this budget |
| `properties` | map | `{}` | Additional R2DBC PostgreSQL startup options |

**How acknowledgement works**: On successful dispatch, the row is updated to `status='PROCESSED', processed_at=NOW()`. On failure, `attempts` is incremented and `error_message` is recorded; once `attempts >= max-attempts`, the row moves to `status='DEAD_LETTER'`. Use the outbox table directly to inspect, reset, or requeue events.

### Application Event Consumer

```yaml
firefly:
  eda:
    consumer:
      application-event:
        enabled: true
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether Application Event consumer is enabled |

**Note:** Application Event consumer listens to Spring's internal event bus. It's enabled by default when the global consumer is enabled.

### NOOP Consumer

```yaml
firefly:
  eda:
    consumer:
      noop:
        enabled: false
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether NOOP consumer is enabled |

**Note:** NOOP consumer is useful for testing scenarios where message consumption should be disabled.

## Resilience Configuration

Resilience features are configured under `firefly.eda.resilience`.

```yaml
firefly:
  eda:
    resilience:
      enabled: true
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 60s
        minimum-number-of-calls: 10
        sliding-window-size: 10
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2.0
      rate-limiter:
        enabled: false
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 5s
```

### Circuit Breaker Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `circuit-breaker.enabled` | boolean | `true` | Whether circuit breaker is enabled |
| `circuit-breaker.failure-rate-threshold` | int | `50` | Failure rate threshold (%) |
| `circuit-breaker.slow-call-rate-threshold` | int | `50` | Slow call rate threshold (%) |
| `circuit-breaker.slow-call-duration-threshold` | duration | `60s` | Slow call duration threshold |
| `circuit-breaker.minimum-number-of-calls` | int | `10` | Minimum number of calls |
| `circuit-breaker.sliding-window-size` | int | `10` | Sliding window size |
| `circuit-breaker.wait-duration-in-open-state` | duration | `60s` | Wait duration in open state |
| `circuit-breaker.permitted-number-of-calls-in-half-open-state` | int | `3` | Permitted calls in half-open state |

### Retry Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `retry.enabled` | boolean | `true` | Whether retry is enabled |
| `retry.max-attempts` | int | `3` | Maximum retry attempts |
| `retry.wait-duration` | duration | `500ms` | Wait duration between retries |
| `retry.exponential-backoff-multiplier` | double | `2.0` | Exponential backoff multiplier |

### Rate Limiter Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `rate-limiter.enabled` | boolean | `false` | Whether rate limiter is enabled |
| `rate-limiter.limit-for-period` | int | `100` | Number of permits per period |
| `rate-limiter.limit-refresh-period` | duration | `1s` | Period duration |
| `rate-limiter.timeout-duration` | duration | `5s` | Timeout for acquiring permits |

## Examples

### Basic Configuration

```yaml
firefly:
  eda:
    enabled: true
    default-publisher-type: KAFKA
    default-destination: "my-events"
    metrics-enabled: true
    health-enabled: true
```

### Multi-Environment Kafka Setup

```yaml
firefly:
  eda:
    publishers:
      kafka:
        primary:
          bootstrap-servers: "kafka-primary:9092"
          default-topic: "events"
          properties:
            acks: "all"
            retries: 3
        secondary:
          bootstrap-servers: "kafka-secondary:9092"
          default-topic: "backup-events"
          properties:
            acks: "1"
            retries: 1
```

### Production Resilience Configuration

```yaml
firefly:
  eda:
    resilience:
      enabled: true
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 30
        minimum-number-of-calls: 20
        wait-duration-in-open-state: 30s
      retry:
        enabled: true
        max-attempts: 5
        wait-duration: 1s
        exponential-backoff-multiplier: 1.5
      rate-limiter:
        enabled: true
        limit-for-period: 1000
        limit-refresh-period: 1s
```

### Consumer with Custom Retry

```yaml
firefly:
  eda:
    consumer:
      enabled: true
      group-id: "my-service"
      concurrency: 5
      retry:
        enabled: true
        max-attempts: 5
        initial-delay: 2s
        max-delay: 10m
        multiplier: 3.0
      kafka:
        default:
          bootstrap-servers: "localhost:9092"
          topics: "orders,payments,notifications"
          auto-offset-reset: "latest"
```

### Complete Multi-Consumer Setup

```yaml
firefly:
  eda:
    # Global consumer settings
    consumer:
      enabled: true
      group-id: "my-service-group"
      concurrency: 3

      # Kafka consumer
      kafka:
        default:
          enabled: true
          bootstrap-servers: "localhost:9092"
          topics: "events"
          auto-offset-reset: "earliest"
          properties:
            enable.auto.commit: false
            session.timeout.ms: 30000

      # RabbitMQ consumer
      rabbitmq:
        default:
          enabled: true
          host: "localhost"
          port: 5672
          username: "guest"
          password: "guest"
          virtual-host: "/"
          queues: "events-queue"
          concurrent-consumers: 2
          max-concurrent-consumers: 10
          prefetch-count: 20

      # Application Event consumer (in-memory)
      application-event:
        enabled: true

      # NOOP consumer (for testing)
      noop:
        enabled: false
```

## Environment Variables

All properties can be set using environment variables by converting to uppercase and replacing dots and hyphens with underscores:

```bash
FIREFLY_EDA_ENABLED=true
FIREFLY_EDA_DEFAULT_PUBLISHER_TYPE=KAFKA
FIREFLY_EDA_PUBLISHERS_KAFKA_DEFAULT_BOOTSTRAP_SERVERS=localhost:9092
FIREFLY_EDA_RESILIENCE_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD=30
```

## Validation

The library includes comprehensive validation for all configuration properties:

- **Required fields**: Some properties like `default-publisher-type` cannot be null
- **Size constraints**: Connection IDs are limited to 100 characters
- **Range validation**: Concurrency must be between 1 and 100
- **Format validation**: Durations must be valid ISO-8601 format

Invalid configurations will cause the application to fail startup with detailed error messages.
