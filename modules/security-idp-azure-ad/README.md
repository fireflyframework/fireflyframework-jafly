![Jafly. IDP - Azure AD Implementation](../../docs/site/assets/modules/security-idp-azure-ad.svg)

# IDP - Azure AD Implementation

Implement identity-provider operations for Microsoft Entra ID/Azure AD and B2C.

Configure tenant/client credentials and the selected provider mode. Match the authority/scopes and user/group permissions to the actual directory operations used by the application.

[Module guide](../../docs/site/modules/security-idp-azure-ad.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Connect applicant or reviewer identity operations to the configured Entra ID or Azure AD B2C tenant. Select the supported mode and client configuration separately from the onboarding application data.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-azure-ad</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (above) and select the provider:

```yaml
firefly:
  security:
    idp:
      provider: azure-ad        # selects this adapter
      azure-ad:
        mode: entra-id          # "entra-id" (default) or "b2c"
        tenant-id: ${AZURE_TENANT_ID}
        client-id: ${AZURE_CLIENT_ID}
        client-secret: ${AZURE_CLIENT_SECRET}
```

That is all the wiring needed. `AzureAdAutoConfiguration` registers an `IdpAdapter` bean automatically.

2. Inject the `IdpAdapter` and use it. Your code depends only on the framework SPI, never on Azure AD
types, so it stays portable across providers:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final IdpAdapter idp;



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

The `firefly.security.idp.azure-ad` namespace is bound by [AzureAdProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.azure-ad.mode` | `String` | `"entra-id"` |
| `firefly.security.idp.azure-ad.tenant-id` | `String` | `not initialized` |
| `firefly.security.idp.azure-ad.client-id` | `String` | `not initialized` |
| `firefly.security.idp.azure-ad.client-secret` | `String` | `not initialized` |
| `firefly.security.idp.azure-ad.b2c` | `B2CProperties` | `new B2CProperties()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AzureAdIdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/adapter/AzureAdIdpAdapter.java) | Azure AD implementation of the Firefly IDP Adapter. |
| [`AzureAdProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdProperties.java) | Carries `mode`, `tenantId`, `clientId`, `clientSecret`, `b2c` data; field constraints and defaults are declared in the linked source. |
| [`AzureAdAdminService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/service/AzureAdAdminService.java) | Admin service interface for Azure AD user management operations. |
| [`GraphClientFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/client/GraphClientFactory.java) | Defines `createClient`, `destroy` operations. |
| [`MsalClientFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/client/MsalClientFactory.java) | Defines `createClient`, `destroy` operations. |

## Continue the application

Use the [Identity Provider guide](../../docs/site/modules/security-idp.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-azure-ad -am verify
./mvnw -pl :fireflyframework-security-idp-azure-ad -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-idp-azure-ad.md).
