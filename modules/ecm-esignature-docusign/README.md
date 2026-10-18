# Firefly Framework - ECM eSignature DocuSign Adapter

[![CI](https://github.com/fireflyframework/fireflyframework-ecm-esignature-docusign/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-ecm-esignature-docusign/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> DocuSign eSignature provider adapter for the Firefly Framework ECM abstraction — plugs into the ECM core's `SignatureEnvelopePort` to create, send, void, and track signing envelopes through the DocuSign eSignature REST API.

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

This module is a **provider adapter** for the Firefly Framework Enterprise Content Management (ECM) abstraction. The ECM core ([`fireflyframework-ecm`](https://github.com/fireflyframework/fireflyframework-ecm)) defines a hexagonal (port/adapter) architecture with provider-neutral ports for document management, e-signature workflows, intelligent document processing, and folder security. This adapter implements the **e-signature envelope port** using [DocuSign](https://www.docusign.com/) as the backing provider.

Concretely, the module supplies `DocuSignSignatureEnvelopeAdapter`, a reactive (Project Reactor) implementation of the ECM core's `SignatureEnvelopePort`. It wraps the official DocuSign eSignature Java SDK (`docusign-esign-java`) and translates Firefly's `SignatureEnvelope` domain model into DocuSign `EnvelopeDefinition` calls for envelope creation, sending, updating, voiding, status sync, and status-based listing. The adapter is registered with the ECM adapter registry through the `@EcmAdapter` annotation, advertising the `ESIGNATURE_ENVELOPES`, `ESIGNATURE_REQUESTS`, and `SIGNATURE_VALIDATION` capabilities.

The adapter authenticates with DocuSign using **OAuth 2.0 JWT Grant** (service-account / impersonation flow): on startup it requests a JWT user token with the `signature` and `impersonation` scopes, validates that the configured account is accessible, and configures a shared `ApiClient` for all subsequent envelope operations.

It selects itself automatically — both the auto-configuration and the adapter bean are gated on the ECM core's provider-selection property `firefly.ecm.esignature.provider=docusign`. Add this module to the classpath alongside the ECM core, set that property, supply your DocuSign JWT credentials, and the e-signature port is wired to DocuSign with no further code.

### Sibling adapters

| Provider | Module | Selector value |
|----------|--------|----------------|
| DocuSign (this module) | `fireflyframework-ecm-esignature-docusign` | `firefly.ecm.esignature.provider=docusign` |
| Adobe Sign | [`fireflyframework-ecm-esignature-adobe-sign`](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign) | `adobe-sign` |
| Logalty | [`fireflyframework-ecm-esignature-logalty`](https://github.com/fireflyframework/fireflyframework-ecm-esignature-logalty) | `logalty` |

Storage providers are published separately as [`fireflyframework-ecm-storage-aws`](https://github.com/fireflyframework/fireflyframework-ecm-storage-aws) and [`fireflyframework-ecm-storage-azure`](https://github.com/fireflyframework/fireflyframework-ecm-storage-azure).

## Features

- **Implements the ECM `SignatureEnvelopePort`** — drop-in e-signature provider for any service built on `fireflyframework-ecm`.
- **Reactive, non-blocking API surface** — all operations return Reactor `Mono`/`Flux`, keeping ECM service code reactive end to end.
- **Full envelope lifecycle** — create, get, update, send, void, and delete (void) envelopes via the DocuSign `EnvelopesApi`.
- **Status tracking & listing** — `syncEnvelopeStatus`, `existsEnvelope`, lookup by external envelope ID, and `getEnvelopesByStatus` backed by DocuSign list-status-changes, with Firefly `EnvelopeStatus` ↔ DocuSign status mapping.
- **OAuth 2.0 JWT Grant authentication** — service-account impersonation flow with `signature`/`impersonation` scopes, token acquisition and account-access validation on startup.
- **Sandbox / production switching** — a single `sandbox-mode` flag retargets both the REST base path (`demo.docusign.net`) and the OAuth auth server (`account-d.docusign.com`).
- **Spring Boot auto-configuration** — `DocuSignAdapterAutoConfiguration` builds and exposes the DocuSign `ApiClient` bean; `@ConditionalOnMissingBean` lets you override it.
- **Adapter registry integration** — declared with `@EcmAdapter(type = "docusign", ...)` so the ECM core can discover its supported features and required/optional properties.
- **Validated, typed configuration** — `DocuSignAdapterProperties` (`@Validated` / `@ConfigurationProperties`) with `@NotBlank` credentials, `Duration` timeouts, and sensible defaults.
- **Webhook (DocuSign Connect) hooks** — configurable webhook URL and shared secret for inbound status callbacks.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- The ECM core module `fireflyframework-ecm` on the classpath
- A **DocuSign account** with an [Integration Key (client ID)](https://developers.docusign.com/) configured for **JWT Grant**, an RSA private key, the impersonated **user ID** (API username / GUID), and the target **account ID**

## Installation

Add the dependency. The version is managed by the Firefly Framework parent/BOM, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-docusign</artifactId>
    <!-- version managed by the Firefly Framework parent/BOM -->
</dependency>
```

This adapter transitively brings in `fireflyframework-ecm` (the ECM core ports and domain model) and the DocuSign eSignature Java SDK, so adding it to a service that already inherits the Firefly parent is sufficient.

## Quick Start

**1. Add the dependency** (see [Installation](#installation)) to a service that already builds on `fireflyframework-ecm`.

**2. Select DocuSign and provide JWT credentials** in `application.yml`:

```yaml
firefly:
  ecm:
    features:
      esignature: true
    esignature:
      provider: docusign          # selects this adapter
    adapter:
      docusign:
        integration-key: ${DOCUSIGN_INTEGRATION_KEY}
        user-id: ${DOCUSIGN_USER_ID}
        account-id: ${DOCUSIGN_ACCOUNT_ID}
        private-key: ${DOCUSIGN_PRIVATE_KEY}   # RSA private key (PEM)
        sandbox-mode: ${DOCUSIGN_SANDBOX:false}
```

**3. Inject the ECM port** — your code depends only on the provider-neutral `SignatureEnvelopePort`, never on DocuSign types:

```java
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ContractService {

    private final SignatureEnvelopePort envelopes; // bound to DocuSign at runtime

    public ContractService(SignatureEnvelopePort envelopes) {
        this.envelopes = envelopes;
    }

    public Mono<SignatureEnvelope> startSigning() {
        SignatureEnvelope envelope = SignatureEnvelope.builder()
                .title("Please sign this document")
                .description("Please review and sign the attached document(s).")
                .build();

        return envelopes.createEnvelope(envelope)                       // creates a DocuSign draft envelope
                .flatMap(created -> envelopes.sendEnvelope(created.getId(), UUID.randomUUID()));
    }
}
```

Swapping providers later (e.g. to Adobe Sign or Logalty) means changing the dependency and the `firefly.ecm.esignature.provider` value — the `SignatureEnvelopePort`-based code above stays unchanged.

## Configuration

All properties live under the `firefly.ecm.adapter.docusign` prefix and are bound by `DocuSignAdapterProperties`. Provider selection itself is the ECM core property `firefly.ecm.esignature.provider`.

```yaml
firefly:
  ecm:
    esignature:
      provider: docusign                 # required to activate this adapter
    adapter:
      docusign:
        # --- Credentials (required) ---
        integration-key: ${DOCUSIGN_INTEGRATION_KEY}   # OAuth client ID / Integration Key
        user-id: ${DOCUSIGN_USER_ID}                   # impersonated API user (GUID)
        account-id: ${DOCUSIGN_ACCOUNT_ID}             # target DocuSign account ID
        private-key: ${DOCUSIGN_PRIVATE_KEY}           # RSA private key (PEM) for JWT Grant

        # --- Endpoints ---
        base-url: https://na3.docusign.net/restapi     # production REST base (ignored when sandbox-mode=true)
        auth-server: https://account.docusign.com      # production OAuth server (ignored when sandbox-mode=true)
        sandbox-mode: false                            # true => demo.docusign.net + account-d.docusign.com

        # --- HTTP / retry ---
        connection-timeout: 30s
        read-timeout: 60s
        max-retries: 3

        # --- JWT ---
        jwt-expiration: 3600                            # requested token lifetime, seconds

        # --- Webhook (DocuSign Connect) ---
        webhook-url:                                    # inbound status callback URL
        webhook-secret:                                 # shared secret for callback verification

        # --- Polling ---
        enable-polling: true
        polling-interval: 5m

        # --- Defaults ---
        default-email-subject: "Please sign this document"
        default-email-message: "Please review and sign the attached document(s)."
```

| Property | Default | Description |
|----------|---------|-------------|
| `integration-key` | _(required)_ | DocuSign Integration Key (OAuth client ID) configured for JWT Grant. |
| `user-id` | _(required)_ | API user GUID to impersonate via the JWT Grant flow. |
| `account-id` | _(required)_ | DocuSign account ID; validated for access on startup. |
| `private-key` | _(required)_ | RSA private key (PEM) used to sign the JWT assertion. |
| `base-url` | `https://na3.docusign.net/restapi` | Production eSignature REST base path. Overridden by `demo.docusign.net` when `sandbox-mode=true`. |
| `auth-server` | `https://account.docusign.com` | Production OAuth auth server. Overridden by `account-d.docusign.com` when `sandbox-mode=true`. |
| `sandbox-mode` | `false` | When `true`, routes both REST and OAuth traffic to the DocuSign developer/demo environment. |
| `connection-timeout` | `30s` | `ApiClient` connect timeout. |
| `read-timeout` | `60s` | `ApiClient` read timeout. |
| `max-retries` | `3` | Maximum retry attempts for adapter operations. |
| `jwt-expiration` | `3600` | Requested JWT user-token lifetime, in seconds. |
| `webhook-url` | _(none)_ | DocuSign Connect callback URL for inbound envelope status events. |
| `webhook-secret` | _(none)_ | Shared secret used to verify inbound webhook callbacks. |
| `enable-polling` | `true` | Enable periodic envelope-status polling. |
| `polling-interval` | `5m` | Interval between status polls when polling is enabled. |
| `default-email-subject` | `Please sign this document` | Fallback email subject for new envelopes. |
| `default-email-message` | `Please review and sign the attached document(s).` | Fallback email body for new envelopes. |

> The credential properties (`integration-key`, `user-id`, `account-id`, `private-key`) are `@NotBlank` — the context fails fast at startup if any is missing. Keep secrets out of source control; inject them via environment variables or a secrets manager as shown above.

## How It Works

1. **Activation** — `DocuSignAdapterAutoConfiguration` and `DocuSignSignatureEnvelopeAdapter` are both `@ConditionalOnProperty(firefly.ecm.esignature.provider=docusign)`, and the auto-config is also `@ConditionalOnClass(ApiClient.class)`, so the adapter only engages when DocuSign is selected and the SDK is present.
2. **Authentication** — the auto-configuration builds a DocuSign `ApiClient`, sets the base path / timeouts, and performs the **JWT Grant** flow (`requestJWTUserToken` with `signature` + `impersonation` scopes), then validates that `account-id` is among the authenticated user's accounts.
3. **Operations** — `DocuSignSignatureEnvelopeAdapter` wraps `EnvelopesApi` and maps Firefly's `SignatureEnvelope` model to DocuSign `EnvelopeDefinition`/`Envelope` calls, returning reactive `Mono`/`Flux`. Internal maps track the Firefly `UUID` ↔ DocuSign envelope-ID correspondence.
4. **Override points** — the `ApiClient` bean is `@ConditionalOnMissingBean`, so you can supply your own pre-authenticated client (e.g. a custom auth strategy or a test stub) and the adapter will use it.

## Documentation

- Framework documentation hub and module catalog: [fireflyframework/fireflyframework](https://github.com/fireflyframework)
- ECM core (ports, domain model, adapter registry): [fireflyframework-ecm](https://github.com/fireflyframework/fireflyframework-ecm)
- Sibling e-signature adapters: [adobe-sign](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign), [logalty](https://github.com/fireflyframework/fireflyframework-ecm-esignature-logalty)
- DocuSign eSignature REST API and JWT Grant: [developers.docusign.com](https://developers.docusign.com/)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
</content>
</invoke>
