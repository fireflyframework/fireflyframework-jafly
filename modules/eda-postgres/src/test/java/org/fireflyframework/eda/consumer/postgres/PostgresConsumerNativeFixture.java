/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.fireflyframework.eda.annotation.EventListener;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.config.*;
import org.fireflyframework.eda.consumer.postgres.model.PostgresDeliveryRow;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.eda.publisher.postgres.PostgresEventPublisher;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Explicit operator setup and actual configured resources, only inside an owned native test. */
public final class PostgresConsumerNativeFixture implements AutoCloseable {
    public record NativeEvent(String value) { }
    public final PostgresEventConsumer consumer;
    public final PostgresEventPublisher publisher;
    public final EventPublisherFactory publisherFactory;
    public final Handler handler;
    public final ConnectionPool queryPool;
    public final AtomicReference<PostgresqlConnection> recordedListen;
    public final AtomicInteger listenConnections;
    private final ConnectionFactory inspectionFactory;
    private final SqlIdentifier relation;
    private final boolean externalPool;
    public final String destination;
    private PostgresConsumerNativeFixture(AssertableApplicationContext context,ConnectionFactory inspection,String schema,
            String destination,AtomicReference<PostgresqlConnection> listen,AtomicInteger creates,boolean externalPool){
        consumer=context.getBean(PostgresEventConsumer.class);publisher=context.getBean(PostgresEventPublisher.class);publisherFactory=context.getBean(EventPublisherFactory.class);handler=context.getBean(Handler.class);
        queryPool=context.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionPool.class);inspectionFactory=inspection;relation=SqlIdentifier.from(SqlIdentifier.quoted(schema),SqlIdentifier.quoted("firefly_eda_outbox"));this.destination=destination;recordedListen=listen;listenConnections=creates;this.externalPool=externalPool;
        assertThat(consumer.isRunning()).isFalse();
    }
    public static void run(PostgreSQLContainer<?> postgres,boolean manual,Consumer<PostgresConsumerNativeFixture> action){run(postgres,manual,false,action);}
    public static void run(PostgreSQLContainer<?> postgres,boolean manual,boolean observeListen,Consumer<PostgresConsumerNativeFixture> action){
        String schema="c_native_"+UUID.randomUUID().toString().replace("-","");String destination="event-"+UUID.randomUUID();
        var raw=new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder().host(postgres.getHost()).port(postgres.getFirstMappedPort()).database(postgres.getDatabaseName()).username(postgres.getUsername()).password(postgres.getPassword()).connectTimeout(Duration.ofSeconds(5)).build());
        var listen=new AtomicReference<PostgresqlConnection>();var creates=new AtomicInteger();ConnectionPool external=observeListen?new ConnectionPool(ConnectionPoolConfiguration.builder(raw).initialSize(0).maxSize(2).maxAcquireTime(Duration.ofSeconds(5)).maxCreateConnectionTime(Duration.ofSeconds(5)).acquireRetry(0).build()):null;
        var runner=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FireflyEdaPostgresPublisherAutoConfiguration.class,FireflyEdaPostgresConsumerAutoConfiguration.class)).withUserConfiguration(Dependencies.class)
                .withPropertyValues("firefly.eda.enabled=true","firefly.eda.consumer.enabled=true","firefly.eda.consumer.concurrency=2",
                        "firefly.eda.publishers.enabled=true","firefly.eda.publishers.postgres.default.enabled=true","firefly.eda.publishers.postgres.default.auto-create-schema=true",
                        "firefly.eda.publishers.postgres.default.host="+postgres.getHost(),"firefly.eda.publishers.postgres.default.port="+postgres.getFirstMappedPort(),"firefly.eda.publishers.postgres.default.database="+postgres.getDatabaseName(),"firefly.eda.publishers.postgres.default.username="+postgres.getUsername(),"firefly.eda.publishers.postgres.default.password="+postgres.getPassword(),"firefly.eda.publishers.postgres.default.schema="+schema,
                        "firefly.eda.consumer.postgres.default.enabled=true","firefly.eda.consumer.postgres.default.host="+postgres.getHost(),"firefly.eda.consumer.postgres.default.port="+postgres.getFirstMappedPort(),"firefly.eda.consumer.postgres.default.database="+postgres.getDatabaseName(),"firefly.eda.consumer.postgres.default.username="+postgres.getUsername(),"firefly.eda.consumer.postgres.default.password="+postgres.getPassword(),"firefly.eda.consumer.postgres.default.schema="+schema,
                        "firefly.eda.consumer.postgres.default.channels="+destination,"firefly.eda.consumer.postgres.default.polling-interval=PT0.05S","firefly.eda.consumer.postgres.default.max-pool-size=2","firefly.eda.consumer.postgres.default.lease-duration=PT3S","firefly.eda.consumer.postgres.default.handler-timeout=PT2S","firefly.eda.consumer.postgres.default.retry-delay=PT0.1S","firefly.eda.consumer.postgres.default.delivery-mode="+(manual?"MANUAL":"ANNOTATED"));
        if(observeListen){
            ConnectionFactory recorded=new ConnectionFactory(){public Publisher<? extends Connection> create(){return raw.create().doOnNext(c->{listen.set(c);creates.incrementAndGet();});}public ConnectionFactoryMetadata getMetadata(){return raw.getMetadata();}};
            runner=runner.withBean("fireflyEdaPostgresConsumerConnectionFactory",ConnectionFactory.class,()->recorded,b->((AbstractBeanDefinition)b).setDefaultCandidate(false))
                    .withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->external,b->{((AbstractBeanDefinition)b).setDefaultCandidate(false);((AbstractBeanDefinition)b).setDestroyMethodName("");});
        }
        try {
            runner.run(context->{
                assertThat(context).hasNotFailed().hasSingleBean(PostgresEventConsumer.class).hasSingleBean(PostgresDeliveryRepository.class);
                // Explicit fixture/operator migration, never a runtime auto-install path.
                Mono.usingWhen(raw.create(),c->Flux.from(c.createStatement(PostgresDeliverySchema.installationSql(schema,"firefly_eda_outbox")).execute()).concatMap(r->r.getRowsUpdated()).then(),Connection::close).block(Duration.ofSeconds(10));
                context.getBean(PostgresDeliveryRepository.class).verifyProtocol().block(Duration.ofSeconds(5));
                try(var fixture=new PostgresConsumerNativeFixture(context,raw,schema,destination,listen,creates,observeListen)){action.accept(fixture);}
            });
        } finally {if(external!=null)external.disposeLater().block(Duration.ofSeconds(5));}
    }
    public void start(){consumer.start().block(Duration.ofSeconds(10));assertThat(consumer.isRunning()).isTrue();}
    public void publish(String value){publisher.publish(new NativeEvent(value),destination,Map.of("integer",7)).block(Duration.ofSeconds(5));}
    public List<PostgresDeliveryRow> rows(){return new R2dbcEntityTemplate(DatabaseClient.create(inspectionFactory),PostgresDialect.INSTANCE).select(PostgresDeliveryRow.class).from(relation).matching(Query.empty()).all().collectList().block(Duration.ofSeconds(5));}
    public void awaitStatus(String status){await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(rows()).anyMatch(r->status.equals(r.status())));}
    public void awaitListen(){await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(consumer.getHealth().block(Duration.ofSeconds(1)).getDetails().get("listenConnected")).isEqualTo(true));}
    public void close(){consumer.stop().block(Duration.ofSeconds(10));if(!externalPool)assertThat(queryPool.isDisposed()).isFalse();}
    private static String quote(String value){return "\""+value.replace("\"","\"\"")+"\"";}
    static class Dependencies {
        @Bean ObjectMapper objectMapper(){return new ObjectMapper();}
        @Bean MessageSerializer messageSerializer(ObjectMapper mapper){return new org.fireflyframework.eda.serialization.JsonMessageSerializer(mapper);}
        @Bean org.fireflyframework.eda.error.CustomErrorHandlerRegistry customErrorHandlerRegistry(){return new org.fireflyframework.eda.error.CustomErrorHandlerRegistry();}
        @Bean org.fireflyframework.eda.listener.EventListenerProcessor eventListenerProcessor(org.springframework.context.ApplicationContext context,org.springframework.context.ApplicationEventPublisher publisher,org.fireflyframework.eda.error.CustomErrorHandlerRegistry registry,org.springframework.core.env.Environment environment){return new org.fireflyframework.eda.listener.EventListenerProcessor(context,publisher,registry,environment);}
        @Bean EventPublisherFactory eventPublisherFactory(List<org.fireflyframework.eda.publisher.EventPublisher> publishers,org.fireflyframework.eda.properties.EdaProperties properties,org.springframework.beans.factory.ObjectProvider<org.fireflyframework.eda.resilience.ResilientEventPublisherFactory> resilience){return new EventPublisherFactory(publishers,properties,resilience);}

        @Bean PostgresEventDecoder postgresEventDecoder(MessageSerializer serializer){return new org.fireflyframework.eda.consumer.postgres.impl.RegisteredPostgresEventDecoder(serializer,Map.of(NativeEvent.class.getName(),NativeEvent.class,String.class.getName(),String.class));}
        @Bean Handler nativeHandler(){return new Handler();}
    }
    public static final class Handler {
        public final AtomicInteger calls=new AtomicInteger();
        public final AtomicReference<BiFunction<NativeEvent,Map<String,Object>,Mono<Void>>> action=new AtomicReference<>((e,h)->Mono.empty());
        @EventListener(destinations="${firefly.eda.consumer.postgres.default.channels}",consumerType=PublisherType.POSTGRES,async=false)
        public Mono<Void> on(NativeEvent event,Map<String,Object> headers){calls.incrementAndGet();return action.get().apply(event,headers);}
    }
}
