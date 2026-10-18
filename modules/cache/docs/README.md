# Firefly Common Cache Library - Documentation

Welcome to the Firefly Common Cache Library documentation! This directory contains comprehensive guides and references to help you use the library effectively.

## 📚 Documentation Index

### Getting Started

- **[Quick Start Guide](QUICKSTART.md)** - Get up and running in minutes
  - Installation instructions
  - Basic configuration
  - First cache operations
  - Verification steps

- **[Auto-Configuration Guide](AUTO_CONFIGURATION.md)** - Spring Boot auto-configuration
  - How auto-configuration works
  - Configuration properties
  - Conditional beans
  - Customization options
  - Testing auto-configuration

### Core Documentation

- **[Architecture Guide](ARCHITECTURE.md)** - Understand how it works
  - Hexagonal architecture overview
  - Core components and their responsibilities
  - Design patterns used
  - Package structure
  - Extension points

- **[Configuration Reference](CONFIGURATION.md)** - Complete configuration guide
  - Global configuration options
  - Caffeine cache configuration
  - Redis cache configuration
  - Multiple cache instances
  - Environment-specific settings
  - Best practices

- **[API Reference](API_REFERENCE.md)** - Detailed API documentation
  - FireflyCacheManager API
  - CacheAdapter interface
  - Annotations (@Cacheable, @CacheEvict, @CachePut)
  - Configuration classes
  - Health and statistics APIs
  - Exception handling

### Practical Guides

- **[Examples](EXAMPLES.md)** - Real-world usage examples
  - Basic usage patterns
  - User service example
  - Product catalog caching
  - Session management
  - Rate limiting
  - Cache-aside pattern
  - Write-through pattern
  - Multi-level caching
  - Error handling

- **[Monitoring Guide](MONITORING.md)** - Observability and metrics
  - Health checks
  - Metrics collection
  - Statistics API
  - Spring Boot Actuator integration
  - Logging configuration
  - Alerting strategies
  - Dashboard examples

- **[Testing Guide](TESTING.md)** - Testing strategies
  - Unit testing with mocks
  - Integration testing
  - Testing with Caffeine
  - Testing with Redis (TestContainers)
  - Mocking strategies
  - Test configuration
  - Best practices

## 🚀 Quick Navigation

### I want to...

**Get started quickly**
→ [Quick Start Guide](QUICKSTART.md)

**Understand the architecture**
→ [Architecture Guide](ARCHITECTURE.md)

**Configure the cache**
→ [Configuration Reference](CONFIGURATION.md)

**See code examples**
→ [Examples](EXAMPLES.md)

**Look up API details**
→ [API Reference](API_REFERENCE.md)

**Set up monitoring**
→ [Monitoring Guide](MONITORING.md)

**Write tests**
→ [Testing Guide](TESTING.md)

## 📖 Learning Path

### For Beginners

1. Start with [Quick Start Guide](QUICKSTART.md)
2. Review [Examples](EXAMPLES.md) for common patterns
3. Read [Configuration Reference](CONFIGURATION.md) for your use case

### For Advanced Users

1. Study [Architecture Guide](ARCHITECTURE.md) to understand internals
2. Explore [API Reference](API_REFERENCE.md) for detailed API information
3. Set up [Monitoring](MONITORING.md) for production readiness
4. Review [Testing Guide](TESTING.md) for comprehensive testing

## 🔍 Common Topics

### Configuration

- [Basic Configuration](CONFIGURATION.md#global-configuration)
- [Caffeine Setup](CONFIGURATION.md#caffeine-configuration)
- [Redis Setup](CONFIGURATION.md#redis-configuration)
- [Multiple Caches](CONFIGURATION.md#multiple-cache-instances)

### Usage Patterns

- [Cache-Aside Pattern](EXAMPLES.md#cache-aside-pattern)
- [Write-Through Pattern](EXAMPLES.md#write-through-pattern)
- [Session Management](EXAMPLES.md#session-management)
- [Rate Limiting](EXAMPLES.md#rate-limiting)

### Operations

- [Health Checks](MONITORING.md#health-checks)
- [Metrics Collection](MONITORING.md#metrics)
- [Statistics](MONITORING.md#statistics-api)
- [Logging](MONITORING.md#logging)

### Testing

- [Unit Tests](TESTING.md#unit-testing)
- [Integration Tests](TESTING.md#integration-testing)
- [Redis Tests with TestContainers](TESTING.md#testing-with-redis)
- [Mocking Strategies](TESTING.md#mocking-strategies)

## 💡 Key Features

### Hexagonal Architecture
Clean separation between business logic and infrastructure. Learn more in [Architecture Guide](ARCHITECTURE.md#hexagonal-architecture).

### Multiple Cache Providers
Support for Caffeine (in-memory), Redis (distributed), Hazelcast (distributed in-memory grid), and JCache (JSR‑107 providers like Ehcache/Infinispan). See [Configuration Reference](CONFIGURATION.md#cache-types).

### Reactive API
Non-blocking operations using Project Reactor. Examples in [API Reference](API_REFERENCE.md#fireflycachemanager).

### Comprehensive Monitoring
Built-in health checks, metrics, and statistics. Details in [Monitoring Guide](MONITORING.md).

### Easy Testing
Designed for testability with mocking support. Guide in [Testing Guide](TESTING.md).

## 🔗 External Resources

- [Main README](../README.md) - Project overview
- [GitHub Repository](https://github.org/fireflyframework-oss/fireflyframework-cache)
- [Caffeine Documentation](https://github.com/ben-manes/caffeine)
- [Redis Documentation](https://redis.io/documentation)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Project Reactor Documentation](https://projectreactor.io/docs)

## 📝 Documentation Conventions

### Code Examples

All code examples are tested and reflect the actual API. They use:
- Java 25 syntax (Java 21+ compatible)
- Spring Boot 3.5+
- Reactive programming with Reactor
- Lombok for brevity

### Configuration Examples

Configuration examples use YAML format (application.yml) but can be adapted to:
- Properties format (application.properties)
- Environment variables
- Programmatic configuration

### Placeholders

- `${VARIABLE}` - Environment variable
- `PT1H` - ISO-8601 duration (1 hour)
- `localhost` - Replace with actual host
- `default` - Default cache name

## 🤝 Contributing to Documentation

Found an error or want to improve the documentation?

1. Check the [Contributing Guidelines](../CONTRIBUTING.md)
2. Submit an issue or pull request
3. Follow the documentation style guide

## 📄 License

This documentation is part of the Firefly Common Cache Library and is licensed under the Apache License 2.0.

---

**Need help?** Check the [Troubleshooting section](../README.md#-troubleshooting) in the main README or open an issue on GitHub.

