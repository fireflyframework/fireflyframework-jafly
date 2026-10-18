# Configuration Examples

This document provides comprehensive configuration examples for the Firefly Framework Rule Engine's performance optimization features.

## Performance Optimization Configuration

### Cache Provider Configuration

#### Caffeine Cache (Default - High Performance Local Cache)

```yaml
firefly:
  rules:
    cache:
      provider: caffeine
      caffeine:
        ast-cache:
          maximum-size: 1000
          expire-after-write: 2h
          expire-after-access: 30m
        constants-cache:
          maximum-size: 500
          expire-after-write: 15m
          expire-after-access: 5m
        rule-definitions-cache:
          maximum-size: 200
          expire-after-write: 10m
          expire-after-access: 3m
        validation-cache:
          maximum-size: 100
          expire-after-write: 5m
          expire-after-access: 2m
```

#### Redis Cache (Optional - Distributed Cache)

```yaml
firefly:
  rules:
    cache:
      provider: redis
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: ${REDIS_DATABASE:0}
        timeout: 5s
        ttl:
          ast-cache: 2h
          constants-cache: 15m
          rule-definitions-cache: 10m
          validation-cache: 5m
```

### Connection Pool Configuration

#### Development Environment

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 5
      max-size: 15
      min-idle: 2
      max-idle-time: 10m
      max-acquire-time: 30s
      max-create-connection-time: 15s
      max-life-time: 15m
      validation-query: SELECT 1
      validation-depth: LOCAL
```

#### Production Environment

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 20
      max-size: 100
      min-idle: 10
      max-idle-time: 20m
      max-acquire-time: 90s
      max-create-connection-time: 30s
      max-life-time: 45m
      validation-query: SELECT 1
      validation-depth: LOCAL
      acquire-retry: 5
      register-jmx: true
```

### Environment-Specific Configurations

#### Development Profile

```yaml
---
spring:
  config:
    activate:
      on-profile: dev

  r2dbc:
    pool:
      initial-size: 5
      max-size: 15
      min-idle: 2
      max-idle-time: 10m
      max-acquire-time: 30s

firefly:
  rules:
    cache:
      provider: caffeine
      caffeine:
        ast-cache:
          maximum-size: 500
          expire-after-write: 1h
          expire-after-access: 15m
        constants-cache:
          maximum-size: 200
          expire-after-write: 10m
          expire-after-access: 3m

logging:
  level:
    org.fireflyframework: DEBUG
    io.r2dbc.pool: DEBUG
```

#### Testing Profile

```yaml
---
spring:
  config:
    activate:
      on-profile: test

  r2dbc:
    pool:
      initial-size: 2
      max-size: 5
      min-idle: 1
      max-idle-time: 5m
      max-acquire-time: 10s

firefly:
  rules:
    cache:
      provider: caffeine
      caffeine:
        ast-cache:
          maximum-size: 100
          expire-after-write: 30m
          expire-after-access: 10m
        constants-cache:
          maximum-size: 50
          expire-after-write: 5m
          expire-after-access: 2m
```

#### Production Profile

```yaml
---
spring:
  config:
    activate:
      on-profile: prod

  r2dbc:
    pool:
      initial-size: 20
      max-size: 100
      min-idle: 10
      max-idle-time: 20m
      max-acquire-time: 90s
      max-life-time: 45m
      acquire-retry: 5

firefly:
  rules:
    cache:
      # Consider Redis for distributed caching in production
      provider: redis
      redis:
        host: ${REDIS_HOST:redis-cluster.example.com}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD}
        database: ${REDIS_DATABASE:0}
        timeout: 5s
        ttl:
          ast-cache: 4h
          constants-cache: 30m
          rule-definitions-cache: 20m
          validation-cache: 15m

logging:
  level:
    root: WARN
    org.fireflyframework: INFO
```

## Environment Variables

### Database Configuration

