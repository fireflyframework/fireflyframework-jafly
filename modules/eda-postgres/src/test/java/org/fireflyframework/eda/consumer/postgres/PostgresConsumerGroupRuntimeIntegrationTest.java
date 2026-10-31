/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.fireflyframework.eda.consumer.postgres.model.GroupSubscription;
import org.fireflyframework.eda.event.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class PostgresConsumerGroupRuntimeIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withPassword(UUID.randomUUID().toString()).withReuse(false);
    @ParameterizedTest @ValueSource(strings={"PROTOCOL","MISSING","CHANNELS","ATTEMPTS"})
    void configuredGroupRefusesReadinessBeforeListenWhenOperatorPolicyIsMissingOrIncompatible(String condition){
        var fixture=new PostgresConsumerGroupNativeFixture(postgres);
        fixture.open("group-A",false,worker->{
            if(!condition.equals("PROTOCOL"))fixture.install();
            if(condition.equals("CHANNELS"))worker.register(new GroupSubscription(Set.of("other-channel"),3));
            if(condition.equals("ATTEMPTS"))worker.register(new GroupSubscription(Set.of(org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper.toChannel(fixture.destination)),4));
            StepVerifier.create(worker.consumer.start()).expectError(IllegalStateException.class).verify(Duration.ofSeconds(5));
            assertThat(worker.consumer.isRunning()).isFalse();assertThat(worker.listens).hasValue(0);assertThat(worker.handler.calls).hasValue(0);
            assertThat(worker.outbox()).isEmpty();assertThat(worker.queryPool.getMetrics().orElseThrow().acquiredSize()).isZero();
        });
    }
    @Test void annotatedGroupProcessingLeavesGlobalDeliveryStateUntouched(){
        var fixture=new PostgresConsumerGroupNativeFixture(postgres);
        fixture.open("group-A",false,worker->{
            fixture.install();worker.register();worker.publish();var original=worker.outbox().getFirst();worker.start();worker.awaitProcessed();
            assertThat(worker.handler.calls).hasValue(1);var retained=worker.outbox().getFirst();
            assertThat(retained.status()).isEqualTo("PENDING");assertThat(retained.attempts()).isZero();assertThat(retained.payload()).containsExactly(original.payload());assertThat(retained.leaseToken()).isNull();
        });
    }
    @Test void manualGroupCallbacksPersistOnlyTheGroupTransition(){
        var fixture=new PostgresConsumerGroupNativeFixture(postgres);
        fixture.open("group-A",true,worker->{
            fixture.install();worker.register();var received=new CopyOnWriteArrayList<EventEnvelope>();
            var subscription=worker.consumer.consume().subscribe(received::add);
            try{worker.publish();await().atMost(Duration.ofSeconds(5)).until(()->received.size()==1);received.getFirst().acknowledge().block(Duration.ofSeconds(5));worker.awaitProcessed();
                assertThat(worker.handler.calls).hasValue(0);assertThat(worker.outbox()).allMatch(row->row.status().equals("PENDING")&&row.attempts()==0);
            }finally{subscription.dispose();}
        });
    }
    @Test void distinctConfiguredApplicationsEachProcessTheSameRetainedEvent(){
        var fixture=new PostgresConsumerGroupNativeFixture(postgres);
        fixture.open("app-A",false,first->{fixture.install();first.register();first.start();
            fixture.open("app-B",false,second->{second.register();second.start();first.publish();first.awaitProcessed();second.awaitProcessed();
                assertThat(first.handler.calls).hasValue(1);assertThat(second.handler.calls).hasValue(1);assertThat(first.outbox()).allMatch(row->row.status().equals("PENDING")&&row.attempts()==0);
            });
        });
    }
    @Test void replicasWithTheSameConfiguredGroupCompeteForOneDelivery(){
        var fixture=new PostgresConsumerGroupNativeFixture(postgres);
        fixture.open("shared-app",false,first->{fixture.install();first.register();first.start();
            fixture.open("shared-app",false,second->{second.start();first.publish();first.awaitProcessed();
                await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(()->assertThat(first.handler.calls.get()+second.handler.calls.get()).isEqualTo(1));
                assertThat(first.deliveries()).hasSize(1);assertThat(second.deliveries()).hasSize(1);
            });
        });
    }
}
