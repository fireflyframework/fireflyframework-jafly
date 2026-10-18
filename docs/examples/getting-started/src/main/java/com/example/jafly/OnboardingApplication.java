package com.example.jafly;

import java.util.UUID;

public record OnboardingApplication(UUID id, String applicantName, String email) {
}
