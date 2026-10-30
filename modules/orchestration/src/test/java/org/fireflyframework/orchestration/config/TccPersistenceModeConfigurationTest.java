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

import org.fireflyframework.orchestration.core.persistence.ExecutionPersistenceProvider;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import org.fireflyframework.orchestration.core.persistence.InMemoryPersistenceProvider;
import org.fireflyframework.orchestration.tcc.builder.TccBuilder;
import org.fireflyframework.orchestration.tcc.engine.TccEngine;
import org.fireflyframework.orchestration.tcc.engine.TccInputs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TccPersistenceModeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrchestrationAutoConfiguration.class,
                    TccAutoConfiguration.class))
            .withPropertyValues("firefly.orchestration.tcc.composition.enabled=false");

    @Test
    void requiredPropertyStopsBeforeAnyParticipantWhenInitialSaveFails() {
        var calls = new AtomicInteger();
        var cause = new IllegalStateException("private-provider-detail");
        var provider = new InMemoryPersistenceProvider() {
            @Override public Mono<Void> save(ExecutionState state) { return Mono.error(cause); }
        };
        runner.withBean(ExecutionPersistenceProvider.class, () -> provider)
                .withPropertyValues("firefly.orchestration.tcc.persistence-mode=REQUIRED")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var tcc = TccBuilder.tccNoRetry("configured-required")
                            .participant("reservation")
                            .tryHandler((input, ctx) -> { calls.incrementAndGet(); return Mono.just("reserved"); })
                            .confirmHandler((input, ctx) -> Mono.just("confirmed"))
                            .cancelHandler((input, ctx) -> Mono.just("canceled")).add().build();
                    StepVerifier.create(context.getBean(TccEngine.class).execute(tcc, TccInputs.empty()))
                            .expectErrorSatisfies(error -> {
                                assertThat(error.getClass().getSimpleName()).isEqualTo("TccPersistenceException");
                                assertThat(error.getCause()).isSameAs(cause);
                                assertThat(error.getMessage()).doesNotContain("private-provider-detail");
                            }).verify();
                    assertThat(calls).hasValue(0);
                });
    }

    @Test
    void omittedPropertyRetainsBestEffortWithUnavailableProvider() {
        runner.withBean(ExecutionPersistenceProvider.class, () -> new InMemoryPersistenceProvider() {
                    @Override public Mono<Void> save(ExecutionState state) {
                        return Mono.error(new IllegalStateException("unavailable"));
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var tcc = TccBuilder.tccNoRetry("configured-default")
                            .participant("reservation")
                            .tryHandler((input, ctx) -> Mono.just("reserved"))
                            .confirmHandler((input, ctx) -> Mono.just("confirmed"))
                            .cancelHandler((input, ctx) -> Mono.just("canceled")).add().build();
                    StepVerifier.create(context.getBean(TccEngine.class).execute(tcc, TccInputs.empty()))
                            .assertNext(result -> assertThat(result.isConfirmed()).isTrue()).verifyComplete();
                });
    }
}
