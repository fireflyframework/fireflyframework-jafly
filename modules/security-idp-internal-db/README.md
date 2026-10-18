# Firefly Framework - IDP Internal Database Adapter

[![CI](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-security-idp-internal-db/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Self-contained, database-backed Identity Provider adapter for the Firefly Framework IDP abstraction — reactive R2DBC user store, JWT issuance, BCrypt hashing, TOTP MFA, account lockout and password policy, with no external IDP required.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [How It Works](#how-it-works)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-security-idp-internal-db` is a **pluggable provider adapter** for the Firefly Framework IDP abstraction
(`fireflyframework-security-idp`). Where the core module defines the provider-neutral `IdpAdapter` SPI and the common DTOs
(`LoginRequest`, `TokenResponse`, `UserInfoResponse`, `SessionInfo`, …), this module supplies a fully self-contained
implementation that stores users, roles, sessions and tokens in your own relational database and issues signed JWTs
locally — without delegating to any external identity platform.

The adapter is selected at runtime by the single core property **`firefly.security.idp.provider=internal-db`**. This is the same
selection mechanism used by its sibling adapters, so an application can switch identity backends by changing one
property and one dependency:

| Provider value | Module | Backing identity store |
|----------------|--------|------------------------|
| `internal-db`  | **fireflyframework-security-idp-internal-db** (this module) | Your own PostgreSQL database (R2DBC) |
| `keycloak`     | fireflyframework-security-idp-keycloak | Keycloak realm |
| `cognito`      | fireflyframework-security-idp-aws-cognito | AWS Cognito user pool |
| `azure-ad`     | fireflyframework-security-idp-azure-ad | Microsoft Entra ID (Azure AD) |

The internal-db adapter is the right choice when you need first-party, standalone authentication and authorization —
development and test environments, microservices that must not depend on an external IDP, or products that ship their
own user directory. It is built entirely on the reactive stack: R2DBC repositories, WebFlux-compatible `Mono`-based
APIs, and non-blocking JWT handling.

Everything ships as a Spring Boot auto-configuration: add the dependency, set the provider property, point Spring at an
R2DBC PostgreSQL connection, and the `IdpAdapter` bean (plus all supporting services and repositories) is wired
automatically. The bundled Flyway migrations create and evolve the schema.

## Features

- **Drop-in `IdpAdapter` implementation** — `InternalDbIdpAdapter` implements the full core SPI: login, token refresh,
  logout, token introspection, user info, user CRUD, role management, scope creation, MFA challenge/verify and session
  management (20 SPI operations).
- **Reactive R2DBC persistence** — non-blocking repositories for users, roles, user-role assignments, sessions, refresh
  tokens and password-reset tokens (`UserRepository`, `RoleRepository`, `UserRoleRepository`, `SessionRepository`,
  `RefreshTokenRepository`, `PasswordResetTokenRepository`).
- **Local JWT issuance and validation** — `JwtTokenService` signs and verifies access/refresh tokens with a configurable
  HMAC secret, issuer claim and expiry (JJWT 0.13).
- **BCrypt password hashing** — `PasswordEncoder` bean configured as `BCryptPasswordEncoder` with strength 12 (override
  with your own bean).
- **Authentication flows** — `AuthenticationService` handles credential verification, token issuance, refresh-token
  rotation and logout.
- **User and role management** — `UserManagementService` and `RoleService` provide CRUD over users, roles and
  user-role assignments.
- **Account lockout** — configurable max failed attempts and lockout duration to defend against brute-force attacks.
- **Password policy** — `PasswordPolicyService` enforces minimum/maximum length and uppercase/lowercase/digit/special
  character requirements at user creation and password change.
- **Password reset** — `PasswordResetService` issues time-limited, single-use reset tokens.
- **TOTP multi-factor authentication** — `MfaService` (backed by `dev.samstevens.totp`) supports per-user TOTP
  enrolment, challenge and verification.
- **Session tracking** — every issued access token is recorded as a session (with IP address and user agent), enabling
  listing and per-session revocation.
- **Versioned schema via Flyway** — bundled `db/migration` scripts create the IDP tables and add lockout, policy and
  password-reset support.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A PostgreSQL database reachable over both R2DBC (runtime access) and JDBC (Flyway migrations)

## Installation

Add the adapter alongside the IDP core. Versions are managed by the Firefly parent / BOM, so you normally omit
`<version>`:

```xml
<dependencies>
    <!-- IDP core SPI and DTOs -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp</artifactId>
    </dependency>

    <!-- Internal database provider adapter (this module) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-security-idp-internal-db</artifactId>
    </dependency>
</dependencies>
```

This adapter transitively brings in `fireflyframework-r2dbc`, `spring-boot-starter-data-r2dbc`,
`spring-boot-starter-webflux`, the PostgreSQL R2DBC and JDBC drivers, JJWT and the TOTP library.

## Quick Start

**1. Select this adapter and configure R2DBC.** The adapter activates only when `firefly.security.idp.provider=internal-db`:

```yaml
firefly:
  idp:
    provider: internal-db
    internal-db:
      jwt:
        secret: "${JWT_SECRET}"   # min 32 chars / 256 bits — generate with: openssl rand -base64 32
        issuer: my-platform

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/firefly
    username: ${DB_USERNAME:firefly}
    password: ${DB_PASSWORD}
  # JDBC datasource + Flyway are used to run the bundled schema migrations
  datasource:
    url: jdbc:postgresql://localhost:5432/firefly
    username: ${DB_USERNAME:firefly}
    password: ${DB_PASSWORD}
  flyway:
    enabled: true
    locations: classpath:db/migration
```

**2. Inject the `IdpAdapter`.** No internal-db classes need to be referenced — code against the core SPI so the same
controller works regardless of provider:

```java
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.LoginRequest;
import org.fireflyframework.security.idp.dtos.TokenResponse;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final IdpAdapter idp; // InternalDbIdpAdapter, wired by auto-configuration

    @PostMapping("/auth/login")
    public Mono<ResponseEntity<TokenResponse>> login(@RequestBody LoginRequest request) {
        return idp.login(request);
    }
}
```

That is all that is required: the `IdpAdapter` bean, all supporting services
(`AuthenticationService`, `JwtTokenService`, `UserManagementService`, `RoleService`, `PasswordResetService`,
`PasswordPolicyService`, `MfaService`), the `PasswordEncoder` and the R2DBC repositories are registered automatically by
`InternalDbIdpAutoConfiguration`. Each bean is `@ConditionalOnMissingBean`, so any of them can be overridden.

## Configuration

All adapter settings live under the `firefly.security.idp.internal-db` prefix and are bound by `InternalDbProperties`. The
provider selector (`firefly.security.idp.provider`) belongs to the IDP core.

```yaml
firefly:
  idp:
    provider: internal-db          # selects THIS adapter (required, from the IDP core)
    internal-db:
      jwt:
        secret: "${JWT_SECRET}"            # REQUIRED — HMAC signing key, min 256 bits (32 chars)
        issuer: firefly-idp-internal-db    # JWT "iss" claim
        access-token-expiration: 900000    # access token TTL in ms (default 15 min)
        refresh-token-expiration: 604800000 # refresh token TTL in ms (default 7 days)
      password-reset:
        token-expiry: 1h                   # reset-token validity (java.time.Duration, default 1h)
      lockout:
        max-failed-attempts: 5             # failed logins before lockout (default 5)
        lockout-duration: 30m              # lockout window (Duration, default 30m)
      password-policy:
        min-length: 8                      # default 8
        max-length: 128                    # default 128 (0 = unlimited)
        require-uppercase: true            # default true
        require-lowercase: true            # default true
        require-digit: true                # default true
        require-special-char: false        # default false
      mfa:
        available: true                    # allow users to enrol TOTP MFA (default true)
```

| Property | Default | Description |
|----------|---------|-------------|
| `firefly.security.idp.provider` | _(none, required)_ | Must be `internal-db` to activate this adapter. |
| `firefly.security.idp.internal-db.jwt.secret` | _(none, required)_ | HMAC secret used to sign and verify JWTs; must be at least 256 bits. |
| `firefly.security.idp.internal-db.jwt.issuer` | `firefly-idp-internal-db` | Value of the JWT `iss` claim. |
| `firefly.security.idp.internal-db.jwt.access-token-expiration` | `900000` (15 min) | Access token lifetime, in milliseconds. |
| `firefly.security.idp.internal-db.jwt.refresh-token-expiration` | `604800000` (7 days) | Refresh token lifetime, in milliseconds. |
| `firefly.security.idp.internal-db.password-reset.token-expiry` | `1h` | Validity window for password-reset tokens. |
| `firefly.security.idp.internal-db.lockout.max-failed-attempts` | `5` | Consecutive failed logins before the account is locked. |
| `firefly.security.idp.internal-db.lockout.lockout-duration` | `30m` | How long an account stays locked. |
| `firefly.security.idp.internal-db.password-policy.min-length` | `8` | Minimum password length. |
| `firefly.security.idp.internal-db.password-policy.max-length` | `128` | Maximum password length (`0` = unlimited). |
| `firefly.security.idp.internal-db.password-policy.require-uppercase` | `true` | Require at least one uppercase letter. |
| `firefly.security.idp.internal-db.password-policy.require-lowercase` | `true` | Require at least one lowercase letter. |
| `firefly.security.idp.internal-db.password-policy.require-digit` | `true` | Require at least one digit. |
| `firefly.security.idp.internal-db.password-policy.require-special-char` | `false` | Require at least one special character. |
| `firefly.security.idp.internal-db.mfa.available` | `true` | Whether users may enrol TOTP-based MFA. |

The adapter also requires a standard `spring.r2dbc.*` connection (runtime data access) and a `spring.datasource.*` JDBC
connection with `spring.flyway.*` enabled so the bundled migrations can build the schema.

## Database Schema

Flyway migrations under `classpath:db/migration` create and evolve the schema:

- **`V1__Create_IDP_Tables.sql`** — `idp_users`, `idp_roles`, `idp_user_roles`, `idp_sessions`, `idp_refresh_tokens`
  (with the necessary indexes, unique constraints and foreign keys).
- **`V2__Add_Password_Reset_Tokens.sql`** — `idp_password_reset_tokens`.
- **`V3__Add_Lockout_And_Policy.sql`** — failed-attempt counters and lockout columns supporting the account-lockout and
  password-policy features.

User records store BCrypt password hashes, account status flags, and TOTP MFA enrolment (`mfa_enabled`, `mfa_secret`).

## How It Works

`InternalDbIdpAutoConfiguration` is registered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is gated by
`@ConditionalOnProperty(name = "firefly.security.idp.provider", havingValue = "internal-db")` and the presence of an R2DBC
`ConnectionFactory` on the classpath. When active it:

1. Enables `InternalDbProperties` and the R2DBC repositories in `org.fireflyframework.security.idp.internaldb.repository`.
2. Registers a strength-12 `BCryptPasswordEncoder` (unless one is already defined).
3. Wires the service layer — JWT, authentication, user management, roles, password reset, password policy and MFA.
4. Exposes the `InternalDbIdpAdapter` as the `IdpAdapter` bean consumed by the IDP core web layer.

Because every bean is `@ConditionalOnMissingBean`, you can replace any individual collaborator (for example, supply a
custom `PasswordEncoder` or `JwtTokenService`) without forking the module.

## Documentation

- Framework module catalog and docs hub: [github.com/fireflyframework](https://github.com/fireflyframework)
- IDP core SPI and DTOs: [fireflyframework-security-idp](https://github.com/fireflyframework/fireflyframework-security-idp)
- In-repo documentation in [`docs/`](docs/):
  - [API reference](docs/API.md)
  - [DTO field mapping](docs/DTO_FIELD_MAPPING.md)
  - [Integration guide](docs/INTEGRATION.md)
  - [Technical details](docs/technical-details.md)
  - [Project status](docs/PROJECT_STATUS.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
