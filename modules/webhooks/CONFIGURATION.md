# Configuration Guide

## Table of Contents

- [Overview](#overview)
- [Environment Variables](#environment-variables)
- [Application Configuration](#application-configuration)
- [Webhook Destination Configuration](#webhook-destination-configuration)
- [Cache Configuration](#cache-configuration)
- [Event-Driven Architecture Configuration](#event-driven-architecture-configuration)
- [Server Configuration](#server-configuration)
- [Monitoring & Observability](#monitoring--observability)
- [Logging Configuration](#logging-configuration)
- [Configuration Examples](#configuration-examples)

## Overview

The Firefly Framework Webhooks Library is configured using a combination of:
1. **Environment Variables** - For deployment-specific values (Kafka URLs, Redis hosts, etc.)
2. **application.yml** - For application-level configuration
3. **Firefly Libraries** - Configuration from `lib-common-eda`, `lib-common-cache`, etc.

All configuration follows the **12-Factor App** methodology, with environment-specific values externalized as environment variables.

## Environment Variables

### Required Environment Variables

These environment variables **must** be set for the application to start:

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `FIREFLY_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers (comma-separated) | `localhost:29092` | None (required) |

### Optional Environment Variables

#### Redis Configuration

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `FIREFLY_REDIS_ENABLED` | Enable Redis distributed cache | `true` | `true` |
| `REDIS_HOST` | Redis server hostname | `localhost` | `localhost` |
| `REDIS_PORT` | Redis server port | `26379` | `6379` |
| `REDIS_DATABASE` | Redis database number | `0` | `0` |
| `REDIS_PASSWORD` | Redis password (if auth enabled) | `secret` | `` (empty) |
| `REDIS_USERNAME` | Redis username (Redis 6+) | `default` | `` (empty) |
| `REDIS_SSL` | Enable SSL/TLS for Redis | `true` | `false` |
| `REDIS_HEALTH_ENABLED` | Enable Redis health check | `true` | `true` |

#### Cache Configuration

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `FIREFLY_CACHE_ENABLED` | Enable caching | `true` | `true` |
| `FIREFLY_CACHE_TYPE` | Cache type: `REDIS` or `CAFFEINE` | `REDIS` | `REDIS` |

#### EDA (Event-Driven Architecture) Configuration

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `FIREFLY_EDA_PUBLISHER_TYPE` | Publisher type: `KAFKA` or `RABBITMQ` | `KAFKA` | `KAFKA` |
| `FIREFLY_EDA_CONSUMER_TYPE` | Consumer type: `KAFKA` or `RABBITMQ` | `KAFKA` | `KAFKA` |
| `FIREFLY_EDA_SERIALIZATION_FORMAT` | Serialization format: `json` or `avro` | `json` | `json` |
| `FIREFLY_EDA_METRICS_ENABLED` | Enable EDA metrics | `true` | `true` |
| `FIREFLY_EDA_HEALTH_ENABLED` | Enable EDA health checks | `true` | `true` |
| `FIREFLY_CONSUMER_GROUP_ID` | Kafka consumer group ID | `webhook-worker` | `webhook-worker-test` |

#### RabbitMQ Configuration (Optional)

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `RABBITMQ_HOST` | RabbitMQ server hostname | `localhost` | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ server port | `5672` | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ username | `admin` | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `secret` | `guest` |
| `RABBITMQ_HEALTH_ENABLED` | Enable RabbitMQ health check | `true` | `false` |

#### Server Configuration

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `SERVER_PORT` | HTTP server port | `8080` | `8080` |

## Application Configuration

### Spring Boot Configuration

#### Application Name
```yaml
spring:
  application:
    name: fireflyframework-webhooks
```

#### Bean Definition Overriding
```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

**Why**: Required for `lib-common-cache` to override Spring Boot's default Redis configuration.

#### WebFlux Configuration
```yaml
spring:
  webflux:
    base-path: /
```

**Description**: Base path for all WebFlux endpoints.

#### Jackson Configuration
```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
```

**Description**:
- `write-dates-as-timestamps: false` - Serialize dates as ISO-8601 strings
- `fail-on-unknown-properties: false` - Ignore unknown JSON properties

### Server Configuration

```yaml
server:
  port: 8080
  http2:
    enabled: true
```

**Description**:
- `port: 8080` - HTTP server port
- `http2.enabled: true` - Enable HTTP/2 support

## Webhook Destination Configuration

The webhook destination configuration determines how webhook events are routed to Kafka/RabbitMQ topics.

### Configuration Properties

```yaml
firefly:
  webhooks:
    destination:
      prefix: ""
      suffix: ""
      use-provider-as-topic: true
      custom: ""
```

### Destination Strategies

#### Strategy 1: Provider-Based Routing (Default)

Each provider gets its own topic:

```yaml
firefly:
  webhooks:
    destination:
      use-provider-as-topic: true
      prefix: ""
      suffix: ""
      custom: ""
```

**Result**:
- `POST /api/v1/webhook/stripe` → Topic: `stripe`
- `POST /api/v1/webhook/paypal` → Topic: `paypal`
- `POST /api/v1/webhook/github` → Topic: `github`

#### Strategy 2: Prefixed Topics

Add a namespace prefix:

```yaml
firefly:
  webhooks:
    destination:
      use-provider-as-topic: true
      prefix: "webhooks."
      suffix: ""
      custom: ""
```

**Result**:
- `POST /api/v1/webhook/stripe` → Topic: `webhooks.stripe`
- `POST /api/v1/webhook/paypal` → Topic: `webhooks.paypal`

#### Strategy 3: Prefixed and Suffixed Topics

Add both prefix and suffix:

```yaml
firefly:
  webhooks:
    destination:
      use-provider-as-topic: true
      prefix: "webhooks."
      suffix: ".received"
      custom: ""
```

**Result**:
- `POST /api/v1/webhook/stripe` → Topic: `webhooks.stripe.received`
- `POST /api/v1/webhook/paypal` → Topic: `webhooks.paypal.received`

#### Strategy 4: Single Topic for All Providers

Route all webhooks to one topic:

```yaml
firefly:
  webhooks:
    destination:
      use-provider-as-topic: false
      prefix: ""
      suffix: ""
      custom: "webhooks.all"
```

**Result**:
- All providers → Topic: `webhooks.all`

#### Strategy 5: Prefix/Suffix Only (No Provider Name)

```yaml
firefly:
  webhooks:
    destination:
      use-provider-as-topic: false
      prefix: "webhooks"
      suffix: ".ingestion"
      custom: ""
```

**Result**:
- All providers → Topic: `webhooks.ingestion`

### Destination Resolution Logic

The destination is resolved in the following order:

1. **If `custom` is set**: Use `custom` value (ignores all other settings)
2. **If `use-provider-as-topic` is true**: Use `{prefix}{providerName}{suffix}`
3. **If `use-provider-as-topic` is false**: Use `{prefix}{suffix}`

## Cache Configuration

The platform uses `lib-common-cache` for distributed caching and event-level idempotency.

**Note**: HTTP-level idempotency (X-Idempotency-Key header) is now handled by `lib-common-web` and configured separately using `idempotency.*` properties. See [HTTP Idempotency Configuration](#http-idempotency-configuration) below.

### Cache Types

#### Redis (Distributed Cache)

**Use Case**: Production deployments with multiple instances

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: REDIS
    
    redis:
      enabled: true
      cache-name: "default"
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
      password: ${REDIS_PASSWORD:}
      username: ${REDIS_USERNAME:}
      connection-timeout: 10s
      command-timeout: 5s
      key-prefix: "firefly:webhooks"
      default-ttl: 7d
      max-pool-size: 8
      min-pool-size: 2
      ssl: ${REDIS_SSL:false}
```

**Configuration Details**:
- `cache-name`: Name of the cache (used for metrics)
- `key-prefix`: Prefix for all Redis keys
- `default-ttl`: Time-to-live for cache entries (7 days)
- `max-pool-size`: Maximum number of Redis connections
- `min-pool-size`: Minimum number of Redis connections
- `connection-timeout`: Timeout for establishing connection
- `command-timeout`: Timeout for Redis commands

#### Caffeine (In-Memory Cache)

**Use Case**: Local development or single-instance deployments

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE
    
    caffeine:
      enabled: true
      cache-name: "default"
      key-prefix: "firefly:webhooks"
      maximum-size: 10000
      expire-after-write: 1h
      record-stats: true
```

**Configuration Details**:
- `maximum-size`: Maximum number of entries in cache
- `expire-after-write`: Expiration time after write
- `record-stats`: Enable cache statistics

### Cache Fallback

The platform supports automatic fallback from Redis to Caffeine:

```yaml
firefly:
  cache:
    default-cache-type: REDIS
    
    redis:
      enabled: true
      # Redis configuration...
    
    caffeine:
      enabled: true  # Fallback to Caffeine if Redis fails
      # Caffeine configuration...
```

## HTTP Idempotency Configuration

HTTP-level idempotency is handled by `lib-common-web`'s `IdempotencyWebFilter`. This provides automatic duplicate request detection using the `X-Idempotency-Key` header.

### Configuration

```yaml
idempotency:
  header-name: X-Idempotency-Key  # Header name (default)
  cache:
    ttl-hours: 24  # How long to cache responses (default: 24 hours)
```

### How It Works

1. **Client sends request with X-Idempotency-Key header**:
   ```bash
   curl -X POST http://localhost:8080/api/v1/webhook/stripe \
     -H "Content-Type: application/json" \
     -H "X-Idempotency-Key: unique-request-id-123" \
     -d '{"type": "payment_intent.succeeded"}'
   ```

2. **IdempotencyWebFilter intercepts the request**:
   - Checks cache for the idempotency key
   - If found: returns cached HTTP response (status, headers, body)
   - If not found: proceeds with request processing and caches the complete response

3. **Subsequent requests with same key**:
   - Return cached response immediately
   - No controller or business logic execution
   - Exact same HTTP response (202 ACCEPTED with same eventId, timestamps, etc.)

### Key Prefixes

The HTTP idempotency cache uses the prefix `:idempotency:` which, combined with the cache's own prefix, results in keys like:
```
firefly:webhooks::idempotency:{your-key}
```

### Disabling Idempotency for Specific Endpoints

Use the `@DisableIdempotency` annotation:
```java
@PostMapping("/api/v1/webhook/special")
@DisableIdempotency
public Mono<ResponseEntity> specialEndpoint() {
    // This endpoint will not have idempotency checking
}
```

### Environment Variables

```bash
# Configure idempotency header name
IDEMPOTENCY_HEADER_NAME=X-Idempotency-Key

# Configure cache TTL (in hours)
IDEMPOTENCY_CACHE_TTL_HOURS=24
```

### Difference from Event-Level Idempotency

The platform has **two levels** of idempotency:

1. **HTTP-level (lib-common-web)**: Prevents duplicate HTTP requests
   - Scope: Request/Response
   - Key: `X-Idempotency-Key` header
   - Cache prefix: `:idempotency:`

2. **Event-level (CacheBasedWebhookIdempotencyService)**: Prevents duplicate event processing by workers
   - Scope: Kafka consumer / Worker processing
   - Key: `eventId` (UUID)
   - Cache prefixes: `webhook:processing:`, `webhook:processed:`, `webhook:failures:`

Both levels are independent and serve different purposes.

## Event-Driven Architecture Configuration

The platform uses `lib-common-eda` for event publishing and consumption.

### Publisher Configuration

#### Kafka Publisher (Primary)

```yaml
firefly:
  eda:
    enabled: true
    default-publisher-type: KAFKA
    
    publishers:
      kafka:
        default:
          enabled: true
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS}
          properties:
            acks: all
            retries: 3
            compression.type: snappy
            max.in.flight.requests.per.connection: 5
            enable.idempotence: true
            linger.ms: 10
            batch.size: 16384
            buffer.memory: 33554432
```

**Configuration Details**:
- `acks: all` - Wait for all replicas to acknowledge
- `retries: 3` - Retry failed sends up to 3 times
- `compression.type: snappy` - Use Snappy compression
- `enable.idempotence: true` - Enable idempotent producer
- `linger.ms: 10` - Wait up to 10ms to batch messages
- `batch.size: 16384` - Batch size in bytes
- `buffer.memory: 33554432` - Total memory for buffering (32MB)

### Consumer Configuration

```yaml
firefly:
  eda:
    consumer:
      enabled: true
      group-id: ${FIREFLY_CONSUMER_GROUP_ID:webhook-worker-test}
      kafka:
        default:
          enabled: true
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS}
          auto-offset-reset: earliest
          properties:
            enable.auto.commit: true
            auto.commit.interval.ms: 1000
            metadata.max.age.ms: 1000
```

**Configuration Details**:
- `group-id`: Kafka consumer group ID
- `auto-offset-reset: earliest` - Start from earliest offset if no offset exists
- `enable.auto.commit: true` - Automatically commit offsets
- `auto.commit.interval.ms: 1000` - Commit offsets every 1 second

## Server Configuration

### HTTP Server

```yaml
server:
  port: 8080
  http2:
    enabled: true
```

### Actuator & Management Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: always
  health:
    rabbit:
      enabled: ${RABBITMQ_HEALTH_ENABLED:false}
    redis:
      enabled: ${REDIS_HEALTH_ENABLED:true}
```

**Exposed Endpoints**:
- `/actuator/health` - Health check
- `/actuator/info` - Application info
- `/actuator/metrics` - Metrics
- `/actuator/prometheus` - Prometheus metrics

## Monitoring & Observability

### OpenAPI / Swagger Configuration

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  show-actuator: true
  packages-to-scan: org.fireflyframework.webhooks.web.controllers
  paths-to-match: /api/**
```

**Endpoints**:
- `/swagger-ui.html` - Interactive API documentation
- `/v3/api-docs` - OpenAPI JSON specification

### Metrics Configuration

```yaml
firefly:
  cache:
    metrics-enabled: true
    stats-enabled: true
  
  eda:
    metrics-enabled: true
```

**Metrics Exposed**:
- Cache hit/miss rates
- Kafka producer/consumer metrics
- HTTP request metrics
- JVM metrics

## Logging Configuration

```yaml
logging:
  level:
    root: INFO
    org.fireflyframework.webhooks: DEBUG
    org.fireflyframework.common.eda: DEBUG
    org.fireflyframework.common.eventsourcing: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

**Log Levels**:
- `root: INFO` - Default log level for all packages
- `org.fireflyframework.webhooks: DEBUG` - Debug level for webhook platform
- `org.fireflyframework.common.eda: DEBUG` - Debug level for EDA library
- `org.fireflyframework.common.eventsourcing: DEBUG` - Debug level for event sourcing

## Configuration Examples

### Development Environment

```bash
# Minimal configuration for local development
export FIREFLY_KAFKA_BOOTSTRAP_SERVERS=localhost:29092
export FIREFLY_CACHE_TYPE=CAFFEINE  # In-memory cache
export FIREFLY_REDIS_ENABLED=false  # Disable Redis
```

```yaml
firefly:
  cache:
    default-cache-type: CAFFEINE
  webhooks:
    destination:
      use-provider-as-topic: true
```

### Staging Environment

```bash
# Staging configuration with Redis
export FIREFLY_KAFKA_BOOTSTRAP_SERVERS=kafka-staging:9092
export FIREFLY_CACHE_TYPE=REDIS
export FIREFLY_REDIS_ENABLED=true
export REDIS_HOST=redis-staging
export REDIS_PORT=6379
export REDIS_PASSWORD=staging-secret
export FIREFLY_CONSUMER_GROUP_ID=webhook-worker-staging
```

```yaml
firefly:
  cache:
    default-cache-type: REDIS
    redis:
      enabled: true
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
  webhooks:
    destination:
      prefix: "staging."
      use-provider-as-topic: true
  eda:
    publishers:
      kafka:
        default:
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS}
```

### Production Environment

```bash
# Production configuration with high availability
export FIREFLY_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
export FIREFLY_CACHE_TYPE=REDIS
export FIREFLY_REDIS_ENABLED=true
export REDIS_HOST=redis-cluster
export REDIS_PORT=6379
export REDIS_PASSWORD=prod-secret
export REDIS_SSL=true
export FIREFLY_CONSUMER_GROUP_ID=webhook-worker-prod
export FIREFLY_EDA_METRICS_ENABLED=true
export FIREFLY_EDA_HEALTH_ENABLED=true
```

```yaml
firefly:
  cache:
    default-cache-type: REDIS
    redis:
      enabled: true
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      ssl: ${REDIS_SSL}
      max-pool-size: 16
      min-pool-size: 4
      default-ttl: 7d
  webhooks:
    destination:
      prefix: "webhooks."
      use-provider-as-topic: true
  eda:
    publishers:
      kafka:
        default:
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS}
          properties:
            acks: all
            retries: 5
            compression.type: snappy
            enable.idempotence: true
```

## Configuration Best Practices

### 1. Use Environment Variables for Secrets

**Bad**:
```yaml
firefly:
  cache:
    redis:
      password: "my-secret-password"  # Hardcoded secret
```

**Good**:
```yaml
firefly:
  cache:
    redis:
      password: ${REDIS_PASSWORD}  # Environment variable
```

### 2. Use Different Cache Types per Environment

**Development**:
```yaml
firefly:
  cache:
    default-cache-type: CAFFEINE  # In-memory for local dev
```

**Production**:
```yaml
firefly:
  cache:
    default-cache-type: REDIS  # Distributed for production
```

### 3. Configure Appropriate TTLs

**Event Idempotency (Worker-level)**:
```yaml
firefly:
  cache:
    redis:
      default-ttl: 7d  # Keep for 7 days (webhook providers may retry)
```

**HTTP Idempotency (Request-level)**:
```yaml
idempotency:
  header-name: X-Idempotency-Key
  cache:
    ttl-hours: 24  # Keep HTTP responses cached for 24 hours
```

### 4. Use Topic Prefixes for Multi-Tenancy

**Tenant A**:
```yaml
firefly:
  webhooks:
    destination:
      prefix: "tenant-a."
      use-provider-as-topic: true
```

**Tenant B**:
```yaml
firefly:
  webhooks:
    destination:
      prefix: "tenant-b."
      use-provider-as-topic: true
```

### 5. Enable Metrics in Production

```yaml
firefly:
  cache:
    metrics-enabled: true
    stats-enabled: true
  eda:
    metrics-enabled: true
    health-enabled: true
```

### 6. Configure Connection Pools Appropriately

**Low Traffic**:
```yaml
firefly:
  cache:
    redis:
      max-pool-size: 8
      min-pool-size: 2
```

**High Traffic**:
```yaml
firefly:
  cache:
    redis:
      max-pool-size: 32
      min-pool-size: 8
```

## Troubleshooting Configuration Issues

### Issue: Application fails to start with "ClassNotFoundException: FireflyCacheManager"

**Cause**: `lib-common-cache` dependency is missing or has wrong scope

**Solution**: Ensure dependency is in compile/runtime scope:
```xml
<dependency>
    <groupId>org.fireflyframework.common</groupId>
    <artifactId>lib-common-cache</artifactId>
    <version>${lib-common-cache.version}</version>
    <!-- NOT test scope -->
</dependency>
```

### Issue: Redis connection fails with "Connection refused"

**Cause**: Redis is not running or wrong host/port

**Solution**: Verify Redis is running and check configuration:
```bash
# Test Redis connection
redis-cli -h localhost -p 6379 ping

# Check environment variables
echo $REDIS_HOST
echo $REDIS_PORT
```

### Issue: Kafka producer fails with "TimeoutException"

**Cause**: Kafka is not reachable or wrong bootstrap servers

**Solution**: Verify Kafka is running and check configuration:
```bash
# Test Kafka connection
nc -zv localhost 29092

# Check environment variable
echo $FIREFLY_KAFKA_BOOTSTRAP_SERVERS
```

### Issue: Bean definition conflict for "redisConnectionFactory"

**Cause**: Multiple beans trying to create Redis connection factory

**Solution**: Enable bean definition overriding:
```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

### Issue: Webhooks not being published to expected topic

**Cause**: Incorrect destination configuration

**Solution**: Check destination configuration and test:
```bash
# Send test webhook
curl -X POST http://localhost:8080/api/v1/webhook/stripe \
  -H "Content-Type: application/json" \
  -d '{"test": true}'

# Check Kafka topic
kafka-console-consumer --bootstrap-server localhost:29092 --topic stripe --from-beginning
```
