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
