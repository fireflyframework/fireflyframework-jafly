/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.fireflyframework.eda.consumer.postgres.PostgresConsumerRuntimeTest.claim;

class PostgresConsumerRecoveryTest {
    @Test void pollFailureRecoversAndKeepsTheSameCapacityBound(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.processor.registerListener("owner","events",PublisherType.POSTGRES,(p,h)->Mono.empty());f.repo.scriptedClaims.add(Mono.error(new IllegalStateException("database unavailable")));f.repo.rows.add(claim(1));f.start();f.tick();
            assertThat(f.repo.acknowledged).isZero();f.scheduler.advanceTimeBy(Duration.ofSeconds(1));f.awaitProcessed(1);assertThat(f.repo.acknowledged).isEqualTo(1);assertThat(f.repo.limits).allMatch(n->n==1);
        }
    }
    @Test void pollingWorksWhileListenNeverConnects(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            doReturn(Mono.never()).when(f.factory).create();f.processor.registerListener("owner","events",PublisherType.POSTGRES,(p,h)->Mono.empty());f.repo.rows.add(claim(1));f.start();f.tick();f.awaitProcessed(1);assertThat(f.repo.acknowledged).isEqualTo(1);
        }
    }
    @Test void notificationFailureClosesAndReconnectsWithoutDisablingPolling(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            when(f.connection.getNotifications()).thenReturn(Flux.error(new IllegalStateException("listen lost")),f.notifications.asFlux());f.start();f.tick();verify(f.connection).close();f.scheduler.advanceTimeBy(Duration.ofSeconds(1));verify(f.factory,times(2)).create();assertThat(f.consumer().isRunning()).isTrue();
        }
    }
    @Test void stoppedRunCannotClaimFromAnAlreadyScheduledWake(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.start();int before=f.repo.limits.size();f.repo.rows.add(claim(1));StepVerifier.create(f.consumer().stop()).verifyComplete();f.scheduler.advanceTimeBy(Duration.ofSeconds(30));assertThat(f.repo.limits).hasSize(before);
        }
    }
    @Test void stopWaitsForOwnedListenClose(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            var close=Sinks.<Void>empty();when(f.connection.close()).thenReturn(close.asMono());f.start();f.tick();
            StepVerifier.create(f.consumer().stop()).then(()->close.tryEmitEmpty()).verifyComplete();verify(f.connection,times(1)).close();
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void stopReportsImmediateOwnedListenCloseFailure(boolean synchronousThrow){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            var failure=new IllegalStateException("controlled owned LISTEN close failure");
            if(synchronousThrow)when(f.connection.close()).thenThrow(failure);
            else when(f.connection.close()).thenReturn(Mono.error(failure));
            f.start();f.tick();
            StepVerifier.create(f.consumer().stop()).expectErrorMatches(e->e==failure||e.getCause()==failure).verify(Duration.ofSeconds(1));
            StepVerifier.create(f.consumer().stop()).expectErrorMatches(e->e==failure||e.getCause()==failure).verify(Duration.ofSeconds(1));
            verify(f.connection,times(1)).close();assertThat(f.consumer().isRunning()).isFalse();
        }
    }
    @Test void concurrentStopsShareAndReportTheSameDelayedListenCloseFailure(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            var close=Sinks.<Void>empty();var failure=new IllegalStateException("controlled delayed LISTEN close failure");
            when(f.connection.close()).thenReturn(close.asMono());f.start();f.tick();
            var first=new java.util.concurrent.atomic.AtomicReference<Throwable>();var second=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var completed=new java.util.concurrent.atomic.AtomicInteger();
            f.consumer().stop().subscribe(v->{},first::set,completed::incrementAndGet);
            f.consumer().stop().subscribe(v->{},second::set,completed::incrementAndGet);
            assertThat(first.get()).isNull();assertThat(second.get()).isNull();assertThat(completed).hasValue(0);
            close.tryEmitError(failure);
            assertThat(first.get()).isSameAs(failure);assertThat(second.get()).isSameAs(failure);assertThat(completed).hasValue(0);
            StepVerifier.create(f.consumer().stop()).expectErrorMatches(e->e==failure).verify(Duration.ofSeconds(1));
            verify(f.connection,times(1)).close();
        }
    }
    @Test void stopTimeoutIsReportedAndDoesNotDisposeTheExternalFactory(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            when(f.connection.close()).thenReturn(Mono.never());f.start();f.tick();
            StepVerifier.create(f.consumer().stop()).then(()->f.scheduler.advanceTimeBy(Duration.ofSeconds(5))).expectError(java.util.concurrent.TimeoutException.class).verify();
            assertThat(f.consumer().isRunning()).isFalse();verify(f.factory).create();
        }
    }
    @Test void aPrecreatedConsumeFluxBindsToTheNewRunAfterRestart(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);Flux<EventEnvelope> reusable=f.consumer().consume();Disposable first=reusable.subscribe();first.dispose();StepVerifier.create(f.consumer().stop()).verifyComplete();
            f.repo.rows.add(claim(1));var received=new ArrayList<EventEnvelope>();Disposable second=reusable.subscribe(received::add);try{f.tick();assertThat(received).hasSize(1);}finally{second.dispose();}
        }
    }
    @Test void annotationObserverCannotChangeTransportState(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.processor.registerListener("owner","events",PublisherType.POSTGRES,(p,h)->Mono.empty());var received=new ArrayList<EventEnvelope>();Disposable observer=f.consumer().consume().subscribe(received::add);try{
                f.repo.rows.add(claim(1));f.signal();f.awaitProcessed(1);assertThat(received).hasSize(1);assertThat(f.repo.acknowledged).isEqualTo(1);StepVerifier.create(received.getFirst().acknowledge()).expectErrorMessage("Observation deliveries cannot change transport state").verify();assertThat(f.repo.acknowledged).isEqualTo(1);
            }finally{observer.dispose();}
        }
    }
    @Test void zeroObserverDemandDoesNotBlockAnnotatedOwnership(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.processor.registerListener("owner","events",PublisherType.POSTGRES,(p,h)->Mono.empty());StepVerifier.create(f.consumer().consume(),0).then(()->f.repo.rows.add(claim(1))).then(f::signal).then(()->{f.awaitProcessed(1);assertThat(f.repo.acknowledged).isEqualTo(1);}).thenCancel().verify();
        }
    }
    @Test void staleManualAckFailsWithoutASecondMutation(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);f.repo.ack=Mono.just(TransitionResult.STALE);f.repo.rows.add(claim(1));var received=new ArrayList<EventEnvelope>();Disposable worker=f.consumer().consume().subscribe(received::add);try{
                f.tick();StepVerifier.create(received.getFirst().acknowledge()).expectErrorMessage("Delivery ownership is stale").verify();assertThat(f.repo.acknowledged).isEqualTo(1);assertThat(f.repo.rejected).isEmpty();
            }finally{worker.dispose();}
        }
    }
    @Test void duplicateManualCallbackSharesOneRealTransitionAndConflictsFail(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);f.repo.rows.add(claim(1));var received=new ArrayList<EventEnvelope>();Disposable worker=f.consumer().consume().subscribe(received::add);try{
                f.tick();var e=received.getFirst();StepVerifier.create(e.acknowledge()).verifyComplete();StepVerifier.create(e.acknowledge()).verifyComplete();assertThat(f.repo.acknowledged).isEqualTo(1);StepVerifier.create(e.reject(new RuntimeException("private"))).expectErrorMessage("Conflicting delivery callback").verify();assertThat(f.repo.rejected).isEmpty();
            }finally{worker.dispose();}
        }
    }
    @Test void cancellationOfManualOwnerRejectsInsteadOfAcknowledging(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);f.repo.rows.add(claim(1));Disposable worker=f.consumer().consume().subscribe();f.tick();worker.dispose();f.tick();assertThat(f.repo.acknowledged).isZero();assertThat(f.repo.rejected).containsExactly(FailureKind.LISTENER_FAILURE);
        }
    }
    @Test void dynamicListenerRefreshAlsoUpdatesActualListenCommands(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.start();f.tick();f.processor.registerListener("added","new-events",PublisherType.POSTGRES,(p,h)->Mono.empty());f.tick();String channel=PostgresChannelMapper.toChannel("new-events");verify(f.connection).createStatement("LISTEN \""+channel+"\"");
        }
    }
    @Test void manualCallbackFreesPermitBeforeNextAdmission(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);f.repo.rows.add(claim(1));f.repo.rows.add(claim(2));var received=new ArrayList<EventEnvelope>();Disposable worker=f.consumer().consume().subscribe(received::add);try{
                f.tick();assertThat(received).hasSize(1);StepVerifier.create(received.getFirst().acknowledge()).verifyComplete();f.tick();assertThat(received).hasSize(2);
            }finally{worker.dispose();}
        }
    }
    @Test void legacyConstructorFailsBeforeAnyFactoryAccess(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            var old=new PostgresEventConsumer(f.provider,f.processor,mock(org.fireflyframework.eda.serialization.MessageSerializer.class),f.properties,new com.fasterxml.jackson.databind.ObjectMapper());
            StepVerifier.create(old.start()).expectErrorMessage("PostgreSQL delivery repository and registered decoder are required").verify();verifyNoInteractions(f.provider);verifyNoInteractions(f.factory);
        }
    }
    @Test void enabledBeanIsNotAStartedReadyWorker(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            assertThat(f.consumer().isAvailable()).isTrue();assertThat(f.consumer().isRunning()).isFalse();StepVerifier.create(f.consumer().getHealth()).assertNext(h->assertThat(h.getStatus()).isEqualTo("DOWN")).verifyComplete();verifyNoInteractions(f.factory);
        }
    }
    @Test void healthRetainsProcessedCountsAfterActualFencedAcknowledgment(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.processor.registerListener("owner","events",PublisherType.POSTGRES,(p,h)->Mono.empty());f.repo.rows.add(claim(1));f.start();f.tick();f.awaitProcessed(1);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(()->{
                var health=f.consumer().getHealth().block(Duration.ofSeconds(1));assertThat(health.getMessagesConsumed()).isEqualTo(1L);assertThat(health.getMessagesProcessed()).isEqualTo(1L);assertThat(health.getMessagesFailures()).isZero();
            });
        }
    }
    @Test void queryFailureHealthIsFixedAndRecoversWithoutRawErrorData(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.repo.scriptedClaims.add(Mono.error(new IllegalStateException("RAW_PRIVATE_QUERY_SENTINEL")));f.start();f.tick();
            StepVerifier.create(f.consumer().getHealth()).assertNext(h->{assertThat(h.getStatus()).isEqualTo("DEGRADED");assertThat(h.getErrorMessage()).isEqualTo("DELIVERY_QUERY_FAILED");assertThat(h.toString()).doesNotContain("RAW_PRIVATE_QUERY_SENTINEL");}).verifyComplete();
            f.scheduler.advanceTimeBy(Duration.ofSeconds(1));StepVerifier.create(f.consumer().getHealth()).assertNext(h->assertThat(h.getStatus()).isEqualTo("UP")).verifyComplete();
        }
    }
    @Test void protocolWaitIsBoundedBeforeAnyListenOrClaimAccess(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.repo.protocol=Mono.never();StepVerifier.create(f.consumer().start()).then(()->f.scheduler.advanceTimeBy(Duration.ofSeconds(5))).expectError(java.util.concurrent.TimeoutException.class).verify(Duration.ofSeconds(1));verifyNoInteractions(f.factory);assertThat(f.repo.limits).isEmpty();
        }
    }
    @Test void hangingClaimIsCancelledAndRecoveryReleasesItsReservedPermit(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            var cancelled=new java.util.concurrent.atomic.AtomicBoolean();f.repo.scriptedClaims.add(Mono.<ClaimBatch>never().doOnCancel(()->cancelled.set(true)));f.start();f.tick();f.scheduler.advanceTimeBy(Duration.ofSeconds(5));assertThat(cancelled).isTrue();f.scheduler.advanceTimeBy(Duration.ofSeconds(1));assertThat(f.repo.limits).hasSize(2);
        }
    }
    @Test void stopDuringProtocolVerificationWaitsAndNeverActivatesAnOldRun(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            var protocol=Sinks.<Void>empty();f.repo.protocol=protocol.asMono();var stopped=new java.util.concurrent.atomic.AtomicBoolean();
            StepVerifier.create(f.consumer().start()).then(()->{
                f.consumer().stop().subscribe(v->{},e->{throw new AssertionError(e);},()->stopped.set(true));
                assertThat(stopped).isFalse();verifyNoInteractions(f.factory);
            }).then(()->protocol.tryEmitEmpty()).expectErrorMessage("Consumer stopped during startup").verify(Duration.ofSeconds(1));
            assertThat(stopped).isTrue();assertThat(f.consumer().isRunning()).isFalse();verifyNoInteractions(f.factory);
        }
    }
    @Test void selectedPgRequiresPositivePollBeforeResourceAccess(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.properties.getConsumer().getPostgres().get("default").setPollingInterval(Duration.ZERO);StepVerifier.create(f.consumer().start()).expectError(IllegalArgumentException.class).verify();verifyNoInteractions(f.factory);assertThat(f.repo.verifications).isZero();
        }
    }
    @Test void excessiveDynamicChannelsFailClosedWithoutStrandingStopOrPermits(){
        try(var f=new PostgresConsumerRuntimeTest.Fixture()){
            f.mode(EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);f.start();
            for(int i=0;i<257;i++)f.processor.registerListener("dynamic-"+i,"channel-"+i,PublisherType.POSTGRES,(p,h)->Mono.empty());
            f.tick();StepVerifier.create(f.consumer().getHealth()).assertNext(h->assertThat(h.getStatus()).isEqualTo("DEGRADED")).verifyComplete();
            StepVerifier.create(f.consumer().stop()).verifyComplete();assertThat(f.repo.claimed).isZero();
        }
    }

}
