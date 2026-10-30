/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fireflyframework.core.transaction;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAuditOwnershipTest {
    static BoundedR2dbcResources resources(BoundedTransactionBoundaryTest.Fixture f){return BoundedR2dbcResources.verify(f.factory,f.client,f.template,f.manager);}
    @Test void originalNarrowedScopeAndExactGraphCanAppendInTheExistingTransaction(){
        var f=new AtomicReference<BoundedTransactionBoundaryTest.Fixture>();
        StepVerifier.withVirtualTime(()->{var x=new BoundedTransactionBoundaryTest.Fixture();f.set(x);return x.execute(scope->{scope.narrow(x.time.budget(1000));return x.factory.requireBoundScope(resources(x),scope).thenReturn("provisional");});})
            .assertNext(r->{assertEquals("provisional",r.value());assertTrue(r.transactionFacts().commitAcknowledged());}).verifyComplete();
        assertEquals(1,f.get().driver.creates);assertEquals(1,f.get().driver.commits);assertEquals(1,f.get().driver.closes);
    }
    @Test void absentInvocationRefusesWithoutAcquisition(){
        var f=new BoundedTransactionBoundaryTest.Fixture();var scope=new RootTransactionScope(){public Duration remaining(){return Duration.ofSeconds(1);}public void narrow(R2dbcOperationBudget cap){}};
        assertThrows(IllegalStateException.class,()->f.factory.requireBoundScope(resources(f),scope).block());assertEquals(0,f.driver.creates);
    }
    @Test void completedRealScopeCannotBeReusedAfterClosure(){
        var retained=new AtomicReference<RootTransactionScope>();var f=new AtomicReference<BoundedTransactionBoundaryTest.Fixture>();
        StepVerifier.withVirtualTime(()->{var x=new BoundedTransactionBoundaryTest.Fixture();f.set(x);return x.execute(scope->{retained.set(scope);return Mono.just("closed");});}).expectNextCount(1).verifyComplete();
        assertThrows(IllegalStateException.class,()->f.get().factory.requireBoundScope(resources(f.get()),retained.get()).block());assertEquals(1,f.get().driver.creates);
    }
    @Test void aDifferentGraphOnTheSameFactoryIsNotTheOriginalResourceGraph(){
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        StepVerifier.withVirtualTime(()->{var f=new BoundedTransactionBoundaryTest.Fixture();var client=DatabaseClient.create(f.factory);var template=new R2dbcEntityTemplate(client,f.template.getDataAccessStrategy());var other=BoundedR2dbcResources.verify(f.factory,client,template,f.manager);
            return f.execute(scope->f.factory.requireBoundScope(other,scope).then(Mono.fromSupplier(()->{attempts.incrementAndGet();return "write";})));}).expectError(RootTransactionException.class).verify();assertEquals(0,attempts.get());
    }
    @Test void foreignFactoryCannotUseARealActiveScope(){
        var foreign=new BoundedTransactionBoundaryTest.Fixture();
        StepVerifier.withVirtualTime(()->{var f=new BoundedTransactionBoundaryTest.Fixture();return f.execute(scope->foreign.factory.requireBoundScope(resources(foreign),scope).thenReturn("write"));}).expectError(RootTransactionException.class).verify();assertEquals(0,foreign.driver.creates);
    }
    @Test void ordinaryAmbientTransactionDoesNotInventABoundedInvocation(){
        var f=new BoundedTransactionBoundaryTest.Fixture();var raw=new BoundedTransactionBoundaryTest.Driver();var foreignScope=new RootTransactionScope(){public Duration remaining(){return Duration.ofSeconds(1);}public void narrow(R2dbcOperationBudget cap){}};
        assertThrows(IllegalStateException.class,()->TransactionalOperator.create(new R2dbcTransactionManager(raw)).transactional(f.factory.requireBoundScope(resources(f),foreignScope).thenReturn("write")).block());assertEquals(0,f.driver.creates);assertEquals(1,raw.rollbacks);
    }
    @Test void completedScopeDoesNotMatchANewInvocationOnTheSameFactory(){
        var prior=new AtomicReference<RootTransactionScope>();
        StepVerifier.withVirtualTime(()->{var f=new BoundedTransactionBoundaryTest.Fixture();return f.execute(scope->{prior.set(scope);return Mono.just("one");}).flatMap(done->f.execute(current->f.factory.requireBoundScope(resources(f),prior.get()).thenReturn("write")));}).expectError(RootTransactionException.class).verify();
    }
}
