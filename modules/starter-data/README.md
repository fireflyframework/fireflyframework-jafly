![Jafly. Starter Data](../../docs/site/assets/modules/starter-data.svg)

# Starter Data

Build data enrichment, job orchestration, quality, lineage, and provider operation services.

Choose the data-job or enrichment extension points that match your use case. Configure provider access and orchestration separately; review caching, timeouts, batch behavior, and persistence for each provider workflow.

[Module guide](../../docs/site/modules/starter-data.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Background document enrichment or verification jobs may need the data-job and provider extension points. Implement the chosen provider operation and configure its limits; these jobs are not part of the runnable submission tutorial.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-data</artifactId>
    <version>26.10.17-SNAPSHOT</version>
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

## Configuration

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

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`DataEnrichmentProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/DataEnrichmentProperties.java) | Configuration properties for data enrichment. |
| [`JobOrchestrationProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/config/JobOrchestrationProperties.java) | Configuration properties for job orchestration in fireflyframework-starter-data. |
| [`DataEnricher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/service/DataEnricher.java) | Type-safe abstract base class for data enrichers for enrichment workflows. |
| [`JobOrchestrator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/orchestration/port/JobOrchestrator.java) | Port interface for job orchestrators (e.g., AWS Step Functions, Azure Durable Functions, etc.). |
| [`AbstractDataJobController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-data/src/main/java/org/fireflyframework/data/controller/AbstractDataJobController.java) | Abstract base controller implementation that provides logging for all job lifecycle phases. |

## Continue the application

Use the [Client guide](../../docs/site/modules/client.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-data -am verify
./mvnw -pl :fireflyframework-starter-data -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/starter-data.md).
