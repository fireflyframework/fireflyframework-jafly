# Configuration Management

The Firefly Common Core configuration management provides auto-configuration for cloud-native applications with support for centralized configuration, service discovery, and reactive web components.

## Features

### Cloud Configuration
- **Spring Cloud Config**: Centralized configuration management
- **Auto-Configuration**: Automatic setup and connection to config server
- **Profile Support**: Environment-specific configuration profiles
- **Refresh Support**: Dynamic configuration updates without restart

### Service Registry
- **Eureka Integration**: Netflix Eureka service discovery
- **Consul Integration**: HashiCorp Consul service discovery  
- **Auto-Registration**: Automatic service registration and discovery
- **Health Checks**: Service health monitoring and reporting

### Web Configuration
- **WebFlux Auto-Configuration**: Reactive web framework setup
- **WebClient Configuration**: Enhanced HTTP client with customization
- **Filter Configuration**: Request/response filtering and processing
- **Resilience Integration**: Circuit breakers, retry, and timeout handling

## Cloud Configuration

### Auto-Configuration
Automatic setup of Spring Cloud Config client:

```java
@ConfigurationProperties(prefix = "firefly.config.cloud")
@Data
public class CloudConfigProperties {
    
    /**
     * Whether cloud config is enabled
     */
    private boolean enabled = true;
    
    /**
     * Config server URI
     */
    private String uri = "http://localhost:8888";
    
    /**
     * Application name for config lookup
     */
    private String name;
    
    /**
     * Configuration profile
     */
    private String profile = "default";
    
    /**
     * Configuration label (branch/tag)
     */
    private String label = "master";
    
    /**
     * Connection timeout
     */
    private Duration timeout = Duration.ofSeconds(10);
    
    /**
     * Enable automatic refresh
     */
    private boolean refreshEnabled = true;
}
```

### Configuration
```yaml
firefly:
  config:
    cloud:
      enabled: true
      uri: http://config-server:8888
      name: ${spring.application.name}
      profile: ${spring.profiles.active:default}
      label: master
      timeout: 10s
      refresh-enabled: true
      
spring:
  cloud:
    config:
      # Additional Spring Cloud Config properties
      retry:
        enabled: true
        max-attempts: 3
      fail-fast: true
```

### Usage Example
```java
@Component
@RefreshScope
public class DynamicConfiguration {
    
    @Value("${app.feature.enabled:false}")
    private boolean featureEnabled;
    
    @Value("${app.database.max-connections:10}")
    private int maxConnections;
    
    // Configuration will be refreshed when config server changes
    public void processRequest() {
        if (featureEnabled) {
            // New feature logic
        }
    }
}
```

## Service Registry

### Eureka Configuration
```java
@ConfigurationProperties(prefix = "firefly.registry.eureka")
@Data
public class ServiceRegistryProperties {
    
    /**
     * Whether service registration is enabled
     */
    private boolean enabled = true;
    
    /**
     * Eureka server URLs
     */
    private List<String> serviceUrl = Arrays.asList("http://localhost:8761/eureka/");
    
    /**
     * Instance hostname
     */
    private String hostname;
    
    /**
     * Health check URL path
     */
    private String healthCheckUrlPath = "/actuator/health";
    
    /**
     * Status page URL path  
     */
    private String statusPageUrlPath = "/actuator/info";
    
    /**
     * Lease renewal interval
     */
    private Duration leaseRenewalInterval = Duration.ofSeconds(30);
}
```

### Service Registry Helper
Utility for service discovery operations:

```java
@Component
public class ServiceRegistryHelper {
    
    private final DiscoveryClient discoveryClient;
    
    /**
     * Get all instances of a service
     */
    public List<ServiceInstance> getServiceInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }
    
    /**
     * Get load-balanced URI for a service
     */
    public URI getServiceUri(String serviceId) {
        List<ServiceInstance> instances = getServiceInstances(serviceId);
        if (instances.isEmpty()) {
            throw new ServiceNotFoundException("No instances found for service: " + serviceId);
        }
        
        // Simple round-robin load balancing
        ServiceInstance instance = instances.get(random.nextInt(instances.size()));
        return instance.getUri();
    }
    
    /**
     * Check if service is available
     */
    public boolean isServiceAvailable(String serviceId) {
        return !getServiceInstances(serviceId).isEmpty();
    }
}
```

### Configuration Examples

#### Eureka Configuration
```yaml
firefly:
  registry:
    eureka:
      enabled: true
      service-url:
        - http://eureka1:8761/eureka/
        - http://eureka2:8761/eureka/
      hostname: ${HOSTNAME:localhost}
      health-check-url-path: /actuator/health
      lease-renewal-interval: 30s

eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: ${firefly.registry.eureka.service-url[0]}
  instance:
    hostname: ${firefly.registry.eureka.hostname}
    health-check-url-path: ${firefly.registry.eureka.health-check-url-path}
```

#### Consul Configuration  
```yaml
firefly:
  registry:
    consul:
      enabled: true
      host: localhost
      port: 8500
      discovery:
        enabled: true
        register: true
        health-check-path: /actuator/health
        health-check-interval: 10s
        
spring:
  cloud:
    consul:
      host: ${firefly.registry.consul.host}
      port: ${firefly.registry.consul.port}
      discovery:
        enabled: ${firefly.registry.consul.discovery.enabled}
        register: ${firefly.registry.consul.discovery.register}
        health-check-path: ${firefly.registry.consul.discovery.health-check-path}
```

