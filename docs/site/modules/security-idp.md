![Jafly. Identity Provider](../assets/modules/security-idp.svg)

# Identity Provider

Define identity-provider contracts and common authentication/user/group management services.

Use the IdpAdapter contract and choose the provider library for your identity system. Provider-specific configuration and SDK types belong in the matching adapter; resource-server token validation is a separate concern.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-idp` | `jar` | [modules/security-idp](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Manage applicant identities through IdpAdapter and a selected provider. Keep identity credentials and user lifecycle separate from the submitted onboarding application; validate incoming tokens with the resource-server module.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Select a provider in `application.yaml`:

```yaml
firefly:
  security:
    idp:
      provider: keycloak   # keycloak | cognito | azure-ad | internal-db
```

With the chosen adapter on the classpath, its `IdpAdapter` bean is auto-configured and the `IdpController` is mounted under `/idp` automatically. No code required to expose the standard IDP REST API.

2. Or consume the `IdpAdapter` directly from your own services:

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

## Configuration and wiring

The `firefly.security.idp` namespace is bound by [IdpProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/config/IdpProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.provider` | `String` | `not initialized` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [IdpObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/observability/IdpObservabilityAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`IdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/adapter/IdpAdapter.java) | Identity Provider (IdP) adapter. the aggregate of the segregated capability ports (`AuthenticationPort`, `TokenIntrospectionPort`, `UserAdminPort`, `RoleScopePort`, `SessionPort`, `MfaPort`) that standardizes authentication and user-management across IdPs such as Keycloak, AWS Cognito, and Entra ID. |
| [`IdpProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/config/IdpProperties.java) | Carries `provider` data; field constraints and defaults are declared in the linked source. |
| [`AuthenticationPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/port/AuthenticationPort.java) | Segregated IdP capability port for authentication / token-lifecycle operations. |
| [`MfaPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/port/MfaPort.java) | Segregated IdP capability port for multi-factor authentication challenge/verify. |
| [`RoleScopePort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/port/RoleScopePort.java) | Segregated IdP capability port for role and scope management and role assignment. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp -am verify
./mvnw -pl :fireflyframework-security-idp -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [IdpAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/adapter/IdpAdapter.java). This fragment needs the full type and its imports.

```java
public interface IdpAdapter
        extends AuthenticationPort, TokenIntrospectionPort, UserAdminPort, RoleScopePort, SessionPort, MfaPort {
    default Mono<ResponseEntity<CreateUserResponse>> registerUser(RegisterUserRequest request) {
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .givenName(request.getFirstName())
                .familyName(request.getLastName())
                .build();
        return createUser(createRequest);
    }
}
```

## Detailed reference

### Configuration

This core module exposes a single property under the `firefly.security.idp` prefix; provider-specific keys (e.g. `firefly.security.idp.keycloak.*`, `firefly.security.idp.cognito.*`) are documented by each adapter module.

```yaml
firefly:
  security:
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

- `IdpWebAutoConfiguration`. registers `IdpController` when an `IdpAdapter` bean exists and the app is a reactive web application (`@ConditionalOnWebApplication(REACTIVE)`); also enables `IdpProperties`.
- `IdpObservabilityAutoConfiguration`. registers `IdpMetrics` when a Micrometer `MeterRegistry` is present and metrics are enabled.

### REST API

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

### Observability

`IdpMetrics` (auto-configured) records, all tagged by `provider`:

- `firefly.security.idp.authentications`. total auth attempts, tagged `status=success|failure`
- `firefly.security.idp.authentication.duration`. authentication latency timer
- `firefly.security.idp.token.issued`. tokens issued, tagged `token.type`
- `firefly.security.idp.token.refreshed`. token refreshes
- `firefly.security.idp.errors`. failed IDP operations, tagged `operation`, `error.type`

Adapters wrap their authentication calls with `IdpMetrics.timedAuthentication(provider, mono)` to get success/failure counters and the latency timer for free.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/resources/application.yaml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
