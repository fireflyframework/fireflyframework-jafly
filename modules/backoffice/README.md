# Firefly Framework - Backoffice

[![CI](https://github.com/fireflyframework/fireflyframework-backoffice/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-backoffice/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Backoffice layer library extending the application module with customer impersonation, audit trail, and enhanced security context.

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

Firefly Framework Backoffice extends the application layer for internal backoffice and portal systems. It adds backoffice-specific context management, customer impersonation capabilities, enhanced security context with operator tracking, and abstract controllers tailored for administrative operations.

The module provides `BackofficeContext` and `BackofficeSecurityContext` which extend the application-layer contexts with operator identity, impersonated customer tracking, and audit trail support. Abstract controllers provide standardized REST patterns for backoffice resource management.

This library is designed for internal-facing microservices that require administrative capabilities, customer support workflows, and enhanced audit tracking beyond what the standard application layer provides.

## Features

- `BackofficeContext` with operator identity and impersonation support
- `BackofficeSecurityContext` extending application security context
- `AbstractBackofficeController` for standardized backoffice REST endpoints
- `AbstractBackofficeResourceController` for resource-based CRUD operations
- `BackofficeContextResolver` for extracting backoffice context from requests
- `DefaultBackofficeContextResolver` with configurable header mappings
- `BackofficeSessionContextMapper` for session-to-context mapping
- Customer impersonation tracking and audit trail

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.9+

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-backoffice</artifactId>
    <version>26.02.07</version>
</dependency>
```

## Quick Start

```java
import org.fireflyframework.common.backoffice.controller.AbstractBackofficeController;
import org.fireflyframework.common.backoffice.context.BackofficeContext;

@RestController
@RequestMapping("/api/backoffice/customers")
public class CustomerBackofficeController extends AbstractBackofficeController {

    @GetMapping("/{customerId}")
    public Mono<CustomerDetails> getCustomer(
            @PathVariable String customerId,
            BackofficeContext context) {
        // context provides operator identity and impersonation info
        return customerService.findById(customerId, context);
    }
}
```

## Configuration

```yaml
firefly:
  backoffice:
    context:
      operator-header: X-Operator-Id
      impersonation-header: X-Impersonated-Customer
```

## Documentation

No additional documentation available for this project.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
