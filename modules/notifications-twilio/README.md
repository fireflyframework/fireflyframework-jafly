# Firefly Framework - Notifications Twilio

[![CI](https://github.com/fireflyframework/fireflyframework-notifications-twilio/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-notifications-twilio/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Twilio SMS adapter for the Firefly Framework notifications abstraction — a reactive `SMSProvider` that delivers text messages through the Twilio REST API.

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

`fireflyframework-notifications-twilio` is a pluggable **SMS provider adapter** for the Firefly Framework notifications subsystem. It implements the `SMSProvider` outbound port defined by the notifications core (`fireflyframework-notifications-core`) using [Twilio](https://www.twilio.com/) as the delivery backend.

The notifications subsystem follows a hexagonal (ports-and-adapters) design: the core module declares technology-agnostic ports such as `SMSProvider`, and infrastructure adapters supply concrete implementations. Application code depends only on the `SMSProvider` interface and stays decoupled from any specific vendor SDK. This adapter contributes one such implementation, `TwilioSMSProvider`, wired automatically through Spring Boot auto-configuration.

Adapter selection is **property-driven**: the adapter only activates when `firefly.notifications.sms.provider` is set to `twilio` (and the Twilio SDK is on the classpath). This makes the SMS backend swappable at deploy time without code changes — drop in a different adapter module and flip the property to switch providers.

The Twilio adapter is one of several notification adapters in the framework, alongside sibling modules such as `fireflyframework-notifications-sendgrid` and `fireflyframework-notifications-resend` (email) and `fireflyframework-notifications-firebase` (push). All of them plug into the same `fireflyframework-notifications-core` ports.

## Features

- **`SMSProvider` implementation** (`TwilioSMSProvider`) backed by the official Twilio Java SDK (`com.twilio.sdk:twilio`).
- **Fully reactive API** — `sendSMS` returns a Reactor `Mono<SMSResponseDTO>`; the blocking Twilio REST call is offloaded to `Schedulers.boundedElastic()` so it never blocks event-loop threads.
- **Property-driven activation** — only engages when `firefly.notifications.sms.provider=twilio`, leaving other SMS adapters inert and avoiding bean conflicts.
- **Spring Boot auto-configuration** (`TwilioAutoConfiguration`) initializes the Twilio client and registers the provider bean with zero boilerplate.
- **`@ConditionalOnMissingBean` beans** — bring your own `SMSProvider` to override the default while keeping the auto-config.
- **Defensive request validation** — null requests, blank recipient numbers, blank message bodies, and unconfigured sender numbers are turned into a structured `SMSResponseDTO.error(...)` rather than thrown exceptions.
- **Structured responses** — successful sends return the Twilio message SID via `SMSResponseDTO.success(sid)`; failures carry an error message and `FAILED` status.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A Twilio account with an Account SID, Auth Token, and a provisioned (or verified) sender phone number
- `fireflyframework-notifications-core` on the classpath (transitively included by this module)

## Installation

Add the adapter to your application. The version is managed by the Firefly Framework BOM / parent, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-twilio</artifactId>
    <!-- version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

This adapter transitively brings in `fireflyframework-notifications-core`, so you do not need to declare the core module separately.

## Quick Start

**1. Add the dependency** (see [Installation](#installation)).

**2. Select Twilio as the SMS provider and supply credentials** in `application.yml`:

```yaml
firefly:
  notifications:
    sms:
      provider: twilio          # activates this adapter
    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      phone-number: "+1234567890"   # your Twilio sender number (E.164)
```

**3. Inject the `SMSProvider` port and send a message** — your code stays vendor-agnostic:

```java
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OtpService {

    private final SMSProvider smsProvider;

    public OtpService(SMSProvider smsProvider) {
        this.smsProvider = smsProvider; // TwilioSMSProvider injected by auto-config
    }

    public Mono<SMSResponseDTO> sendOtp(String phoneNumber, String code) {
        SMSRequestDTO request = SMSRequestDTO.builder()
                .phoneNumber(phoneNumber)
                .message("Your verification code is " + code)
                .build();

        return smsProvider.sendSMS(request)
                .doOnNext(response -> {
                    if ("SENT".equals(response.getStatus())) {
                        // response.getMessageId() == Twilio message SID
                    }
                });
    }
}
```

A successful send returns an `SMSResponseDTO` with status `SENT` and the Twilio message SID in `messageId`. Validation failures (null request, blank number, blank message, or missing sender number) return status `FAILED` with a populated `errorMessage`.

## Configuration

The adapter is configured through two property groups: the shared notifications selector and the Twilio-specific credentials (`@ConfigurationProperties(prefix = "firefly.notifications.twilio")`).

```yaml
firefly:
  notifications:
    sms:
      provider: twilio          # required: must equal "twilio" to enable this adapter
    twilio:
      account-sid:              # Twilio Account SID (starts with "AC...")
      auth-token:               # Twilio Auth Token
      phone-number:             # Twilio sender number in E.164 format, e.g. +14155552671
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.notifications.sms.provider` | _(none)_ | Adapter selector. Must be `twilio` for this module's auto-configuration to activate. |
| `firefly.notifications.twilio.account-sid` | _(none)_ | Twilio Account SID used to authenticate with the Twilio REST API. |
| `firefly.notifications.twilio.auth-token` | _(none)_ | Twilio Auth Token paired with the Account SID. Keep this secret (env var / vault). |
| `firefly.notifications.twilio.phone-number` | _(none)_ | The "from" number messages are sent from. Must be a Twilio-owned or verified number in E.164 format. If unset, sends fail with `Sender phone number not configured`. |

> Tip: source `account-sid` and `auth-token` from environment variables or a secrets manager rather than committing them to `application.yml`.

## How It Works

`TwilioAutoConfiguration` is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is guarded by:

- `@ConditionalOnProperty(name = "firefly.notifications.sms.provider", havingValue = "twilio")` — opt-in selection.
- `@ConditionalOnClass(com.twilio.Twilio.class)` — only when the Twilio SDK is present.
- `@EnableConfigurationProperties(TwilioProperties.class)` — binds the `firefly.notifications.twilio.*` keys.

When active, it initializes the static Twilio client with `Twilio.init(accountSid, authToken)` and registers a `TwilioSMSProvider` as the `SMSProvider` bean (both `@ConditionalOnMissingBean`, so you can override either). `TwilioSMSProvider.sendSMS` validates the request, builds a Twilio `Message` (recipient, sender, body), executes the REST call on `Schedulers.boundedElastic()`, and maps the result to an `SMSResponseDTO`.

## Documentation

- Notifications core (ports/SPI): [`fireflyframework-notifications`](https://github.com/fireflyframework/fireflyframework-notifications)
- Firefly Framework organization and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Twilio Java SDK reference: [twilio.com/docs/libraries/java](https://www.twilio.com/docs/libraries/java)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
