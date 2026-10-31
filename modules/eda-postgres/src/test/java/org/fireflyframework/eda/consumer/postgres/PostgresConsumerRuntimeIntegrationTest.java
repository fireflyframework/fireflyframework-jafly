/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.eda.event.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
class PostgresConsumerRuntimeIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withPassword(UUID.randomUUID().toString()).withReuse(false);
    @Test void actualWiringIsLazyUntilExplicitlyStarted(){PostgresConsumerNativeFixture.run(postgres,false,f->{assertThat(f.queryPool.getMetrics().orElseThrow().allocatedSize()).isZero();assertThat(f.consumer.isRunning()).isFalse();f.start();f.awaitListen();});}
    @Test void annotatedCompletionWaitsAndHoldsNoQueryConnection(){PostgresConsumerNativeFixture.run(postgres,false,f->{var gate=Sinks.<Void>empty();f.handler.action.set((e,h)->gate.asMono());f.start();f.publish("held");await().atMost(Duration.ofSeconds(5)).until(()->f.handler.calls.get()==1);assertThat(f.rows()).allMatch(r->r.status().equals("IN_FLIGHT"));await().atMost(Duration.ofSeconds(5)).until(()->f.queryPool.getMetrics().orElseThrow().acquiredSize()==0);gate.tryEmitEmpty();f.awaitStatus("PROCESSED");});}
    @Test void observerCannotAcknowledgeTheRealClaim(){PostgresConsumerNativeFixture.run(postgres,false,f->{var observed=new CopyOnWriteArrayList<EventEnvelope>();Disposable observer=f.consumer.consume().subscribe(observed::add);try{f.publish("observed");f.awaitStatus("PROCESSED");await().atMost(Duration.ofSeconds(5)).until(()->!observed.isEmpty());StepVerifier.create(observed.getFirst().acknowledge()).expectErrorMessage("Observation deliveries cannot change transport state").verify();}finally{observer.dispose();}});}
    @Test void manualDemandGatesRealClaimsAndCallbacksPersist(){PostgresConsumerNativeFixture.run(postgres,true,f->{var received=new CopyOnWriteArrayList<EventEnvelope>();var worker=new BaseSubscriber<EventEnvelope>(){protected void hookOnSubscribe(Subscription s){}protected void hookOnNext(EventEnvelope e){received.add(e);}};f.consumer.consume().subscribe(worker);try{f.publish("manual");await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(()->assertThat(f.rows()).allMatch(r->r.attempts()==0));worker.request(1);await().atMost(Duration.ofSeconds(5)).until(()->received.size()==1);assertThat(f.handler.calls).hasValue(0);received.getFirst().acknowledge().block(Duration.ofSeconds(5));f.awaitStatus("PROCESSED");}finally{worker.dispose();}});}
    @Test void manualSecondSubscriberIsRejected(){PostgresConsumerNativeFixture.run(postgres,true,f->{Disposable first=f.consumer.consume().subscribe();try{StepVerifier.create(f.consumer.consume()).expectErrorMessage("A manual processing subscriber is already registered").verify();}finally{first.dispose();}});}
    @Test void manualCancellationCannotAutoAcknowledge(){PostgresConsumerNativeFixture.run(postgres,true,f->{var received=new CopyOnWriteArrayList<EventEnvelope>();Disposable worker=f.consumer.consume().subscribe(received::add);f.publish("cancel");await().atMost(Duration.ofSeconds(5)).until(()->received.size()==1);worker.dispose();f.awaitStatus("PENDING");assertThat(f.rows()).noneMatch(r->r.status().equals("PROCESSED"));assertThat(f.handler.calls).hasValue(0);});}
    @Test void unknownWireClassReachesTypedDurableDeadLetter(){PostgresConsumerNativeFixture.run(postgres,false,f->{f.start();f.publisher.publish(Map.of("unknown","class"),f.destination,Map.of()).block(Duration.ofSeconds(5));f.awaitStatus("DEAD_LETTER");assertThat(f.rows()).allSatisfy(r->{assertThat(r.attempts()).isEqualTo(3);assertThat(r.errorMessage()).contains("DESERIALIZATION_FAILURE");});assertThat(f.handler.calls).hasValue(0);});}
    @Test void retryPersistsAnotherAttemptBeforeSuccess(){PostgresConsumerNativeFixture.run(postgres,false,f->{f.handler.action.set((e,h)->f.handler.calls.get()==1?Mono.error(new IllegalStateException("synthetic failure")):Mono.empty());f.start();f.publish("retry");f.awaitStatus("PROCESSED");assertThat(f.rows()).allMatch(r->r.attempts()==2);});}
    @Test void stoppedWorkerDoesNotClaimAndExplicitRestartRecovers(){PostgresConsumerNativeFixture.run(postgres,false,f->{f.start();f.consumer.stop().block(Duration.ofSeconds(5));f.publish("offline");await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(()->assertThat(f.rows()).allMatch(r->r.attempts()==0));f.start();f.awaitStatus("PROCESSED");});}
    @Test void lateManualCallbackCannotMutateAReplacementAttempt(){PostgresConsumerNativeFixture.run(postgres,true,f->{var received=new CopyOnWriteArrayList<EventEnvelope>();Disposable worker=f.consumer.consume().subscribe(received::add);try{f.publish("late");await().atMost(Duration.ofSeconds(8)).until(()->received.size()>=2);long generation=f.rows().getFirst().leaseGeneration();StepVerifier.create(received.getFirst().acknowledge()).expectError().verify();assertThat(f.rows().getFirst().leaseGeneration()).isEqualTo(generation);assertThat(f.rows()).noneMatch(r->r.status().equals("PROCESSED"));}finally{worker.dispose();}});}
    @Test void listenReconnectDoesNotReplacePollingRecovery(){PostgresConsumerNativeFixture.run(postgres,false,true,f->{f.start();f.awaitListen();f.recordedListen.get().close().block(Duration.ofSeconds(5));f.publish("listen outage");f.awaitStatus("PROCESSED");await().atMost(Duration.ofSeconds(5)).until(()->f.listenConnections.get()>=2);f.awaitListen();});}
    @Test void burstRespectsSharedDeliveryPermitsAndReleasesQueryConnections(){PostgresConsumerNativeFixture.run(postgres,false,f->{var gate=Sinks.<Void>empty();f.handler.action.set((e,h)->gate.asMono());f.start();for(int i=0;i<10;i++)f.publish("burst-"+i);await().atMost(Duration.ofSeconds(5)).until(()->f.handler.calls.get()==2);await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(()->assertThat(f.handler.calls).hasValue(2));assertThat(f.rows().stream().filter(r->r.status().equals("IN_FLIGHT")).count()).isEqualTo(2);assertThat(f.queryPool.getMetrics().orElseThrow().acquiredSize()).isZero();gate.tryEmitEmpty();await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(f.rows()).allMatch(r->r.status().equals("PROCESSED")));});}
}
