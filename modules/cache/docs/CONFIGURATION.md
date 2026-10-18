# Configuration Reference

Complete reference for all configuration options in the Firefly Common Cache Library.

## Table of Contents

- [Global Configuration](#global-configuration)
- [Cache Types](#cache-types)
- [Caffeine Configuration](#caffeine-configuration)
- [Redis Configuration](#redis-configuration)
- [Multiple Cache Instances](#multiple-cache-instances)
- [Environment-Specific Configuration](#environment-specific-configuration)
- [Configuration Examples](#configuration-examples)

## Global Configuration

These properties control the overall behavior of the cache library.

### Basic Properties

```yaml
firefly:
  cache:
    # Enable or disable the cache library
    enabled: true  # Default: true
    
    # Default cache type to use
    default-cache-type: CAFFEINE  # Options: CAFFEINE, REDIS, AUTO, NOOP
    
    # Default cache name
    default-cache-name: default  # Default: "default"
    
    # Default serialization format
    default-serialization-format: json  # Default: "json"
    
    # Default timeout for cache operations
    default-timeout: PT5S  # Default: 5 seconds (ISO-8601 duration)
```

### Monitoring Properties

```yaml
firefly:
  cache:
    # Enable metrics collection
    metrics-enabled: true  # Default: true
    
    # Enable health checks
    health-enabled: true  # Default: true
    
    # Enable statistics tracking
    stats-enabled: true  # Default: true
```

## Cache Types

The library supports multiple cache types:

| Type | Description | Use Case |
|------|-------------|----------|
| `CAFFEINE` | High-performance in-memory cache | Single instance, low latency |
| `REDIS` | Distributed cache with persistence | Multi-instance, shared state |
| `HAZELCAST` | Distributed in-memory data grid | Clustered in‑memory sharing |
| `JCACHE` | JSR‑107 provider (Ehcache/Infinispan) | Standardized cache API |
| `AUTO` | Automatically select best available | Prefers REDIS > HAZELCAST > JCACHE > CAFFEINE |
| `NOOP` | Disabled (no-operation) | Testing, debugging |

### Selecting Cache Type

Provide the default type globally. If you use Hazelcast or JCache, ensure a HazelcastInstance or JCache CacheManager bean exists in the context.

```yaml
firefly:
  cache:
    default-cache-type: CAFFEINE  # Use Caffeine by default
```

## Caffeine Configuration

Caffeine is a high-performance, in-memory cache for Java.

### Complete Caffeine Properties

```yaml
firefly:
  cache:
    caffeine:
      default:  # Cache instance name
        # Enable this cache instance
        enabled: true  # Default: true
        
        # Maximum number of entries
        maximum-size: 1000  # Default: 1000
        
        # Expire entries after write
        expire-after-write: PT1H  # Default: 1 hour (ISO-8601)
        
        # Expire entries after last access
        expire-after-access:  # Optional, no default
        
        # Refresh entries after write
        refresh-after-write:  # Optional, no default
        
        # Record cache statistics
        record-stats: true  # Default: true
        
        # Use weak references for keys
        weak-keys: false  # Default: false
        
        # Use weak references for values
        weak-values: false  # Default: false
        
        # Use soft references for values
        soft-values: false  # Default: false
```

### Caffeine Property Details

#### maximum-size

Maximum number of entries the cache can hold.

- **Type**: Long
- **Default**: 1000
- **Example**: `maximum-size: 10000`

When the cache reaches this size, entries are evicted based on the eviction policy (LRU by default).

#### expire-after-write

Duration after which entries are automatically removed after creation or update.

- **Type**: Duration (ISO-8601 format)
- **Default**: PT1H (1 hour)
- **Examples**:
  - `PT30M` - 30 minutes
  - `PT2H` - 2 hours
  - `P1D` - 1 day

#### expire-after-access

Duration after which entries are automatically removed after last access.

- **Type**: Duration (ISO-8601 format)
- **Default**: None (disabled)
- **Example**: `expire-after-access: PT30M`

Useful for keeping frequently accessed items longer.

#### refresh-after-write

Duration after which entries are eligible for automatic refresh.

- **Type**: Duration (ISO-8601 format)
- **Default**: None (disabled)
- **Example**: `refresh-after-write: PT45M`

Requires async loading support.

#### record-stats

Enable statistics collection for monitoring.

- **Type**: Boolean
- **Default**: true
- **Example**: `record-stats: true`

#### weak-keys / weak-values / soft-values

Control reference types for memory management.

- **Type**: Boolean
- **Default**: false
- **Use Cases**:
  - `weak-keys`: Keys can be garbage collected
  - `weak-values`: Values can be garbage collected
  - `soft-values`: Values collected only under memory pressure

### Caffeine Configuration Examples

#### High-Performance Configuration

```yaml
firefly:
  cache:
    caffeine:
      default:
        maximum-size: 10000
        expire-after-access: PT2H
        record-stats: true
```

#### Memory-Efficient Configuration

```yaml
firefly:
  cache:
    caffeine:
      default:
        maximum-size: 100
        expire-after-write: PT15M
        soft-values: true
        record-stats: true
```

#### Long-Lived Cache Configuration

```yaml
firefly:
  cache:
    caffeine:
      default:
        maximum-size: 5000
        expire-after-write: P1D  # 1 day
        record-stats: true
```

## Redis Configuration

Redis provides distributed caching with persistence.

### Complete Redis Properties

```yaml
firefly:
  cache:
    redis:
      default:  # Cache instance name
        # Enable this cache instance
        enabled: true  # Default: true
        
        # Connection settings
        host: localhost  # Default: "localhost"
        port: 6379  # Default: 6379
        database: 0  # Default: 0
        
        # Authentication
        username:  # Optional, for Redis 6+ ACL
        password:  # Optional
        
        # Timeouts
        connection-timeout: PT10S  # Default: 10 seconds
        command-timeout: PT5S  # Default: 5 seconds
        
        # Key management
        key-prefix: "firefly:cache"  # Default: "firefly:cache"
        
        # TTL
        default-ttl: PT30M  # Optional, default TTL for entries
        
        # Connection pool
        max-pool-size: 8  # Default: 8
        min-pool-size: 0  # Default: 0
        
        # Security
        ssl: false  # Default: false
        
        # Advanced
        enable-keyspace-notifications: false  # Default: false
```

### Redis Property Details

#### Connection Settings

```yaml
host: localhost  # Redis server hostname
port: 6379      # Redis server port
database: 0     # Redis database index (0-15)
```

#### Authentication

```yaml
# Redis 6+ with ACL
username: myuser
password: mypassword

# Redis < 6 (password only)
password: mypassword
```

#### Timeouts

```yaml
connection-timeout: PT10S  # Time to establish connection
command-timeout: PT5S      # Time to execute command
```

#### Key Prefix

All cache keys are prefixed to avoid collisions:

```yaml
key-prefix: "firefly:cache"
```

Actual Redis key: `firefly:cache:user:123`

#### Connection Pool

```yaml
max-pool-size: 8   # Maximum connections
min-pool-size: 0   # Minimum idle connections
```

#### SSL/TLS

```yaml
ssl: true  # Enable SSL/TLS encryption
```

### Redis Configuration Examples

#### Development Configuration

```yaml
firefly:
  cache:
    redis:
      default:
        host: localhost
        port: 6379
        database: 0
        connection-timeout: PT10S
        command-timeout: PT5S
```

#### Production Configuration

```yaml
firefly:
  cache:
    redis:
      default:
        host: ${REDIS_HOST:redis.example.com}
        port: ${REDIS_PORT:6379}
        database: 0
        username: ${REDIS_USERNAME}
        password: ${REDIS_PASSWORD}
        connection-timeout: PT30S
        command-timeout: PT10S
        key-prefix: "prod:cache"
        default-ttl: PT30M
        max-pool-size: 16
        min-pool-size: 2
        ssl: true
```

#### Secure Configuration

```yaml
firefly:
  cache:
    redis:
      default:
        host: secure-redis.example.com
        port: 6380  # Common SSL port
        database: 0
        username: cache-user
        password: ${REDIS_PASSWORD}
        ssl: true
        connection-timeout: PT15S
        command-timeout: PT8S
```

## Smart (L1+L2) Configuration

```yaml
firefly:
  cache:
    smart:
      enabled: true            # Enable SmartCache automatically when a distributed provider is used
      write-strategy: WRITE_THROUGH
      backfill-l1-on-read: true
```

## Multiple Cache Instances

You can configure multiple named cache instances with different settings.

### Example: Multiple Caches

```yaml
firefly:
  cache:
    default-cache-name: default
    
    # Individual cache configurations
    caches:
      default:
        type: CAFFEINE
        default-ttl: PT1H
        enabled: true
      
      user-cache:
        type: REDIS
        default-ttl: PT30M
        enabled: true
      
      session-cache:
        type: CAFFEINE
        default-ttl: PT15M
        enabled: true
    
    # Caffeine configurations
    caffeine:
      default:
        maximum-size: 1000
        expire-after-write: PT1H
      
      session-cache:
        maximum-size: 5000
        expire-after-write: PT15M
    
    # Redis configurations
    redis:
      user-cache:
        host: redis-users.example.com
        port: 6379
        database: 1
        key-prefix: "firefly:user"
        default-ttl: PT30M
```

### Using Named Caches

```java
// Use specific cache
cacheManager.put("user-cache", userId, user).subscribe();
cacheManager.get("session-cache", sessionId).subscribe();
```

## Environment-Specific Configuration

### Development

```yaml
spring:
  profiles: development

firefly:
  cache:
    default-cache-type: CAFFEINE
    metrics-enabled: false
    caffeine:
      default:
        maximum-size: 100
        expire-after-write: PT5M
        record-stats: false
```

### Testing

```yaml
spring:
  profiles: test

firefly:
  cache:
    default-cache-type: CAFFEINE
    caffeine:
      default:
        maximum-size: 10
        expire-after-write: PT1M
```

### Production

```yaml
spring:
  profiles: production

firefly:
  cache:
    default-cache-type: REDIS
    metrics-enabled: true
    health-enabled: true
    redis:
      default:
        host: ${REDIS_HOST}
        port: ${REDIS_PORT}
        password: ${REDIS_PASSWORD}
        max-pool-size: 16
        ssl: true
```

## Configuration Examples

### Minimal Configuration

```yaml
firefly:
  cache:
    enabled: true
```

Uses all defaults: Caffeine cache, 1000 entries, 1-hour TTL.

### Recommended Production Configuration

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: REDIS
    metrics-enabled: true
    health-enabled: true
    
    redis:
      default:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: 0
        connection-timeout: PT30S
        command-timeout: PT10S
        key-prefix: "app:cache"
        default-ttl: PT30M
        max-pool-size: 16
        min-pool-size: 2
        ssl: ${REDIS_SSL:true}
```

### Hybrid Configuration (Caffeine + Redis)

```yaml
firefly:
  cache:
    enabled: true
    default-cache-name: default
    
    caches:
      default:
        type: CAFFEINE
      distributed:
        type: REDIS
    
    caffeine:
      default:
        maximum-size: 1000
        expire-after-write: PT1H
    
    redis:
      distributed:
        host: ${REDIS_HOST:localhost}
        port: 6379
        default-ttl: PT30M
```

## Best Practices

1. **Use environment variables** for sensitive data (passwords, hosts)
2. **Set appropriate TTLs** based on data volatility
3. **Monitor cache statistics** in production
4. **Use named caches** for different data types
5. **Configure connection pools** based on load
6. **Enable SSL** for production Redis
7. **Set reasonable timeouts** to prevent hanging
8. **Use key prefixes** to avoid collisions

## Validation

The library validates configuration at startup:

- Required properties are present
- Values are within acceptable ranges
- Durations are properly formatted (ISO-8601)
- Connection settings are valid

Invalid configuration will prevent application startup with clear error messages.

