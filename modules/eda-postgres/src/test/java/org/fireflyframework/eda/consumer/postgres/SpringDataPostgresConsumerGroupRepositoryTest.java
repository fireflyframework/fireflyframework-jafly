package org.fireflyframework.eda.consumer.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresConsumerGroupRepositoryImpl;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.*;

class SpringDataPostgresConsumerGroupRepositoryTest {
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-31T00:00:00Z");
    static final UUID TOKEN=UUID.fromString("00000000-0000-4000-8000-000000000001");
    final ConnectionFactory factory=mock(ConnectionFactory.class);
    final List<Session> sessions=new ArrayList<>();
    final AtomicInteger acquired=new AtomicInteger();
    SpringDataPostgresConsumerGroupRepositoryTest(){
        when(factory.getMetadata()).thenReturn(()->"PostgreSQL");
        doReturn(Mono.defer(()->Mono.just(sessions.get(acquired.getAndIncrement()).connection))).when(factory).create();
    }
    ConsumerGroupTables tables(){return new ConsumerGroupTables("scope\"schema","outbox","registry","deliveries","journal");}
    SpringDataPostgresConsumerGroupRepositoryImpl repository(){return repository("group-A");}
    SpringDataPostgresConsumerGroupRepositoryImpl repository(String group){return new SpringDataPostgresConsumerGroupRepositoryImpl(factory,tables(),new ConsumerGroupId(group),Duration.ofSeconds(10));}
    GroupSubscription subscription(){return new GroupSubscription(Set.of("events"),3);}
    ClaimRequest request(){return new ClaimRequest(Set.of("events"),4,3,Duration.ofSeconds(20));}
    DeliveryClaim claim(){return new DeliveryClaim(new PostgresDeliveryRow(11L,"orders.created","events",null,new byte[]{0,1,(byte)255},Json.of("{\"a\":12.3400}"),null,"POSTGRES","default","untrusted.MustNotLoad","IN_FLIGHT",1,null,NOW,null,null,TOKEN,1,NOW.plusSeconds(20),null),7);}
    DeadLetterRedriveCommand command(){return new DeadLetterRedriveCommand(UUID.fromString("00000000-0000-4000-8000-000000000002"),11,TOKEN,1,3,"operator-supplied","reviewed reason");}
    Map<String,Object> registration(){return new LinkedHashMap<>(Map.of("group_id","group-A","channels",new String[]{"events"},"max_attempts",3,"replay_policy","ALL_RETAINED","registered_at",NOW));}
    Map<String,Object> event(){
        var row=claim().row();var m=new LinkedHashMap<String,Object>();
        m.put("id",row.id());m.put("destination",row.destination());m.put("channel",row.channel());m.put("event_type",null);m.put("payload",row.payload());m.put("headers",row.headers());m.put("transaction_id",null);m.put("publisher_type","POSTGRES");m.put("connection_id","default");m.put("event_class",row.eventClass());m.put("status","IN_FLIGHT");m.put("attempts",1);m.put("error_message",null);m.put("created_at",NOW);m.put("processed_at",null);m.put("failed_at",null);m.put("lease_token",TOKEN);m.put("lease_generation",1L);m.put("lease_until",NOW.plusSeconds(20));m.put("next_attempt_at",null);m.put("attempt_ceiling",7);return m;
    }
    Map<String,Object> delivery(){
        var m=new LinkedHashMap<String,Object>();m.put("delivery_id",UUID.randomUUID());m.put("group_id","group-A");m.put("event_id",11L);m.put("status","DEAD_LETTER");m.put("attempts",3);m.put("attempt_ceiling",3);m.put("error_message","DLQ_EXHAUSTED:LISTENER_FAILURE");m.put("processed_at",null);m.put("failed_at",NOW);m.put("lease_token",TOKEN);m.put("lease_generation",1L);m.put("lease_until",null);m.put("next_attempt_at",null);return m;
    }
    Session session(){var x=new Session();sessions.add(x);return x;}
    final class Execution {
        final String sql;final Map<Integer,Object> bindings=new LinkedHashMap<>();
        Execution(String sql){this.sql=sql;}
    }
    final class Session {
        final Connection connection=mock(Connection.class);
        final List<Execution> statements=new ArrayList<>();
        final AtomicInteger committed=new AtomicInteger(),rolledBack=new AtomicInteger(),closed=new AtomicInteger();
        Mono<Void> commitGate=Mono.empty(),closeGate=Mono.empty();
        Map<String,Object> registration=SpringDataPostgresConsumerGroupRepositoryTest.this.registration();
        Map<String,Object> delivery=SpringDataPostgresConsumerGroupRepositoryTest.this.delivery();
        List<Map<String,Object>> claimRows=List.of(event());
        List<Map<String,Object>> aggregateRows=List.of(Map.of("affected_count",1L,"audit_count",1L,"outcome","APPLIED"));
        List<Map<String,Object>> materializationRows=List.of(Map.of("affected_count",1L));
        long inserted=1;boolean compatible=true,hangClaim;
        @SuppressWarnings({"unchecked","rawtypes"}) Session(){
            when(connection.isAutoCommit()).thenReturn(true);
            when(connection.setAutoCommit(anyBoolean())).thenReturn(Mono.empty());
            when(connection.beginTransaction(any(io.r2dbc.spi.TransactionDefinition.class))).thenReturn(Mono.empty());
            when(connection.commitTransaction()).thenAnswer(x->Mono.defer(()->{committed.incrementAndGet();return commitGate;}));
            when(connection.rollbackTransaction()).thenReturn(Mono.fromRunnable(rolledBack::incrementAndGet));
            when(connection.close()).thenAnswer(x->Mono.defer(()->{closed.incrementAndGet();return closeGate;}));
            when(connection.createStatement(anyString())).thenAnswer(call->{
                var execution=new Execution(call.getArgument(0));statements.add(execution);var statement=mock(Statement.class,RETURNS_SELF);
                when(statement.bind(anyInt(),any())).thenAnswer(binding->{Object value=binding.getArgument(1);execution.bindings.put(binding.getArgument(0),value instanceof io.r2dbc.spi.Parameter p?p.getValue():value);return statement;});
                when(statement.bindNull(anyInt(),any())).thenAnswer(binding->{execution.bindings.put(binding.getArgument(0),null);return statement;});
                var result=mock(Result.class);when(result.getRowsUpdated()).thenAnswer(x->Mono.just(execution.sql.startsWith("INSERT")?inserted:0L));
                when(result.map(any(BiFunction.class))).thenAnswer(mapping->{
                    BiFunction<Row,RowMetadata,Object> mapper=mapping.getArgument(0);List<Map<String,Object>> rows;
                    String sql=execution.sql;
                    if(sql.contains("pg_catalog.pg_class"))rows=List.of(Map.of("compatible",compatible));
                    else if(sql.startsWith("SELECT")&&sql.contains("FOR UPDATE"))rows=delivery==null?List.of():List.of(delivery);
                    else if(sql.startsWith("SELECT")&&sql.contains("registry"))rows=registration==null?List.of():List.of(registration);
                    else if(sql.contains("missing AS MATERIALIZED"))rows=materializationRows;
                    else if(sql.contains("claim_rows AS"))rows=claimRows;
                    else rows=aggregateRows;
                    return Flux.fromIterable(rows).map(values->{
                        var row=mock(Row.class);var metadata=mock(RowMetadata.class);var columns=new ArrayList<ColumnMetadata>();
                        values.forEach((name,value)->{var column=mock(ColumnMetadata.class);when(column.getName()).thenReturn(name);doReturn(value!=null?value.getClass():Object.class).when(column).getJavaType();columns.add(column);when(metadata.getColumnMetadata(name)).thenReturn(column);});
                        doReturn(columns).when(metadata).getColumnMetadatas();when(row.getMetadata()).thenReturn(metadata);
                        when(row.get(anyString())).thenAnswer(read->values.get(read.getArgument(0)));
                        when(row.get(anyString(),any(Class.class))).thenAnswer(read->values.get(read.getArgument(0)));
                        return mapper.apply(row,metadata);
                    });
                });
                doReturn(execution.sql.contains("claim_rows AS")&&hangClaim?Flux.never():Flux.just(result)).when(statement).execute();return statement;
            });
        }
        Execution last(){return statements.getLast();}
    }
    @Test void registrationUsesActualGeneratedInsertDefaultsAndExactAssignedIdCount(){
        var x=session();assertThat(repository().registerGroup(subscription(),RetainedReplayPolicy.ALL_RETAINED).block()).isEqualTo(GroupRegistrationResult.CREATED);
        var insert=x.statements.stream().filter(e->e.sql.startsWith("INSERT")).findFirst().orElseThrow();
        assertThat(insert.sql).contains("ON CONFLICT", "group_id", "channels", "max_attempts", "replay_policy").doesNotContain("registered_at","UPDATE SET");
        assertThat(insert.bindings.values()).contains("group-A",3,"ALL_RETAINED");assertThat(x.committed.get()).isEqualTo(1);assertThat(x.closed.get()).isEqualTo(1);assertThat(acquired.get()).isEqualTo(1);
    }
    @Test void identicalRegistrationIgnoresChannelOrderButNeverUpdatesExistingPolicy(){
        var x=session();x.inserted=0;x.registration.put("channels",new String[]{"b","a"});
        assertThat(repository().registerGroup(new GroupSubscription(Set.of("a","b"),3),RetainedReplayPolicy.ALL_RETAINED).block()).isEqualTo(GroupRegistrationResult.EXISTING);
        assertThat(x.statements.stream().map(e->e.sql)).noneMatch(sql->sql.startsWith("UPDATE"));
    }
    @Test void explicitReplayAndNonemptyArgumentsAreRequiredBeforeAcquisition(){
        assertThatThrownBy(()->repository().registerGroup(subscription(),null).block()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->repository().registerGroup(null,RetainedReplayPolicy.ALL_RETAINED).block()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->repository().claim(null).block()).isInstanceOf(IllegalArgumentException.class);assertThat(acquired.get()).isZero();
    }
    @Test void mismatchedExistingRegistrationAndImpossibleInsertCountRollback(){
        var x=session();x.inserted=0;x.registration.put("max_attempts",4);
        assertThatThrownBy(()->repository().registerGroup(subscription(),RetainedReplayPolicy.ALL_RETAINED).block()).isInstanceOf(IllegalStateException.class);
        var y=session();y.inserted=2;assertThatThrownBy(()->repository().registerGroup(subscription(),RetainedReplayPolicy.ALL_RETAINED).block()).isInstanceOf(IllegalStateException.class);
        assertThat(x.rolledBack.get()).isEqualTo(1);assertThat(y.rolledBack.get()).isEqualTo(1);
    }
    @Test void everyLoadedMalformedRegistrationRefusesBeforeMaterialization(){
        var x=session();x.registration.put("channels",new String[]{"events","events"});
        assertThatThrownBy(()->repository().claim(request()).block()).isInstanceOf(RuntimeException.class);
        assertThat(x.statements.stream().map(e->e.sql)).noneMatch(sql->sql.contains("missing AS MATERIALIZED")||sql.contains("claim_rows AS"));assertThat(x.rolledBack.get()).isEqualTo(1);
    }
    @Test void claimMapsRealSpringDataProjectionAndRowSpecificCeilingAfterCommitAndRelease(){
        var x=session();var batch=repository().claim(request()).block();var c=batch.claims().getFirst();
        assertThat(c.row().id()).isEqualTo(11L);assertThat(c.row().headers().asString()).contains("12.3400");assertThat(c.row().payload()).containsExactly(0,1,(byte)255);
        assertThat(c.row().eventType()).isNull();assertThat(c.row().eventClass()).isEqualTo("untrusted.MustNotLoad");assertThat(c.maxAttempts()).isEqualTo(7);
        assertThat(x.committed.get()).isEqualTo(1);assertThat(x.closed.get()).isEqualTo(1);assertThat(acquired.get()).isEqualTo(1);
    }
    @Test void everyClaimRepeatsAntiJoinWithoutIdOrTimeWatermarkOrGlobalStateRewrite(){
        var repo=repository();var a=session();repo.claim(request()).block();var b=session();repo.claim(request()).block();
        for(var x:List.of(a,b)){
            int material=-1,claim=-1;
            for(int i=0;i<x.statements.size();i++){var e=x.statements.get(i);if(e.sql.contains("missing AS MATERIALIZED")){material=i;assertThat(e.sql).contains("NOT EXISTS","ON CONFLICT","event.id").doesNotContain("event.id >","created_at >","status=","UPDATE ");}if(e.sql.contains("claim_rows AS"))claim=i;}
            assertThat(material).isGreaterThanOrEqualTo(0);assertThat(claim).isGreaterThan(material);
        }
    }
    @Test void pinnedSubscriptionMismatchAndMissingRegistrationAreNotHealthyEmptyClaims(){
        var a=session();a.registration.put("max_attempts",4);assertThatThrownBy(()->repository().claim(request()).block()).isInstanceOf(IllegalStateException.class);
        var b=session();b.registration=null;assertThatThrownBy(()->repository().claim(request()).block()).isInstanceOf(IllegalStateException.class);
        for(var x:List.of(a,b))assertThat(x.statements.stream().map(e->e.sql)).noneMatch(sql->sql.contains("missing AS MATERIALIZED"));
    }
    @Test void claimBindsExactConstructorGroupAndActualArraysToEveryProtocolStatement(){
        var a=session();repository().claim(request()).block();var b=session();b.registration.put("group_id","group-B");repository("group-B").claim(request()).block();
        for(var pair:List.of(Map.entry(a,"group-A"),Map.entry(b,"group-B"))){
            for(var e:pair.getKey().statements)if(e.sql.contains("missing AS MATERIALIZED")||e.sql.contains("claim_rows AS")){
                assertThat(e.bindings.values()).contains(pair.getValue(),4);assertThat(e.bindings.values().stream().filter(String[].class::isInstance).map(String[].class::cast).findFirst().orElseThrow()).containsExactly("events");
            }
        }
    }
    @Test void overCapacityClaimsRollbackInsteadOfEmitting(){
        var x=session();x.claimRows=Collections.nCopies(5,event());assertThatThrownBy(()->repository().claim(request()).block()).isInstanceOf(IllegalStateException.class);assertThat(x.rolledBack.get()).isEqualTo(1);
    }
    @Test void ownedCommitReleaseAndFailureAreObservedBeforeDelivery(){
        var x=session();var commit=Sinks.<Void>empty();var close=Sinks.<Void>empty();x.commitGate=commit.asMono();x.closeGate=close.asMono();var seen=new AtomicReference<ClaimBatch>();
        var d=repository().claim(request()).doOnNext(seen::set).subscribe();try{assertThat(seen.get()).isNull();commit.tryEmitEmpty();assertThat(seen.get()).isNull();close.tryEmitEmpty();assertThat(seen.get()).isNotNull();}finally{d.dispose();}
        var y=session();y.closeGate=Mono.error(new IllegalStateException("synthetic release failure"));assertThatThrownBy(()->repository().claim(request()).block()).isInstanceOf(IllegalStateException.class);assertThat(y.committed.get()).isEqualTo(1);assertThat(y.closed.get()).isEqualTo(1);
    }
    @Test void ambientSameFactoryIsSuspendedWithoutAdoptingOuterTransaction(){
        var outer=session();var inner=session();var expected=new IllegalStateException("intentional outer rollback");
        assertThatThrownBy(()->TransactionalOperator.create(new R2dbcTransactionManager(factory)).transactional(repository().claim(request()).doOnNext(batch->{assertThat(inner.closed.get()).isEqualTo(1);assertThat(inner.committed.get()).isEqualTo(1);assertThat(outer.closed.get()).isZero();}).then(Mono.error(expected))).block()).isSameAs(expected);
        assertThat(inner.rolledBack.get()).isZero();assertThat(outer.rolledBack.get()).isEqualTo(1);
    }
    @Test void cancelledQueryRollsBackAndReleasesExactlyOnce(){
        var x=session();x.hangClaim=true;var d=repository().claim(request()).subscribe();d.dispose();assertThat(x.rolledBack.get()).isEqualTo(1);assertThat(x.closed.get()).isEqualTo(1);assertThat(x.committed.get()).isZero();
    }
    static Stream<Arguments> counts(){return Stream.of(Arguments.of(1L,"APPLIED",TransitionResult.APPLIED),Arguments.of(0L,"ALREADY_APPLIED",TransitionResult.ALREADY_APPLIED),Arguments.of(0L,"STALE",TransitionResult.STALE));}
    @ParameterizedTest @MethodSource("counts") void exactBoundGroupAckOutcomeAndCount(long count,String outcome,TransitionResult expected){
        var x=session();x.aggregateRows=List.of(Map.of("affected_count",count,"audit_count",0L,"outcome",outcome));assertThat(repository().acknowledge(claim()).block()).isEqualTo(expected);
        assertThat(x.last().bindings.values()).contains("group-A",11L,TOKEN,1L);assertThat(x.last().sql).contains("clocked AS MATERIALIZED","clocked.lease_until>clocked.database_now");
    }
    static Stream<Arguments> badCounts(){return Stream.of(Arguments.of(0L,"APPLIED"),Arguments.of(2L,"APPLIED"),Arguments.of(1L,"STALE"),Arguments.of(1L,"ALREADY_APPLIED"),Arguments.of(0L,"invalid"));}
    @ParameterizedTest @MethodSource("badCounts") void impossibleAckPairsRollback(long count,String outcome){var x=session();x.aggregateRows=List.of(Map.of("affected_count",count,"outcome",outcome));assertThatThrownBy(()->repository().acknowledge(claim()).block()).isInstanceOf(IllegalStateException.class);assertThat(x.rolledBack.get()).isEqualTo(1);}
    @Test void missingOrMultipleAggregatesCannotBecomeStale(){
        var x=session();x.aggregateRows=List.of();assertThatThrownBy(()->repository().acknowledge(claim()).block()).isInstanceOf(IllegalStateException.class);
        var y=session();y.aggregateRows=Collections.nCopies(2,Map.of("affected_count",0L,"outcome","STALE"));assertThatThrownBy(()->repository().acknowledge(claim()).block()).isInstanceOf(RuntimeException.class);
    }
    @Test void nackUsesPersistedCeilingSafeCodesAndLiveFenceWithoutCounterReset(){
        var x=session();repository().reject(claim(),FailureKind.LISTENER_FAILURE,Duration.ofMillis(23)).block();assertThat(x.last().sql).contains("attempt_ceiling","DLQ_EXHAUSTED:","RETRY:","clocked.lease_until>clocked.database_now").doesNotContain("attempts=0","lease_generation=0");assertThat(x.last().bindings.values()).contains("group-A",23L,"LISTENER_FAILURE");
    }
    @Test void reaperUsesPerRowCeilingAndKeepsLiveFinalAttemptLease(){
        var x=session();x.aggregateRows=List.of(Map.of("affected_count",2L));assertThat(repository().reapExhausted(request()).block()).isEqualTo(2L);assertThat(x.last().sql).contains("attempt_ceiling","FOR UPDATE OF delivery SKIP LOCKED","clocked.database_now","PROTOCOL_").doesNotContain("attempts=0","lease_generation=0");
    }
    @Test void readinessValidatesExactJournalFunctionAndAlwaysTriggersWithoutDdl(){
        var x=session();repository().verifyProtocol().block();var query=x.statements.stream().filter(e->e.sql.contains("pg_catalog.pg_class")).findFirst().orElseThrow();
        assertThat(query.sql).contains("pg_catalog.pg_trigger","pg_catalog.pg_proc","prosrc","tgenabled","firefly.eda.groups/v1","relrowsecurity").doesNotContain("CREATE ","ALTER ","GRANT ");assertThat(x.closed.get()).isEqualTo(1);
        var y=session();y.compatible=false;assertThatThrownBy(()->repository().verifyProtocol().block()).isInstanceOf(IllegalStateException.class);
    }
    @Test void redriveLocksBeforeNewStatementAuditSnapshotAndPreservesCounters(){
        var x=session();assertThat(repository().redrive(command()).block()).isEqualTo(TransitionResult.APPLIED);
        int lock=-1,journal=-1;for(int i=0;i<x.statements.size();i++){String sql=x.statements.get(i).sql;if(sql.startsWith("SELECT")&&sql.contains("FOR UPDATE"))lock=i;if(sql.contains("journaled AS"))journal=i;}
        assertThat(lock).isGreaterThanOrEqualTo(0);assertThat(journal).isGreaterThan(lock);assertThat(x.last().sql).contains("clocked AS MATERIALIZED","clocked.database_now","attempt_ceiling","REDRIVE_REQUESTED","prior_failure_code").doesNotContain("attempts=0","lease_generation=0","operator-supplied","reviewed reason");assertThat(x.last().bindings.values()).contains(command().commandId(),"group-A","operator-supplied","reviewed reason",3);assertThat(x.closed.get()).isEqualTo(1);
    }
    @ParameterizedTest @MethodSource("counts") void redriveRequiresExactStateAndAuditCountPair(long count,String outcome,TransitionResult expected){
        var x=session();x.aggregateRows=List.of(Map.of("affected_count",count,"audit_count",count,"outcome",outcome));assertThat(repository().redrive(command()).block()).isEqualTo(expected);
    }
    @Test void auditMismatchConflictingIdentityAndMissingAggregateNeverBecomeApplied(){
        var x=session();x.aggregateRows=List.of(Map.of("affected_count",1L,"audit_count",0L,"outcome","APPLIED"));assertThatThrownBy(()->repository().redrive(command()).block()).isInstanceOf(IllegalStateException.class);
        var y=session();y.aggregateRows=List.of(Map.of("affected_count",0L,"audit_count",0L,"outcome","CONFLICT"));assertThatThrownBy(()->repository().redrive(command()).block()).isInstanceOf(IllegalStateException.class);
        var z=session();z.aggregateRows=List.of();assertThatThrownBy(()->repository().redrive(command()).block()).isInstanceOf(IllegalStateException.class);
    }
    @Test void invalidRedriveAndRetryDelayAreRejectedBeforeAnyConnection(){
        assertThatThrownBy(()->repository().redrive(null).block()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeadLetterRedriveCommand(null,11,TOKEN,1,3,"actor","reason")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->repository().reject(claim(),FailureKind.LISTENER_FAILURE,Duration.ofNanos(1)).block()).isInstanceOf(IllegalArgumentException.class);assertThat(acquired.get()).isZero();
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"aware","single-false","single-true","alias"})
    void groupAdapterRejectsActualScopedProvidersBeforeTargetResolutionOrAcquisition(String provider) {
        var resolutions=new AtomicInteger();
        ConnectionFactory target;
        if(provider.equals("aware"))target=new org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy(factory);
        else {
            var single=new org.springframework.r2dbc.connection.SingleConnectionFactory(factory);
            single.setSuppressClose(!provider.equals("single-false"));target=single;
        }
        try(var context=new org.springframework.context.support.GenericApplicationContext()) {
            context.getBeanFactory().registerScope("group-test",new org.springframework.context.support.SimpleThreadScope() {
                @Override public Object get(String name,org.springframework.beans.factory.ObjectFactory<?> creator) {
                    resolutions.incrementAndGet();return super.get(name,creator);
                }
            });
            ConnectionFactory declared=target;
            context.registerBean("scopedTarget.group",ConnectionFactory.class,()->declared,definition->definition.setScope("group-test"));
            context.refresh();
            var proxyFactory=new org.springframework.aop.scope.ScopedProxyFactoryBean();
            proxyFactory.setTargetBeanName("scopedTarget.group");proxyFactory.setProxyTargetClass(false);proxyFactory.setBeanFactory(context.getBeanFactory());
            ConnectionFactory scoped=(ConnectionFactory)proxyFactory.getObject();
            assertThat(scoped).isInstanceOf(org.springframework.aop.scope.ScopedObject.class);
            ConnectionFactory supplied=provider.equals("alias")?new GroupInfrastructureAlias(scoped):scoped;
            assertThatThrownBy(()->new SpringDataPostgresConsumerGroupRepositoryImpl(supplied,tables(),new ConsumerGroupId("group-A"),Duration.ofSeconds(10)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(resolutions.get()).isZero();assertThat(acquired.get()).isZero();
        }
    }
    static final class GroupInfrastructureAlias implements ConnectionFactory,org.springframework.core.InfrastructureProxy {
        private final ConnectionFactory target;
        GroupInfrastructureAlias(ConnectionFactory target){this.target=target;}
        @Override public Object getWrappedObject(){return target;}
        @Override public org.reactivestreams.Publisher<? extends Connection> create(){return target.create();}
        @Override public ConnectionFactoryMetadata getMetadata(){return target.getMetadata();}
    }
}
