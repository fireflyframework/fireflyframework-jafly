![Jafly. Starter Application](../../docs/site/assets/modules/starter-application.svg)

# Starter Application

Build application-layer request contexts and business process coordination.

Use the application context/resolver and orchestration facilities to connect authenticated requests to domain operations. Inspect the context and security tests before customizing header or session interpretation.

[Module guide](../../docs/site/modules/starter-application.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

An application-tier onboarding API can use these request/context conventions while coordinating domain services. Populate tenant and identity values only from the service's trusted authentication boundary.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-application</artifactId>
    <version>26.10.22</version>
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

## Configuration

The `firefly.application` namespace is bound by [ApplicationLayerProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/config/ApplicationLayerProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.application.security` | `Security` | `new Security()` |
| `firefly.application.context` | `Context` | `new Context()` |
| `firefly.application.config` | `Config` | `new Config()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AbstractApplicationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/controller/AbstractApplicationController.java) | Abstract Base Controller for Application-Layer Endpoints This base class is for controllers that operate on application-level resources . |
| [`ApplicationLayerProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/config/ApplicationLayerProperties.java) | Configuration properties for the Application Layer. |
| [`AbstractResourceController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/controller/AbstractResourceController.java) | Abstract Base Controller for Resource Endpoints This base class is a thin, product-agnostic foundation for REST controllers. |
| [`ConfigResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/resolver/ConfigResolver.java) | Interface for resolving tenant configuration. |
| [`ContextResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-application/src/main/java/org/fireflyframework/common/application/resolver/ContextResolver.java) | Resolves the product-agnostic `AppContext` for a request from the validated security context. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-application -am verify
./mvnw -pl :fireflyframework-starter-application -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/starter-application.md).
