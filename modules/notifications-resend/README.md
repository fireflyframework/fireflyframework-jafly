# Firefly Framework - Notifications Resend Adapter

[![CI](https://github.com/fireflyframework/fireflyframework-notifications-resend/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-notifications-resend/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Resend email transport adapter for the Firefly Framework notifications abstraction — drop it on the classpath, set one property, and send email through the Resend API reactively.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-notifications-resend` is a pluggable **email provider adapter** for the Firefly Framework notifications subsystem. It implements the `EmailProvider` SPI defined in `fireflyframework-notifications-core` and routes outbound email through [Resend](https://resend.com), a developer-focused transactional email API.

The notifications core defines a transport-agnostic contract (`EmailProvider`, `EmailRequestDTO`, `EmailResponseDTO`) so that application and domain code never depends on a specific email vendor. This module is one of several interchangeable backends for that contract: you select it at runtime by setting `firefly.notifications.email.provider=resend`. Swapping vendors becomes a configuration change rather than a code change.

The adapter is fully reactive (Project Reactor `Mono`) and builds on `fireflyframework-client`'s `RestClient`/`ServiceClient`, so it inherits the framework's HTTP client conventions (JSON content negotiation, bearer-token auth headers, and a named client instance). Auto-configuration wires everything automatically once the module is on the classpath and a Resend API key is present — there are no beans to declare by hand.

Where it sits in the framework:

- **Core SPI** — [`fireflyframework-notifications-core`](https://github.com/fireflyframework/fireflyframework-notifications-core) defines the `EmailProvider` interface and email DTOs this adapter implements.
- **Sibling adapters** — other email/SMS/push providers implement the same SPIs; only one email provider is active per application, chosen by `firefly.notifications.email.provider`.
- **HTTP client** — [`fireflyframework-client`](https://github.com/fireflyframework/fireflyframework-client) supplies the reactive `RestClient` used to call the Resend REST API.

## Features

- **`EmailProvider` implementation** (`ResendEmailProvider`) backed by the Resend `POST /emails` endpoint.
- **Reactive, non-blocking** delivery returning `Mono<EmailResponseDTO>`; the blocking-friendly call is dispatched on `Schedulers.boundedElastic()`.
- **Spring Boot auto-configuration** (`ResendAutoConfiguration`) activated by the `firefly.notifications.email.provider=resend` selector and the presence of an API key — no manual bean definitions required.
- **Rich message support** — HTML and/or plain-text bodies, multiple `to`/`cc`/`bcc` recipients, and file attachments (automatically Base64-encoded with optional content type).
- **Sensible `from` resolution** — uses the per-message `from` when supplied, otherwise falls back to the configured `default-from`.
- **Resilient by default** — delivery errors are caught and surfaced as a structured `EmailResponseDTO.error(...)` rather than propagating raw exceptions.
- **Named, secured HTTP client** — a dedicated `resend` `RestClient` is built with JSON content type and a `Bearer` authorization header derived from your API key.
- **Override-friendly** — every auto-configured bean is `@ConditionalOnMissingBean`, so you can supply your own `RestClient` or `EmailProvider` to customize behavior.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- `fireflyframework-notifications-core` on the classpath (transitively provided by this module)
- A [Resend](https://resend.com) account with an API key and at least one verified sending domain/address

## Installation

Add the adapter alongside the notifications core. The version is managed by the Firefly parent/BOM, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-resend</artifactId>
    <!-- version managed by the Firefly Framework parent/BOM -->
</dependency>
```

This adapter declares a compile-scoped dependency on `fireflyframework-notifications-core` and `fireflyframework-client`, so they are pulled in transitively.

If your project does not inherit the Firefly parent, import the BOM in `dependencyManagement` (or pin an explicit `<version>` such as `26.05.07`):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>26.05.07</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Quick Start

**1. Add the dependency** (see [Installation](#installation)).

**2. Select Resend and provide credentials** in `application.yml`:

```yaml
firefly:
  notifications:
    email:
      provider: resend          # selects this adapter as the active EmailProvider
    resend:
      api-key: ${RESEND_API_KEY}
      default-from: "Acme <no-reply@acme.com>"
```

**3. Inject `EmailProvider` and send a message** — your code depends only on the core SPI, never on Resend:

```java
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WelcomeEmailService {

    private final EmailProvider emailProvider; // ResendEmailProvider, auto-wired

    public WelcomeEmailService(EmailProvider emailProvider) {
        this.emailProvider = emailProvider;
    }

    public Mono<EmailResponseDTO> sendWelcome(String recipient) {
        EmailRequestDTO request = EmailRequestDTO.builder()
                .from("Acme <no-reply@acme.com>") // optional; falls back to firefly.notifications.resend.default-from
                .to(recipient)
                .subject("Welcome to Acme")
                .html("<h1>Welcome!</h1><p>Glad to have you on board.</p>")
                .text("Welcome! Glad to have you on board.")
                .build();

        return emailProvider.sendEmail(request); // -> EmailResponseDTO.success(messageId) or .error(reason)
    }
}
```

On success the returned `EmailResponseDTO` carries the Resend message id; on failure it carries the error reason (the exception is logged and swallowed, not propagated).

## Configuration

All properties live under the `firefly.notifications.resend` prefix, except the provider selector which is shared by the notifications core.

```yaml
firefly:
  notifications:
    email:
      provider: resend                       # required: activates this adapter
    resend:
      api-key: ${RESEND_API_KEY}             # required: your Resend API key (re_...)
      default-from: "Acme <no-reply@acme.com>" # optional: fallback sender when a message omits `from`
      base-url: https://api.resend.com        # optional: Resend API base URL (override for tests/proxies)
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.notifications.email.provider` | _(none)_ | Selector that activates this adapter. Must equal `resend` for the auto-configuration to apply. |
| `firefly.notifications.resend.api-key` | _(none)_ | Resend API key used as the `Bearer` token. **Required** — the `RestClient`/`EmailProvider` beans are only created when this is present. |
| `firefly.notifications.resend.default-from` | _(none)_ | Sender used when an `EmailRequestDTO` does not specify `from`. |
| `firefly.notifications.resend.base-url` | `https://api.resend.com` | Base URL of the Resend REST API. Override to point at a mock server in tests. |

The auto-configuration is gated by two conditions: `firefly.notifications.email.provider=resend` (class-level) and the presence of `firefly.notifications.resend.api-key` (on the `RestClient` bean). If either is missing, the adapter stays inactive and another provider may take over.

## How It Works

1. `ResendAutoConfiguration` is imported via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and activates when `firefly.notifications.email.provider=resend`.
2. It builds a named `resend` `RestClient` from `ServiceClient.rest("resend")` with the configured `base-url`, JSON content type, and an `Authorization: Bearer <api-key>` header.
3. It registers `ResendEmailProvider` as the `EmailProvider` bean (unless one is already defined).
4. `ResendEmailProvider.sendEmail(...)` translates the `EmailRequestDTO` into the Resend JSON payload (`from`, `to`, `subject`, `html`/`text`, `cc`, `bcc`, Base64 `attachments`) and `POST`s it to `/emails`, mapping the response id into `EmailResponseDTO.success(id)`.

## Documentation

- Framework hub and module catalog: [Firefly Framework on GitHub](https://github.com/fireflyframework)
- Notifications core SPI: [fireflyframework-notifications-core](https://github.com/fireflyframework/fireflyframework-notifications-core)
- Reactive HTTP client: [fireflyframework-client](https://github.com/fireflyframework/fireflyframework-client)
- Resend API reference: [resend.com/docs](https://resend.com/docs)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
