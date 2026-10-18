# Firefly Framework - Notifications

[![CI](https://github.com/fireflyframework/fireflyframework-notifications/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-notifications/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive, provider-agnostic notifications core for Spring Boot — email, SMS and push ports with pluggable SendGrid, Resend, Twilio and Firebase adapters, FreeMarker templating, per-user channel preferences and built-in Micrometer metrics.

---

## Table of Contents

- [Overview](#overview)
  - [Modules](#modules)
  - [Provider Adapters](#provider-adapters)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Notifications is the **core notifications abstraction** for the Firefly platform. It defines the application services and outbound ports for sending notifications across three channels — **email**, **SMS** and **push** — and lets you swap concrete delivery providers without touching business logic.

The module follows a strict **hexagonal (ports & adapters)** design. Your code depends only on the channel services (`EmailService`, `SMSService`, `PushService`) and the outbound port interfaces (`EmailProvider`, `SMSProvider`, `PushProvider`). The actual integration with a SaaS provider lives in a **separate adapter module** (SendGrid, Resend, Twilio, Firebase) that you add as a dependency and select with a single property. Because the ports are the only contract, you can switch from, say, SendGrid to Resend by changing a dependency and one config line — no code changes.

Everything is **reactive end to end**: services and ports return Project Reactor `Mono` types, so notification dispatch composes naturally into WebFlux pipelines and never blocks request threads. On top of the raw send path the core adds two cross-cutting concerns out of the box: a **FreeMarker template engine** for rendering templated emails by ID, and a **notification preference service** so deliveries can respect per-user, per-channel opt-outs. A Spring Boot auto-configuration wires shared **Micrometer** instrumentation (`NotificationMetrics`) that every adapter reuses to emit consistent send/latency/error metrics tagged by channel and provider.

This repository ships only the contracts, DTOs, services and observability — it has **no opinionated runtime provider of its own**. Pick one (or more) of the sibling adapter modules below to actually deliver messages.

### Modules

This is an aggregator (`pom` packaging) project. It currently contains a single submodule; the structure leaves room for additional core submodules without breaking coordinates.

| Module | Artifact | Purpose |
| --- | --- | --- |
| Notifications Core | `fireflyframework-notifications-core` | Channel services (email/SMS/push), outbound provider ports, request/response DTOs, FreeMarker template engine, in-memory notification preference service, and Micrometer observability auto-configuration. |

### Provider Adapters

Concrete delivery is supplied by these **separate adapter repositories**, each implementing the matching port from this core and activating via a `firefly.notifications.<channel>.provider` property:

| Adapter | Channel | Implements | Selecting property |
| --- | --- | --- | --- |
| [`fireflyframework-notifications-sendgrid`](https://github.com/fireflyframework/fireflyframework-notifications-sendgrid) | Email | `EmailProvider` | `firefly.notifications.email.provider=sendgrid` |
| [`fireflyframework-notifications-resend`](https://github.com/fireflyframework/fireflyframework-notifications-resend) | Email | `EmailProvider` | `firefly.notifications.email.provider=resend` |
| [`fireflyframework-notifications-twilio`](https://github.com/fireflyframework/fireflyframework-notifications-twilio) | SMS | `SMSProvider` | `firefly.notifications.sms.provider=twilio` |
| [`fireflyframework-notifications-firebase`](https://github.com/fireflyframework/fireflyframework-notifications-firebase) | Push | `PushProvider` | `firefly.notifications.push.provider=firebase` |

## Features

- **Reactive channel services** — `EmailService`, `SMSService` and `PushService`, each returning `Mono<...ResponseDTO>` for non-blocking dispatch.
- **Hexagonal outbound ports** — `EmailProvider`, `SMSProvider`, `PushProvider`. Add an adapter module and a config property to bind an implementation; no code changes to switch providers.
- **Rich email model** — `EmailRequestDTO` with `from`/`to`, CC/BCC lists, plain-text and HTML bodies, Bean Validation (`@Email`, `@NotBlank`), and `EmailAttachmentDTO` for byte-content attachments with MIME type.
- **Templated email** — `sendTemplateEmail(EmailTemplateRequestDTO)` renders a template by `templateId` with a variable map before delivery. Backed by the pluggable `NotificationTemplateEngine` SPI.
- **FreeMarker template engine** — `FreemarkerNotificationTemplateEngine` loads `.ftl` templates from a configurable classpath prefix (default `/notification-templates`) and an optional filesystem directory; rendering runs on a bounded-elastic scheduler.
- **Per-user channel preferences** — `NotificationPreferenceService` with a ready-to-use `InMemoryNotificationPreferenceService`; toggle email/SMS/push per user (with per-channel overrides) and check `isChannelEnabled(userId, channel)` before sending.
- **Consistent response contracts** — `EmailResponseDTO` (with `EmailStatusEnum` SENT/FAILED and `success(...)`/`error(...)` factories), `SMSResponseDTO`, and `PushNotificationResponse`, each carrying message ID, status and error message.
- **Built-in observability** — `NotificationObservabilityAutoConfiguration` registers a shared `NotificationMetrics` bean (when a `MeterRegistry` is present) emitting `firefly.notifications.sent`, `firefly.notifications.delivery.duration`, `firefly.notifications.errors` and `firefly.notifications.templates.rendered`, tagged by `channel`, `provider`, `status` and `template`.
- **Reusable by every adapter** — adapters wrap their dispatch in `NotificationMetrics.timedDispatch(channel, provider, mono)` so all providers report metrics the same way.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- At least one provider adapter (SendGrid, Resend, Twilio, or Firebase) plus its account/API credentials to actually deliver notifications
- A `MeterRegistry` (e.g. Micrometer + Actuator) on the classpath if you want notification metrics
- FreeMarker on the classpath only if you use templated email (it is an optional dependency)

## Installation

The notifications core is consumed transitively by every provider adapter, so in most applications you simply add the adapter you need (see [Provider Adapters](#provider-adapters)). To depend on the core contracts directly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-core</artifactId>
    <!-- Version is managed by the Firefly BOM / parent; omit it when inheriting fireflyframework-parent -->
</dependency>
```

The version is governed by the Firefly Framework parent/BOM. When your project inherits `fireflyframework-parent` (or imports the Firefly BOM), you can omit the `<version>` entirely.

## Quick Start

**1. Add a provider adapter** (here: SendGrid for email). The adapter brings `fireflyframework-notifications-core` in transitively.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-sendgrid</artifactId>
</dependency>
```

**2. Select and configure the provider** in `application.yml`:

```yaml
firefly:
  notifications:
    email:
      provider: sendgrid        # binds the SendGrid EmailProvider adapter
    sendgrid:
      api-key: ${SENDGRID_API_KEY}
```

**3. Inject the channel service and send** — the `EmailProvider` adapter is auto-wired behind `EmailService`:

```java
import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OrderNotificationService {

    private final EmailService emailService;

    public OrderNotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    public Mono<EmailResponseDTO> sendOrderConfirmation(String customerEmail, String orderId) {
        EmailRequestDTO request = EmailRequestDTO.builder()
                .from("orders@example.com")
                .to(customerEmail)
                .subject("Order Confirmation - " + orderId)
                .html("<h1>Thanks!</h1><p>Your order " + orderId + " is confirmed.</p>")
                .build();
        return emailService.sendEmail(request);
    }
}
```

**Templated email** — provide a `NotificationTemplateEngine` bean (the FreeMarker engine renders `.ftl` files from `/notification-templates`) and call `sendTemplateEmail`:

```java
@Bean
NotificationTemplateEngine notificationTemplateEngine() {
    return new FreemarkerNotificationTemplateEngine(); // classpath:/notification-templates
}

// ...
EmailTemplateRequestDTO request = EmailTemplateRequestDTO.builder()
        .from("welcome@example.com")
        .to(user.getEmail())
        .subject("Welcome!")
        .templateId("welcome-email")                 // resolves welcome-email.ftl
        .templateVariables(Map.of("firstName", user.getFirstName()))
        .build();
return emailService.sendTemplateEmail(request);
```

**Respect user preferences** before dispatching:

```java
return preferenceService.isChannelEnabled(userId, "email")
        .filter(Boolean::booleanValue)
        .flatMap(enabled -> emailService.sendEmail(request));
```

SMS and push follow the same pattern via `SMSService.sendSMS(SMSRequestDTO)` and `PushService.sendPush(PushNotificationRequest)`, selecting the Twilio and Firebase adapters respectively.

## Configuration

This core module exposes one auto-configuration toggle; the bulk of configuration (API keys, sender identities, provider selection) lives in the provider adapter you choose.

```yaml
firefly:
  notifications:
    # Provider selection — supplied by the adapter modules you add (see table below).
    email:
      provider: sendgrid        # or: resend
    sms:
      provider: twilio
    push:
      provider: firebase
  observability:
    metrics:
      enabled: true             # default; set false to skip NotificationMetrics registration
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.notifications.email.provider` | _(none)_ | Selects the email `EmailProvider` adapter — `sendgrid` or `resend`. Defined by the adapter module. |
| `firefly.notifications.sms.provider` | _(none)_ | Selects the SMS `SMSProvider` adapter — `twilio`. Defined by the adapter module. |
| `firefly.notifications.push.provider` | _(none)_ | Selects the push `PushProvider` adapter — `firebase`. Defined by the adapter module. |
| `firefly.observability.metrics.enabled` | `true` | When `true` (and a `MeterRegistry` is present), registers the shared `NotificationMetrics` bean used by all adapters. |

Provider-specific keys (for example `firefly.notifications.sendgrid.*`, `firefly.notifications.twilio.*`, `firefly.notifications.firebase.*`) are documented in each adapter's README.

**Template engine** — `FreemarkerNotificationTemplateEngine` is not auto-registered; declare it as a bean. Its constructor accepts a classpath prefix (default `/notification-templates`) and an optional filesystem directory for template overrides. Without a `NotificationTemplateEngine` bean, `sendTemplateEmail` fails fast with an `UnsupportedOperationException`.

**Notification preferences** — `InMemoryNotificationPreferenceService` is suitable for development and single-instance deployments. For multi-instance/production usage, provide your own `NotificationPreferenceService` backed by R2DBC or a cache.

## Documentation

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — in-depth hexagonal architecture guide: ports & adapters, layer responsibilities, dependency flow, and how to add a new provider.
- [Firefly Framework Module Catalog](https://github.com/fireflyframework) — the full set of framework modules and adapters.
- Adapter READMes: [SendGrid](https://github.com/fireflyframework/fireflyframework-notifications-sendgrid) · [Resend](https://github.com/fireflyframework/fireflyframework-notifications-resend) · [Twilio](https://github.com/fireflyframework/fireflyframework-notifications-twilio) · [Firebase](https://github.com/fireflyframework/fireflyframework-notifications-firebase).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
