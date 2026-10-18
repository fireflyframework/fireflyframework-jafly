package com.example.onboarding;

import lombok.RequiredArgsConstructor;
import org.fireflyframework.orchestration.core.argument.FromStep;
import org.fireflyframework.orchestration.core.argument.Input;
import org.fireflyframework.orchestration.workflow.annotation.Workflow;
import org.fireflyframework.orchestration.workflow.annotation.WorkflowStep;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Workflow(id = "review-onboarding", maxRetries = 0)
@RequiredArgsConstructor
public class ReviewOnboardingWorkflow {
    private final OnboardingProviders providers;

    @WorkflowStep(id = "validate")
    public Mono<OnboardingApplication> validate(@Input("application") OnboardingApplication application) {
        return providers.validate(application);
    }

    @WorkflowStep(id = "documents", dependsOn = "validate")
    public Mono<OnboardingApplication> documents(@FromStep("validate") OnboardingApplication application) {
        return providers.checkDocuments(application);
    }

    @WorkflowStep(id = "decision", dependsOn = "documents")
    public Mono<String> decision(@FromStep("documents") OnboardingApplication application) {
        return providers.recordDecision(application);
    }
}
