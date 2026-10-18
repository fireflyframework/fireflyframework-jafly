# Configuration Reference

**Firefly Common Core Library**  
*Copyright (c) 2025 Firefly Software Foundation*  
*Licensed under the Apache License, Version 2.0*

This document provides a complete reference of all configuration options available in Firefly Common Core, organized by component and feature area.

## Table of Contents

- [Messaging Configuration](#messaging-configuration)
- [Cloud Configuration](#cloud-configuration)
- [Service Registry Configuration](#service-registry-configuration)
- [WebClient Configuration](#webclient-configuration)
- [Actuator Configuration](#actuator-configuration)
- [Step Events Configuration](#step-events-configuration)
- [Orchestration Engine Configuration](#orchestration-engine-configuration)
- [Environment-Specific Configuration](#environment-specific-configuration)
- [Security Configuration](#security-configuration)
- [Configuration Validation](#configuration-validation)

## Messaging Configuration

### Core Messaging Properties

```yaml
messaging:
  # Enable/disable messaging features globally
  enabled: true                    # Default: false
  
  # Enable resilience patterns (circuit breaker, retry, metrics)
  resilience: true                 # Default: true
  
  # Default timeout for publishing operations (seconds)
  publish-timeout-seconds: 5       # Default: 5
  
  # Application name for message source identification
  application-name: my-service     # Default: ${spring.application.name}
  
  # Default connection ID when not specified
  default-connection-id: default   # Default: "default"
  
  # Serialization configuration
  serialization:
    default-format: JSON           # Default: JSON
```

### Apache Kafka Configuration

```yaml
messaging:
  kafka:
    # Enable Kafka publisher
    enabled: true                  # Default: false
    
    # Kafka broker addresses (comma-separated)
    bootstrap-servers: localhost:9092  # Default: "localhost:9092"
    
    # Default topic when not specified in annotation
    default-topic: events          # Default: "events"
    
    # Producer client ID
    client-id: messaging-publisher # Default: "messaging-publisher"
    
    # Key serializer class
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    
    # Value serializer class  
    value-serializer: org.apache.kafka.common.serialization.StringSerializer
    
    # Security protocol
    security-protocol: PLAINTEXT   # Options: PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL
    
    # SASL mechanism
    sasl-mechanism: ""             # Options: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI
    
    # SASL credentials
    sasl-username: ""
    sasl-password: ""
    
    # Additional Kafka producer properties
    properties:
      "batch.size": "16384"
      "linger.ms": "5"
      "compression.type": "snappy"
      "acks": "all"
      "retries": "3"
```

### Multiple Kafka Connections

```yaml
messaging:
  kafka-connections:
    primary:
      enabled: true
      bootstrap-servers: kafka-primary:9092
      default-topic: primary-events
      client-id: primary-publisher
      properties:
        "acks": "all"
        "retries": "5"
    
    secondary:
      enabled: true
      bootstrap-servers: kafka-secondary:9092
      default-topic: secondary-events
      client-id: secondary-publisher
      properties:
        "acks": "1"
        "retries": "3"
```

### RabbitMQ Configuration

```yaml
messaging:
  rabbitmq:
    enabled: true                  # Default: false
    
    # Connection details
    host: localhost                # Default: "localhost"
    port: 5672                     # Default: 5672
    virtual-host: "/"              # Default: "/"
    username: guest                # Default: "guest"
    password: guest                # Default: "guest"
    
    # Default routing configuration
    default-exchange: events       # Default: "events"
    default-queue: events          # Default: "events"
    default-routing-key: default   # Default: "default"
    
    # SSL configuration
    ssl: false                     # Default: false
    
    # Connection timeout (milliseconds)
    connection-timeout: 60000      # Default: 60000
    
    # Consumer configuration
    prefetch-count: 10             # Default: 10
    
    # Queue configuration
    durable: true                  # Default: true
    exclusive: false               # Default: false
    auto-delete: false             # Default: false
    
    # Additional properties
    properties:
      "connection.recovery.enabled": "true"
      "topology.recovery.enabled": "true"
```

### AWS SQS Configuration

```yaml
messaging:
  sqs:
    enabled: true                  # Default: false
    
    # AWS region
    region: us-east-1              # Default: "us-east-1"
    
    # Default queue name
    default-queue: events          # Default: "events"
    
    # AWS credentials (optional - can use IAM roles)
    access-key-id: ""              # Default: ""
    secret-access-key: ""          # Default: ""
    session-token: ""              # Default: ""
    
    # Custom endpoint (for LocalStack or custom endpoints)
    endpoint: ""                   # Default: ""
    
    # Message handling configuration
    max-number-of-messages: 10     # Default: 10
    visibility-timeout: 30         # Default: 30 seconds
    wait-time-seconds: 20          # Default: 20 seconds (long polling)
    
    # Additional properties
    properties:
      "message-retention-period": "1209600"  # 14 days
      "max-receive-count": "3"
```

### Google Cloud Pub/Sub Configuration

```yaml
messaging:
  google-pub-sub:
    enabled: true                  # Default: false
    
    # GCP project ID
    project-id: my-gcp-project     # Required
    
    # Default topic
    default-topic: events          # Default: "events"
    
    # Credentials configuration (choose one)
    credentials-path: /path/to/service-account.json
    credentials-json: |            # JSON string
      {
        "type": "service_account",
        "project_id": "my-project"
      }
    
    # Emulator configuration (for development)
    use-emulator: false            # Default: false
    emulator-host: localhost:8085  # Default: ""
    endpoint: ""                   # Custom endpoint override
    
    # Retry configuration
    initial-retry-delay-millis: 100      # Default: 100
    retry-delay-multiplier: 1.3          # Default: 1.3
    max-retry-delay-millis: 60000        # Default: 60000
    max-attempts: 5                      # Default: 5
    
    # Additional properties
    properties:
      "enable-message-ordering": "true"
      "max-extension-period": "300"
```

### Azure Service Bus Configuration

```yaml
messaging:
  azure-service-bus:
    enabled: true                  # Default: false
    
    # Connection configuration (choose one)
    connection-string: "Endpoint=sb://..."  # Full connection string
    
    # Or individual components
    namespace: my-servicebus-namespace
    shared-access-key-name: RootManageSharedAccessKey
    shared-access-key: "..."
    
    # Managed Identity configuration
    use-managed-identity: false    # Default: false
    client-id: ""                  # Default: ""
    
    # Default destinations
    default-topic: events          # Default: "events"
    default-queue: events          # Default: "events"
    
    # Retry configuration
    max-retries: 3                 # Default: 3
    retry-delay-millis: 100        # Default: 100
    max-retry-delay-millis: 30000  # Default: 30000
    retry-delay-multiplier: 1.5    # Default: 1.5
    
    # Additional properties
    properties:
      "enable-sessions": "false"
      "max-delivery-count": "10"
```

### Redis Configuration

```yaml
messaging:
  redis:
    enabled: true                  # Default: false
    
    # Connection details
    host: localhost                # Default: "localhost"
    port: 6379                     # Default: 6379
    password: ""                   # Default: ""
    database: 0                    # Default: 0
    timeout: 2000                  # Default: 2000ms
    
    # SSL configuration
    ssl: false                     # Default: false
    
    # Default channel
    default-channel: events        # Default: "events"
    
    # Redis Sentinel configuration
    sentinel-master: ""            # Default: ""
    sentinel-nodes: ""             # Comma-separated list
    
    # Redis Cluster configuration
    cluster-nodes: ""              # Comma-separated list
    max-redirects: 3               # Default: 3
    
    # Additional properties
    properties:
      "connection-pool-size": "8"
      "connection-minimum-idle-size": "4"
```

### JMS/ActiveMQ Configuration

```yaml
messaging:
  jms:
    enabled: true                  # Default: false
    
    # Default destination
    default-destination: events    # Default: "events"
    
    # Destination type
    use-topic: true                # Default: true (false = queues)
    
    # Broker configuration
    broker-url: tcp://localhost:61616  # Default: "tcp://localhost:61616"
    username: ""                   # Default: ""
    password: ""                   # Default: ""
    client-id: messaging-publisher # Default: "messaging-publisher"
    
    # Connection factory class
    connection-factory-class: org.apache.activemq.ActiveMQConnectionFactory
    
    # Transaction configuration
    transacted: false              # Default: false
    acknowledge-mode: 1            # Default: 1 (AUTO_ACKNOWLEDGE)
    
    # Connection timeout
    connection-timeout: 30000      # Default: 30000ms
    
    # SSL configuration
    ssl: false                     # Default: false
    trust-store-path: ""           # Default: ""
    trust-store-password: ""       # Default: ""
    key-store-path: ""             # Default: ""
    key-store-password: ""         # Default: ""
    
    # Additional properties
    properties:
      "use-async-send": "true"
      "copy-message-on-send": "false"
```

### AWS Kinesis Configuration

```yaml
messaging:
  kinesis:
    enabled: true                  # Default: false
    
    # AWS region
    region: us-east-1              # Default: "us-east-1"
    
    # Default stream
    default-stream: events         # Default: "events"
    
    # AWS credentials (optional - can use IAM roles)
    access-key-id: ""              # Default: ""
    secret-access-key: ""          # Default: ""
    session-token: ""              # Default: ""
    
    # Custom endpoint (for LocalStack)
    endpoint: ""                   # Default: ""
    
    # Consumer configuration
    max-records: 100               # Default: 100
    initial-position: LATEST       # Default: "LATEST"
    initial-timestamp: ""          # ISO-8601 format
    shard-iterator-type: LATEST    # Default: "LATEST"
    
    # KCL configuration
    application-name: messaging-consumer  # Default: "messaging-consumer"
    enhanced-fan-out: false        # Default: false
    consumer-name: messaging-consumer     # Default: "messaging-consumer"
    
    # Retry configuration
    max-retries: 3                 # Default: 3
    retry-delay-millis: 1000       # Default: 1000
    
    # Additional properties
    properties:
      "shard-refresh-interval": "60000"
      "idle-time-between-reads": "1000"
```

## Cloud Configuration

### Spring Cloud Config

```yaml
cloud:
  config:
    # Enable Spring Cloud Config client
    enabled: true                  # Default: false
    
    # Config server URI
    uri: http://localhost:8888     # Default: "http://localhost:8888"
    
    # Application name for config lookup
    name: ""                       # Default: ${spring.application.name}
    
    # Configuration profile
    profile: ""                    # Default: ${spring.profiles.active}
    
    # Configuration label (Git branch/tag)
    label: main                    # Default: "main"
    
    # Fail startup if config server unavailable
    fail-fast: false               # Default: false
    
    # Connection timeout
    timeout-ms: 5000               # Default: 5000
    
    # Retry configuration
    retry: true                    # Default: true
    max-retries: 6                 # Default: 6
    initial-retry-interval-ms: 1000     # Default: 1000
    max-retry-interval-ms: 2000         # Default: 2000
    retry-multiplier: 1.1               # Default: 1.1
    
    # Dynamic refresh configuration
    refresh-enabled: true          # Default: true
```

## Service Registry Configuration

### Eureka Configuration

```yaml
service:
  registry:
    # Enable service registry
    enabled: true                  # Default: false
    
    # Registry type
    type: EUREKA                   # Options: EUREKA, CONSUL
    
    eureka:
      # Eureka server URLs
      service-url: http://localhost:8761/eureka/  # Default
      
      # Registration settings
      register: true               # Default: true
      fetch-registry: true         # Default: true
      
      # Health check settings
      health-check-enabled: true   # Default: true
      health-check-url-path: /actuator/health    # Default
      status-page-url-path: /actuator/info       # Default
      
      # Instance settings
      instance-id: ""              # Default: auto-generated
      prefer-ip-address: false     # Default: false
      
      # Lease settings
      lease-renewal-interval-in-seconds: 30      # Default: 30
      lease-expiration-duration-in-seconds: 90   # Default: 90
      
      # Registry fetch interval
      registry-fetch-interval-seconds: 30        # Default: 30
```

### Consul Configuration

```yaml
service:
  registry:
    enabled: true
    type: CONSUL
    
    consul:
      # Consul connection details
      host: localhost              # Default: "localhost"
      port: 8500                   # Default: 8500
      
      # Service registration
      register: true               # Default: true
      deregister: true             # Default: true
      
      # Service configuration
      service-name: ""             # Default: ${spring.application.name}
      instance-id: ""              # Default: auto-generated
      tags: []                     # Default: empty array
      
      # Health check configuration
      health-check-interval: 10    # Default: 10 seconds
      health-check-timeout: 10     # Default: 10 seconds
      health-check-path: /actuator/health  # Default
      
      # Catalog services watch
      catalog-services-watch: true          # Default: true
      catalog-services-watch-timeout: 55    # Default: 55 seconds
      catalog-services-watch-delay: 1000    # Default: 1000ms
```

## WebClient Configuration

```yaml
webclient:
  # Enable WebClient auto-configuration
  enabled: true                   # Default: true
  
  # Headers to skip during propagation
  skip-headers:
    - authorization
    - cookie
  
  # Timeout configuration
  connect-timeout-ms: 5000        # Default: 5000
  read-timeout-ms: 10000          # Default: 10000
  write-timeout-ms: 10000         # Default: 10000
  
  # Buffer size configuration
  max-in-memory-size: 16777216    # Default: 16MB
  
  # SSL configuration
  ssl:
    enabled: false                # Default: false
    use-default-ssl-context: true # Default: true
    trust-store-path: ""          # Default: ""
    trust-store-password: ""      # Default: ""
    trust-store-type: JKS         # Default: "JKS"
    key-store-path: ""            # Default: ""
    key-store-password: ""        # Default: ""
    key-store-type: JKS           # Default: "JKS"
    verify-hostname: true         # Default: true
    enabled-protocols:            # Default: empty (use system default)
      - TLSv1.3
      - TLSv1.2
    enabled-cipher-suites: []     # Default: empty (use system default)
  
  # Proxy configuration
  proxy:
    enabled: false                # Default: false
    type: HTTP                    # Options: HTTP, SOCKS4, SOCKS5
    host: ""                      # Default: ""
    port: 8080                    # Default: 8080
    username: ""                  # Default: ""
    password: ""                  # Default: ""
    non-proxy-hosts:              # Default: empty
      - localhost
      - "127.0.0.1"
  
  # Connection pool configuration
  connection-pool:
    enabled: true                 # Default: true
    max-connections: 500          # Default: 500
    max-pending-acquires: 1000    # Default: 1000
    max-idle-time-ms: 30000       # Default: 30000
    max-life-time-ms: 60000       # Default: 60000
    metrics-enabled: true         # Default: true
  
  # HTTP/2 configuration
  http2:
    enabled: true                 # Default: true
    max-concurrent-streams: 100   # Default: 100
    initial-window-size: 1048576  # Default: 1MB
  
  # Codec configuration
  codec:
    enabled: false                # Default: false
    max-in-memory-size: 16777216  # Default: 16MB
    enable-logging-form-data: false  # Default: false
    jackson-properties:
      "default-property-inclusion": "NON_NULL"
      "write-dates-as-timestamps": "false"
```

## Actuator Configuration

```yaml
management:
  endpoints:
    enabled: true                 # Default: true
    web:
      exposure: "*"               # Default: "health"
      base-path: /actuator        # Default: "/actuator"
      include-details: true       # Default: false
    jmx:
      exposure: "*"               # Default: "*"
      domain: org.springframework.boot  # Default
  
  # Metrics configuration
  metrics:
    enabled: true                 # Default: true
    tags:
      service: ${spring.application.name}
      environment: ${spring.profiles.active:default}
    
    prometheus:
      enabled: true               # Default: false
      path: /actuator/prometheus  # Default: "/actuator/prometheus"
  
  # Tracing configuration
  tracing:
    enabled: true                 # Default: false
    sampling:
      probability: 0.1            # Default: 0.1 (10%)
    zipkin:
      enabled: false              # Default: false
      base-url: http://localhost:9411  # Default
      service-name: ${spring.application.name}  # Default
    propagation:
      type:                       # Default: ["B3", "W3C"]
        - B3
        - W3C
  
  # Health configuration
  health:
    show-details: always          # Options: never, when-authorized, always
    show-components: true         # Default: false
    disk-space:
      enabled: true               # Default: true
      threshold: 10MB             # Default: 10MB
      path: "."                   # Default: "."

# Extended metrics configuration
actuator:
  extended-metrics:
    jvm:
      enabled: true               # Default: true
      memory: true                # Default: true
      gc: true                    # Default: true
      threads: true               # Default: true
      classloader: true           # Default: true
    
    database:
      enabled: true               # Default: true
      connection-timeout: 5       # Default: 5 seconds
      include-pool-metrics: true  # Default: true
    
    thread-pool:
      enabled: true               # Default: true
      include-executor-services: true     # Default: true
      include-fork-join-pool: true        # Default: true
    
    http-client:
      enabled: true               # Default: true
      connectivity-test: false    # Default: false
      connectivity-timeout: 5     # Default: 5 seconds
    
    cache:
      enabled: true               # Default: true
      operational-test: true      # Default: true
      include-specific-metrics: true      # Default: true
    
    application:
      enabled: true               # Default: true
      startup-phases: true        # Default: true
      enhanced-info: true         # Default: true
```

## Step Events Configuration

```yaml
step-events:
  # Enable step events integration
  enabled: true                   # Default: false
  
  # Publisher type for step events
  publisher-type: KAFKA           # Required when enabled
  
  # Connection ID for the publisher
  connection-id: default          # Default: "default"
  
  # Destination for step events
  event-destination: saga-events  # Required when enabled
  
  # Include step context in events
  include-step-context: true      # Default: true
  
  # Event type prefix
  event-type-prefix: "saga"       # Default: "saga"
  
  # Additional event metadata
  metadata:
    service-name: ${spring.application.name}
    environment: ${spring.profiles.active:default}
```

## Orchestration Engine Configuration

The orchestration engine (`fireflyframework-orchestration`) is the consolidated replacement for the
archived `lib-transactional-engine`. It provides Saga, TCC, and Workflow patterns with its own
auto-configuration. When present on the classpath, `OrchestrationAutoConfiguration` is activated
automatically.

```yaml
firefly:
  orchestration:
    # Enable orchestration engine
    enabled: true                   # Default: true (when on classpath)

    # Saga pattern configuration
    saga:
      default-timeout: 30s         # Default: 30s

    # TCC (Try-Confirm/Cancel) pattern configuration
    tcc:
      default-timeout: 30s         # Default: 30s

    # Workflow pattern configuration
    workflow:
      default-timeout: 60s         # Default: 60s
```

## Environment-Specific Configuration

### Development Environment

```yaml
# application-development.yml
messaging:
  enabled: true
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
  
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.fireflyframework.core: DEBUG
    org.springframework.kafka: INFO
```

### Production Environment

```yaml
# application-production.yml
messaging:
  enabled: true
  resilience: true
  kafka:
    enabled: true
    bootstrap-servers: kafka-cluster:9092
    security-protocol: SASL_SSL
    sasl-mechanism: SCRAM-SHA-256
    properties:
      "acks": "all"
      "retries": "3"
      "batch.size": "65536"
      "compression.type": "lz4"

management:
  endpoints:
    web:
      exposure:
        include: "health,metrics,prometheus,info"
  metrics:
    export:
      prometheus:
        enabled: true

cloud:
  config:
    enabled: true
    uri: https://config-server.prod.company.com
    fail-fast: true

service:
  registry:
    enabled: true
    type: EUREKA
    eureka:
      service-url: https://eureka.prod.company.com/eureka/

logging:
  level:
    org.fireflyframework.core: INFO
    org.springframework.kafka: WARN
```

## Security Configuration

### Authentication Configuration

```yaml
# Kafka SASL/SSL
messaging:
  kafka:
    security-protocol: SASL_SSL
    sasl-mechanism: SCRAM-SHA-256
    sasl-username: ${KAFKA_USERNAME}
    sasl-password: ${KAFKA_PASSWORD}
    properties:
      "ssl.truststore.location": "/etc/ssl/kafka.client.truststore.jks"
      "ssl.truststore.password": "${KAFKA_TRUSTSTORE_PASSWORD}"

# RabbitMQ SSL
  rabbitmq:
    ssl: true
    properties:
      "ssl.enabled": "true"
      "ssl.truststore.location": "/etc/ssl/rabbitmq-truststore.jks"
      "ssl.keystore.location": "/etc/ssl/rabbitmq-keystore.jks"

# WebClient SSL
webclient:
  ssl:
    enabled: true
    trust-store-path: "/etc/ssl/truststore.jks"
    trust-store-password: "${SSL_TRUSTSTORE_PASSWORD}"
    key-store-path: "/etc/ssl/keystore.jks"
    key-store-password: "${SSL_KEYSTORE_PASSWORD}"
    verify-hostname: true

# Actuator security
management:
  endpoints:
    web:
      exposure:
        include: "health,metrics,prometheus"
  endpoint:
    health:
      show-details: when-authorized
```

## Configuration Validation

### Required Properties Validation

The library validates required properties at startup:

```java
@Component
@Validated
public class MessagingConfigValidator {
    
    @EventListener
    public void validateConfiguration(ApplicationReadyEvent event) {
        // Validates that enabled publishers have required configuration
        // Throws ConfigurationException if validation fails
    }
}
```

### Configuration Profile Validation

```yaml
# Validation rules for different profiles
spring:
  profiles:
    include: validation
  
validation:
  messaging:
    required-when-enabled:
      - bootstrap-servers  # for Kafka
      - host               # for RabbitMQ/Redis
      - region             # for AWS services
  
  actuator:
    required-for-production:
      - prometheus.enabled
      - health.show-details
```

### Environment Variables Override

All configuration properties can be overridden with environment variables:

```bash
# Messaging configuration
export MESSAGING_ENABLED=true
export MESSAGING_KAFKA_ENABLED=true
export MESSAGING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Security configuration  
export KAFKA_USERNAME=producer
export KAFKA_PASSWORD=secret
export SSL_TRUSTSTORE_PASSWORD=trustpass

# Cloud configuration
export CLOUD_CONFIG_URI=https://config-server:8888
export SERVICE_REGISTRY_EUREKA_SERVICE_URL=https://eureka:8761/eureka/

# Actuator configuration
export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE="health,metrics,prometheus"
```

### Docker Compose Example

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    image: my-app:latest
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - MESSAGING_ENABLED=true
      - MESSAGING_KAFKA_ENABLED=true
      - MESSAGING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - CLOUD_CONFIG_URI=http://config-server:8888
      - SERVICE_REGISTRY_EUREKA_SERVICE_URL=http://eureka:8761/eureka/
    depends_on:
      - kafka
      - config-server
      - eureka
```

This configuration reference covers all available options in the Firefly Common Core library. For specific use cases, refer to the [Quick Start Guide](QUICKSTART.md) and [Integration Guide](INTEGRATIONS.md).