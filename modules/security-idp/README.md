![Jafly. Identity Provider](../../docs/site/assets/modules/security-idp.svg)

# Identity Provider

Define identity-provider contracts and common authentication/user/group management services.

Use the IdpAdapter contract and choose the provider library for your identity system. Provider-specific configuration and SDK types belong in the matching adapter; resource-server token validation is a separate concern.

[Module guide](../../docs/site/modules/security-idp.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Manage applicant identities through IdpAdapter and a selected provider. Keep identity credentials and user lifecycle separate from the submitted onboarding application; validate incoming tokens with the resource-server module.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

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
import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final IdpAdapter idp;



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

The `firefly.security.idp` namespace is bound by [IdpProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/config/IdpProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.provider` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`IdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/adapter/IdpAdapter.java) | Identity Provider (IdP) adapter. the aggregate of the segregated capability ports (`AuthenticationPort`, `TokenIntrospectionPort`, `UserAdminPort`, `RoleScopePort`, `SessionPort`, `MfaPort`) that standardizes authentication and user-management across IdPs such as Keycloak, AWS Cognito, and Entra ID. |
| [`IdpProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/config/IdpProperties.java) | Carries `provider` data; field constraints and defaults are declared in the linked source. |
| [`AuthenticationPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/port/AuthenticationPort.java) | Segregated IdP capability port for authentication / token-lifecycle operations. |
| [`MfaPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/port/MfaPort.java) | Segregated IdP capability port for multi-factor authentication challenge/verify. |
| [`RoleScopePort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp/src/main/java/org/fireflyframework/security/idp/port/RoleScopePort.java) | Segregated IdP capability port for role and scope management and role assignment. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp -am verify
./mvnw -pl :fireflyframework-security-idp -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-idp.md).
