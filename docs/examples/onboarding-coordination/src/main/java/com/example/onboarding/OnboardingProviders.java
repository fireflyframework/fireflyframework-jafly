package com.example.onboarding;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OnboardingProviders {
    Mono<OnboardingApplication> record(OnboardingApplication application);
    Mono<Void> removeRecord(OnboardingApplication application);
    Mono<OnboardingApplication> checkEligibility(OnboardingApplication application);
    Mono<ResourceReservation> reserve(UUID applicationId, String resource);
    Mono<Void> confirm(ResourceReservation reservation);
    Mono<Void> cancel(ResourceReservation reservation);
    Mono<OnboardingApplication> validate(OnboardingApplication application);
    Mono<OnboardingApplication> checkDocuments(OnboardingApplication application);
    Mono<String> recordDecision(OnboardingApplication application);
}
