/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.impl;

import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.fireflyframework.eda.consumer.postgres.PostgresDeliveryRepository;
import org.fireflyframework.eda.consumer.postgres.PostgresDeliverySchema;
import org.fireflyframework.eda.consumer.postgres.model.ClaimBatch;
import org.fireflyframework.eda.consumer.postgres.model.ClaimRequest;
import org.fireflyframework.eda.consumer.postgres.model.DeliveryBounds;
import org.fireflyframework.eda.consumer.postgres.model.DeliveryClaim;
import org.fireflyframework.eda.consumer.postgres.model.FailureKind;
import org.fireflyframework.eda.consumer.postgres.model.PostgresDeliveryRow;
import org.fireflyframework.eda.consumer.postgres.model.TransitionResult;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/** Mapped technical outbox protocol; native CTEs make lock/clock/fence one database statement. */
public final class SpringDataPostgresDeliveryRepository implements PostgresDeliveryRepository {
    private final String schema;
    private final String table;
    private final String qualified;
    private final R2dbcEntityTemplate operations;
    private final DeliveryTransactionBoundary transactions;

    public SpringDataPostgresDeliveryRepository(ConnectionFactory factory, String schema, String table, Duration timeout) {
        Objects.requireNonNull(factory, "factory");
        this.qualified = PostgresDeliverySchema.qualified(schema, table);
        this.schema = schema;
        this.table = table;
        this.transactions = new DeliveryTransactionBoundary(factory, timeout);
        this.operations = new R2dbcEntityTemplate(DatabaseClient.builder().connectionFactory(factory)
                .bindMarkers(PostgresDialect.INSTANCE.getBindMarkersFactory()).build(), PostgresDialect.INSTANCE);
    }

    @Override
    public Mono<ClaimBatch> claim(ClaimRequest request) {
        return Mono.defer(() -> {
            if (request == null) return Mono.error(new IllegalArgumentException("Claim request is required"));
            return inTransaction(() -> operations.getDatabaseClient().sql(sql(CLAIM_SQL))
                    .bind("channels", channels(request)).bind("limit", request.limit()).bind("maxAttempts", request.maxAttempts())
                    .bind("leaseMillis", DeliveryBounds.milliseconds(request.leaseDuration(), false))
                    .map(operations.getDataAccessStrategy().getRowMapper(PostgresDeliveryRow.class)).all()
                    .map(row -> new DeliveryClaim(row, request.maxAttempts())).collectList().map(claims -> {
                        if (claims.size() > request.limit()) throw new IllegalStateException("Claim count exceeds requested capacity");
                        return new ClaimBatch(claims);
                    }));
        });
    }

    @Override
    public Mono<Long> reapExhausted(ClaimRequest request) {
        return Mono.defer(() -> {
            if (request == null) return Mono.error(new IllegalArgumentException("Claim request is required"));
            return inTransaction(() -> operations.getDatabaseClient().sql(sql(REAPER_SQL))
                    .bind("channels", channels(request)).bind("limit", request.limit()).bind("maxAttempts", request.maxAttempts())
                    .map(operations.getDataAccessStrategy().getRowMapper(CountAggregate.class)).one()
                    .switchIfEmpty(Mono.error(new IllegalStateException("Reaper aggregate is missing")))
                    .map(count -> {
                        if (count.affectedCount() == null || count.affectedCount() < 0 || count.affectedCount() > request.limit()) {
                            throw new IllegalStateException("Reaper count exceeds requested capacity");
                        }
                        return count.affectedCount();
                    }));
        });
    }

    @Override
    public Mono<TransitionResult> acknowledge(DeliveryClaim claim) {
        return Mono.defer(() -> {
            if (claim == null) return Mono.error(new IllegalArgumentException("Delivery claim is required"));
            return inTransaction(() -> transition(fenced(ACK_SQL, claim)));
        });
    }

