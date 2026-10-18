# Firefly Framework - Domain Starter

[![CI](https://github.com/fireflyframework/fireflyframework-starter-domain/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-starter-domain/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Opinionated starter for domain-layer microservices — DDD patterns, CQRS, SAGA orchestration, and reactive event-driven architecture.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

**Firefly Framework Domain Starter** is an opinionated Spring Boot starter for building domain-layer microservices following Domain-Driven Design (DDD) principles. This starter brings together the essential capabilities needed for implementing business logic in a reactive, event-driven architecture.

Unlike a traditional library, this **starter** is designed to bootstrap domain-tier microservices with pre-configured integrations for:

- **Domain-Driven Design (DDD)**: Aggregate roots, value objects, domain events, and repository patterns
- **CQRS**: Command/query separation with handler auto-discovery and execution context propagation
- **SAGA Orchestration**: Distributed transactions via the orchestration engine
- **Event-Driven Architecture**: Reactive domain event publishing through Kafka, RabbitMQ, or other adapters
- **Service Communication**: Resilient inter-service communication with circuit breakers and retries

This starter automatically wires up JSON structured logging, orchestration engine integration, and observability infrastructure. It serves as the architectural foundation for domain microservices that implement core business logic while participating in distributed workflows and event streams.

**When to use this starter**: Building domain-layer microservices that encapsulate business rules, coordinate distributed transactions, and emit domain events to drive downstream processing.

## Features

- **DDD Building Blocks**: Domain entities, aggregates, value objects, and repository abstractions
- **CQRS Integration**: Automatic command/query bus wiring with fireflyframework-cqrs
- **Orchestration Support**: Saga, TCC, and Workflow patterns via fireflyframework-orchestration
- **Event-Driven Architecture**: Seamless integration with fireflyframework-eda for domain event publishing
- **Service Client Framework**: Reactive inter-service communication with resilience patterns
- **Observability**: JSON structured logging, metrics, and distributed tracing auto-configuration
- **Reactive-First**: Built on Project Reactor for non-blocking I/O and backpressure handling
- **Clean Architecture**: Enforces separation between domain logic and infrastructure concerns

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.9+

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-domain</artifactId>
    <version>26.02.07</version>
</dependency>
```

This starter transitively includes:
- `fireflyframework-cqrs` for command/query handling
- `fireflyframework-eda` for event publishing
- `fireflyframework-client` for service communication
- `fireflyframework-orchestration` for Saga, TCC, and Workflow orchestration
- `fireflyframework-observability` for metrics and tracing

## Quick Start

```java
@Service
@Saga(name = "create-account")
public class AccountSaga {

    @SagaStep(id = "validate", compensate = "rollbackValidation")
    public Mono<ValidationResult> validate(@Input CreateAccountRequest request) {
        return validateRequest(request);
    }

    @SagaStep(id = "persist", dependsOn = "validate", compensate = "deleteAccount")
    public Mono<Account> persist(@FromStep("validate") ValidationResult result) {
        return persistAccount(result);
    }

    public Mono<Void> deleteAccount(@FromStep("persist") Account account) {
        return removeAccount(account.getId());
    }
}
```

## Configuration

```yaml
firefly:
  domain:
    json-logging:
      enabled: true
  orchestration:
    enabled: true
```

## Documentation

No additional documentation available for this project.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
