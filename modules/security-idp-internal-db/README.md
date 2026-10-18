![Jafly. IDP - Internal Database Implementation](../../docs/site/assets/modules/security-idp-internal-db.svg)

# IDP - Internal Database Implementation

Implement identity-provider authentication and user/group persistence with an internal reactive database.

Configure the database and signing/password settings, apply the included schema, and invoke IdpAdapter through the common identity services. Review credential storage, token issuance, and user lifecycle tests before deploying an internal authority.

[Module guide](../../docs/site/modules/security-idp-internal-db.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use the internal database adapter when this service owns its identity store. Configure its database, schema, and token settings; keep those credentials and users separate from application submission records.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

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

## Configuration

The `firefly.security.idp.internal-db` namespace is bound by [InternalDbProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/config/InternalDbProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.internal-db.jwt` | `JwtConfig` | `new JwtConfig()` |
| `firefly.security.idp.internal-db.password-reset` | `PasswordResetConfig` | `new PasswordResetConfig()` |
| `firefly.security.idp.internal-db.lockout` | `LockoutConfig` | `new LockoutConfig()` |
| `firefly.security.idp.internal-db.password-policy` | `PasswordPolicyConfig` | `new PasswordPolicyConfig()` |
| `firefly.security.idp.internal-db.mfa` | `MfaConfig` | `new MfaConfig()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`InternalDbIdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/adapter/InternalDbIdpAdapter.java) | Internal Database implementation of the Firefly IDP Adapter. |
| [`InternalDbProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/config/InternalDbProperties.java) | Configuration properties for Internal Database IDP implementation. |
| [`PasswordResetTokenRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/repository/PasswordResetTokenRepository.java) | Reactive repository for password reset token operations. |
| [`RefreshTokenRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/repository/RefreshTokenRepository.java) | Reactive repository for RefreshToken entity operations. |
| [`RoleRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-internal-db/src/main/java/org/fireflyframework/security/idp/internaldb/repository/RoleRepository.java) | Reactive repository for Role entity operations. |

## Continue the application

Use the [Identity Provider guide](../../docs/site/modules/security-idp.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-internal-db -am verify
./mvnw -pl :fireflyframework-security-idp-internal-db -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-idp-internal-db.md).
