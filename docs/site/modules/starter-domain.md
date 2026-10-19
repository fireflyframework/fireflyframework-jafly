![Jafly. Starter Domain](../assets/modules/starter-domain.svg)

# Starter Domain

Assemble the framework dependencies used by domain services.

Use this dependency bundle when a domain service needs the framework CQRS, orchestration, and client stack. It contains no authored production Java API of its own; the behavior comes from its dependencies and their auto-configurations.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-starter-domain` | `jar` | [modules/starter-domain](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-domain) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

Use this dependency bundle when application submission/review grows into a domain service with the included CQRS, orchestration, and client facilities. The starter adds dependencies rather than defining the product's handlers.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-domain</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

```java
@Service
@Saga(name = "create-account")
public class AccountSaga {

    @SagaStep(id = "validate", compensate = "rollbackValidation")
    public Mono<ValidationResult> validate(@Input CreateAccountRequest request) {
        return validateRequest(request);
    }

    @SagaStep(id = "persist", dependsOn = "validate", compensate = "deleteAccount")
    public Mono<Account> persist(@FromStep("validate") ValidationResult result) {
        return persistAccount(result);
    }

    public Mono<Void> deleteAccount(@FromStep("persist") Account account) {
        return removeAccount(account.getId());
    }
}
```

## Configuration and wiring

This module has no authored configuration-properties class. Its behavior is configured through the Maven generator or the dependencies listed below.

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Validators](validators.md) | Apply Jakarta Bean Validation constraints to financial, identity, contact, and date fields. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [: Orchestration](orchestration.md) | Coordinate workflows, compensating sagas, and try-confirm-cancel operations in a reactive application. |
| [Common CQRS Library](cqrs.md) | Separate commands and queries with reactive dispatch, execution context, validation, and authorization hooks. |
| [Common Client Library](client.md) | Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-domain -am verify
./mvnw -pl :fireflyframework-starter-domain -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/src/test/java/org/fireflyframework/domain/config/AutoConfigurationTest.java).

## Test fixture

The `shouldAutoConfigureCqrsFrameworkWhenEnabled` test demonstrates one concrete behavior. Excerpt from [AutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/src/test/java/org/fireflyframework/domain/config/AutoConfigurationTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("Should auto-configure CQRS framework when enabled")
void shouldAutoConfigureCqrsFrameworkWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "firefly.cqrs.enabled=true"
        )
        .run(context -> {
            assertThat(context).hasSingleBean(CommandBus.class);
            assertThat(context).hasSingleBean(QueryBus.class);
            assertThat(context).hasSingleBean(CorrelationContext.class);

            CommandBus commandBus = context.getBean(CommandBus.class);
            assertThat(commandBus).isNotNull();

            QueryBus queryBus = context.getBean(QueryBus.class);
            assertThat(queryBus).isNotNull();
        });
}
```

## Detailed reference

### Configuration

```yaml
firefly:
  domain:
    json-logging:
      enabled: true
  orchestration:
    enabled: true
```

## Continue the application

Use the [CQRS guide](cqrs.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/pom.xml).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/src/main/resources/application.yml).
- [banner.txt](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/src/main/resources/banner.txt).
- [logback-spring.xml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-domain/src/main/resources/logback-spring.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
