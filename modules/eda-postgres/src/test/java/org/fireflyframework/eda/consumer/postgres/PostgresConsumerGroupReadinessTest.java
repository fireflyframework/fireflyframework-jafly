/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.event.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import org.reactivestreams.Subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostgresConsumerGroupReadinessTest {
    @ParameterizedTest @ValueSource(strings={"CHANNELS","ATTEMPTS","MISSING","CATALOG"})
    void incompatibleGroupReadinessRefusesStartBeforeListenOrClaims(String mismatch){
        var database=new SpringDataPostgresConsumerGroupRepositoryTest();var query=database.session();
        query.registration.put("channels",new String[]{PostgresChannelMapper.toChannel("events")});
        if(mismatch.equals("CHANNELS"))query.registration.put("channels",new String[]{"different-channel"});
        if(mismatch.equals("ATTEMPTS"))query.registration.put("max_attempts",4);
        if(mismatch.equals("MISSING"))query.registration=null;
        if(mismatch.equals("CATALOG"))query.compatible=false;
        try(var runtime=new PostgresConsumerRuntimeTest.Fixture()){
            runtime.current=new PostgresEventConsumerImpl(runtime.provider,runtime.processor,mock(MessageSerializer.class),runtime.properties,new ObjectMapper(),database.repository(),(bytes,type,event)->"payload",runtime.scheduler);
            StepVerifier.create(runtime.current.start()).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));
            assertThat(runtime.current.isRunning()).isFalse();verifyNoInteractions(runtime.factory);
            assertThat(query.statements).noneMatch(statement->statement.sql.contains("missing AS MATERIALIZED")||statement.sql.contains("claim_rows AS"));
            assertThat(query.rolledBack).hasValue(1);assertThat(query.closed).hasValue(1);
        }
    }
    @Test void legacyNoArgumentProtocolAdapterRemainsCompatibleWithRequestAwareStartup(){
        var legacy=new PostgresConsumerRuntimeTest.RecordingRepository();
        StepVerifier.create(legacy.verifyProtocol(new ClaimRequest(Set.of("events"),1,3,Duration.ofSeconds(20)))).verifyComplete();
        assertThat(legacy.verifications).isEqualTo(1);assertThat(legacy.limits).isEmpty();
    }
    @Test void startupPassesTheActualUnionAttemptLimitBatchAndLeaseBeforeAdmission(){
        try(var runtime=new PostgresConsumerRuntimeTest.Fixture()){
            var captured=new AtomicReference<ClaimRequest>();var ready=Sinks.<Void>empty();
            var repository=new PostgresDeliveryRepository(){
                public Mono<Void> verifyProtocol(){return Mono.error(new AssertionError("Request-aware readiness must be used"));}
                public Mono<Void> verifyProtocol(ClaimRequest request){captured.set(request);return ready.asMono();}
                public Mono<ClaimBatch> claim(ClaimRequest request){return runtime.repo.claim(request);}
                public Mono<Long> reapExhausted(ClaimRequest request){return runtime.repo.reapExhausted(request);}
                public Mono<TransitionResult> acknowledge(DeliveryClaim claim){return runtime.repo.acknowledge(claim);}
                public Mono<TransitionResult> reject(DeliveryClaim claim,FailureKind kind,Duration delay){return runtime.repo.reject(claim,kind,delay);}
            };
            var config=runtime.properties.getConsumer().getPostgres().get("default");
            config.setChannels("configured-topic");config.setMaxAttempts(5);config.setBatchSize(7);config.setLeaseDuration(Duration.ofSeconds(8));config.setHandlerTimeout(Duration.ofSeconds(8));
            runtime.processor.registerListener("listener","listener-topic",PublisherType.POSTGRES,(payload,headers)->Mono.empty());
            runtime.current=new PostgresEventConsumerImpl(runtime.provider,runtime.processor,mock(MessageSerializer.class),runtime.properties,new ObjectMapper(),repository,(bytes,type,event)->"payload",runtime.scheduler);
            StepVerifier.create(runtime.current.start()).then(()->{
                assertThat(captured.get()).isEqualTo(new ClaimRequest(Set.of("firefly_eda_configured_topic","firefly_eda_listener_topic"),7,5,Duration.ofSeconds(8)));
                verifyNoInteractions(runtime.factory);assertThat(runtime.current.isRunning()).isFalse();
            }).then(()->ready.tryEmitEmpty()).verifyComplete();
        }
    }
    @Test void newlyRegisteredChannelsCannotMaterializeOrClaimAgainstTheImmutableGroupPolicy(){
        var database=new SpringDataPostgresConsumerGroupRepositoryTest();var startup=database.session();
        startup.registration.put("channels",new String[]{"firefly_eda_events"});
        try(var runtime=new PostgresConsumerRuntimeTest.Fixture()){
            runtime.mode(org.fireflyframework.eda.properties.EdaProperties.Consumer.PostgresConfig.DeliveryMode.MANUAL);
            runtime.current=new PostgresEventConsumerImpl(runtime.provider,runtime.processor,mock(MessageSerializer.class),runtime.properties,new ObjectMapper(),database.repository(),(bytes,type,event)->"payload",runtime.scheduler);
            StepVerifier.create(runtime.current.start()).verifyComplete();
            var changed=database.session();changed.registration.put("channels",new String[]{"firefly_eda_events"});
            runtime.processor.registerListener("late","new-topic",PublisherType.POSTGRES,(payload,headers)->Mono.empty());
            var worker=new BaseSubscriber<EventEnvelope>(){protected void hookOnSubscribe(Subscription subscription){subscription.request(1);}};
            runtime.current.consume().subscribe(worker);
            try{
                runtime.tick();assertThat(runtime.current.getHealth().block().getStatus()).isEqualTo("DEGRADED");
                assertThat(changed.rolledBack).hasValue(1);assertThat(changed.closed).hasValue(1);
                assertThat(changed.statements).noneMatch(statement->statement.sql.contains("missing AS MATERIALIZED")||statement.sql.contains("claim_rows AS"));
            }finally{worker.dispose();}
        }
    }
    @Test void nullRequestAwareReadinessRefusesBeforeAcquisition(){
        var database=new SpringDataPostgresConsumerGroupRepositoryTest();
        StepVerifier.create(database.repository().verifyProtocol(null)).expectError(IllegalArgumentException.class).verify();
        assertThat(database.acquired).hasValue(0);
    }
}
