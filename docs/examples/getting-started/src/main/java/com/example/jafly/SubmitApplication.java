package com.example.jafly;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.fireflyframework.cqrs.command.Command;

public record SubmitApplication(String commandId, @NotBlank String applicantName,
                                @NotBlank @Email String email) implements Command<OnboardingApplication> {
    public SubmitApplication(String applicantName, String email) {
        this(UUID.randomUUID().toString(), applicantName, email);
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    @Override
    public Class<OnboardingApplication> getResultType() {
        return OnboardingApplication.class;
    }
}
