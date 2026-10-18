package com.example.onboarding;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryOnboardingProviders implements OnboardingProviders {
    private final Map<UUID, OnboardingApplication> applications = new ConcurrentHashMap<>();
    private final Map<ResourceReservation, String> reservations = new ConcurrentHashMap<>();
    private final Map<UUID, String> decisions = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> operations = new ConcurrentHashMap<>();
    private final Set<UUID> rejectedEligibility = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rejectedDocuments = ConcurrentHashMap.newKeySet();

    public Mono<OnboardingApplication> record(OnboardingApplication application) {
        return Mono.fromSupplier(() -> {
            observe(application.id(), "record");
            applications.put(application.id(), application);
            return application;
        });
    }

    public Mono<Void> removeRecord(OnboardingApplication application) {
        return Mono.fromRunnable(() -> {
            observe(application.id(), "remove-record");
            applications.remove(application.id());
        });
    }

    public Mono<OnboardingApplication> checkEligibility(OnboardingApplication application) {
        return Mono.fromSupplier(() -> {
            observe(application.id(), "eligibility");
            if (rejectedEligibility.contains(application.id())) {
                throw new IllegalStateException("Applicant is ineligible");
            }
            return application;
        });
    }

    public Mono<ResourceReservation> reserve(UUID applicationId, String resource) {
        return Mono.fromSupplier(() -> {
            observe(applicationId, "try:" + resource);
            if (resource.equals("documents") && rejectedDocuments.contains(applicationId)) {
                throw new IllegalStateException("Document check capacity unavailable");
            }
            var reservation = new ResourceReservation(applicationId, resource);
            reservations.put(reservation, "RESERVED");
            return reservation;
        });
    }

    public Mono<Void> confirm(ResourceReservation reservation) {
        return Mono.fromRunnable(() -> {
            observe(reservation.applicationId(), "confirm:" + reservation.resource());
            if (!reservations.replace(reservation, "RESERVED", "CONFIRMED")) {
                throw new IllegalStateException("Reservation is not held");
            }
        });
    }

    public Mono<Void> cancel(ResourceReservation reservation) {
        return Mono.fromRunnable(() -> {
            observe(reservation.applicationId(), "cancel:" + reservation.resource());
            reservations.remove(reservation);
        });
    }

    public Mono<OnboardingApplication> validate(OnboardingApplication application) {
        return Mono.fromSupplier(() -> {
            observe(application.id(), "validate");
            if (application.applicantName() == null || application.applicantName().isBlank()) {
                throw new IllegalArgumentException("Applicant name is required");
            }
            return application;
        });
    }

    public Mono<OnboardingApplication> checkDocuments(OnboardingApplication application) {
        return Mono.fromSupplier(() -> {
            observe(application.id(), "documents");
            if (rejectedDocuments.contains(application.id())) {
                throw new IllegalStateException("Document provider unavailable");
            }
            return application;
        });
    }

    public Mono<String> recordDecision(OnboardingApplication application) {
        return Mono.fromSupplier(() -> {
            observe(application.id(), "decision");
            decisions.put(application.id(), "READY_FOR_REVIEW");
            return "READY_FOR_REVIEW";
        });
    }

    public void rejectEligibility(UUID id) { rejectedEligibility.add(id); }
    public void rejectDocuments(UUID id) { rejectedDocuments.add(id); }
    public void acceptDocuments(UUID id) { rejectedDocuments.remove(id); }
    public Optional<OnboardingApplication> application(UUID id) { return Optional.ofNullable(applications.get(id)); }
    public Optional<String> reservation(UUID id, String resource) {
        return Optional.ofNullable(reservations.get(new ResourceReservation(id, resource)));
    }
    public Optional<String> decision(UUID id) { return Optional.ofNullable(decisions.get(id)); }
    public List<String> operations(UUID id) { return List.copyOf(operations.getOrDefault(id, List.of())); }

    private void observe(UUID id, String operation) {
        operations.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>()).add(operation);
    }
}
