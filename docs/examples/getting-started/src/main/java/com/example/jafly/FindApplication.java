package com.example.jafly;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.fireflyframework.cqrs.query.Query;

public record FindApplication(String queryId, @NotNull UUID applicationId) implements Query<OnboardingApplication> {
    public FindApplication(UUID applicationId) {
        this(UUID.randomUUID().toString(), applicationId);
    }

    @Override
    public String getQueryId() {
        return queryId;
    }

    @Override
    public Class<OnboardingApplication> getResultType() {
        return OnboardingApplication.class;
    }

    @Override
    public boolean isCacheable() {
        return false;
    }
}
