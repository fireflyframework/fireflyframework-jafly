![Jafly. Common Backoffice Library](../assets/modules/backoffice.svg)

# Common Backoffice Library

Extend application-layer contexts for internal portals, impersonation, and backoffice resource controllers.

Use BackofficeContext and its resolver at the portal boundary. Keep impersonation metadata and security context explicit, and extend the abstract resource controller only for the operations your portal exposes.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-backoffice` | `jar` | [modules/backoffice](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/backoffice) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

A reviewer portal can use backoffice contexts and controller bases to expose internal application-review operations. Define reviewer permissions and any impersonation policy in the host service.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-backoffice</artifactId>
    <version>26.10.20</version>
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`BackofficeContext`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/context/BackofficeContext.java) | Immutable, product-agnostic backoffice context container for internal portal/backoffice requests. |
| [`BackofficeSecurityContext`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/context/BackofficeSecurityContext.java) | Immutable, product-agnostic security context for backoffice requests. |
| [`BackofficeContextResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/resolver/BackofficeContextResolver.java) | Interface for resolving the `BackofficeContext` from incoming requests. |
| [`AbstractBackofficeResourceController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/controller/AbstractBackofficeResourceController.java) | Thin, product-agnostic base for backoffice resource controllers. |
| [`AbstractBackofficeController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/main/java/org/fireflyframework/common/backoffice/controller/AbstractBackofficeController.java) | Abstract Base Controller for Backoffice Administrative Endpoints This base class is for controllers that operate on administrative resources without requiring customer impersonation. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [Starter Core](starter-core.md) | Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery. |
| [Starter Domain](starter-domain.md) | Assemble the framework dependencies used by domain services. |
| [Starter Data](starter-data.md) | Build data enrichment, job orchestration, quality, lineage, and provider operation services. |
| [Common Client Library](client.md) | Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |
| [Common CQRS Library](cqrs.md) | Separate commands and queries with reactive dispatch, execution context, validation, and authorization hooks. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |
| [Starter Application](starter-application.md) | Build application-layer request contexts and business process coordination. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-backoffice -am verify
./mvnw -pl :fireflyframework-backoffice -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [BackofficeContextTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/test/java/org/fireflyframework/common/backoffice/context/BackofficeContextTest.java).
- [AbstractBackofficeControllerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/test/java/org/fireflyframework/common/backoffice/controller/AbstractBackofficeControllerTest.java).
- [AbstractBackofficeResourceControllerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/test/java/org/fireflyframework/common/backoffice/controller/AbstractBackofficeResourceControllerTest.java).

## Test fixture

The `impersonationIsGenericSubject` test demonstrates one concrete behavior. Excerpt from [BackofficeContextTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/src/test/java/org/fireflyframework/common/backoffice/context/BackofficeContextTest.java). The test class supplies its fixtures and imports.

```java
@Test
void impersonationIsGenericSubject() {
    BackofficeContext notImpersonating = BackofficeContext.builder()
            .backofficeUserId(UUID.randomUUID()).build();
    assertThat(notImpersonating.isImpersonating()).isFalse();

    BackofficeContext impersonating = BackofficeContext.builder()
            .backofficeUserId(UUID.randomUUID())
            .impersonatedSubject("user-123")
            .impersonatedSubjectRoles(Set.of("customer"))
            .impersonationReason("Support ticket #42")
            .build();
    assertThat(impersonating.isImpersonating()).isTrue();
    assertThat(impersonating.getImpersonatedSubject()).isEqualTo("user-123");
    assertThat(impersonating.impersonatedSubjectHasRole("customer")).isTrue();
    assertThat(impersonating.getImpersonationReason()).isEqualTo("Support ticket #42");
}
```

## Detailed reference

### Configuration

```yaml
firefly:
  backoffice:
    context:
      operator-header: X-Operator-Id
      impersonation-header: X-Impersonated-Customer
```

## Continue the application

Use the [Security Method Policy guide](security-method-policy.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/backoffice/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/backoffice/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
