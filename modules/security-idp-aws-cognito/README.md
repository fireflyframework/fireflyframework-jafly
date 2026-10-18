# Firefly Framework - IDP AWS Cognito

[![CI](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Amazon Cognito adapter for the Firefly Framework IDP abstraction — drops AWS Cognito User Pools in behind the unified, reactive `IdpAdapter` SPI for authentication, token, user and role management.

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

`fireflyframework-security-idp-aws-cognito` is a **pluggable provider adapter** for the Firefly Framework Identity Provider (IDP) abstraction. It implements the framework's `IdpAdapter` SPI — defined in the core [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp) module — on top of [Amazon Cognito User Pools](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-identity-pools.html), using the AWS SDK for Java v2.

Application code depends only on the provider-neutral `IdpAdapter` interface and the framework's request/response DTOs. By adding this module to the classpath and selecting it with `firefly.security.idp.provider=cognito`, all authentication and identity operations are transparently routed to AWS Cognito — with no Cognito-specific code in your services. Swapping to a different backend (Keycloak, Azure AD, an internal database) is a configuration change, not a code change.

The adapter is **fully reactive** (Project Reactor `Mono`): the blocking AWS SDK v2 synchronous client is wrapped in `Mono.fromCallable(...)` so it composes cleanly into Spring WebFlux pipelines. The implementation is split into focused collaborators:

- **`CognitoIdpAdapter`** — the `IdpAdapter` entry point; a thin facade that delegates each operation to the user or admin service.
- **`CognitoUserService`** — end-user flows: login (`InitiateAuth`), token refresh, logout (`GlobalSignOut`), introspection, user info, and refresh-token revocation.
- **`CognitoAdminService`** — administrative flows: user CRUD, password change/reset, MFA challenge/verify, session listing/revocation, and role/group management.
- **`CognitoClientFactory`** — lazily builds and caches a single `CognitoIdentityProviderClient`, applying region, timeouts, optional endpoint override and credentials, and closing it cleanly on shutdown.
- **`CognitoSecretHashCalculator`** — computes the `SECRET_HASH` (`Base64(HMAC_SHA256(clientSecret, username + clientId))`) required by Cognito app clients that are configured with a client secret.

### Sibling modules

This adapter is one of several interchangeable implementations of the same `IdpAdapter` SPI:

| Module | Backend | Selector (`firefly.security.idp.provider`) |
| --- | --- | --- |
| [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp) | Core SPI, DTOs, auto-config | — |
| **`fireflyframework-security-idp-aws-cognito`** | **AWS Cognito User Pools** | **`cognito`** |
| [`fireflyframework-security-idp-keycloak`](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak) | Keycloak | `keycloak` |
| [`fireflyframework-security-idp-azure-ad`](https://github.com/fireflyframework/fireflyframework-security-idp-azure-ad) | Microsoft Entra ID (Azure AD) | `azure-ad` |
| [`fireflyframework-security-idp-internal-db`](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db) | Internal database | `internal-db` |

## Features

- **Complete `IdpAdapter` implementation** backed by AWS Cognito User Pools — no Cognito types leak into application code.
- **Authentication flows** — username/password login via `InitiateAuth`, token refresh, global logout (`GlobalSignOut`), token introspection, and OIDC-style user info.
- **Token lifecycle** — refresh-token revocation and session-aware sign-out.
- **User management** — create, update, delete users and reset/change passwords through the Cognito admin APIs.
- **Roles & scopes** — group/role creation, role assignment and removal, and scope management mapped onto Cognito groups.
- **MFA** — multi-factor challenge issuance and verification.
- **Automatic `SECRET_HASH` handling** — transparently computed for app clients configured with a client secret (`CognitoSecretHashCalculator`).
- **Reactive, non-blocking API** — every operation returns a Project Reactor `Mono`, composing into Spring WebFlux pipelines.
- **Managed client lifecycle** — `CognitoClientFactory` lazily creates a single, double-checked-locking-guarded `CognitoIdentityProviderClient`, applies API call/attempt timeouts, and closes it on `@PreDestroy`.
- **Zero-code activation** — Spring Boot auto-configuration (`CognitoAutoConfiguration`) wires every bean automatically, gated on `firefly.security.idp.provider=cognito` and the Cognito SDK being on the classpath.
- **LocalStack-friendly** — optional endpoint and credentials overrides enable fully offline integration tests against [LocalStack](https://localstack.cloud/) (see `CognitoIdpAdapterLocalStackIT`).

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- The core [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp) module on the classpath
- An AWS account with a configured **Cognito User Pool** and **app client** (and valid AWS credentials resolvable via the default provider chain, or via LocalStack for testing)

## Installation

Add the adapter alongside the IDP core. Versions are managed by the Firefly Framework parent/BOM, so you normally omit `<version>`:

```xml
<dependencies>
    <!-- Core IDP SPI, DTOs and auto-configuration -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp</artifactId>
    </dependency>

    <!-- AWS Cognito provider adapter -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp-aws-cognito</artifactId>
    </dependency>
</dependencies>
```

> The version is managed by the Firefly Framework parent POM / BOM. If you are not inheriting the parent, pin the version explicitly to match your framework release.

## Quick Start

**1. Select the Cognito provider and configure your User Pool** in `application.yml`:

```yaml
firefly:
  idp:
    provider: cognito          # activates this adapter
    cognito:
      region: us-east-1
      user-pool-id: us-east-1_XXXXXXXXX
      client-id: your-app-client-id
      client-secret: your-app-client-secret   # only for app clients with a secret
```

**2. Inject the provider-neutral `IdpAdapter`** — auto-configuration supplies the Cognito-backed bean:

```java
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.LoginRequest;
import org.fireflyframework.security.idp.dtos.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final IdpAdapter idp;   // CognitoIdpAdapter wired by CognitoAutoConfiguration

    public AuthService(IdpAdapter idp) {
        this.idp = idp;
    }

    public Mono<ResponseEntity<TokenResponse>> login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return idp.login(request);   // routed to AWS Cognito InitiateAuth
    }
}
```

Because `AuthService` depends only on `IdpAdapter`, switching to another backend later means swapping the adapter dependency and changing `firefly.security.idp.provider` — the service code is unchanged.

## Configuration

All properties are bound from `CognitoProperties` under the `firefly.security.idp.cognito` prefix. Activation additionally requires the top-level selector `firefly.security.idp.provider=cognito`.

```yaml
firefly:
  idp:
    provider: cognito              # required to activate this adapter
    cognito:
      region: us-east-1            # AWS region of the User Pool (default: us-east-1)
      user-pool-id:                # required — Cognito User Pool ID, e.g. us-east-1_XXXXXXXXX
      client-id:                   # required — Cognito app client ID
      client-secret:               # optional — app client secret (enables SECRET_HASH)
      domain:                      # optional — Cognito hosted-UI domain
      endpoint-override:           # optional — custom endpoint URI (e.g. LocalStack)
      connection-timeout: 30000    # API call attempt timeout, milliseconds (default: 30000)
      request-timeout: 60000       # overall API call timeout, milliseconds (default: 60000)
```

| Property | Required | Default | Description |
| --- | --- | --- | --- |
| `firefly.security.idp.provider` | Yes | — | Must be `cognito` to activate this adapter. |
| `firefly.security.idp.cognito.region` | Yes (non-blank) | `us-east-1` | AWS region where the Cognito User Pool lives. |
| `firefly.security.idp.cognito.user-pool-id` | Yes | — | Cognito User Pool ID. |
| `firefly.security.idp.cognito.client-id` | Yes | — | Cognito app client ID. |
| `firefly.security.idp.cognito.client-secret` | No | — | App client secret; when set, the adapter computes and sends `SECRET_HASH`. |
| `firefly.security.idp.cognito.domain` | No | — | Cognito hosted-UI domain. |
| `firefly.security.idp.cognito.endpoint-override` | No | — | Custom Cognito endpoint URI (used to point at LocalStack in tests). |
| `firefly.security.idp.cognito.connection-timeout` | No | `30000` | Per-attempt API call timeout, in milliseconds. |
| `firefly.security.idp.cognito.request-timeout` | No | `60000` | Overall API call timeout (across retries), in milliseconds. |

`region`, `user-pool-id` and `client-id` are validated as non-blank (`@Validated` + `@NotBlank`); the application fails fast at startup if any are missing.

## How It Works

`CognitoAutoConfiguration` is registered as a Spring Boot `@AutoConfiguration` and is loaded automatically via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It activates only when:

- `firefly.security.idp.provider=cognito` (`@ConditionalOnProperty`), and
- `software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient` is on the classpath (`@ConditionalOnClass`).

When active, it contributes (each `@ConditionalOnMissingBean`, so you can override any of them):

1. `CognitoClientFactory` — builds the AWS SDK v2 client from `CognitoProperties`, applying the endpoint override when configured.
2. `CognitoUserService` and `CognitoAdminService` — the end-user and administrative operation services.
3. `IdpAdapter` (a `CognitoIdpAdapter`) — registered only if no other `IdpAdapter` bean exists, so a single provider wins.

Each adapter call delegates to the matching service, which obtains the lazily-cached client from the factory and invokes the corresponding Cognito API inside a `Mono.fromCallable(...)`, returning the framework's neutral response DTOs. The client is closed on application shutdown via `@PreDestroy`.

## Documentation

- Firefly Framework module catalog and docs hub: [github.com/fireflyframework](https://github.com/fireflyframework)
- Core IDP SPI and DTOs: [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp)
- AWS Cognito reference: [Amazon Cognito Developer Guide](https://docs.aws.amazon.com/cognito/latest/developerguide/what-is-amazon-cognito.html)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
