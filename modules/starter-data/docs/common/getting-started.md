# Add verification jobs or enrichment

An extended onboarding service may run a background document check or fetch a provider result. Choose a data job when the application needs a staged lifecycle; choose an enricher when it needs a provider request and a mapped response.

## Start from the application

[The onboarding tutorial](../../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

Implement the provider operation or `JobOrchestrator` required by the chosen path. The starter does not supply a working Airflow or Step Functions deployment merely because a property names one. `DataConfiguration` separates job orchestration under `firefly.data.orchestration` from the optional orchestration engine under `firefly.data.orchestration-engine`; use different keys for these settings.

### Bind an application job service

This controller is an API class, not a complete provider integration. Supply a `DataJobService` bean implementing the lifecycle operations and its infrastructure. The controller and service contracts have no generic type parameters.

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

## Configuration and API

- [Component guide](../../../../docs/site/modules/starter-data.md) for dependency selection, wiring, configuration, and public contracts.
- [Source and tests](../..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.
