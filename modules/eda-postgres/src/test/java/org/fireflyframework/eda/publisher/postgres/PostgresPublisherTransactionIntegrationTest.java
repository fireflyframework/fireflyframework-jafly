/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.publisher.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.fireflyframework.eda.config.FireflyEdaPostgresPublisherAutoConfiguration;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.serialization.JsonMessageSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
class PostgresPublisherTransactionIntegrationTest {
    private static final Duration WAIT = Duration.ofSeconds(15);
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("publisher_transaction_qualification")
            .withUsername("publisher_test").withPassword("synthetic-only");
    private static ConnectionPool pool;
    private static DatabaseClient database;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void startActualPublisherInfrastructure() {
        var properties = properties();
        pool = new FireflyEdaPostgresPublisherAutoConfiguration(properties)
                .fireflyEdaPostgresPublisherConnectionFactory(properties);
        database = DatabaseClient.create(pool);
        database.sql("CREATE TABLE publisher_business_fixture(id UUID PRIMARY KEY, value TEXT NOT NULL)")
                .fetch().rowsUpdated().block(WAIT);
        database.sql("""
                CREATE FUNCTION publisher_zero_count_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.destination LIKE 'zero-count-%' THEN RETURN NULL; END IF; RETURN NEW; END $$
                """).fetch().rowsUpdated().block(WAIT);
        database.sql("CREATE TRIGGER publisher_zero_count_fixture BEFORE INSERT ON firefly_eda_outbox "
                + "FOR EACH ROW EXECUTE FUNCTION publisher_zero_count_fixture()")
                .fetch().rowsUpdated().block(WAIT);
    }

    @AfterAll
    static void closeActualPublisherInfrastructure() {
        if (pool != null) pool.dispose();
    }

    private static EdaProperties properties() {
        var properties = new EdaProperties();
        properties.getPublishers().setEnabled(true);
        var config = properties.getPublishers().getPostgres().get("default");
        config.setEnabled(true);
        config.setHost(postgres.getHost());
        config.setPort(postgres.getMappedPort(5432));
        config.setDatabase(postgres.getDatabaseName());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setAutoCreateSchema(true);
        return properties;
    }

