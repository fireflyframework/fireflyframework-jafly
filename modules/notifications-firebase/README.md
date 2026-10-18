# Firefly Framework - Notifications Firebase

[![CI](https://github.com/fireflyframework/fireflyframework-notifications-firebase/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-notifications-firebase/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Firebase Cloud Messaging (FCM) push adapter for the Firefly Framework notifications abstraction — reactive mobile and web push delivery via the Firebase Admin SDK.

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

`fireflyframework-notifications-firebase` is a **pluggable push provider adapter** for the Firefly Framework notifications subsystem. It implements the `PushProvider` SPI defined in `fireflyframework-notifications-core` and delivers push notifications through [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) using the official Firebase Admin SDK.

The notifications core defines transport-agnostic provider interfaces (`PushProvider`, `EmailProvider`, `SMSProvider`) and reactive request/response DTOs. Each concrete delivery channel ships as its own thin adapter module so applications depend only on the providers they actually use. This module supplies the FCM-backed implementation, `FcmPushProvider`, and the Spring Boot auto-configuration that wires it.

The adapter is **selected by configuration, not by classpath alone**. Auto-configuration activates only when `firefly.notifications.push.provider=firebase` is set, so multiple push adapters can coexist on the classpath and exactly one is chosen at runtime. When active, the module builds a `FirebaseApp` and `FirebaseMessaging` client from your service-account credentials and registers a `PushProvider` bean that the notifications core consumes — no application code changes required beyond adding the dependency and setting the selector property.

### Sibling modules

| Module | Channel | Provider |
| --- | --- | --- |
| `fireflyframework-notifications-core` | Core SPI + DTOs (`PushProvider`, `EmailProvider`, `SMSProvider`) | — |
| **`fireflyframework-notifications-firebase`** | **Push** | **Firebase Cloud Messaging (FCM)** |
| `fireflyframework-notifications-twilio` | SMS | Twilio |
| `fireflyframework-notifications-sendgrid` | Email | SendGrid |
| `fireflyframework-notifications-resend` | Email | Resend |

## Features

- **`PushProvider` implementation** (`FcmPushProvider`) backed by Firebase Cloud Messaging.
- **Selector-driven auto-configuration** — activated only when `firefly.notifications.push.provider=firebase`, so it can sit alongside other push adapters without conflict.
- **Reactive, non-blocking API** — `sendPush` returns a `Mono<PushNotificationResponse>`; the blocking FCM call is offloaded to `Schedulers.boundedElastic()`.
- **Flexible credential resolution** — point at a service-account JSON file via `credentials-path`, or fall back to [Google Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials) when none is supplied.
- **Token-targeted delivery with data payloads** — sets FCM `title`/`body` notification fields and forwards arbitrary `data` key/value pairs from the request.
- **Override-friendly beans** — the `FirebaseApp`, `FirebaseMessaging`, and `PushProvider` beans are all `@ConditionalOnMissingBean`, so you can supply your own client or provider.
- **Safe FirebaseApp reuse** — reuses an existing default `FirebaseApp` if one is already initialized, avoiding duplicate-app errors.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A Firebase project with Cloud Messaging enabled and a service-account key (JSON), or an environment configured for Google Application Default Credentials
- `fireflyframework-notifications-core` on the classpath (transitively pulled in by this module)

## Installation

Add the dependency alongside the notifications core. The version is managed by the Firefly Framework BOM / parent POM, so you should not need to specify it explicitly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-firebase</artifactId>
</dependency>
```

This module transitively brings in `fireflyframework-notifications-core` and the `com.google.firebase:firebase-admin` SDK.

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
        <artifactId>fireflyframework-notifications-firebase</artifactId>
    </dependency>
</dependencies>
```

**2. Select and configure the Firebase adapter** in `application.yml`:

```yaml
firefly:
  notifications:
    push:
      provider: firebase          # selects this adapter
    firebase:
      credentials-path: /etc/secrets/firebase-service-account.json
      project-id: my-firebase-project
```

**3. Inject and use the `PushProvider`** — your code depends on the core SPI, not on Firebase directly:

```java
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationRequest;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationResponse;
import org.fireflyframework.notifications.interfaces.providers.push.v1.PushProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class AlertService {

    private final PushProvider pushProvider; // FcmPushProvider injected by auto-config

    public AlertService(PushProvider pushProvider) {
        this.pushProvider = pushProvider;
    }

    public Mono<PushNotificationResponse> notifyDevice(String deviceToken) {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .token(deviceToken)
                .title("Payment received")
                .body("Your transfer of €250.00 has been completed.")
                .data(Map.of("type", "PAYMENT", "txId", "abc-123"))
                .build();

        return pushProvider.sendPush(request);
    }
}
```

On success the returned `PushNotificationResponse` carries the FCM `messageId` and `success = true`; on failure it returns `success = false` with an `errorMessage`.

## Configuration

All properties live under the `firefly.notifications.firebase` prefix (bound by `FcmProperties`). The adapter is enabled by the separate selector property `firefly.notifications.push.provider`.

```yaml
firefly:
  notifications:
    push:
      provider: firebase          # required: activates the Firebase auto-configuration
    firebase:
      credentials-path:           # path to service-account JSON; empty = Application Default Credentials
      project-id:                 # Firebase / GCP project id (optional if inferable from credentials)
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.notifications.push.provider` | _(none)_ | Selector that activates this adapter. Must equal `firebase` for the Firebase auto-configuration to apply. |
| `firefly.notifications.firebase.credentials-path` | _(empty)_ | Filesystem path to a Firebase service-account JSON key. When blank, the adapter falls back to Google Application Default Credentials. |
| `firefly.notifications.firebase.project-id` | _(empty)_ | Firebase / GCP project id. Set explicitly when it cannot be inferred from the credentials. |

## How It Works

`FcmAutoConfiguration` is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is gated by:

- `@ConditionalOnProperty(name = "firefly.notifications.push.provider", havingValue = "firebase")` — the runtime selector.
- `@ConditionalOnClass(FirebaseMessaging.class)` — the Firebase Admin SDK must be present.

When both conditions hold it contributes three beans (each `@ConditionalOnMissingBean`, so all are overridable):

1. **`FirebaseApp`** — built from `credentials-path` (or Application Default Credentials) and `project-id`; an already-initialized default app is reused if present.
2. **`FirebaseMessaging`** — obtained from the `FirebaseApp`.
3. **`PushProvider`** — an `FcmPushProvider` wrapping the `FirebaseMessaging` client, consumed by the notifications core.

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Notifications core SPI: [`fireflyframework-notifications-core`](https://github.com/fireflyframework/fireflyframework-notifications)
- Firebase Cloud Messaging: [firebase.google.com/docs/cloud-messaging](https://firebase.google.com/docs/cloud-messaging)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
