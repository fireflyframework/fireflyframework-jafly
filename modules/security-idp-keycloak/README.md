![Jafly. IDP Keycloak](../../docs/site/assets/modules/security-idp-keycloak.svg)

# IDP Keycloak

Implement identity-provider operations against Keycloak.

Configure server URL, realm, and service-client credentials. Match the realm client roles and permissions to the operations used through IdpAdapter; operate Keycloak separately from this library.

[Module guide](../../docs/site/modules/security-idp-keycloak.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Manage applicant identities in the configured Keycloak realm. Set its server and service-client credentials before invoking the adapter, and keep the identity lifecycle separate from saved applications.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-keycloak</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Select the Keycloak provider and point it at your server (`application.yaml`):

```yaml
firefly:
  security:
    idp:
      provider: keycloak          # activates KeycloakAutoConfiguration
      keycloak:
        server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8080}
        realm: ${KEYCLOAK_REALM:master}
        client-id: ${KEYCLOAK_CLIENT_ID:firefly_client}
        client-secret: ${KEYCLOAK_CLIENT_SECRET:}
```

2. Inject the `IdpAdapter` and call it. The same provider-agnostic API works for every IDP backend:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.LoginRequest;
import org.fireflyframework.security.idp.dtos.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final IdpAdapter idp;



    public Mono<ResponseEntity<TokenResponse>> login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return idp.login(request);   // Keycloak ROPC flow under the hood
    }
}
```

Because the contract lives in `fireflyframework-security-idp`, switching to Cognito, Azure AD, or the internal-db provider is just a dependency swap plus a change to `firefly.security.idp.provider`. Your application code is untouched.

## Configuration

The `firefly.security.idp.keycloak` namespace is bound by [KeycloakProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/properties/KeycloakProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`KeycloakProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/properties/KeycloakProperties.java) | Implement identity-provider operations against Keycloak. |
| [`IdpAdminService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/adapter/service/IdpAdminService.java) | Defines `createUser`, `changePassword`, `resetPassword`, `assignTemporaryPassword` operations. |
| [`IdpUserService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/adapter/service/IdpUserService.java) | Defines `login`, `refresh`, `logout`, `introspect` operations. |
| [`TokenService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/adapter/service/TokenService.java) | Defines `parseAccessToken`, `extractUserId`, `extractSessionId`, `extractRoles` operations. |
| [`LibIdpKeycloakImplApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/LibIdpKeycloakImplApplication.java) | Defines `main` operations. |

## Continue the application

Use the [Identity Provider guide](../../docs/site/modules/security-idp.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-keycloak -am verify
./mvnw -pl :fireflyframework-security-idp-keycloak -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-idp-keycloak.md).
