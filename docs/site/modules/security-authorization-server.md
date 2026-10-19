![Jafly. Security Authorization Server](../assets/modules/security-authorization-server.svg)

# Security Authorization Server

Configure a servlet OAuth2/OIDC authorization server with JWKS backed by KeyManagementPort.

Run this component in a dedicated servlet deployment with registered clients and key-management wiring. Reactive resource servers consume its tokens separately; do not place incompatible servlet and reactive security assumptions into one application.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-authorization-server` | `jar` | [modules/security-authorization-server](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-authorization-server) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Use this separate servlet service when the deployment needs to issue its own OAuth2/OIDC tokens. The reactive onboarding service must trust its published JWKS; the two processes do not share default development keys.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-authorization-server</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

With the module on the classpath of a servlet Spring Boot application, the authorization server is fully wired. OIDC discovery, JWKS, and the token endpoint are live with zero code:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthorizationServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServerApplication.class, args);
    }
}
```

Request a token with the bundled demo client:

```bash
curl -u firefly-demo:firefly-secret \
  -d 'grant_type=client_credentials&scope=read' \
  http://localhost:8080/oauth2/token
```

The response carries a signed JWT (`access_token`, `"token_type":"Bearer"`) verifiable against this authorization server's published JWKS. A separately deployed resource server has a different default in-memory key: configure its `jwk-set-uri` to this server's `/oauth2/jwks` endpoint and set the expected issuer, or supply explicitly shared key management.

For production, override the client store and the key port by contributing your own beans (both are `@ConditionalOnMissingBean`):

```java
@Bean
RegisteredClientRepository registeredClientRepository() {
    RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("payments-service")
            .clientSecret(System.getenv("PAYMENTS_CLIENT_SECRET_HASH"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("payments.read")
            .build();
    return new InMemoryRegisteredClientRepository(client);
}

```

Set `PAYMENTS_CLIENT_SECRET_HASH` to an encoded secret accepted by Spring Security's password encoder. Provide an application-owned `KeyManagementPort` implementation for durable signing keys and rotation; the shipped Vault adapter implements `SecretsPort`, not key management. Configure downstream services from the [resource-server guide](security-resource-server.md).

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AuthorizationServerConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-authorization-server/src/main/java/org/fireflyframework/security/authz/AuthorizationServerConfiguration.java) | Optional, servlet-only OAuth2/OIDC Authorization Server built on Spring Authorization Server. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security SPI](security-spi.md) | Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services. |
| [Security Core](security-core.md) | Evaluate the framework security model with authority mapping, policy decisions, introspection caching, and principal projection. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-authorization-server -am verify
./mvnw -pl :fireflyframework-security-authorization-server -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AuthorizationServerIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-authorization-server/src/test/java/org/fireflyframework/security/authz/AuthorizationServerIntegrationTest.java).

## Test fixture

