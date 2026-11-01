/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.impl;

import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fireflyframework.eda.consumer.postgres.PostgresConsumerGroupRepository;
import org.fireflyframework.eda.consumer.postgres.PostgresConsumerGroupSchema;
import org.fireflyframework.eda.consumer.postgres.PostgresDeliverySchema;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.r2dbc.core.binding.BindTarget;
import reactor.core.publisher.Mono;

/** Group-bound technical delivery protocol; explicit CTEs preserve atomic lock/clock/fence semantics. */
public final class SpringDataPostgresConsumerGroupRepositoryImpl implements PostgresConsumerGroupRepository {
    private final ConsumerGroupTables tables;
    private final ConsumerGroupId group;
    private final R2dbcEntityTemplate operations;
    private final DeliveryTransactionBoundaryImpl transactions;
    private final Map<String,String> relations;

    public SpringDataPostgresConsumerGroupRepositoryImpl(ConnectionFactory factory, ConsumerGroupTables tables,
            ConsumerGroupId group, Duration timeout) {
        this.tables=Objects.requireNonNull(tables,"tables");
        this.group=Objects.requireNonNull(group,"group");
        this.transactions=new DeliveryTransactionBoundaryImpl(Objects.requireNonNull(factory,"factory"),timeout);
        this.operations=new R2dbcEntityTemplate(DatabaseClient.builder().connectionFactory(factory)
                .bindMarkers(PostgresDialect.INSTANCE.getBindMarkersFactory()).build(),PostgresDialect.INSTANCE);
        this.relations=Map.of("outbox",qualified(tables.outbox()),"registry",qualified(tables.registry()),
                "deliveries",qualified(tables.deliveries()),"redrives",qualified(tables.redrives()));
    }

    @Override
    public Mono<GroupRegistrationResult> registerGroup(GroupSubscription subscription, RetainedReplayPolicy policy) {
        return Mono.defer(()->{
            if(subscription==null||policy!=RetainedReplayPolicy.ALL_RETAINED)
                return Mono.error(new IllegalArgumentException("Explicit ALL_RETAINED group adoption is required"));
            return owned(()->catalog().then(Mono.defer(()->{
                var strategy=operations.getDataAccessStrategy();
                var mapper=strategy.getStatementMapper();
                var insert=mapper.createInsert(target(tables.registry()));
                var value=new RegistrationInsert(group.value(),subscription.channels().stream().sorted().toArray(String[]::new),subscription.maxAttempts(),policy.name());
                var outbound=strategy.getOutboundRow(value);
                for(var column:outbound.keySet())insert=insert.withColumn(column,outbound.get(column));
                return operations.getDatabaseClient().sql(ignoreExisting(mapper.getMappedObject(insert))).fetch().rowsUpdated()
                        .switchIfEmpty(Mono.error(new IllegalStateException("Group insert count is missing")))
                        .flatMap(count->{
                            if(count<0||count>1)return Mono.error(new IllegalStateException("Group insert count is invalid"));
                            return registration().map(existing->{
                                if(!existing.subscription().equals(subscription))throw new IllegalStateException("Existing group subscription is immutable");
                                return count==1?GroupRegistrationResult.CREATED:GroupRegistrationResult.EXISTING;
                            });
                        });
            })));
        });
    }

    private static <T> PreparedOperation<T> ignoreExisting(PreparedOperation<T> original) {
        return new PreparedOperation<>() {
            @Override public T getSource(){return original.getSource();}
            @Override public String toQuery(){return original.toQuery()+" ON CONFLICT (group_id) DO NOTHING";}
            @Override public void bindTo(BindTarget target){original.bindTo(target);}
        };
    }

    @Override
    public Mono<ClaimBatch> claim(ClaimRequest request) {
        return Mono.defer(()->{
            if(request==null)return Mono.error(new IllegalArgumentException("Claim request is required"));
            return owned(()->policy(request).then(count(scoped(MATERIALIZE_SQL,request).bind("maxAttempts",request.maxAttempts()),request.limit()))
                    .then(Mono.defer(()->{
                        var mapper=operations.getDataAccessStrategy().getRowMapper(PostgresDeliveryRow.class);
                        return scoped(CLAIM_SQL,request).bind("leaseMillis",DeliveryBounds.milliseconds(request.leaseDuration(),false))
                                .map((row,metadata)->new DeliveryClaim(mapper.apply(row,metadata),Objects.requireNonNull(row.get("attempt_ceiling",Integer.class),"attempt ceiling")))
                                .all().collectList().map(claims->{
                                    if(claims.size()>request.limit())throw new IllegalStateException("Claim count exceeds requested capacity");
                                    return new ClaimBatch(claims);
                                });
                    })));
        });
    }

