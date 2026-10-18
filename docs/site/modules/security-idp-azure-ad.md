![Jafly. IDP - Azure AD Implementation](../assets/modules/security-idp-azure-ad.svg)

# IDP - Azure AD Implementation

Implement identity-provider operations for Microsoft Entra ID/Azure AD and B2C.

Configure tenant/client credentials and the selected provider mode. Match the authority/scopes and user/group permissions to the actual directory operations used by the application.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-idp-azure-ad` | `jar` | [modules/security-idp-azure-ad](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-azure-ad) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Connect applicant or reviewer identity operations to the configured Entra ID or Azure AD B2C tenant. Select the supported mode and client configuration separately from the onboarding application data.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-idp-azure-ad</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

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
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthFacade {

    private final IdpAdapter idp;

    public AuthFacade(IdpAdapter idp) {
        this.idp = idp;
    }

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

## Configuration and wiring

The `firefly.security.idp.azure-ad` namespace is bound by [AzureAdProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.idp.azure-ad.mode` | `String` | `"entra-id"` |
| `firefly.security.idp.azure-ad.tenant-id` | `String` | `not initialized` |
| `firefly.security.idp.azure-ad.client-id` | `String` | `not initialized` |
| `firefly.security.idp.azure-ad.client-secret` | `String` | `not initialized` |
| `firefly.security.idp.azure-ad.b2c` | `B2CProperties` | `new B2CProperties()` |
| `firefly.security.idp.azure-ad.role-mapping` | `RoleMappingProperties` | `new RoleMappingProperties()` |
| `firefly.security.idp.azure-ad.connection-timeout` | `Integer` | `30000` |
| `firefly.security.idp.azure-ad.request-timeout` | `Integer` | `60000` |
| `firefly.security.idp.azure-ad.graph` | `GraphProperties` | `new GraphProperties()` |

Property-based wiring conditions found in this module:

