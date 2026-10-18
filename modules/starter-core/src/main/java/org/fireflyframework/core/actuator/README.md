# Actuator Components

The Firefly Common Core actuator components extend Spring Boot Actuator with enhanced health indicators, custom metrics, and monitoring capabilities for distributed systems.

## Features

### Enhanced Health Indicators
- **Messaging Health**: Health checks for all supported messaging platforms
- **HTTP Client Health**: WebClient connection and endpoint monitoring
- **Custom Health Indicators**: Application-specific health monitoring
- **Aggregated Health**: Composite health status with detailed breakdown

### Custom Metrics
- **HTTP Client Metrics**: Request/response metrics for WebClient operations
- **Messaging Metrics**: Message throughput, latency, and error rates
- **Application Metrics**: Business-specific performance indicators
- **Cache Metrics**: Performance statistics for internal caches

### Configuration Management
- **Dynamic Configuration**: Runtime configuration updates via actuator endpoints
- **Property Validation**: Configuration property validation and defaults
- **Environment Information**: Enhanced environment and configuration details

## Health Indicators

### Messaging Health Indicator
Monitors the health of messaging components across all platforms:

```java
@Component
public class CustomMessagingHealth extends AbstractMessagingHealthIndicator {
    
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // Check Kafka connectivity
        checkKafkaHealth(builder);
        
        // Check Redis connectivity  
        checkRedisHealth(builder);
        
        // Check queue depths
        checkQueueDepths(builder);
    }
}
```

### Platform-Specific Health Indicators

#### Kafka Health Indicator
```java
@ConditionalOnProperty(name = "firefly.messaging.kafka.enabled", havingValue = "true")
@Component
public class KafkaHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // Check cluster connectivity
            AdminClient adminClient = kafkaAdminClient();
            DescribeClusterResult cluster = adminClient.describeCluster();
            
            return Health.up()
                .withDetail("clusterId", cluster.clusterId().get())
                .withDetail("nodeCount", cluster.nodes().get().size())
                .withDetail("controller", cluster.controller().get().idString())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### HTTP Client Health Indicator
Monitors WebClient health and endpoint availability:

```java
@Component
public class WebClientHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        // Check configured endpoints
        for (String endpoint : monitoredEndpoints) {
            try {
                checkEndpointHealth(endpoint, builder);
            } catch (Exception e) {
                builder.down().withDetail(endpoint, "Error: " + e.getMessage());
            }
        }
        
        return builder.build();
    }
}
```

## Configuration

### Actuator Properties
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,env,configprops
      base-path: /actuator
    
  endpoint:
    health:
      show-details: always
      show-components: always
      
  health:
    # Custom health indicator configuration
    messaging:
      enabled: true
    web-client:
      enabled: true
      endpoints:
        - https://api.example.com/health
        - https://payment.service.com/ping

  metrics:
    # Enable specific metric groups
    enable:
      http.client.requests: true
      messaging.throughput: true
      cache.statistics: true
```

### Custom Actuator Configuration
```yaml
firefly:
  actuator:
    # Enhanced metrics configuration
    metrics:
      http-client:
        enabled: true
        include-request-headers: false
        include-response-headers: false
        
    # Health check configuration  
    health:
      messaging:
        timeout: 5s
        retry-attempts: 3
      web-client:
        timeout: 10s
        connection-timeout: 5s
```

## Custom Metrics

### HTTP Client Metrics
Track WebClient performance and reliability:

```java
@Component
public class HttpClientMetricsConfig {
    
    @Bean
    public WebClientCustomizer webClientMetricsCustomizer(MeterRegistry meterRegistry) {
        return webClientBuilder -> webClientBuilder
            .filter(ExchangeFilterFunction.ofRequestProcessor(request -> {
                Timer.Sample sample = Timer.start(meterRegistry);
                request.attribute("timer.sample", sample);
                return Mono.just(request);
            }))
            .filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
                Timer.Sample sample = response.request().attribute("timer.sample").get();
                sample.stop(Timer.builder("http.client.requests")
                    .tag("method", response.request().getMethod().name())
                    .tag("status", String.valueOf(response.statusCode().value()))
                    .register(meterRegistry));
                return Mono.just(response);
            }));
    }
}
```

### Messaging Metrics
Monitor messaging system performance:

