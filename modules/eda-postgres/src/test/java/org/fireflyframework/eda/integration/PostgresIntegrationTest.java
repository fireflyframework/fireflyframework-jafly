/* Copyright 2024-2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.consumer.postgres.PostgresConsumerNativeFixture;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Disposable;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Real PostgreSQL publisher/consumer contracts with explicit decoder and operator migration. */
@Testcontainers
class PostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withPassword(UUID.randomUUID().toString()).withReuse(false);
    @Test void publishInsertsOutboxRow(){PostgresConsumerNativeFixture.run(postgres,false,f->{f.publish("outbox row");assertThat(f.rows()).hasSize(1);});}
    @Test void publisherIdentifiesItself(){PostgresConsumerNativeFixture.run(postgres,false,f->{assertThat(f.publisher.getPublisherType()).isEqualTo(PublisherType.POSTGRES);assertThat(f.publisher.isAvailable()).isTrue();StepVerifier.create(f.publisher.getHealth()).assertNext(h->{assertThat(h.getPublisherType()).isEqualTo(PublisherType.POSTGRES);assertThat(h.isAvailable()).isTrue();assertThat(h.getStatus()).isEqualTo("UP");}).verifyComplete();});}
    @Test void factoryReturnsPostgresPublisher(){PostgresConsumerNativeFixture.run(postgres,false,f->{var resolved=f.publisherFactory.getPublisher(PublisherType.POSTGRES,"default");assertThat(resolved).isNotNull();assertThat(resolved.getPublisherType()).isEqualTo(PublisherType.POSTGRES);assertThat(resolved.isAvailable()).isTrue();});}
    @Test void consumerProcessesPublishedEvents(){PostgresConsumerNativeFixture.run(postgres,false,f->{var seen=new CopyOnWriteArrayList<EventEnvelope>();Disposable observer=f.consumer.consume(f.destination).subscribe(seen::add);try{f.awaitListen();f.publish("via NOTIFY");f.awaitStatus("PROCESSED");await().atMost(Duration.ofSeconds(5)).until(()->!seen.isEmpty());}finally{observer.dispose();}});}
    @Test void channelColumnMatchesMapping(){PostgresConsumerNativeFixture.run(postgres,false,f->{f.publish("channel");assertThat(f.rows()).hasSize(1).allMatch(r->r.channel().equals(PostgresChannelMapper.toChannel(f.destination)));});}
}
