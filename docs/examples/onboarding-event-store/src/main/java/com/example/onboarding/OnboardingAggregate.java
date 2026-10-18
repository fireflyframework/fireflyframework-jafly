package com.example.onboarding;

import java.util.UUID;
import lombok.Getter;
import org.fireflyframework.eventsourcing.aggregate.AggregateRoot;

@Getter
public class OnboardingAggregate extends AggregateRoot {
    public static final String TYPE = "CustomerOnboarding";

    public enum Status { DRAFT, SUBMITTED, APPROVED }

    private Status status = Status.DRAFT;
    private String applicantName;
    private String email;
    private String reviewer;

    public OnboardingAggregate(UUID id) {
        super(id, TYPE);
    }

    public void submit(String applicantName, String email) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only a draft application can be submitted");
        }
        requireText(applicantName, "Applicant name");
        requireText(email, "Email");
        applyChange(new ApplicationSubmitted(getId(), applicantName, email));
    }

    public void approve(String reviewer) {
        if (status != Status.SUBMITTED) {
            throw new IllegalStateException("Only a submitted application can be approved");
        }
        requireText(reviewer, "Reviewer");
        applyChange(new ApplicationApproved(getId(), reviewer));
    }

    private void on(ApplicationSubmitted event) {
        applicantName = event.applicantName();
        email = event.email();
        status = Status.SUBMITTED;
    }

    private void on(ApplicationApproved event) {
        reviewer = event.reviewer();
        status = Status.APPROVED;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