```bash
# Database connection
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=firefly_rules
export DB_USERNAME=firefly_user
export DB_PASSWORD=secure_password
export DB_SSL_MODE=disable

# Server configuration
export SERVER_ADDRESS=0.0.0.0
export SERVER_PORT=8080
```

### Redis Configuration (when using Redis cache)

```bash
# Redis connection
export REDIS_HOST=redis-cluster.example.com
export REDIS_PORT=6379
export REDIS_PASSWORD=redis_secure_password
export REDIS_DATABASE=0
```

## Docker Compose Configuration

### Development with PostgreSQL and Redis

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: firefly_rules
      POSTGRES_USER: firefly_user
      POSTGRES_PASSWORD: firefly_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

  firefly-rule-engine:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: firefly_rules
      DB_USERNAME: firefly_user
      DB_PASSWORD: firefly_password
      DB_SSL_MODE: disable
      REDIS_HOST: redis
      REDIS_PORT: 6379
      SPRING_PROFILES_ACTIVE: dev
    depends_on:
      - postgres
      - redis

volumes:
  postgres_data:
  redis_data:
```

## Performance Tuning Guidelines

### Cache Size Recommendations

| Environment | AST Cache | Constants Cache | Rule Definitions Cache | Validation Cache |
|-------------|-----------|-----------------|------------------------|------------------|
| **Development** | 500 | 200 | 100 | 50 |
| **Testing** | 100 | 50 | 50 | 25 |
| **Staging** | 1000 | 500 | 200 | 100 |
| **Production** | 2000+ | 1000+ | 500+ | 300+ |

### Connection Pool Sizing

| Environment | Initial Size | Max Size | Min Idle | Recommendations |
|-------------|--------------|----------|----------|-----------------|
| **Development** | 5 | 15 | 2 | Minimal resource usage |
| **Testing** | 2 | 5 | 1 | Fast test execution |
| **Staging** | 10 | 30 | 5 | Production-like load |
| **Production** | 20 | 100+ | 10 | High-load capacity |

### Cache Provider Selection

| Scenario | Recommended Provider | Reason |
|----------|---------------------|---------|
| **Single Instance** | Caffeine | 664x faster, no network overhead |
| **Multi-Instance** | Redis | Shared cache across instances |
| **Development** | Caffeine | Simpler setup, faster development |
| **Production (Single)** | Caffeine | Maximum performance |
| **Production (Cluster)** | Redis | Distributed caching |
| **High Availability** | Redis | Persistence and clustering |

## Monitoring Configuration

### Actuator Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### Key Metrics to Monitor

- `cache.gets` - Cache access patterns
- `cache.puts` - Cache write patterns  
- `cache.evictions` - Cache eviction rates
- `r2dbc.pool.acquired` - Active connections
- `r2dbc.pool.allocated` - Total allocated connections
- `r2dbc.pool.pending` - Pending connection requests
- `http.server.requests` - API request metrics
- `jvm.memory.used` - Memory usage patterns

## Troubleshooting Common Issues

### High Cache Miss Rate

```yaml
# Increase cache sizes and TTL
firefly:
  rules:
    cache:
      caffeine:
        ast-cache:
          maximum-size: 2000  # Increase from 1000
          expire-after-write: 4h  # Increase from 2h
```

### Connection Pool Exhaustion

```yaml
# Increase pool size and timeouts
spring:
  r2dbc:
    pool:
      max-size: 50  # Increase from 20
      max-acquire-time: 120s  # Increase from 60s
```

### Memory Issues

```yaml
# Reduce cache sizes
firefly:
  rules:
    cache:
      caffeine:
        ast-cache:
          maximum-size: 500  # Reduce from 1000
```

### Redis Connection Issues

```yaml
# Increase timeouts and add retry logic
firefly:
  rules:
    cache:
      redis:
        timeout: 10s  # Increase from 5s
        # Add connection pool settings
```
