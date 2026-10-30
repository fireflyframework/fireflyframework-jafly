![Jafly. IDP Keycloak](../assets/modules/security-idp-keycloak.svg)

# IDP Keycloak

Implement identity-provider operations against Keycloak.

Configure server URL, realm, and service-client credentials. Match the realm client roles and permissions to the operations used through IdpAdapter; operate Keycloak separately from this library.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-idp-keycloak` | `jar` | [modules/security-idp-keycloak](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-keycloak) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Manage applicant identities in the configured Keycloak realm. Set its server and service-client credentials before invoking the adapter, and keep the identity lifecycle separate from saved applications.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-keycloak</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

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

## Configuration and wiring

The `firefly.security.idp.keycloak` namespace is bound by [KeycloakProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/properties/KeycloakProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |

Property-based wiring conditions found in this module:

- `name = "firefly.security.idp.provider", havingValue = "keycloak"`. [KeycloakAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/config/KeycloakAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`KeycloakProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/properties/KeycloakProperties.java) | Implement identity-provider operations against Keycloak. |
| [`IdpAdminService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/adapter/service/IdpAdminService.java) | Defines `createUser`, `changePassword`, `resetPassword`, `assignTemporaryPassword` operations. |
| [`IdpUserService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/adapter/service/IdpUserService.java) | Defines `login`, `refresh`, `logout`, `introspect` operations. |
| [`TokenService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/adapter/service/TokenService.java) | Defines `parseAccessToken`, `extractUserId`, `extractSessionId`, `extractRoles` operations. |
| [`LibIdpKeycloakImplApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/LibIdpKeycloakImplApplication.java) | Defines `main` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Identity Provider](security-idp.md) | Define identity-provider contracts and common authentication/user/group management services. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-keycloak -am verify
./mvnw -pl :fireflyframework-security-idp-keycloak -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [KeycloakProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/java/org/fireflyframework/security/idp/properties/KeycloakProperties.java). This fragment needs the full type and its imports.

```java
public record KeycloakProperties(
        String clientSecret,
) {
    public KeycloakProperties {
        if (serverUrl != null && !serverUrl.endsWith("/")) {
            serverUrl = serverUrl + "/";
        }
        if (connectionPoolSize == null) connectionPoolSize = 10;
        if (connectionTimeout == null) connectionTimeout = 30000;
        if (requestTimeout == null) requestTimeout = 60000;
    }
}
```

## Detailed reference

### Configuration

All properties live under the `firefly.security.idp.keycloak.*` prefix and are bound by the validated `KeycloakProperties` record. Provider selection itself is the top-level `firefly.security.idp.provider` switch.

```yaml
firefly:
  security:
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
| `firefly.security.idp.provider` | yes |. | Provider selector; set to `keycloak` to activate this adapter. |
| `firefly.security.idp.keycloak.server-url` | yes |. | Base URL of the Keycloak server. Trailing slash is added automatically if missing. |
| `firefly.security.idp.keycloak.realm` | yes |. | Keycloak realm used for authentication and administration. |
| `firefly.security.idp.keycloak.client-id` | yes |. | OAuth2 client id used by the adapter. |
| `firefly.security.idp.keycloak.client-secret` | no |. | Client secret; required for the client-credentials grant used by admin operations. |
| `firefly.security.idp.keycloak.connection-pool-size` | no | `10` | Maximum size of the HTTP connection pool to Keycloak (must be positive). |
| `firefly.security.idp.keycloak.connection-timeout` | no | `30000` | Connection timeout in milliseconds (must be positive). |
| `firefly.security.idp.keycloak.request-timeout` | no | `60000` | Request timeout in milliseconds (must be positive). |

Validation fails fast on startup if `server-url`, `realm`, or `client-id` are blank, or if any timeout/pool value is non-positive.

## Continue the application

Use the [Identity Provider guide](security-idp.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-keycloak/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-keycloak/src/main/resources/application.yaml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
