# Performance Optimization Guide

This guide provides comprehensive information about the Firefly Framework Rule Engine's performance optimization features, configuration options, and best practices for high-load production environments.

## Overview

The Firefly Framework Rule Engine includes three major performance optimization systems:

1. **AST Caching** - Dual cache providers (Caffeine/Redis) for parsed AST models
2. **Connection Pool Tuning** - Optimized R2DBC connection pools for high-load scenarios  
3. **Batch Operations** - Concurrent rule evaluation with configurable concurrency limits

## 🚀 AST Caching System

### Cache Provider Selection

The system supports two cache providers that can be selected via configuration:

#### Caffeine Cache Provider (Default)
- **Ultra-fast local caching** - 664x faster reads than Redis
- **Memory-efficient** - Automatic eviction and size management
- **Zero network overhead** - Perfect for single-instance deployments
- **Thread-safe** - Concurrent access without locks

#### Redis Cache Provider (Optional)
- **Distributed caching** - Share cache across multiple instances
- **Persistence** - Survive application restarts
- **Scalability** - Handle large cache datasets
- **High availability** - Redis cluster support

### Configuration

```yaml
firefly:
  rules:
    cache:
      provider: caffeine  # or 'redis'
      
      # Caffeine Configuration (Default)
      caffeine:
        ast-cache:
          maximum-size: 1000
          expire-after-write: 2h
          expire-after-access: 30m
        constants-cache:
          maximum-size: 500
          expire-after-write: 15m
          expire-after-access: 5m
        validation-cache:
          maximum-size: 200
          expire-after-write: 1h
          expire-after-access: 15m
        
      # Redis Configuration (Optional)
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: ${REDIS_DATABASE:0}
        timeout: 5s
        ttl:
          ast-cache: 2h
          constants-cache: 15m
          validation-cache: 1h
```

### Performance Comparison

| Operation | Caffeine | Redis | Performance Gain |
|-----------|----------|-------|------------------|
| **Read Operations** | 0.26 ms | 175.12 ms | **664x faster** |
| **Write Operations** | 0.25 ms | 44.28 ms | **180x faster** |
| **Network Overhead** | None | TCP/Redis Protocol | **Zero vs Network** |
| **Use Case** | Single instance | Multi-instance | **Deployment specific** |

### Cache Usage Patterns

- **AST Cache**: Stores parsed AST models using SHA-256 hash of YAML content
- **Constants Cache**: Caches system constants to avoid repeated database queries
- **Validation Cache**: Caches validation results for frequently validated rules

### Monitoring Cache Performance

```bash
# Get cache statistics
curl http://localhost:8080/api/v1/rules/cache/statistics

# Response includes hit rates, miss rates, eviction counts
{
  "astCache": {
    "provider": "caffeine",
    "hitRate": 85.5,
    "missRate": 14.5,
    "evictionCount": 12,
    "averageLoadTime": "2.3ms",
    "size": 750
  },
  "constantsCache": {
    "hitRate": 92.1,
    "missRate": 7.9,
    "size": 245
  }
}
```

## 🔗 Connection Pool Optimization

### Environment-Specific Configuration

#### Production Settings (High Load)
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
      max-acquire-time: 60s
      max-create-connection-time: 30s
      max-life-time: 1800s
      validation-query: SELECT 1
      validation-depth: LOCAL
```

#### Development Settings (Resource Efficient)
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 5
      max-size: 10
      max-idle-time: 15m
      max-acquire-time: 30s
      max-create-connection-time: 15s
      max-life-time: 900s
```

### Pool Monitoring

Monitor connection pool health through actuator endpoints:

```bash
# Connection pool metrics
curl http://localhost:8080/actuator/metrics/r2dbc.pool.acquired
curl http://localhost:8080/actuator/metrics/r2dbc.pool.allocated
curl http://localhost:8080/actuator/metrics/r2dbc.pool.pending
```

## 📦 Batch Processing Optimization

### Batch Evaluation API

```bash
curl -X POST http://localhost:8080/api/v1/rules/batch/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "evaluationRequests": [
      {
        "requestId": "req-001",
        "ruleDefinitionCode": "LOAN_APPROVAL",
        "inputData": {"creditScore": 750, "income": 75000},
        "priority": 1
      }
    ],
    "batchOptions": {
      "maxConcurrency": 10,
      "timeoutSeconds": 300,
      "failFast": false,
      "returnPartialResults": true,
      "sortByPriority": true
    }
  }'
```

### Configuration Options

