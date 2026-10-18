![Jafly. Common Backoffice Library](../../docs/site/assets/modules/backoffice.svg)

# Common Backoffice Library

Extend application-layer contexts for internal portals, impersonation, and backoffice resource controllers.

Use BackofficeContext and its resolver at the portal boundary. Keep impersonation metadata and security context explicit, and extend the abstract resource controller only for the operations your portal exposes.

[Module guide](../../docs/site/modules/backoffice.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A reviewer portal can use backoffice contexts and controller bases to expose internal application-review operations. Define reviewer permissions and any impersonation policy in the host service.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-backoffice</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

```java
import org.fireflyframework.common.backoffice.controller.AbstractBackofficeController;
import org.fireflyframework.common.backoffice.context.BackofficeContext;

@RestController
@RequestMapping("/api/backoffice/customers")
public class CustomerBackofficeController extends AbstractBackofficeController {

    @GetMapping("/{customerId}")
    public Mono<CustomerDetails> getCustomer(
            @PathVariable String customerId,
            BackofficeContext context) {
        // context provides operator identity and impersonation info
        return customerService.findById(customerId, context);
    }
}
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`BackofficeContext`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/context/BackofficeContext.java) | Immutable, product-agnostic backoffice context container for internal portal/backoffice requests. |
| [`BackofficeSecurityContext`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/context/BackofficeSecurityContext.java) | Immutable, product-agnostic security context for backoffice requests. |
| [`BackofficeContextResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/resolver/BackofficeContextResolver.java) | Interface for resolving the `BackofficeContext` from incoming requests. |
| [`AbstractBackofficeResourceController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/controller/AbstractBackofficeResourceController.java) | Thin, product-agnostic base for backoffice resource controllers. |
| [`AbstractBackofficeController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/controller/AbstractBackofficeController.java) | Abstract Base Controller for Backoffice Administrative Endpoints This base class is for controllers that operate on administrative resources without requiring customer impersonation. |

## Continue the application

Use the [Security Method Policy guide](../../docs/site/modules/security-method-policy.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-backoffice -am verify
./mvnw -pl :fireflyframework-backoffice -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/backoffice.md).
