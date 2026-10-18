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

package org.fireflyframework.eventsourcing.transaction;

import org.fireflyframework.eventsourcing.annotation.EventSourcingTransactional;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import org.fireflyframework.eventsourcing.publisher.EventSourcingPublisher;
import org.fireflyframework.eventsourcing.store.ConcurrencyException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.retry.Retry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AOP Aspect that provides transactional behavior for methods annotated with
 * {@link EventSourcingTransactional}.
 * <p>
 * This aspect provides full ACID guarantees for event sourcing operations:
 * <ul>
 *   <li><b>Atomicity</b> - All events are saved or none. If any operation fails,
 *       the entire transaction is rolled back and no events are persisted.</li>
 *   <li><b>Consistency</b> - Aggregate version checks ensure data integrity.
 *       Concurrent modifications are detected via optimistic locking.</li>
 *   <li><b>Isolation</b> - Configurable isolation levels (READ_COMMITTED, SERIALIZABLE, etc.)
 *       control visibility of changes between concurrent transactions.</li>
 *   <li><b>Durability</b> - Once committed, events are permanently stored and
 *       survive system failures.</li>
 * </ul>
 * <p>
 * <b>Transaction Lifecycle:</b>
 * <ol>
 *   <li><b>Begin</b> - Transaction started based on propagation settings</li>
 *   <li><b>Execute</b> - Method logic runs within transaction boundary</li>
 *   <li><b>Commit</b> - On success, all changes are committed atomically</li>
 *   <li><b>Publish</b> - Events published to message broker (Transactional Outbox)</li>
 *   <li><b>Rollback</b> - On failure, all changes are discarded</li>
 * </ol>
 * <p>
 * <b>Rollback Behavior:</b>
 * <p>
 * Transactions are automatically rolled back on:
 * <ul>
 *   <li>{@link RuntimeException} and subclasses (e.g., IllegalStateException)</li>
 *   <li>{@link Error} and subclasses (e.g., OutOfMemoryError)</li>
 *   <li>{@link org.fireflyframework.eventsourcing.store.ConcurrencyException}</li>
 *   <li>Database errors (connection failures, constraint violations)</li>
 * </ul>
 * <p>
 * Transactions are NOT rolled back on checked exceptions unless explicitly configured.
 * <p>
 * <b>Concurrency Control:</b>
 * <p>
 * Uses optimistic locking with automatic version checking:
 * <ul>
 *   <li>Each aggregate has a version number</li>
 *   <li>Version incremented on each change</li>
 *   <li>Concurrent modifications detected via version mismatch</li>
 *   <li>Optional automatic retry with exponential backoff</li>
 * </ul>
 * <p>
 * <b>Reactive Support:</b>
 * <p>
 * Fully supports reactive programming with Project Reactor.
 * Works with both {@link Mono} and {@link Flux} return types.
 * Transactions are managed reactively without blocking threads.
 * <p>
 * <b>Example - Successful Transaction:</b>
 * <pre>
 * {@code
 * @EventSourcingTransactional
 * public Mono<Account> withdraw(UUID accountId, BigDecimal amount) {
 *     return eventStore.loadEventStream(accountId, "Account")
 *         .map(stream -> Account.fromEvents(stream.getEvents()))
 *         .flatMap(account -> {
 *             account.withdraw(amount);  // Business logic
 *             return eventStore.appendEvents(
 *                 accountId, "Account", account.getUncommittedEvents(),
 *                 account.getVersion(), null
 *             );
 *         })
 *         .thenReturn(account);
 *     // Transaction commits here, events published to Kafka
 * }
 * }
 * </pre>
 * <p>
 * <b>Example - Rollback on Error:</b>
 * <pre>
 * {@code
 * @EventSourcingTransactional
 * public Mono<Account> withdraw(UUID accountId, BigDecimal amount) {
 *     return eventStore.loadEventStream(accountId, "Account")
 *         .map(stream -> Account.fromEvents(stream.getEvents()))
 *         .flatMap(account -> {
 *             if (account.getBalance().compareTo(amount) < 0) {
 *                 // RuntimeException triggers rollback
 *                 return Mono.error(new InsufficientFundsException());
 *             }
 *             account.withdraw(amount);
 *             return eventStore.appendEvents(...);
 *         });
 *     // Transaction rolled back, no events saved, exception propagated
 * }
 * }
 * </pre>
 *
 * @see EventSourcingTransactional
 * @see TransactionalOperator
 * @see org.springframework.transaction.ReactiveTransactionManager
 */
