# Monitoring Guide

Complete guide to monitoring, metrics, and observability for the Firefly Common Cache Library.

## Table of Contents

- [Overview](#overview)
- [Health Checks](#health-checks)
- [Metrics](#metrics)
- [Statistics API](#statistics-api)
- [Spring Boot Actuator Integration](#spring-boot-actuator-integration)
- [Logging](#logging)
- [Alerting](#alerting)
- [Dashboard Examples](#dashboard-examples)

## Overview

The Firefly Common Cache Library provides comprehensive monitoring capabilities:

- **Health Checks**: Verify cache availability and responsiveness
- **Metrics**: Track performance and usage via Micrometer
- **Statistics**: Detailed cache statistics (hits, misses, evictions)
- **Logging**: Structured logging for debugging and auditing

## Health Checks

### Enabling Health Checks

```yaml
firefly:
  cache:
    health-enabled: true  # Default: true

management:
  endpoints:
    web:
      exposure:
        include: health
  health:
    cache:
      enabled: true
```

### Health Check Endpoint

Access cache health via Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health/cache
```

### Health Response Format

```json
{
  "status": "UP",
  "details": {
    "totalCaches": 2,
    "healthyCaches": 2,
    "unhealthyCaches": 0,
    "caches": {
      "default": {
        "type": "caffeine",
        "status": "UP",
        "available": true,
        "configured": true,
        "responseTimeMs": 1
      },
      "user-cache": {
        "type": "redis",
        "status": "UP",
        "available": true,
        "configured": true,
        "responseTimeMs": 5
      }
    }
  }
}
```

### Health Status Values

- **UP**: Cache is healthy and operational
- **DOWN**: Cache is unavailable or unhealthy
- **UNKNOWN**: Health status cannot be determined

### Programmatic Health Checks

```java
@Service
@RequiredArgsConstructor
public class CacheHealthMonitor {
    
    private final FireflyCacheManager cacheManager;
    
    /**
     * Check health of all caches
     */
    public Flux<CacheHealth> checkAllCaches() {
        return cacheManager.getHealth()
            .doOnNext(health -> {
                if (health.getStatus() != HealthStatus.UP) {
                    log.warn("Cache {} is unhealthy: {}", 
                        health.getCacheName(), 
                        health.getMessage());
                }
            });
    }
    
    /**
     * Check specific cache health
     */
    public Mono<Boolean> isCacheHealthy(String cacheName) {
        return cacheManager.getHealth(cacheName)
            .map(health -> health.getStatus() == HealthStatus.UP)
            .defaultIfEmpty(false);
    }
}
```

## Metrics

### Enabling Metrics

```yaml
firefly:
  cache:
    metrics-enabled: true  # Default: true

management:
  endpoints:
    web:
      exposure:
        include: metrics, prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Available Metrics

The library exposes the following metrics via Micrometer:

#### Cache Operations

- `cache.gets` - Total number of get operations
  - Tags: `cache`, `result` (hit/miss)
- `cache.puts` - Total number of put operations
  - Tags: `cache`
- `cache.evictions` - Total number of evictions
  - Tags: `cache`
- `cache.size` - Current cache size
  - Tags: `cache`

#### Performance Metrics

- `cache.get.duration` - Time taken for get operations
  - Tags: `cache`, `result`
- `cache.put.duration` - Time taken for put operations
  - Tags: `cache`

#### Hit Rate

- `cache.hit.rate` - Cache hit rate (0.0 to 1.0)
  - Tags: `cache`

### Accessing Metrics

#### Prometheus Format

```bash
curl http://localhost:8080/actuator/prometheus | grep cache
```

Example output:
```
cache_gets_total{cache="default",result="hit"} 1523
cache_gets_total{cache="default",result="miss"} 234
cache_puts_total{cache="default"} 234
cache_size{cache="default"} 189
cache_hit_rate{cache="default"} 0.867
```

#### JSON Format

```bash
curl http://localhost:8080/actuator/metrics/cache.gets
```

Response:
```json
{
  "name": "cache.gets",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1757
    }
  ],
  "availableTags": [
    {
      "tag": "cache",
      "values": ["default", "user-cache"]
    },
    {
      "tag": "result",
      "values": ["hit", "miss"]
    }
  ]
}
```

## Statistics API

### Getting Statistics

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheStatsService {
    
    private final FireflyCacheManager cacheManager;
    
    /**
     * Get statistics for all caches
     */
    public Flux<CacheStats> getAllStats() {
        return cacheManager.getStats()
            .doOnNext(stats -> {
                log.info("Cache: {} - Hit Rate: {:.2f}%, Size: {}", 
                    stats.getCacheName(),
                    stats.getHitRate() * 100,
                    stats.getSize());
            });
    }
    
    /**
     * Get statistics for specific cache
     */
    public Mono<CacheStats> getCacheStats(String cacheName) {
        return cacheManager.getStats(cacheName);
    }
    
    /**
     * Calculate overall hit rate
     */
    public Mono<Double> getOverallHitRate() {
        return cacheManager.getStats()
            .reduce(
                new long[]{0, 0}, // [hits, total]
                (acc, stats) -> {
                    acc[0] += stats.getHitCount();
                    acc[1] += stats.getHitCount() + stats.getMissCount();
                    return acc;
                }
            )
            .map(totals -> totals[1] == 0 ? 0.0 : (double) totals[0] / totals[1]);
    }
}
```

### Statistics Fields

```java
public class CacheStats {
    private final String cacheName;
    private final CacheType cacheType;
    private final long hitCount;        // Number of cache hits
    private final long missCount;       // Number of cache misses
    private final long loadSuccessCount; // Successful loads
    private final long loadFailureCount; // Failed loads
    private final long evictionCount;   // Number of evictions
    private final long size;            // Current cache size
    
    // Calculated hit rate (0.0 to 1.0)
    public double getHitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }
}
```

### Monitoring Statistics

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheStatsMonitor {
    
    private final FireflyCacheManager cacheManager;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void logCacheStats() {
        cacheManager.getStats()
            .subscribe(stats -> {
                double hitRate = stats.getHitRate() * 100;
                
                log.info("Cache Statistics - Name: {}, Type: {}, " +
                        "Hit Rate: {:.2f}%, Hits: {}, Misses: {}, " +
                        "Size: {}, Evictions: {}",
                    stats.getCacheName(),
                    stats.getCacheType(),
                    hitRate,
                    stats.getHitCount(),
                    stats.getMissCount(),
                    stats.getSize(),
                    stats.getEvictionCount()
                );
                
                // Alert on low hit rate
                if (hitRate < 50.0) {
                    log.warn("Low cache hit rate detected: {:.2f}% for cache: {}", 
                        hitRate, stats.getCacheName());
                }
            });
    }
}
```

## Spring Boot Actuator Integration

### Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus, info
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
  health:
    cache:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

### Available Endpoints

- `/actuator/health` - Overall application health
- `/actuator/health/cache` - Cache-specific health
- `/actuator/metrics` - List of available metrics
- `/actuator/metrics/cache.*` - Cache-specific metrics
- `/actuator/prometheus` - Prometheus-formatted metrics

## Logging

### Configuration

```yaml
logging:
  level:
    org.fireflyframework.cache: INFO
    org.fireflyframework.cache.adapter: DEBUG
    org.fireflyframework.cache.manager: DEBUG
```

### Log Levels

- **ERROR**: Critical errors (cache unavailable, serialization failures)
- **WARN**: Warnings (low hit rate, evictions, timeouts)
- **INFO**: Important events (cache registration, configuration)
- **DEBUG**: Detailed operations (get, put, evict operations)
- **TRACE**: Very detailed (serialization, internal state)

### Structured Logging Example

```java
@Slf4j
public class CacheLoggingExample {
    
    public Mono<User> getUser(String userId) {
        return cacheManager.get(userId, User.class)
            .doOnSubscribe(s -> 
                log.debug("Attempting to get user from cache: {}", userId))
            .doOnNext(result -> {
                if (result.isPresent()) {
                    log.debug("Cache hit for user: {}", userId);
                } else {
                    log.debug("Cache miss for user: {}", userId);
                }
            })
            .doOnError(error -> 
                log.error("Cache error for user {}: {}", userId, error.getMessage(), error))
            .flatMap(cached -> cached
                .map(Mono::just)
                .orElseGet(() -> loadFromDatabase(userId))
            );
    }
}
```

## Alerting

### Key Metrics to Alert On

#### 1. Low Hit Rate

```java
@Component
@RequiredArgsConstructor
public class HitRateAlert {
    
    private final FireflyCacheManager cacheManager;
    private final AlertService alertService;
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkHitRate() {
        cacheManager.getStats()
            .filter(stats -> stats.getHitRate() < 0.5) // Less than 50%
            .subscribe(stats -> {
                alertService.sendAlert(
                    "Low Cache Hit Rate",
                    String.format("Cache %s has hit rate of %.2f%%", 
                        stats.getCacheName(), 
                        stats.getHitRate() * 100)
                );
            });
    }
}
```

#### 2. Cache Unavailability

```java
@Component
@RequiredArgsConstructor
public class CacheAvailabilityAlert {
    
    private final FireflyCacheManager cacheManager;
    private final AlertService alertService;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void checkAvailability() {
        cacheManager.getHealth()
            .filter(health -> health.getStatus() != HealthStatus.UP)
            .subscribe(health -> {
                alertService.sendAlert(
                    "Cache Unavailable",
                    String.format("Cache %s is %s: %s", 
                        health.getCacheName(),
                        health.getStatus(),
                        health.getMessage())
                );
            });
    }
}
```

#### 3. High Eviction Rate

```java
@Component
public class EvictionRateAlert {
    
    private final Map<String, Long> previousEvictions = new ConcurrentHashMap<>();
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkEvictionRate() {
        cacheManager.getStats()
            .subscribe(stats -> {
                String cacheName = stats.getCacheName();
                long currentEvictions = stats.getEvictionCount();
                long previousCount = previousEvictions.getOrDefault(cacheName, 0L);
                long evictionRate = currentEvictions - previousCount;
                
                if (evictionRate > 1000) { // More than 1000 evictions in 5 minutes
                    alertService.sendAlert(
                        "High Eviction Rate",
                        String.format("Cache %s had %d evictions in 5 minutes", 
                            cacheName, evictionRate)
                    );
                }
                
                previousEvictions.put(cacheName, currentEvictions);
            });
    }
}
```

## Dashboard Examples

### Grafana Dashboard

Example Prometheus queries for Grafana:

#### Hit Rate Panel

```promql
# Hit rate percentage
rate(cache_gets_total{result="hit"}[5m]) / 
rate(cache_gets_total[5m]) * 100
```

#### Operations Per Second

```promql
# Get operations per second
rate(cache_gets_total[1m])

# Put operations per second
rate(cache_puts_total[1m])
```

#### Cache Size

```promql
# Current cache size
cache_size
```

#### Response Time

```promql
# Average get operation duration
rate(cache_get_duration_sum[5m]) / 
rate(cache_get_duration_count[5m])
```

### Custom Monitoring Dashboard

```java
@RestController
@RequestMapping("/api/cache/monitoring")
@RequiredArgsConstructor
public class CacheMonitoringController {
    
    private final FireflyCacheManager cacheManager;
    
    @GetMapping("/dashboard")
    public Mono<CacheDashboard> getDashboard() {
        return cacheManager.getStats()
            .collectList()
            .zipWith(cacheManager.getHealth().collectList())
            .map(tuple -> {
                List<CacheStats> stats = tuple.getT1();
                List<CacheHealth> health = tuple.getT2();
                
                return CacheDashboard.builder()
                    .totalCaches(stats.size())
                    .healthyCaches((int) health.stream()
                        .filter(h -> h.getStatus() == HealthStatus.UP)
                        .count())
                    .overallHitRate(calculateOverallHitRate(stats))
                    .cacheStats(stats)
                    .cacheHealth(health)
                    .timestamp(Instant.now())
                    .build();
            });
    }
    
    private double calculateOverallHitRate(List<CacheStats> stats) {
        long totalHits = stats.stream().mapToLong(CacheStats::getHitCount).sum();
        long totalRequests = stats.stream()
            .mapToLong(s -> s.getHitCount() + s.getMissCount())
            .sum();
        return totalRequests == 0 ? 0.0 : (double) totalHits / totalRequests;
    }
}
```

## Best Practices

1. **Monitor Hit Rates**: Aim for >80% hit rate for effective caching
2. **Set Up Alerts**: Alert on cache unavailability and low hit rates
3. **Track Evictions**: High eviction rates may indicate undersized cache
4. **Monitor Response Times**: Ensure cache operations remain fast
5. **Use Dashboards**: Visualize metrics for better insights
6. **Log Important Events**: Log cache misses for critical operations
7. **Regular Reviews**: Review cache statistics regularly
8. **Capacity Planning**: Use metrics to plan cache capacity

## Troubleshooting

### Low Hit Rate

**Symptoms**: Hit rate below 50%

**Possible Causes**:
- TTL too short
- Cache size too small
- Data access patterns not suitable for caching
- Cache keys not consistent

**Solutions**:
- Increase TTL
- Increase cache size
- Review caching strategy
- Verify key generation logic

### High Eviction Rate

**Symptoms**: Many evictions per minute

**Possible Causes**:
- Cache size too small
- TTL too long (filling cache)
- Memory pressure

**Solutions**:
- Increase maximum cache size
- Reduce TTL
- Use soft/weak references for Caffeine

### Slow Cache Operations

**Symptoms**: High response times

**Possible Causes**:
- Network latency (Redis)
- Serialization overhead
- Large objects

**Solutions**:
- Optimize network configuration
- Use binary serialization
- Cache smaller objects
- Use compression

