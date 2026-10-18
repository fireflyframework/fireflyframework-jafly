package org.fireflyframework.eventsourcing.logging;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Utility class for managing logging context in event sourcing operations.
 * <p>
 * This class provides methods to set and clear MDC (Mapped Diagnostic Context) values
 * for correlation IDs, aggregate IDs, event types, and other contextual information
 * that helps with distributed tracing and log analysis.
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * {@code
 * EventSourcingLoggingContext.withAggregateContext(aggregateId, "Account", () -> {
 *     // Your code here - all logs will include aggregate context
 *     log.info("Processing account operation");
 * });
 * }
 * </pre>
 *
 * @author Firefly Team
 * @since 1.0.0
 */
@UtilityClass
@Slf4j
public class EventSourcingLoggingContext {

    // MDC Keys
    public static final String CORRELATION_ID = "correlationId";
    public static final String CAUSATION_ID = "causationId";
    public static final String AGGREGATE_ID = "aggregateId";
    public static final String AGGREGATE_TYPE = "aggregateType";
    public static final String EVENT_TYPE = "eventType";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String OPERATION = "operation";
    public static final String DURATION = "duration";
    public static final String VERSION = "version";
    public static final String GLOBAL_SEQUENCE = "globalSequence";
    public static final String OUTBOX_ID = "outboxId";
    public static final String STATUS = "status";
    public static final String RETRY_COUNT = "retryCount";
    public static final String PRIORITY = "priority";
    public static final String DESTINATION = "destination";

    /**
     * Sets the correlation ID in the MDC context.
     *
     * @param correlationId the correlation ID
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }

    /**
     * Sets the causation ID in the MDC context.
     *
     * @param causationId the causation ID
     */
    public static void setCausationId(String causationId) {
        if (causationId != null && !causationId.isEmpty()) {
            MDC.put(CAUSATION_ID, causationId);
        }
    }

