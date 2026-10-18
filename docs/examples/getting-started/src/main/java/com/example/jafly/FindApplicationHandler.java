package com.example.jafly;

import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

@QueryHandlerComponent(cacheable = false)
public class FindApplicationHandler extends QueryHandler<FindApplication, OnboardingApplication> {
    private final InMemoryApplicationRepository repository;

    public FindApplicationHandler(InMemoryApplicationRepository repository) {
        this.repository = repository;
    }

    @Override
    protected Mono<OnboardingApplication> doHandle(FindApplication query) {
        return repository.findById(query.applicationId());
    }
}
