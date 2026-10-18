package com.example.onboarding;

import org.fireflyframework.orchestration.core.model.ExecutionStatus;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.fireflyframework.orchestration.tcc.engine.TccEngine;
import org.fireflyframework.orchestration.tcc.engine.TccInputs;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OnboardingCoordinationTest {
    @Autowired SagaEngine sagas;
    @Autowired TccEngine transactions;
    @Autowired WorkflowEngine workflows;
    @Autowired InMemoryOnboardingProviders providers;

    @Test
    void sagaKeepsAnEligibleApplication() {
        var application = applicant();
        StepVerifier.create(sagas.execute("submit-onboarding", StepInputs.of("record", application)))
                .assertNext(result -> assertThat(result.isSuccess()).isTrue()).verifyComplete();
        assertThat(providers.application(application.id())).contains(application);
        assertThat(providers.operations(application.id())).containsExactly("record", "eligibility");
    }

    @Test
    void sagaRemovesTheRecordWhenEligibilityFails() {
        var application = applicant();
        providers.rejectEligibility(application.id());
        StepVerifier.create(sagas.execute("submit-onboarding", StepInputs.of("record", application)))
                .assertNext(result -> assertThat(result.isSuccess()).isFalse()).verifyComplete();
        assertThat(providers.application(application.id())).isEmpty();
        assertThat(providers.operations(application.id()))
                .containsExactly("record", "eligibility", "remove-record");
    }

    @Test
    void tccReservesEverythingBeforeConfirming() {
        var id = UUID.randomUUID();
        StepVerifier.create(transactions.execute("reserve-onboarding", reservationInputs(id)))
                .assertNext(result -> assertThat(result.isConfirmed()).isTrue()).verifyComplete();
        assertThat(providers.reservation(id, "reviewer")).contains("CONFIRMED");
        assertThat(providers.reservation(id, "documents")).contains("CONFIRMED");
        assertThat(providers.operations(id)).containsExactly(
                "try:reviewer", "try:documents", "confirm:reviewer", "confirm:documents");
    }

    @Test
    void tccCancelsTheReviewerReservationWhenDocumentReservationFails() {
        var id = UUID.randomUUID();
        providers.rejectDocuments(id);
        StepVerifier.create(transactions.execute("reserve-onboarding", reservationInputs(id)))
                .assertNext(result -> {
                    assertThat(result.isCanceled()).isTrue();
                    assertThat(result.error()).isPresent();
                }).verifyComplete();
        assertThat(providers.reservation(id, "reviewer")).isEmpty();
        assertThat(providers.reservation(id, "documents")).isEmpty();
        assertThat(providers.operations(id)).containsExactly(
                "try:reviewer", "try:documents", "cancel:reviewer");
    }

    @Test
    void workflowRespectsDependenciesAndRecordsADecision() {
        var application = applicant();
        StepVerifier.create(workflows.startWorkflow("review-onboarding", Map.of("application", application)))
                .assertNext(state -> {
                    assertThat(state.status()).isEqualTo(ExecutionStatus.COMPLETED);
                    assertThat(state.stepResults()).containsKeys("validate", "documents", "decision");
                }).verifyComplete();
        assertThat(providers.decision(application.id())).contains("READY_FOR_REVIEW");
        assertThat(providers.operations(application.id())).containsExactly("validate", "documents", "decision");
    }

    @Test
    void failedWorkflowStaysFailedAndANewExecutionCanSucceedAfterProviderRecovery() {
        var application = applicant();
        providers.rejectDocuments(application.id());
        var failed = workflows.startWorkflow("review-onboarding", Map.of("application", application)).block();
        assertThat(failed.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failed.stepResults()).doesNotContainKey("decision");
        assertThat(providers.decision(application.id())).isEmpty();

        providers.acceptDocuments(application.id());
        StepVerifier.create(workflows.startWorkflow("review-onboarding", Map.of("application", application)))
                .assertNext(recovered -> {
                    assertThat(recovered.status()).isEqualTo(ExecutionStatus.COMPLETED);
                    assertThat(recovered.correlationId()).isNotEqualTo(failed.correlationId());
                }).verifyComplete();
        StepVerifier.create(workflows.findByCorrelationId(failed.correlationId()))
                .assertNext(saved -> assertThat(saved.orElseThrow().status()).isEqualTo(ExecutionStatus.FAILED))
                .verifyComplete();
        assertThat(providers.decision(application.id())).contains("READY_FOR_REVIEW");
        assertThat(providers.operations(application.id()))
                .containsExactly("validate", "documents", "validate", "documents", "decision");
    }

    private TccInputs reservationInputs(UUID id) {
        return TccInputs.builder().forParticipant("reviewer", id).forParticipant("documents", id).build();
    }

    private OnboardingApplication applicant() {
        return new OnboardingApplication(UUID.randomUUID(), "Alex Rivera", "alex@example.test");
    }
}