@Aspect
public class EventSourcingTransactionalAspect {

    private static final Logger log = LoggerFactory.getLogger(EventSourcingTransactionalAspect.class);
    private static final String PENDING_EVENTS_KEY = "eventsourcing.pending.events";

    private final ReactiveTransactionManager transactionManager;
    private final EventSourcingPublisher eventPublisher;

    public EventSourcingTransactionalAspect(
            ReactiveTransactionManager transactionManager,
            EventSourcingPublisher eventPublisher) {
        this.transactionManager = transactionManager;
        this.eventPublisher = eventPublisher;
        log.info("EventSourcingTransactionalAspect initialized with transaction manager and event publisher");
    }

    /**
     * Intercepts methods annotated with @EventSourcingTransactional.
     * <p>
     * This advice wraps the method execution in a transaction and handles
     * all the transactional concerns defined by the annotation.
     *
     * @param joinPoint the join point representing the intercepted method
     * @return the result of the method execution
     * @throws Throwable if the method execution fails
     */
    @Around("@annotation(org.fireflyframework.eventsourcing.annotation.EventSourcingTransactional)")
    public Object handleTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        EventSourcingTransactional annotation = AnnotationUtils.findAnnotation(
            method, 
            EventSourcingTransactional.class
        );
        
        if (annotation == null) {
            // Fallback: proceed without transaction
            log.warn("@EventSourcingTransactional annotation not found on method {}", method.getName());
            return joinPoint.proceed();
        }

        Class<?> returnType = method.getReturnType();
        
        // Handle Mono return type
        if (Mono.class.isAssignableFrom(returnType)) {
            return handleMonoTransaction(joinPoint, annotation);
        }
        
        // Handle Flux return type
        if (Flux.class.isAssignableFrom(returnType)) {
            return handleFluxTransaction(joinPoint, annotation);
        }
        
