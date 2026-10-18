# Firefly Framework - IDP Azure AD

[![CI](https://github.com/fireflyframework/fireflyframework-security-idp-azure-ad/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-security-idp-azure-ad/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Microsoft Entra ID and Azure AD B2C adapter for the Firefly Framework IDP abstraction — reactive authentication, token, and user-management operations via MSAL4J and the Microsoft Graph SDK.

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

`fireflyframework-security-idp-azure-ad` is a pluggable identity-provider adapter for the Firefly Framework
**IDP abstraction** (`fireflyframework-security-idp`). It implements the framework's provider-agnostic
`IdpAdapter` SPI on top of **Microsoft Entra ID** (formerly Azure Active Directory) and **Azure AD B2C**,
so an application can authenticate users, manage tokens, and administer accounts and roles through the
same reactive API it would use with any other Firefly IDP provider.

The IDP core defines the `IdpAdapter` contract and selects exactly one adapter at runtime via the
`firefly.security.idp.provider` property. Adding this module to the classpath and setting
`firefly.security.idp.provider=azure-ad` wires the Azure AD implementation automatically — no other code change is
required. This adapter sits alongside its sibling adapters
[`fireflyframework-security-idp-keycloak`](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak),
[`fireflyframework-security-idp-aws-cognito`](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito),
and [`fireflyframework-security-idp-internal-db`](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db),
all of which implement the same SPI.

Internally the adapter (`AzureAdIdpAdapter`) is a thin delegator that splits responsibilities across two
collaborators: `AzureAdAuthService` handles authentication and token operations (login, refresh, logout,
introspection, user info, token revocation) using **MSAL4J**, while the `AzureAdAdminService` strategy
handles administrative operations (user CRUD, password, roles, sessions, MFA) using the **Microsoft Graph
SDK**. The active admin strategy is chosen automatically from the configured `mode`: `EntraIdAdminService`
for standard Entra ID directories (the default) and `B2CAdminService` (which extends the Entra ID service
with B2C-specific local-account behaviour) for Azure AD B2C tenants.

Because MSAL4J and the Microsoft Graph SDK are blocking, every operation is wrapped in
`Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` so it integrates cleanly with the
non-blocking WebFlux stack used across Firefly.

## Features

- **Full `IdpAdapter` implementation** — every operation in the Firefly IDP SPI is implemented (login,
  refresh, logout, introspect, getUserInfo, createUser, updateUser, deleteUser, changePassword,
  resetPassword, MFA challenge/verify, sessions, roles, scopes, token revocation).
- **Two operating modes from one adapter** — Microsoft Entra ID (`mode: entra-id`, default) and Azure AD
  B2C (`mode: b2c`), selected purely by configuration.
- **Strategy-based admin layer** — `EntraIdAdminService` and `B2CAdminService` are wired conditionally on
  `firefly.security.idp.azure-ad.mode`; B2C overrides user creation (identities array / `ObjectIdentity` local
  accounts) and password reset semantics.
- **MSAL4J authentication** — ROPC username/password login and refresh-token flow via a
  `PublicClientApplication`; admin token acquisition via a `ConfidentialClientApplication`
  (`MsalClientFactory`).
- **Microsoft Graph SDK integration** — `GraphClientFactory` builds a `GraphServiceClient` backed by a
  `ClientSecretCredential` for all directory operations (users, groups, sessions).
- **Configurable role mapping** — derive a user's roles from app roles, security groups, or both, with an
  optional group-name prefix filter.
- **Local JWT introspection** — `AzureAdTokenParser` decodes and validates access tokens locally
  (expiry, `sub`/`iss`/`scp`, and `oid`/`upn` extraction) without an extra network round trip.
- **Spring Boot auto-configuration** — `AzureAdAutoConfiguration` activates only when
  `firefly.security.idp.provider=azure-ad` and MSAL4J is on the classpath; all beans are
  `@ConditionalOnMissingBean`, so any one can be overridden.
- **Lazy, thread-safe clients** — Graph and MSAL clients are created on first use behind
  double-checked locking and released on context shutdown via `@PreDestroy`.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- An Azure AD tenant with an **app registration**:
  - a **client ID** and **client secret** (confidential client) for Microsoft Graph admin operations;
  - the public-client / ROPC flow enabled if you use username/password login;
  - Microsoft Graph application permissions (e.g. `User.ReadWrite.All`, `Group.ReadWrite.All`) granted
    with admin consent;
  - for B2C: a B2C tenant with the relevant user flows (sign-up/sign-in, password reset).

## Installation

This module is part of the Firefly Framework and its version is managed by the Firefly parent POM / BOM,
so you normally omit the `<version>` tag.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-azure-ad</artifactId>
    <!-- version managed by the Firefly parent POM / BOM -->
</dependency>
```

The adapter transitively brings in the IDP core (`fireflyframework-security-idp`), so you do not need to declare it
separately — though you may add it explicitly for clarity:

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp-azure-ad</artifactId>
    </dependency>
</dependencies>
```

## Quick Start

**1. Add the dependency** (above) and select the provider:

```yaml
firefly:
  idp:
    provider: azure-ad        # selects this adapter
    azure-ad:
      mode: entra-id          # "entra-id" (default) or "b2c"
      tenant-id: ${AZURE_TENANT_ID}
      client-id: ${AZURE_CLIENT_ID}
      client-secret: ${AZURE_CLIENT_SECRET}
```

That is all the wiring needed — `AzureAdAutoConfiguration` registers an `IdpAdapter` bean automatically.

**2. Inject the `IdpAdapter` and use it.** Your code depends only on the framework SPI, never on Azure AD
types, so it stays portable across providers:

```java
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthFacade {

    private final IdpAdapter idp;

    public AuthFacade(IdpAdapter idp) {
        this.idp = idp;
    }

    public Mono<ResponseEntity<TokenResponse>> login(String username, String password) {
        return idp.login(LoginRequest.builder()
                .username(username)
                .password(password)
                .build());
    }

    public Mono<ResponseEntity<UserInfoResponse>> me(String accessToken) {
        return idp.getUserInfo(accessToken);
    }

    public Mono<ResponseEntity<CreateUserResponse>> register(CreateUserRequest request) {
        return idp.createUser(request);
    }
}
```

## Configuration

All properties live under the `firefly.security.idp.azure-ad` prefix and map to `AzureAdProperties`.
`firefly.security.idp.provider=azure-ad` (defined by the IDP core) is what activates this adapter.

```yaml
firefly:
  idp:
    provider: azure-ad                 # required: selects the Azure AD adapter
    azure-ad:
      mode: entra-id                   # "entra-id" (default) or "b2c"
      tenant-id: your-tenant-id        # required
      client-id: your-client-id        # required
      client-secret: your-client-secret # required
      connection-timeout: 30000        # MSAL HTTP connect timeout (ms)
      request-timeout: 60000           # MSAL HTTP read timeout (ms)
      role-mapping:
        strategy: app-roles            # "app-roles" (default), "groups", or "both"
        group-prefix: ""               # only map groups whose name starts with this prefix
      graph:
        scopes:                        # Microsoft Graph token scopes
          - https://graph.microsoft.com/.default
      b2c:                             # only used when mode = b2c
        domain: your-tenant.b2clogin.com
        sign-up-sign-in-policy: B2C_1_signup_signin
        reset-password-policy: B2C_1_password_reset
```

### Key properties

| Property | Default | Description |
| --- | --- | --- |
| `firefly.security.idp.provider` | _(none)_ | Must be `azure-ad` to activate this adapter (defined by the IDP core). |
| `firefly.security.idp.azure-ad.mode` | `entra-id` | Selects the admin strategy: `entra-id` or `b2c`. |
| `firefly.security.idp.azure-ad.tenant-id` | _(required)_ | Azure AD / Entra tenant ID. |
| `firefly.security.idp.azure-ad.client-id` | _(required)_ | App-registration client ID. |
| `firefly.security.idp.azure-ad.client-secret` | _(required)_ | App-registration client secret. |
| `firefly.security.idp.azure-ad.connection-timeout` | `30000` | MSAL4J HTTP connect timeout in milliseconds. |
| `firefly.security.idp.azure-ad.request-timeout` | `60000` | MSAL4J HTTP read timeout in milliseconds. |
| `firefly.security.idp.azure-ad.role-mapping.strategy` | `app-roles` | How roles are derived: `app-roles`, `groups`, or `both`. |
| `firefly.security.idp.azure-ad.role-mapping.group-prefix` | `""` | Only security groups with this name prefix are mapped to roles. |
| `firefly.security.idp.azure-ad.graph.scopes` | `https://graph.microsoft.com/.default` | OAuth2 scopes used when acquiring Graph tokens. |
| `firefly.security.idp.azure-ad.b2c.domain` | _(none)_ | B2C login domain (e.g. `tenant.b2clogin.com`); drives the authority URL in B2C mode. |
| `firefly.security.idp.azure-ad.b2c.sign-up-sign-in-policy` | `B2C_1_signup_signin` | B2C sign-up/sign-in user-flow name. |
| `firefly.security.idp.azure-ad.b2c.reset-password-policy` | `B2C_1_password_reset` | B2C password-reset user-flow name. |

`tenant-id`, `client-id`, and `client-secret` are validated with `@NotBlank` — the application fails fast
at startup if any is missing. The effective MSAL authority is derived automatically:
`https://login.microsoftonline.com/{tenant-id}` for Entra ID, or
`https://{b2c.domain}/{tenant-id}` for B2C.

## How It Works

- **`AzureAdAutoConfiguration`** — `@ConditionalOnProperty(firefly.security.idp.provider=azure-ad)` and
  `@ConditionalOnClass(ConfidentialClientApplication.class)`; registers the client factories, services, and
  the `IdpAdapter` bean. The admin-service bean is chosen by `firefly.security.idp.azure-ad.mode`.
- **`AzureAdIdpAdapter`** — implements `IdpAdapter`; delegates auth/token calls to `AzureAdAuthService` and
  admin calls to the selected `AzureAdAdminService`.
- **`AzureAdAuthService`** — login (ROPC), refresh, logout, introspect, user info, and token revocation via
  MSAL4J + Graph.
- **`EntraIdAdminService` / `B2CAdminService`** — user, role, session, and password operations via the
  Microsoft Graph SDK; `B2CAdminService extends EntraIdAdminService` with B2C local-account overrides.
- **`MsalClientFactory` / `GraphClientFactory`** — lazily build and cache the confidential-client and Graph
  clients, released on shutdown.
- **`AzureAdTokenParser`** — local, dependency-free JWT decoding for introspection and user-ID extraction.

## Documentation

- Firefly Framework documentation hub and module catalog:
  [github.com/fireflyframework](https://github.com/fireflyframework)
- IDP abstraction / SPI:
  [`fireflyframework-security-idp`](https://github.com/fireflyframework/fireflyframework-security-idp)
- Sibling adapters:
  [Keycloak](https://github.com/fireflyframework/fireflyframework-security-idp-keycloak) ·
  [AWS Cognito](https://github.com/fireflyframework/fireflyframework-security-idp-aws-cognito) ·
  [Internal DB](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
