package org.fireflyframework.core.transaction;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.data.annotation.*;
import org.springframework.data.auditing.ReactiveIsNewAwareAuditingHandler;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.r2dbc.mapping.event.ReactiveAuditingEntityCallback;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class BoundedR2dbcResourcesTest {
    record AuditProbe(@Id UUID id,@CreatedDate Instant createdAt,@CreatedBy String createdBy) {}
    interface Probes extends ReactiveCrudRepository<AuditProbe,UUID> {}
    static final Instant RECORDED=Instant.parse("2026-10-26T00:00:00Z");
    static final class Probe implements BeforeConvertCallback<AuditProbe>,Ordered {
        int calls;final IllegalStateException stop=new IllegalStateException("Source callback before acquisition");
        public int getOrder(){return 200;}
        public Mono<AuditProbe> onBeforeConvert(AuditProbe value,SqlIdentifier table){assertEquals(RECORDED,value.createdAt());assertEquals("source-auditor",value.createdBy());calls++;return Mono.error(stop);}
    }
    @Test void callerManagedTemplatePreservesStrategyConverterAuditingAndRepositoryCallbacks(){
        var driver=new BoundedTransactionBoundaryTest.Driver();var original=new R2dbcEntityTemplate(driver);var owned=new OwnedLeaseConnectionFactory(driver);var client=DatabaseClient.create(owned);var template=new R2dbcEntityTemplate(client,original.getDataAccessStrategy());var probe=new Probe();
        var handler=new ReactiveIsNewAwareAuditingHandler(PersistentEntities.of(original.getConverter().getMappingContext()));handler.setAuditorAware(()->Mono.just("source-auditor"));handler.setDateTimeProvider(()->Optional.of(RECORDED));
        try(var context=new AnnotationConfigApplicationContext()){
            context.registerBean("callerTemplate",R2dbcEntityTemplate.class,()->template);context.registerBean("auditing",ReactiveAuditingEntityCallback.class,()->new ReactiveAuditingEntityCallback(()->handler));context.registerBean("probe",Probe.class,()->probe);context.refresh();
            assertSame(original.getConverter(),template.getConverter());assertSame(original.getDataAccessStrategy(),template.getDataAccessStrategy());
            var manager=new R2dbcTransactionManager(owned);var resources=BoundedR2dbcResources.verify(owned,client,template,manager);assertSame(owned,resources.factory());assertSame(owned,manager.getConnectionFactory());assertSame(client,template.getDatabaseClient());
            var factory=new R2dbcRepositoryFactory(template);factory.setBeanFactory(context);var repository=factory.getRepository(Probes.class);
            for(var result:List.of(template.insert(new AuditProbe(null,null,null)),repository.save(new AuditProbe(null,null,null))))StepVerifier.create(result).expectErrorMatches(e->e==probe.stop).verify(Duration.ofSeconds(1));
            assertEquals(2,probe.calls);assertEquals(0,driver.creates);
        }
    }
    @Test void actualCallerExecuteFunctionAndStatementFilterArePreserved(){
        var calls=new AtomicInteger();var filters=new AtomicInteger();var fixture=new java.util.concurrent.atomic.AtomicReference<BoundedTransactionBoundaryTest.Fixture>();
        StepVerifier.withVirtualTime(()->{
            var f=new BoundedTransactionBoundaryTest.Fixture();fixture.set(f);
            var client=DatabaseClient.builder().connectionFactory(f.factory).executeFunction(statement->{calls.incrementAndGet();return statement.execute();}).build();var template=new R2dbcEntityTemplate(client,f.template.getDataAccessStrategy());
            var service=new DefaultBoundedRootTransaction(BoundedR2dbcResources.verify(f.factory,client,template,f.manager),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
            return service.execute(f.original,new DefaultTransactionDefinition(),scope->client.sql("source statement").filter((statement,next)->{filters.incrementAndGet();return next.execute(statement);}).fetch().rowsUpdated());
        }).assertNext(value->assertEquals(1L,value.value())).verifyComplete();assertEquals(1,calls.get());assertEquals(1,filters.get());assertEquals(1,fixture.get().driver.creates);assertEquals(1,fixture.get().driver.closes);
    }
    @Test void mismatchedFactoryClientTemplateAndManagerRefuseBeforeAcquisition(){
        var own=new BoundedTransactionBoundaryTest.Driver();var foreign=new BoundedTransactionBoundaryTest.Driver();var a=new OwnedLeaseConnectionFactory(own);var b=new OwnedLeaseConnectionFactory(foreign);var client=DatabaseClient.create(a);var template=new R2dbcEntityTemplate(client,org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE);
        assertThrows(IllegalArgumentException.class,()->BoundedR2dbcResources.verify(a,client,template,new R2dbcTransactionManager(b)));
        var other=DatabaseClient.create(a);assertThrows(IllegalArgumentException.class,()->BoundedR2dbcResources.verify(a,other,template,new R2dbcTransactionManager(a)));
        assertThrows(IllegalArgumentException.class,()->BoundedR2dbcResources.verify(b,client,template,new R2dbcTransactionManager(b)));assertEquals(0,own.creates);assertEquals(0,foreign.creates);
    }
    @Test void managerFactoryChangedAfterBindingMustRefuseBeforeAnyNewAcquisition(){
        var own=new BoundedTransactionBoundaryTest.Driver();var foreign=new BoundedTransactionBoundaryTest.Driver();var factory=new OwnedLeaseConnectionFactory(own);var client=DatabaseClient.create(factory);var template=new R2dbcEntityTemplate(client,org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE);var manager=new R2dbcTransactionManager(factory);
        var resources=BoundedR2dbcResources.verify(factory,client,template,manager);manager.setConnectionFactory(foreign);
        var service=new DefaultBoundedRootTransaction(resources,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));var now=Instant.now();var budget=R2dbcOperationBudget.observed(now,now.plusSeconds(1),System.nanoTime(),Duration.ofSeconds(1),Clock.systemUTC(),System::nanoTime);
        StepVerifier.create(service.execute(budget,new DefaultTransactionDefinition(),scope->Mono.just("protected-value"))).expectError(RootTransactionException.class).verify(Duration.ofSeconds(2));assertEquals(0,own.creates);assertEquals(0,foreign.creates);assertEquals(0,foreign.closes);
    }
    @Test void unusedApiHasNoAutomaticBeansWrappingOrAcquisition(){
        var driver=new BoundedTransactionBoundaryTest.Driver();var client=DatabaseClient.create(driver);
        try(var context=new AnnotationConfigApplicationContext()){
            context.registerBean("callerClient",DatabaseClient.class,()->client);context.refresh();assertTrue(context.getBeansOfType(BoundedRootTransaction.class).isEmpty());assertTrue(context.getBeansOfType(OwnedLeaseConnectionFactory.class).isEmpty());assertSame(driver,client.getConnectionFactory());assertEquals(0,driver.creates);
        }
    }
}
