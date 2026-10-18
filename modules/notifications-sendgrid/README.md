# Firefly Framework - Notifications SendGrid

[![CI](https://github.com/fireflyframework/fireflyframework-notifications-sendgrid/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-notifications-sendgrid/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> SendGrid email delivery adapter for the Firefly Framework notifications abstraction — a reactive `EmailProvider` that sends transactional email (HTML/plain text, CC/BCC, attachments) through the SendGrid v3 Mail Send API.

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

`fireflyframework-notifications-sendgrid` is a pluggable **email provider adapter** for the Firefly Framework notifications subsystem. It implements the core
`EmailProvider` SPI (`org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider`) defined in
[`fireflyframework-notifications-core`](https://github.com/fireflyframework/fireflyframework-notifications), backing it with [Twilio SendGrid](https://www.twilio.com/en-us/sendgrid/email-api) as the delivery channel.

The notifications core exposes a transport-agnostic `EmailService` facade; the actual wire delivery is delegated to whichever `EmailProvider` bean is present on the classpath. This module supplies that bean — `SendGridEmailProvider` — so application code keeps depending only on the core abstraction and never on the SendGrid SDK directly. Swapping SendGrid for another email backend (for example a Resend or SMTP adapter) is a dependency-and-configuration change, not a code change.

The adapter is **selected** by setting `firefly.notifications.email.provider=sendgrid`. When that property matches and the SendGrid SDK is on the classpath, the bundled Spring Boot auto-configuration wires a `SendGrid` client (from your API key) and the `SendGridEmailProvider` automatically — no manual `@Bean` declarations required. Because it builds on the blocking `sendgrid-java` SDK, the adapter wraps each call in a `Mono` and offloads it to Reactor's `boundedElastic` scheduler so the WebFlux event loop is never blocked.

This is one of several interchangeable notification adapters in the framework. Sibling adapters include
[`fireflyframework-notifications-resend`](https://github.com/fireflyframework/fireflyframework-notifications-resend) (email),
[`fireflyframework-notifications-twilio`](https://github.com/fireflyframework/fireflyframework-notifications-twilio) (SMS),
and [`fireflyframework-notifications-firebase`](https://github.com/fireflyframework/fireflyframework-notifications-firebase) (push), all targeting the same core SPIs.

## Features

- **Reactive `EmailProvider` implementation** (`SendGridEmailProvider`) returning `Mono<EmailResponseDTO>`, with the blocking SendGrid SDK isolated on `Schedulers.boundedElastic()`.
- **SendGrid v3 Mail Send API** via the official `com.sendgrid:sendgrid-java` client (`POST mail/send`).
- **Rich message support** — single recipient with multiple **CC** and **BCC** addresses, **subject**, and body as either `text/html` (preferred when `html` is set) or `text/plain`.
- **Attachments** — binary `EmailAttachmentDTO` content is Base64-encoded and sent inline with content type, filename, and `attachment` disposition.
- **Message-ID capture** — extracts SendGrid's `X-Message-Id` response header into the `EmailResponseDTO` for correlation and delivery tracking.
- **Robust result mapping** — HTTP 2xx maps to `EmailResponseDTO.success(messageId)`; non-2xx responses and `IOException`s map to `EmailResponseDTO.error(...)` instead of throwing.
- **Spring Boot 3 auto-configuration** — activates only when `firefly.notifications.email.provider=sendgrid`, the SendGrid class is present, and an API key is configured; all beans are `@ConditionalOnMissingBean`, so your own definitions always win.
- **Drop-in adapter** — include the dependency alongside `fireflyframework-notifications-core`; no SendGrid types leak into application code.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A [SendGrid](https://www.twilio.com/en-us/sendgrid/email-api) account and a v3 **API key** with `Mail Send` permission
- A verified SendGrid **sender identity** (single sender or authenticated domain) for the `from` address
- `fireflyframework-notifications-core` on the classpath (provides the `EmailProvider` SPI and `EmailService` facade)

## Installation

Add the adapter alongside the notifications core. Versions are managed by the Firefly Framework parent/BOM, so you should not need to pin them explicitly.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-sendgrid</artifactId>
    <!-- version managed by the Firefly BOM / parent -->
</dependency>
```

This module declares a transitive dependency on `fireflyframework-notifications-core`, so adding it pulls in the `EmailProvider` SPI automatically. If you inherit the Firefly parent POM, you can omit the `<version>`:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.07</version>
</parent>
```

## Quick Start

**1. Add the dependencies** (core + this adapter):

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-notifications-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-notifications-sendgrid</artifactId>
    </dependency>
</dependencies>
```

**2. Select SendGrid and provide your API key** in `application.yml`:

```yaml
firefly:
  notifications:
    email:
      provider: sendgrid       # selects this adapter
    sendgrid:
      api-key: ${SENDGRID_API_KEY}
```

**3. Inject the core `EmailService`** and send — your code never touches SendGrid types:

```java
import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import reactor.core.publisher.Mono;

@Service
public class WelcomeMailer {

    private final EmailService emailService;

    public WelcomeMailer(EmailService emailService) {
        this.emailService = emailService;
    }

    public Mono<EmailResponseDTO> sendWelcome(String to) {
        EmailRequestDTO request = new EmailRequestDTO();
        request.setFrom("noreply@example.com");   // a verified SendGrid sender
        request.setTo(to);
        request.setSubject("Welcome to Firefly");
        request.setHtml("<h1>Welcome!</h1><p>Thanks for signing up.</p>");
        return emailService.sendEmail(request);
    }
}
```

The auto-configuration builds the `SendGrid` client and the `SendGridEmailProvider` bean for you. The returned `EmailResponseDTO`
carries `success`/`error` status and, on success, the SendGrid `X-Message-Id`.

You can also depend on the `EmailProvider` SPI directly if you prefer to bypass the `EmailService` facade — the same `SendGridEmailProvider` bean satisfies it.

## Configuration

All properties live under the `firefly.notifications` prefix. This adapter binds a single
`@ConfigurationProperties` class, `SendGridProperties` (prefix `firefly.notifications.sendgrid`).

```yaml
firefly:
  notifications:
    email:
      provider: sendgrid          # required: activates this adapter
    sendgrid:
      api-key: ${SENDGRID_API_KEY} # required: SendGrid v3 API key with Mail Send permission
```

| Property | Description | Default |
| --- | --- | --- |
| `firefly.notifications.email.provider` | Selects the active email adapter. Must equal `sendgrid` for this module's auto-configuration to activate. | _(none)_ |
| `firefly.notifications.sendgrid.api-key` | SendGrid v3 API key used to authenticate the `SendGrid` client. Required for the `SendGrid` bean to be created. Keep it out of source control (use an environment variable or secret manager). | _(none)_ |

Notes:

- The sender (`from`) address and recipients are supplied **per message** on the `EmailRequestDTO`, not via configuration — there are no global `from-email` / `from-name` properties. Ensure the `from` address is a SendGrid-verified sender or authenticated domain, otherwise SendGrid rejects the send.
- All adapter beans are `@ConditionalOnMissingBean`. To override delivery behaviour, declare your own `SendGrid` or `EmailProvider` bean and the auto-configured one steps aside.

## How It Works

`SendGridAutoConfiguration` activates only when `firefly.notifications.email.provider=sendgrid` **and** `com.sendgrid.SendGrid` is on the classpath. It then:

1. Creates a `SendGrid` client from `firefly.notifications.sendgrid.api-key` (only when the key is present).
2. Registers `SendGridEmailProvider` as the `EmailProvider`, unless another `EmailProvider` bean already exists.

On each `sendEmail(...)` call, `SendGridEmailProvider` builds a SendGrid `Mail` (mapping subject, HTML/text content, CC, BCC, and Base64-encoded attachments), issues a `POST mail/send`, and maps the result: 2xx -> `EmailResponseDTO.success(X-Message-Id)`, anything else (or an `IOException`) -> `EmailResponseDTO.error(...)`. The whole call runs via `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` to keep blocking I/O off the reactive event loop.

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Notifications core SPI and `EmailService` facade: [`fireflyframework-notifications-core`](https://github.com/fireflyframework/fireflyframework-notifications)
- SendGrid v3 Mail Send API reference: [docs.sendgrid.com — Mail Send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