    @Override
    public Mono<Long> reapExhausted(ClaimRequest request) {
        return Mono.defer(()->{
            if(request==null)return Mono.error(new IllegalArgumentException("Claim request is required"));
            return owned(()->policy(request).then(count(scoped(REAPER_SQL,request),request.limit())));
        });
    }

    @Override
    public Mono<TransitionResult> acknowledge(DeliveryClaim claim) {
        return Mono.defer(()->{
            if(claim==null)return Mono.error(new IllegalArgumentException("Delivery claim is required"));
            return owned(()->registration().then(transition(fenced(ACK_SQL,claim),false)));
        });
    }

    @Override
    public Mono<TransitionResult> reject(DeliveryClaim claim, FailureKind kind, Duration retryDelay) {
        return Mono.defer(()->{
            if(claim==null||kind==null)return Mono.error(new IllegalArgumentException("Delivery failure identity is required"));
            long millis=DeliveryBounds.milliseconds(retryDelay,true);
            return owned(()->registration().then(transition(fenced(NACK_SQL,claim).bind("direct",kind==FailureKind.EXPLICIT_DEAD_LETTER)
                    .bind("failureKind",kind.name()).bind("retryMillis",millis),false)));
        });
    }

    @Override
    public Mono<Void> verifyProtocol(){return owned(()->catalog().then(registration()).then());}

    @Override
    public Mono<Void> verifyProtocol(ClaimRequest request){
        return Mono.defer(()->request==null?Mono.error(new IllegalArgumentException("Claim request is required"))
                :owned(()->catalog().then(policy(request))));
    }

    @Override
    public Mono<TransitionResult> redrive(DeadLetterRedriveCommand command) {
        return Mono.defer(()->{
            if(command==null)return Mono.error(new IllegalArgumentException("Redrive command is required"));
            return owned(()->registration().flatMap(registration->{
                if(command.additionalAttempts()>registration.maxAttempts())return Mono.error(new IllegalArgumentException("Redrive budget exceeds registered policy"));
                // A subsequent READ_COMMITTED statement sees an audit committed while this row lock waited.
                return statement(REDRIVE_LOCK_SQL).bind("id",command.eventId())
                        .map(operations.getDataAccessStrategy().getRowMapper(PostgresGroupDeliveryRow.class)).one()
                        .flatMap(locked->transition(statement(REDRIVE_SQL).bind("id",command.eventId())
                                .bind("commandId",command.commandId()).bind("token",command.terminalToken())
                                .bind("generation",command.terminalGeneration()).bind("additionalAttempts",command.additionalAttempts())
                                .bind("actor",command.actor()).bind("reason",command.reason()),true))
                        .defaultIfEmpty(TransitionResult.STALE);
            }));
        });
    }

