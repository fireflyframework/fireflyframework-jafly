# Configuration Reference

Complete configuration reference for `fireflyframework-starter-data` starter.

## Table of Contents

- [Overview](#overview)
- [Core Configuration](#core-configuration)
- [Job Orchestration](#job-orchestration)
- [EDA Integration](#eda-integration)
- [CQRS Integration](#cqrs-integration)
- [Orchestration Engine](#orchestration-engine)
- [Step Events](#step-events)
- [Configuration Examples](#configuration-examples)

---

## Overview

The `fireflyframework-starter-data` starter uses Spring Boot's configuration mechanism with sensible defaults. All configuration is under the `firefly` namespace.

### Configuration Hierarchy

```yaml
firefly:
  data:                    # Main data library configuration
    eda:                   # Event-Driven Architecture settings
    cqrs:                  # CQRS pattern settings
    orchestration:         # Job orchestration settings
    enrichment:            # Data enrichment settings
      providers:           # Per-provider resilience overrides
    quality:               # Data quality framework settings
    lineage:               # Data lineage tracking settings
  stepevents:              # Step event bridge settings
  eda:                     # fireflyframework-eda configuration
```

---

## Core Configuration

### Enable/Disable Features

```yaml
firefly:
  data:
    eda:
      enabled: true        # Enable EDA integration (default: true)
    cqrs:
      enabled: true        # Enable CQRS integration (default: true)
    orchestration:
      enabled: true        # Enable job orchestration (default: true)
    orchestration:
      enabled: false       # Enable orchestration engine (default: true)
```

**Notes:**
- All features are enabled by default
- Disabling a feature will prevent its auto-configuration
- Dependencies must be on classpath for features to work

---

## Job Orchestration

### Basic Configuration

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: APACHE_AIRFLOW       # Type of orchestrator
      default-timeout: 24h                    # Default job timeout
      max-retries: 3                          # Max retry attempts
      retry-delay: 5s                         # Delay between retries
      publish-job-events: true                # Publish job lifecycle events
      job-events-topic: data-job-events       # Topic for job events
```

### Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable job orchestration |
| `orchestrator-type` | string | `APACHE_AIRFLOW` | Orchestrator implementation (`APACHE_AIRFLOW`, `AWS_STEP_FUNCTIONS`) |
| `default-timeout` | duration | `24h` | Default timeout for jobs |
| `max-retries` | int | `3` | Maximum retry attempts |
| `retry-delay` | duration | `5s` | Delay between retries |
| `publish-job-events` | boolean | `true` | Publish job events to EDA |
| `job-events-topic` | string | `data-job-events` | Topic for job events |

### Apache Airflow Configuration

```yaml
firefly:
  data:
    orchestration:
      orchestrator-type: APACHE_AIRFLOW
      airflow:
        base-url: http://airflow.example.com:8080  # Airflow base URL
        api-version: v1                             # API version
        authentication-type: BASIC                  # Auth type (BASIC, BEARER_TOKEN, NONE)
        username: airflow                           # Username for basic auth
        password: airflow                           # Password for basic auth
        dag-id-prefix: data_job                     # DAG ID prefix
        connection-timeout: 10s                     # Connection timeout
        request-timeout: 30s                        # Request timeout
        verify-ssl: true                            # Verify SSL certificates
        max-concurrent-dag-runs: 10                 # Max concurrent DAG runs
```

#### Airflow Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `airflow.base-url` | string | - | Airflow base URL (required) |
| `airflow.api-version` | string | `v1` | Airflow API version |
| `airflow.authentication-type` | string | `BASIC` | Authentication type (BASIC, BEARER_TOKEN, NONE) |
| `airflow.username` | string | - | Username for basic authentication |
| `airflow.password` | string | - | Password for basic authentication |
| `airflow.bearer-token` | string | - | Bearer token for token-based authentication |
| `airflow.dag-id-prefix` | string | `data_job` | Prefix for DAG IDs |
| `airflow.connection-timeout` | duration | `10s` | Connection timeout |
| `airflow.request-timeout` | duration | `30s` | Request timeout |
| `airflow.verify-ssl` | boolean | `true` | Verify SSL certificates |
| `airflow.max-concurrent-dag-runs` | int | `10` | Maximum concurrent DAG runs |

**Example DAG ID Construction:**

Given `dag-id-prefix: data_job` and job definition `customer-data-extraction`:
```
data_job_customer_data_extraction
```

### AWS Step Functions Configuration

```yaml
firefly:
  data:
    orchestration:
      orchestrator-type: AWS_STEP_FUNCTIONS
      aws-step-functions:
        region: us-east-1                     # AWS region
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:DataJobStateMachine
        use-default-credentials: true         # Use AWS default credentials
        connection-timeout: 10s               # Connection timeout
        request-timeout: 30s                  # Request timeout
        max-concurrent-executions: 100        # Max concurrent executions
        enable-x-ray-tracing: false           # Enable X-Ray tracing
```

#### AWS Step Functions Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `aws-step-functions.region` | string | `us-east-1` | AWS region for Step Functions |
| `aws-step-functions.state-machine-arn` | string | - | State machine ARN (required) |
| `aws-step-functions.use-default-credentials` | boolean | `true` | Use AWS default credential provider |
| `aws-step-functions.access-key-id` | string | - | AWS access key ID (if not using default credentials) |
| `aws-step-functions.secret-access-key` | string | - | AWS secret access key (if not using default credentials) |
| `aws-step-functions.session-token` | string | - | AWS session token (for temporary credentials) |
| `aws-step-functions.connection-timeout` | duration | `10s` | Connection timeout |
| `aws-step-functions.request-timeout` | duration | `30s` | Request timeout |
| `aws-step-functions.max-concurrent-executions` | int | `100` | Maximum concurrent executions |
| `aws-step-functions.enable-x-ray-tracing` | boolean | `false` | Enable AWS X-Ray tracing |

**Example State Machine ARN:**
```
arn:aws:states:us-east-1:123456789012:stateMachine:DataJobStateMachine
```

---

## EDA Integration

### Basic EDA Configuration

```yaml
firefly:
  data:
    eda:
      enabled: true
```

### fireflyframework-eda Configuration

The library integrates with `fireflyframework-eda`. Configure publishers and connections:

```yaml
firefly:
  eda:
    publishers:
      - id: default
        type: KAFKA                           # Publisher type
        connection-id: kafka-default
        
    connections:
      kafka:
        - id: kafka-default
          bootstrap-servers: localhost:9092
          properties:
            acks: all
            retries: 3
```

### Supported Publisher Types

- `KAFKA` - Apache Kafka
- `RABBITMQ` - RabbitMQ
- `SQS` - AWS Simple Queue Service
- `SNS` - AWS Simple Notification Service
- `PUBSUB` - Google Cloud Pub/Sub

### Event Publishing Configuration

```yaml
firefly:
  data:
    orchestration:
      publish-job-events: true
      job-events-topic: my-service-job-events
```

**Published Events:**
- Job started
- Job completed
- Job failed
- Job status changed

---

## CQRS Integration

### Basic CQRS Configuration

```yaml
firefly:
  data:
    cqrs:
      enabled: true
```

### Command/Query Separation

The library automatically separates:

**Commands (Write Operations):**
- `startJob()` - Initiates job execution
- `stopJob()` - Terminates job execution

**Queries (Read Operations):**
- `checkJob()` - Reads job status
- `collectJobResults()` - Reads raw results
- `getJobResult()` - Reads transformed results

No additional configuration needed - separation is built into the interfaces.

---

## Orchestration Engine

### Basic Configuration

The `fireflyframework-orchestration` module is auto-configured via `firefly.orchestration` properties. It consolidates Saga, TCC, and Workflow patterns into a single module with its own `EventGateway` for EDA bridging.

```yaml
firefly:
  orchestration:
    enabled: true
```

**Note:** See the `fireflyframework-orchestration` module documentation for complete options including Saga, TCC, and Workflow configuration.

---

## Data Enrichment

### Basic Enrichment Configuration

```yaml
firefly:
  data:
    enrichment:
      enabled: true
      cache-enabled: true
      cache-ttl: 300         # Cache TTL in seconds
```

### Per-Provider Resilience

Override global resilience settings for specific providers:

```yaml
firefly:
  data:
    enrichment:
      providers:
        equifax:
          circuit-breaker-enabled: true
          circuit-breaker-failure-rate-threshold: 30.0
          circuit-breaker-wait-duration-in-open-state: 30s
          retry-enabled: true
          retry-max-attempts: 5
          retry-wait-duration: 2s
          rate-limiter-enabled: true
          rate-limiter-limit-for-period: 50
          rate-limiter-limit-refresh-period: 1s
          bulkhead-enabled: true
          bulkhead-max-concurrent-calls: 10
          timeout-duration: 15s
        moodys:
          circuit-breaker-enabled: true
          circuit-breaker-failure-rate-threshold: 60.0
          retry-enabled: true
          retry-max-attempts: 2
          timeout-duration: 30s
```

#### Per-Provider Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `circuit-breaker-enabled` | boolean | `true` | Enable circuit breaker for this provider |
| `circuit-breaker-failure-rate-threshold` | float | `50.0` | Failure rate to trip circuit |
| `circuit-breaker-wait-duration-in-open-state` | duration | `60s` | Wait before half-open |
| `retry-enabled` | boolean | `true` | Enable retry for this provider |
| `retry-max-attempts` | int | `3` | Max retry attempts |
| `retry-wait-duration` | duration | `5s` | Wait between retries |
| `rate-limiter-enabled` | boolean | `false` | Enable rate limiting |
| `rate-limiter-limit-for-period` | int | `100` | Requests per period |
| `rate-limiter-limit-refresh-period` | duration | `1s` | Rate limit refresh period |
| `bulkhead-enabled` | boolean | `false` | Enable bulkhead isolation |
| `bulkhead-max-concurrent-calls` | int | `25` | Max concurrent calls |
| `timeout-duration` | duration | `30s` | Operation timeout |

---

## Data Quality

### Quality Framework Configuration

```yaml
firefly:
  data:
    quality:
      enabled: true          # Enable data quality framework (default: true)
```

### Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable data quality auto-configuration |

When enabled, `DataQualityEngine` is created as a bean and auto-discovers all `DataQualityRule<?>` beans in the Spring context.

---

## Data Lineage

### Lineage Tracking Configuration

```yaml
firefly:
  data:
    lineage:
      enabled: false         # Enable lineage tracking (default: false)
```

### Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable lineage tracking auto-configuration |

When enabled, an `InMemoryLineageTracker` bean is created. For production, provide your own `LineageTracker` implementation backed by a persistent store.

---

## Step Events

### Basic Configuration

```yaml
firefly:
  stepevents:
    enabled: true
    topic: data-processing-step-events
    include-job-context: true
```

### Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable step event bridge |
| `topic` | string | `data-processing-step-events` | Default topic for step events |
| `include-job-context` | boolean | `true` | Include job context in event headers |

### Step Event Headers

When `include-job-context: true`, events include:

```yaml
Headers:
  step.saga_name: "data-processing-saga"
  step.saga_id: "saga-123"
  step.step_id: "extract-data"
  step.type: "STEP_STARTED"
  step.attempts: 1
  step.latency_ms: 1500
  step.started_at: "2025-01-15T10:30:00Z"
  step.completed_at: "2025-01-15T10:30:01.5Z"
  step.result_type: "SUCCESS"
  context: "data-processing"
  library: "fireflyframework-starter-data"
  routing_key: "data-processing-saga:saga-123"
```

---

## Configuration Examples

### Development Environment

```yaml
spring:
  application:
    name: my-data-service
  profiles:
    active: dev

firefly:
  data:
    eda:
      enabled: true
    cqrs:
      enabled: true
    orchestration:
      enabled: true
      orchestrator-type: MOCK              # Use mock for development
      publish-job-events: true
      job-events-topic: dev-job-events
    orchestration:
      enabled: false                       # Disable orchestration in dev
    quality:
      enabled: true
    lineage:
      enabled: false               # Disable lineage in dev

  eda:
    publishers:
      - id: default
        type: KAFKA
        connection-id: local-kafka
    connections:
      kafka:
        - id: local-kafka
          bootstrap-servers: localhost:9092

logging:
  level:
    org.fireflyframework: DEBUG
```

### Production Environment (Apache Airflow)

```yaml
spring:
  application:
    name: customer-data-service
  profiles:
    active: prod

firefly:
  data:
    eda:
      enabled: true
    cqrs:
      enabled: true
    orchestration:
      enabled: true
      orchestrator-type: APACHE_AIRFLOW
      default-timeout: 24h
      max-retries: 3
      retry-delay: 10s
      publish-job-events: true
      job-events-topic: prod-customer-job-events
      airflow:
        base-url: https://airflow.production.example.com
        api-version: v1
        authentication-type: BEARER_TOKEN
        bearer-token: ${AIRFLOW_API_TOKEN}
        dag-id-prefix: customer_data
        verify-ssl: true
        max-concurrent-dag-runs: 20
    orchestration:
      enabled: true
    quality:
      enabled: true
    lineage:
      enabled: true
    enrichment:
      providers:
        equifax:
          circuit-breaker-failure-rate-threshold: 30.0
          retry-max-attempts: 5
          rate-limiter-enabled: true
          rate-limiter-limit-for-period: 50
          timeout-duration: 15s

  stepevents:
    enabled: true
    topic: prod-customer-step-events
    include-job-context: true

  eda:
    publishers:
      - id: default
        type: KAFKA
        connection-id: prod-kafka
    connections:
      kafka:
        - id: prod-kafka
          bootstrap-servers: kafka-1.prod:9092,kafka-2.prod:9092,kafka-3.prod:9092
          properties:
            acks: all
            retries: 5
            compression.type: snappy
            max.in.flight.requests.per.connection: 1

logging:
  level:
    org.fireflyframework: INFO
    reactor: WARN
```

### Multi-Region Setup

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      aws-step-functions:
        region: ${AWS_REGION:us-east-1}
        state-machine-arn: arn:aws:states:${AWS_REGION}:${AWS_ACCOUNT_ID}:stateMachine:DataJobStateMachine

  eda:
    publishers:
      - id: primary
        type: KAFKA
        connection-id: primary-kafka
      - id: secondary
        type: SQS
        connection-id: backup-sqs
    connections:
      kafka:
        - id: primary-kafka
          bootstrap-servers: ${KAFKA_BROKERS}
      sqs:
        - id: backup-sqs
          region: ${AWS_REGION}
          queue-url: ${SQS_QUEUE_URL}
```

### Testing Configuration

```yaml
spring:
  application:
    name: test-service
  profiles:
    active: test

firefly:
  data:
    eda:
      enabled: false                       # Disable EDA in tests
    cqrs:
      enabled: false                       # Disable CQRS in tests
    orchestration:
      enabled: true
      orchestrator-type: MOCK              # Use mock orchestrator
      publish-job-events: false            # Don't publish events
    orchestration:
      enabled: false

logging:
  level:
    org.fireflyframework: DEBUG
```

---

## Environment Variables

All properties can be overridden with environment variables:

```bash
# Job orchestration
export FIREFLY_DATA_ORCHESTRATION_ENABLED=true
export FIREFLY_DATA_ORCHESTRATION_ORCHESTRATOR_TYPE=APACHE_AIRFLOW
export FIREFLY_DATA_ORCHESTRATION_AIRFLOW_BASE_URL=http://airflow.example.com:8080
export FIREFLY_DATA_ORCHESTRATION_AIRFLOW_USERNAME=airflow
export FIREFLY_DATA_ORCHESTRATION_AIRFLOW_PASSWORD=airflow

# EDA
export FIREFLY_EDA_PUBLISHERS_0_TYPE=KAFKA
export FIREFLY_EDA_CONNECTIONS_KAFKA_0_BOOTSTRAP_SERVERS=kafka:9092

# Step events
export FIREFLY_STEPEVENTS_ENABLED=true
export FIREFLY_STEPEVENTS_TOPIC=my-step-events
```

---

## Configuration Validation

The library validates configuration at startup:

**Required Properties:**
- None - all have defaults

**Conditional Requirements:**
- If `orchestrator-type: APACHE_AIRFLOW`, then `airflow.base-url` must be set
- If `orchestrator-type: AWS_STEP_FUNCTIONS`, then `aws-step-functions.state-machine-arn` must be set
- If `publish-job-events: true`, then EDA must be enabled

**Validation Errors:**
```
Configuration property 'firefly.data.orchestration.airflow.base-url' is required when orchestrator-type is APACHE_AIRFLOW
Configuration property 'firefly.data.orchestration.aws-step-functions.state-machine-arn' is required when orchestrator-type is AWS_STEP_FUNCTIONS
```

---

## Best Practices

### 1. Use Profiles

Separate configuration by environment:
```
application.yml              # Common config
application-dev.yml          # Development
application-test.yml         # Testing
application-prod.yml         # Production
```

### 2. Externalize Secrets

Never commit credentials:
```yaml
firefly:
  eda:
    connections:
      kafka:
        - id: prod-kafka
          bootstrap-servers: ${KAFKA_BROKERS}
          properties:
            sasl.jaas.config: ${KAFKA_SASL_CONFIG}
```

### 3. Use Sensible Timeouts

```yaml
firefly:
  data:
    orchestration:
      default-timeout: 24h     # Long-running jobs
      retry-delay: 10s         # Give time to recover
```

### 4. Enable Observability

```yaml
firefly:
  data:
    orchestration:
      publish-job-events: true  # Always enable in production
  stepevents:
    enabled: true
    include-job-context: true   # Rich event metadata
```

---

## Troubleshooting

### Common Issues

**Issue:** Auto-configuration not working
```
Solution: Ensure dependency is on classpath and feature is enabled
```

**Issue:** Airflow connection fails
```
Solution: Check Airflow base URL, credentials, and network connectivity
```

**Issue:** AWS Step Functions connection fails
```
Solution: Check AWS credentials, region, and state machine ARN configuration
```

**Issue:** Events not publishing
```
Solution: Verify EDA is enabled and publisher is configured
```

### Debug Configuration

Enable debug logging:
```yaml
logging:
  level:
    org.fireflyframework.data.config: DEBUG
    org.springframework.boot.autoconfigure: DEBUG
```

View active configuration:
```bash
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.jobOrchestrationProperties'
```

---

## See Also

- [Getting Started](getting-started.md) - Setup guide
- [Architecture](architecture.md) - Design overview
- [Examples](examples.md) - Configuration examples

