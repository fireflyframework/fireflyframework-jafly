![Jafly. Starter Application](../assets/modules/starter-application.svg)

# Starter Application

Build application-layer request contexts and business process coordination.

Use the application context/resolver and orchestration facilities to connect authenticated requests to domain operations. Inspect the context and security tests before customizing header or session interpretation.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-starter-application` | `jar` | [modules/starter-application](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-application) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

An application-tier onboarding API can use these request/context conventions while coordinating domain services. Populate tenant and identity values only from the service's trusted authentication boundary.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-application</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

```java
import org.fireflyframework.common.application.controller.AbstractApplicationController;
import org.fireflyframework.common.application.security.annotation.Secure;

@RestController
@RequestMapping("/api/accounts")
public class AccountController extends AbstractApplicationController {

    @Secure(roles = {"ADMIN", "ACCOUNT_MANAGER"})
    @PostMapping
    public Mono<AccountResponse> create(@RequestBody AccountRequest request) {
        return accountService.create(request);
    }
}
```

## Configuration and wiring

The `firefly.application` namespace is bound by [ApplicationLayerProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/config/ApplicationLayerProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.application.security` | `Security` | `new Security()` |
| `firefly.application.context` | `Context` | `new Context()` |
| `firefly.application.config` | `Config` | `new Config()` |

Property-based wiring conditions found in this module:

- `name = "firefly.application.banner.enabled", matchIfMissing = true`. [ApplicationLayerAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/config/ApplicationLayerAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AbstractApplicationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/controller/AbstractApplicationController.java) | Abstract Base Controller for Application-Layer Endpoints This base class is for controllers that operate on application-level resources . |
| [`ApplicationLayerProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/config/ApplicationLayerProperties.java) | Configuration properties for the Application Layer. |
| [`AbstractResourceController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/controller/AbstractResourceController.java) | Abstract Base Controller for Resource Endpoints This base class is a thin, product-agnostic foundation for REST controllers. |
| [`ConfigResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/resolver/ConfigResolver.java) | Interface for resolving tenant configuration. |
| [`ContextResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/resolver/ContextResolver.java) | Resolves the product-agnostic `AppContext` for a request from the validated security context. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security Resource Server](security-resource-server.md) | Validate signed JWT bearer tokens in a reactive resource server with hardened default security behavior. |
| [Security Method Policy](security-method-policy.md) | Enable reactive method authorization through Secure and the core authorization evaluator. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Common Client Library](client.md) | Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |
| [Common CQRS Library](cqrs.md) | Separate commands and queries with reactive dispatch, execution context, validation, and authorization hooks. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |
| [: Orchestration](orchestration.md) | Coordinate workflows, compensating sagas, and try-confirm-cancel operations in a reactive application. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-application -am verify
./mvnw -pl :fireflyframework-starter-application -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [FireflyApplicationInfoContributorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/actuator/FireflyApplicationInfoContributorTest.java).
- [ApplicationLayerBannerConfigTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/config/ApplicationLayerBannerConfigTest.java).
- [ApplicationLayerPropertiesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/config/ApplicationLayerPropertiesTest.java).
- [WebFluxJacksonCodecAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/config/WebFluxJacksonCodecAutoConfigurationTest.java).
- [AppConfigTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/context/AppConfigTest.java).
- [AppContextTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/context/AppContextTest.java).

## Test fixture

The `shouldCreateProviderConfigWithBuilder` test demonstrates one concrete behavior. Excerpt from [AppConfigTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/test/java/org/fireflyframework/common/application/context/AppConfigTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("Should create ProviderConfig with builder")
void shouldCreateProviderConfigWithBuilder() {
    Map<String, Object> properties = Map.of("apiKey", "secret123", "timeout", 5000);

    ProviderConfig config = ProviderConfig.builder()
        .providerType("PAYMENT_GATEWAY")
        .implementation("stripe")
        .properties(properties)
        .enabled(true)
        .priority(10)
        .build();

    assertEquals("PAYMENT_GATEWAY", config.getProviderType());
    assertEquals("stripe", config.getImplementation());
    assertTrue(config.isEnabled());
    assertEquals(10, config.getPriority());
}
```

## Detailed reference

### Configuration

```yaml
firefly:
  application:
    security:
      enabled: true
    config-cache:
      enabled: true
      ttl: 5m
    domain-passthrough:
      enabled: true
```

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-application/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/resources/application.yml).
- [banner.txt](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/resources/banner.txt).
- [logback-spring.xml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/resources/logback-spring.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
