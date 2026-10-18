# Firefly Framework - Identity Provider (IDP)

[![CI](https://github.com/fireflyframework/fireflyframework-security-idp/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-security-idp/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Provider-agnostic identity SPI for Spring Boot WebFlux — one reactive `IdpAdapter` contract, a ready-to-use REST controller, and built-in metrics, with pluggable Keycloak, AWS Cognito, Azure AD and internal-DB adapters.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Observability](#observability)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework IDP is the **core identity-provider abstraction** of the Firefly ecosystem. It defines a single reactive port — the `IdpAdapter` interface — that standardizes authentication, token, user, role, scope and session operations across heterogeneous identity backends such as Keycloak, AWS Cognito, Azure AD or a local database, so application code never depends on a specific vendor SDK.

Beyond the contract, this module ships the **shared infrastructure** every adapter reuses, so you wire it once and swap providers with a single property:

- A complete set of request/response DTOs (`LoginRequest`, `TokenResponse`, `CreateUserRequest`, `IntrospectionResponse`, `MfaChallengeResponse`, `SessionInfo`, and more) covering the full IDP surface.
- A ready-to-use reactive `IdpController` (auto-mounted under `/idp`) that exposes every `IdpAdapter` operation over HTTP — it is registered automatically by `IdpWebAutoConfiguration` as soon as an `IdpAdapter` bean is present in a reactive web application.
- Cross-cutting `IdpMetrics` (Micrometer) auto-configured via `IdpObservabilityAutoConfiguration`, giving uniform authentication, token and error metrics regardless of which provider is active.

The concrete provider is selected at runtime with the `firefly.security.idp.provider` property; each adapter activates itself with `@ConditionalOnProperty(name = "firefly.security.idp.provider", havingValue = "<provider>")`. You depend on this core plus exactly one provider adapter.

### Provider adapters

| Provider value | Adapter module | Backend |
| --- | --- | --- |
| `keycloak` | [`fireflyframework-security-idp-keycloak`](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak) | Keycloak Admin API + token endpoint |
| `cognito` | [`fireflyframework-security-idp-aws-cognito`](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito) | AWS Cognito User Pools |
| `azure-ad` | [`fireflyframework-security-idp-azure-ad`](https://github.com/fireflyframework/fireflyframework-security-idp-azure-ad) | Microsoft Entra ID (Azure AD) |
| `internal-db` | [`fireflyframework-security-idp-internal-db`](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db) | Local database-backed identity store |

## Features

- **Reactive `IdpAdapter` SPI** — a single `Mono`-based contract for every IDP operation, designed for Spring WebFlux.
- **Full authentication lifecycle** — `login`, `refresh`, `logout`, token `introspect` (RFC 7662) and OIDC `getUserInfo`.
- **User management** — create, update, delete users, change/reset password, plus a default `registerUser` self-service flow that delegates to `createUser`.
- **Multi-factor authentication** — `mfaChallenge` / `mfaVerify` contracts for provider-backed MFA.
- **Roles & scopes** — create roles and scopes, assign/remove roles, and read a user's effective roles.
- **Session management** — list active sessions, revoke a session, and revoke refresh tokens.
- **Drop-in REST controller** — `IdpController` exposes all of the above under `/idp` with zero boilerplate, auto-configured only when an `IdpAdapter` bean exists in a reactive web app.
- **Built-in observability** — `IdpMetrics` records authentication counts/latency, tokens issued/refreshed and errors, tagged by `provider`.
- **Vendor-neutral DTOs** — a complete, validated DTO surface (`jakarta.validation`) shared by all adapters.
- **Pluggable by property** — switch providers with `firefly.security.idp.provider` and a single dependency swap; no code changes.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A reactive web stack (Spring WebFlux) to expose the bundled `IdpController`
- One IDP provider adapter on the classpath (Keycloak, AWS Cognito, Azure AD or internal-DB) plus its backing service (e.g. a running Keycloak instance or AWS Cognito User Pool)

## Installation

Add the core abstraction together with exactly one provider adapter. The version is managed by the Firefly parent/BOM, so you can omit it:

```xml
<dependencies>
    <!-- Core IDP abstraction (this module) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp</artifactId>
    </dependency>

    <!-- Choose one provider adapter -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp-keycloak</artifactId>
    </dependency>
</dependencies>
```

If you are not inheriting the Firefly parent, pin the version explicitly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp</artifactId>
    <version>26.05.08</version>
</dependency>
```

## Quick Start

**1. Select a provider** in `application.yaml`:

```yaml
firefly:
  idp:
    provider: keycloak   # keycloak | cognito | azure-ad | internal-db
```

With the chosen adapter on the classpath, its `IdpAdapter` bean is auto-configured and the `IdpController` is mounted under `/idp` automatically — no code required to expose the standard IDP REST API.

**2. Or consume the `IdpAdapter` directly** from your own services:

```java
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final IdpAdapter idp;

    public AuthService(IdpAdapter idp) {
        this.idp = idp;
    }

    public Mono<ResponseEntity<TokenResponse>> login(String username, String password) {
        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();
        return idp.login(request);
    }

    public Mono<ResponseEntity<CreateUserResponse>> register(RegisterUserRequest request) {
        return idp.registerUser(request);
    }
}
```

Because every operation returns a Reactor `Mono`, the adapter composes cleanly into reactive WebFlux pipelines.

## Configuration

This core module exposes a single property under the `firefly.security.idp` prefix; provider-specific keys (e.g. `firefly.security.idp.keycloak.*`, `firefly.security.idp.cognito.*`) are documented by each adapter module.

```yaml
firefly:
  idp:
    provider: keycloak          # REQUIRED — selects the active adapter (no default; @NotBlank)
  observability:
    metrics:
      enabled: true             # default true — gates IdpMetrics registration
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.security.idp.provider` | _(none, required)_ | Selects the active IDP adapter. One of `keycloak`, `cognito`, `azure-ad`, `internal-db`. Validated as `@NotBlank` via `IdpProperties`. |
| `firefly.observability.metrics.enabled` | `true` | When `true` (or absent), registers the `IdpMetrics` bean. Set to `false` to disable IDP metrics. |

Auto-configuration entry points (`META-INF/spring/...AutoConfiguration.imports`):

- `IdpWebAutoConfiguration` — registers `IdpController` when an `IdpAdapter` bean exists and the app is a reactive web application (`@ConditionalOnWebApplication(REACTIVE)`); also enables `IdpProperties`.
- `IdpObservabilityAutoConfiguration` — registers `IdpMetrics` when a Micrometer `MeterRegistry` is present and metrics are enabled.

## REST API

When the `IdpController` is auto-mounted, the following endpoints are exposed under `/idp`:

| Method | Path | Operation |
| --- | --- | --- |
| `POST` | `/idp/login` | Authenticate and obtain tokens |
| `POST` | `/idp/refresh` | Refresh an access token |
| `POST` | `/idp/logout` | Logout / revoke tokens |
| `GET`  | `/idp/introspect` | Introspect an access token (RFC 7662) |
| `GET`  | `/idp/userinfo` | OIDC user info for an access token |
| `POST` | `/idp/register` | Self-service user registration |
| `POST` | `/idp/revoke-refresh-token` | Revoke a refresh token |
| `POST` | `/idp/admin/users` | Create a user |
| `PUT`  | `/idp/admin/users` | Update a user |
| `DELETE` | `/idp/admin/users/{userId}` | Delete a user |
| `POST` | `/idp/admin/password` | Change a user's password |
| `POST` | `/idp/admin/password/reset` | Trigger a password reset |
| `POST` | `/idp/admin/mfa/challenge` | Initiate an MFA challenge |
| `POST` | `/idp/admin/mfa/verify` | Verify an MFA challenge |
| `GET`  | `/idp/admin/users/{userId}/sessions` | List active sessions |
| `DELETE` | `/idp/admin/sessions/{sessionId}` | Revoke a session |
| `GET`  | `/idp/admin/users/{userId}/roles` | Read a user's roles |
| `POST` | `/idp/admin/roles` | Create roles |
| `POST` | `/idp/admin/scopes` | Create a scope |
| `POST` | `/idp/admin/users/roles/assign` | Assign roles to a user |
| `POST` | `/idp/admin/users/roles/remove` | Remove roles from a user |

## Observability

`IdpMetrics` (auto-configured) records, all tagged by `provider`:

- `firefly.security.idp.authentications` — total auth attempts, tagged `status=success|failure`
- `firefly.security.idp.authentication.duration` — authentication latency timer
- `firefly.security.idp.token.issued` — tokens issued, tagged `token.type`
- `firefly.security.idp.token.refreshed` — token refreshes
- `firefly.security.idp.errors` — failed IDP operations, tagged `operation`, `error.type`

Adapters wrap their authentication calls with `IdpMetrics.timedAuthentication(provider, mono)` to get success/failure counters and the latency timer for free.

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Provider adapters: [Keycloak](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak) · [AWS Cognito](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito) · [Azure AD](https://github.com/fireflyframework/fireflyframework-security-idp-azure-ad) · [Internal DB](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
