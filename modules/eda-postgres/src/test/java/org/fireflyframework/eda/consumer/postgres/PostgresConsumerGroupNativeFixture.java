/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.fireflyframework.eda.config.*;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.publisher.postgres.PostgresEventPublisher;
import org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Explicit operator setup and owned resources for intended group-runtime PostgreSQL tests. */
public final class PostgresConsumerGroupNativeFixture {
    final PostgresqlConnectionFactory raw;
    final ConsumerGroupTables tables;
    final String destination="group-event-"+UUID.randomUUID();
    private final PostgreSQLContainer<?> postgres;
    private boolean first=true;
    PostgresConsumerGroupNativeFixture(PostgreSQLContainer<?> postgres){
        this.postgres=postgres;
        raw=new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder().host(postgres.getHost()).port(postgres.getFirstMappedPort()).database(postgres.getDatabaseName()).username(postgres.getUsername()).password(postgres.getPassword()).connectTimeout(Duration.ofSeconds(5)).build());
        tables=new ConsumerGroupTables("group_native_"+UUID.randomUUID().toString().replace("-",""),"firefly_eda_outbox","runtime_registry","runtime_deliveries","runtime_redrives");
        execute("CREATE SCHEMA "+quote(tables.schema()));
    }
    void execute(String sql){Mono.usingWhen(raw.create(),connection->Flux.from(connection.createStatement(sql).execute()).concatMap(result->result.getRowsUpdated()).then(),Connection::close).block(Duration.ofSeconds(10));}
    void install(){execute(PostgresDeliverySchema.installationSql(tables.schema(),tables.outbox()));execute(PostgresConsumerGroupSchema.installationSql(tables));}
    void open(String group,boolean manual,Consumer<Worker> action){
        var pool=new ConnectionPool(ConnectionPoolConfiguration.builder(raw).initialSize(0).maxSize(2).maxAcquireTime(Duration.ofSeconds(5)).maxCreateConnectionTime(Duration.ofSeconds(5)).acquireRetry(0).build());
        var listens=new AtomicInteger();
        ConnectionFactory listen=new ConnectionFactory(){
            public Publisher<? extends Connection> create(){return Mono.defer(()->{listens.incrementAndGet();return raw.create();});}
            public ConnectionFactoryMetadata getMetadata(){return raw.getMetadata();}
        };
        boolean bootstrap=first;first=false;
        var runner=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FireflyEdaPostgresPublisherAutoConfiguration.class,FireflyEdaPostgresConsumerAutoConfiguration.class))
                .withUserConfiguration(PostgresConsumerNativeFixture.Dependencies.class)
                .withBean("fireflyEdaPostgresConsumerConnectionFactory",ConnectionFactory.class,()->listen,bean->{((AbstractBeanDefinition)bean).setDefaultCandidate(false);((AbstractBeanDefinition)bean).setDestroyMethodName("");})
                .withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->pool,bean->{((AbstractBeanDefinition)bean).setDefaultCandidate(false);((AbstractBeanDefinition)bean).setDestroyMethodName("");})
                .withPropertyValues("firefly.eda.enabled=true","firefly.eda.consumer.enabled=true","firefly.eda.publishers.enabled=true",
                        "firefly.eda.publishers.postgres.default.enabled=true","firefly.eda.publishers.postgres.default.auto-create-schema="+bootstrap,
                        "firefly.eda.publishers.postgres.default.host="+postgres.getHost(),"firefly.eda.publishers.postgres.default.port="+postgres.getFirstMappedPort(),"firefly.eda.publishers.postgres.default.database="+postgres.getDatabaseName(),"firefly.eda.publishers.postgres.default.username="+postgres.getUsername(),"firefly.eda.publishers.postgres.default.password="+postgres.getPassword(),"firefly.eda.publishers.postgres.default.schema="+tables.schema(),
                        "firefly.eda.consumer.postgres.default.enabled=true","firefly.eda.consumer.postgres.default.group-id="+group,"firefly.eda.consumer.postgres.default.schema="+tables.schema(),
                        "firefly.eda.consumer.postgres.default.group-registry-table="+tables.registry(),"firefly.eda.consumer.postgres.default.group-deliveries-table="+tables.deliveries(),"firefly.eda.consumer.postgres.default.group-redrives-table="+tables.redrives(),
                        "firefly.eda.consumer.postgres.default.channels="+destination,"firefly.eda.consumer.postgres.default.delivery-mode="+(manual?"MANUAL":"ANNOTATED"),
                        "firefly.eda.consumer.postgres.default.polling-interval=PT0.05S","firefly.eda.consumer.postgres.default.lease-duration=PT5S","firefly.eda.consumer.postgres.default.handler-timeout=PT3S","firefly.eda.consumer.postgres.default.retry-delay=PT0.1S");
        try{
            runner.run(context->{
                assertThat(context).hasNotFailed();
                var repository=context.getBean(PostgresConsumerGroupRepository.class);
                try(var worker=new Worker(group,context.getBean(PostgresEventConsumer.class),context.getBean(PostgresEventPublisher.class),context.getBean(PostgresConsumerNativeFixture.Handler.class),repository,pool,listens)){action.accept(worker);}
            });
        }finally{pool.disposeLater().block(Duration.ofSeconds(5));}
    }
    final class Worker implements AutoCloseable {
        final String group;final PostgresEventConsumer consumer;final PostgresEventPublisher publisher;final PostgresConsumerNativeFixture.Handler handler;
        final PostgresConsumerGroupRepository repository;final ConnectionPool queryPool;final AtomicInteger listens;
        Worker(String group,PostgresEventConsumer consumer,PostgresEventPublisher publisher,PostgresConsumerNativeFixture.Handler handler,PostgresConsumerGroupRepository repository,ConnectionPool queryPool,AtomicInteger listens){this.group=group;this.consumer=consumer;this.publisher=publisher;this.handler=handler;this.repository=repository;this.queryPool=queryPool;this.listens=listens;assertThat(consumer.isRunning()).isFalse();}
        void register(){register(new GroupSubscription(java.util.Set.of(PostgresChannelMapper.toChannel(destination)),3));}
        void register(GroupSubscription policy){repository.registerGroup(policy,RetainedReplayPolicy.ALL_RETAINED).block(Duration.ofSeconds(5));}
        void start(){consumer.start().block(Duration.ofSeconds(5));assertThat(consumer.isRunning()).isTrue();}
        void publish(){publisher.publish(new PostgresConsumerNativeFixture.NativeEvent("group-runtime"),destination,Map.of("number",7)).block(Duration.ofSeconds(5));}
        List<PostgresGroupDeliveryRow> deliveries(){return template().select(PostgresGroupDeliveryRow.class).from(relation(tables.deliveries())).matching(Query.query(Criteria.where("group_id").is(group))).all().collectList().block(Duration.ofSeconds(5));}
        List<PostgresDeliveryRow> outbox(){return template().select(PostgresDeliveryRow.class).from(relation(tables.outbox())).matching(Query.empty()).all().collectList().block(Duration.ofSeconds(5));}
        void awaitProcessed(){await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(deliveries()).hasSize(1).allMatch(row->row.status().equals("PROCESSED")));}
        public void close(){consumer.stop().block(Duration.ofSeconds(10));assertThat(queryPool.isDisposed()).isFalse();}
    }
    private R2dbcEntityTemplate template(){return new R2dbcEntityTemplate(DatabaseClient.create(raw),PostgresDialect.INSTANCE);}
    private SqlIdentifier relation(String name){return SqlIdentifier.from(SqlIdentifier.quoted(tables.schema()),SqlIdentifier.quoted(name));}
    private static String quote(String value){return "\""+value.replace("\"","\"\"")+"\"";}
}
