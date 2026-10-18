package com.example.onboarding;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.eventsourcing.store.EventStore;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OnboardingEventService {
    private final EventStore eventStore;

    public Mono<OnboardingAggregate> save(OnboardingAggregate application) {
        return Mono.defer(() -> {
            var pending = List.copyOf(application.getUncommittedEvents());
            if (pending.isEmpty()) {
                return Mono.just(application);
            }
            long expectedVersion = application.getCurrentVersion() - pending.size();
            return eventStore.appendEvents(application.getId(), OnboardingAggregate.TYPE,
                            pending, expectedVersion)
                    .map(stream -> {
                        application.markEventsAsCommitted();
                        return application;
                    });
        });
    }

    public Mono<OnboardingAggregate> load(UUID id) {
        return eventStore.loadEventStream(id, OnboardingAggregate.TYPE)
                .flatMap(stream -> {
                    if (stream.isEmpty()) {
                        return Mono.empty();
                    }
                    var application = new OnboardingAggregate(id);
                    application.loadFromHistory(stream.getEvents());
                    return Mono.just(application);
                });
    }
}