The `issuesClientCredentialsTokenAsJwt` test demonstrates one concrete behavior. Excerpt from [AuthorizationServerIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-authorization-server/src/test/java/org/fireflyframework/security/authz/AuthorizationServerIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void issuesClientCredentialsTokenAsJwt() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth("firefly-demo", "firefly-secret");
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "read");

    ResponseEntity<String> response = restTemplate.postForEntity(
            "/oauth2/token", new HttpEntity<>(form, headers), String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("access_token").contains("\"token_type\":\"Bearer\"");
    // access_token is a signed JWT: three dot-separated base64url segments
    String body = response.getBody();
    int idx = body.indexOf("\"access_token\":\"") + "\"access_token\":\"".length();
    String token = body.substring(idx, body.indexOf('"', idx));
    assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(2);
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and providers attach as outboard adapters:

```
security-api  →  security-spi  →  security-core  →  security-authorization-server  →  adapters
 (ports +         (driven           (neutral          (this module:                      (Vault, KMS,
  domain)          ports)            engine +          Spring Authorization Server         internal-db
                                     dev key)          issuer wiring, servlet-only)        client store, …)
```

- `security-api` defines the domain. including `SigningKey`, the RSA key-pair record this server turns into JWKS entries.
- `security-spi` defines the driven port this module consumes: `KeyManagementPort` (`activeSigningKey()`, `verificationKeys()`, `jwkSetJson()`, `rotate()`).
- `security-core` supplies the framework-neutral default key adapter, `InMemoryKeyManagementAdapter`, used as the dev/default `KeyManagementPort`.
- This module binds the above into a concrete Spring Authorization Server deployment: it builds a Nimbus `JWKSource` from the port's `verificationKeys()` and wires the standard OAuth2/OIDC filter chains.
- Adapters (key management, client store) replace the in-process defaults by contributing their own `KeyManagementPort` or `RegisteredClientRepository` beans. e.g. a Vault/AWS-KMS key adapter and an internal-db client store for production.

This module depends only on `security-spi` and `security-core` (which transitively bring `security-api`), plus `spring-boot-starter-webmvc` and `spring-security-oauth2-authorization-server`. It imports no vendor SDK.

### What it provides

`AuthorizationServerConfiguration` contributes:

- The authorization-server filter chain. `authorizationServerSecurityFilterChain` (`@Order(1)`) creates `OAuth2AuthorizationServerConfigurer`, selects its endpoint matcher, applies it with `http.with(...)`, and enables OIDC (`oidc(Customizer.withDefaults())`), and redirects unauthenticated `text/html` requests to `/login`. This chain owns the OAuth2/OIDC protocol endpoints.
- A default form-login chain. `defaultSecurityFilterChain` (`@Order(2)`) requires authentication for any other request and enables form login, so the authorization/consent flows have a working login page.
- A JWKS sourced from the framework key port. `jwkSource(KeyManagementPort)` (`@ConditionalOnMissingBean`) collects `KeyManagementPort.verificationKeys()`, converts each `SigningKey` into a Nimbus `RSAKey` (public key, private key, and `kid`), and exposes them as an `ImmutableJWKSet<SecurityContext>`. The published JWKS and signer use this key set. Resource servers must be configured to trust the published keys.
- A JWT decoder built from that JWK source via `OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)`, for the server's own introspection/validation needs.
- Authorization-server settings. A default `AuthorizationServerSettings` (`@ConditionalOnMissingBean`), overridable to customise issuer URL and endpoint paths.
- A default `KeyManagementPort`. `authorizationServerKeyManagementPort()` (`@ConditionalOnMissingBean`) returns an `InMemoryKeyManagementAdapter` so the server boots with a working dev key; production contributes a real key adapter and this default steps aside.
- A demo `client_credentials` client. `registeredClientRepository()` (`@ConditionalOnMissingBean`) registers a single `RegisteredClient`. client id `firefly-demo`, secret `{noop}firefly-secret`, `client_secret_basic` authentication, the `client_credentials` grant, and scope `read`. in an `InMemoryRegisteredClientRepository`. Production overrides this bean (e.g. with an internal-db-backed store).

The auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Key types

| Type | Role |
| --- | --- |
| `AuthorizationServerConfiguration` | `@AutoConfiguration` entry point; builds the two servlet `SecurityFilterChain`s, the `JWKSource`, the `JwtDecoder`, the `AuthorizationServerSettings`, the default `KeyManagementPort`, and the demo `RegisteredClientRepository`. |

Port consumed (from `security-spi`): `KeyManagementPort`. Domain type (from `security-api`): `SigningKey`. Default key adapter (from `security-core`): `InMemoryKeyManagementAdapter`. The protocol machinery (`OAuth2AuthorizationServerConfiguration`, `OAuth2AuthorizationServerConfigurer`, `RegisteredClient`, `InMemoryRegisteredClientRepository`, `AuthorizationServerSettings`, Nimbus `JWKSource`/`RSAKey`) comes from Spring Authorization Server and Nimbus JOSE.

### Endpoints

Served on the standard Spring Authorization Server paths:

| Endpoint | Path | Purpose |
| --- | --- | --- |
| OIDC discovery | `/.well-known/openid-configuration` | Advertises `issuer`, `jwks_uri`, `token_endpoint`, etc. |
| JWKS | `/oauth2/jwks` | Public keys (`{"keys":[{"kty":"RSA",...}]}`) drawn from `KeyManagementPort.verificationKeys()`. |
| Token | `/oauth2/token` | Issues tokens for the registered grants (demo: `client_credentials`). |

### Deployment topology

```
                         ┌──────────────────────────────────────┐
                         │  Authorization Server (this module)   │
                         │  Servlet · Spring Authorization Server│
   KeyManagementPort ───►│  /oauth2/token  /oauth2/jwks  OIDC    │
   (shared key source)   └──────────────────┬───────────────────┘
                                             │  publishes JWKS / signs JWTs
                                             ▼
                         ┌──────────────────────────────────────┐
                         │  Resource Servers (separate process)  │
                         │  Reactive · Spring WebFlux            │
                         │  verify JWT signature against JWKS    │
                         └──────────────────────────────────────┘
```

The issuer and the resource servers are deployed as distinct processes on distinct stacks (servlet vs. Reactive). They are coupled only through the `KeyManagementPort` key material and the published JWKS. Never through a shared application context.

### Testing

The module ships a `@SpringBootTest(webEnvironment = RANDOM_PORT)` integration test, `AuthorizationServerIntegrationTest`, that boots a real authorization server with the real auto-configuration and exercises the OAuth2/OIDC surface over HTTP with `TestRestTemplate`:

- OIDC discovery. `GET /.well-known/openid-configuration` returns 2xx and a body containing `"issuer"`, `"jwks_uri"`, and `"token_endpoint"`.
- JWKS. `GET /oauth2/jwks` returns 2xx and a body containing `"keys"` and `"kty":"RSA"`, proving the published keys are sourced from the wired `KeyManagementPort`.
- client_credentials token. `POST /oauth2/token` with HTTP-Basic `firefly-demo:firefly-secret` and `grant_type=client_credentials&scope=read` returns 2xx with `access_token` and `"token_type":"Bearer"`, and the returned `access_token` is asserted to be a signed JWT (three dot-separated base64url segments).

These tests exercise the live local issuer endpoints and check discovery fields, RSA JWKS publication, and the three-part format of the issued access token. They do not independently verify that token through a separately configured resource-server decoder.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-authorization-server/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-authorization-server/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-authorization-server/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
