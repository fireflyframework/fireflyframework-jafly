# Publish an application event

Add an event when a separate process needs to react to an accepted application. Decide what happens if the application is saved but publication fails before attaching this work to a handler.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

`EventPublisherFactory` selects a configured publisher. `APPLICATION_EVENT` stays within one process; Kafka, RabbitMQ, and PostgreSQL need their respective adapter and connection configuration. `EventEnvelope` is a record, so its payload accessor is `payload()`.

### Publish within one process

This API class needs Spring/EDA wiring and a caller that composes or subscribes to the returned publisher. It does not save an application or make the save and event atomic.

```java
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ApplicationEventPublisher {
    private final EventPublisherFactory publishers;



    public Mono<Void> submitted(UUID applicationId) {
        return publishers.getPublisher(PublisherType.APPLICATION_EVENT)
                .publish(applicationId, "application-submitted");
    }
}
```

## Configuration and API

- [Component guide](../../../docs/site/modules/eda.md) for dependency selection, wiring, configuration, and public contracts.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../../docs/site/maven.md#constructor-injection).