    /**
     * Gets the correlation ID from the MDC context, or generates a new one if not present.
     *
     * @return the correlation ID
     */
    public static String getOrGenerateCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            setCorrelationId(correlationId);
        }
        return correlationId;
    }

    /**
     * Sets the aggregate context in the MDC.
     *
     * @param aggregateId the aggregate ID
     * @param aggregateType the aggregate type
     */
    public static void setAggregateContext(UUID aggregateId, String aggregateType) {
        if (aggregateId != null) {
            MDC.put(AGGREGATE_ID, aggregateId.toString());
        }
        if (aggregateType != null && !aggregateType.isEmpty()) {
            MDC.put(AGGREGATE_TYPE, aggregateType);
        }
    }

    /**
     * Sets the aggregate context with version in the MDC.
     *
     * @param aggregateId the aggregate ID
     * @param aggregateType the aggregate type
     * @param version the aggregate version
     */
    public static void setAggregateContext(UUID aggregateId, String aggregateType, long version) {
        setAggregateContext(aggregateId, aggregateType);
        MDC.put(VERSION, String.valueOf(version));
    }

    /**
     * Sets the event type in the MDC context.
     *
     * @param eventType the event type
     */
    public static void setEventType(String eventType) {
        if (eventType != null && !eventType.isEmpty()) {
            MDC.put(EVENT_TYPE, eventType);
        }
    }

    /**
     * Sets the tenant ID in the MDC context.
     *
     * @param tenantId the tenant ID
     */
    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            MDC.put(TENANT_ID, tenantId);
        }
    }

    /**
     * Sets the user ID in the MDC context.
     *
     * @param userId the user ID
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            MDC.put(USER_ID, userId);
        }
    }

    /**
     * Sets the operation name in the MDC context.
     *
     * @param operation the operation name
     */
    public static void setOperation(String operation) {
        if (operation != null && !operation.isEmpty()) {
            MDC.put(OPERATION, operation);
        }
    }

    /**
     * Sets the operation duration in the MDC context.
     *
     * @param durationMs the duration in milliseconds
     */
    public static void setDuration(long durationMs) {
        MDC.put(DURATION, String.valueOf(durationMs));
    }

    /**
     * Sets the global sequence in the MDC context.
     *
     * @param globalSequence the global sequence number
     */
    public static void setGlobalSequence(long globalSequence) {
        MDC.put(GLOBAL_SEQUENCE, String.valueOf(globalSequence));
    }

    /**
     * Sets the outbox ID in the MDC context.
     *
     * @param outboxId the outbox ID
     */
    public static void setOutboxId(UUID outboxId) {
        if (outboxId != null) {
            MDC.put(OUTBOX_ID, outboxId.toString());
        }
    }

    /**
     * Sets the status in the MDC context.
     *
     * @param status the status
     */
    public static void setStatus(String status) {
        if (status != null && !status.isEmpty()) {
            MDC.put(STATUS, status);
        }
    }

    /**
     * Sets the retry count in the MDC context.
     *
     * @param retryCount the retry count
     */
    public static void setRetryCount(int retryCount) {
        MDC.put(RETRY_COUNT, String.valueOf(retryCount));
    }

    /**
     * Sets the priority in the MDC context.
     *
     * @param priority the priority
     */
    public static void setPriority(int priority) {
        MDC.put(PRIORITY, String.valueOf(priority));
    }

    /**
     * Sets the destination in the MDC context.
     *
     * @param destination the destination
     */
    public static void setDestination(String destination) {
        if (destination != null && !destination.isEmpty()) {
            MDC.put(DESTINATION, destination);
        }
    }

    /**
     * Clears the aggregate context from the MDC.
     */
    public static void clearAggregateContext() {
        MDC.remove(AGGREGATE_ID);
        MDC.remove(AGGREGATE_TYPE);
        MDC.remove(VERSION);
    }

    /**
     * Clears the event type from the MDC.
     */
    public static void clearEventType() {
        MDC.remove(EVENT_TYPE);
    }

    /**
     * Clears the operation context from the MDC.
     */
    public static void clearOperationContext() {
        MDC.remove(OPERATION);
        MDC.remove(DURATION);
    }

    /**
     * Clears all event sourcing related MDC values.
     */
    public static void clearAll() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(CAUSATION_ID);
        MDC.remove(AGGREGATE_ID);
        MDC.remove(AGGREGATE_TYPE);
        MDC.remove(EVENT_TYPE);
        MDC.remove(TENANT_ID);
        MDC.remove(USER_ID);
        MDC.remove(OPERATION);
        MDC.remove(DURATION);
        MDC.remove(VERSION);
        MDC.remove(GLOBAL_SEQUENCE);
        MDC.remove(OUTBOX_ID);
        MDC.remove(STATUS);
        MDC.remove(RETRY_COUNT);
        MDC.remove(PRIORITY);
        MDC.remove(DESTINATION);
    }

    /**
     * Clears outbox-related MDC values.
     */
    public static void clearOutboxContext() {
        MDC.remove(OUTBOX_ID);
        MDC.remove(STATUS);
        MDC.remove(RETRY_COUNT);
        MDC.remove(PRIORITY);
    }

    /**
     * Executes a runnable with aggregate context set in the MDC.
     *
     * @param aggregateId the aggregate ID
     * @param aggregateType the aggregate type
     * @param runnable the runnable to execute
     */
    public static void withAggregateContext(UUID aggregateId, String aggregateType, Runnable runnable) {
        try {
            setAggregateContext(aggregateId, aggregateType);
            runnable.run();
        } finally {
            clearAggregateContext();
        }
    }

    /**
     * Executes a runnable with operation context set in the MDC.
     *
     * @param operation the operation name
     * @param runnable the runnable to execute
     */
    public static void withOperationContext(String operation, Runnable runnable) {
        long startTime = System.currentTimeMillis();
        try {
            setOperation(operation);
            runnable.run();
        } finally {
            setDuration(System.currentTimeMillis() - startTime);
            log.debug("Operation '{}' completed in {}ms", operation, MDC.get(DURATION));
            clearOperationContext();
        }
    }

    /**
     * Wraps a Mono with MDC context propagation.
     * <p>
     * With {@code Hooks.enableAutomaticContextPropagation()} enabled by the observability
     * module, MDC is automatically bridged between ThreadLocal and Reactor Context.
     * This method captures the current MDC and writes it to the Reactor Context so that
     * downstream operators can access it across thread boundaries.
     * <p>
     * <b>Note:</b> The previous implementation used {@code doOnEach/MDC.setContextMap()}
     * which was expensive per-signal and racy. The automatic context propagation mechanism
     * now handles MDC restoration transparently.
     *
     * @param mono the Mono to wrap
     * @param <T> the type of the Mono
     * @return the wrapped Mono with MDC context
     */
    public static <T> Mono<T> withMdcContext(Mono<T> mono) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap == null || contextMap.isEmpty()) {
            return mono;
        }

        return mono.contextWrite(ctx -> {
            Context updated = ctx;
            for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                updated = updated.put(entry.getKey(), entry.getValue());
            }
            return updated;
        });
    }

    /**
     * Creates a function that wraps a Mono with MDC context propagation.
     * <p>
     * With {@code Hooks.enableAutomaticContextPropagation()} enabled by the observability
     * module, this simply writes the current MDC values to the Reactor Context.
     *
     * @param <T> the input type
     * @param <R> the result type
     * @return a function that wraps Monos with MDC context
     */
    public static <T, R> Function<Mono<R>, Mono<R>> mdcContextPropagation() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap == null || contextMap.isEmpty()) {
            return mono -> mono;
        }

        return mono -> mono.contextWrite(ctx -> {
            Context updated = ctx;
            for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                updated = updated.put(entry.getKey(), entry.getValue());
            }
            return updated;
        });
    }
}

