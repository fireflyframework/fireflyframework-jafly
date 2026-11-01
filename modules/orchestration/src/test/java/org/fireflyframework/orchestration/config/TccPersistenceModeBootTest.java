/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.orchestration.config;

import org.fireflyframework.orchestration.core.argument.ArgumentResolver;
import org.fireflyframework.orchestration.core.event.NoOpEventPublisherImpl;
import org.fireflyframework.orchestration.core.exception.TccPersistenceException;
import org.fireflyframework.orchestration.core.model.ExecutionStatus;
import org.fireflyframework.orchestration.core.observability.OrchestrationEvents;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import org.fireflyframework.orchestration.core.persistence.InMemoryPersistenceProviderImpl;
import org.fireflyframework.orchestration.core.step.StepInvoker;
import org.fireflyframework.orchestration.tcc.builder.TccBuilder;
import org.fireflyframework.orchestration.tcc.engine.TccEngine;
import org.fireflyframework.orchestration.tcc.engine.TccExecutionOrchestrator;
import org.fireflyframework.orchestration.tcc.engine.TccInputs;
import org.fireflyframework.orchestration.tcc.engine.TccPersistenceMode;
import org.fireflyframework.orchestration.tcc.registry.TccDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class TccPersistenceModeBootTest {
    @Test
    void configuredRequiredModeRetainsAcknowledgedCheckpointsAndFinalReport() {
        var provider = new RetainingProvider(false);
        try (var context = application(provider).properties(
                "firefly.orchestration.tcc.persistence-mode=required").run()) {
            assertThat(context.getBean(OrchestrationProperties.class).getTcc().getPersistenceMode())
                    .isEqualTo(TccPersistenceMode.REQUIRED);
            assertThat(context.getBean(TccExecutionOrchestrator.class).getPersistenceMode())
                    .isEqualTo(TccPersistenceMode.REQUIRED);
            var counts = new Counts();
            StepVerifier.create(context.getBean(TccEngine.class).execute(definition(counts), TccInputs.empty()))
                    .assertNext(result -> {
                        assertThat(result.isConfirmed()).isTrue();
                        assertThat(provider.accepted).extracting(ExecutionState::status).containsExactly(
                                ExecutionStatus.TRYING, ExecutionStatus.CONFIRMING,
                                ExecutionStatus.CONFIRMED, ExecutionStatus.CONFIRMED);
                        assertThat(provider.accepted).allMatch(state ->
                                state.correlationId().equals(result.correlationId()));
                        assertThat(provider.accepted.get(1).stepResults()).containsEntry("reservation", "reserved");
                        assertThat(provider.accepted.get(2).report()).isEmpty();
                        assertThat(provider.findById(result.correlationId()).block().orElseThrow().report())
                                .isPresent();
                    }).verifyComplete();
            assertThat(counts.tries).hasValue(1);
            assertThat(counts.confirms).hasValue(1);
            assertThat(counts.cancels).hasValue(0);
        }
    }

    @Test
    void configuredRequiredFinalFailureLeavesLastAcknowledgedSnapshotAndSignalsUncertainty() {
        var provider = new RetainingProvider(true);
        try (var context = application(provider).properties(
                "firefly.orchestration.tcc.persistence-mode=REQUIRED").run()) {
            var counts = new Counts();
            StepVerifier.create(context.getBean(TccEngine.class).execute(definition(counts), TccInputs.empty()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(TccPersistenceException.class);
                        var failure = (TccPersistenceException) error;
                        assertThat(failure.getStage()).isEqualTo(TccPersistenceException.Stage.FINAL);
                        assertThat(failure.hasPossibleParticipantEffects()).isTrue();
                        assertThat(failure.getCause()).isSameAs(provider.cause);
                        var retained = provider.findById(failure.getCorrelationId()).block().orElseThrow();
                        assertThat(retained.status()).isEqualTo(ExecutionStatus.CONFIRMED);
                        assertThat(retained.report()).isEmpty();
                        assertThat(retained.stepResults()).containsEntry("reservation", "reserved");
                        assertThat(provider.accepted).hasSize(3);
                        assertThat(provider.attempted).hasSize(4);
                    }).verify();
            assertThat(counts.tries).hasValue(1);
            assertThat(counts.confirms).hasValue(1);
            assertThat(counts.cancels).hasValue(0);
        }
    }

    @Test
    void defaultBootPropertyUsesBestEffortAndCanReturnConfirmedWithoutFinalAcknowledgment() {
        var provider = new RetainingProvider(true);
        try (var context = application(provider).run()) {
            assertThat(context.getBean(OrchestrationProperties.class).getTcc().getPersistenceMode())
                    .isEqualTo(TccPersistenceMode.BEST_EFFORT);
            StepVerifier.create(context.getBean(TccEngine.class).execute(definition(new Counts()), TccInputs.empty()))
                    .assertNext(result -> assertThat(result.isConfirmed()).isTrue()).verifyComplete();
            assertThat(provider.attempted).hasSize(4);
            assertThat(provider.accepted).hasSize(3);
        }
    }

    @Test
    void requiredBootPropertyRejectsCustomBestEffortOrchestrator() {
        var provider = new RetainingProvider(false);
        var custom = new TccExecutionOrchestrator(new StepInvoker(new ArgumentResolver()),
                new OrchestrationEvents() {}, new NoOpEventPublisherImpl(), provider);
        assertThatThrownBy(() -> application(provider).initializers(context ->
                        context.getBeanFactory().registerSingleton("customTccOrchestrator", custom))
                .properties("firefly.orchestration.tcc.persistence-mode=REQUIRED").run())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("persistence modes must match");
        assertThat(provider.attempted).isEmpty();
    }

    @Test
    void invalidBootPersistenceModeFailsInsteadOfFallingBack() {
        assertThatThrownBy(() -> application(new RetainingProvider(false)).properties(
                "firefly.orchestration.tcc.persistence-mode=UNRECOGNIZED").run())
                .hasStackTraceContaining("UNRECOGNIZED");
    }

    private SpringApplicationBuilder application(RetainingProvider provider) {
        return new SpringApplicationBuilder(OrchestrationAutoConfiguration.class, TccAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(context -> context.getBeanFactory().registerSingleton("retainedTccProvider", provider))
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR",
                        "firefly.orchestration.tcc.composition.enabled=false");
    }

    private TccDefinition definition(Counts counts) {
        return TccBuilder.tccNoRetry("boot-required-persistence").participant("reservation")
                .tryHandler((input, ctx) -> { counts.tries.incrementAndGet(); return Mono.just("reserved"); })
                .confirmHandler((input, ctx) -> { counts.confirms.incrementAndGet(); return Mono.just("confirmed"); })
                .cancelHandler((input, ctx) -> { counts.cancels.incrementAndGet(); return Mono.just("canceled"); })
                .add().build();
    }

    static class Counts {
        final AtomicInteger tries = new AtomicInteger();
        final AtomicInteger confirms = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
    }

    static class RetainingProvider extends InMemoryPersistenceProviderImpl {
        final List<ExecutionState> attempted = new ArrayList<>();
        final List<ExecutionState> accepted = new ArrayList<>();
        final IllegalStateException cause = new IllegalStateException("private-provider-detail");
        private final boolean failFinal;
        RetainingProvider(boolean failFinal) { this.failFinal = failFinal; }
        @Override public Mono<Void> save(ExecutionState state) {
            attempted.add(state);
            if (failFinal && state.report().isPresent()) return Mono.error(cause);
            return super.save(state).doOnSuccess(ignored -> accepted.add(state));
        }
    }
}