- `name = "firefly.security.idp.provider", havingValue = "azure-ad"`. [AzureAdAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdAutoConfiguration.java).
- `name = "firefly.security.idp.azure-ad.mode", havingValue = "entra-id", matchIfMissing = true`. [AzureAdAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdAutoConfiguration.java).
- `name = "firefly.security.idp.azure-ad.mode", havingValue = "b2c"`. [AzureAdAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AzureAdIdpAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/adapter/AzureAdIdpAdapter.java) | Azure AD implementation of the Firefly IDP Adapter. |
| [`AzureAdProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/config/AzureAdProperties.java) | Carries `mode`, `tenantId`, `clientId`, `clientSecret`, `b2c` data; field constraints and defaults are declared in the linked source. |
| [`AzureAdAdminService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/service/AzureAdAdminService.java) | Admin service interface for Azure AD user management operations. |
| [`GraphClientFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/client/GraphClientFactory.java) | Defines `createClient`, `destroy` operations. |
| [`MsalClientFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/java/org/fireflyframework/security/idp/azuread/client/MsalClientFactory.java) | Defines `createClient`, `destroy` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Identity Provider](security-idp.md) | Define identity-provider contracts and common authentication/user/group management services. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-idp-azure-ad -am verify
./mvnw -pl :fireflyframework-security-idp-azure-ad -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AzureAdIdpAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/test/java/org/fireflyframework/security/idp/azuread/adapter/AzureAdIdpAdapterTest.java).
- [AzureAdTokenParserTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/test/java/org/fireflyframework/security/idp/azuread/util/AzureAdTokenParserTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `testChangePassword_Success` test demonstrates one concrete behavior. Excerpt from [AzureAdIdpAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/test/java/org/fireflyframework/security/idp/azuread/adapter/AzureAdIdpAdapterTest.java). The test class supplies its fixtures and imports.

```java
@Test
void testChangePassword_Success() {
    // Arrange
    ChangePasswordRequest request = ChangePasswordRequest.builder()
            .userId("testuser")
            .oldPassword("oldpass")
            .newPassword("newpass")
            .build();

    when(adminService.changePassword(any(ChangePasswordRequest.class)))
            .thenReturn(Mono.empty());

    // Act & Assert
    StepVerifier.create(adapter.changePassword(request))
            .verifyComplete();

    verify(adminService).changePassword(request);
}
```

## Detailed reference

### Configuration

All properties live under the `firefly.security.idp.azure-ad` prefix and map to `AzureAdProperties`.
`firefly.security.idp.provider=azure-ad` (defined by the IDP core) is what activates this adapter.

```yaml
firefly:
  security:
    idp:
      provider: azure-ad                 # required: selects the Azure AD adapter
      azure-ad:
        mode: entra-id                   # "entra-id" (default) or "b2c"
        tenant-id: your-tenant-id        # required
        client-id: your-client-id        # required
        client-secret: your-client-secret # required
        connection-timeout: 30000        # MSAL HTTP connect timeout (ms)
        request-timeout: 60000           # MSAL HTTP read timeout (ms)
        role-mapping:
          strategy: app-roles            # "app-roles" (default), "groups", or "both"
          group-prefix: ""               # only map groups whose name starts with this prefix
        graph:
          scopes:                        # Microsoft Graph token scopes
            - https://graph.microsoft.com/.default
        b2c:                             # only used when mode = b2c
          domain: your-tenant.b2clogin.com
          sign-up-sign-in-policy: B2C_1_signup_signin
          reset-password-policy: B2C_1_password_reset
```

#### Key properties

| Property | Default | Description |
| --- | --- | --- |
| `firefly.security.idp.provider` | _(none)_ | Must be `azure-ad` to activate this adapter (defined by the IDP core). |
| `firefly.security.idp.azure-ad.mode` | `entra-id` | Selects the admin strategy: `entra-id` or `b2c`. |
| `firefly.security.idp.azure-ad.tenant-id` | _(required)_ | Azure AD / Entra tenant ID. |
| `firefly.security.idp.azure-ad.client-id` | _(required)_ | App-registration client ID. |
| `firefly.security.idp.azure-ad.client-secret` | _(required)_ | App-registration client secret. |
| `firefly.security.idp.azure-ad.connection-timeout` | `30000` | MSAL4J HTTP connect timeout in milliseconds. |
| `firefly.security.idp.azure-ad.request-timeout` | `60000` | MSAL4J HTTP read timeout in milliseconds. |
| `firefly.security.idp.azure-ad.role-mapping.strategy` | `app-roles` | How roles are derived: `app-roles`, `groups`, or `both`. |
| `firefly.security.idp.azure-ad.role-mapping.group-prefix` | `""` | Only security groups with this name prefix are mapped to roles. |
| `firefly.security.idp.azure-ad.graph.scopes` | `https://graph.microsoft.com/.default` | OAuth2 scopes used when acquiring Graph tokens. |
| `firefly.security.idp.azure-ad.b2c.domain` | _(none)_ | B2C login domain (e.g. `tenant.b2clogin.com`); drives the authority URL in B2C mode. |
| `firefly.security.idp.azure-ad.b2c.sign-up-sign-in-policy` | `B2C_1_signup_signin` | B2C sign-up/sign-in user-flow name. |
| `firefly.security.idp.azure-ad.b2c.reset-password-policy` | `B2C_1_password_reset` | B2C password-reset user-flow name. |

`tenant-id`, `client-id`, and `client-secret` are validated with `@NotBlank`. The application fails fast
at startup if any is missing. The effective MSAL authority is derived automatically:
`https://login.microsoftonline.com/{tenant-id}` for Entra ID, or
`https://{b2c.domain}/{tenant-id}` for B2C.

### How It Works

- `AzureAdAutoConfiguration`. `@ConditionalOnProperty(firefly.security.idp.provider=azure-ad)` and
  `@ConditionalOnClass(ConfidentialClientApplication.class)`; registers the client factories, services, and
  the `IdpAdapter` bean. The admin-service bean is chosen by `firefly.security.idp.azure-ad.mode`.
- `AzureAdIdpAdapter`. implements `IdpAdapter`; delegates auth/token calls to `AzureAdAuthService` and
  admin calls to the selected `AzureAdAdminService`.
- `AzureAdAuthService`. login (ROPC), refresh, logout, introspect, user info, and token revocation via
  MSAL4J + Graph.
- `EntraIdAdminService` / `B2CAdminService`. user, role, session, and password operations via the
  Microsoft Graph SDK; `B2CAdminService extends EntraIdAdminService` with B2C local-account overrides.
- `MsalClientFactory` / `GraphClientFactory`. lazily build and cache the confidential-client and Graph
  clients, released on shutdown.
- `AzureAdTokenParser`. local, dependency-free JWT decoding for introspection and user-ID extraction.

## Continue the application

Use the [Identity Provider guide](security-idp.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-idp-azure-ad/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-idp-azure-ad/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
