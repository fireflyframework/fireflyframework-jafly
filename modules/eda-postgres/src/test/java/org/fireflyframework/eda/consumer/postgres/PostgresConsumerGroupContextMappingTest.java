/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.config.FireflyEdaPostgresConsumerAutoConfiguration;
import org.fireflyframework.eda.consumer.postgres.impl.RegisteredPostgresEventDecoderImpl;
import org.fireflyframework.eda.consumer.postgres.model.ClaimRequest;
import org.fireflyframework.eda.error.CustomErrorHandlerRegistry;
import org.fireflyframework.eda.listener.EventListenerProcessorImpl;
import org.fireflyframework.eda.serialization.JsonMessageSerializerImpl;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostgresConsumerGroupContextMappingTest {
    private ApplicationContextRunner runner(ConnectionFactory query,ConnectionFactory listen){
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FireflyEdaPostgresConsumerAutoConfiguration.class))
                .withUserConfiguration(Dependencies.class)
                .withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->query,bean->{((AbstractBeanDefinition)bean).setDefaultCandidate(false);((AbstractBeanDefinition)bean).setDestroyMethodName("");})
                .withBean("fireflyEdaPostgresConsumerConnectionFactory",ConnectionFactory.class,()->listen,bean->{((AbstractBeanDefinition)bean).setDefaultCandidate(false);((AbstractBeanDefinition)bean).setDestroyMethodName("");})
                .withPropertyValues("firefly.eda.consumer.enabled=true","firefly.eda.consumer.postgres.default.enabled=true",
                        "firefly.eda.consumer.postgres.default.group-id=group-A","firefly.eda.consumer.postgres.default.delivery-mode=MANUAL");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void configuredRelationsReachTheActualSpringDataCatalogAndMappedRegistrationRead(boolean custom){
        var database=new SpringDataPostgresConsumerGroupRepositoryTest();var query=database.session();var listen=mock(ConnectionFactory.class);
        String schema=custom?"custom_schema":"public",outbox=custom?"custom_outbox":"firefly_eda_outbox";
        String registry=custom?"custom_registry":"firefly_eda_group_registry",deliveries=custom?"custom_deliveries":"firefly_eda_group_deliveries",redrives=custom?"custom_redrives":"firefly_eda_group_redrives";
        var configured=runner(database.factory,listen);
        if(custom)configured=configured.withPropertyValues("firefly.eda.consumer.postgres.default.schema=custom_schema","firefly.eda.consumer.postgres.default.outbox-table=custom_outbox",
                "firefly.eda.consumer.postgres.default.group-registry-table=custom_registry","firefly.eda.consumer.postgres.default.group-deliveries-table=custom_deliveries","firefly.eda.consumer.postgres.default.group-redrives-table=custom_redrives");
        configured.run(context->{
            assertThat(context).hasNotFailed();var repository=context.getBean(PostgresDeliveryRepository.class);
            StepVerifier.create(repository.verifyProtocol(new ClaimRequest(Set.of("events"),1,3,Duration.ofSeconds(20)))).verifyComplete();
            var catalog=query.statements.stream().filter(statement->statement.sql.contains("pg_catalog.pg_class")).findFirst().orElseThrow();
            assertThat(catalog.bindings.values()).contains(schema,outbox,registry,deliveries,redrives);
            assertThat(query.statements).anyMatch(statement->statement.sql.startsWith("SELECT")&&statement.sql.contains("\""+schema+"\".\""+registry+"\""));
            assertThat(query.statements).noneMatch(statement->statement.sql.startsWith("INSERT")||statement.sql.startsWith("UPDATE")||statement.sql.startsWith("CREATE"));
            assertThat(query.committed).hasValue(1);assertThat(query.closed).hasValue(1);verifyNoInteractions(listen);
            assertThat(context.getBean(PostgresEventConsumerImpl.class).isRunning()).isFalse();
        });
    }
    @Test void startupValidatesConfiguredAndRegisteredChannelsAndPolicyBeforeOpeningListen(){
        var database=new SpringDataPostgresConsumerGroupRepositoryTest();var query=database.session();
        query.registration.put("channels",new String[]{"firefly_eda_configured_topic","firefly_eda_listener_topic"});query.registration.put("max_attempts",5);
        var listen=mock(ConnectionFactory.class);var subscriptions=new AtomicInteger();
        doReturn(Mono.defer(()->{
            assertThat(query.committed).hasValue(1);assertThat(query.closed).hasValue(1);subscriptions.incrementAndGet();return Mono.never();
        })).when(listen).create();
        runner(database.factory,listen).withPropertyValues("firefly.eda.consumer.postgres.default.channels=configured-topic",
                "firefly.eda.consumer.postgres.default.max-attempts=5","firefly.eda.consumer.postgres.default.lease-duration=PT8S",
                "firefly.eda.consumer.postgres.default.handler-timeout=PT8S").run(context->{
            assertThat(context).hasNotFailed();context.getBean(EventListenerProcessorImpl.class).registerListener("listener","listener-topic",PublisherType.POSTGRES,(payload,headers)->Mono.empty());
            var consumer=context.getBean(PostgresEventConsumerImpl.class);
            StepVerifier.create(consumer.start()).verifyComplete();assertThat(consumer.isRunning()).isTrue();assertThat(subscriptions).hasValue(1);
            assertThat(query.statements).noneMatch(statement->statement.sql.contains("missing AS MATERIALIZED")||statement.sql.contains("claim_rows AS"));
            StepVerifier.create(consumer.stop()).verifyComplete();assertThat(consumer.isRunning()).isFalse();assertThat(database.acquired).hasValue(1);
        });
    }
    static class Dependencies {
        @Bean ObjectMapper objectMapper(){return new ObjectMapper();}
        @Bean MessageSerializer messageSerializer(ObjectMapper mapper){return new JsonMessageSerializerImpl(mapper);}
        @Bean PostgresEventDecoder decoder(MessageSerializer serializer){return new RegisteredPostgresEventDecoderImpl(serializer,Map.of("java.lang.String",String.class));}
        @Bean EventListenerProcessorImpl processor(ApplicationContext context,ApplicationEventPublisher publisher,Environment environment){return new EventListenerProcessorImpl(context,publisher,mock(CustomErrorHandlerRegistry.class),environment);}
    }
}
