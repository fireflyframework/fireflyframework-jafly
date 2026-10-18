# Firefly Framework - ECM eSignature Logalty

[![CI](https://github.com/fireflyframework/fireflyframework-ecm-esignature-logalty/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-ecm-esignature-logalty/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Logalty eSignature provider adapter for the Firefly Framework ECM abstraction — eIDAS-compliant electronic signatures (simple, advanced, qualified) with biometric, SMS-OTP and video signer identification.

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

This module is a **pluggable eSignature provider adapter** for the Firefly Framework Enterprise Content Management (ECM) abstraction. It implements the ECM signature SPI — `SignatureEnvelopePort` from `fireflyframework-ecm` — on top of [Logalty](https://www.logalty.com/), a Spanish qualified Trust Service Provider (TSP) whose certified electronic signature and digital evidence platform is compliant with the EU eIDAS regulation (Regulation (EU) No 910/2014).

ECM follows a hexagonal (ports-and-adapters) design: the core `fireflyframework-ecm` module defines provider-neutral ports for documents, content and signature envelopes, and each provider ships as a separate adapter module on the classpath. Applications program against the ECM ports; the concrete provider is selected at runtime by configuration. This adapter contributes the `LogaltySignatureEnvelopeAdapter` (annotated `@EcmAdapter(type = "logalty")`, mapped to `SignatureProvider.LOGALTY`) and wires it via Spring Boot auto-configuration.

The adapter is **provider-selected**: it activates only when `firefly.ecm.esignature.provider=logalty` is set. This makes Logalty a drop-in alternative to the sibling eSignature adapters — [`fireflyframework-ecm-esignature-docusign`](https://github.com/fireflyframework/fireflyframework-ecm-esignature-docusign) and [`fireflyframework-ecm-esignature-adobe-sign`](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign) — without touching application code. The adapter is fully reactive (Spring WebFlux `WebClient`, Project Reactor) and hardened with Resilience4j circuit breaker and retry around every outbound Logalty API call, plus OAuth2 `client_credentials` token management with automatic refresh.

> **Maturity note:** the current `LogaltySignatureEnvelopeAdapter` is a reference/scaffold implementation. The OAuth flow, resilience pipeline, status mapping and the core `createEnvelope` / `getEnvelope` / `sendEnvelope` / `getSigningUrl` / `archiveEnvelope` / `resendEnvelope` paths call the Logalty REST API, while some query operations (list-by-status, list-by-creator, expiring/completed lookups) and a few mutation paths (`updateEnvelope`, `voidEnvelope`) are placeholders to be completed against a live Logalty contract. Envelope-id mappings are held in-memory; replace with persistent storage for production.

## Features

- **eIDAS-compliant electronic signatures** — simple, advanced and qualified signature types via a configurable default (`default-signature-type`).
- **Implements the ECM signature SPI** — drop-in implementation of `SignatureEnvelopePort`; declared as an `@EcmAdapter` advertising `ESIGNATURE_ENVELOPES`, `ESIGNATURE_REQUESTS` and `SIGNATURE_VALIDATION` capabilities.
- **Provider-selected activation** — switched on by `firefly.ecm.esignature.provider=logalty`; coexists with other ECM eSignature adapters on the classpath without conflict.
- **Reactive, non-blocking** — built on Spring WebFlux `WebClient` and Project Reactor (`Mono`/`Flux`) end to end.
- **OAuth2 client-credentials auth** — automatic access-token acquisition, caching and refresh with a configurable expiration window.
- **Resilience built in** — Resilience4j `CircuitBreaker` (50% failure-rate threshold, sliding window of 10, automatic half-open transition) and `Retry` (configurable max attempts, exponential-friendly fixed wait) applied to all Logalty calls.
- **Strong signer identification** — opt-in biometric signature capture, SMS-OTP verification and video identification.
- **Embedded & remote signing** — supports both remote email signing ceremonies and embedded signing with a configurable return URL.
- **Long-term archival** — Logalty document retention with a configurable retention period.
- **Reminders** — automatic signature reminders with a configurable frequency.
- **Validated configuration** — `@ConfigurationProperties` with Jakarta Bean Validation constraints (required client credentials, bounded retries/timeouts/retention).
- **Spring Boot auto-configuration** — zero hand-wiring; everything is contributed through `LogaltyAdapterAutoConfiguration`.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- `fireflyframework-ecm` (ECM core ports) on the classpath
- A Logalty account with API credentials (client ID / client secret); production or sandbox endpoint

## Installation

Add the adapter alongside the ECM core module. The version is managed by the Firefly parent/BOM, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-logalty</artifactId>
    <!-- version managed by the Firefly BOM / parent POM -->
</dependency>
```

This module transitively brings in `fireflyframework-ecm`, but it is good practice to declare the core dependency explicitly:

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-ecm</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-ecm-esignature-logalty</artifactId>
    </dependency>
</dependencies>
```

## Quick Start

### 1. Add the dependency and select the provider

With the dependency on the classpath, activate the adapter by selecting Logalty as the eSignature provider and supplying credentials:

```yaml
firefly:
  ecm:
    esignature:
      provider: logalty          # selects this adapter (required to activate)
    adapter:
      logalty:
        client-id: ${LOGALTY_CLIENT_ID}
        client-secret: ${LOGALTY_CLIENT_SECRET}
        base-url: https://api.logalty.com
```

That is all the wiring required. `LogaltyAdapterAutoConfiguration` contributes the `WebClient`, `CircuitBreaker`, `Retry`, `ObjectMapper` and the `LogaltySignatureEnvelopeAdapter` bean automatically.

### 2. Use the ECM signature port

Inject the provider-neutral `SignatureEnvelopePort` — your code never references Logalty directly:

```java
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ContractSigningService {

    private final SignatureEnvelopePort envelopes; // backed by Logalty at runtime

    public ContractSigningService(SignatureEnvelopePort envelopes) {
        this.envelopes = envelopes;
    }

    public Mono<SignatureEnvelope> startContract(SignatureEnvelope draft, UUID sender) {
        return envelopes.createEnvelope(draft)
            .flatMap(created -> envelopes.sendEnvelope(created.getId(), sender));
    }

    public Mono<String> signingLink(UUID envelopeId, String email, String name) {
        return envelopes.getSigningUrl(envelopeId, email, name, null);
    }
}
```

Switching providers later is a configuration change only: swap the dependency and set `firefly.ecm.esignature.provider` to `docusign` or `adobe-sign`.

## Configuration

All properties live under the `firefly.ecm.adapter.logalty` prefix (bound by `LogaltyAdapterProperties`). The adapter only activates when `firefly.ecm.esignature.provider=logalty`.

```yaml
firefly:
  ecm:
    esignature:
      provider: logalty                    # activation switch (no default)
    adapter:
      logalty:
        # --- Authentication (client credentials required) ---
        client-id: ${LOGALTY_CLIENT_ID}        # required
        client-secret: ${LOGALTY_CLIENT_SECRET} # required
        username:                              # optional, alternative to OAuth
        password:                              # optional, alternative to OAuth

        # --- Endpoint ---
        base-url: https://api.logalty.com      # production; sandbox: https://api-sandbox.logalty.com
        api-version: v1
        sandbox-mode: false

        # --- Webhooks ---
        webhook-url:                           # receive signature status callbacks
        webhook-secret:                        # validate incoming webhook requests

        # --- HTTP / resilience ---
        connection-timeout: 30s                # Duration
        read-timeout: 60s                      # Duration
        max-retries: 3                         # 0..10
        token-expiration: 3600                 # seconds, 300..86400

        # --- Signing behaviour ---
        default-signature-type: ADVANCED       # SIMPLE | ADVANCED | QUALIFIED
        enable-embedded-signing: false
        return-url:                            # used for embedded signing
        enable-biometric-signature: false
        enable-sms-verification: false
        enable-video-identification: false

        # --- Messaging ---
        default-email-subject: "Firma requerida / Signature required"
        default-email-message: "Por favor, revise y firme el documento adjunto / Please review and sign the attached document."

        # --- Reminders & retention ---
        enable-reminders: true
        reminder-frequency-days: 3             # 1..30
        enable-document-retention: true
        document-retention-days: 365           # 1..3650
```

### Key properties

| Property | Default | Description |
|----------|---------|-------------|
| `firefly.ecm.esignature.provider` | _(none)_ | Must equal `logalty` to activate this adapter. |
| `client-id` | _(required)_ | Logalty API client ID used for OAuth2 `client_credentials`. |
| `client-secret` | _(required)_ | Logalty API client secret. |
| `base-url` | `https://api.logalty.com` | Logalty API endpoint; use the sandbox host for testing. |
| `api-version` | `v1` | API version segment used when building request URIs. |
| `default-signature-type` | `ADVANCED` | eIDAS signature level: `SIMPLE`, `ADVANCED` or `QUALIFIED`. |
| `connection-timeout` / `read-timeout` | `30s` / `60s` | HTTP connection and response timeouts. |
| `max-retries` | `3` | Retry attempts for transient failures (`0`–`10`). |
| `token-expiration` | `3600` | OAuth token lifetime in seconds (`300`–`86400`). |
| `enable-biometric-signature` | `false` | Capture biometric signature data. |
| `enable-sms-verification` | `false` | Require SMS-OTP signer verification. |
| `enable-video-identification` | `false` | Require video identification of signers. |
| `enable-embedded-signing` / `return-url` | `false` / _(none)_ | Enable in-app embedded signing and the post-signature redirect URL. |
| `enable-document-retention` / `document-retention-days` | `true` / `365` | Long-term archival in Logalty (`1`–`3650` days). |
| `enable-reminders` / `reminder-frequency-days` | `true` / `3` | Automatic signature reminders (`1`–`30` days). |
| `webhook-url` / `webhook-secret` | _(none)_ | Endpoint and shared secret for inbound status callbacks. |

`client-id` and `client-secret` are validated as `@NotBlank`; the bounded numeric properties are enforced via `@Min`/`@Max`. Invalid configuration fails fast at startup.

## How It Works

`LogaltyAdapterAutoConfiguration` is registered through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is gated by `@ConditionalOnClass({WebClient.class, CircuitBreaker.class})` and `@ConditionalOnProperty(firefly.ecm.esignature.provider=logalty)`. When active it contributes (each as `@ConditionalOnMissingBean`, so any can be overridden):

- a reactive `WebClient` bound to `base-url` with the configured response timeout;
- a named `logaltyCircuitBreaker` (`CircuitBreaker`) — 50% failure-rate threshold, 30s open state, sliding window of 10, automatic open→half-open transition;
- a named `logaltyRetry` (`Retry`) — `max-retries` attempts retrying socket/IO/`WebClientRequestException` failures;
- a Logalty-tuned `ObjectMapper`; and
- the `LogaltySignatureEnvelopeAdapter` (`SignatureEnvelopePort`) wired with the ECM `DocumentPort` and `DocumentContentPort`.

Every outbound call goes through `ensureValidAccessToken()` (cached OAuth2 token with a 60-second safety margin) and is decorated with the circuit breaker and retry operators (`transformDeferred(CircuitBreakerOperator.of(...))` / `RetryOperator.of(...)`). Logalty status values are normalised to the ECM `EnvelopeStatus` enum (`DRAFT`, `SENT`, `COMPLETED`, `VOIDED`, `EXPIRED`).

## Documentation

- Firefly Framework organization and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- ECM core ports (the SPI this adapter implements): [fireflyframework-ecm](https://github.com/fireflyframework/fireflyframework-ecm)
- Sibling eSignature adapters: [DocuSign](https://github.com/fireflyframework/fireflyframework-ecm-esignature-docusign) · [Adobe Sign](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign)
- Logalty platform: [logalty.com](https://www.logalty.com/)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