    private static PostgresEventPublisher publisher(ConnectionFactory factory, EdaProperties properties) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("fireflyEdaPostgresPublisherConnectionFactory", factory);
        return new PostgresEventPublisher(beans.getBeanProvider(ConnectionFactory.class),
                new JsonMessageSerializer(mapper), properties, mapper);
    }

    @Test
    void autoConfigurationActuallyContributesItsPublisher() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FireflyEdaPostgresPublisherAutoConfiguration.class))
                .withBean("fireflyEdaPostgresPublisherConnectionFactory", ConnectionFactory.class, () -> pool)
                .withBean(ObjectMapper.class, () -> mapper)
                .withBean(JsonMessageSerializer.class, () -> new JsonMessageSerializer(mapper))
                .run(context -> assertThat(context).hasSingleBean(PostgresEventPublisher.class));
    }

    @Test
    void actualAutoConfiguredPoolAndPublisherAppendWithoutComponentScanning() {
        String destination = "auto-configured-" + UUID.randomUUID();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FireflyEdaPostgresPublisherAutoConfiguration.class))
                .withBean(ObjectMapper.class, () -> mapper)
                .withBean(JsonMessageSerializer.class, () -> new JsonMessageSerializer(mapper))
                .withPropertyValues("firefly.eda.publishers.enabled=true",
                        "firefly.eda.publishers.postgres.default.enabled=true",
                        "firefly.eda.publishers.postgres.default.auto-create-schema=false",
                        "firefly.eda.publishers.postgres.default.host=" + postgres.getHost(),
                        "firefly.eda.publishers.postgres.default.port=" + postgres.getMappedPort(5432),
                        "firefly.eda.publishers.postgres.default.database=" + postgres.getDatabaseName(),
                        "firefly.eda.publishers.postgres.default.username=" + postgres.getUsername(),
                        "firefly.eda.publishers.postgres.default.password=" + postgres.getPassword())
                .run(context -> {
                    assertThat(context).hasSingleBean(PostgresEventPublisher.class);
                    context.getBean(PostgresEventPublisher.class).publish("configured payload", destination).block(WAIT);
                    assertThat(outboxCount(destination)).isEqualTo(1L);
                });
    }

    @Test
    void configuredSchemaAndTableRetainTheirDistinctPhysicalTarget() {
        database.sql("CREATE SCHEMA publisher_custom_fixture").fetch().rowsUpdated().block(WAIT);
        database.sql("CREATE TABLE publisher_custom_fixture.custom_events (LIKE public.firefly_eda_outbox INCLUDING DEFAULTS)")
                .fetch().rowsUpdated().block(WAIT);
        var properties = properties();
        var config = properties.getPublishers().getPostgres().get("default");
        config.setSchema("publisher_custom_fixture");
        config.setOutboxTable("custom_events");
        String destination = "custom-target-" + UUID.randomUUID();

        publisher(pool, properties).publish("custom payload", destination).block(WAIT);

        assertThat(database.sql("SELECT count(*) AS n FROM publisher_custom_fixture.custom_events WHERE destination=:destination")
                .bind("destination", destination).map((row, metadata) -> row.get("n", Long.class)).one().block(WAIT)).isEqualTo(1L);
        assertThat(outboxCount(destination)).isZero();
    }

    @Test
    void ownerRollbackRemovesBothBusinessWriteAndOutboxAppend() {
        var factory = new RecordingFactory(pool);
        var client = DatabaseClient.create(factory);
        var publisher = publisher(factory, properties());
        UUID id = UUID.randomUUID();
        String destination = "rollback-" + id;
        var refusal = new IllegalStateException("synthetic owner rollback");
        var work = business(client, id).then(publisher.publish("rollback payload", destination))
                .then(Mono.error(refusal));

        StepVerifier.create(TransactionalOperator.create(new R2dbcTransactionManager(factory)).transactional(work))
                .expectErrorMatches(error -> error == refusal).verify(WAIT);

        assertThat(businessCount(id)).isZero();
        assertThat(outboxCount(destination)).isZero();
        assertThat(factory.opened.get()).isEqualTo(1);
        assertThat(factory.released.get()).isEqualTo(1);
    }

    @Test
    void ownerCommitUsesOneConnectionAndTheSameDatabaseTransactionClock() {
        var factory = new RecordingFactory(pool);
        var client = DatabaseClient.create(factory);
        var publisher = publisher(factory, properties());
        UUID id = UUID.randomUUID();
        String destination = "commit-" + id;
        OffsetDateTime now = TransactionalOperator.create(new R2dbcTransactionManager(factory))
                .transactional(business(client, id).then(publisher.publish("commit payload", destination))
                        .then(client.sql("SELECT CURRENT_TIMESTAMP AS at").map((row, metadata) ->
                                row.get("at", OffsetDateTime.class)).one())).block(WAIT);

        assertThat(businessCount(id)).isEqualTo(1);
        assertThat(outboxCount(destination)).isEqualTo(1);
        var stored = row(destination);
        assertThat(stored.get("created_at")).isEqualTo(now);
        assertThat(stored.get("id")).isInstanceOf(Long.class);
        assertThat(stored.get("status")).isEqualTo("PENDING");
        assertThat(stored.get("attempts")).isEqualTo(0);
        assertThat(factory.opened.get()).isEqualTo(1);
        assertThat(factory.released.get()).isEqualTo(1);
    }

    @Test
    void cancellationRollsBackAndReleasesTheOwnerConnection() {
        var factory = new RecordingFactory(pool);
        var client = DatabaseClient.create(factory);
        var publisher = publisher(factory, properties());
        UUID id = UUID.randomUUID();
        String destination = "cancel-" + id;
        var appended = Sinks.one();
        var work = business(client, id).then(publisher.publish("cancel payload", destination))
                .then(Mono.fromRunnable(() -> appended.tryEmitEmpty())).then(Mono.never());
        var subscription = TransactionalOperator.create(new R2dbcTransactionManager(factory))
                .transactional(work).subscribe();
        try {
            appended.asMono().block(WAIT);
        } finally {
            subscription.dispose();
        }
        await().atMost(WAIT).until(() -> factory.released.get() >= 1);
        assertThat(businessCount(id)).isZero();
        assertThat(outboxCount(destination)).isZero();
        assertThat(factory.opened.get()).isEqualTo(1);
        assertThat(factory.released.get()).isEqualTo(1);
    }

    @Test
    void publicationOutsideAnOwnerTransactionCommitsAndReleasesExactlyOnce() {
        var factory = new RecordingFactory(pool);
        String destination = "standalone-" + UUID.randomUUID();

        publisher(factory, properties()).publish("standalone payload", destination).block(WAIT);

        assertThat(outboxCount(destination)).isEqualTo(1);
        assertThat(factory.opened.get()).isEqualTo(1);
        assertThat(factory.released.get()).isEqualTo(1);
    }

    @Test
    void aDifferentPublisherFactoryDoesNotBecomeAnAtomicBusinessOutbox() {
        var owner = new RecordingFactory(pool);
        var separate = new RecordingFactory(pool);
        UUID id = UUID.randomUUID();
        String destination = "separate-" + id;
        var refusal = new IllegalStateException("synthetic separate owner rollback");
        var work = business(DatabaseClient.create(owner), id)
                .then(publisher(separate, properties()).publish("separate payload", destination))
                .then(Mono.error(refusal));

        StepVerifier.create(TransactionalOperator.create(new R2dbcTransactionManager(owner)).transactional(work))
                .expectErrorMatches(error -> error == refusal).verify(WAIT);

        assertThat(businessCount(id)).isZero();
        assertThat(outboxCount(destination)).isEqualTo(1);
        assertThat(owner.opened.get()).isEqualTo(1);
        assertThat(separate.opened.get()).isEqualTo(1);
    }

    @Test
    void publicationMustObserveExactlyOneInsertedRow() {
        String destination = "zero-count-" + UUID.randomUUID();
        StepVerifier.create(publisher(pool, properties()).publish("not inserted", destination))
                .expectError().verify(WAIT);
        assertThat(outboxCount(destination)).isZero();
    }

    @Test
    void payloadMetadataJsonbAndNullableColumnsRetainTheirValues() throws Exception {
        String destination = "metadata-" + UUID.randomUUID();
        byte[] payload = "unmodified payload é".getBytes(StandardCharsets.UTF_8);
        var headers = new HashMap<String, Object>();
        headers.put("event_type", "custom-event");
        headers.put("nullable", null);
        headers.put("exact", new java.math.BigInteger("9223372036854775807"));
        publisher(pool, properties()).publish(payload, destination, headers).block(WAIT);

        var stored = row(destination);
        assertThat((byte[]) stored.get("payload")).containsExactly(payload);
        assertThat(stored.get("destination")).isEqualTo(destination);
        assertThat(stored.get("channel")).isEqualTo(PostgresChannelMapper.toChannel(destination));
        assertThat(stored.get("event_type")).isEqualTo("custom-event");
        assertThat(stored.get("transaction_id")).isNull();
        assertThat(stored.get("publisher_type")).isEqualTo("POSTGRES");
        assertThat(stored.get("connection_id")).isEqualTo("default");
        assertThat(stored.get("event_class")).isEqualTo(byte[].class.getName());
        var json = mapper.readTree((String) stored.get("headers"));
        assertThat(json.path("exact").asText()).isEqualTo("9223372036854775807");
        assertThat(json.has("nullable")).isFalse();
        assertThat(json.path("published_at").asText()).isNotBlank();
        assertThat(stored.get("processed_at")).isNull();
        assertThat(stored.get("failed_at")).isNull();
        assertThat(stored.get("error_message")).isNull();
    }

    private static Mono<Void> business(DatabaseClient client, UUID id) {
        return client.sql("INSERT INTO publisher_business_fixture(id,value) VALUES(:id,'synthetic')")
                .bind("id", id).fetch().rowsUpdated().then();
    }

    private static long businessCount(UUID id) {
        return database.sql("SELECT count(*) AS n FROM publisher_business_fixture WHERE id=:id")
                .bind("id", id).map((row, metadata) -> row.get("n", Long.class)).one().block(WAIT);
    }

    private static long outboxCount(String destination) {
        return database.sql("SELECT count(*) AS n FROM firefly_eda_outbox WHERE destination=:destination")
                .bind("destination", destination).map((row, metadata) -> row.get("n", Long.class)).one().block(WAIT);
    }

    private static Map<String, Object> row(String destination) {
        return database.sql("SELECT *,headers::text AS headers_text FROM firefly_eda_outbox WHERE destination=:destination")
                .bind("destination", destination).map((row, metadata) -> {
                    var values = new HashMap<String, Object>();
                    for (var column : metadata.getColumnMetadatas()) {
                        values.put(column.getName(), row.get(column.getName()));
                    }
                    values.put("headers", row.get("headers_text", String.class));
                    values.put("payload", row.get("payload", byte[].class));
                    values.put("created_at", row.get("created_at", OffsetDateTime.class));
                    return values;
                }).one().block(WAIT);
    }

    private static final class RecordingFactory implements ConnectionFactory {
        private final ConnectionFactory delegate;
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger released = new AtomicInteger();

        private RecordingFactory(ConnectionFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.defer(() -> Mono.from(delegate.create()).map(connection -> {
                opened.incrementAndGet();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("close")) {
                                return Mono.from(connection.close()).doOnSuccess(unused -> released.incrementAndGet());
                            }
                            try {
                                return method.invoke(connection, args);
                            } catch (InvocationTargetException error) {
                                throw error.getCause();
                            }
                        });
            }));
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return delegate.getMetadata();
        }
    }
}
