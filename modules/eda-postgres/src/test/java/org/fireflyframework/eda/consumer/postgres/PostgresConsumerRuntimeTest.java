/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.error.CustomErrorHandlerRegistry;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.listener.EventListenerProcessor;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PostgresConsumerRuntimeTest {
    @Test void protocolVerifyBeforeAnyAdmission() {
        try (var f = new Fixture()) {
            var gate = Sinks.<Void>empty(); f.repo.protocol = gate.asMono();
            StepVerifier.create(f.consumer().start())
                    .then(() -> { assertThat(f.repo.verifications).isEqualTo(1); assertThat(f.repo.limits).isEmpty(); verifyNoInteractions(f.factory); })
                    .then(() -> gate.tryEmitEmpty())
                    .verifyComplete();
            assertThat(f.consumer().isRunning()).isTrue();
        }
    }
    @Test void protocolFailureNeverOpensListenOrClaims() {
        try (var f = new Fixture()) {
            f.repo.protocol = Mono.error(new IllegalStateException("missing protocol"));
            StepVerifier.create(f.consumer().start()).expectErrorMessage("missing protocol").verify();
            assertThat(f.consumer().isRunning()).isFalse(); assertThat(f.repo.limits).isEmpty(); verifyNoInteractions(f.factory);
        }
    }
    @Test void delayedStrictPublisherCannotAckEarly() {
        try (var f = new Fixture()) {
            var completion = Sinks.<Void>empty(); f.processor.registerListener("owner", "events", PublisherType.POSTGRES, (p,h) -> completion.asMono());
            f.repo.rows.add(claim(1)); f.start(); f.tick();
            assertThat(f.repo.acknowledged).isZero(); assertThat(f.repo.limits).containsExactly(1);
            completion.tryEmitEmpty(); f.tick(); f.awaitProcessed(1); assertThat(f.repo.acknowledged).isEqualTo(1);
        }
    }
    @Test void unhandledOutcomeRejectsInsteadOfAcknowledging() {
        try (var f = new Fixture()) {
            f.repo.rows.add(claim(1)); f.start(); f.tick();
            assertThat(f.repo.acknowledged).isZero(); assertThat(f.repo.rejected).containsExactly(FailureKind.UNHANDLED);
        }
    }
    @Test void manualZeroDemandDoesNotClaim() {
        try (var f = new Fixture()) {
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL); f.repo.rows.add(claim(1));
            StepVerifier.create(f.consumer().consume(), 0).then(f::tick)
                    .then(() -> { assertThat(f.consumer().isRunning()).isTrue(); assertThat(f.repo.verifications).isEqualTo(1); assertThat(f.repo.limits).isEmpty(); }).thenCancel().verify();
        }
    }
    @Test void manualSecondSubscriberFails() {
        try (var f = new Fixture()) {
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);
            Disposable first = f.consumer().consume().subscribe();
            try { StepVerifier.create(f.consumer().consume()).expectErrorMessage("A manual processing subscriber is already registered").verify(); }
            finally { first.dispose(); }
        }
    }
    @Test void manualCallbackWaitsForRealFencedResult() {
        try (var f = new Fixture()) {
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL); f.repo.rows.add(claim(1));
            var transition = Sinks.<TransitionResult>one(); f.repo.ack = transition.asMono(); var received = new ArrayList<EventEnvelope>();
            Disposable worker = f.consumer().consume().subscribe(received::add);
            try {
                f.tick(); assertThat(received).hasSize(1); assertThat(f.repo.acknowledged).isZero();
                StepVerifier.create(received.getFirst().acknowledge()).then(() -> assertThat(f.repo.acknowledged).isEqualTo(1))
                        .then(() -> transition.tryEmitValue(TransitionResult.APPLIED)).verifyComplete();
            } finally { worker.dispose(); }
        }
    }
    @Test void notificationBurstCannotExceedSharedPermitBound() {
        try (var f = new Fixture()) {
            f.properties.getConsumer().setConcurrency(2); var calls = new AtomicInteger();
            f.processor.registerListener("owner", "events", PublisherType.POSTGRES, (p,h) -> { calls.incrementAndGet(); return Mono.never(); });
            for (int i=0;i<10;i++) f.repo.rows.add(claim(i+1)); f.start();
            for (int i=0;i<100;i++) { var n=mock(Notification.class);when(n.getName()).thenReturn(PostgresChannelMapper.toChannel("events"));when(n.getParameter()).thenReturn("untrusted");f.notifications.tryEmitNext(n); }
            f.tick(); org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->calls.get()==2); assertThat(calls).hasValue(2); assertThat(f.repo.limits).allMatch(n -> n<=2); assertThat(f.repo.claimed).isEqualTo(2); assertThat(f.repo.acknowledged).isZero();
        }
    }
    @Test void nativeFixtureUsesActualBoundedComponentGraphWithoutOpeningConnections(){
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.fireflyframework.eda.config.FireflyEdaPostgresPublisherAutoConfiguration.class,
                        org.fireflyframework.eda.config.FireflyEdaPostgresConsumerAutoConfiguration.class))
                .withUserConfiguration(PostgresConsumerNativeFixture.Dependencies.class)
                .withPropertyValues("firefly.eda.enabled=true","firefly.eda.consumer.enabled=true",
                        "firefly.eda.consumer.postgres.default.enabled=true","firefly.eda.consumer.postgres.default.host=127.0.0.1",
                        "firefly.eda.consumer.postgres.default.database=source_fixture","firefly.eda.consumer.postgres.default.username=source_fixture",
                        "firefly.eda.consumer.postgres.default.channels=native-source-events","firefly.eda.publishers.enabled=true",
                        "firefly.eda.publishers.postgres.default.enabled=true","firefly.eda.publishers.postgres.default.host=127.0.0.1",
                        "firefly.eda.publishers.postgres.default.database=source_fixture","firefly.eda.publishers.postgres.default.username=source_fixture",
                        "firefly.eda.publishers.postgres.default.auto-create-schema=false")
                .run(context->{
                    assertThat(context).hasNotFailed().hasSingleBean(PostgresEventConsumer.class);
                    assertThat(context.getBean(org.fireflyframework.eda.serialization.MessageSerializer.class)).isInstanceOf(org.fireflyframework.eda.serialization.JsonMessageSerializer.class);
                    assertThat(context.getBean(EventListenerProcessor.class).getTopicsForConsumerType("POSTGRES")).containsExactly("native-source-events");
                    assertThat(context.getBean(PostgresConsumerNativeFixture.Handler.class).calls).hasValue(0);
                    assertThat(context.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory",io.r2dbc.pool.ConnectionPool.class).getMetrics().orElseThrow().allocatedSize()).isZero();
                    assertThat(context.getBean(PostgresEventConsumer.class).isRunning()).isFalse();
                });
    }
    static DeliveryClaim claim(long id) {
        var at=OffsetDateTime.of(2026,10,30,0,0,0,0,ZoneOffset.UTC);
        return new DeliveryClaim(new PostgresDeliveryRow(id,"events",PostgresChannelMapper.toChannel("events"),"String","payload".getBytes(StandardCharsets.UTF_8),null,"transaction","POSTGRES","default","java.lang.String","IN_FLIGHT",1,null,at,null,null,UUID.randomUUID(),1,at.plusDays(1),null),3);
    }
    static final class RecordingRepository implements PostgresDeliveryRepository {
        Mono<Void> protocol=Mono.empty();Mono<TransitionResult> ack=Mono.just(TransitionResult.APPLIED);volatile int verifications,acknowledged,claimed;
        final Queue<Mono<ClaimBatch>> scriptedClaims=new ArrayDeque<>();
        final Queue<DeliveryClaim> rows=new ArrayDeque<>();final List<Integer> limits=new ArrayList<>();final List<FailureKind> rejected=new ArrayList<>();
        public Mono<Void> verifyProtocol(){return Mono.defer(()->{verifications++;return protocol;});}
        public Mono<ClaimBatch> claim(ClaimRequest r){return Mono.defer(()->{limits.add(r.limit());if(!scriptedClaims.isEmpty())return scriptedClaims.remove();var list=new ArrayList<DeliveryClaim>();while(list.size()<r.limit()&&!rows.isEmpty())list.add(rows.remove());claimed+=list.size();return Mono.just(new ClaimBatch(list));});}
        public Mono<Long> reapExhausted(ClaimRequest r){return Mono.just(0L);}
        public Mono<TransitionResult> acknowledge(DeliveryClaim c){return Mono.defer(()->{synchronized(this){acknowledged++;}return ack;});}
        public Mono<TransitionResult> reject(DeliveryClaim c,FailureKind k,Duration d){return Mono.fromSupplier(()->{rejected.add(k);return TransitionResult.APPLIED;});}
    }
    static final class Fixture implements AutoCloseable {
        final EdaProperties properties=new EdaProperties();final RecordingRepository repo=new RecordingRepository();final VirtualTimeScheduler scheduler=VirtualTimeScheduler.create();
        final ConnectionFactory factory=mock(ConnectionFactory.class);final PostgresqlConnection connection=mock(PostgresqlConnection.class);final Sinks.Many<Notification> notifications=Sinks.many().multicast().directBestEffort();
        final EventListenerProcessor processor=new EventListenerProcessor(mock(ApplicationContext.class),mock(ApplicationEventPublisher.class),mock(CustomErrorHandlerRegistry.class),new MockEnvironment());
        final ObjectProvider<ConnectionFactory> provider;PostgresEventConsumer current;
        @SuppressWarnings("unchecked") Fixture(){
            provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(factory);properties.getConsumer().setEnabled(true);properties.getConsumer().getPostgres().get("default").setEnabled(true);
            var statement=mock(io.r2dbc.postgresql.api.PostgresqlStatement.class);var result=mock(io.r2dbc.postgresql.api.PostgresqlResult.class);
            doReturn(Mono.just(connection)).when(factory).create();when(connection.getNotifications()).thenReturn(notifications.asFlux());when(connection.createStatement(anyString())).thenReturn(statement);
            doReturn(Flux.just(result)).when(statement).execute();doReturn(Mono.just(0L)).when(result).getRowsUpdated();when(connection.close()).thenReturn(Mono.empty());
        }
        void mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode m){properties.getConsumer().getPostgres().get("default").setDeliveryMode(m);}
        PostgresEventConsumer consumer(){if(current==null)current=new PostgresEventConsumer(provider,processor,mock(MessageSerializer.class),properties,new ObjectMapper(),repo,(bytes,type,event)->"payload",scheduler);return current;}
        void start(){StepVerifier.create(consumer().start()).verifyComplete();}
        void tick(){scheduler.advanceTimeBy(Duration.ZERO);}
        void awaitProcessed(int n){try{org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->repo.acknowledged==n);}catch(org.awaitility.core.ConditionTimeoutException e){throw new AssertionError("ACK="+repo.acknowledged+", claimed="+repo.claimed+", limits="+repo.limits+", rejected="+repo.rejected+", notificationSubscribers="+notifications.currentSubscriberCount()+", running="+consumer().isRunning());}}
        void signal(){var n=mock(Notification.class);when(n.getName()).thenReturn(PostgresChannelMapper.toChannel("events"));notifications.tryEmitNext(n);tick();}
        public void close(){if(current!=null)current.stop().onErrorResume(e->Mono.empty()).block(Duration.ofSeconds(1));scheduler.dispose();}
    }
}