    private Mono<PostgresGroupRegistrationRow> registration() {
        return operations.select(PostgresGroupRegistrationRow.class).from(target(tables.registry()))
                .matching(Query.query(Criteria.where("group_id").is(group.value()))).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("Consumer group requires explicit prior registration")))
                .map(row->{
                    if(!group.value().equals(row.groupId()))throw new IllegalStateException("Loaded group identity is incompatible");
                    row.subscription();
                    return row;
                });
    }

    private Mono<Void> policy(ClaimRequest request) {
        return registration().flatMap(row->row.subscription().equals(new GroupSubscription(request.channels(),request.maxAttempts()))
                ?Mono.<Void>empty():Mono.error(new IllegalStateException("Claim request differs from immutable group subscription")));
    }

    private Mono<Void> catalog() {
        return operations.getDatabaseClient().sql(PROTOCOL_SQL).bind("schema",tables.schema()).bind("outbox",tables.outbox())
                .bind("registry",tables.registry()).bind("deliveries",tables.deliveries()).bind("redrives",tables.redrives())
                .bind("guardName",PostgresConsumerGroupSchema.journalGuardName(tables))
                .bind("mutationName",PostgresConsumerGroupSchema.journalMutationTriggerName(tables))
                .bind("truncateName",PostgresConsumerGroupSchema.journalTruncateTriggerName(tables))
                .bind("guardBody",PostgresConsumerGroupSchema.JOURNAL_GUARD_BODY)
                .map(operations.getDataAccessStrategy().getRowMapper(ProtocolState.class)).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("Group protocol catalog result is missing")))
                .flatMap(row->Boolean.TRUE.equals(row.compatible())?Mono.<Void>empty():Mono.error(new IllegalStateException("Group protocol catalog, journal guards or RLS is incompatible")));
    }

    private Mono<Long> count(DatabaseClient.GenericExecuteSpec statement,int maximum) {
        return statement.map(operations.getDataAccessStrategy().getRowMapper(CountAggregate.class)).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("Group count aggregate is missing")))
                .map(row->{if(row.affectedCount()==null||row.affectedCount()<0||row.affectedCount()>maximum)throw new IllegalStateException("Group count exceeds requested capacity");return row.affectedCount();});
    }

    private Mono<TransitionResult> transition(DatabaseClient.GenericExecuteSpec statement,boolean audit) {
        return statement.map(operations.getDataAccessStrategy().getRowMapper(TransitionAggregate.class)).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("Group transition aggregate is missing")))
                .map(row->{
                    final TransitionResult result;
                    try{result=TransitionResult.valueOf(row.outcome());}catch(RuntimeException error){throw new IllegalStateException("Unknown group transition outcome",error);}
                    long expected=result==TransitionResult.APPLIED?1:0;
                    if(row.affectedCount()==null||row.affectedCount()!=expected||(audit&&(row.auditCount()==null||row.auditCount()!=expected)))
                        throw new IllegalStateException("Group transition count disagrees with outcome");
                    return result;
                });
    }

    private DatabaseClient.GenericExecuteSpec scoped(String sql,ClaimRequest request) {
        return statement(sql).bind("channels",request.channels().stream().sorted().toArray(String[]::new)).bind("limit",request.limit());
    }
    private DatabaseClient.GenericExecuteSpec fenced(String sql,DeliveryClaim claim) {
        return statement(sql).bind("id",claim.row().id()).bind("token",claim.row().leaseToken()).bind("generation",claim.row().leaseGeneration());
    }
    private DatabaseClient.GenericExecuteSpec statement(String sql){return operations.getDatabaseClient().sql(sql(sql)).bind("groupId",group.value());}
    private <T> Mono<T> owned(Supplier<Mono<T>> work){return transactions.execute(()->operations.getDatabaseClient().sql("SET LOCAL search_path TO pg_catalog").then().then(Mono.defer(work)));}
    private String qualified(String table){return PostgresDeliverySchema.qualified(tables.schema(),table);}
    private SqlIdentifier target(String table){return SqlIdentifier.from(SqlIdentifier.quoted(tables.schema().replace("\"","\"\"")),SqlIdentifier.quoted(table.replace("\"","\"\"")));}
    private String sql(String template){return Pattern.compile("\\{\\{([A-Za-z]+)}}").matcher(template).replaceAll(match->Matcher.quoteReplacement(Objects.requireNonNull(relations.get(match.group(1)),"SQL relation")));}

    public record RegistrationInsert(@org.springframework.data.annotation.Id String groupId,String[] channels,int maxAttempts,String replayPolicy){ }
    public record CountAggregate(Long affectedCount){ }
    public record TransitionAggregate(Long affectedCount,Long auditCount,String outcome){ }
    public record ProtocolState(Boolean compatible){ }

    private static final String MATERIALIZE_SQL = """
            WITH missing AS MATERIALIZED (
             SELECT event.id
             FROM {{outbox}} AS event
             WHERE event.channel COLLATE pg_catalog."C" = ANY(CAST(:channels AS pg_catalog.text[]))
             AND NOT EXISTS (
              SELECT 1 FROM {{deliveries}} AS delivery
              WHERE delivery.group_id=:groupId AND delivery.event_id=event.id
             )
             ORDER BY event.id
             LIMIT :limit
            ), appended AS (
             INSERT INTO {{deliveries}}(group_id,event_id,attempt_ceiling)
             SELECT :groupId,missing.id,:maxAttempts FROM missing
             ON CONFLICT(group_id,event_id) DO NOTHING
             RETURNING event_id
            )
            SELECT pg_catalog.count(*) AS affected_count FROM appended;
            """;

    private static final String CLAIM_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT delivery.* FROM {{deliveries}} delivery JOIN {{outbox}} event ON event.id=delivery.event_id
             WHERE delivery.group_id=:groupId AND event.channel=ANY(CAST(:channels AS pg_catalog.text[]))
             AND delivery.attempts>=0 AND delivery.attempts<delivery.attempt_ceiling
             AND delivery.lease_generation>=0 AND delivery.lease_generation<9223372036854775807
             AND ((delivery.status='PENDING' AND (delivery.next_attempt_at IS NULL OR delivery.next_attempt_at<=pg_catalog.clock_timestamp()))
               OR (delivery.status='IN_FLIGHT' AND delivery.lease_until<=pg_catalog.clock_timestamp()))
             ORDER BY COALESCE(delivery.next_attempt_at,delivery.lease_until,event.created_at),event.created_at,event.id
             LIMIT :limit FOR UPDATE OF delivery SKIP LOCKED
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), claim_rows AS (
             UPDATE {{deliveries}} target SET status='IN_FLIGHT',attempts=clocked.attempts+1,
             lease_generation=clocked.lease_generation+1,lease_token=pg_catalog.gen_random_uuid(),
             lease_until=clocked.database_now+CAST(:leaseMillis AS pg_catalog.int8)*INTERVAL '1 millisecond',next_attempt_at=NULL
             FROM clocked WHERE target.delivery_id=clocked.delivery_id AND target.group_id=:groupId
             AND clocked.attempts>=0 AND clocked.attempts<clocked.attempt_ceiling
             AND clocked.lease_generation>=0 AND clocked.lease_generation<9223372036854775807
             AND ((clocked.status='PENDING' AND (clocked.next_attempt_at IS NULL OR clocked.next_attempt_at<=clocked.database_now))
               OR (clocked.status='IN_FLIGHT' AND clocked.lease_until<=clocked.database_now))
             RETURNING target.*
            )
            SELECT event.id,event.destination,event.channel,event.event_type,event.payload,event.headers,event.transaction_id,
             event.publisher_type,event.connection_id,event.event_class,delivery.status,delivery.attempts,delivery.error_message,
             event.created_at,delivery.processed_at,delivery.failed_at,delivery.lease_token,delivery.lease_generation,
             delivery.lease_until,delivery.next_attempt_at,delivery.attempt_ceiling
            FROM claim_rows delivery JOIN {{outbox}} event ON event.id=delivery.event_id;
            """;

    private static final String ACK_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{deliveries}} WHERE group_id=:groupId AND event_id=:id AND lease_token=:token AND lease_generation=:generation FOR UPDATE
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), changed AS (
             UPDATE {{deliveries}} AS target SET status='PROCESSED',processed_at=clocked.database_now,
              error_message=NULL,lease_until=NULL,next_attempt_at=NULL
             FROM clocked WHERE target.delivery_id=clocked.delivery_id AND clocked.status='IN_FLIGHT'
              AND clocked.lease_until>clocked.database_now RETURNING target.event_id
            )
            SELECT (SELECT count(*) FROM changed) AS affected_count,
             CASE WHEN EXISTS(SELECT 1 FROM changed) THEN 'APPLIED'
             WHEN EXISTS(SELECT 1 FROM clocked WHERE status='PROCESSED' AND error_message IS NULL) THEN 'ALREADY_APPLIED'
             ELSE 'STALE' END AS outcome;
            """;

    private static final String NACK_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{deliveries}} WHERE group_id=:groupId AND event_id=:id AND lease_token=:token AND lease_generation=:generation FOR UPDATE
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now,
             (:direct OR attempts>=attempt_ceiling OR lease_generation=9223372036854775807) AS terminal,
             CASE WHEN :direct THEN 'DLQ_DIRECT:' WHEN attempts>=attempt_ceiling THEN 'DLQ_EXHAUSTED:'
             WHEN lease_generation=9223372036854775807 THEN 'DLQ_GENERATION_EXHAUSTED:' ELSE 'RETRY:' END || CAST(:failureKind AS pg_catalog.text) AS failure_code
             FROM locked
            ), changed AS (
             UPDATE {{deliveries}} target SET status=CASE WHEN clocked.terminal THEN 'DEAD_LETTER' ELSE 'PENDING' END,
             failed_at=clocked.database_now,error_message=clocked.failure_code,lease_until=NULL,
             next_attempt_at=CASE WHEN clocked.terminal THEN NULL ELSE clocked.database_now+CAST(:retryMillis AS pg_catalog.int8)*INTERVAL '1 millisecond' END
             FROM clocked WHERE target.delivery_id=clocked.delivery_id AND clocked.status='IN_FLIGHT'
             AND clocked.lease_until>clocked.database_now RETURNING target.event_id
            )
            SELECT (SELECT pg_catalog.count(*) FROM changed) AS affected_count,
             CASE WHEN EXISTS(SELECT 1 FROM changed) THEN 'APPLIED'
             WHEN EXISTS(SELECT 1 FROM clocked WHERE terminal AND status='DEAD_LETTER' AND error_message=failure_code) THEN 'ALREADY_APPLIED'
             ELSE 'STALE' END AS outcome;
            """;

    private static final String REAPER_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT delivery.* FROM {{deliveries}} delivery JOIN {{outbox}} event ON event.id=delivery.event_id
             WHERE delivery.group_id=:groupId AND event.channel=ANY(CAST(:channels AS pg_catalog.text[]))
             AND ((delivery.status='PENDING' AND (delivery.attempts<0 OR delivery.attempts>=delivery.attempt_ceiling OR delivery.lease_generation<0 OR delivery.lease_generation=9223372036854775807)) OR (delivery.status='IN_FLIGHT' AND delivery.lease_until<=pg_catalog.clock_timestamp() AND (delivery.attempts>=delivery.attempt_ceiling OR delivery.lease_generation=9223372036854775807)))
             ORDER BY event.created_at,event.id LIMIT :limit FOR UPDATE OF delivery SKIP LOCKED
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), changed AS (
             UPDATE {{deliveries}} target SET status='DEAD_LETTER',failed_at=clocked.database_now,
             error_message=CASE WHEN clocked.attempts<0 OR clocked.lease_generation<0 THEN 'PROTOCOL_COUNTER_INVALID'
             WHEN clocked.lease_generation=9223372036854775807 THEN 'PROTOCOL_GENERATION_EXHAUSTED'
             ELSE 'PROTOCOL_ATTEMPTS_EXHAUSTED' END,lease_until=NULL,next_attempt_at=NULL
             FROM clocked WHERE target.delivery_id=clocked.delivery_id AND ((clocked.status='PENDING' AND (clocked.attempts<0 OR clocked.attempts>=clocked.attempt_ceiling OR clocked.lease_generation<0 OR clocked.lease_generation=9223372036854775807)) OR (clocked.status='IN_FLIGHT' AND clocked.lease_until<=clocked.database_now AND (clocked.attempts>=clocked.attempt_ceiling OR clocked.lease_generation=9223372036854775807)))
             RETURNING target.event_id
            )
            SELECT pg_catalog.count(*) AS affected_count FROM changed;
            """;

    private static final String REDRIVE_LOCK_SQL = """
            SELECT * FROM {{deliveries}} WHERE group_id=:groupId AND event_id=:id FOR UPDATE;
            """;

    private static final String REDRIVE_SQL = """
            WITH locked AS MATERIALIZED (
             SELECT * FROM {{deliveries}} WHERE group_id=:groupId AND event_id=:id FOR UPDATE
            ), clocked AS MATERIALIZED (
             SELECT locked.*,pg_catalog.clock_timestamp() AS database_now FROM locked
            ), prior AS MATERIALIZED (
             SELECT * FROM {{redrives}} WHERE group_id=:groupId AND command_id=:commandId
            ), identical AS MATERIALIZED (
             SELECT 1 FROM prior WHERE event_id=:id AND terminal_token=:token AND terminal_generation=:generation
             AND additional_attempts=:additionalAttempts AND actor=:actor AND reason=:reason
            ), changed AS (
             UPDATE {{deliveries}} target SET status='PENDING',attempt_ceiling=clocked.attempt_ceiling+:additionalAttempts,
             error_message='REDRIVE_REQUESTED',next_attempt_at=clocked.database_now,lease_until=NULL
             FROM clocked WHERE target.delivery_id=clocked.delivery_id AND clocked.status='DEAD_LETTER'
             AND clocked.lease_token=:token AND clocked.lease_generation=:generation
             AND clocked.lease_generation>0 AND clocked.lease_generation<9223372036854775807
             AND clocked.attempts>=0 AND clocked.attempt_ceiling>=clocked.attempts
             AND CAST(clocked.attempt_ceiling AS pg_catalog.int8)+CAST(:additionalAttempts AS pg_catalog.int8)<=2147483647
             AND NOT EXISTS(SELECT 1 FROM prior)
             RETURNING target.group_id,target.event_id,target.attempts,clocked.attempt_ceiling AS old_ceiling,
             target.attempt_ceiling AS new_ceiling,clocked.error_message AS prior_failure_code,
             clocked.failed_at AS prior_failed_at,clocked.database_now
            ), journaled AS (
             INSERT INTO {{redrives}}(group_id,event_id,command_id,terminal_token,terminal_generation,additional_attempts,
             old_ceiling,new_ceiling,attempts,prior_failure_code,prior_failed_at,actor,reason,redriven_at)
             SELECT group_id,event_id,:commandId,:token,:generation,:additionalAttempts,old_ceiling,new_ceiling,attempts,
             prior_failure_code,prior_failed_at,:actor,:reason,database_now FROM changed
             RETURNING audit_id
            )
            SELECT (SELECT pg_catalog.count(*) FROM changed) AS affected_count,
             (SELECT pg_catalog.count(*) FROM journaled) AS audit_count,
             CASE WHEN EXISTS(SELECT 1 FROM prior) AND NOT EXISTS(SELECT 1 FROM identical) THEN 'CONFLICT'
             WHEN EXISTS(SELECT 1 FROM changed) AND EXISTS(SELECT 1 FROM journaled) THEN 'APPLIED'
             WHEN EXISTS(SELECT 1 FROM identical) THEN 'ALREADY_APPLIED' ELSE 'STALE' END AS outcome;
            """;

    private static final String PROTOCOL_SQL = """
            WITH requested(role,relation_name,marker) AS (VALUES
             ('outbox',CAST(:outbox AS pg_catalog.text),NULL::pg_catalog.text),
             ('registry',CAST(:registry AS pg_catalog.text),'firefly.eda.groups/v1:registry'),
             ('deliveries',CAST(:deliveries AS pg_catalog.text),'firefly.eda.groups/v1:delivery'),
             ('redrives',CAST(:redrives AS pg_catalog.text),'firefly.eda.groups/v1:audit')
            ), relations AS MATERIALIZED (
             SELECT requested.*,c.oid,c.relkind,c.relrowsecurity,c.relforcerowsecurity
             FROM requested LEFT JOIN pg_catalog.pg_namespace n ON n.nspname=:schema
             LEFT JOIN pg_catalog.pg_class c ON c.relnamespace=n.oid AND c.relname=requested.relation_name
            ), expected(role,name,type_name,type_modifier,not_null,default_expression,c_collation) AS (VALUES
             ('outbox','id','int8',-1,true,NULL,false),
             ('outbox','destination','varchar',259,true,NULL,false),
             ('outbox','channel','varchar',67,true,NULL,false),
             ('outbox','event_type','varchar',259,false,NULL,false),
             ('outbox','payload','bytea',-1,true,NULL,false),
             ('outbox','headers','jsonb',-1,false,NULL,false),
             ('outbox','transaction_id','varchar',259,false,NULL,false),
             ('outbox','publisher_type','varchar',54,false,NULL,false),
             ('outbox','connection_id','varchar',54,false,NULL,false),
             ('outbox','event_class','varchar',504,false,NULL,false),
             ('outbox','status','varchar',24,true,NULL,false),
             ('outbox','attempts','int4',-1,true,NULL,false),
             ('outbox','error_message','text',-1,false,NULL,false),
             ('outbox','created_at','timestamptz',-1,true,NULL,false),
             ('outbox','processed_at','timestamptz',-1,false,NULL,false),
             ('outbox','failed_at','timestamptz',-1,false,NULL,false),
             ('outbox','lease_token','uuid',-1,false,NULL,false),
             ('outbox','lease_generation','int8',-1,true,'0',false),
             ('outbox','lease_until','timestamptz',-1,false,NULL,false),
             ('outbox','next_attempt_at','timestamptz',-1,false,NULL,false),
             ('registry','group_id','varchar',259,true,NULL,true),
             ('registry','channels','_text',-1,true,NULL,true),
             ('registry','max_attempts','int4',-1,true,NULL,false),
             ('registry','replay_policy','varchar',36,true,NULL,false),
             ('registry','registered_at','timestamptz',-1,true,'clock_timestamp()',false),
             ('deliveries','delivery_id','uuid',-1,true,'gen_random_uuid()',false),
             ('deliveries','group_id','varchar',259,true,NULL,true),
             ('deliveries','event_id','int8',-1,true,NULL,false),
             ('deliveries','status','varchar',24,true,'''PENDING''::character varying',false),
             ('deliveries','attempts','int4',-1,true,'0',false),
             ('deliveries','attempt_ceiling','int4',-1,true,NULL,false),
             ('deliveries','error_message','text',-1,false,NULL,false),
             ('deliveries','processed_at','timestamptz',-1,false,NULL,false),
             ('deliveries','failed_at','timestamptz',-1,false,NULL,false),
             ('deliveries','lease_token','uuid',-1,false,NULL,false),
             ('deliveries','lease_generation','int8',-1,true,'0',false),
             ('deliveries','lease_until','timestamptz',-1,false,NULL,false),
             ('deliveries','next_attempt_at','timestamptz',-1,false,NULL,false),
             ('redrives','audit_id','uuid',-1,true,'gen_random_uuid()',false),
             ('redrives','group_id','varchar',259,true,NULL,true),
             ('redrives','event_id','int8',-1,true,NULL,false),
             ('redrives','command_id','uuid',-1,true,NULL,false),
             ('redrives','terminal_token','uuid',-1,true,NULL,false),
             ('redrives','terminal_generation','int8',-1,true,NULL,false),
             ('redrives','additional_attempts','int4',-1,true,NULL,false),
             ('redrives','old_ceiling','int4',-1,true,NULL,false),
             ('redrives','new_ceiling','int4',-1,true,NULL,false),
             ('redrives','attempts','int4',-1,true,NULL,false),
             ('redrives','prior_failure_code','text',-1,false,NULL,false),
             ('redrives','prior_failed_at','timestamptz',-1,false,NULL,false),
             ('redrives','actor','varchar',259,true,NULL,true),
             ('redrives','reason','text',-1,true,NULL,true),
             ('redrives','redriven_at','timestamptz',-1,true,NULL,false)
            ), keys(role,kind,columns) AS (VALUES
             ('outbox','p',ARRAY['id']::pg_catalog.text[]),
             ('registry','p',ARRAY['group_id']::pg_catalog.text[]),
             ('deliveries','p',ARRAY['delivery_id']::pg_catalog.text[]),
             ('deliveries','u',ARRAY['group_id','event_id']::pg_catalog.text[]),
             ('redrives','p',ARRAY['audit_id']::pg_catalog.text[]),
             ('redrives','u',ARRAY['group_id','command_id']::pg_catalog.text[])
            ), foreign_keys(role,columns,target_role,target_columns) AS (VALUES
             ('deliveries',ARRAY['group_id']::pg_catalog.text[],'registry',ARRAY['group_id']::pg_catalog.text[]),
             ('deliveries',ARRAY['event_id']::pg_catalog.text[],'outbox',ARRAY['id']::pg_catalog.text[]),
             ('redrives',ARRAY['group_id','event_id']::pg_catalog.text[],'deliveries',ARRAY['group_id','event_id']::pg_catalog.text[])
            ), journal_guard AS MATERIALIZED (
             SELECT p.oid FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
             JOIN pg_catalog.pg_language l ON l.oid=p.prolang
             WHERE n.nspname=:schema AND p.proname=:guardName AND p.pronargs=0
             AND p.prorettype='pg_catalog.trigger'::pg_catalog.regtype AND p.prokind='f'
             AND NOT p.prosecdef AND l.lanname='plpgsql' AND p.prosrc=:guardBody
             AND p.proconfig=ARRAY['search_path=pg_catalog']::pg_catalog.text[]
            )
            SELECT
             (SELECT pg_catalog.count(*)=4 AND pg_catalog.count(oid)=4 FROM relations)
             AND NOT EXISTS(SELECT 1 FROM relations WHERE relrowsecurity OR relforcerowsecurity
               OR (role='outbox' AND relkind NOT IN ('r','p')) OR (role<>'outbox' AND relkind<>'r')
               OR (marker IS NOT NULL AND pg_catalog.obj_description(oid,'pg_class') IS DISTINCT FROM marker))
             AND NOT EXISTS(SELECT 1 FROM expected e JOIN relations r ON r.role=e.role
             LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid=r.oid AND a.attname=e.name AND a.attnum>0 AND NOT a.attisdropped
             LEFT JOIN pg_catalog.pg_type t ON t.oid=a.atttypid
             LEFT JOIN pg_catalog.pg_namespace tn ON tn.oid=t.typnamespace
             LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
             LEFT JOIN pg_catalog.pg_collation coll ON coll.oid=a.attcollation
             LEFT JOIN pg_catalog.pg_namespace cn ON cn.oid=coll.collnamespace
             WHERE a.attnum IS NULL OR t.typname<>e.type_name OR tn.nspname<>'pg_catalog'
             OR a.atttypmod<>e.type_modifier OR a.attnotnull<>e.not_null
             OR (e.default_expression IS NOT NULL AND pg_catalog.pg_get_expr(d.adbin,d.adrelid) IS DISTINCT FROM e.default_expression)
             OR (e.c_collation AND (coll.collname IS DISTINCT FROM 'C' OR cn.nspname IS DISTINCT FROM 'pg_catalog')))
             AND NOT EXISTS(SELECT 1 FROM relations r WHERE r.role<>'outbox' AND
               (SELECT pg_catalog.count(*) FROM pg_catalog.pg_attribute a WHERE a.attrelid=r.oid AND a.attnum>0 AND NOT a.attisdropped)
               <> (SELECT pg_catalog.count(*) FROM expected e WHERE e.role=r.role))
             AND NOT EXISTS(SELECT 1 FROM keys k JOIN relations r ON r.role=k.role
             WHERE NOT EXISTS(SELECT 1 FROM pg_catalog.pg_constraint c WHERE c.conrelid=r.oid
             AND c.contype::pg_catalog.text=k.kind AND c.convalidated AND NOT c.condeferrable
             AND ARRAY(SELECT a.attname::pg_catalog.text FROM pg_catalog.unnest(c.conkey) WITH ORDINALITY key(attnum,position)
               JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=key.attnum ORDER BY key.position)=k.columns))
             AND NOT EXISTS(SELECT 1 FROM foreign_keys fk JOIN relations source ON source.role=fk.role
             JOIN relations target ON target.role=fk.target_role
             WHERE NOT EXISTS(SELECT 1 FROM pg_catalog.pg_constraint c WHERE c.conrelid=source.oid AND c.confrelid=target.oid
             AND c.contype='f' AND c.convalidated AND NOT c.condeferrable AND c.confdeltype='r' AND c.confupdtype='a'
             AND ARRAY(SELECT a.attname::pg_catalog.text FROM pg_catalog.unnest(c.conkey) WITH ORDINALITY key(attnum,position)
               JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=key.attnum ORDER BY key.position)=fk.columns
             AND ARRAY(SELECT a.attname::pg_catalog.text FROM pg_catalog.unnest(c.confkey) WITH ORDINALITY key(attnum,position)
               JOIN pg_catalog.pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=key.attnum ORDER BY key.position)=fk.target_columns))
             AND NOT EXISTS(SELECT 1 FROM relations r WHERE r.role<>'outbox' AND
               ((SELECT pg_catalog.count(*) FROM pg_catalog.pg_constraint c WHERE c.conrelid=r.oid AND c.contype='c' AND c.convalidated)
                 <>CASE r.role WHEN 'registry' THEN 4 WHEN 'deliveries' THEN 7 ELSE 7 END
               OR EXISTS(SELECT 1 FROM pg_catalog.pg_constraint c WHERE c.conrelid=r.oid AND NOT c.convalidated)))
             AND EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a JOIN relations r ON r.role='outbox' AND a.attrelid=r.oid
               WHERE a.attname='lease_generation' AND NOT a.attisdropped AND pg_catalog.col_description(r.oid,a.attnum)='firefly.eda.delivery/v1')
             AND (SELECT pg_catalog.count(*) FROM journal_guard)=1
             AND (SELECT pg_catalog.count(*) FROM pg_catalog.pg_trigger t JOIN relations r ON r.role='redrives' AND t.tgrelid=r.oid
               WHERE NOT t.tgisinternal)=2
             AND NOT EXISTS(SELECT 1 FROM (VALUES (CAST(:mutationName AS pg_catalog.text),26),(CAST(:truncateName AS pg_catalog.text),34)) wanted(name,kind)
               WHERE NOT EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t JOIN relations r ON r.role='redrives' AND t.tgrelid=r.oid
                 WHERE t.tgname=wanted.name AND t.tgtype=wanted.kind AND t.tgenabled='A' AND NOT t.tgisinternal
                 AND t.tgfoid=(SELECT oid FROM journal_guard) AND t.tgnargs=0 AND t.tgqual IS NULL AND t.tgattr=''::pg_catalog.int2vector))
             AND (SELECT pg_catalog.count(*) FROM pg_catalog.pg_index i JOIN relations r ON r.role='deliveries' AND i.indrelid=r.oid
               WHERE i.indisvalid AND i.indisready AND NOT i.indisunique AND i.indpred IS NOT NULL AND i.indexprs IS NULL
               AND ARRAY(SELECT a.attname::pg_catalog.text FROM pg_catalog.unnest(i.indkey::pg_catalog.int2[]) WITH ORDINALITY key(attnum,position)
                JOIN pg_catalog.pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=key.attnum ORDER BY key.position)
                 IN (ARRAY['group_id','next_attempt_at','event_id']::pg_catalog.text[],ARRAY['group_id','lease_until','event_id']::pg_catalog.text[]))=2
             AS compatible;
            """;
}
