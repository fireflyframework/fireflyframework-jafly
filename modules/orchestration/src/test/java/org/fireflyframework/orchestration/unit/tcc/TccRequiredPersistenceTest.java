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

package org.fireflyframework.orchestration.unit.tcc;

import org.fireflyframework.orchestration.core.argument.ArgumentResolver;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.core.event.NoOpEventPublisher;
import org.fireflyframework.orchestration.core.event.OrchestrationEvent;
import org.fireflyframework.orchestration.core.event.OrchestrationEventPublisher;
import org.fireflyframework.orchestration.core.exception.TccPersistenceException;
import org.fireflyframework.orchestration.core.model.ExecutionPattern;
import org.fireflyframework.orchestration.core.model.ExecutionStatus;
import org.fireflyframework.orchestration.core.observability.OrchestrationEvents;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import org.fireflyframework.orchestration.core.persistence.InMemoryPersistenceProvider;
import org.fireflyframework.orchestration.core.step.StepInvoker;
import org.fireflyframework.orchestration.tcc.annotation.OnTccComplete;
import org.fireflyframework.orchestration.tcc.annotation.OnTccError;
import org.fireflyframework.orchestration.tcc.builder.TccBuilder;
import org.fireflyframework.orchestration.tcc.engine.*;
import org.fireflyframework.orchestration.tcc.registry.TccDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class TccRequiredPersistenceTest {
    private final StepInvoker invoker = new StepInvoker(new ArgumentResolver());
    private final List<OrchestrationEvent> published = new CopyOnWriteArrayList<>();
    // Deliberately eager: even construction of publish must wait for acknowledgment.
    private final OrchestrationEventPublisher publisher = event -> { published.add(event); return Mono.empty(); };
    private final AtomicInteger completed = new AtomicInteger();
    private final OrchestrationEvents events = new OrchestrationEvents() {
        @Override public void onCompleted(String name, String correlationId, ExecutionPattern pattern,
                                          boolean success, long duration) { completed.incrementAndGet(); }
    };

    @ParameterizedTest
    @CsvSource({"1,false,0,0,INITIAL,TRYING", "1,true,0,0,INITIAL,TRYING",
            "2,false,1,0,CHECKPOINT,CONFIRMING", "2,true,1,0,CHECKPOINT,CONFIRMING",
            "3,false,1,1,CHECKPOINT,CONFIRMED", "3,true,1,1,CHECKPOINT,CONFIRMED",
            "4,false,1,1,FINAL,CONFIRMED", "4,true,1,1,FINAL,CONFIRMED"})
    void requiredFailureStopsAtTheFailedAcknowledgment(int failAt, boolean synchronous,
            int tries, int confirms, TccPersistenceException.Stage stage, ExecutionStatus status) throws Exception {
        var provider = new FailingProvider(failAt, synchronous);
        var callbacks = new Callbacks();
        var counts = new Counts();
        var definition = definition(counts, callbacks, false, false);
        var context = ExecutionContext.forTcc("required-correlation", definition.name);
        context.putVariable("recoveryMarker", "retained");
        var execution = engine(provider, TccPersistenceMode.REQUIRED).execute(definition, TccInputs.empty(), context);
        assertThat(provider.attempted).isEmpty();
        assertThat(counts.tries).hasValue(0);
        assertThat(published).isEmpty();

        StepVerifier.create(execution).expectErrorSatisfies(error -> {
            assertThat(error).isInstanceOf(TccPersistenceException.class);
            var failure = (TccPersistenceException) error;
            assertThat(failure.getCause()).isSameAs(provider.cause);
            assertThat(failure.getStage()).isEqualTo(stage);
            assertThat(failure.getAttemptedStatus()).isEqualTo(status);
            assertThat(failure.getCorrelationId()).isEqualTo(context.getCorrelationId());
            assertThat(failure.hasPossibleParticipantEffects()).isEqualTo(failAt != 1);
            assertThat(failure.getExecutionContext()).isSameAs(context);
            assertThat(failure.getExecutionContext().getVariable("recoveryMarker")).isEqualTo("retained");
            assertThat(failure.getMessage()).doesNotContain("private-provider-detail");
            assertThat(failure.getErrorCode()).isEqualTo("ORCHESTRATION_TCC_PERSISTENCE_ERROR");
        }).verify(Duration.ofSeconds(5));

        assertThat(counts.tries).hasValue(tries);
        assertThat(counts.confirms).hasValue(confirms);
        assertThat(counts.cancels).hasValue(0);
        assertThat(provider.attempted).hasSize(failAt);
        assertThat(provider.accepted).hasSize(failAt - 1);
        assertThat(provider.attempted).noneMatch(state -> state.status() == ExecutionStatus.FAILED);
        assertThat(completed).hasValue(0);
        assertThat(published).noneMatch(event -> event.eventType().equals("EXECUTION_COMPLETED"));
        assertThat(callbacks.syncComplete).hasValue(0);
        assertThat(callbacks.error).hasValue(0);
        assertThat(callbacks.asyncComplete.await(100, TimeUnit.MILLISECONDS)).isFalse();
        if (tries > 0) assertThat(context.getTryResult("reservation")).isEqualTo("reserved");
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void cancelCheckpointFailureDoesNotBeginCompensation(boolean synchronous) throws Exception {
        var provider = new FailingProvider(2, synchronous);
        var counts = new Counts();
        var callbacks = new Callbacks();
        var tcc = definition(counts, callbacks, true, false);
        StepVerifier.create(engine(provider, TccPersistenceMode.REQUIRED).execute(tcc, TccInputs.empty()))
                .expectErrorSatisfies(error -> {
                    var failure = (TccPersistenceException) error;
                    assertThat(failure.getAttemptedStatus()).isEqualTo(ExecutionStatus.CANCELING);
                    assertThat(failure.getStage()).isEqualTo(TccPersistenceException.Stage.CHECKPOINT);
                    assertThat(failure.hasPossibleParticipantEffects()).isTrue();
                    assertThat(failure.getExecutionContext().getTryResult("reservation")).isEqualTo("reserved");
                }).verify();
        assertThat(counts.tries).hasValue(2);
        assertThat(counts.confirms).hasValue(0);
        assertThat(counts.cancels).hasValue(0);
        assertThat(provider.attempted).extracting(ExecutionState::status)
                .containsExactly(ExecutionStatus.TRYING, ExecutionStatus.CANCELING);
        assertThat(callbacks.error).hasValue(0);
        assertThat(completed).hasValue(0);
    }

    @Test
    void successObserversAndBothKindsOfCompleteCallbackWaitForFinalAcknowledgment() throws Exception {
        var finalAck = Sinks.<Void>empty();
        var provider = new FailingProvider(0, false) {
            @Override public Mono<Void> save(ExecutionState state) {
                if (state.report().isPresent()) {
                    attempted.add(state);
                    return finalAck.asMono().then(Mono.defer(() -> super.accept(state)));
                }
                return super.save(state);
            }
        };
        var callbacks = new Callbacks();
        var counts = new Counts();
        var result = new AtomicReference<TccResult>();
        var failure = new AtomicReference<Throwable>();
        var subscription = engine(provider, TccPersistenceMode.REQUIRED)
                .execute(definition(counts, callbacks, false, false), TccInputs.empty())
                .subscribe(result::set, failure::set);
        try {
            assertThat(counts.confirms).hasValue(1);
            assertThat(provider.accepted).hasSize(3);
            assertThat(result.get()).isNull();
            assertThat(completed).hasValue(0);
            assertThat(published).noneMatch(event -> event.eventType().equals("EXECUTION_COMPLETED"));
            assertThat(callbacks.syncComplete).hasValue(0);
            assertThat(callbacks.asyncComplete.await(100, TimeUnit.MILLISECONDS)).isFalse();
            finalAck.tryEmitEmpty();
            assertThat(callbacks.asyncComplete.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(result.get()).isNotNull().matches(TccResult::isConfirmed);
            assertThat(provider.accepted).hasSize(4);
            assertThat(completed).hasValue(1);
            assertThat(callbacks.syncComplete).hasValue(1);
            assertThat(published).filteredOn(event -> event.eventType().equals("EXECUTION_COMPLETED"))
                    .extracting(OrchestrationEvent::status).containsExactly(ExecutionStatus.CONFIRMED);
        } finally { subscription.dispose(); }
    }

    @Test
    void genuineParticipantFailureStillCompensatesAfterAcknowledgedCancelCheckpoint() {
        var provider = new FailingProvider(0, false);
        var counts = new Counts();
        StepVerifier.create(engine(provider, TccPersistenceMode.REQUIRED)
                        .execute(definition(counts, new Callbacks(), true, false), TccInputs.empty()))
                .assertNext(result -> assertThat(result.isCanceled()).isTrue()).verifyComplete();
        assertThat(counts.tries).hasValue(2);
        assertThat(counts.confirms).hasValue(0);
        assertThat(counts.cancels).hasValue(1);
        assertThat(provider.accepted).extracting(ExecutionState::status)
                .containsExactly(ExecutionStatus.TRYING, ExecutionStatus.CANCELING, ExecutionStatus.CANCELED);
    }

    @Test
    void genuineConfirmFailureStillCompensatesInRequiredMode() {
        var provider = new FailingProvider(0, false);
        var counts = new Counts();
        var tcc = TccBuilder.tccNoRetry("confirm-failure").participant("reservation")
                .tryHandler((input, ctx) -> { counts.tries.incrementAndGet(); return Mono.just("reserved"); })
                .confirmHandler((input, ctx) -> { counts.confirms.incrementAndGet();
                    return Mono.error(new IllegalStateException("participant confirm failure")); })
                .cancelHandler((input, ctx) -> { counts.cancels.incrementAndGet(); return Mono.just("canceled"); })
                .add().build();
        StepVerifier.create(engine(provider, TccPersistenceMode.REQUIRED).execute(tcc, TccInputs.empty()))
                .assertNext(result -> assertThat(result.isCanceled()).isTrue()).verifyComplete();
        assertThat(counts.tries).hasValue(1);
        assertThat(counts.confirms).hasValue(1);
        assertThat(counts.cancels).hasValue(1);
        assertThat(provider.accepted).extracting(ExecutionState::status).containsExactly(
                ExecutionStatus.TRYING, ExecutionStatus.CONFIRMING, ExecutionStatus.CANCELING, ExecutionStatus.CANCELED);
    }

    @Test
    void providerThatWritesBeforeFailingAcknowledgmentStillStopsWithoutCompensation() {
        var provider = new FailingProvider(0, false) {
            @Override public Mono<Void> save(ExecutionState state) {
                var saved = super.save(state);
                return attempted.size() == 3 ? saved.then(Mono.error(cause)) : saved;
            }
        };
        var counts = new Counts();
        StepVerifier.create(engine(provider, TccPersistenceMode.REQUIRED)
                        .execute(definition(counts, new Callbacks(), false, false), TccInputs.empty()))
                .expectErrorSatisfies(error -> {
                    var failure = (TccPersistenceException) error;
                    assertThat(failure.hasPossibleParticipantEffects()).isTrue();
                    assertThat(failure.getCause()).isSameAs(provider.cause);
                    assertThat(provider.findById(failure.getCorrelationId()).block().orElseThrow().status())
                            .isEqualTo(ExecutionStatus.CONFIRMED);
                }).verify();
        assertThat(counts.tries).hasValue(1);
        assertThat(counts.confirms).hasValue(1);
        assertThat(counts.cancels).hasValue(0);
        assertThat(provider.attempted).hasSize(3);
        assertThat(provider.accepted).hasSize(3);
        assertThat(completed).hasValue(0);
    }

    @Test
    void finalFailedSaveDoesNotBecomeSuppressedSuccessOrRunCompleteCallbacks() throws Exception {
        var provider = new FailingProvider(3, false);
        var counts = new Counts();
        var callbacks = new Callbacks();
        StepVerifier.create(engine(provider, TccPersistenceMode.REQUIRED)
                        .execute(definition(counts, callbacks, true, true), TccInputs.empty()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TccPersistenceException.class);
                    assertThat(((TccPersistenceException) error).getStage()).isEqualTo(TccPersistenceException.Stage.FINAL);
                    assertThat(error.getCause()).isSameAs(provider.cause);
                }).verify();
        assertThat(counts.cancels).hasValue(1);
        // This callback handles the genuine cancel failure, not the later persistence failure.
        assertThat(callbacks.error).hasValue(1);
        assertThat(callbacks.syncComplete).hasValue(0);
        assertThat(callbacks.asyncComplete.await(100, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(provider.attempted).hasSize(3);
        assertThat(completed).hasValue(0);
    }

    @ParameterizedTest
    @CsvSource({"false,CANCELED", "true,FAILED"})
    void failedFinalAcknowledgmentAlsoPropagatesForCanceledAndUnsuppressedFailedResults(
            boolean failCancel, ExecutionStatus attemptedStatus) {
        var provider = new FailingProvider(3, false);
        var counts = new Counts();
        var tcc = definition(counts, new Callbacks(), true, failCancel);
        tcc.onTccErrorMethods = List.of();
        StepVerifier.create(engine(provider, TccPersistenceMode.REQUIRED).execute(tcc, TccInputs.empty()))
                .expectErrorSatisfies(error -> {
                    var failure = (TccPersistenceException) error;
                    assertThat(failure.getStage()).isEqualTo(TccPersistenceException.Stage.FINAL);
                    assertThat(failure.getAttemptedStatus()).isEqualTo(attemptedStatus);
                    assertThat(failure.getCause()).isSameAs(provider.cause);
                }).verify();
        assertThat(counts.cancels).hasValue(1);
        assertThat(provider.attempted).hasSize(3);
        assertThat(provider.accepted).hasSize(2);
        assertThat(completed).hasValue(0);
        assertThat(published).noneMatch(event -> event.eventType().equals("EXECUTION_COMPLETED"));
    }

    @Test
    void requiredNeedsProviderAndMatchingManualEngineOrchestratorPolicies() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TccExecutionOrchestrator(invoker,
                events, publisher, null, TccPersistenceMode.REQUIRED));
        var provider = new FailingProvider(0, false);
        var bestEffort = new TccExecutionOrchestrator(invoker, events, publisher, provider);
        assertThatIllegalArgumentException().isThrownBy(() -> new TccEngine(null, events, bestEffort,
                provider, null, publisher, null, TccPersistenceMode.REQUIRED));
        var required = new TccExecutionOrchestrator(invoker, events, publisher, provider, TccPersistenceMode.REQUIRED);
        assertThatIllegalArgumentException().isThrownBy(() -> new TccEngine(null, events, required,
                provider, null, publisher));
        assertThatIllegalArgumentException().isThrownBy(() -> new TccEngine(null, events, required,
                null, null, publisher, null, TccPersistenceMode.REQUIRED));
        assertThatIllegalArgumentException().isThrownBy(() -> new TccEngine(null, events, required,
                new InMemoryPersistenceProvider(), null, publisher, null, TccPersistenceMode.REQUIRED));
    }

    @Test
    void existingConstructorsRemainBestEffortAndPermitAbsentCheckpointProvider() {
        var counts = new Counts();
        var provider = new FailingProvider(1, false);
        var oldOrchestrator = new TccExecutionOrchestrator(invoker, events, new NoOpEventPublisher());
        var oldEngine = new TccEngine(null, events, oldOrchestrator, provider, null, publisher);
        StepVerifier.create(oldEngine.execute(definition(counts, new Callbacks(), false, false), TccInputs.empty()))
                .assertNext(result -> assertThat(result.isConfirmed()).isTrue()).verifyComplete();
        assertThat(counts.tries).hasValue(1);
        assertThat(counts.confirms).hasValue(1);
        assertThat(counts.cancels).hasValue(0);
    }

    private TccEngine engine(FailingProvider provider, TccPersistenceMode mode) {
        return new TccEngine(null, events,
                new TccExecutionOrchestrator(invoker, events, publisher, provider, mode),
                provider, null, publisher, null, mode);
    }

    private TccDefinition definition(Counts counts, Callbacks callbacks, boolean failTry, boolean failCancel)
            throws RuntimeException {
        var builder = TccBuilder.tccNoRetry("required-persistence").participant("reservation")
                .tryHandler((input, ctx) -> { counts.tries.incrementAndGet(); return Mono.just("reserved"); })
                .confirmHandler((input, ctx) -> { counts.confirms.incrementAndGet(); return Mono.just("confirmed"); })
                .cancelHandler((input, ctx) -> { counts.cancels.incrementAndGet();
                    return failCancel ? Mono.error(new IllegalStateException("participant cancel failure")) : Mono.just("canceled"); })
                .add();
        if (failTry) builder.participant("rejected").tryHandler((input, ctx) -> {
                    counts.tries.incrementAndGet(); return Mono.error(new IllegalStateException("participant try failure")); })
                .confirmHandler((input, ctx) -> Mono.just("unexpected"))
                .cancelHandler((input, ctx) -> Mono.just("unexpected")).add();
        var base = builder.build();
        var tcc = new TccDefinition(base.name, callbacks, callbacks, base.timeoutMs, false, 0, 0);
        tcc.participants.putAll(base.participants);
        try {
            tcc.onTccCompleteMethods = List.of(Callbacks.class.getDeclaredMethod("complete"),
                    Callbacks.class.getDeclaredMethod("asyncComplete"));
            tcc.onTccErrorMethods = List.of(Callbacks.class.getDeclaredMethod("error"));
        } catch (NoSuchMethodException e) { throw new AssertionError(e); }
        return tcc;
    }

    static class Counts {
        final AtomicInteger tries = new AtomicInteger();
        final AtomicInteger confirms = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
    }

    static class Callbacks {
        final AtomicInteger syncComplete = new AtomicInteger();
        final CountDownLatch asyncComplete = new CountDownLatch(1);
        final AtomicInteger error = new AtomicInteger();
        @OnTccComplete public void complete() { syncComplete.incrementAndGet(); }
        @OnTccComplete(async = true) public void asyncComplete() { asyncComplete.countDown(); }
        @OnTccError(suppressError = true) public void error() { error.incrementAndGet(); }
    }

    static class FailingProvider extends InMemoryPersistenceProvider {
        final List<ExecutionState> attempted = new ArrayList<>();
        final List<ExecutionState> accepted = new ArrayList<>();
        final IllegalStateException cause = new IllegalStateException("private-provider-detail");
        private final int failAt;
        private final boolean synchronous;
        FailingProvider(int failAt, boolean synchronous) { this.failAt = failAt; this.synchronous = synchronous; }
        @Override public Mono<Void> save(ExecutionState state) {
            attempted.add(state);
            if (attempted.size() == failAt) {
                if (synchronous) throw cause;
                return Mono.error(cause);
            }
            return accept(state);
        }
        Mono<Void> accept(ExecutionState state) {
            return super.save(state).doOnSuccess(ignored -> accepted.add(state));
        }
    }
}
