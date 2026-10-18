package com.example.onboarding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.eventsourcing.annotation.DomainEvent;
import org.fireflyframework.eventsourcing.domain.Event;

@DomainEvent(value = "onboarding.application.approved", publishable = false)
@JsonTypeName("onboarding.application.approved")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationApproved(UUID aggregateId, String reviewer, Instant occurredAt) implements Event {
    public ApplicationApproved(UUID aggregateId, String reviewer) {
        this(aggregateId, reviewer, Instant.now());
    }

    @Override public UUID getAggregateId() { return aggregateId; }
    @Override public Instant getEventTimestamp() { return occurredAt; }
}