## Web Configuration

### WebFlux Auto-Configuration
Reactive web framework setup:

```java
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Configure JSON message codecs with JavaTimeModule
        configurer.defaultCodecs().jackson2JsonEncoder(jsonEncoder());
        configurer.defaultCodecs().jackson2JsonDecoder(jsonDecoder());
    }
}
```

> **Note:** CORS configuration is provided by `fireflyframework-web` via `firefly.web.cors.*` properties.
> See the [fireflyframework-web](https://github.com/fireflyframework/fireflyframework-web) module.

### WebClient Configuration
Enhanced HTTP client with customization:

```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build())
            .defaultHeaders(headers -> {
                headers.add("User-Agent", "Firefly-Common-Core/1.0");
                headers.add("Accept", "application/json");
            });
    }
    
    @Bean
    @LoadBalanced
    public WebClient loadBalancedWebClient(WebClient.Builder builder) {
        return builder.build();
    }
}
```

### WebClient Properties
Configuration for HTTP client behavior:

```java
@ConfigurationProperties(prefix = "firefly.web.client")
@Data
public class WebClientProperties {
    
    /**
     * Connection timeout
     */
    private Duration connectTimeout = Duration.ofSeconds(10);
    
    /**
     * Read timeout
     */
    private Duration readTimeout = Duration.ofSeconds(30);
    
    /**
     * Write timeout
     */
    private Duration writeTimeout = Duration.ofSeconds(30);
    
    /**
     * Maximum connections per route
     */
    private int maxConnectionsPerRoute = 50;
    
    /**
     * Maximum total connections
     */
    private int maxTotalConnections = 200;
    
    /**
     * Enable metrics collection
     */
    private boolean metricsEnabled = true;
    
    /**
     * Enable request/response logging
     */
    private boolean loggingEnabled = false;
}
```

## Filter Configuration

### Custom Web Filters
Request/response processing filters:

```java
@Configuration
public class WebFilterConfig {
    
    @Bean
    @Order(1)
    public WebFilter requestIdFilter() {
        return (exchange, chain) -> {
            String requestId = UUID.randomUUID().toString();
            ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-Request-ID", requestId)
                .build();
                
            return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(Context.of("requestId", requestId));
        };
    }
    
    @Bean
    @Order(2) 
    public WebFilter correlationIdFilter() {
        return (exchange, chain) -> {
            String correlationId = exchange.getRequest().getHeaders()
                .getFirst("X-Correlation-ID");
                
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            return chain.filter(exchange)
                .contextWrite(Context.of("correlationId", correlationId));
        };
    }
    
    @Bean
    @Order(3)
    public WebFilter loggingFilter() {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            
            return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Request {} {} completed in {}ms", 
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getPath(),
                        duration);
                });
        };
    }
}
```

## Integration Examples

### Service-to-Service Communication
```java
@Service
public class OrderService {
    
    private final WebClient webClient;
    private final ServiceRegistryHelper registryHelper;
    
    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return Mono.fromCallable(() -> registryHelper.getServiceUri("payment-service"))
            .flatMap(paymentServiceUri -> 
                webClient.post()
                    .uri(paymentServiceUri.resolve("/api/payments"))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(PaymentResult.class)
            )
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Payment service error: {}", ex.getMessage());
                return Mono.just(PaymentResult.failed("Service unavailable"));
            });
    }
}
```

### Configuration Refresh
```java
@RestController
public class ConfigController {
    
    @Autowired
    private RefreshEndpoint refreshEndpoint;
    
    @PostMapping("/admin/refresh")
    public Mono<Collection<String>> refreshConfiguration() {
        return Mono.fromCallable(() -> refreshEndpoint.refresh())
            .subscribeOn(Schedulers.boundedElastic());
    }
}
```

## Best Practices

1. **Environment-Specific Configs**: Use profiles for different environments
2. **Sensitive Data**: Use encrypted properties for sensitive configuration
3. **Health Checks**: Implement proper health check endpoints for service registry
4. **Circuit Breakers**: Use resilience patterns for service-to-service communication
5. **Configuration Validation**: Validate configuration properties at startup
6. **Monitoring**: Monitor service discovery and configuration refresh events
7. **Security**: Secure configuration endpoints and service registry access

## Troubleshooting

### Common Issues

#### Configuration Server Connection
```yaml
# Increase timeout for slow networks
firefly:
  config:
    cloud:
      timeout: 30s
      
# Enable retry on failure      
spring:
  cloud:
    config:
      retry:
        enabled: true
        max-attempts: 5
        initial-interval: 1000ms
```

#### Service Discovery Issues  
```yaml
# Increase lease renewal for unstable networks
firefly:
  registry:
    eureka:
      lease-renewal-interval: 15s
      
# Enable health checks
eureka:
  client:
    healthcheck:
      enabled: true
```

#### WebClient Timeouts
```yaml
# Adjust timeouts for slow services
firefly:
  web:
    client:
      connect-timeout: 5s
      read-timeout: 60s
      write-timeout: 60s
```

### Debugging Configuration
Enable debug logging for configuration components:

```yaml
logging:
  level:
    org.fireflyframework.core.config: DEBUG
    org.springframework.cloud.config: DEBUG
    org.springframework.cloud.netflix.eureka: DEBUG
    org.springframework.web.reactive: DEBUG
```