/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresDeliveryRepository;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.publisher.postgres.impl.SpringDataPostgresOutboxWriter;
import org.fireflyframework.eda.publisher.postgres.model.PostgresOutboxAppend;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Testcontainers
class PostgresDeliveryRepositoryIntegrationTest {
    private static final Duration WAIT = Duration.ofSeconds(15);
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery_protocol_qualification").withUsername("delivery_test").withPassword("synthetic-only");
    private static ConnectionPool pool;
    private static DatabaseClient database;
    private String schema;
    private String table = "outbox";
    private final byte[] payload = {0, 1, 2, (byte) 255};

    private static ConnectionPool newPool(Map<String, String> options) {
        var configuration = PostgresqlConnectionConfiguration.builder().host(POSTGRES.getHost()).port(POSTGRES.getMappedPort(5432))
                .database(POSTGRES.getDatabaseName()).username(POSTGRES.getUsername()).password(POSTGRES.getPassword())
                .connectTimeout(Duration.ofSeconds(3)).options(options).build();
        return new ConnectionPool(ConnectionPoolConfiguration.builder(new PostgresqlConnectionFactory(configuration))
                .initialSize(0).maxSize(8).maxAcquireTime(Duration.ofSeconds(5)).maxCreateConnectionTime(Duration.ofSeconds(3))
                .maxValidationTime(Duration.ofSeconds(3)).acquireRetry(0).build());
    }
    @BeforeAll static void startOwnedInfrastructure() { pool = newPool(Map.of()); database = DatabaseClient.create(pool); }
    @AfterAll static void closeOwnedInfrastructure() { if (pool != null) pool.disposeLater().block(WAIT); }
    @BeforeEach void legacyRelation() {
        schema = "delivery_" + UUID.randomUUID().toString().replace("-", "");
        createLegacyRelation();
    }
    private String qualified() { return PostgresDeliverySchema.qualified(schema, table); }
    private void createLegacyRelation() {
        database.sql("CREATE SCHEMA " + PostgresDeliverySchema.quoted(schema)).then().block(WAIT);
        database.sql("CREATE TABLE " + qualified() + " (id BIGSERIAL PRIMARY KEY,destination VARCHAR(255) NOT NULL,"
                + "channel VARCHAR(63) NOT NULL,event_type VARCHAR(255),payload BYTEA NOT NULL,headers JSONB,"
                + "transaction_id VARCHAR(255),publisher_type VARCHAR(50),connection_id VARCHAR(50),event_class VARCHAR(500),"
                + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,error_message TEXT,"
                + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),processed_at TIMESTAMPTZ,failed_at TIMESTAMPTZ)").then().block(WAIT);
    }
    private void install() {
        Mono.usingWhen(Mono.from(pool.create()), connection -> Flux.from(connection.createStatement(
                        PostgresDeliverySchema.installationSql(schema, table)).execute())
                .concatMap(result -> Mono.from(result.getRowsUpdated())).then(), Connection::close,
                (connection, error) -> Mono.from(connection.rollbackTransaction()).then(Mono.from(connection.close())),
                connection -> Mono.from(connection.rollbackTransaction()).then(Mono.from(connection.close()))).block(WAIT);
    }
    private SpringDataPostgresDeliveryRepository repository(ConnectionFactory factory) {
        return new SpringDataPostgresDeliveryRepository(factory, schema, table, Duration.ofSeconds(10));
    }
    private SpringDataPostgresDeliveryRepository ready() {
        install(); var repository = repository(pool); repository.verifyProtocol().block(WAIT); return repository;
    }
    private ClaimRequest request(int limit, int attempts, Duration lease) { return new ClaimRequest(Set.of("events"), limit, attempts, lease); }
    private ClaimRequest request() { return request(8, 3, Duration.ofSeconds(5)); }
    private long append(String channel) {
        new SpringDataPostgresOutboxWriter(new R2dbcEntityTemplate(database, PostgresDialect.INSTANCE))
                .append(schema, table, new PostgresOutboxAppend(null, "orders.created", channel, null, payload,
                        Json.of("{\"decimal\":12.3400}"), null, "POSTGRES", "default", "untrusted.MustNotInitialize")).block(WAIT);
        return database.sql("SELECT max(id) AS id FROM " + qualified()).map((row, metadata) -> row.get("id", Long.class)).one().block(WAIT);
    }
    private Map<String, Object> stored(long id) {
        return database.sql("SELECT * FROM " + qualified() + " WHERE id=:id").bind("id", id).fetch().one().block(WAIT);
    }
    private byte[] storedPayload(long id) {
        return database.sql("SELECT payload FROM " + qualified() + " WHERE id=:id").bind("id", id)
                .map((row, metadata) -> row.get("payload", byte[].class)).one().block(WAIT);
    }
    private void update(long id, String assignment) {
        database.sql("UPDATE " + qualified() + " SET " + assignment + " WHERE id=:id").bind("id", id).then().block(WAIT);
    }
    private OffsetDateTime clock() {
        return database.sql("SELECT pg_catalog.clock_timestamp() AS now").map((row, metadata) -> row.get("now", OffsetDateTime.class)).one().block(WAIT);
    }
    private DeliveryClaim one(SpringDataPostgresDeliveryRepository repository, ClaimRequest request) {
        var batch = repository.claim(request).block(WAIT); assertThat(batch.claims()).hasSize(1); return batch.claims().getFirst();
    }
    private void expire(DeliveryClaim claim) { update(claim.row().id(), "lease_until=pg_catalog.clock_timestamp()-INTERVAL '1 second'"); }

    @Test void missingProtocolRejectsUntilManualInstallationAndRlsNeverGetsSilentlyAdopted() {
        var repository = repository(pool);
        assertThatThrownBy(() -> repository.verifyProtocol().block(WAIT)).isInstanceOf(IllegalStateException.class);
        install(); repository.verifyProtocol().block(WAIT);
        database.sql("ALTER TABLE " + qualified() + " ENABLE ROW LEVEL SECURITY").then().block(WAIT);
        assertThatThrownBy(() -> repository.verifyProtocol().block(WAIT)).isInstanceOf(IllegalStateException.class);
    }
    @Test void actualMappedClaimPreservesJsonBytesNullsAndReleasesBeforeEmission() {
        var repository = ready(); long id = append("events"); append("other");
        var batch = repository.claim(request()).doOnNext(value ->
                assertThat(pool.getMetrics().orElseThrow().acquiredSize()).isZero()).block(WAIT);
        assertThat(batch.claims()).hasSize(1); var row = batch.claims().getFirst().row(); assertThat(row.id()).isEqualTo(id);
        assertThat(row.payload()).containsExactly(payload); assertThat(row.headers().asString()).contains("12.3400");
        assertThat(row.eventType()).isNull(); assertThat(row.transactionId()).isNull(); assertThat(row.eventClass()).isEqualTo("untrusted.MustNotInitialize");
        assertThat(row.attempts()).isEqualTo(1); assertThat(row.leaseGeneration()).isEqualTo(1); assertThat(row.leaseToken()).isNotNull();
        assertThat(stored(id).get("status")).isEqualTo("IN_FLIGHT");
    }
    @Test void independentClaimantsCannotOwnTheSameRowsConcurrently() {
        ready(); for (int index = 0; index < 24; index++) append("events");
        var claims = Flux.range(0, 16).flatMap(index -> repository(pool).claim(request(1, 3, Duration.ofSeconds(5))), 8)
                .flatMapIterable(ClaimBatch::claims).collectList().block(WAIT);
        assertThat(claims).hasSize(16); assertThat(claims.stream().map(claim -> claim.row().id())).doesNotHaveDuplicates();
        assertThat(claims).allSatisfy(claim -> {
            assertThat(claim.row().attempts()).isEqualTo(1); assertThat(claim.row().leaseGeneration()).isEqualTo(1);
        });
    }
    @Test void acknowledgmentIsIdempotentOnlyForTheSameTerminalActionAndClaim() {
        var repository = ready(); append("events"); var claim = one(repository, request());
        assertThat(repository.acknowledge(claim).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
        assertThat(repository.acknowledge(claim).block(WAIT)).isEqualTo(TransitionResult.ALREADY_APPLIED);
        assertThat(repository.reject(claim, FailureKind.EXPLICIT_DEAD_LETTER, Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.STALE);
        assertThat(stored(claim.row().id()).get("status")).isEqualTo("PROCESSED");
    }
    @Test void retryNackCannotRescheduleOrIncrementOnRepeatedCallback() {
        var repository = ready(); append("events"); var claim = one(repository, request());
        assertThat(repository.reject(claim, FailureKind.LISTENER_FAILURE, Duration.ofSeconds(2)).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
        var first = stored(claim.row().id()); assertThat(first.get("status")).isEqualTo("PENDING");
        assertThat(repository.reject(claim, FailureKind.LISTENER_FAILURE, Duration.ofDays(1)).block(WAIT)).isEqualTo(TransitionResult.STALE);
        var repeated = stored(claim.row().id()); assertThat(repeated.get("next_attempt_at")).isEqualTo(first.get("next_attempt_at"));
        assertThat(repeated.get("attempts")).isEqualTo(1); assertThat(repository.claim(request()).block(WAIT).claims()).isEmpty();
    }
    @Test void explicitDeadLetterRequiresIdenticalKindForAlreadyApplied() {
        var repository = ready(); append("events"); var claim = one(repository, request());
        assertThat(repository.reject(claim, FailureKind.EXPLICIT_DEAD_LETTER, Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
        assertThat(repository.reject(claim, FailureKind.EXPLICIT_DEAD_LETTER, Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.ALREADY_APPLIED);
        assertThat(repository.reject(claim, FailureKind.LISTENER_FAILURE, Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.STALE);
        assertThat(stored(claim.row().id()).get("error_message")).isEqualTo("DLQ_DIRECT:EXPLICIT_DEAD_LETTER");
    }
    @Test void maximumAttemptIncrementCannotOverflowAndFailureBecomesTerminal() {
        var repository = ready(); long id = append("events"); update(id, "attempts=2147483646");
        var claim = one(repository, request(1, Integer.MAX_VALUE, Duration.ofSeconds(5)));
        assertThat(claim.row().attempts()).isEqualTo(Integer.MAX_VALUE);
        assertThat(repository.reject(claim, FailureKind.LISTENER_FAILURE, Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
        assertThat(stored(id).get("status")).isEqualTo("DEAD_LETTER"); assertThat(stored(id).get("attempts")).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void generationExhaustionReaperDoesNotOverflowOrResetTheCounter() {
        var repository = ready(); long id = append("events"); update(id, "lease_generation=9223372036854775807");
        assertThat(repository.claim(request()).block(WAIT).claims()).isEmpty();
        assertThat(repository.reapExhausted(request()).block(WAIT)).isEqualTo(1L);
        assertThat(stored(id).get("lease_generation")).isEqualTo(Long.MAX_VALUE); assertThat(stored(id).get("status")).isEqualTo("DEAD_LETTER");
    }
    @Test void activeLastAttemptIsNotReapedUntilItsLeaseExpires() {
        var repository = ready(); append("events"); var request = request(1, 1, Duration.ofSeconds(5)); var claim = one(repository, request);
        assertThat(repository.reapExhausted(request).block(WAIT)).isZero(); expire(claim);
        assertThat(repository.reapExhausted(request).block(WAIT)).isEqualTo(1L);
        assertThat(repository.acknowledge(claim).block(WAIT)).isEqualTo(TransitionResult.STALE);
        assertThat(stored(claim.row().id()).get("attempts")).isEqualTo(1);
    }
    @Test void reconstructedRepositoryRecoversExpiredLeaseWithANewFence() {
        var first = ready(); append("events"); var old = one(first, request()); expire(old);
        var recovered = one(repository(pool), request()); assertThat(recovered.row().leaseToken()).isNotEqualTo(old.row().leaseToken());
        assertThat(recovered.row().leaseGeneration()).isEqualTo(2); assertThat(recovered.row().attempts()).isEqualTo(2);
        assertThat(first.acknowledge(old).block(WAIT)).isEqualTo(TransitionResult.STALE);
        assertThat(first.reject(old, FailureKind.LISTENER_FAILURE, Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.STALE);
    }
    @Test void installedProtocolPreservesRetainedTerminalIdsBytesAttemptsAndStates() {
        long processed = append("events"); long dead = append("events");
        update(processed, "status='PROCESSED',attempts=7"); update(dead, "status='DEAD_LETTER',attempts=8");
        var beforeProcessed = storedPayload(processed); var beforeDead = storedPayload(dead); var repository = ready();
        assertThat(repository.claim(request()).block(WAIT).claims()).isEmpty(); assertThat(repository.reapExhausted(request()).block(WAIT)).isZero();
        assertThat(stored(processed)).containsEntry("id", processed).containsEntry("attempts", 7).containsEntry("status", "PROCESSED");
        assertThat(stored(dead)).containsEntry("id", dead).containsEntry("attempts", 8).containsEntry("status", "DEAD_LETTER");
        assertThat(storedPayload(processed)).containsExactly(beforeProcessed);
        assertThat(storedPayload(dead)).containsExactly(beforeDead);
    }
    @Test void migrationRerunFailsAtomicallyAndMarkerCannotBeGuessed() {
        var repository = ready(); assertThatThrownBy(this::install).isInstanceOf(RuntimeException.class); repository.verifyProtocol().block(WAIT);
        database.sql("COMMENT ON COLUMN " + qualified() + ".lease_generation IS 'unknown'").then().block(WAIT);
        assertThatThrownBy(() -> repository.verifyProtocol().block(WAIT)).isInstanceOf(IllegalStateException.class);
    }
    @Test void validTokenLikeIdentifiersRemainLiteralDuringInstallation() {
        schema = "{{dueIndex}}"; table = "{{leaseIndex}}"; createLegacyRelation(); var repository = ready(); append("events");
        assertThat(one(repository, request()).row().destination()).isEqualTo("orders.created");
    }
    @Test void ambientRollbackCannotUndoInnerClaimOrChangeOuterSearchPath() {
        var repository = ready(); append("events");
        database.sql("CREATE TABLE " + PostgresDeliverySchema.quoted(schema) + ".owner_probe(id INT PRIMARY KEY)").then().block(WAIT);
        var original = new java.util.concurrent.atomic.AtomicReference<String>();
        var expected = new IllegalStateException("intentional outer rollback");
        var work = database.sql("SHOW search_path").map((row, metadata) -> row.get(0, String.class)).one().doOnNext(original::set)
                .then(database.sql("INSERT INTO " + PostgresDeliverySchema.quoted(schema) + ".owner_probe VALUES(1)").then())
                .then(repository.claim(request())).doOnNext(batch -> {
                    assertThat(batch.claims()).hasSize(1); assertThat(pool.getMetrics().orElseThrow().acquiredSize()).isEqualTo(1);
                }).then(database.sql("SHOW search_path").map((row, metadata) -> row.get(0, String.class)).one())
                .doOnNext(value -> assertThat(value).isEqualTo(original.get())).then(Mono.error(expected));
        assertThatThrownBy(() -> TransactionalOperator.create(new R2dbcTransactionManager(pool)).transactional(work).block(WAIT)).isSameAs(expected);
        assertThat(database.sql("SELECT count(*) AS n FROM " + PostgresDeliverySchema.quoted(schema) + ".owner_probe")
                .map((row, metadata) -> row.get("n", Long.class)).one().block(WAIT)).isZero();
        assertThat(database.sql("SELECT status FROM " + qualified()).map((row, metadata) -> row.get("status", String.class)).one().block(WAIT)).isEqualTo("IN_FLIGHT");
    }
    @Test void hostileSearchPathCannotRedirectClockTypesOrTheOutbox() {
        ready(); long id = append("events"); String shadow = schema + "_shadow";
        database.sql("CREATE SCHEMA " + PostgresDeliverySchema.quoted(shadow)).then().block(WAIT);
        database.sql("CREATE DOMAIN " + PostgresDeliverySchema.quoted(shadow) + ".text AS pg_catalog.text").then().block(WAIT);
        database.sql("CREATE FUNCTION " + PostgresDeliverySchema.quoted(shadow)
                + ".clock_timestamp() RETURNS TIMESTAMPTZ LANGUAGE sql AS $clock$ SELECT TIMESTAMPTZ '1900-01-01' $clock$").then().block(WAIT);
        var scopedPool = newPool(Map.of("search_path", PostgresDeliverySchema.quoted(shadow) + ",pg_catalog"));
        try {
            var scoped = repository(scopedPool); scoped.verifyProtocol().block(WAIT); var claim = one(scoped, request());
            assertThat(claim.row().id()).isEqualTo(id); assertThat(claim.row().leaseUntil()).isAfter(clock());
            assertThat(scoped.acknowledge(claim).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
            assertThat(DatabaseClient.create(scopedPool).sql("SHOW search_path").map((row, metadata) -> row.get(0, String.class)).one().block(WAIT)).contains(shadow);
        } finally { scopedPool.disposeLater().block(WAIT); }
    }

    private final class ObservedFactory implements ConnectionFactory {
        final AtomicInteger pid = new AtomicInteger();
        public Publisher<? extends Connection> create() {
            return Mono.from(pool.create()).flatMap(connection -> Mono.from(connection.createStatement("SELECT pg_catalog.pg_backend_pid() AS pid").execute())
                    .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("pid", Integer.class)))).doOnNext(pid::set).thenReturn(connection)
                    .onErrorResume(error -> Mono.from(connection.close()).then(Mono.error(error))));
        }
        public ConnectionFactoryMetadata getMetadata() { return pool.getMetadata(); }
    }
    private void awaitLock(ObservedFactory factory) {
        await().atMost(WAIT).until(() -> factory.pid.get() > 0);
        await().atMost(WAIT).until(() -> Boolean.TRUE.equals(database.sql("SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_stat_activity WHERE pid=:pid AND wait_event_type='Lock') AS blocked")
                .bind("pid", factory.pid.get()).map((row, metadata) -> row.get("blocked", Boolean.class)).one().block(WAIT)));
    }
    private void rollbackAndClose(Connection blocker, boolean committed) {
        try { if (!committed) Mono.from(blocker.rollbackTransaction()).block(WAIT); }
        finally { Mono.from(blocker.close()).block(WAIT); }
    }
    @Test void cancellationDuringAnObservedClaimWaitRollsBackWithoutAClaim() {
        ready(); long id = append("events"); var blocker = Mono.from(pool.create()).block(WAIT);
        CompletableFuture<ClaimBatch> future = null; boolean committed = false;
        try {
            Mono.from(blocker.beginTransaction()).block(WAIT);
            Mono.from(blocker.createStatement("LOCK TABLE " + qualified() + " IN ACCESS EXCLUSIVE MODE").execute())
                    .flatMap(result -> Mono.from(result.getRowsUpdated())).block(WAIT);
            var observed = new ObservedFactory(); future = repository(observed).claim(request()).toFuture(); awaitLock(observed);
            assertThat(future.cancel(true)).isTrue();
            Mono.from(blocker.commitTransaction()).block(WAIT); committed = true;
            await().atMost(WAIT).until(() -> pool.getMetrics().orElseThrow().acquiredSize() == 1);
        } finally { if (future != null && !future.isDone()) future.cancel(true); rollbackAndClose(blocker, committed); }
        assertThat(stored(id)).containsEntry("status", "PENDING").containsEntry("attempts", 0).containsEntry("lease_generation", 0L);
    }
    @Test void releaseFailureAfterRealCommitWithholdsBatchAndLeavesLeaseRecoverable() {
        ready(); long id = append("events"); var opened = new AtomicInteger(); var closed = new AtomicInteger();
        var failure = new IllegalStateException("synthetic release failure");
        ConnectionFactory faulty = new ConnectionFactory() {
            public Publisher<? extends Connection> create() {
                return Mono.from(pool.create()).map(connection -> {
                    opened.incrementAndGet();
                    return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                                if (method.getName().equals("close")) {
                                    return Mono.from(connection.close()).doOnSuccess(unused -> closed.incrementAndGet())
                                            .then(Mono.error(failure));
                                }
                                try { return method.invoke(connection, arguments); }
                                catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                            });
                });
            }
            public ConnectionFactoryMetadata getMetadata() { return pool.getMetadata(); }
        };
        assertThatThrownBy(() -> repository(faulty).claim(request()).block(WAIT)).isInstanceOf(IllegalStateException.class).hasCause(failure);
        assertThat(opened.get()).isEqualTo(1); assertThat(closed.get()).isEqualTo(1);
        assertThat(stored(id)).containsEntry("status", "IN_FLIGHT").containsEntry("attempts", 1).containsEntry("lease_generation", 1L);
        update(id, "lease_until=pg_catalog.clock_timestamp()-INTERVAL '1 second'");
        var recovered = one(repository(pool), request());
        assertThat(recovered.row().attempts()).isEqualTo(2); assertThat(recovered.row().leaseGeneration()).isEqualTo(2);
    }
    @Test void claimLeaseClockStartsAfterAnObservedTableLockWait() {
        ready(); append("events"); var blocker = Mono.from(pool.create()).block(WAIT);
        CompletableFuture<ClaimBatch> future = null; boolean committed = false;
        try {
            Mono.from(blocker.beginTransaction()).block(WAIT);
            Mono.from(blocker.createStatement("LOCK TABLE " + qualified() + " IN ACCESS EXCLUSIVE MODE").execute())
                    .flatMap(result -> Mono.from(result.getRowsUpdated())).block(WAIT);
            var observed = new ObservedFactory(); var started = clock();
            future = repository(observed).claim(request(1, 3, Duration.ofSeconds(1))).toFuture(); awaitLock(observed);
            await().atMost(WAIT).until(() -> clock().isAfter(started.plusSeconds(2))); var released = clock();
            Mono.from(blocker.commitTransaction()).block(WAIT); committed = true;
            var batch = Mono.fromFuture(future).block(WAIT); assertThat(batch.claims()).hasSize(1);
            assertThat(batch.claims().getFirst().row().leaseUntil()).isAfter(released.plusNanos(800000000));
        } finally { if (future != null && !future.isDone()) future.cancel(true); rollbackAndClose(blocker, committed); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ackAndNackRecheckLeaseAfterAnObservedRowLockWait(boolean reject) {
        var repository = ready(); append("events"); var claim = one(repository, request(1, 3, Duration.ofMillis(500)));
        var blocker = Mono.from(pool.create()).block(WAIT);
        CompletableFuture<TransitionResult> future = null; boolean committed = false;
        try {
            Mono.from(blocker.beginTransaction()).block(WAIT);
            Flux.from(blocker.createStatement("SELECT id FROM " + qualified() + " WHERE id=" + claim.row().id() + " FOR UPDATE").execute())
                    .concatMap(result -> Flux.from(result.map((row, metadata) -> row.get("id", Long.class)))).then().block(WAIT);
            var observed = new ObservedFactory(); var contender = repository(observed);
            future = (reject ? contender.reject(claim, FailureKind.LISTENER_FAILURE, Duration.ZERO) : contender.acknowledge(claim)).toFuture(); awaitLock(observed);
            await().atMost(WAIT).until(() -> clock().isAfter(claim.row().leaseUntil().plusNanos(200000000)));
            Mono.from(blocker.commitTransaction()).block(WAIT); committed = true;
            assertThat(Mono.fromFuture(future).block(WAIT)).isEqualTo(TransitionResult.STALE);
            assertThat(stored(claim.row().id()).get("status")).isEqualTo("IN_FLIGHT");
        } finally { if (future != null && !future.isDone()) future.cancel(true); rollbackAndClose(blocker, committed); }
    }
}
