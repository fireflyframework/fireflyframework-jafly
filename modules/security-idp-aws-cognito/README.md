![Jafly. IDP - AWS Cognito Implementation](../../docs/site/assets/modules/security-idp-aws-cognito.svg)

# IDP - AWS Cognito Implementation

Implement the identity-provider adapter using Amazon Cognito user pools.

Configure the Cognito user pool, app client, region, and AWS access for the chosen operation. The application should depend on IdpAdapter rather than calling the Cognito SDK directly at every use site.

[Module guide](../../docs/site/modules/security-idp-aws-cognito.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use an existing Cognito user pool for applicant identities. Configure its region, pool, and client before using the identity operations; the submitted application remains in the service-owned repository.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-aws-cognito</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

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

    private final IdpAdapter idp;   // CognitoIdpAdapter wired by CognitoAutoConfiguration



    public Mono<ResponseEntity<TokenResponse>> login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return idp.login(request);   // routed to AWS Cognito InitiateAuth
    }
}
```

Because `AuthService` depends only on `IdpAdapter`, switching to another backend later means swapping the adapter dependency and changing `firefly.security.idp.provider`. The service code is unchanged.

## Configuration

The `firefly.security.idp.cognito` namespace is bound by [CognitoProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/properties/CognitoProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.cognito.region` | `String` | `"us-east-1"` |
| `firefly.security.idp.cognito.user-pool-id` | `String` | `not initialized` |
| `firefly.security.idp.cognito.client-id` | `String` | `not initialized` |
| `firefly.security.idp.cognito.client-secret` | `String` | `not initialized` |
| `firefly.security.idp.cognito.domain` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`CognitoIdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/adapter/CognitoIdpAdapter.java) | AWS Cognito implementation of the Firefly IDP Adapter. |
| [`CognitoProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/properties/CognitoProperties.java) | Configuration properties for AWS Cognito Identity Provider. |
| [`CognitoClientFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/client/CognitoClientFactory.java) | Factory for creating and managing AWS Cognito Identity Provider clients. |
| [`CognitoAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/config/CognitoAutoConfiguration.java) | Spring auto-configuration for AWS Cognito IDP Adapter. |
| [`CognitoAdminService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-aws-cognito/src/main/java/org/fireflyframework/security/idp/cognito/service/CognitoAdminService.java) | Service for handling AWS Cognito admin operations. |

## Continue the application

Use the [Identity Provider guide](../../docs/site/modules/security-idp.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-aws-cognito -am verify
./mvnw -pl :fireflyframework-security-idp-aws-cognito -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-idp-aws-cognito.md).
