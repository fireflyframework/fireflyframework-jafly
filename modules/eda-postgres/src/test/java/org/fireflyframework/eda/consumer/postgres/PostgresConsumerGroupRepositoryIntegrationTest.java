package org.fireflyframework.eda.consumer.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import io.r2dbc.pool.*;
import io.r2dbc.postgresql.*;
import io.r2dbc.spi.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresConsumerGroupRepositoryImpl;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.reactivestreams.Publisher;
import org.springframework.dao.DataAccessException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import reactor.core.publisher.*;

@Testcontainers
class PostgresConsumerGroupRepositoryIntegrationTest {
    static final Duration WAIT=Duration.ofSeconds(15);
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("group_delivery_qualification").withUsername("group_test").withPassword("synthetic-only");
    static ConnectionPool pool;static DatabaseClient database;
    ConsumerGroupTables tables;
    @BeforeAll static void ownedPool(){
        var configuration=PostgresqlConnectionConfiguration.builder().host(POSTGRES.getHost()).port(POSTGRES.getMappedPort(5432))
                .database(POSTGRES.getDatabaseName()).username(POSTGRES.getUsername()).password(POSTGRES.getPassword()).connectTimeout(Duration.ofSeconds(3)).build();
        pool=new ConnectionPool(ConnectionPoolConfiguration.builder(new PostgresqlConnectionFactory(configuration)).initialSize(0).maxSize(8)
                .maxAcquireTime(Duration.ofSeconds(5)).maxCreateConnectionTime(Duration.ofSeconds(3)).acquireRetry(0).build());database=DatabaseClient.create(pool);
    }
    @AfterAll static void releaseOwnedPool(){if(pool!=null)pool.disposeLater().block(WAIT);}
    @BeforeEach void manualOwnedSchema(){
        tables=new ConsumerGroupTables("groups_"+UUID.randomUUID().toString().replace("-",""),"outbox","registry","deliveries","journal");
        database.sql("CREATE SCHEMA "+PostgresDeliverySchema.quoted(tables.schema())).then().block(WAIT);
        database.sql("CREATE TABLE "+relation(tables.outbox())+" (id BIGSERIAL PRIMARY KEY,destination VARCHAR(255) NOT NULL,channel VARCHAR(63) NOT NULL,event_type VARCHAR(255),payload BYTEA NOT NULL,headers JSONB,transaction_id VARCHAR(255),publisher_type VARCHAR(50),connection_id VARCHAR(50),event_class VARCHAR(500),status VARCHAR(20) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,error_message TEXT,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),processed_at TIMESTAMPTZ,failed_at TIMESTAMPTZ)").then().block(WAIT);
        install(PostgresDeliverySchema.installationSql(tables.schema(),tables.outbox()));
        install(PostgresConsumerGroupSchema.installationSql(tables));
    }
    String relation(String table){return PostgresDeliverySchema.qualified(tables.schema(),table);}
    void install(String sql){Mono.usingWhen(Mono.from(pool.create()),c->Flux.from(c.createStatement(sql).execute()).concatMap(r->Mono.from(r.getRowsUpdated())).then(),Connection::close,(c,e)->Mono.from(c.rollbackTransaction()).then(Mono.from(c.close())),c->Mono.from(c.rollbackTransaction()).then(Mono.from(c.close()))).block(WAIT);}
    SpringDataPostgresConsumerGroupRepositoryImpl repository(String group){return repository(pool,group);}
    SpringDataPostgresConsumerGroupRepositoryImpl repository(ConnectionFactory cf,String group){return new SpringDataPostgresConsumerGroupRepositoryImpl(cf,tables,new ConsumerGroupId(group),Duration.ofSeconds(10));}
    SpringDataPostgresConsumerGroupRepositoryImpl ready(String group,int attempts){var r=repository(group);r.registerGroup(new GroupSubscription(Set.of("events"),attempts),RetainedReplayPolicy.ALL_RETAINED).block(WAIT);r.verifyProtocol().block(WAIT);return r;}
    ClaimRequest request(){return request(8,3,Duration.ofSeconds(5));}
    ClaimRequest request(int limit,int attempts,Duration lease){return new ClaimRequest(Set.of("events"),limit,attempts,lease);}
    long append(){return database.sql("INSERT INTO "+relation(tables.outbox())+" (destination,channel,payload,headers,event_class) VALUES ('orders.created','events',decode('0001ff','hex'),'{\"decimal\":12.3400}'::pg_catalog.jsonb,'untrusted.MustNotInitialize') RETURNING id").map((row,m)->row.get("id",Long.class)).one().block(WAIT);}
    DeliveryClaim one(SpringDataPostgresConsumerGroupRepositoryImpl r,ClaimRequest request){var batch=r.claim(request).block(WAIT);assertThat(batch.claims()).hasSize(1);return batch.claims().getFirst();}
    String snapshot(String table,String where){return database.sql("SELECT pg_catalog.to_jsonb(value)::pg_catalog.text AS snapshot FROM "+relation(table)+" value WHERE "+where).map((row,m)->row.get("snapshot",String.class)).one().block(WAIT);}
    long count(String table){return database.sql("SELECT pg_catalog.count(*) AS count FROM "+relation(table)).map((row,m)->row.get("count",Long.class)).one().block(WAIT);}
    Map<String,Object> state(String group,long event){return database.sql("SELECT * FROM "+relation(tables.deliveries())+" WHERE group_id=:group AND event_id=:id").bind("group",group).bind("id",event).fetch().one().block(WAIT);}
    OffsetDateTime clock(){return database.sql("SELECT pg_catalog.clock_timestamp() AS now").map((row,m)->row.get("now",OffsetDateTime.class)).one().block(WAIT);}
    DeadLetterRedriveCommand command(DeliveryClaim c){return new DeadLetterRedriveCommand(UUID.randomUUID(),c.row().id(),c.row().leaseToken(),c.row().leaseGeneration(),3,"operator-supplied","reviewed technical request");}
    DeliveryClaim deadLetter(SpringDataPostgresConsumerGroupRepositoryImpl r){append();var c=one(r,request());assertThat(r.reject(c,FailureKind.EXPLICIT_DEAD_LETTER,Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.APPLIED);return c;}
    Connection blocker(String sql){var c=Mono.from(pool.create()).block(WAIT);try{Mono.from(c.beginTransaction()).block(WAIT);Flux.from(c.createStatement(sql).execute()).concatMap(r->Flux.from(r.getRowsUpdated())).then().block(WAIT);return c;}catch(RuntimeException e){Mono.from(c.rollbackTransaction()).then(Mono.from(c.close())).block(WAIT);throw e;}}
    void rollbackClose(Connection c,boolean committed){try{if(!committed)Mono.from(c.rollbackTransaction()).block(WAIT);}finally{Mono.from(c.close()).block(WAIT);}}
    final class ObservedFactory implements ConnectionFactory {
        final AtomicInteger pid=new AtomicInteger();
        public Publisher<? extends Connection> create(){return Mono.from(pool.create()).flatMap(c->Mono.from(c.createStatement("SELECT pg_catalog.pg_backend_pid() AS pid").execute()).flatMap(r->Mono.from(r.map((row,m)->row.get("pid",Integer.class)))).doOnNext(pid::set).thenReturn(c).onErrorResume(e->Mono.from(c.close()).then(Mono.error(e))));}
        public ConnectionFactoryMetadata getMetadata(){return pool.getMetadata();}
    }
    void awaitLock(ObservedFactory observed){await().atMost(WAIT).until(()->observed.pid.get()>0);await().atMost(WAIT).until(()->Boolean.TRUE.equals(database.sql("SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_stat_activity WHERE pid=:pid AND wait_event_type='Lock') AS waiting").bind("pid",observed.pid.get()).map((row,m)->row.get("waiting",Boolean.class)).one().block(WAIT)));}

    @Test void registrationIsExplicitAndConcurrentIdenticalAdoptionDoesNotRewriteState(){
        var a=repository("a");assertThatThrownBy(()->a.verifyProtocol().block(WAIT)).isInstanceOf(IllegalStateException.class);
        var results=Flux.merge(a.registerGroup(new GroupSubscription(Set.of("events"),3),RetainedReplayPolicy.ALL_RETAINED),repository("a").registerGroup(new GroupSubscription(Set.of("events"),3),RetainedReplayPolicy.ALL_RETAINED)).collectList().block(WAIT);
        assertThat(results).containsExactlyInAnyOrder(GroupRegistrationResult.CREATED,GroupRegistrationResult.EXISTING);
        append();var c=one(a,request());String before=snapshot(tables.registry(),"group_id='a'");
        assertThat(a.registerGroup(new GroupSubscription(Set.of("events"),3),RetainedReplayPolicy.ALL_RETAINED).block(WAIT)).isEqualTo(GroupRegistrationResult.EXISTING);
        assertThat(snapshot(tables.registry(),"group_id='a'")).isEqualTo(before);assertThat(state("a",c.row().id())).containsEntry("attempts",1);
        assertThatThrownBy(()->a.registerGroup(new GroupSubscription(Set.of("other"),3),RetainedReplayPolicy.ALL_RETAINED).block(WAIT)).isInstanceOf(IllegalStateException.class);
    }
    @Test void independentGroupsReceiveTheSameEnvelopeAndKeepStateIndependent(){
        var a=ready("a",3);var b=ready("b",3);long id=append();String original=snapshot(tables.outbox(),"id="+id);
        var ca=one(a,request());var cb=one(b,request());assertThat(ca.row().id()).isEqualTo(id);assertThat(cb.row().id()).isEqualTo(id);
        assertThat(ca.row().leaseToken()).isNotEqualTo(cb.row().leaseToken());assertThat(ca.row().payload()).containsExactly(0,1,(byte)255);assertThat(ca.row().headers().asString()).contains("12.3400");assertThat(ca.row().eventType()).isNull();
        assertThat(a.acknowledge(ca).block(WAIT)).isEqualTo(TransitionResult.APPLIED);assertThat(state("b",id)).containsEntry("status","IN_FLIGHT").containsEntry("attempts",1);
        assertThat(snapshot(tables.outbox(),"id="+id)).isEqualTo(original);assertThat(count(tables.deliveries())).isEqualTo(2);
    }
    @Test void sameGroupWorkersAndMaterializersCompeteWithoutDuplicatesAndRepeatedScansProgress(){
        ready("a",3);for(int i=0;i<24;i++)append();
        var claims=new ArrayList<>(Flux.range(0,32).flatMap(i->repository("a").claim(request(1,3,Duration.ofSeconds(30))),8).flatMapIterable(ClaimBatch::claims).collectList().block(WAIT));
        for(int i=0;i<64&&claims.size()<24;i++)claims.addAll(repository("a").claim(request(1,3,Duration.ofSeconds(30))).block(WAIT).claims());
        assertThat(claims).hasSize(24);assertThat(claims.stream().map(c->c.row().id())).doesNotHaveDuplicates();assertThat(count(tables.deliveries())).isEqualTo(24);assertThat(pool.getMetrics().orElseThrow().acquiredSize()).isZero();
    }
    @Test void lateSmallerIdAndOlderCreatedAtCommitIsDiscoveredWithoutHighwater(){
        var r=ready("a",3);Connection held=blocker("INSERT INTO "+relation(tables.outbox())+" (id,destination,channel,payload) VALUES (1,'late','events',decode('01','hex'))");boolean committed=false;
        try{database.sql("INSERT INTO "+relation(tables.outbox())+" (id,destination,channel,payload) VALUES (2,'early','events',decode('02','hex'))").then().block(WAIT);
            assertThat(one(r,request()).row().id()).isEqualTo(2);Mono.from(held.commitTransaction()).block(WAIT);committed=true;assertThat(one(r,request()).row().id()).isEqualTo(1);
        }finally{rollbackClose(held,committed);}
        assertThat(count(tables.deliveries())).isEqualTo(2);
    }
    @Test void rolledBackPublisherRowsNeverMaterialize(){
        var r=ready("a",3);Connection held=blocker("INSERT INTO "+relation(tables.outbox())+" (id,destination,channel,payload) VALUES (1,'rollback','events',decode('01','hex'))");
        try{assertThat(r.claim(request()).block(WAIT).claims()).isEmpty();}finally{rollbackClose(held,false);}
        assertThat(r.claim(request()).block(WAIT).claims()).isEmpty();assertThat(count(tables.deliveries())).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"PROCESSED","DEAD_LETTER"})
    void allRetainedAdoptionReplaysGlobalTerminalDataWithoutChangingItsCounters(String terminal){
        long id=append();database.sql("UPDATE "+relation(tables.outbox())+" SET status=:terminal,attempts=7,lease_generation=9,error_message='retained',failed_at=pg_catalog.clock_timestamp()").bind("terminal",terminal).then().block(WAIT);String before=snapshot(tables.outbox(),"id="+id);
        var r=ready("new-group",3);var c=one(r,request());assertThat(c.row().attempts()).isEqualTo(1);assertThat(c.row().leaseGeneration()).isEqualTo(1);r.acknowledge(c).block(WAIT);assertThat(snapshot(tables.outbox(),"id="+id)).isEqualTo(before);
    }
    static Stream<Arguments> malformedArrays(){return Stream.<String[]>of(new String[]{"events","events"},new String[]{null},new String[]{" "},new String[]{"😀".repeat(16)}).map(value->Arguments.of((Object)value));}
    @ParameterizedTest @MethodSource("malformedArrays")
    void malformedPersistedRegistrationFailsBeforeCreatingAnyDelivery(String[] channels){
        var r=ready("a",3);append();database.sql("UPDATE "+relation(tables.registry())+" SET channels=CAST(:channels AS pg_catalog.text[]) WHERE group_id='a'").bind("channels",channels).then().block(WAIT);
        assertThatThrownBy(()->r.claim(request()).block(WAIT)).isInstanceOf(RuntimeException.class);assertThat(count(tables.deliveries())).isZero();
    }
    @Test void retryDeadLetterAndStaleCallbacksRemainPerGroup(){
        var a=ready("a",3);var b=ready("b",3);long id=append();var ca=one(a,request());var cb=one(b,request());
        assertThat(b.acknowledge(ca).block(WAIT)).isEqualTo(TransitionResult.STALE);assertThat(a.reject(ca,FailureKind.LISTENER_FAILURE,Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
        assertThat(a.reject(ca,FailureKind.LISTENER_FAILURE,Duration.ZERO).block(WAIT)).isEqualTo(TransitionResult.STALE);
        var again=one(a,request());assertThat(again.row().attempts()).isEqualTo(2);assertThat(again.row().leaseGeneration()).isEqualTo(2);assertThat(a.acknowledge(ca).block(WAIT)).isEqualTo(TransitionResult.STALE);
        a.reject(again,FailureKind.EXPLICIT_DEAD_LETTER,Duration.ZERO).block(WAIT);assertThat(state("a",id)).containsEntry("status","DEAD_LETTER");assertThat(b.acknowledge(cb).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
    }
    @Test void liveFinalAttemptIsNotReapedAndExpiredOneIsReapedWithoutReset(){
        var r=ready("a",1);long id=append();var request=request(1,1,Duration.ofMillis(500));var c=one(r,request);
        assertThat(r.reapExhausted(request).block(WAIT)).isZero();await().atMost(WAIT).until(()->clock().isAfter(c.row().leaseUntil()));
        assertThat(r.reapExhausted(request).block(WAIT)).isEqualTo(1);assertThat(state("a",id)).containsEntry("status","DEAD_LETTER").containsEntry("attempts",1).containsEntry("lease_generation",1L);
    }
    @Test void redriveExtendsCeilingButNeverResetsAttemptsOrGenerationAndJournalDeduplicatesAfterLaterWork(){
        var r=ready("a",3);var c=deadLetter(r);var command=command(c);assertThat(r.redrive(command).block(WAIT)).isEqualTo(TransitionResult.APPLIED);
        assertThat(state("a",c.row().id())).containsEntry("attempts",1).containsEntry("lease_generation",1L).containsEntry("attempt_ceiling",6);
        var again=one(r,request());assertThat(again.maxAttempts()).isEqualTo(6);assertThat(again.row().attempts()).isEqualTo(2);assertThat(again.row().leaseGeneration()).isEqualTo(2);
        r.acknowledge(again).block(WAIT);String state=snapshot(tables.deliveries(),"group_id='a'");
        assertThat(r.redrive(command).block(WAIT)).isEqualTo(TransitionResult.ALREADY_APPLIED);assertThat(snapshot(tables.deliveries(),"group_id='a'")).isEqualTo(state);assertThat(count(tables.redrives())).isEqualTo(1);
    }
    @Test void concurrentIdenticalRedriveSeesAuditAfterItsRowLockWait(){
        var r=ready("a",3);var c=deadLetter(r);var command=command(c);
        var outcomes=Flux.merge(r.redrive(command),repository("a").redrive(command)).collectList().block(WAIT);
        assertThat(outcomes).containsExactlyInAnyOrder(TransitionResult.APPLIED,TransitionResult.ALREADY_APPLIED);assertThat(count(tables.redrives())).isEqualTo(1);assertThat(state("a",c.row().id())).containsEntry("attempt_ceiling",6);
    }
    @Test void reusedCommandWithDifferentIntentAndStaleTerminalFenceCannotReschedule(){
        var r=ready("a",3);var c=deadLetter(r);var command=command(c);r.redrive(command).block(WAIT);String state=snapshot(tables.deliveries(),"group_id='a'");
        assertThatThrownBy(()->r.redrive(new DeadLetterRedriveCommand(command.commandId(),command.eventId(),command.terminalToken(),command.terminalGeneration(),1,command.actor(),command.reason())).block(WAIT)).isInstanceOf(IllegalStateException.class);
        assertThat(r.redrive(new DeadLetterRedriveCommand(UUID.randomUUID(),command.eventId(),UUID.randomUUID(),command.terminalGeneration(),3,command.actor(),command.reason())).block(WAIT)).isEqualTo(TransitionResult.STALE);
        assertThat(snapshot(tables.deliveries(),"group_id='a'")).isEqualTo(state);assertThat(count(tables.redrives())).isEqualTo(1);
    }
    @Test void ceilingAndGenerationExhaustionRefuseRedriveWithoutCounterReset(){
        var r=ready("a",3);var c=deadLetter(r);database.sql("UPDATE "+relation(tables.deliveries())+" SET attempt_ceiling=2147483647 WHERE group_id='a'").then().block(WAIT);assertThat(r.redrive(command(c)).block(WAIT)).isEqualTo(TransitionResult.STALE);
        database.sql("UPDATE "+relation(tables.deliveries())+" SET attempt_ceiling=3,lease_generation=9223372036854775807 WHERE group_id='a'").then().block(WAIT);
        assertThat(r.redrive(new DeadLetterRedriveCommand(UUID.randomUUID(),c.row().id(),c.row().leaseToken(),Long.MAX_VALUE,3,"operator-supplied","overflow control")).block(WAIT)).isEqualTo(TransitionResult.STALE);assertThat(count(tables.redrives())).isZero();assertThat(state("a",c.row().id())).containsEntry("lease_generation",Long.MAX_VALUE);
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE","DELETE","TRUNCATE"})
    void journalRejectsActualUpdateDeleteAndTruncateWithoutChangingStoredAudit(String operation){
        var r=ready("a",3);r.redrive(command(deadLetter(r))).block(WAIT);String before=snapshot(tables.redrives(),"group_id='a'");
        String sql=switch(operation){case "UPDATE"->"UPDATE "+relation(tables.redrives())+" SET actor='changed'";case "DELETE"->"DELETE FROM "+relation(tables.redrives());default->"TRUNCATE TABLE "+relation(tables.redrives());};
        assertThatThrownBy(()->database.sql(sql).then().block(WAIT)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");assertThat(snapshot(tables.redrives(),"group_id='a'")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","body","RLS","dropped"})
    void readinessRefusesAlteredOrDisabledJournalTechnicalGuards(String change){
        var r=ready("a",3);String mutation=PostgresDeliverySchema.quoted(PostgresConsumerGroupSchema.journalMutationTriggerName(tables));
        switch(change){case "disabled"->database.sql("ALTER TABLE "+relation(tables.redrives())+" DISABLE TRIGGER "+mutation).then().block(WAIT);case "body"->database.sql("CREATE OR REPLACE FUNCTION "+relation(PostgresConsumerGroupSchema.journalGuardName(tables))+"() RETURNS pg_catalog.trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path TO pg_catalog AS $wrong$BEGIN RETURN NULL; END;$wrong$").then().block(WAIT);case "RLS"->database.sql("ALTER TABLE "+relation(tables.redrives())+" ENABLE ROW LEVEL SECURITY").then().block(WAIT);default->database.sql("DROP TRIGGER "+mutation+" ON "+relation(tables.redrives())).then().block(WAIT);}
        assertThatThrownBy(()->r.verifyProtocol().block(WAIT)).isInstanceOf(IllegalStateException.class);
    }
    @Test void innerClaimCommitsAndReleasesBeforeOuterRollback(){
        var r=ready("a",3);append();var result=new java.util.concurrent.atomic.AtomicReference<DeliveryClaim>();var expected=new IllegalStateException("intentional outer rollback");
        assertThatThrownBy(()->TransactionalOperator.create(new R2dbcTransactionManager(pool)).transactional(r.claim(request()).doOnNext(batch->{result.set(batch.claims().getFirst());assertThat(pool.getMetrics().orElseThrow().acquiredSize()).isEqualTo(1);}).then(Mono.error(expected))).block(WAIT)).isSameAs(expected);
        assertThat(r.acknowledge(result.get()).block(WAIT)).isEqualTo(TransitionResult.APPLIED);assertThat(pool.getMetrics().orElseThrow().acquiredSize()).isZero();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void callbacksRecheckLiveLeaseAfterObservedRowLockWait(boolean reject){
        var r=ready("a",3);append();var c=one(r,request(1,3,Duration.ofMillis(500)));Connection held=blocker("SELECT event_id FROM "+relation(tables.deliveries())+" WHERE group_id='a' FOR UPDATE");boolean committed=false;CompletableFuture<TransitionResult> future=null;
        try{var observed=new ObservedFactory();var contender=repository(observed,"a");future=(reject?contender.reject(c,FailureKind.LISTENER_FAILURE,Duration.ZERO):contender.acknowledge(c)).toFuture();awaitLock(observed);await().atMost(WAIT).until(()->clock().isAfter(c.row().leaseUntil().plusNanos(100000000)));Mono.from(held.commitTransaction()).block(WAIT);committed=true;assertThat(Mono.fromFuture(future).block(WAIT)).isEqualTo(TransitionResult.STALE);}finally{if(future!=null&&!future.isDone())future.cancel(true);rollbackClose(held,committed);}
    }
    @Test void claimClockStartsAfterObservedMaterializationTableWait(){
        ready("a",3);append();Connection held=blocker("LOCK TABLE "+relation(tables.deliveries())+" IN ACCESS EXCLUSIVE MODE");boolean committed=false;CompletableFuture<ClaimBatch> future=null;
        try{var observed=new ObservedFactory();var start=clock();future=repository(observed,"a").claim(request(1,3,Duration.ofSeconds(1))).toFuture();awaitLock(observed);await().atMost(WAIT).until(()->clock().isAfter(start.plusSeconds(2)));var released=clock();Mono.from(held.commitTransaction()).block(WAIT);committed=true;var c=Mono.fromFuture(future).block(WAIT).claims().getFirst();assertThat(c.row().leaseUntil()).isAfter(released.plusNanos(800000000));}finally{if(future!=null&&!future.isDone())future.cancel(true);rollbackClose(held,committed);}
    }
}