| Parameter | Description | Default | Range | Impact |
|-----------|-------------|---------|-------|---------|
| `maxConcurrency` | Concurrent evaluations | 10 | 1-50 | **Throughput** |
| `timeoutSeconds` | Batch timeout | 300 | 30-1800 | **Reliability** |
| `failFast` | Stop on first error | false | true/false | **Error Handling** |
| `returnPartialResults` | Return partial success | true | true/false | **Availability** |
| `sortByPriority` | Process by priority | false | true/false | **Ordering** |

### Performance Characteristics

- **Throughput**: Up to 2000 rules/minute with optimal configuration
- **Latency**: Average 45ms per rule evaluation
- **Concurrency**: Configurable from 1-50 concurrent evaluations
- **Error Resilience**: Partial results with detailed error reporting

### Batch Statistics

```bash
# Get real-time batch statistics
curl http://localhost:8080/api/v1/rules/batch/statistics

{
  "batchSummary": {
    "totalRequests": 1000,
    "successfulEvaluations": 985,
    "failedEvaluations": 15,
    "successRate": 98.5,
    "averageProcessingTimeMs": 45.2,
    "cacheHits": 750,
    "cacheHitRate": 75.0
  },
  "performanceMetrics": {
    "totalBatchesProcessed": 50,
    "averageConcurrency": 8.5,
    "peakThroughput": "2000 rules/minute"
  }
}
```

## 📊 Performance Monitoring

### Key Performance Indicators (KPIs)

| Metric | Target | Description |
|--------|--------|-------------|
| **Cache Hit Rate** | >80% | Percentage of cache hits vs total requests |
| **Average Response Time** | <50ms | Average time per rule evaluation |
| **Throughput** | >1000 rules/min | Rules processed per minute in batch mode |
| **Error Rate** | <1% | Percentage of failed evaluations |
| **Resource Utilization** | CPU <70%, Memory <80% | System resource usage |

### Health Checks

```bash
# Overall system health
curl http://localhost:8080/api/v1/rules/batch/health

# Cache provider health
curl http://localhost:8080/actuator/health/cache

# Database connection health  
curl http://localhost:8080/actuator/health/r2dbc
```

## 🎯 Best Practices

### Cache Optimization
1. **Use Caffeine for single-instance deployments** - 664x faster than Redis
2. **Use Redis for multi-instance deployments** - Shared cache across instances
3. **Monitor cache hit rates** - Target >80% for optimal performance
4. **Tune cache sizes** - Balance memory usage vs hit rates
5. **Set appropriate TTL values** - Balance freshness vs performance

### Connection Pool Tuning
1. **Size pools for peak load** - Monitor connection usage patterns
2. **Set appropriate timeouts** - Balance responsiveness vs resource usage
3. **Enable connection validation** - Ensure connection health
4. **Monitor pool metrics** - Track acquired, allocated, pending connections

### Batch Processing
1. **Tune concurrency for your hardware** - Start with 10, adjust based on CPU cores
2. **Use priority sorting for critical rules** - Process high-priority rules first
3. **Enable partial results** - Improve availability in error scenarios
4. **Monitor batch statistics** - Track throughput and error rates
5. **Set appropriate timeouts** - Balance completeness vs responsiveness

### Memory Management
1. **Monitor heap usage** - Ensure adequate memory for caches
2. **Tune garbage collection** - Use G1GC for low-latency applications
3. **Set cache size limits** - Prevent out-of-memory errors
4. **Monitor memory leaks** - Watch for growing heap usage

## 🚨 Troubleshooting

### Common Performance Issues

#### Low Cache Hit Rate
- **Symptoms**: High response times, frequent database queries
- **Solutions**: Increase cache sizes, adjust TTL values, check cache key patterns

#### High Connection Pool Contention
- **Symptoms**: High acquire times, pending connections
- **Solutions**: Increase pool size, optimize query performance, check for connection leaks

#### Batch Processing Timeouts
- **Symptoms**: Batch operations timing out, partial results
- **Solutions**: Increase timeout values, reduce concurrency, optimize rule complexity

#### Memory Issues
- **Symptoms**: OutOfMemoryError, high GC pressure
- **Solutions**: Increase heap size, reduce cache sizes, tune GC settings

### Performance Tuning Checklist

- [ ] Cache provider selected based on deployment architecture
- [ ] Cache hit rates >80%
- [ ] Connection pool sized for peak load
- [ ] Batch concurrency tuned for hardware
- [ ] Memory usage <80% of available heap
- [ ] Response times <50ms average
- [ ] Error rates <1%
- [ ] Monitoring and alerting configured
