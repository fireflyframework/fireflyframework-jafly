# Submit and read an onboarding application

Start with the tested onboarding service: `POST /applications` dispatches `SubmitApplication` through `CommandBus`; `GET /applications/{id}` dispatches `FindApplication` through `QueryBus`.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

The service owns an in-memory repository, returns `400` for invalid input and `404` for a missing ID, and loses data on restart. Its query is uncached. It does not authenticate applicants, publish events, or make duplicate submissions idempotent. Protected operations need a trusted identity context and an authorization rule; accepting an Authorization header is not token validation.

### SubmitApplication

This class is part of the complete tutorial project.

```java
package com.example.jafly;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.fireflyframework.cqrs.command.Command;

public record SubmitApplication(String commandId, @NotBlank String applicantName,
                                @NotBlank @Email String email) implements Command<OnboardingApplication> {
    public SubmitApplication(String applicantName, String email) {
        this(UUID.randomUUID().toString(), applicantName, email);
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    @Override
    public Class<OnboardingApplication> getResultType() {
        return OnboardingApplication.class;
    }
}
```

### SubmitApplicationHandler

This class is part of the complete tutorial project.

```java
package com.example.jafly;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

@CommandHandlerComponent
@RequiredArgsConstructor
public class SubmitApplicationHandler extends CommandHandler<SubmitApplication, OnboardingApplication> {
    private final InMemoryApplicationRepository repository;

    @Override
    protected Mono<OnboardingApplication> doHandle(SubmitApplication command) {
        var application = new OnboardingApplication(UUID.randomUUID(), command.applicantName(), command.email());
        return repository.save(application);
    }
}
```

## Configuration and API

- [Component guide](../../../docs/site/modules/cqrs.md) for dependency selection, wiring, configuration, and public contracts.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.