        // Non-reactive methods not supported
        throw new UnsupportedOperationException(
            "@EventSourcingTransactional only supports reactive return types (Mono or Flux). " +
            "Method: " + method.getName()
        );
    }

    /**
     * Handles transactional execution for methods returning Mono.
     */
    @SuppressWarnings("unchecked")
    private Mono<?> handleMonoTransaction(
            ProceedingJoinPoint joinPoint,
            EventSourcingTransactional annotation) {

        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        long startTime = System.currentTimeMillis();

        EventSourcingLoggingContext.setOperation(methodName);
        EventSourcingLoggingContext.getOrGenerateCorrelationId();

        log.info("Starting transactional operation: {}.{} (propagation={}, isolation={})",
                className, methodName, annotation.propagation(), annotation.isolation());

        return Mono.defer(() -> {
            try {
                Object result = joinPoint.proceed();
                return (Mono<?>) result;
            } catch (Throwable e) {
                return Mono.error(e);
            }
        })
        .as(mono -> applyTransactionalOperator(mono, annotation))
        .as(mono -> applyRetryStrategy(mono, annotation))
        .flatMap(result -> publishEventsIfNeeded(result, annotation))
        .doOnSuccess(result -> {
            long duration = System.currentTimeMillis() - startTime;
            EventSourcingLoggingContext.setDuration(duration);
            log.info("Transactional operation completed successfully: {}.{} in {}ms",
                    className, methodName, duration);
            EventSourcingLoggingContext.clearOperationContext();
        })
        .doOnError(error -> {
            long duration = System.currentTimeMillis() - startTime;
            EventSourcingLoggingContext.setDuration(duration);

            if (error instanceof ConcurrencyException) {
                log.warn("Concurrency conflict in {}.{} after {}ms: {}",
                        className, methodName, duration, error.getMessage());
            } else {
                log.error("Transactional operation failed: {}.{} after {}ms",
                        className, methodName, duration, error);
            }
            EventSourcingLoggingContext.clearOperationContext();
        });
    }

    /**
     * Handles transactional execution for methods returning Flux.
     */
    @SuppressWarnings("unchecked")
    private Flux<?> handleFluxTransaction(
            ProceedingJoinPoint joinPoint,
            EventSourcingTransactional annotation) {

        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        long startTime = System.currentTimeMillis();

        EventSourcingLoggingContext.setOperation(methodName);
        EventSourcingLoggingContext.getOrGenerateCorrelationId();

        log.info("Starting transactional Flux operation: {}.{} (propagation={}, isolation={})",
                className, methodName, annotation.propagation(), annotation.isolation());

        return Flux.defer(() -> {
            try {
                Object result = joinPoint.proceed();
                return (Flux<?>) result;
            } catch (Throwable e) {
                return Flux.error(e);
            }
        })
        .as(flux -> applyTransactionalOperator(flux, annotation))
        .as(flux -> applyRetryStrategy(flux, annotation))
        .flatMap(result -> publishEventsIfNeeded(result, annotation))
        .doOnComplete(() -> {
            long duration = System.currentTimeMillis() - startTime;
            EventSourcingLoggingContext.setDuration(duration);
            log.info("Transactional Flux operation completed: {}.{} in {}ms",
                    className, methodName, duration);
            EventSourcingLoggingContext.clearOperationContext();
        })
        .doOnError(error -> {
            long duration = System.currentTimeMillis() - startTime;
            EventSourcingLoggingContext.setDuration(duration);

            if (error instanceof ConcurrencyException) {
                log.warn("Concurrency conflict in Flux {}.{} after {}ms: {}",
                        className, methodName, duration, error.getMessage());
            } else {
                log.error("Transactional Flux operation failed: {}.{} after {}ms",
                        className, methodName, duration, error);
            }
            EventSourcingLoggingContext.clearOperationContext();
        });
    }

    /**
     * Applies the transactional operator based on annotation settings.
     */
    private <T> Mono<T> applyTransactionalOperator(Mono<T> mono, EventSourcingTransactional annotation) {
        TransactionalOperator operator = createTransactionalOperator(annotation);
        return operator.transactional(mono);
    }

    /**
     * Applies the transactional operator to a Flux.
     */
    private <T> Flux<T> applyTransactionalOperator(Flux<T> flux, EventSourcingTransactional annotation) {
        TransactionalOperator operator = createTransactionalOperator(annotation);
        return operator.transactional(flux);
    }

    /**
     * Creates a TransactionalOperator based on annotation settings.
     */
    private TransactionalOperator createTransactionalOperator(EventSourcingTransactional annotation) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();

        // Set propagation behavior
        definition.setPropagationBehavior(mapPropagation(annotation.propagation()));

        // Set isolation level
        definition.setIsolationLevel(mapIsolation(annotation.isolation()));

        // Set read-only flag
        definition.setReadOnly(annotation.readOnly());

        // Set timeout
        if (annotation.timeout() > 0) {
            definition.setTimeout(annotation.timeout());
        }

        // Note: Rollback rules are handled by Spring's TransactionalOperator automatically
        // RuntimeException and Error cause rollback by default
        // We could add custom rollback rules here if needed in the future

        log.debug("Created transaction definition: propagation={}, isolation={}, readOnly={}, timeout={}",
            annotation.propagation(), annotation.isolation(), annotation.readOnly(), annotation.timeout());

        return TransactionalOperator.create(transactionManager, definition);
    }

    /**
     * Maps annotation propagation to Spring's propagation constants.
     */
    private int mapPropagation(EventSourcingTransactional.Propagation propagation) {
        return switch (propagation) {
            case REQUIRED -> DefaultTransactionDefinition.PROPAGATION_REQUIRED;
            case REQUIRES_NEW -> DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW;
            case MANDATORY -> DefaultTransactionDefinition.PROPAGATION_MANDATORY;
            case NEVER -> DefaultTransactionDefinition.PROPAGATION_NEVER;
            case SUPPORTS -> DefaultTransactionDefinition.PROPAGATION_SUPPORTS;
            case NOT_SUPPORTED -> DefaultTransactionDefinition.PROPAGATION_NOT_SUPPORTED;
        };
    }

    /**
     * Maps annotation isolation to Spring's isolation constants.
     */
    private int mapIsolation(EventSourcingTransactional.Isolation isolation) {
        return switch (isolation) {
            case DEFAULT -> DefaultTransactionDefinition.ISOLATION_DEFAULT;
            case READ_UNCOMMITTED -> DefaultTransactionDefinition.ISOLATION_READ_UNCOMMITTED;
            case READ_COMMITTED -> DefaultTransactionDefinition.ISOLATION_READ_COMMITTED;
            case REPEATABLE_READ -> DefaultTransactionDefinition.ISOLATION_REPEATABLE_READ;
            case SERIALIZABLE -> DefaultTransactionDefinition.ISOLATION_SERIALIZABLE;
        };
    }

    /**
     * Applies retry strategy for concurrency conflicts if enabled.
     */
    private <T> Mono<T> applyRetryStrategy(Mono<T> mono, EventSourcingTransactional annotation) {
        if (!annotation.retryOnConcurrencyConflict()) {
            return mono;
        }

        return mono.retryWhen(
            Retry.backoff(annotation.maxRetries(), Duration.ofMillis(annotation.retryDelay()))
                .filter(throwable -> throwable instanceof ConcurrencyException)
                .doBeforeRetry(signal -> 
                    log.warn("Retrying due to concurrency conflict. Attempt: {}", 
                        signal.totalRetries() + 1)
                )
                .onRetryExhaustedThrow((spec, signal) -> signal.failure())
        );
    }

    /**
     * Applies retry strategy to a Flux.
     */
    private <T> Flux<T> applyRetryStrategy(Flux<T> flux, EventSourcingTransactional annotation) {
        if (!annotation.retryOnConcurrencyConflict()) {
            return flux;
        }

        return flux.retryWhen(
            Retry.backoff(annotation.maxRetries(), Duration.ofMillis(annotation.retryDelay()))
                .filter(throwable -> throwable instanceof ConcurrencyException)
                .doBeforeRetry(signal -> 
                    log.warn("Retrying due to concurrency conflict. Attempt: {}", 
                        signal.totalRetries() + 1)
                )
                .onRetryExhaustedThrow((spec, signal) -> signal.failure())
        );
    }

    /**
     * Publishes events after successful transaction commit if enabled.
     */
    private <T> Mono<T> publishEventsIfNeeded(T result, EventSourcingTransactional annotation) {
        if (!annotation.publishEvents()) {
            return Mono.just(result);
        }

        // Publish events from Reactor context after transaction commit
        return Mono.deferContextual(ctx -> {
            List<StoredEventEnvelope> pendingEvents = ctx.getOrDefault(PENDING_EVENTS_KEY, new ArrayList<>());

            if (pendingEvents.isEmpty()) {
                log.debug("No pending events to publish");
                return Mono.just(result);
            }

            log.debug("Publishing {} events after transaction commit", pendingEvents.size());

            return eventPublisher.publishEvents(pendingEvents)
                .thenReturn(result)
                .doOnSuccess(v -> log.debug("Successfully published {} events", pendingEvents.size()))
                .doOnError(error ->
                    log.error("Failed to publish {} events after transaction commit",
                        pendingEvents.size(), error)
                )
                .onErrorResume(error -> {
                    // Don't fail the transaction if publishing fails
                    // Events are already persisted and can be republished later
                    log.warn("Continuing despite event publishing failure - events are persisted", error);
                    return Mono.just(result);
                });
        });
    }

    /**
     * Adds an event envelope to the pending events list in the Reactor context.
     * This is used by the EventStore to track events that need to be published.
     */
    public static <T> Mono<T> addPendingEvent(Mono<T> mono, StoredEventEnvelope event) {
        return mono.contextWrite(ctx -> {
            List<StoredEventEnvelope> events = new ArrayList<>(ctx.getOrDefault(PENDING_EVENTS_KEY, new ArrayList<>()));
            events.add(event);
            return ctx.put(PENDING_EVENTS_KEY, events);
        });
    }

    /**
     * Adds multiple event envelopes to the pending events list in the Reactor context.
     */
    public static <T> Mono<T> addPendingEvents(Mono<T> mono, List<StoredEventEnvelope> newEvents) {
        return mono.contextWrite(ctx -> {
            List<StoredEventEnvelope> events = new ArrayList<>(ctx.getOrDefault(PENDING_EVENTS_KEY, new ArrayList<>()));
            events.addAll(newEvents);
            return ctx.put(PENDING_EVENTS_KEY, events);
        });
    }
}