```java
@Component
public class MessagingMetrics {
    
    private final Counter messagesProduced;
    private final Counter messagesConsumed;
    private final Timer processingTime;
    
    public MessagingMetrics(MeterRegistry meterRegistry) {
        this.messagesProduced = Counter.builder("messaging.messages.produced")
            .description("Total messages produced")
            .register(meterRegistry);
            
        this.messagesConsumed = Counter.builder("messaging.messages.consumed")
            .description("Total messages consumed")
            .register(meterRegistry);
            
        this.processingTime = Timer.builder("messaging.processing.time")
            .description("Message processing time")
            .register(meterRegistry);
    }
}
```

## Health Check Examples

### Composite Health Indicator
Combine multiple health checks into a single indicator:

```java
@Component
public class ApplicationHealthIndicator implements HealthIndicator {
    
    private final List<HealthIndicator> healthIndicators;
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        boolean allHealthy = true;
        
        for (HealthIndicator indicator : healthIndicators) {
            Health health = indicator.health();
            String name = indicator.getClass().getSimpleName();
            
            builder.withDetail(name, health.getStatus());
            if (health.getStatus() != Status.UP) {
                allHealthy = false;
            }
        }
        
        return allHealthy ? builder.up().build() : builder.down().build();
    }
}
```

### Database Health Indicator
Monitor database connectivity and performance:

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // Perform database health check
            long startTime = System.currentTimeMillis();
            String result = jdbcTemplate.queryForObject("SELECT 1", String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            return Health.up()
                .withDetail("database", "available")
                .withDetail("responseTime", responseTime + "ms")
                .withDetail("query", "SELECT 1")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "unavailable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## Available Endpoints

### Health Endpoints
- `/actuator/health` - Overall application health
- `/actuator/health/messaging` - Messaging system health
- `/actuator/health/web-client` - HTTP client health
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe

### Metrics Endpoints
- `/actuator/metrics` - Available metrics list
- `/actuator/metrics/http.client.requests` - HTTP client request metrics
- `/actuator/metrics/messaging.throughput` - Message processing metrics
- `/actuator/metrics/cache.statistics` - Cache performance metrics
- `/actuator/prometheus` - Prometheus format metrics (if enabled)

### Configuration Endpoints
- `/actuator/env` - Environment properties
- `/actuator/configprops` - Configuration properties
- `/actuator/info` - Application information

## Integration with Monitoring Systems

### Prometheus Integration
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 1m
```

### Grafana Dashboard
Create dashboards using the exported metrics:

```json
{
  "dashboard": {
    "title": "Firefly Common Core Metrics",
    "panels": [
      {
        "title": "HTTP Client Requests",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_client_requests_total[5m])",
            "legendFormat": "{{method}} {{status}}"
          }
        ]
      },
      {
        "title": "Message Processing Rate",
        "type": "graph", 
        "targets": [
          {
            "expr": "rate(messaging_messages_consumed_total[5m])",
            "legendFormat": "Messages/sec"
          }
        ]
      }
    ]
  }
}
```

### AlertManager Rules
Define alerting rules for critical metrics:

```yaml
groups:
  - name: firefly.rules
    rules:
      - alert: HighErrorRate
        expr: rate(http_client_requests_total{status=~"5.."}[5m]) > 0.1
        for: 5m
        annotations:
          summary: "High HTTP error rate detected"
          
      - alert: MessagingDown
        expr: up{job="messaging-health"} == 0
        for: 2m
        annotations:
          summary: "Messaging system is down"
```

## Best Practices

1. **Health Check Timeout**: Set appropriate timeouts for health checks to avoid blocking
2. **Graceful Degradation**: Design health indicators to fail gracefully
3. **Meaningful Details**: Include useful diagnostic information in health responses
4. **Security**: Restrict access to sensitive actuator endpoints
5. **Performance Impact**: Monitor the performance impact of health checks and metrics
6. **Alerting**: Set up proper alerting based on health and metrics data
7. **Documentation**: Document custom metrics and their meanings

## Security Considerations

### Endpoint Security
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
        exclude: env,configprops
      base-path: /private/actuator
      
spring:
  security:
    user:
      name: actuator
      password: ${ACTUATOR_PASSWORD}
      roles: ACTUATOR
```

### Network Security
- Expose actuator endpoints only on management network
- Use firewall rules to restrict access
- Consider using mutual TLS for sensitive endpoints
- Implement proper authentication and authorization