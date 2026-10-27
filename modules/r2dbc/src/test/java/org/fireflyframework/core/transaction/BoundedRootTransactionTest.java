package org.fireflyframework.core.transaction;

import io.r2dbc.spi.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class BoundedRootTransactionTest {
    static final class Driver implements ConnectionFactory {
        int creates,begins,beginCancelled,commits,closes;
        final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()) {
            case "isAutoCommit" -> true;
            case "getTransactionIsolationLevel" -> IsolationLevel.READ_COMMITTED;
            case "beginTransaction" -> Mono.defer(()->{begins++;return Mono.<Void>never().doOnCancel(()->beginCancelled++);});
            case "commitTransaction" -> Mono.fromRunnable(()->commits++);
            case "close" -> Mono.fromRunnable(()->closes++);
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            case "toString" -> "OwnedNetworklessLease";
            default -> Mono.empty();
        });
        public Publisher<? extends Connection> create(){return Mono.defer(()->{creates++;return Mono.just(connection);});}
        public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
    }
    @Test void boundedRootBeginCancellationClosesExactlyItsCreatedLease() {
        var d=new Driver();StepVerifier.withVirtualTime(()->proposedOrOriginal(d))
            .thenAwait(Duration.ofSeconds(8)).expectError().verify(Duration.ofSeconds(1));
        assertEquals(1,d.creates);assertEquals(1,d.begins);assertEquals(1,d.beginCancelled);assertEquals(1,d.closes);assertEquals(0,d.commits);
    }
    @Test void originalSpringOuterTimeoutCharacterizationLeavesBeginLeaseUnclosed() {
        var d=new Driver();StepVerifier.withVirtualTime(()->TransactionalOperator.create(new R2dbcTransactionManager(d)).transactional(Mono.just("source-value")).timeout(Duration.ofSeconds(5)))
            .thenAwait(Duration.ofSeconds(6)).expectError(java.util.concurrent.TimeoutException.class).verify(Duration.ofSeconds(1));
        assertEquals(1,d.beginCancelled);assertEquals(0,d.closes);assertEquals(0,d.commits);
    }
    static Mono<?> proposedOrOriginal(Driver d) {
        var factory=new OwnedLeaseConnectionFactory(d);var client=org.springframework.r2dbc.core.DatabaseClient.create(factory);
        var template=new org.springframework.data.r2dbc.core.R2dbcEntityTemplate(client,org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE);
        var resources=BoundedR2dbcResources.verify(factory,client,template,new R2dbcTransactionManager(factory));
        var now=Instant.now();var budget=R2dbcOperationBudget.observed(now,now.plusSeconds(5),System.nanoTime(),Duration.ofSeconds(5),Clock.systemUTC(),System::nanoTime);
        return new org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction(resources,new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)))
            .execute(budget,new org.springframework.transaction.support.DefaultTransactionDefinition(),scope->Mono.just("source-value"));
    }
}
