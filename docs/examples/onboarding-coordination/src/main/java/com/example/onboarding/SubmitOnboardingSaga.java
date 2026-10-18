package com.example.onboarding;

import lombok.RequiredArgsConstructor;
import org.fireflyframework.orchestration.core.argument.FromStep;
import org.fireflyframework.orchestration.core.argument.Input;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Saga(name = "submit-onboarding")
@RequiredArgsConstructor
public class SubmitOnboardingSaga {
    private final OnboardingProviders providers;

    @SagaStep(id = "record", compensate = "removeRecord")
    public Mono<OnboardingApplication> record(@Input OnboardingApplication application) {
        return providers.record(application);
    }

    @SagaStep(id = "eligibility", dependsOn = "record")
    public Mono<OnboardingApplication> eligibility(@FromStep("record") OnboardingApplication application) {
        return providers.checkEligibility(application);
    }

    public Mono<Void> removeRecord(@FromStep("record") OnboardingApplication application) {
        return providers.removeRecord(application);
    }
}
