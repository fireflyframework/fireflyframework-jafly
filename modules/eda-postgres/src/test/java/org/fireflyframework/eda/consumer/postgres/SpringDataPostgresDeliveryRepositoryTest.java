/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresDeliveryRepository;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class SpringDataPostgresDeliveryRepositoryTest {
    final ConnectionFactory factory = mock(ConnectionFactory.class);
    final List<Session> sessions = new ArrayList<>();
    final AtomicInteger acquired = new AtomicInteger();
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-30T00:00:00Z");
    static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000001");
    final byte[] payload = {0, 1, 2, (byte)255};
    final Json headers = Json.of("{\"decimal\":12.3400}");

    SpringDataPostgresDeliveryRepositoryTest() {
        when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
        doReturn(Mono.defer(() -> Mono.just(sessions.get(acquired.getAndIncrement()).connection))).when(factory).create();
    }

    Session session() { var session = new Session(); sessions.add(session); return session; }
    SpringDataPostgresDeliveryRepository repository() {
        return new SpringDataPostgresDeliveryRepository(factory, "custom\"schema", "custom\"outbox", Duration.ofSeconds(10));
    }
    ClaimRequest request() { return new ClaimRequest(Set.of("events"), 4, 3, Duration.ofSeconds(20)); }
    PostgresDeliveryRow row() {
        return new PostgresDeliveryRow(11L, "orders.created", "events", null, payload, headers,
                null, "POSTGRES", "connection", "untrusted.MustNotLoad", "IN_FLIGHT", 1, null,
                NOW, null, null, TOKEN, 1, NOW.plusSeconds(20), null);
    }
    DeliveryClaim claim() { return new DeliveryClaim(row(), 3); }
    Map<String,Object> eventRow() {
        var values = new LinkedHashMap<String,Object>();
        values.put("id",11L);values.put("destination","orders.created");values.put("channel","events");
        values.put("event_type",null);values.put("payload",payload);values.put("headers",headers);
        values.put("transaction_id",null);values.put("publisher_type","POSTGRES");values.put("connection_id","connection");
        values.put("event_class","untrusted.MustNotLoad");values.put("status","IN_FLIGHT");values.put("attempts",1);
        values.put("error_message",null);values.put("created_at",NOW);values.put("processed_at",null);values.put("failed_at",null);
        values.put("lease_token",TOKEN);values.put("lease_generation",1L);values.put("lease_until",NOW.plusSeconds(20));values.put("next_attempt_at",null);
        return values;
    }

    final class Session {
        final Connection connection = mock(Connection.class);
        final AtomicInteger began = new AtomicInteger(), committed = new AtomicInteger(), rolledBack = new AtomicInteger(), closed = new AtomicInteger();
        final Map<Integer,Object> bindings = new LinkedHashMap<>();
        Mono<Void> commitGate = Mono.empty(), closeGate = Mono.empty();
        List<Map<String,Object>> rows = List.of(eventRow());
        String sql = "";
        final List<String> statements = new ArrayList<>();
        io.r2dbc.spi.TransactionDefinition definition;
        @SuppressWarnings({"unchecked","rawtypes"})
        Session() {
            when(connection.isAutoCommit()).thenReturn(true);
            when(connection.setAutoCommit(anyBoolean())).thenReturn(Mono.empty());
            when(connection.beginTransaction(any(io.r2dbc.spi.TransactionDefinition.class))).thenAnswer(call -> {
                definition=call.getArgument(0);return Mono.fromRunnable(began::incrementAndGet);
            });
            when(connection.commitTransaction()).thenAnswer(call -> Mono.defer(() -> {committed.incrementAndGet();return commitGate;}));
            when(connection.rollbackTransaction()).thenReturn(Mono.fromRunnable(rolledBack::incrementAndGet));
            when(connection.close()).thenAnswer(call -> Mono.defer(() -> {closed.incrementAndGet();return closeGate;}));
            when(connection.createStatement(anyString())).thenAnswer(call -> {
                sql=call.getArgument(0);statements.add(sql);bindings.clear();var statement=mock(Statement.class,RETURNS_SELF);
                when(statement.bind(anyInt(),any())).thenAnswer(binding -> {Object value=binding.getArgument(1);bindings.put(binding.getArgument(0),value instanceof io.r2dbc.spi.Parameter parameter?parameter.getValue():value);return statement;});
                when(statement.bindNull(anyInt(),any())).thenAnswer(binding -> {bindings.put(binding.getArgument(0),null);return statement;});
                var result=mock(Result.class);
                when(result.getRowsUpdated()).thenReturn(Mono.just(0L));
                when(result.map(any(BiFunction.class))).thenAnswer(mapping -> {
                    BiFunction<Row,RowMetadata,Object> mapper=mapping.getArgument(0);
                    return Flux.fromIterable(rows).map(values -> {
                        var row=mock(Row.class);var metadata=mock(RowMetadata.class);var columns=new ArrayList<ColumnMetadata>();
                        values.forEach((name,value) -> {
                            var column=mock(ColumnMetadata.class);when(column.getName()).thenReturn(name);
                            doReturn(value!=null?value.getClass():Object.class).when(column).getJavaType();columns.add(column);
                            when(metadata.getColumnMetadata(name)).thenReturn(column);
                        });
                        doReturn(columns).when(metadata).getColumnMetadatas();
                        when(row.getMetadata()).thenReturn(metadata);
                        when(row.get(anyString())).thenAnswer(read -> values.get(read.getArgument(0)));
                        when(row.get(anyString(),any(Class.class))).thenAnswer(read -> values.get(read.getArgument(0)));
                        return mapper.apply(row,metadata);
                    });
                });
                doReturn(Flux.just(result)).when(statement).execute();return statement;
            });
        }
    }

    @Test void claimMapsActualSpringDataRowAndReleasesBeforeEmission() {
        var session=session();var batch=repository().claim(request()).block();
        assertThat(batch.claims()).hasSize(1);var claim=batch.claims().getFirst();
        assertThat(claim.row().id()).isEqualTo(11L);assertThat(claim.row().payload()).containsExactly(payload);
        assertThat(claim.row().headers().asString()).isEqualTo(headers.asString());
        assertThat(claim.row().eventType()).isNull();assertThat(claim.row().eventClass()).isEqualTo("untrusted.MustNotLoad");
        assertThat(claim.maxAttempts()).isEqualTo(3);assertThat(session.committed.get()).isEqualTo(1);
        assertThat(session.closed.get()).isEqualTo(1);assertThat(acquired.get()).isEqualTo(1);
    }

    @Test void claimDoesNotEmitWhileCommitOrReleaseIsPending() {
        var session=session();var commit=Sinks.<Void>empty();var close=Sinks.<Void>empty();
        session.commitGate=commit.asMono();session.closeGate=close.asMono();
        var seen=new AtomicReference<ClaimBatch>();var subscription=repository().claim(request()).doOnNext(seen::set).subscribe();
        try {
            assertThat(session.began.get()).isEqualTo(1);assertThat(session.committed.get()).isEqualTo(1);assertThat(seen.get()).isNull();
            commit.tryEmitEmpty();assertThat(session.closed.get()).isEqualTo(1);assertThat(seen.get()).isNull();
            close.tryEmitEmpty();assertThat(seen.get().claims()).hasSize(1);
        } finally {subscription.dispose();}
    }

    @Test void ambientSameFactoryTransactionIsSuspendedAndInnerClaimCommitsIndependently() {
        var outer=session();var inner=session();var repository=repository();var expected=new IllegalStateException("intentional outer rollback");
        assertThatThrownBy(()->TransactionalOperator.create(new R2dbcTransactionManager(factory)).transactional(
                repository.claim(request()).doOnNext(batch -> {
                    assertThat(acquired.get()).isEqualTo(2);assertThat(inner.committed.get()).isEqualTo(1);
                    assertThat(inner.closed.get()).isEqualTo(1);assertThat(outer.committed.get()).isZero();assertThat(outer.closed.get()).isZero();
                }).then(Mono.error(expected))).block()).isSameAs(expected);
        assertThat(inner.committed.get()).isEqualTo(1);assertThat(inner.closed.get()).isEqualTo(1);
        assertThat(inner.rolledBack.get()).isZero();assertThat(outer.rolledBack.get()).isEqualTo(1);assertThat(outer.closed.get()).isEqualTo(1);
    }

    @Test void queryFailureRollsBackAndClosesWithoutBatch() {
        var session=session();when(session.connection.createStatement(anyString())).thenThrow(new IllegalArgumentException("statement"));
        assertThatThrownBy(() -> repository().claim(request()).block()).isInstanceOf(IllegalArgumentException.class);
        assertThat(session.rolledBack.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);assertThat(session.committed.get()).isZero();
    }

    @Test void claimSqlBindsScopeAndSamplesClockOnlyAfterMaterializedLocks() {
        var session=session();repository().claim(request()).block();
        assertThat(session.sql).contains("\"custom\"\"schema\".\"custom\"\"outbox\"", "FOR UPDATE SKIP LOCKED", "locked AS MATERIALIZED", "clocked AS MATERIALIZED",
                "pg_catalog.gen_random_uuid()", "lease_generation", "9223372036854775807", "attempts");
        assertThat(session.sql).contains("FROM locked", "clocked.database_now").doesNotContain("NOW()", "processed_at =", "Class.forName");
        assertThat(session.bindings.values()).contains(4,3,20000L);
        assertThat(session.bindings.values().stream().filter(String[].class::isInstance).map(String[].class::cast).findFirst().orElseThrow()).containsExactly("events");
        assertThat(session.definition.getAttribute(io.r2dbc.spi.TransactionDefinition.ISOLATION_LEVEL)).isEqualTo(IsolationLevel.READ_COMMITTED);
    }

    static Stream<Arguments> transitionCounts() {
        return Stream.of(Arguments.of(1L,"APPLIED",TransitionResult.APPLIED),Arguments.of(0L,"ALREADY_APPLIED",TransitionResult.ALREADY_APPLIED),Arguments.of(0L,"STALE",TransitionResult.STALE));
    }
    @ParameterizedTest @MethodSource("transitionCounts")
    void exactTransitionCountAndOutcomeAreReturned(long count,String value,TransitionResult expected) {
        var session=session();session.rows=List.of(Map.of("affected_count",count,"outcome",value));
        assertThat(repository().acknowledge(claim()).block()).isEqualTo(expected);
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(Arguments.of(-1L,"STALE"),Arguments.of(2L,"APPLIED"),Arguments.of(0L,"APPLIED"),Arguments.of(1L,"ALREADY_APPLIED"),Arguments.of(1L,"STALE"),Arguments.of(0L,"unknown"));
    }
    @ParameterizedTest @MethodSource("invalidTransitions")
    void impossibleTransitionPairsRollBack(long count,String value) {
        var session=session();session.rows=List.of(Map.of("affected_count",count,"outcome",value));
        assertThatThrownBy(() -> repository().acknowledge(claim()).block()).isInstanceOf(IllegalStateException.class);
        assertThat(session.rolledBack.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);
    }

    @Test void missingAndMultipleTransitionRowsCannotBecomeStale() {
        var session=session();session.rows=List.of();
        assertThatThrownBy(() -> repository().acknowledge(claim()).block()).isInstanceOf(IllegalStateException.class);
        var next=session();next.rows=List.of(Map.of("affected_count",0L,"outcome","STALE"),Map.of("affected_count",0L,"outcome","STALE"));
        assertThatThrownBy(() -> repository().acknowledge(claim()).block()).isInstanceOf(RuntimeException.class);
        assertThat(session.rolledBack.get()).isEqualTo(1);assertThat(next.rolledBack.get()).isEqualTo(1);
    }

    @Test void ackAndNackUseExactTokenGenerationAndLivePostLockClock() {
        var ack=session();ack.rows=List.of(Map.of("affected_count",1L,"outcome","APPLIED"));repository().acknowledge(claim()).block();
        assertThat(ack.sql).contains("FOR UPDATE", "clocked AS MATERIALIZED", "clocked.lease_until>clocked.database_now", "status='IN_FLIGHT'", "ALREADY_APPLIED");
        assertThat(ack.bindings.values()).contains(11L,TOKEN,1L);
        var nack=session();nack.rows=List.of(Map.of("affected_count",1L,"outcome","APPLIED"));repository().reject(claim(),FailureKind.LISTENER_FAILURE,Duration.ofMillis(23)).block();
        assertThat(nack.sql).contains("clocked.lease_until>clocked.database_now", "CASE WHEN", "next_attempt_at", "clocked.database_now", "error_message");
        assertThat(nack.bindings.values()).contains(23L,false,"RETRY:LISTENER_FAILURE");
    }

    @Test void terminalNackUsesSafeActionSpecificCodeAndNeverRawFailureText() {
        var session=session();session.rows=List.of(Map.of("affected_count",1L,"outcome","APPLIED"));
        repository().reject(claim(),FailureKind.EXPLICIT_DEAD_LETTER,Duration.ZERO).block();
        assertThat(session.bindings.values()).contains(true,"DLQ_DIRECT:EXPLICIT_DEAD_LETTER");
        assertThat(session.sql).contains("error_message=", "ALREADY_APPLIED").doesNotContain("attempts=attempts+1", "lease_generation=lease_generation+1");
    }

    @Test void reaperIsBoundedDoesNotResetCountersOrStealALiveLastAttempt() {
        var session=session();session.rows=List.of(Map.of("affected_count",2L));
        assertThat(repository().reapExhausted(request()).block()).isEqualTo(2L);
        assertThat(session.sql).contains("FOR UPDATE SKIP LOCKED","clocked.database_now","DEAD_LETTER","PROTOCOL_", "lease_until");
        assertThat(session.sql).doesNotContain("attempts=0","lease_generation=0","PROCESSED'");
        assertThat(session.bindings.values()).contains(4,3);assertThat(session.closed.get()).isEqualTo(1);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of(Set.of("events"),0,3,Duration.ofSeconds(1)),Arguments.of(Set.of("events"),1025,3,Duration.ofSeconds(1)),
                Arguments.of(Set.of("events"),1,0,Duration.ofSeconds(1)),Arguments.of(Set.of(),1,3,Duration.ofSeconds(1)),
                Arguments.of(Set.of("events"),1,3,Duration.ZERO),Arguments.of(Set.of("events"),1,3,Duration.ofNanos(1)),
                Arguments.of(Set.of("events"),1,3,Duration.ofDays(31)));
    }
    @ParameterizedTest @MethodSource("invalidRequests")
    void invalidBoundsFailBeforeAnyConnection(Set<String> channels,int limit,int maxAttempts,Duration lease) {
        assertThatThrownBy(() -> repository().claim(new ClaimRequest(channels,limit,maxAttempts,lease)).block()).isInstanceOf(IllegalArgumentException.class);
        assertThat(acquired.get()).isZero();
    }

    @Test void immutableRequestBatchAndPayloadCannotChangeAfterConstruction() {
        var channels=new HashSet<>(Set.of("events"));var request=new ClaimRequest(channels,1,3,Duration.ofSeconds(1));channels.clear();assertThat(request.channels()).containsExactly("events");
        var row=row();payload[0]=99;assertThat(row.payload()[0]).isZero();var bytes=row.payload();bytes[0]=88;assertThat(row.payload()[0]).isZero();
        var claims=new ArrayList<>(List.of(new DeliveryClaim(row,3)));var batch=new ClaimBatch(claims);claims.clear();assertThat(batch.claims()).hasSize(1);
        assertThatThrownBy(() -> batch.claims().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void installerIsExplicitVersionedAtomicAndPreservesRetainedRows() {
        var sql=PostgresDeliverySchema.installationSql("custom\"schema","custom\"outbox");
        assertThat(sql).startsWith("BEGIN;").endsWith("COMMIT;\n");
        assertThat(sql).contains("ACCESS EXCLUSIVE", "lease_token UUID", "lease_generation BIGINT NOT NULL DEFAULT 0", "lease_until TIMESTAMPTZ", "next_attempt_at TIMESTAMPTZ", "COMMENT ON COLUMN", "firefly.eda.delivery/v1");
        assertThat(sql).doesNotContain("IF NOT EXISTS","UPDATE ","DELETE ","DROP ","GRANT ","CREATE ROLE");
        assertThat(acquired.get()).isZero();
    }

    @Test void literalIdentifiersCannotBeReinterpretedAsTemplateTokens() {
        String relation = "\"{{dueIndex}}\".\"{{leaseIndex}}\"";
        var sql = PostgresDeliverySchema.installationSql("{{dueIndex}}", "{{leaseIndex}}");
        assertThat(sql).contains("LOCK TABLE " + relation + " IN ACCESS EXCLUSIVE MODE", "ALTER TABLE " + relation);
    }

    @Test void protocolSqlIsPreparedAfterTransactionLocalCatalogSearchPath() {
        var session = session();repository().claim(request()).block();
        assertThat(session.statements).hasSize(2);
        assertThat(session.statements.getFirst()).isEqualTo("SET LOCAL search_path TO pg_catalog");
        assertThat(session.statements.getLast()).contains("CAST(", "AS pg_catalog.text[]");
        assertThat(acquired.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);
    }

    @Test void protocolVerificationUsesExactCatalogNamespaceAndDoesNotExecuteDdl() {
        var session=session();session.rows=List.of(Map.of("compatible",true));repository().verifyProtocol().block();
        assertThat(session.sql).contains("pg_catalog", "lease_generation", "firefly.eda.delivery/v1");
        assertThat(session.sql).doesNotContain("ALTER ","CREATE ","GRANT ");
        assertThat(session.bindings.values()).contains("custom\"schema","custom\"outbox");
    }
}
