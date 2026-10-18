# Firefly Framework - IDP Keycloak Adapter

[![CI](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Keycloak provider adapter for the Firefly Framework identity-provider (IDP) abstraction — implements the reactive `IdpAdapter` SPI over the Keycloak Admin REST API and OIDC token endpoints.

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

Firefly Framework IDP Keycloak is a **pluggable provider adapter** for the framework's identity-provider abstraction. It implements the `IdpAdapter` SPI defined in [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp) by translating the framework's provider-agnostic operations — authentication, token management, user and role administration, MFA and session control — into calls against a [Keycloak](https://www.keycloak.org/) server using the official **Keycloak Admin Client** and OIDC/OAuth2 endpoints.

The adapter is fully reactive: every operation returns a Reactor `Mono`, and the Keycloak Admin Client calls are scheduled off the event loop so they integrate cleanly with Spring WebFlux applications. The core module ships the `IdpController` REST surface and the request/response DTOs; this module only supplies the Keycloak-backed implementation, so swapping providers is a configuration change rather than a code change.

Adapter selection is driven by a single property. Adding this dependency and setting `firefly.security.idp.provider=keycloak` activates `KeycloakAutoConfiguration`, which registers the full bean graph (`IdpAdapter`, the user/admin/token services, the Keycloak client factories, and a permissive CORS `WebFilter`). The auto-configuration is also guarded by `@ConditionalOnClass(Keycloak.class)`, so it stays inert unless the Keycloak Admin Client is on the classpath.

This module is one of several interchangeable providers for the same SPI. Its siblings select with the same `firefly.security.idp.provider` switch:

| Adapter | `firefly.security.idp.provider` value | Backing provider |
| --- | --- | --- |
| `fireflyframework-security-idp-keycloak` (this module) | `keycloak` | Keycloak |
| `fireflyframework-security-idp-aws-cognito` | `cognito` | Amazon Cognito |
| `fireflyframework-security-idp-azure-ad` | `azure-ad` | Microsoft Entra ID / Azure AD B2C |
| `fireflyframework-security-idp-internal-db` | `internal-db` | Self-hosted database-backed IDP |

## Features

- **Drop-in `IdpAdapter` implementation** (`IdpAdapterImpl`) covering the full SPI: login, refresh, logout, introspection, user info, user CRUD, password change/reset, role and scope management, role assignment, MFA challenge/verify, and session listing/revocation.
- **Reactive API** — all operations return Reactor `Mono`, ready for Spring WebFlux.
- **Layered service design** — `IdpUserService` for end-user flows (login/refresh/logout/introspect/user-info/token revocation), `IdpAdminService` for realm administration (user, role, scope, session management, MFA), and `TokenService` for JWT parsing/validation.
- **`TokenService` JWT utilities** — parse Keycloak access tokens and extract user id, session id, realm/client roles, and expiry without an extra round trip.
- **Centralised client construction** — `KeycloakClientFactory` builds Admin Clients for the Resource Owner Password and Client Credentials grant flows; `KeycloakAPIFactory` constructs API connections from the configured properties.
- **Connection tuning** — configurable connection pool size, connection timeout, and request timeout via `KeycloakProperties`.
- **Validated configuration** — `KeycloakProperties` is a `@Validated` record that fails fast on missing `server-url`, `realm`, or `client-id` and auto-normalizes the server URL.
- **Consistent error handling** — `KeycloakExceptionHandler` maps Keycloak failures to standardized framework responses.
- **CORS out of the box** — a permissive reactive `CorsWebFilter` is registered for cross-origin clients (override by supplying your own bean).
- **Zero-code activation** — Spring Boot auto-configuration (`KeycloakAutoConfiguration`) wires everything when `firefly.security.idp.provider=keycloak`; every bean is `@ConditionalOnMissingBean`, so any piece can be overridden.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A reachable **Keycloak** server (Admin Client `26.x` compatible) with a realm and a confidential client configured for the grant flows you use

## Installation

Add the adapter alongside the IDP core. Both artifacts share the `org.fireflyframework` group; the version is managed by the Firefly BOM / parent, so you normally omit it:

```xml
<dependencies>
    <!-- IDP core SPI + REST controller + DTOs -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp</artifactId>
    </dependency>

    <!-- Keycloak provider adapter (this module) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp-keycloak</artifactId>
    </dependency>
</dependencies>
```

If you are not inheriting the Firefly parent/BOM, pin the version explicitly (for example `26.05.08`) on each dependency.

## Quick Start

**1. Select the Keycloak provider and point it at your server** (`application.yaml`):

```yaml
firefly:
  idp:
    provider: keycloak          # activates KeycloakAutoConfiguration
    keycloak:
      server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8080}
      realm: ${KEYCLOAK_REALM:master}
      client-id: ${KEYCLOAK_CLIENT_ID:firefly_client}
      client-secret: ${KEYCLOAK_CLIENT_SECRET:}
```

**2. Inject the `IdpAdapter` and call it** — the same provider-agnostic API works for every IDP backend:

```java
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.LoginRequest;
import org.fireflyframework.security.idp.dtos.TokenResponse;
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
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return idp.login(request);   // Keycloak ROPC flow under the hood
    }
}
```

Because the contract lives in `fireflyframework-security-idp`, switching to Cognito, Azure AD, or the internal-db provider is just a dependency swap plus a change to `firefly.security.idp.provider` — your application code is untouched.

## Configuration

All properties live under the `firefly.security.idp.keycloak.*` prefix and are bound by the validated `KeycloakProperties` record. Provider selection itself is the top-level `firefly.security.idp.provider` switch.

```yaml
firefly:
  idp:
    provider: keycloak                    # must be "keycloak" to enable this adapter
    keycloak:
      server-url: http://localhost:8080   # required; auto-normalized to end with "/"
      realm: master                       # required
      client-id: firefly_client           # required
      client-secret:                      # optional; required for client-credentials flow
      connection-pool-size: 10            # default 10
      connection-timeout: 30000           # default 30000 (ms)
      request-timeout: 60000              # default 60000 (ms)
```

| Property | Required | Default | Description |
| --- | --- | --- | --- |
| `firefly.security.idp.provider` | yes | — | Provider selector; set to `keycloak` to activate this adapter. |
| `firefly.security.idp.keycloak.server-url` | yes | — | Base URL of the Keycloak server. Trailing slash is added automatically if missing. |
| `firefly.security.idp.keycloak.realm` | yes | — | Keycloak realm used for authentication and administration. |
| `firefly.security.idp.keycloak.client-id` | yes | — | OAuth2 client id used by the adapter. |
| `firefly.security.idp.keycloak.client-secret` | no | — | Client secret; required for the client-credentials grant used by admin operations. |
| `firefly.security.idp.keycloak.connection-pool-size` | no | `10` | Maximum size of the HTTP connection pool to Keycloak (must be positive). |
| `firefly.security.idp.keycloak.connection-timeout` | no | `30000` | Connection timeout in milliseconds (must be positive). |
| `firefly.security.idp.keycloak.request-timeout` | no | `60000` | Request timeout in milliseconds (must be positive). |

Validation fails fast on startup if `server-url`, `realm`, or `client-id` are blank, or if any timeout/pool value is non-positive.

## Documentation

- Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- IDP core SPI and DTOs: [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp)
- Sibling adapters: [`fireflyframework-security-idp-aws-cognito`](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito), [`fireflyframework-security-idp-azure-ad`](https://github.com/fireflyframework/fireflyframework-security-idp-azure-ad), [`fireflyframework-security-idp-internal-db`](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
