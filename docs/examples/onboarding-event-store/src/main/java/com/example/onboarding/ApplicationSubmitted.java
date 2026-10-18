package com.example.onboarding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.eventsourcing.annotation.DomainEvent;
import org.fireflyframework.eventsourcing.domain.Event;

@DomainEvent(value = "onboarding.application.submitted", publishable = false)
@JsonTypeName("onboarding.application.submitted")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationSubmitted(UUID aggregateId, String applicantName, String email,
                                   Instant occurredAt) implements Event {
    public ApplicationSubmitted(UUID aggregateId, String applicantName, String email) {
        this(aggregateId, applicantName, email, Instant.now());
    }

    @Override public UUID getAggregateId() { return aggregateId; }
    @Override public Instant getEventTimestamp() { return occurredAt; }
}
