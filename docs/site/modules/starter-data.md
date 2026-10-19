![Jafly. Starter Data](../assets/modules/starter-data.svg)

# Starter Data

Build data enrichment, job orchestration, quality, lineage, and provider operation services.

Choose the data-job or enrichment extension points that match your use case. Configure provider access and orchestration separately; review caching, timeouts, batch behavior, and persistence for each provider workflow.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-starter-data` | `jar` | [modules/starter-data](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-data) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

Background document enrichment or verification jobs may need the data-job and provider extension points. Implement the chosen provider operation and configure its limits; these jobs are not part of the runnable submission tutorial.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-data</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

A verification-job HTTP boundary can delegate to an application-supplied `DataJobService`. This is an API class, not a complete data provider. Register a service bean implementing START, CHECK, COLLECT, RESULT, and STOP, and configure its orchestrator/provider dependencies.

```java
import org.fireflyframework.data.controller.AbstractDataJobController;
import org.fireflyframework.data.service.DataJobService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verification-jobs")
public class VerificationJobController extends AbstractDataJobController {
    public VerificationJobController(DataJobService service) {
        super(service);
    }
}
```

`AbstractDataJobController` and `DataJobService` have no generic parameters. To use `AbstractResilientDataJobService`, supply its tracing, metrics, and resiliency collaborators through a supported constructor and implement the protected lifecycle methods. The starter does not create those business operations for the application.

## Configuration and wiring

The `firefly.data` namespace is bound by [DataConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataConfiguration.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.data.eda` | `EdaConfig` | `new EdaConfig()` |
| `firefly.data.cqrs` | `CqrsConfig` | `new CqrsConfig()` |
| `firefly.data.orchestration` | `JobOrchestrationProperties` | `new JobOrchestrationProperties()` |
| `firefly.data.orchestration-engine` | `OrchestrationConfig` | `new OrchestrationConfig()` |

