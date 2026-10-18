# Observability & Monitoring Guide

This guide covers the comprehensive observability and monitoring features of the Firefly Common Client Library, including metrics, tracing, health checks, and logging.

## Table of Contents

- [Overview](#overview)
- [Metrics with Micrometer](#metrics-with-micrometer)
- [Distributed Tracing](#distributed-tracing)
- [Health Checks](#health-checks)
- [Request/Response Logging](#requestresponse-logging)
- [Performance Metrics](#performance-metrics)
- [Spring Boot Actuator Integration](#spring-boot-actuator-integration)
- [Prometheus & Grafana](#prometheus--grafana)
- [Best Practices](#best-practices)

---

## Overview

The library provides enterprise-grade observability features out of the box:

| Feature | Technology | Description |
|---------|-----------|-------------|
| **Metrics** | Micrometer | Request counts, durations, error rates, circuit breaker states |
| **Tracing** | OpenTelemetry/Zipkin | Distributed tracing across microservices |
| **Health Checks** | Spring Boot Actuator | Service health monitoring and diagnostics |
| **Logging** | SLF4J/Logback | Structured logging with correlation IDs |
| **Performance** | Custom Collectors | Throughput, latency percentiles, cache metrics |

---

## Metrics with Micrometer

### Available Metrics

The library automatically collects the following metrics:

#### Request Metrics

```
service.client.requests.success{service, client.type}
  - Total number of successful requests (2xx status codes)
  - Tags: service name, client type (REST/gRPC/SOAP)

service.client.requests.failure{service, client.type}
  - Total number of failed requests (4xx/5xx status codes)
  - Tags: service name, client type

service.client.requests.duration{service, client.type}
  - Request duration timer with percentiles (p50, p95, p99)
  - Tags: service name, client type
```

#### Circuit Breaker Metrics

```
service.client.circuit.breaker.state{service, client.type}
  - Current circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
  - Tags: service name, client type

service.client.circuit.breaker.transitions{service, from.state, to.state}
  - Circuit breaker state transition counter
  - Tags: service name, from state, to state
```

#### Error Metrics

```
service.client.errors{service, client.type, error.type}
  - Error count by type
  - Tags: service name, client type, error type
```

#### Performance Metrics

```
service.client.performance.requests.total{service}
  - Total requests counter

service.client.performance.requests.success{service}
  - Successful requests (2xx)

service.client.performance.requests.client.errors{service}
  - Client errors (4xx)

service.client.performance.requests.server.errors{service}
  - Server errors (5xx)

service.client.performance.retries{service}
  - Retry attempts counter

service.client.performance.timeouts{service}
  - Timeout counter

service.client.performance.request.duration{service}
  - Request duration with percentiles

service.client.performance.response.size{service}
  - Response size distribution in bytes

service.client.performance.concurrent.requests{service}
  - Current concurrent requests gauge

service.client.performance.cache.hits{service, cache.type}
  - Cache hit counter

service.client.performance.cache.misses{service, cache.type}
  - Cache miss counter

service.client.performance.connection.pool.active{service}
  - Active connections in pool

service.client.performance.connection.pool.idle{service}
  - Idle connections in pool
```

### Configuration

Enable metrics in your `application.yml`:

```yaml
management:
  metrics:
    enable:
      service.client: true
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        service.client.requests.duration: true
        service.client.performance.request.duration: true
```

### Programmatic Access

```java
@Autowired
private ServiceClientMetrics metrics;

// Get total requests
long totalRequests = metrics.getTotalRequests("user-service");

// Get success rate
double successRate = metrics.getSuccessRate("user-service");

// Get circuit breaker state
CircuitBreakerState state = metrics.getCircuitBreakerState("user-service");
```

---

## Distributed Tracing

### OpenTelemetry Integration

The library supports both Brave (Zipkin) and OpenTelemetry for distributed tracing.

#### Configuration

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% of requests (adjust for production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

#### Automatic Trace Propagation

Traces are automatically propagated across service boundaries:

```java
// Trace context is automatically propagated
Mono<User> user = restClient.get("/users/{id}", User.class)
    .pathVariable("id", "123")
    .execute();
// Trace ID and Span ID are included in headers
```

#### Custom Spans

Add custom spans for detailed tracing:

```java
@Autowired
private Tracer tracer;

public Mono<Result> complexOperation() {
    Span span = tracer.nextSpan().name("complex-operation").start();
    
    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
        return performOperation()
            .doOnSuccess(result -> span.tag("result.size", String.valueOf(result.size())))
            .doOnError(error -> span.error(error))
            .doFinally(signal -> span.end());
    }
}
```

### Trace Attributes

The library automatically adds the following attributes to traces:

- `service.name` - Target service name
- `http.method` - HTTP method
- `http.url` - Request URL
- `http.status_code` - Response status code
- `client.type` - Client type (REST/gRPC/SOAP)
- `circuit.breaker.state` - Circuit breaker state
- `retry.attempt` - Retry attempt number (if applicable)

---

## Health Checks

### Spring Boot Actuator Integration

The library provides a custom health indicator that integrates with Spring Boot Actuator.

#### Configuration

```yaml
management:
  health:
    service-clients:
      enabled: true
    defaults:
      enabled: true
  endpoint:
    health:
      show-details: always
      show-components: always
```

#### Health Endpoint Response

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "serviceClients": {
      "status": "UP",
      "details": {
        "overallState": "HEALTHY",
        "totalServices": 3,
        "healthyServices": 3,
        "degradedServices": 0,
        "unhealthyServices": 0,
        "services": {
          "user-service": {
            "state": "HEALTHY",
            "consecutiveFailures": 0,
            "lastCheckTime": "2025-10-25T10:30:00Z",
            "timeSinceLastCheck": "5000ms",
            "message": "Health check successful"
          },
          "payment-service": {
            "state": "HEALTHY",
            "consecutiveFailures": 0,
            "lastCheckTime": "2025-10-25T10:30:00Z",
            "timeSinceLastCheck": "5000ms",
            "message": "Health check successful"
          }
        }
      }
    }
  }
}
```

#### Programmatic Health Checks

```java
@Autowired
private ServiceClientHealthManager healthManager;

// Register a client for health monitoring
healthManager.registerClient(serviceClient);

// Start health monitoring
healthManager.start();

// Get health status
ServiceClientHealthStatus status = healthManager.getHealthStatus("user-service");
if (status.isHealthy()) {
    log.info("Service is healthy");
}

// Get overall health state
OverallHealthState overallState = healthManager.getOverallHealthState();
```

### Custom Health Checks

Implement custom health check logic:

```java
@Component
public class CustomServiceHealthCheck {
    
    @Autowired
    private ServiceClient serviceClient;
    
    @Scheduled(fixedRate = 30000)
    public void performHealthCheck() {
        serviceClient.healthCheck()
            .doOnSuccess(v -> log.info("Health check passed"))
            .doOnError(error -> log.error("Health check failed", error))
            .subscribe();
    }
}
```

---

## Request/Response Logging

### Advanced Logging Interceptor

The library provides a comprehensive logging interceptor with configurable detail levels.

#### Configuration

```java
RequestResponseLoggingInterceptor loggingInterceptor = 
    RequestResponseLoggingInterceptor.builder()
        .logLevel(LogLevel.HEADERS)
        .maskSensitiveHeaders(true)
        .maxBodyLogSize(1024)
        .includeTimings(true)
        .includeStatistics(true)
        .build();

WebClient client = WebClient.builder()
    .filter(loggingInterceptor.filter())
    .build();
```

#### Log Levels

| Level | Description | Logs |
|-------|-------------|------|
| `NONE` | No logging | Nothing |
| `BASIC` | Basic info | Method, URL, status code |
| `HEADERS` | Include headers | Basic + request/response headers |
| `FULL` | Everything | Headers + request/response bodies |

#### Example Log Output

```
[Request #1234] GET http://user-service/users/123
  Headers: {Authorization=[***MASKED***], Content-Type=[application/json]}

[Response #1234] GET http://user-service/users/123 -> 200 OK [150ms]
  Headers: {Content-Type=[application/json], Content-Length=[256]}
```

#### Sensitive Header Masking

By default, the following headers are masked:
- `Authorization`
- `X-API-Key`
- `X-Auth-Token`
- `Cookie`
- `Set-Cookie`
- `Proxy-Authorization`

Add custom sensitive headers:

```java
Set<String> customSensitiveHeaders = Set.of("X-Custom-Secret", "X-Internal-Token");

RequestResponseLoggingInterceptor interceptor = 
    RequestResponseLoggingInterceptor.builder()
        .maskSensitiveHeaders(true)
        .additionalSensitiveHeaders(customSensitiveHeaders)
        .build();
```

#### Request Statistics

Get statistics from the logging interceptor:

```java
Map<String, RequestStatistics> stats = loggingInterceptor.getStatistics();

stats.forEach((endpoint, statistics) -> {
    log.info("Endpoint: {} - Total: {}, Success Rate: {:.2f}%, Avg Duration: {:.2f}ms",
        endpoint,
        statistics.getTotalRequests(),
        statistics.getSuccessRate(),
        statistics.getAverageDurationMs());
});
```

---

## Performance Metrics

### Performance Metrics Collector

The `PerformanceMetricsCollector` provides advanced performance tracking.

#### Usage

```java
@Autowired
private MeterRegistry meterRegistry;

PerformanceMetricsCollector collector = new PerformanceMetricsCollector(meterRegistry);

// Record a request
collector.recordRequest("user-service", "GET", "/users/123",
    Duration.ofMillis(150), 200, 1024);

// Record retry
collector.recordRetry("user-service", 2);

// Record timeout
collector.recordTimeout("user-service");

// Record cache hit/miss
collector.recordCacheHit("user-service", "query");
collector.recordCacheMiss("user-service", "query");

// Get summary
PerformanceMetricsSummary summary = collector.getSummary("user-service");
log.info("Service: {}, RPS: {:.2f}, Success Rate: {:.2f}%, Avg Duration: {:.2f}ms",
    summary.getServiceName(),
    summary.getRequestsPerSecond(),
    summary.getSuccessRate(),
    summary.getAverageDurationMs());
```

#### Metrics Summary

```java
public class PerformanceMetricsSummary {
    private String serviceName;
    private long totalRequests;
    private long successfulRequests;
    private long clientErrors;
    private long serverErrors;
    private long retries;
    private long timeouts;
    private double averageDurationMs;
    private double maxDurationMs;
    private double requestsPerSecond;
    private double successRate;
    private double errorRate;
}
```

---

## Spring Boot Actuator Integration

### Actuator Endpoints

The library integrates with Spring Boot Actuator to expose the following endpoints:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health status including service clients |
| `/actuator/metrics` | All Micrometer metrics |
| `/actuator/prometheus` | Prometheus-formatted metrics |
| `/actuator/info` | Application information |

### Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
      base-path: /actuator
  endpoint:
    health:
      show-details: always
      show-components: always
    metrics:
      enabled: true
    prometheus:
      enabled: true
```

### Custom Actuator Endpoint

Create a custom endpoint for service client statistics:

```java
@Component
@Endpoint(id = "service-clients")
public class ServiceClientsEndpoint {
    
    @Autowired
    private ServiceClientMetrics metrics;
    
    @ReadOperation
    public Map<String, Object> serviceClients() {
        Map<String, Object> result = new HashMap<>();
        // Add custom statistics
        return result;
    }
}
```

---

## Prometheus & Grafana

### Prometheus Configuration

Add the library metrics to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### Grafana Dashboard

Import the provided Grafana dashboard or create custom panels:

#### Request Rate Panel

```promql
rate(service_client_requests_success_total[5m])
```

#### Error Rate Panel

```promql
rate(service_client_requests_failure_total[5m]) / 
rate(service_client_requests_success_total[5m])
```

#### Request Duration (p95)

```promql
histogram_quantile(0.95, 
  rate(service_client_requests_duration_bucket[5m]))
```

#### Circuit Breaker State

```promql
service_client_circuit_breaker_state
```

---

## Best Practices

### 1. ✅ Enable Metrics in Production

```yaml
management:
  metrics:
    enable:
      service.client: true
```

### 2. ✅ Use Appropriate Sampling Rates

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling in production
```

### 3. ✅ Configure Health Check Intervals

```java
ServiceClientHealthManager healthManager = new ServiceClientHealthManager(
    Duration.ofSeconds(30),  // Check every 30 seconds
    Duration.ofSeconds(5),   // Timeout after 5 seconds
    3                        // Mark unhealthy after 3 failures
);
```

### 4. ✅ Use Structured Logging

```java
log.info("Request completed: service={}, endpoint={}, duration={}ms, status={}",
    serviceName, endpoint, duration.toMillis(), statusCode);
```

### 5. ✅ Monitor Circuit Breaker States

Set up alerts for circuit breaker state changes:

```promql
changes(service_client_circuit_breaker_state[5m]) > 0
```

### 6. ✅ Track Error Rates

Alert on high error rates:

```promql
rate(service_client_requests_failure_total[5m]) > 0.05
```

### 7. ✅ Monitor Request Latency

Alert on high p95 latency:

```promql
histogram_quantile(0.95, 
  rate(service_client_requests_duration_bucket[5m])) > 1000
```

---

## Related Documentation

- [REST Client Guide](REST_CLIENT.md)
- [gRPC Client Guide](GRPC_CLIENT.md)
- [SOAP Client Guide](SOAP_CLIENT.md)
- [Circuit Breaker Guide](CIRCUIT_BREAKER.md)
- [Integration Testing Guide](INTEGRATION_TESTING.md)

