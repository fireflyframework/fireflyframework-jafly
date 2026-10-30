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

package org.fireflyframework.eda.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.fireflyframework.eda.annotation.ErrorHandlingStrategy;
import org.fireflyframework.eda.annotation.EventListener;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.error.CustomErrorHandlerRegistry;
import org.fireflyframework.eda.event.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class EventListenerDeliveryOutcomeTest {
    private EventListenerProcessor processor(Object... beans) {
        var context = mock(ApplicationContext.class);
        var environment = mock(Environment.class);
        when(environment.resolvePlaceholders(anyString())).thenAnswer(call -> call.getArgument(0));
        String[] names = new String[beans.length];
        for (int i = 0; i < beans.length; i++) {
            names[i] = "handler" + i;
            when(context.getBean(names[i])).thenReturn(beans[i]);
        }
        when(context.getBeanDefinitionNames()).thenReturn(names);
        var processor = new EventListenerProcessor(context, mock(ApplicationEventPublisher.class),
                mock(CustomErrorHandlerRegistry.class), environment);
        processor.initializeEventListeners();
        return processor;
    }

    private EventEnvelope envelope() {
        return EventEnvelope.forConsuming("events", "String", "payload", "transaction",
                Map.of("custom", "preserved"), EventEnvelope.EventMetadata.empty(), Instant.EPOCH,
                "POSTGRES", "default", mock(EventEnvelope.AckCallback.class));
    }

    private EventDeliveryOutcome run(EventListenerProcessor processor, EventEnvelope envelope) {
        return processor.processDelivery(envelope).block(Duration.ofSeconds(2));
    }

    @Test void noMatchingListenerIsExplicitlyUnhandled() {
        var result = run(processor(), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.UNHANDLED);
        assertThat(result.matchedListeners()).isZero();
    }

    static Stream<Object> eligibleHandlers() {
        return Stream.of(new AutoHandler(), new PostgresHandler(), new GenericHandler());
    }

    @ParameterizedTest @MethodSource("eligibleHandlers")
    void postgresAutoAndObjectHandlersAreEligible(Object bean) {
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(result.matchedListeners()).isEqualTo(1);
        assertThat(result.completedListeners()).isEqualTo(1);
    }

    @Test void actualEnvelopeAndHeadersReachTheHandler() {
        var bean = new EnvelopeHandler();
        var envelope = envelope();
        assertThat(run(processor(bean), envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(bean.seen.get()).isSameAs(envelope);
        assertThat(bean.seen.get().headers()).isSameAs(envelope.headers());
    }

    @Test void handlerPublisherMustCompleteBeforeHandledOutcome() {
        var bean = new DelayedHandler();
        var seen = new AtomicReference<EventDeliveryOutcome>();
        var subscription = processor(bean).processDelivery(envelope()).doOnNext(seen::set).subscribe();
        try {
            assertThat(seen.get()).isNull();
            bean.gate.tryEmitEmpty();
            assertThat(seen.get().disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        } finally { subscription.dispose(); }
    }

    static Stream<Arguments> failures() {
        return Stream.of(Arguments.of(new IgnoreFailure(), EventDeliveryOutcome.Disposition.RETRY),
                Arguments.of(new ContinueFailure(), EventDeliveryOutcome.Disposition.RETRY),
                Arguments.of(new RetryFailure(), EventDeliveryOutcome.Disposition.RETRY),
                Arguments.of(new CustomFailure(), EventDeliveryOutcome.Disposition.RETRY),
                Arguments.of(new RejectFailure(), EventDeliveryOutcome.Disposition.RETRY),
                Arguments.of(new DeadLetterFailure(), EventDeliveryOutcome.Disposition.DEAD_LETTER));
    }

    @ParameterizedTest @MethodSource("failures")
    void annotationErrorStrategiesCannotHideFailedDelivery(Object bean, EventDeliveryOutcome.Disposition disposition) {
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(disposition);
        assertThat(result.matchedListeners()).isEqualTo(1);
        assertThat(result.failure()).isNotNull();
    }

    @Test void everySelectedHandlerTerminatesBeforeFailureOutcome() {
        var bean = new DelayedHandler();
        var seen = new AtomicReference<EventDeliveryOutcome>();
        var subscription = processor(bean, new ContinueFailure()).processDelivery(envelope()).doOnNext(seen::set).subscribe();
        try {
            assertThat(seen.get()).isNull();
            bean.gate.tryEmitEmpty();
            assertThat(seen.get().disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
            assertThat(seen.get().matchedListeners()).isEqualTo(2);
        } finally { subscription.dispose(); }
    }

    @Test void anotherTransportHandlerIsNotInvoked() {
        var bean = new KafkaHandler();
        assertThat(run(processor(bean), envelope()).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.UNHANDLED);
        assertThat(bean.called).isFalse();
    }

    @Test void legacyContinueStillCompletesForNonStrictCallers() {
        processor(new ContinueFailure()).processEvent("payload", Map.of()).block(Duration.ofSeconds(2));
    }

    @Test void strictProcessorDoesNotMutateAcknowledgment() {
        var envelope = envelope();
        assertThat(run(processor(new AutoHandler()), envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        verifyNoInteractions(envelope.ackCallback());
    }

    static class AutoHandler {
        @EventListener(async = false) public Mono<Void> on(String value) { return Mono.empty(); }
    }
    static class PostgresHandler {
        @EventListener(async = false, consumerType = PublisherType.POSTGRES) public Mono<Void> on(String value) { return Mono.empty(); }
    }
    static class GenericHandler {
        @EventListener(async = false) public Mono<Void> on(Object value) { return Mono.empty(); }
    }
    static class EnvelopeHandler {
        final AtomicReference<EventEnvelope> seen = new AtomicReference<>();
        @EventListener(async = false, destinations = "events") public Mono<Void> on(EventEnvelope value) { seen.set(value); return Mono.empty(); }
    }
    static class DelayedHandler {
        final Sinks.Empty<Void> gate = Sinks.empty();
        @EventListener(async = false) public Mono<Void> on(String value) { return gate.asMono(); }
    }
    static class KafkaHandler {
        boolean called;
        @EventListener(async = false, consumerType = PublisherType.KAFKA) public Mono<Void> on(String value) { called = true; return Mono.empty(); }
    }
    static class IgnoreFailure {
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.IGNORE) public Mono<Void> on(String value) { return Mono.error(new IllegalStateException("handler failed")); }
    }
    static class ContinueFailure {
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE) public Mono<Void> on(String value) { return Mono.error(new IllegalStateException("handler failed")); }
    }
    static class RetryFailure {
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY, maxRetries = 0) public Mono<Void> on(String value) { return Mono.error(new IllegalStateException("handler failed")); }
    }
    static class CustomFailure {
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.CUSTOM) public Mono<Void> on(String value) { return Mono.error(new IllegalStateException("handler failed")); }
    }
    static class RejectFailure {
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.REJECT_AND_STOP) public Mono<Void> on(String value) { return Mono.error(new IllegalStateException("handler failed")); }
    }
    static class DeadLetterFailure {
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.DEAD_LETTER) public Mono<Void> on(String value) { return Mono.error(new IllegalStateException("handler failed")); }
    }
}
