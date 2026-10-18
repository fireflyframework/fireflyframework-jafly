# Configuration Reference

Complete reference for all configuration options in the Firefly Common Client Library.

## Table of Contents

- [Overview](#overview)
- [Global Configuration](#global-configuration)
- [REST Configuration](#rest-configuration)
- [gRPC Configuration](#grpc-configuration)
- [SOAP Configuration](#soap-configuration)
- [Circuit Breaker Configuration](#circuit-breaker-configuration)
- [Retry Configuration](#retry-configuration)
- [Metrics Configuration](#metrics-configuration)
- [Security Configuration](#security-configuration)
- [Environment-Specific Configuration](#environment-specific-configuration)

---

## Overview

Configuration can be provided in two ways:

1. **Programmatically** - Using the builder API
2. **Declaratively** - Using `application.yml` or `application.properties`

### Configuration Precedence

1. Programmatic configuration (highest priority)
2. `application.yml` / `application.properties`
3. Default values (lowest priority)

---

## Global Configuration

### application.yml

```yaml
firefly:
  service-client:
    enabled: true                    # Enable/disable the library
    default-timeout: 30s             # Default timeout for all clients
    environment: DEVELOPMENT         # DEVELOPMENT, TESTING, or PRODUCTION
    
    # Global default headers (applied to all clients)
    default-headers:
      User-Agent: "MyApp/1.0"
      X-Client-Version: "1.0.0"
```

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable auto-configuration |
| `default-timeout` | Duration | `30s` | Default timeout for all clients |
| `environment` | String | `DEVELOPMENT` | Environment: DEVELOPMENT, TESTING, PRODUCTION |
| `default-headers` | Map | `{}` | Headers applied to all clients |

---

## REST Configuration

### application.yml

```yaml
firefly:
  service-client:
    rest:
      # Connection Pool
      max-connections: 100           # Maximum connections in pool
      max-idle-time: 5m              # Max idle time for connections
      max-life-time: 30m             # Max lifetime for connections
      pending-acquire-timeout: 10s   # Timeout for acquiring connection
      
      # Timeouts
      response-timeout: 30s          # Response timeout
      connect-timeout: 10s           # Connection timeout
      read-timeout: 30s              # Read timeout
      
      # Features
      compression-enabled: true      # Enable gzip compression
      logging-enabled: false         # Enable request/response logging
      follow-redirects: true         # Follow HTTP redirects
      
      # Limits
      max-in-memory-size: 1048576    # 1MB - Max in-memory buffer size
      max-retries: 3                 # Max retry attempts
      
      # Content Types
      default-content-type: "application/json"
      default-accept-type: "application/json"
```

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `max-connections` | int | `100` | Connection pool size |
| `max-idle-time` | Duration | `5m` | Max idle time for pooled connections |
| `max-life-time` | Duration | `30m` | Max lifetime for pooled connections |
| `pending-acquire-timeout` | Duration | `10s` | Timeout waiting for connection |
| `response-timeout` | Duration | `30s` | How long to wait for response |
| `connect-timeout` | Duration | `10s` | How long to wait for connection |
| `read-timeout` | Duration | `30s` | How long to wait reading response |
| `compression-enabled` | boolean | `true` | Enable gzip compression |
| `logging-enabled` | boolean | `false` | Log requests/responses |
| `follow-redirects` | boolean | `true` | Follow HTTP redirects |
| `max-in-memory-size` | int | `1048576` | Max buffer size (bytes) |
| `max-retries` | int | `3` | Max retry attempts |
| `default-content-type` | String | `application/json` | Default Content-Type header |
| `default-accept-type` | String | `application/json` | Default Accept header |

### Programmatic Configuration

```java
RestClient client = ServiceClient.rest("my-service")
    .baseUrl("http://localhost:8080")
    .timeout(Duration.ofSeconds(30))
    .maxConnections(100)
    .defaultHeader("X-API-Key", "key")
    .jsonContentType()
    .build();
```

---

## gRPC Configuration

### application.yml

```yaml
firefly:
  service-client:
    grpc:
      # Keep-Alive
      keep-alive-time: 5m            # Send keep-alive ping interval
      keep-alive-timeout: 30s        # Keep-alive ping timeout
      keep-alive-without-calls: true # Keep-alive even without calls
      
      # Message Limits
      max-inbound-message-size: 4194304   # 4MB - Max inbound message
      max-inbound-metadata-size: 8192     # 8KB - Max metadata size
      
      # Timeouts
      call-timeout: 30s              # Default call timeout
      
      # Features
      retry-enabled: true            # Enable retry
      use-plaintext-by-default: true # Use plaintext (dev only!)
      compression-enabled: true      # Enable compression
      
      # Concurrency
      max-concurrent-streams: 100    # Max concurrent streams
```

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `keep-alive-time` | Duration | `5m` | Keep-alive ping interval |
| `keep-alive-timeout` | Duration | `30s` | Keep-alive response timeout |
| `keep-alive-without-calls` | boolean | `true` | Keep-alive without active calls |
| `max-inbound-message-size` | int | `4194304` | Max message size (4MB) |
| `max-inbound-metadata-size` | int | `8192` | Max metadata size (8KB) |
| `call-timeout` | Duration | `30s` | Default call timeout |
| `retry-enabled` | boolean | `true` | Enable retry logic |
| `use-plaintext-by-default` | boolean | `true` | Use plaintext (dev only!) |
| `compression-enabled` | boolean | `true` | Enable compression |
| `max-concurrent-streams` | int | `100` | Max concurrent streams |

### Programmatic Configuration

```java
GrpcClient<UserServiceStub> client = ServiceClient.grpc("user-service", UserServiceStub.class)
    .address("localhost:9090")
    .timeout(Duration.ofSeconds(30))
    .usePlaintext()  // or .useTransportSecurity()
    .stubFactory(channel -> UserServiceGrpc.newStub(channel))
    .build();
```

---

## SOAP Configuration

### application.yml

```yaml
firefly:
  service-client:
    soap:
      # Timeouts
      default-timeout: 30s           # Default request timeout
      connection-timeout: 10s        # Connection timeout
      receive-timeout: 30s           # Receive timeout
      
      # Features
      mtom-enabled: false            # Enable MTOM for attachments
      schema-validation-enabled: true # Validate XML against schema
      message-logging-enabled: false  # Log SOAP messages (dev only)
      ws-addressing-enabled: false   # Enable WS-Addressing
      ws-security-enabled: false     # Enable WS-Security
      
      # Limits
      max-message-size: 10485760     # 10MB - Max message size
      
      # SOAP Version
      soap-version: "1.1"            # 1.1 or 1.2
      
      # Caching
      wsdl-cache-enabled: true       # Cache WSDL
      wsdl-cache-expiration: 1h      # WSDL cache expiration
      
      # HTTP
      follow-redirects: true         # Follow HTTP redirects
```

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `default-timeout` | Duration | `30s` | Default request timeout |
| `connection-timeout` | Duration | `10s` | Connection timeout |
| `receive-timeout` | Duration | `30s` | Receive timeout |
| `mtom-enabled` | boolean | `false` | Enable MTOM for attachments |
| `schema-validation-enabled` | boolean | `true` | Validate XML against schema |
| `message-logging-enabled` | boolean | `false` | Log SOAP messages |
| `ws-addressing-enabled` | boolean | `false` | Enable WS-Addressing |
| `ws-security-enabled` | boolean | `false` | Enable WS-Security |
| `max-message-size` | int | `10485760` | Max message size (10MB) |
| `soap-version` | String | `1.1` | SOAP version (1.1 or 1.2) |
| `wsdl-cache-enabled` | boolean | `true` | Cache WSDL |
| `wsdl-cache-expiration` | Duration | `1h` | WSDL cache expiration |
| `follow-redirects` | boolean | `true` | Follow HTTP redirects |

### Programmatic Configuration

```java
SoapClient client = ServiceClient.soap("payment-service")
    .wsdlUrl("https://payment.example.com/service?WSDL")
    .credentials("username", "password")
    .timeout(Duration.ofSeconds(60))
    .enableMtom()
    .enableSchemaValidation()
    .header("X-API-Key", "key")
    .build();
```

---

## Circuit Breaker Configuration

### application.yml

```yaml
firefly:
  service-client:
    circuit-breaker:
      enabled: true                  # Enable circuit breaker
      
      # Thresholds
      failure-rate-threshold: 50.0   # Open after 50% failures
      minimum-number-of-calls: 5     # Min calls before evaluation
      
      # Sliding Window
      sliding-window-size: 10        # Window size for metrics
      
      # State Transitions
      wait-duration-in-open-state: 60s                    # Wait before half-open
      permitted-number-of-calls-in-half-open-state: 3     # Calls in half-open
      max-wait-duration-in-half-open-state: 30s           # Max time in half-open
      automatic-transition-from-open-to-half-open-enabled: true
      
      # Timeouts
      call-timeout: 10s              # Call timeout
      
      # Slow Calls
      slow-call-duration-threshold: 5s    # Threshold for slow call
      slow-call-rate-threshold: 100.0     # Slow call rate threshold
```

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable circuit breaker |
| `failure-rate-threshold` | double | `50.0` | Failure rate to open (%) |
| `minimum-number-of-calls` | int | `5` | Min calls before evaluation |
| `sliding-window-size` | int | `10` | Sliding window size |
| `wait-duration-in-open-state` | Duration | `60s` | Wait before half-open |
| `permitted-number-of-calls-in-half-open-state` | int | `3` | Calls in half-open state |
| `max-wait-duration-in-half-open-state` | Duration | `30s` | Max time in half-open |
| `automatic-transition-from-open-to-half-open-enabled` | boolean | `true` | Auto transition |
| `call-timeout` | Duration | `10s` | Call timeout |
| `slow-call-duration-threshold` | Duration | `5s` | Slow call threshold |
| `slow-call-rate-threshold` | double | `100.0` | Slow call rate (%) |

---

## Retry Configuration

### application.yml

```yaml
firefly:
  service-client:
    retry:
      enabled: true                  # Enable retry
      max-attempts: 3                # Max retry attempts
      initial-interval: 1s           # Initial retry interval
      multiplier: 2.0                # Backoff multiplier
      max-interval: 30s              # Max retry interval
```

### Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable retry |
| `max-attempts` | int | `3` | Max retry attempts |
| `initial-interval` | Duration | `1s` | Initial retry delay |
| `multiplier` | double | `2.0` | Backoff multiplier |
| `max-interval` | Duration | `30s` | Max retry delay |

---

## Metrics Configuration

### application.yml

```yaml
firefly:
  service-client:
    metrics:
      enabled: true                  # Enable metrics
      collect-detailed-metrics: false # Collect detailed metrics
      histogram-buckets: [0.001, 0.01, 0.1, 1, 10]  # Histogram buckets
```

---

## Security Configuration

### application.yml

```yaml
firefly:
  service-client:
    security:
      tls-enabled: false             # Enable TLS
      trust-store-path: ""           # Trust store path
      trust-store-password: ""       # Trust store password
      key-store-path: ""             # Key store path
      key-store-password: ""         # Key store password
```

---

## Environment-Specific Configuration

### Development

```yaml
firefly:
  service-client:
    environment: DEVELOPMENT
    rest:
      logging-enabled: true          # Enable logging for debugging
      max-connections: 50            # Fewer connections
    grpc:
      use-plaintext-by-default: true # No TLS in dev
    circuit-breaker:
      minimum-number-of-calls: 2     # Faster circuit breaker
```

### Testing

```yaml
firefly:
  service-client:
    environment: TESTING
    default-timeout: 10s             # Faster timeouts
    rest:
      max-connections: 20            # Minimal connections
    circuit-breaker:
      minimum-number-of-calls: 2
      wait-duration-in-open-state: 10s
```

### Production

```yaml
firefly:
  service-client:
    environment: PRODUCTION
    rest:
      logging-enabled: false         # No logging overhead
      max-connections: 200           # More connections
      compression-enabled: true
    grpc:
      use-plaintext-by-default: false # Always use TLS
    circuit-breaker:
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 10
```

---

**Next Steps**:
- [REST Client Guide](REST_CLIENT.md)
- [gRPC Client Guide](GRPC_CLIENT.md)
- [SOAP Client Guide](SOAP_CLIENT.md)