The `firefly.data.enrichment` namespace is bound by [DataEnrichmentProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.data.enrichment.enabled` | `boolean` | `true` |
| `firefly.data.enrichment.publish-events` | `boolean` | `true` |
| `firefly.data.enrichment.cache-enabled` | `boolean` | `false` |
| `firefly.data.enrichment.cache-ttl-seconds` | `int` | `3600` |
| `firefly.data.enrichment.default-timeout-seconds` | `int` | `30` |
| `firefly.data.enrichment.capture-raw-responses` | `boolean` | `false` |
| `firefly.data.enrichment.max-concurrent-enrichments` | `int` | `100` |
| `firefly.data.enrichment.retry-enabled` | `boolean` | `true` |
| `firefly.data.enrichment.max-retry-attempts` | `int` | `3` |
| `firefly.data.enrichment.max-batch-size` | `int` | `100` |
| `firefly.data.enrichment.batch-parallelism` | `int` | `10` |
| `firefly.data.enrichment.batch-fail-fast` | `boolean` | `false` |
| `firefly.data.enrichment.providers` | `Map<String, ProviderResiliencyConfig>` | `new HashMap<>()` |
| `firefly.data.enrichment.operations` | `OperationsConfig` | `new OperationsConfig()` |

The `firefly.data.orchestration` namespace is bound by [JobOrchestrationProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/JobOrchestrationProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.data.orchestration.enabled` | `boolean` | `true` |
| `firefly.data.orchestration.orchestrator-type` | `String` | `"APACHE_AIRFLOW"` |
| `firefly.data.orchestration.default-timeout` | `Duration` | `Duration.ofHours(24)` |
| `firefly.data.orchestration.max-retries` | `int` | `3` |
| `firefly.data.orchestration.retry-delay` | `Duration` | `Duration.ofSeconds(5)` |
| `firefly.data.orchestration.publish-job-events` | `boolean` | `true` |
| `firefly.data.orchestration.job-events-topic` | `String` | `"data-job-events"` |
| `firefly.data.orchestration.airflow` | `AirflowConfig` | `new AirflowConfig()` |
| `firefly.data.orchestration.aws-step-functions` | `AwsStepFunctionsConfig` | `new AwsStepFunctionsConfig()` |
| `firefly.data.orchestration.resiliency` | `ResiliencyConfig` | `new ResiliencyConfig()` |
| `firefly.data.orchestration.observability` | `ObservabilityConfig` | `new ObservabilityConfig()` |
| `firefly.data.orchestration.health-check` | `HealthCheckConfig` | `new HealthCheckConfig()` |
| `firefly.data.orchestration.persistence` | `PersistenceConfig` | `new PersistenceConfig()` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.data.cqrs", name = "enabled", havingValue = "true", matchIfMissing = true`. [CqrsAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/CqrsAutoConfiguration.java).
- `prefix = "firefly.data.enrichment", name = "enabled", havingValue = "true", matchIfMissing = true`. [DataEnrichmentAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentAutoConfiguration.java).
- `prefix = "firefly.data.enrichment", name = "cache-enabled", havingValue = "true"`. [DataEnrichmentAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentAutoConfiguration.java).
- `prefix = "firefly.data.enrichment.operations", name = "cache-enabled", havingValue = "true", matchIfMissing = true`. [DataEnrichmentAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentAutoConfiguration.java).
- `prefix = "firefly.data.enrichment", name = "publish-events", havingValue = "true", matchIfMissing = true`. [DataEnrichmentAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentAutoConfiguration.java).
- `prefix = "firefly.data.enrichment.operations", name = "publish-events", havingValue = "true", matchIfMissing = true`. [DataEnrichmentAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentAutoConfiguration.java).
- `prefix = "firefly.data.lineage", name = "enabled", havingValue = "true", matchIfMissing = false`. [DataLineageAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataLineageAutoConfiguration.java).
- `prefix = "firefly.data.quality", name = "enabled", havingValue = "true", matchIfMissing = true`. [DataQualityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataQualityAutoConfiguration.java).
- `prefix = "firefly.data.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [EdaAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/EdaAutoConfiguration.java).
- `prefix = "firefly.data.orchestration", name = "enabled", havingValue = "true", matchIfMissing = true`. [JobOrchestrationAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/JobOrchestrationAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`DataEnrichmentProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentProperties.java) | Configuration properties for data enrichment. |
| [`JobOrchestrationProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/JobOrchestrationProperties.java) | Configuration properties for job orchestration in fireflyframework-starter-data. |
| [`DataEnricher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/service/DataEnricher.java) | Type-safe abstract base class for data enrichers for enrichment workflows. |
| [`JobOrchestrator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/orchestration/port/JobOrchestrator.java) | Port interface for job orchestrators (e.g., AWS Step Functions, Azure Durable Functions, etc.). |
| [`AbstractDataJobController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/controller/AbstractDataJobController.java) | Abstract base controller implementation that provides logging for all job lifecycle phases. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [Common Client Library](client.md) | Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities. |
| [Common CQRS Library](cqrs.md) | Separate commands and queries with reactive dispatch, execution context, validation, and authorization hooks. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |
| [: Orchestration](orchestration.md) | Coordinate workflows, compensating sagas, and try-confirm-cancel operations in a reactive application. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-data -am verify
./mvnw -pl :fireflyframework-starter-data -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [EnrichmentCacheIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/cache/EnrichmentCacheIntegrationTest.java).
- [EnrichmentCacheKeyGeneratorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/cache/EnrichmentCacheKeyGeneratorTest.java).
- [EnrichmentCacheServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/cache/EnrichmentCacheServiceTest.java).
- [OperationCacheServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/cache/OperationCacheServiceTest.java).
- [DataConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/config/DataConfigurationTest.java).
- [DataEnrichmentPropertiesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/config/DataEnrichmentPropertiesTest.java).

## Test fixture

The `generateKey_shouldProduceDifferentKeysForDifferentProviders` test demonstrates one concrete behavior. Excerpt from [EnrichmentCacheKeyGeneratorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/test/java/org/fireflyframework/data/cache/EnrichmentCacheKeyGeneratorTest.java). The test class supplies its fixtures and imports.

```java
@Test
void generateKey_shouldProduceDifferentKeysForDifferentProviders() {
    // Given
    EnrichmentRequest request = EnrichmentRequest.builder()
            .type("company-profile")
            .parameters(Map.of("companyId", "12345"))
            .tenantId(TENANT_ABC)
            .build();

    // When
    String key1 = keyGenerator.generateKey(request, "Provider1");
    String key2 = keyGenerator.generateKey(request, "Provider2");

    // Then
    assertThat(key1).isNotEqualTo(key2);
    assertThat(key1).contains("Provider1");
    assertThat(key2).contains("Provider2");
}
```

## Detailed reference

### Configuration

```yaml
firefly:
  data:
    orchestration:
      enabled: false
    enrichment:
      enabled: true
      cache-enabled: true
      cache-ttl-seconds: 300
      operations:
        cache-enabled: true
        cache-ttl-seconds: 600
    quality:
      enabled: true
    lineage:
      enabled: true
```

This example enables enrichment, quality-rule discovery, and the default in-memory lineage tracker. Register application-owned enrichers and quality rules; lineage records are lost on restart. Job orchestration is disabled here. To add it, supply a `JobOrchestrator` and configure `firefly.data.orchestration`; its observability and persistence settings live under that same namespace.

## Continue the application

Use the [Client guide](client.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-data/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [banner.txt](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/resources/banner.txt).
- [logback-spring.xml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/resources/logback-spring.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
