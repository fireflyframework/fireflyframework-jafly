/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.orchestration.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.store.EventStore;
import org.fireflyframework.orchestration.core.model.*;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import org.fireflyframework.orchestration.persistence.cache.CachePersistenceProviderImpl;
import org.fireflyframework.orchestration.persistence.eventsourced.EventSourcedPersistenceProviderImpl;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersistenceProviderLogSafetyTest {
    private static final String BREAKS = "\r\n\u000b\f\u0085\u2028\u2029";
    private static final String VISIBLE = "\\r\\n\\u000b\\f\\u0085\\u2028\\u2029";
    private static final Duration LIMIT = Duration.ofSeconds(2);
    private final List<ILoggingEvent> events = new ArrayList<>();
    private final Map<Logger, Level> levels = new LinkedHashMap<>();
    private final Map<Logger, Boolean> additive = new LinkedHashMap<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing(); events.add(event); }
    };
    @BeforeEach void attach() {
        appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());appender.start();
        for (Class<?> type : List.of(CachePersistenceProviderImpl.class, EventSourcedPersistenceProviderImpl.class)) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);levels.put(logger, logger.getLevel());additive.put(logger, logger.isAdditive());
            logger.setLevel(Level.DEBUG);logger.setAdditive(false);logger.addAppender(appender);
        }
        MDC.put("correlation", "raw" + BREAKS);
    }
    @AfterEach void detach() {
        levels.forEach((logger, level) -> { logger.detachAppender(appender);logger.setLevel(level);logger.setAdditive(additive.get(logger)); });
        appender.stop();MDC.clear();
    }
    @TestFactory Stream<DynamicTest> ordinaryAndLineBoundarySaveDiagnostics() {
        return Stream.of(false, true).flatMap(eventSourced -> Stream.of(false, true).flatMap(failure -> Stream.of("ordinary-id", "id" + BREAKS)
                .map(id -> DynamicTest.dynamicTest((eventSourced ? "event" : "cache") + (failure ? " error " : " success ") + (id.equals("ordinary-id") ? "ordinary" : "visible boundaries"),
                        () -> save(eventSourced, failure, id, false)))));
    }
    @Test void nullCacheSuccessKeepsNullAndRawCacheKey() { save(false, false, null, false); }
    @Test void nullCacheErrorKeepsNullAndExactThrowable() { save(false, true, null, false); }
    @Test void disabledCacheDiagnosticsDoNotChangeStoredIdentity() { save(false, false, "id" + BREAKS, true); }
    @Test void disabledEventDiagnosticsDoNotChangeSerializedIdentity() { save(true, false, "id" + BREAKS, true); }
    private static ExecutionState state(String id) {
        return new ExecutionState(id, "workflow", ExecutionPattern.WORKFLOW, ExecutionStatus.RUNNING,
                Map.of("result", "raw" + BREAKS), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("header", "raw" + BREAKS),
                Set.of(), List.of(), null, Instant.EPOCH, Instant.EPOCH, Optional.empty());
    }
    private void save(boolean eventSourced, boolean failure, String id, boolean disabled) {
        events.clear();
        if (disabled) levels.keySet().forEach(logger -> logger.setLevel(Level.OFF));
        var state = state(id);var error = new IllegalStateException("ordinary provider failure");Mono<Void> work;
        CacheAdapter cache = null;EventStore store = null;EventSourcedPersistenceProviderImpl eventProvider = null;
        if (eventSourced) {
            store = mock(EventStore.class);
            when(store.getAggregateVersion(any(), eq("orchestration-execution"))).thenReturn(Mono.just(0L));
            when(store.appendEvents(any(), eq("orchestration-execution"), anyList(), eq(0L), eq(Map.of())))
                    .thenReturn(failure ? Mono.error(error) : Mono.empty());
            eventProvider = new EventSourcedPersistenceProviderImpl(store, new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Jdk8Module()));
            work = eventProvider.save(state);
        } else {
            cache = mock(CacheAdapter.class);
            when(cache.put(any(), any(), any(Duration.class))).thenReturn(failure ? Mono.error(error) : Mono.empty());
            work = new CachePersistenceProviderImpl(cache, Duration.ofMinutes(5)).save(state);
        }
        if (failure) assertSame(error, assertThrows(IllegalStateException.class, () -> work.block(LIMIT)));
        else assertNull(work.block(LIMIT));
        if (eventSourced) {
            var calls = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(store).appendEvents(any(), eq("orchestration-execution"), calls.capture(), eq(0L), eq(Map.of()));
            Event event = (Event) calls.getValue().getFirst();
            assertEquals(id, event.getMetadata().get("correlationId"));
            assertEquals(Map.of("header", "raw" + BREAKS), event.getMetadata().get("headers"));
            assertEquals(id, eventProvider.getProjection().findByPattern(ExecutionPattern.WORKFLOW).getFirst().correlationId());
        } else {
            verify(cache).put(eq("orchestration:state:" + id), same(state), eq(Duration.ofMinutes(5)));
            verify(cache).put(eq("orchestration:pattern:WORKFLOW:" + id), eq(id), eq(Duration.ofMinutes(5)));
            verify(cache).put(eq("orchestration:status:RUNNING:" + id), eq(id), eq(Duration.ofMinutes(5)));
        }
        assertEquals("raw" + BREAKS, MDC.get("correlation"));
        String template = eventSourced ? failure ? "[orchestration] Failed to save event-sourced state: {}" : "[orchestration] Saved state as event: {}"
                : failure ? "[orchestration] Failed to save execution state: {}" : "[orchestration] Saved execution state: {}";
        var matching = events.stream().filter(e -> e.getMessage().equals(template)).toList();
        if (disabled) { assertTrue(matching.isEmpty());return; }
        assertEquals(1, matching.size());var logged = matching.getFirst();
        assertEquals(failure ? Level.ERROR : Level.DEBUG, logged.getLevel());
        assertArrayEquals(new Object[]{id == null ? null : id.replace(BREAKS, VISIBLE)}, logged.getArgumentArray());
        assertEquals("raw" + BREAKS, logged.getMDCPropertyMap().get("correlation"));
        if (failure) assertSame(error, ((ThrowableProxy) logged.getThrowableProxy()).getThrowable());
        else assertNull(logged.getThrowableProxy());
        for (char value : BREAKS.toCharArray()) assertFalse(logged.getFormattedMessage().contains(String.valueOf(value)));
    }
}
