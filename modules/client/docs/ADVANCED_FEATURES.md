# Advanced Features Guide

This guide covers the advanced features of the Firefly Common Client Library.

## Table of Contents

1. [Chaos Engineering & Fault Injection](#chaos-engineering--fault-injection)
2. [Request/Response Caching](#requestresponse-caching)
3. [Service Discovery](#service-discovery)
4. [Request Deduplication](#request-deduplication)
5. [Advanced Load Balancing](#advanced-load-balancing)
6. [Webhook Client](#webhook-client)
7. [Plugin System](#plugin-system)

---

## Chaos Engineering & Fault Injection

Test your system's resilience by injecting controlled faults.

### Features

- **Latency Injection** - Add artificial delays to requests
- **Error Injection** - Throw exceptions or return error responses
- **Timeout Injection** - Force requests to timeout
- **Network Failure Simulation** - Simulate connection failures
- **Response Corruption** - Corrupt response data

### Example Usage

```java
// Configure fault injection
FaultInjectionConfig config = FaultInjectionConfig.builder()
    .enabled(true)
    .latencyInjectionEnabled(true)
    .latencyProbability(0.2) // 20% of requests
    .minLatency(Duration.ofMillis(100))
    .maxLatency(Duration.ofSeconds(2))
    .errorInjectionEnabled(true)
    .errorProbability(0.1) // 10% of requests
    .build();

// Create chaos interceptor
ChaosEngineeringInterceptor chaosInterceptor = new ChaosEngineeringInterceptor(config);

// Add to client
RestClient client = ServiceClient.rest("user-service")
    .baseUrl("http://localhost:8080")
    .interceptor(chaosInterceptor)
    .build();

// Get statistics
ChaosStatistics stats = chaosInterceptor.getStatistics();
System.out.println("Total injections: " + stats.getTotalInjections());
System.out.println("Latency injection rate: " + stats.getLatencyInjectionRate());
```

### Pre-configured Scenarios

```java
// Test latency resilience
FaultInjectionConfig latencyTest = FaultInjectionConfig.latencyTesting();

// Test error resilience
FaultInjectionConfig errorTest = FaultInjectionConfig.errorTesting();
```

---

## Request/Response Caching

HTTP caching with ETag support, Cache-Control directives, and TTL-based expiration.

### Features

- **ETag-based validation** (If-None-Match)
- **Last-Modified validation** (If-Modified-Since)
- **Cache-Control directives** (max-age, no-cache, no-store)
- **TTL-based expiration**
- **Cache warming and invalidation**
- **Conditional requests** (304 Not Modified)

### Example Usage

```java
// Configure caching
HttpCacheConfig cacheConfig = HttpCacheConfig.builder()
    .enabled(true)
    .defaultTtl(Duration.ofMinutes(5))
    .maxCacheSize(1000)
    .respectCacheControl(true)
    .cacheGetOnly(true)
    .build();

// Create cache manager
HttpCacheManager cacheManager = new HttpCacheManager(cacheConfig);

// Use with interceptor
HttpCacheInterceptor cacheInterceptor = new HttpCacheInterceptor(cacheManager, cacheConfig);

RestClient client = ServiceClient.rest("user-service")
    .baseUrl("http://localhost:8080")
    .interceptor(cacheInterceptor)
    .build();

// Cache statistics
CacheStatistics stats = cacheManager.getStatistics();
System.out.println("Hit rate: " + stats.hitRate());
System.out.println("Cache size: " + stats.size());

// Manual cache operations
cacheManager.invalidate("user-service:GET:/users/123");
cacheManager.invalidateService("user-service");
cacheManager.clear();
```

### Pre-configured Strategies

```java
// Aggressive caching (15 min TTL, 5000 entries)
HttpCacheConfig aggressive = HttpCacheConfig.aggressive();

// Conservative caching (1 min TTL, 500 entries)
HttpCacheConfig conservative = HttpCacheConfig.conservative();
```

---

## Service Discovery

Dynamic endpoint resolution with support for Kubernetes, Eureka, Consul, and static configuration.

### Features

- **Kubernetes Service Discovery** - DNS-based discovery
- **Eureka Integration** - Netflix OSS service registry
- **Consul Integration** - HashiCorp Consul
- **Static Configuration** - Manual endpoint configuration
- **Health-based routing** - Only route to healthy instances

### Example Usage

```java
// Kubernetes discovery
ServiceDiscoveryClient k8sDiscovery = ServiceDiscoveryClient.kubernetes("default");
String endpoint = k8sDiscovery.resolveEndpoint("user-service").block();

// Eureka discovery
ServiceDiscoveryClient eurekaDiscovery = ServiceDiscoveryClient.eureka("http://eureka:8761");
List<ServiceInstance> instances = eurekaDiscovery.getInstances("user-service").collectList().block();

// Consul discovery
ServiceDiscoveryClient consulDiscovery = ServiceDiscoveryClient.consul("http://consul:8500");
ServiceInstance healthyInstance = consulDiscovery.getHealthyInstance("user-service").block();

// Static configuration
Map<String, List<String>> endpoints = Map.of(
    "user-service", List.of("http://localhost:8080", "http://localhost:8081"),
    "payment-service", List.of("http://localhost:9090")
);
ServiceDiscoveryClient staticDiscovery = ServiceDiscoveryClient.staticConfig(endpoints);
```

---

## Request Deduplication

Prevent duplicate operations with idempotency keys and request fingerprinting.

### Features

- **Idempotency key generation**
- **Request fingerprinting** (SHA-256)
- **In-flight request tracking**
- **Automatic deduplication**
- **TTL-based cleanup**

### Example Usage

```java
// Create deduplication manager
RequestDeduplicationManager dedup = new RequestDeduplicationManager(Duration.ofMinutes(5));

// Generate idempotency key
String key = dedup.generateIdempotencyKey();

// Execute with deduplication
Mono<Response> response = dedup.executeWithDeduplication(
    key,
    performOperation()
);

// Check if duplicate
boolean isDuplicate = dedup.isDuplicate(key);

// Get completed result
Optional<Response> cachedResult = dedup.getCompletedResult(key);

// Statistics
DeduplicationStatistics stats = dedup.getStatistics();
System.out.println("In-flight: " + stats.inFlightRequests());
System.out.println("Completed: " + stats.completedRequests());
```

---

## Advanced Load Balancing

Client-side load balancing with multiple strategies.

### Strategies

- **Round Robin** - Distributes requests evenly
- **Weighted Round Robin** - Distributes based on weights
- **Random** - Selects random instance
- **Least Connections** - Selects instance with fewest active connections
- **Sticky Session** - Routes to same instance based on session ID
- **Zone-Aware** - Prefers instances in same zone

### Example Usage

```java
// Round Robin
LoadBalancerStrategy roundRobin = new LoadBalancerStrategy.RoundRobin();
Optional<ServiceInstance> instance = roundRobin.selectInstance(instances);

// Weighted Round Robin
Map<String, Integer> weights = Map.of(
    "instance-1", 3,  // 3x weight
    "instance-2", 1   // 1x weight
);
LoadBalancerStrategy weighted = new LoadBalancerStrategy.WeightedRoundRobin(weights);

// Least Connections
LoadBalancerStrategy.LeastConnections leastConn = new LoadBalancerStrategy.LeastConnections();
Optional<ServiceInstance> selected = leastConn.selectInstance(instances);
leastConn.incrementConnections(selected.get().instanceId());

// Sticky Session
LoadBalancerStrategy.StickySession sticky = new LoadBalancerStrategy.StickySession(
    new LoadBalancerStrategy.RoundRobin()
);
Optional<ServiceInstance> sessionInstance = sticky.selectInstanceWithSession("session-123", instances);

// Zone-Aware
LoadBalancerStrategy zoneAware = new LoadBalancerStrategy.ZoneAware(
    "us-east-1a",
    new LoadBalancerStrategy.RoundRobin()
);
```

---

## Webhook Client

Event-driven integrations with webhook support.

### Features

- **Webhook subscription management**
- **Signature verification** (HMAC-SHA256)
- **Automatic retry** for webhook delivery
- **Event filtering and routing**
- **Webhook health monitoring**

### Example Usage

```java
// Configure webhook client
WebhookConfig config = WebhookConfig.builder()
    .secret("webhook-secret")
    .signatureHeader("X-Webhook-Signature")
    .retryEnabled(true)
    .maxRetries(3)
    .timeout(Duration.ofSeconds(30))
    .build();

WebhookClientHelper webhook = new WebhookClientHelper(
    "https://api.example.com/webhooks",
    config
);

// Subscribe to events
webhook.subscribe("user.created", event -> {
    System.out.println("User created: " + event.getData());
});

webhook.subscribe("user.updated", event -> {
    System.out.println("User updated: " + event.getData());
});

// Verify webhook signature
boolean valid = webhook.verifySignature(payload, signature);

// Send webhook
WebhookEvent event = new WebhookEvent(
    UUID.randomUUID().toString(),
    "user.created",
    userData,
    System.currentTimeMillis()
);
webhook.sendWebhook(event).block();
```

---

## Plugin System

Extend the framework with custom plugins using the Service Provider Interface (SPI).

### Features

- **Plugin discovery** via ServiceLoader
- **Lifecycle management** (initialize, beforeRequest, afterResponse, shutdown)
- **Priority-based execution**
- **Enable/disable plugins**
- **Plugin statistics**

### Creating a Plugin

```java
public class CustomMetricsPlugin implements ServiceClientPlugin {
    
    @Override
    public String getName() {
        return "custom-metrics";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public void initialize(ServiceClient client) {
        System.out.println("Initializing custom metrics for: " + client.getServiceName());
    }
    
    @Override
    public Mono<Void> beforeRequest(RequestContext context) {
        context.setAttribute("startTime", System.currentTimeMillis());
        return Mono.empty();
    }
    
    @Override
    public Mono<Void> afterResponse(ResponseContext context) {
        long startTime = (long) context.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime();
        System.out.println("Request took: " + duration + "ms");
        return Mono.empty();
    }
    
    @Override
    public int getPriority() {
        return 50; // Lower values execute first
    }
}
```

### Registering a Plugin

Create file: `META-INF/services/org.fireflyframework.client.plugin.ServiceClientPlugin`

```
com.example.CustomMetricsPlugin
```

### Using Plugin Manager

```java
// Plugin manager is automatically created
PluginManager pluginManager = new PluginManager(client);

// Get plugin
Optional<ServiceClientPlugin> plugin = pluginManager.getPlugin("custom-metrics");

// Get all plugins
List<ServiceClientPlugin> plugins = pluginManager.getAllPlugins();

// Get statistics
PluginStatistics stats = pluginManager.getStatistics();
System.out.println("Total plugins: " + stats.totalPlugins());
System.out.println("Enabled: " + stats.enabledPlugins());
```

---

## Best Practices

1. **Chaos Engineering**: Only enable in testing/staging environments
2. **Caching**: Use conservative TTLs for frequently changing data
3. **Service Discovery**: Implement health checks for accurate routing
4. **Deduplication**: Use appropriate TTL based on operation duration
5. **Load Balancing**: Choose strategy based on your use case
6. **Webhooks**: Always verify signatures in production
7. **Plugins**: Keep plugins lightweight and focused

---

## See Also

- [Security Guide](SECURITY.md)
- [Observability Guide](OBSERVABILITY.md)
- [Integration Testing Guide](INTEGRATION_TESTING.md)
- [Migration Guide](MIGRATION_GUIDE.md)

