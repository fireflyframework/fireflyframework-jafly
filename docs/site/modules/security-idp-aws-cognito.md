![Jafly. IDP - AWS Cognito Implementation](../assets/modules/security-idp-aws-cognito.svg)

# IDP - AWS Cognito Implementation

Implement the identity-provider adapter using Amazon Cognito user pools.

Configure the Cognito user pool, app client, region, and AWS access for the chosen operation. The application should depend on IdpAdapter rather than calling the Cognito SDK directly at every use site.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-idp-aws-cognito` | `jar` | [modules/security-idp-aws-cognito](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-aws-cognito) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Use an existing Cognito user pool for applicant identities. Configure its region, pool, and client before using the identity operations; the submitted application remains in the service-owned repository.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-aws-cognito</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Select the Cognito provider and configure your User Pool in `application.yml`:

```yaml
firefly:
  security:
    idp:
      provider: cognito          # activates this adapter
      cognito:
        region: us-east-1
        user-pool-id: us-east-1_XXXXXXXXX
        client-id: your-app-client-id
        client-secret: your-app-client-secret   # only for app clients with a secret
```

2. Inject the provider-neutral `IdpAdapter`. Auto-configuration supplies the Cognito-backed bean:

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

    private final IdpAdapter idp;   // CognitoIdpAdapterImpl wired by CognitoAutoConfiguration



    public Mono<ResponseEntity<TokenResponse>> login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return idp.login(request);   // routed to AWS Cognito InitiateAuth
    }
}
```

Because `AuthService` depends only on `IdpAdapter`, switching to another backend later means swapping the adapter dependency and changing `firefly.security.idp.provider`. The service code is unchanged.

## Configuration and wiring

The `firefly.security.idp.cognito` namespace is bound by [CognitoProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/properties/CognitoProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.cognito.region` | `String` | `"us-east-1"` |
| `firefly.security.idp.cognito.user-pool-id` | `String` | `not initialized` |
| `firefly.security.idp.cognito.client-id` | `String` | `not initialized` |
| `firefly.security.idp.cognito.client-secret` | `String` | `not initialized` |
| `firefly.security.idp.cognito.domain` | `String` | `not initialized` |
| `firefly.security.idp.cognito.endpoint-override` | `String` | `not initialized` |
| `firefly.security.idp.cognito.connection-timeout` | `Integer` | `30000` |
| `firefly.security.idp.cognito.request-timeout` | `Integer` | `60000` |

Property-based wiring conditions found in this module:

- `name = "firefly.security.idp.provider", havingValue = "cognito"`. [CognitoAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/config/CognitoAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`CognitoIdpAdapterImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/adapter/CognitoIdpAdapterImpl.java) | AWS Cognito implementation of the Firefly IDP Adapter. |
| [`CognitoProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/properties/CognitoProperties.java) | Configuration properties for AWS Cognito Identity Provider. |
| [`CognitoClientFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/client/CognitoClientFactory.java) | Factory for creating and managing AWS Cognito Identity Provider clients. |
| [`CognitoAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/config/CognitoAutoConfiguration.java) | Spring auto-configuration for AWS Cognito IDP Adapter. |
| [`CognitoAdminService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/service/CognitoAdminService.java) | Service for handling AWS Cognito admin operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Identity Provider](security-idp.md) | Define identity-provider contracts and common authentication/user/group management services. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-aws-cognito -am verify
./mvnw -pl :fireflyframework-security-idp-aws-cognito -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [CognitoIdpAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/test/java/org/fireflyframework/security/idp/cognito/adapter/CognitoIdpAdapterTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `testLoginFailureUserNotFound` test demonstrates one concrete behavior. Excerpt from [CognitoIdpAdapterLocalStackIT.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/test/java/org/fireflyframework/security/idp/cognito/adapter/CognitoIdpAdapterLocalStackIT.java). The test class supplies its fixtures and imports.

```java
@Test
@Order(11)
@DisplayName("Should handle authentication failure for non-existent user")
void testLoginFailureUserNotFound() {
    LoginRequest request = LoginRequest.builder()
            .username("nonexistent-user-12345")
            .password("anypassword")
            .scope("openid")
            .build();

    StepVerifier.create(adapter.login(request))
            .assertNext(response -> {
                // User doesn't exist - should return 404 NOT_FOUND
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody()).isNull();
            })
            .verifyComplete();
}
```

## Detailed reference

### Configuration

All properties are bound from `CognitoProperties` under the `firefly.security.idp.cognito` prefix. Activation additionally requires the top-level selector `firefly.security.idp.provider=cognito`.

```yaml
firefly:
  security:
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
| `firefly.security.idp.provider` | Yes |. | Must be `cognito` to activate this adapter. |
| `firefly.security.idp.cognito.region` | Yes (non-blank) | `us-east-1` | AWS region where the Cognito User Pool lives. |
| `firefly.security.idp.cognito.user-pool-id` | Yes |. | Cognito User Pool ID. |
| `firefly.security.idp.cognito.client-id` | Yes |. | Cognito app client ID. |
| `firefly.security.idp.cognito.client-secret` | No |. | App client secret; when set, the adapter computes and sends `SECRET_HASH`. |
| `firefly.security.idp.cognito.domain` | No |. | Cognito hosted-UI domain. |
| `firefly.security.idp.cognito.endpoint-override` | No |. | Custom Cognito endpoint URI (used to point at LocalStack in tests). |
| `firefly.security.idp.cognito.connection-timeout` | No | `30000` | Per-attempt API call timeout, in milliseconds. |
| `firefly.security.idp.cognito.request-timeout` | No | `60000` | Overall API call timeout (across retries), in milliseconds. |

`region`, `user-pool-id` and `client-id` are validated as non-blank (`@Validated` + `@NotBlank`); the application fails fast at startup if any are missing.

### How It Works

`CognitoAutoConfiguration` is registered as a Spring Boot `@AutoConfiguration` and is loaded automatically via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It activates only when:

- `firefly.security.idp.provider=cognito` (`@ConditionalOnProperty`), and
- `software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient` is on the classpath (`@ConditionalOnClass`).

When active, it contributes (each `@ConditionalOnMissingBean`, so you can override any of them):

1. `CognitoClientFactory`. Builds the AWS SDK v2 client from `CognitoProperties`, applying the endpoint override when configured.
2. `CognitoUserService` and `CognitoAdminService`. The end-user and administrative operation services.
3. `IdpAdapter` (a `CognitoIdpAdapterImpl`). Registered only if no other `IdpAdapter` bean exists, so a single provider wins.

Each adapter call delegates to the matching service, which obtains the lazily-cached client from the factory and invokes the corresponding Cognito API inside a `Mono.fromCallable(...)`, returning the framework's neutral response DTOs. The client is closed on application shutdown via `@PreDestroy`.

## Continue the application

Use the [Identity Provider guide](security-idp.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-aws-cognito/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
