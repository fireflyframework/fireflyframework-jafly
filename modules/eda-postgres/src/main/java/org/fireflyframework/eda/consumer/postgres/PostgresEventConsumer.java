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

package org.fireflyframework.eda.consumer.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import org.fireflyframework.eda.consumer.ConsumerHealth;
import org.fireflyframework.eda.consumer.EventConsumer;
import org.fireflyframework.eda.consumer.postgres.impl.PostgresDeliveryRuntime;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.listener.EventListenerProcessor;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** A single processing owner over committed PostgreSQL delivery claims. */
public class PostgresEventConsumer implements EventConsumer {
    private final PostgresDeliveryRuntime runtime;
    public PostgresEventConsumer(@Qualifier("fireflyEdaPostgresConsumerConnectionFactory") ObjectProvider<ConnectionFactory> provider,
            EventListenerProcessor processor, MessageSerializer serializer, EdaProperties properties, ObjectMapper mapper) {
        this(provider,processor,serializer,properties,mapper,null,null);
    }
    public PostgresEventConsumer(ObjectProvider<ConnectionFactory> provider, EventListenerProcessor processor,
            MessageSerializer serializer, EdaProperties properties, ObjectMapper mapper,
            PostgresDeliveryRepository repository, PostgresEventDecoder decoder) {
        this(provider,processor,serializer,properties,mapper,repository,decoder,null);
    }
    PostgresEventConsumer(ObjectProvider<ConnectionFactory> provider, EventListenerProcessor processor,
            MessageSerializer serializer, EdaProperties properties, ObjectMapper mapper,
            PostgresDeliveryRepository repository, PostgresEventDecoder decoder, Scheduler scheduler) {
        runtime=new PostgresDeliveryRuntime(provider,processor,properties,mapper,repository,decoder,scheduler);
    }
    @Override public Flux<EventEnvelope> consume(){return runtime.consume();}
    @Override public Flux<EventEnvelope> consume(String... destinations){return runtime.consume(destinations);}
    @Override public Mono<Void> start(){return runtime.start();}
    @Override public Mono<Void> stop(){return runtime.stop();}
    @Override public boolean isRunning(){return runtime.isRunning();}
    @Override public boolean isAvailable(){return runtime.isAvailable();}
    @Override public String getConsumerType(){return "POSTGRES";}
    @Override public Mono<ConsumerHealth> getHealth(){return runtime.health();}
    @PreDestroy public void shutdown(){runtime.shutdown();}
}
