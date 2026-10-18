![Jafly. IDP - Internal Database Implementation](../assets/modules/security-idp-internal-db.svg)

# IDP - Internal Database Implementation

Implement identity-provider authentication and user/group persistence with an internal reactive database.

Configure the database and signing/password settings, apply the included schema, and invoke IdpAdapter through the common identity services. Review credential storage, token issuance, and user lifecycle tests before deploying an internal authority.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-idp-internal-db` | `jar` | [modules/security-idp-internal-db](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-internal-db) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Use the internal database adapter when this service owns its identity store. Configure its database, schema, and token settings; keep those credentials and users separate from application submission records.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-internal-db</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Select this adapter and configure R2DBC. The adapter activates only when `firefly.security.idp.provider=internal-db`:

```yaml
firefly:
  security:
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

2. Inject the `IdpAdapter`. No internal-db classes need to be referenced. Code against the core SPI so the same
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

## Configuration and wiring

The `firefly.security.idp.internal-db` namespace is bound by [InternalDbProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/config/InternalDbProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.internal-db.jwt` | `JwtConfig` | `new JwtConfig()` |
| `firefly.security.idp.internal-db.password-reset` | `PasswordResetConfig` | `new PasswordResetConfig()` |
| `firefly.security.idp.internal-db.lockout` | `LockoutConfig` | `new LockoutConfig()` |
| `firefly.security.idp.internal-db.password-policy` | `PasswordPolicyConfig` | `new PasswordPolicyConfig()` |
| `firefly.security.idp.internal-db.mfa` | `MfaConfig` | `new MfaConfig()` |

Property-based wiring conditions found in this module:

- `name = "firefly.security.idp.provider", havingValue = "internal-db"`. [InternalDbIdpAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/config/InternalDbIdpAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`InternalDbIdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/adapter/InternalDbIdpAdapter.java) | Internal Database implementation of the Firefly IDP Adapter. |
| [`InternalDbProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/config/InternalDbProperties.java) | Configuration properties for Internal Database IDP implementation. |
| [`PasswordResetTokenRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/repository/PasswordResetTokenRepository.java) | Reactive repository for password reset token operations. |
| [`RefreshTokenRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/repository/RefreshTokenRepository.java) | Reactive repository for RefreshToken entity operations. |
| [`RoleRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/repository/RoleRepository.java) | Reactive repository for Role entity operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Identity Provider](security-idp.md) | Define identity-provider contracts and common authentication/user/group management services. |
| [R2DBC](r2dbc.md) | Build reactive repository filtering and pagination on Spring Data R2DBC. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-internal-db -am verify
./mvnw -pl :fireflyframework-security-idp-internal-db -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AuthenticationServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/test/java/org/fireflyframework/security/idp/internaldb/service/AuthenticationServiceTest.java).
- [RoleServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/test/java/org/fireflyframework/security/idp/internaldb/service/RoleServiceTest.java).
- [UserManagementServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/test/java/org/fireflyframework/security/idp/internaldb/service/UserManagementServiceTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `shouldCreateMultipleRoles` test demonstrates one concrete behavior. Excerpt from [RoleServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/test/java/org/fireflyframework/security/idp/internaldb/service/RoleServiceTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldCreateMultipleRoles() {
    // Given
    when(roleRepository.existsByName(anyString())).thenReturn(Mono.just(false));
    when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
        Role role = invocation.getArgument(0);
        role.setId(UUID.randomUUID());
        return Mono.just(role);
    });

    // When & Then
    StepVerifier.create(roleService.createRoles(Arrays.asList("USER", "ADMIN", "GUEST")))
            .expectNextCount(3)
            .verifyComplete();

    verify(roleRepository, times(3)).existsByName(anyString());
    verify(roleRepository, times(3)).save(any(Role.class));
}
```

## Detailed reference

### Configuration

All adapter settings live under the `firefly.security.idp.internal-db` prefix and are bound by `InternalDbProperties`. The
provider selector (`firefly.security.idp.provider`) belongs to the IDP core.

```yaml
firefly:
  security:
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

### Database Schema

Flyway migrations under `classpath:db/migration` create and evolve the schema:

- `V1__Create_IDP_Tables.sql`. `idp_users`, `idp_roles`, `idp_user_roles`, `idp_sessions`, `idp_refresh_tokens`
  (with the necessary indexes, unique constraints and foreign keys).
- `V2__Add_Password_Reset_Tokens.sql`. `idp_password_reset_tokens`.
- `V3__Add_Lockout_And_Policy.sql`. failed-attempt counters and lockout columns supporting the account-lockout and
  password-policy features.

User records store BCrypt password hashes, account status flags, and TOTP MFA enrolment (`mfa_enabled`, `mfa_secret`).

### How It Works

`InternalDbIdpAutoConfiguration` is registered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is gated by
`@ConditionalOnProperty(name = "firefly.security.idp.provider", havingValue = "internal-db")` and the presence of an R2DBC
`ConnectionFactory` on the classpath. When active it:

1. Enables `InternalDbProperties` and the R2DBC repositories in `org.fireflyframework.security.idp.internaldb.repository`.
2. Registers a strength-12 `BCryptPasswordEncoder` (unless one is already defined).
3. Wires the service layer. JWT, authentication, user management, roles, password reset, password policy and MFA.
4. Exposes the `InternalDbIdpAdapter` as the `IdpAdapter` bean consumed by the IDP core web layer.

Because every bean is `@ConditionalOnMissingBean`, you can replace any individual collaborator (for example, supply a
custom `PasswordEncoder` or `JwtTokenService`) without forking the module.

## Continue the application

Use the [Identity Provider guide](security-idp.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-internal-db/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-internal-db-example.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/resources/application-internal-db-example.yml).
- [V1__Create_IDP_Tables.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/resources/db/migration/V1__Create_IDP_Tables.sql).
- [V2__Add_Password_Reset_Tokens.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/resources/db/migration/V2__Add_Password_Reset_Tokens.sql).
- [V3__Add_Lockout_And_Policy.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/resources/db/migration/V3__Add_Lockout_And_Policy.sql).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
