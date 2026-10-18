package com.example.jafly;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class InMemoryApplicationRepository {
    // Tutorial data belongs to this process and is lost when the application restarts.
    private final Map<UUID, OnboardingApplication> applications = new ConcurrentHashMap<>();

    public Mono<OnboardingApplication> save(OnboardingApplication application) {
        return Mono.fromSupplier(() -> {
            applications.put(application.id(), application);
            return application;
        });
    }

    public Mono<OnboardingApplication> findById(UUID id) {
        return Mono.defer(() -> Mono.justOrEmpty(applications.get(id)));
    }
}
