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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.fireflyframework.eda.annotation.ErrorHandlingStrategy;
import org.fireflyframework.eda.annotation.EventListener;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.error.CustomErrorHandlerRegistry;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.expression.SpelExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class EventListenerDeliveryContractTest {
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
        return envelope("events", "POSTGRES", "default", Map.of("custom", "preserved"));
    }

    private EventEnvelope envelope(String destination, String consumer, String connection, Map<String, Object> headers) {
        return EventEnvelope.forConsuming(destination, "String", "payload", "transaction", headers,
                EventEnvelope.EventMetadata.empty(), Instant.EPOCH, consumer, connection,
                mock(EventEnvelope.AckCallback.class));
    }

    private EventDeliveryOutcome run(EventListenerProcessor processor, EventEnvelope envelope) {
        return processor.processDelivery(envelope).block(Duration.ofSeconds(2));
    }

    @Test void conditionSeesMethodArgumentAndActualEnvelope() {
        var bean = new TrueCondition();
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(result.completedListeners()).isEqualTo(1);
        assertThat(bean.calls).isEqualTo(1);
    }

    @Test void allFalseConditionsRemainUnhandled() {
        var bean = new FalseCondition();
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.UNHANDLED);
        assertThat(result.matchedListeners()).isEqualTo(1);
        assertThat(result.completedListeners()).isZero();
        assertThat(result.failure()).isNull();
        assertThat(bean.calls).isZero();
    }

    @Test void filteredListenerCannotVetoAnotherCompletedListener() {
        var filtered = new FalseCondition();
        var actual = new TrueCondition();
        var result = run(processor(filtered, actual), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(result.matchedListeners()).isEqualTo(2);
        assertThat(result.completedListeners()).isEqualTo(1);
        assertThat(filtered.calls).isZero();
        assertThat(actual.calls).isEqualTo(1);
    }

    static Stream<Object> invalidConditions() {
        return Stream.of(new MalformedCondition(), new NullCondition(), new TextCondition(), new MissingPropertyCondition());
    }

    @ParameterizedTest @MethodSource("invalidConditions")
    void invalidConditionNeverAcknowledgesOrLogsRawExpression(Object bean) {
        var processor = processor(bean);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        Logger evaluator = (Logger) LoggerFactory.getLogger(SpelExpressionEvaluator.class);
        Logger listener = (Logger) LoggerFactory.getLogger(EventListenerProcessor.class);
        evaluator.addAppender(appender);
        listener.addAppender(appender);
        try {
            var result = run(processor, envelope());
            assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
            assertThat(result.completedListeners()).isZero();
            assertThat(result.failure()).isNotNull();
            assertThat(appender.list).isEmpty();
        } finally {
            evaluator.detachAppender(appender);
            listener.detachAppender(appender);
            appender.stop();
        }
    }

    @Test void incomingRoutingHeadersCannotOverrideTrustedEnvelope() {
        var postgres = new DestinationHandler();
        var kafka = new KafkaHandler();
        var envelope = envelope("events", "POSTGRES", "default",
                Map.of("destination", "other", "consumer_type", "KAFKA", "connection_id", "foreign"));
        assertThat(run(processor(postgres, kafka), envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(postgres.called).isTrue();
        assertThat(kafka.called).isFalse();
    }

    @Test void forgedHeadersCannotSelectWrongActualDestinationOrConnection() {
        var bean = new DestinationHandler();
        var envelope = envelope("other", "POSTGRES", "foreign",
                Map.of("destination", "events", "consumer_type", "POSTGRES", "connection_id", "default"));
        assertThat(run(processor(bean), envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.UNHANDLED);
        assertThat(bean.called).isFalse();
    }

    @Test void annotationManualAcknowledgmentIsConservativelyIncompatible() {
        var bean = new ManualHandler();
        var envelope = envelope();
        var result = run(processor(bean), envelope);
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
        assertThat(result.failure()).isNotNull();
        assertThat(bean.called).isFalse();
        verifyNoInteractions(envelope.ackCallback());
    }

    @Test void cancellationCancelsListenerWithoutOutcomeOrAcknowledgment() {
        var bean = new CancellableHandler();
        var envelope = envelope();
        var result = new AtomicReference<EventDeliveryOutcome>();
        var subscription = processor(bean).processDelivery(envelope).doOnNext(result::set).subscribe();
        assertThat(bean.subscribed.get()).isTrue();
        assertThat(result.get()).isNull();
        subscription.dispose();
        assertThat(bean.cancelled.get()).isTrue();
        assertThat(result.get()).isNull();
        verifyNoInteractions(envelope.ackCallback());
    }

    @Test void fluxValuesDoNotCountAsCompletion() {
        var bean = new FluxHandler();
        var result = new AtomicReference<EventDeliveryOutcome>();
        var subscription = processor(bean).processDelivery(envelope()).doOnNext(result::set).subscribe();
        try {
            assertThat(bean.gate.tryEmitNext("first")).isEqualTo(Sinks.EmitResult.OK);
            assertThat(result.get()).isNull();
            assertThat(bean.gate.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
            assertThat(result.get().disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        } finally { subscription.dispose(); }
    }

    @Test void nullDeclaredPublisherCannotBecomeHandled() {
        assertThat(run(processor(new NullPublisherHandler()), envelope()).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
    }

    @Test void unsupportedArgumentsCannotSilentlyBecomeNull() {
        var bean = new UnsupportedArgumentHandler();
        assertThat(run(processor(bean), envelope()).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
        assertThat(bean.called).isFalse();
    }

    static Stream<Object> futureHandlers() { return Stream.of(new FutureHandler(), new NullFutureHandler()); }

    @ParameterizedTest @MethodSource("futureHandlers")
    void futureResultCannotBecomeHandledWithoutAwaitingIt(Object bean) {
        assertThat(run(processor(bean), envelope()).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
    }

    @Test void synchronousFailurePreservesOriginalThrowableInternally() {
        var bean = new ThrowingHandler();
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
        assertThat(result.failure()).isSameAs(bean.failure);
    }

    @Test void successfulRetryStillWaitsForItsPublisherCompletion() {
        var bean = new RetryHandler();
        StepVerifier.withVirtualTime(() -> processor(bean).processDelivery(envelope()))
                .expectSubscription().thenAwait(Duration.ofSeconds(1))
                .then(() -> assertThat(bean.calls.get()).isEqualTo(2))
                .then(() -> assertThat(bean.gate.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK))
                .assertNext(result -> {
                    assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
                    assertThat(result.completedListeners()).isEqualTo(1);
                }).verifyComplete();
    }

    @Test void listenerTimeoutReturnsRetryWithoutAcknowledgment() {
        var envelope = envelope();
        StepVerifier.withVirtualTime(() -> processor(new TimedHandler()).processDelivery(envelope))
                .expectSubscription().thenAwait(Duration.ofMillis(11))
                .assertNext(result -> {
                    assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
                    assertThat(result.completedListeners()).isZero();
                }).verifyComplete();
        verifyNoInteractions(envelope.ackCallback());
    }

    @Test void jdkFlowPublisherCannotBecomeHandledWithoutCompletion() {
        var result = run(processor(new FlowHandler()), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
        assertThat(result.failure()).isNotNull();
    }

    @Test void nullDeclaredJdkFlowPublisherCannotBecomeHandled() {
        assertThat(run(processor(new NullFlowHandler()), envelope()).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
    }

    static Stream<Object> unsupportedSynchronousResults() {
        return Stream.of(new ObjectResultHandler(), new StringResultHandler(), new NullObjectHandler(), new NullVoidHandler());
    }

    @ParameterizedTest @MethodSource("unsupportedSynchronousResults")
    void onlyVoidIsAValidNonReactiveCompletion(Object bean) {
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
        assertThat(result.failure()).isNotNull();
    }

    @Test void synchronousVoidCompletionIsHandled() {
        var bean = new VoidHandler();
        var result = run(processor(bean), envelope());
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(result.completedListeners()).isEqualTo(1);
        assertThat(bean.called).isTrue();
    }

    @Test void objectDeclaredActualReactiveStreamsPublisherIsAwaited() {
        var bean = new ObjectPublisherHandler();
        var result = new AtomicReference<EventDeliveryOutcome>();
        var subscription = processor(bean).processDelivery(envelope()).doOnNext(result::set).subscribe();
        try {
            assertThat(result.get()).isNull();
            assertThat(bean.gate.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
            assertThat(result.get().disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        } finally { subscription.dispose(); }
    }

    static class FlowHandler {
        @EventListener(async = false) public java.util.concurrent.Flow.Publisher<String> on(String value) { return subscriber -> { }; }
    }
    static class NullFlowHandler {
        @EventListener(async = false) public java.util.concurrent.Flow.Publisher<String> on(String value) { return null; }
    }
    static class ObjectResultHandler {
        @EventListener(async = false) public Object on(String value) { return new Object(); }
    }
    static class StringResultHandler {
        @EventListener(async = false) public String on(String value) { return "result"; }
    }
    static class NullObjectHandler {
        @EventListener(async = false) public Object on(String value) { return null; }
    }
    static class NullVoidHandler {
        @EventListener(async = false) public Void on(String value) { return null; }
    }
    static class VoidHandler {
        boolean called;
        @EventListener(async = false) public void on(String value) { called = true; }
    }
    static class ObjectPublisherHandler {
        final Sinks.Empty<Void> gate = Sinks.empty();
        @EventListener(async = false) public Object on(String value) { return gate.asMono(); }
    }

    private EventEnvelope mapEnvelope(Map<String,Object> payload) {
        return EventEnvelope.forConsuming("events", "Map", payload, "transaction", Map.of("headerField", "headersOnly"),
                EventEnvelope.EventMetadata.empty(), Instant.EPOCH, "POSTGRES", "default", mock(EventEnvelope.AckCallback.class));
    }

    @Test void twoMapArgumentsKeepFirstPayloadAndLaterHeadersDistinct() {
        var bean = new MapPairHandler();
        var envelope = mapEnvelope(Map.of("payloadField", "payloadOnly"));
        assertThat(run(processor(bean), envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(bean.payload.get()).isSameAs(envelope.payload());
        assertThat(bean.headers.get()).isSameAs(envelope.headers());
        assertThat(bean.headers.get()).isNotSameAs(envelope.payload());
    }

    @Test void envelopePlusMapReceivesExactEnvelopeAndItsHeaders() {
        var bean = new EnvelopeMapHandler();
        var envelope = mapEnvelope(Map.of("payloadField", "payloadOnly"));
        assertThat(run(processor(bean), envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(bean.envelope.get()).isSameAs(envelope);
        assertThat(bean.headers.get()).isSameAs(envelope.headers());
    }

    @Test void actualDynamicGenericMapListenerReceivesPayloadAndSeparateHeaders() {
        var processor = processor();
        var payload = new AtomicReference<Object>();
        var headers = new AtomicReference<Map<String,Object>>();
        processor.registerListener("dynamic-map", "events", PublisherType.POSTGRES, (event, actualHeaders) -> {
            payload.set(event);headers.set(actualHeaders);return Mono.empty();
        });
        var envelope = mapEnvelope(Map.of("payloadField", "payloadOnly"));
        assertThat(run(processor, envelope).disposition()).isEqualTo(EventDeliveryOutcome.Disposition.HANDLED);
        assertThat(payload.get()).isSameAs(envelope.payload());
        assertThat(headers.get()).isSameAs(envelope.headers());
    }

    @Test void incompatibleConcreteMapHeaderCannotBeGuessedFromThePayload() {
        var bean = new ConcreteMapHeaderHandler();
        var payload = new java.util.TreeMap<String,Object>();payload.put("payloadField", "payloadOnly");
        var result = run(processor(bean), mapEnvelope(payload));
        assertThat(result.disposition()).isEqualTo(EventDeliveryOutcome.Disposition.RETRY);
        assertThat(result.failure()).isNotNull();
        assertThat(bean.called).isFalse();
    }

    static class MapPairHandler {
        final AtomicReference<Map<String,Object>> payload = new AtomicReference<>();
        final AtomicReference<Map<String,Object>> headers = new AtomicReference<>();
        @EventListener(async = false) public Mono<Void> on(Map<String,Object> payload, Map<String,Object> headers) {
            this.payload.set(payload);this.headers.set(headers);return Mono.empty();
        }
    }
    static class EnvelopeMapHandler {
        final AtomicReference<EventEnvelope> envelope = new AtomicReference<>();
        final AtomicReference<Map<String,Object>> headers = new AtomicReference<>();
        @EventListener(async = false, destinations = "events") public Mono<Void> on(EventEnvelope envelope, Map<String,Object> headers) {
            this.envelope.set(envelope);this.headers.set(headers);return Mono.empty();
        }
    }
    static class ConcreteMapHeaderHandler {
        boolean called;
        @EventListener(async = false) public Mono<Void> on(Object payload, java.util.TreeMap<String,Object> headers) {
            called = true;return Mono.empty();
        }
    }

    static class TrueCondition {
        int calls;
        @EventListener(async = false, condition = "#param0 == 'payload' && #event.headers['custom'] == 'preserved' && #event.destination == 'events'")
        public Mono<Void> on(String value) { calls++; return Mono.empty(); }
    }
    static class FalseCondition {
        int calls;
        @EventListener(async = false, condition = "#event.destination == 'other'")
        public Mono<Void> on(String value) { calls++; return Mono.empty(); }
    }
    static class MalformedCondition {
        @EventListener(async = false, condition = "#event.headers['private-value'] == (") public Mono<Void> on(String value) { throw new AssertionError("must not invoke"); }
    }
    static class NullCondition {
        @EventListener(async = false, condition = "#{null}") public Mono<Void> on(String value) { throw new AssertionError("must not invoke"); }
    }
    static class TextCondition {
        @EventListener(async = false, condition = "#{'true'}") public Mono<Void> on(String value) { throw new AssertionError("must not invoke"); }
    }
    static class MissingPropertyCondition {
        @EventListener(async = false, condition = "#event.missingPrivateValue") public Mono<Void> on(String value) { throw new AssertionError("must not invoke"); }
    }
    static class DestinationHandler {
        boolean called;
        @EventListener(async = false, consumerType = PublisherType.POSTGRES, destinations = "events")
        public Mono<Void> on(String value) { called = true; return Mono.empty(); }
    }
    static class KafkaHandler {
        boolean called;
        @EventListener(async = false, consumerType = PublisherType.KAFKA, destinations = "events")
        public Mono<Void> on(String value) { called = true; return Mono.empty(); }
    }
    static class ManualHandler {
        boolean called;
        @EventListener(async = false, autoAck = false) public Mono<Void> on(String value) { called = true; return Mono.empty(); }
    }
    static class CancellableHandler {
        final AtomicBoolean subscribed = new AtomicBoolean();
        final AtomicBoolean cancelled = new AtomicBoolean();
        @EventListener(async = false) public Mono<Void> on(String value) {
            return Mono.<Void>never().doOnSubscribe(ignored -> subscribed.set(true)).doOnCancel(() -> cancelled.set(true));
        }
    }
    static class FluxHandler {
        final Sinks.Many<String> gate = Sinks.many().unicast().onBackpressureBuffer();
        @EventListener(async = false) public Flux<String> on(String value) { return gate.asFlux(); }
    }
    static class NullPublisherHandler {
        @EventListener(async = false) public Mono<Void> on(String value) { return null; }
    }
    static class UnsupportedArgumentHandler {
        boolean called;
        @EventListener(async = false) public Mono<Void> on(String value, Integer unsupported) { called = true; return Mono.empty(); }
    }
    static class FutureHandler {
        @EventListener(async = false) public CompletableFuture<Void> on(String value) { return new CompletableFuture<>(); }
    }
    static class NullFutureHandler {
        @EventListener(async = false) public CompletableFuture<Void> on(String value) { return null; }
    }
    static class ThrowingHandler {
        final RuntimeException failure = new IllegalStateException("private failure");
        @EventListener(async = false) public Mono<Void> on(String value) { throw failure; }
    }
    static class RetryHandler {
        final AtomicInteger calls = new AtomicInteger();
        final Sinks.Empty<Void> gate = Sinks.empty();
        @EventListener(async = false, errorStrategy = ErrorHandlingStrategy.LOG_AND_RETRY, maxRetries = 1, retryDelayMs = 1)
        public Mono<Void> on(String value) { return calls.incrementAndGet() == 1 ? Mono.error(new IllegalStateException("first")) : gate.asMono(); }
    }
    static class TimedHandler {
        @EventListener(async = false, timeoutMs = 10) public Mono<Void> on(String value) { return Mono.never(); }
    }
}