    @Override
    public Mono<TransitionResult> reject(DeliveryClaim claim, FailureKind kind, Duration retryDelay) {
        return Mono.defer(() -> {
            if (claim == null || kind == null) return Mono.error(new IllegalArgumentException("Delivery failure identity is required"));
            long millis = DeliveryBounds.milliseconds(retryDelay, true);
            boolean direct = kind == FailureKind.EXPLICIT_DEAD_LETTER;
            boolean exhausted = claim.row().attempts() >= claim.maxAttempts();
            boolean generationExhausted = claim.row().leaseGeneration() == Long.MAX_VALUE;
            boolean terminal = direct || exhausted || generationExhausted;
            String code = (direct ? "DLQ_DIRECT:" : exhausted ? "DLQ_EXHAUSTED:"
                    : generationExhausted ? "DLQ_GENERATION_EXHAUSTED:" : "RETRY:") + kind.name();
            return inTransaction(() -> transition(fenced(NACK_SQL, claim)
                    .bind("terminal", terminal).bind("failureCode", code).bind("retryMillis", millis)));
        });
    }

    @Override
    public Mono<Void> verifyProtocol() {
        return inTransaction(() -> operations.getDatabaseClient().sql(PROTOCOL_SQL)
                .bind("schema", schema).bind("table", table)
                .map(operations.getDataAccessStrategy().getRowMapper(ProtocolState.class)).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("Delivery protocol relation is missing")))
                .flatMap(state -> Boolean.TRUE.equals(state.compatible()) ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException("Delivery protocol shape, marker, identity or RLS is incompatible"))));
    }

    private DatabaseClient.GenericExecuteSpec fenced(String query, DeliveryClaim claim) {
        return operations.getDatabaseClient().sql(sql(query)).bind("id", claim.row().id())
                .bind("token", claim.row().leaseToken()).bind("generation", claim.row().leaseGeneration());
    }

    private Mono<TransitionResult> transition(DatabaseClient.GenericExecuteSpec statement) {
        return statement.map(operations.getDataAccessStrategy().getRowMapper(TransitionAggregate.class)).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("Transition aggregate is missing")))
                .map(row -> {
                    final TransitionResult result;
                    try { result = TransitionResult.valueOf(row.outcome()); }
                    catch (RuntimeException error) { throw new IllegalStateException("Unknown delivery transition outcome", error); }
                    long expected = result == TransitionResult.APPLIED ? 1 : 0;
                    if (row.affectedCount() == null || row.affectedCount() != expected) {
                        throw new IllegalStateException("Delivery transition count disagrees with outcome");
                    }
                    return result;
                });
    }

    private <T> Mono<T> inTransaction(java.util.function.Supplier<Mono<T>> work) {
        return transactions.execute(() -> operations.getDatabaseClient().sql("SET LOCAL search_path TO pg_catalog")
                .then().then(Mono.defer(work)));
    }

    private String sql(String query) { return query.replace("{{outbox}}", qualified); }
    private String[] channels(ClaimRequest request) { return request.channels().stream().sorted().toArray(String[]::new); }

    public record TransitionAggregate(Long affectedCount, String outcome) { }
    public record CountAggregate(Long affectedCount) { }
    public record ProtocolState(Boolean compatible) { }

    private static final String CLAIM_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{outbox}}
             WHERE channel = ANY(CAST(:channels AS pg_catalog.text[]))
               AND attempts >= 0 AND attempts < :maxAttempts
               AND lease_generation >= 0 AND lease_generation < 9223372036854775807
               AND ((status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= pg_catalog.clock_timestamp()))
                 OR (status='IN_FLIGHT' AND lease_until <= pg_catalog.clock_timestamp()))
             ORDER BY COALESCE(next_attempt_at,lease_until,created_at),created_at,id
             LIMIT :limit FOR UPDATE SKIP LOCKED
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            )
            UPDATE {{outbox}} AS target SET status='IN_FLIGHT',attempts=clocked.attempts+1,
             lease_generation=clocked.lease_generation+1,lease_token=pg_catalog.gen_random_uuid(),
             lease_until=clocked.database_now + CAST(:leaseMillis AS pg_catalog.int8)*INTERVAL '1 millisecond',next_attempt_at=NULL
            FROM clocked WHERE target.id=clocked.id
             AND clocked.attempts>=0 AND clocked.attempts<:maxAttempts
             AND clocked.lease_generation>=0 AND clocked.lease_generation<9223372036854775807
             AND ((clocked.status='PENDING' AND (clocked.next_attempt_at IS NULL OR clocked.next_attempt_at<=clocked.database_now))
               OR (clocked.status='IN_FLIGHT' AND clocked.lease_until<=clocked.database_now))
            RETURNING target.*;
            """;

    private static final String ACK_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{outbox}} WHERE id=:id AND lease_token=:token AND lease_generation=:generation FOR UPDATE
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), changed AS (
             UPDATE {{outbox}} AS target SET status='PROCESSED',processed_at=clocked.database_now,
              error_message=NULL,lease_until=NULL,next_attempt_at=NULL
             FROM clocked WHERE target.id=clocked.id AND clocked.status='IN_FLIGHT'
              AND clocked.lease_until>clocked.database_now RETURNING target.id
            )
            SELECT (SELECT count(*) FROM changed) AS affected_count,
             CASE WHEN EXISTS(SELECT 1 FROM changed) THEN 'APPLIED'
             WHEN EXISTS(SELECT 1 FROM clocked WHERE status='PROCESSED' AND error_message IS NULL) THEN 'ALREADY_APPLIED'
             ELSE 'STALE' END AS outcome;
            """;

    private static final String NACK_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{outbox}} WHERE id=:id AND lease_token=:token AND lease_generation=:generation FOR UPDATE
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), changed AS (
             UPDATE {{outbox}} AS target SET status=CASE WHEN :terminal THEN 'DEAD_LETTER' ELSE 'PENDING' END,
              failed_at=clocked.database_now,error_message=:failureCode,lease_until=NULL,
              next_attempt_at=CASE WHEN :terminal THEN NULL ELSE clocked.database_now+CAST(:retryMillis AS pg_catalog.int8)*INTERVAL '1 millisecond' END
             FROM clocked WHERE target.id=clocked.id AND clocked.status='IN_FLIGHT'
              AND clocked.lease_until>clocked.database_now RETURNING target.id
            )
            SELECT (SELECT count(*) FROM changed) AS affected_count,
             CASE WHEN EXISTS(SELECT 1 FROM changed) THEN 'APPLIED'
             WHEN :terminal AND EXISTS(SELECT 1 FROM clocked WHERE status='DEAD_LETTER' AND error_message=:failureCode) THEN 'ALREADY_APPLIED'
             ELSE 'STALE' END AS outcome;
            """;

    private static final String REAPER_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{outbox}} WHERE channel=ANY(CAST(:channels AS pg_catalog.text[]))
             AND ((status='PENDING' AND (attempts<0 OR attempts>=:maxAttempts OR lease_generation<0 OR lease_generation=9223372036854775807))
             OR (status='IN_FLIGHT' AND (lease_until<=pg_catalog.clock_timestamp() OR lease_until IS NULL OR lease_token IS NULL OR lease_generation<=0 OR attempts<=0)
             AND (attempts<0 OR attempts>=:maxAttempts OR lease_generation<0 OR lease_generation=9223372036854775807 OR lease_until IS NULL OR lease_token IS NULL OR lease_generation<=0 OR attempts<=0)))
             ORDER BY created_at,id LIMIT :limit FOR UPDATE SKIP LOCKED
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), changed AS (
             UPDATE {{outbox}} AS target SET status='DEAD_LETTER',failed_at=clocked.database_now,
             error_message=CASE WHEN clocked.attempts<0 OR clocked.lease_generation<0 THEN 'PROTOCOL_COUNTER_INVALID'
             WHEN clocked.lease_generation=9223372036854775807 THEN 'PROTOCOL_GENERATION_EXHAUSTED'
             WHEN clocked.attempts>=:maxAttempts THEN 'PROTOCOL_ATTEMPTS_EXHAUSTED' ELSE 'PROTOCOL_LEASE_INVALID' END,
             lease_until=NULL,next_attempt_at=NULL
             FROM clocked WHERE target.id=clocked.id
             AND ((clocked.status='PENDING' AND (clocked.attempts<0 OR clocked.attempts>=:maxAttempts OR clocked.lease_generation<0 OR clocked.lease_generation=9223372036854775807))
             OR (clocked.status='IN_FLIGHT' AND (clocked.lease_until<=clocked.database_now OR clocked.lease_until IS NULL OR clocked.lease_token IS NULL OR clocked.lease_generation<=0 OR clocked.attempts<=0)
             AND (clocked.attempts<0 OR clocked.attempts>=:maxAttempts OR clocked.lease_generation<0 OR clocked.lease_generation=9223372036854775807 OR clocked.lease_until IS NULL OR clocked.lease_token IS NULL OR clocked.lease_generation<=0 OR clocked.attempts<=0)))
             RETURNING target.id
            )
            SELECT count(*) AS affected_count FROM changed;
            """;

    private static final String PROTOCOL_SQL = """
            WITH expected(name,type_name,type_modifier,not_null) AS (VALUES
             ('id','int8',-1,true),('destination','varchar',259,true),('channel','varchar',67,true),
             ('event_type','varchar',259,false),('payload','bytea',-1,true),('headers','jsonb',-1,false),
             ('transaction_id','varchar',259,false),('publisher_type','varchar',54,false),('connection_id','varchar',54,false),
             ('event_class','varchar',504,false),('status','varchar',24,true),('attempts','int4',-1,true),
             ('error_message','text',-1,false),('created_at','timestamptz',-1,true),('processed_at','timestamptz',-1,false),
             ('failed_at','timestamptz',-1,false),('lease_token','uuid',-1,false),('lease_generation','int8',-1,true),
             ('lease_until','timestamptz',-1,false),('next_attempt_at','timestamptz',-1,false)
            )
            SELECT c.relkind IN ('r','p') AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity
             AND NOT EXISTS(SELECT 1 FROM expected e LEFT JOIN pg_catalog.pg_attribute a
             ON a.attrelid=c.oid AND a.attname=e.name AND NOT a.attisdropped
             LEFT JOIN pg_catalog.pg_type t ON t.oid=a.atttypid
             LEFT JOIN pg_catalog.pg_namespace type_namespace ON type_namespace.oid=t.typnamespace
             WHERE a.attnum IS NULL OR t.typname<>e.type_name OR type_namespace.nspname<>'pg_catalog'
             OR a.atttypmod<>e.type_modifier OR a.attnotnull<>e.not_null)
             AND EXISTS(SELECT 1 FROM pg_catalog.pg_index i JOIN pg_catalog.pg_attribute id_column
             ON id_column.attrelid=c.oid AND id_column.attname='id' AND NOT id_column.attisdropped
             WHERE i.indrelid=c.oid AND i.indisprimary AND i.indnkeyatts=1 AND i.indkey[0]=id_column.attnum)
             AND EXISTS(SELECT 1 FROM pg_catalog.pg_attribute generation JOIN pg_catalog.pg_attrdef defaults
             ON defaults.adrelid=c.oid AND defaults.adnum=generation.attnum
             WHERE generation.attrelid=c.oid AND generation.attname='lease_generation' AND NOT generation.attisdropped
             AND pg_catalog.col_description(c.oid,generation.attnum)='firefly.eda.delivery/v1'
             AND pg_catalog.pg_get_expr(defaults.adbin,defaults.adrelid)='0') AS compatible
            FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace namespace ON namespace.oid=c.relnamespace
            WHERE namespace.nspname=:schema AND c.relname=:table;
            """;

}
