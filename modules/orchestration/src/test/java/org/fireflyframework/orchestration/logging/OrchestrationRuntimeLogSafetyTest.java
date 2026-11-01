/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fireflyframework.orchestration.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fireflyframework.orchestration.core.argument.ArgumentResolver;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.core.dlq.DeadLetterEntry;
import org.fireflyframework.orchestration.core.dlq.DeadLetterService;
import org.fireflyframework.orchestration.core.event.NoOpEventPublisherImpl;
import org.fireflyframework.orchestration.core.model.*;
import org.fireflyframework.orchestration.core.observability.OrchestrationEvents;
import org.fireflyframework.orchestration.core.persistence.*;
import org.fireflyframework.orchestration.core.step.StepInvoker;
import org.fireflyframework.orchestration.persistence.redis.RedisPersistenceProviderImpl;
import org.fireflyframework.orchestration.workflow.child.ChildWorkflowService;
import org.fireflyframework.orchestration.workflow.engine.*;
import org.fireflyframework.orchestration.workflow.registry.*;
import org.fireflyframework.orchestration.workflow.signal.SignalService;
import org.fireflyframework.orchestration.workflow.timer.TimerService;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

class OrchestrationRuntimeLogSafetyTest {
    private static final String BREAKS = "\r\n\u000b\f\u0085\u2028\u2029";
    private static final String VISIBLE = "\\r\\n\\u000b\\f\\u0085\\u2028\\u2029";
    private static final Duration LIMIT = Duration.ofSeconds(3);
    private static final List<Class<?>> TYPES = List.of(SignalService.class, TimerService.class,
            ChildWorkflowService.class, WorkflowExecutor.class, WorkflowEngine.class, RedisPersistenceProviderImpl.class);
    private final List<ILoggingEvent> captured = new CopyOnWriteArrayList<>();
    private final Map<Logger, Level> oldLevels = new LinkedHashMap<>();
    private final Map<Logger, Boolean> oldAdditive = new LinkedHashMap<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing(); captured.add(event); }
    };
    private static final class Payload {
        @Override public String toString() { throw new AssertionError("Payload must not be rendered"); }
    }
    private record Scenario(String name, java.util.function.Consumer<String> run) { }
    @BeforeEach void attach() {
        appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()); appender.start();
        for (Class<?> type : TYPES) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            oldLevels.put(logger, logger.getLevel()); oldAdditive.put(logger, logger.isAdditive());
            logger.setLevel(Level.DEBUG); logger.setAdditive(false); logger.addAppender(appender);
        }
        MDC.put("correlation", "raw" + BREAKS);
    }
    @AfterEach void detach() {
        oldLevels.forEach((logger, level) -> { logger.detachAppender(appender); logger.setLevel(level); logger.setAdditive(oldAdditive.get(logger)); });
        appender.stop(); MDC.clear();
    }
    private ILoggingEvent event(String template, Level level, Throwable throwable, Object... expected) {
        await().atMost(LIMIT).until(() -> captured.stream().anyMatch(e -> e.getMessage().equals(template)));
        ILoggingEvent e = captured.stream().filter(x -> x.getMessage().equals(template)).findFirst().orElseThrow();
        assertEquals(level, e.getLevel()); assertEquals(template, e.getMessage());
        Object[] escaped = Arrays.stream(expected).map(v -> v instanceof String text ? text.replace(BREAKS, VISIBLE) : v).toArray();
        assertArrayEquals(escaped, e.getArgumentArray());
        for (char c : BREAKS.toCharArray()) assertFalse(e.getFormattedMessage().contains(String.valueOf(c)));
        if (throwable != null) assertSame(throwable, ((ThrowableProxy) e.getThrowableProxy()).getThrowable());
        return e;
    }
    private static ExecutionState state(String id, ExecutionStatus status) {
        return new ExecutionState(id, "workflow", ExecutionPattern.WORKFLOW, status, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), List.of(), null, Instant.EPOCH, Instant.EPOCH, Optional.empty());
    }
    public static final class Step {
        public Mono<String> run() { return Mono.just("result"); }
    }
    private static WorkflowDefinition definition(String id, TriggerMode mode) {
        try {
            var step = new WorkflowStepDefinition("step", "Step", "", List.of(), 0, "", 3000,
                    RetryPolicy.NO_RETRY, "", false, false, "", new Step(), Step.class.getMethod("run"));
            return new WorkflowDefinition(id, "Workflow", "", "1", List.of(step), mode, "", 3000,
                    RetryPolicy.NO_RETRY, null, null, null, null);
        } catch (NoSuchMethodException error) { throw new AssertionError(error); }
    }
    private Object invoke(Object target, String method, Class<?>[] signature, Object... args) {
        try { Method m = target.getClass().getDeclaredMethod(method, signature); m.setAccessible(true); return m.invoke(target, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    @TestFactory Stream<DynamicTest> diagnosticFieldsAndOrdinaryValues() {
        List<Scenario> scenarios = List.of(
                new Scenario("missing signal", this::missingSignal), new Scenario("terminal signal", this::terminalSignal),
                new Scenario("buffered signal", suffix -> deliveredSignal(suffix, false)), new Scenario("resumed signal", suffix -> deliveredSignal(suffix, true)),
                new Scenario("relative timer", suffix -> timer(suffix, false)), new Scenario("absolute timer", suffix -> timer(suffix, true)),
                new Scenario("cancelled timer", this::cancelledTimer), new Scenario("child spawn", this::childSpawn),
                new Scenario("child cancellation error", this::childCancel), new Scenario("checkpoint failure", this::checkpoint),
                new Scenario("dead letter failure", this::deadLetter), new Scenario("async completion", suffix -> asyncWorkflow(suffix, false)),
                new Scenario("async failure", suffix -> asyncWorkflow(suffix, true)), new Scenario("redis prefix", this::redisPrefix),
                new Scenario("redis save", suffix -> redisSave(suffix, false)), new Scenario("redis save failure", suffix -> redisSave(suffix, true)),
                new Scenario("redis malformed state", this::redisMalformed), new Scenario("redis malformed metadata", this::redisMetadata));
        return scenarios.stream().flatMap(s -> Stream.of("", BREAKS).map(suffix -> DynamicTest.dynamicTest(
                s.name() + (suffix.isEmpty() ? " ordinary" : " line breaks"), () -> { captured.clear(); s.run().accept(suffix); })));
    }
    private void missingSignal(String suffix) {
        String id = "missing" + suffix;
        var result = new SignalService(new InMemoryPersistenceProviderImpl(), new OrchestrationEvents() {}).signal(id, "sig", null).block(LIMIT);
        assertNotNull(result); assertFalse(result.delivered()); assertEquals(id, result.correlationId());
        event("[signal] No workflow instance found: {}", Level.WARN, null, id);
    }
    private void terminalSignal(String suffix) {
        String id = "terminal" + suffix; var p = new InMemoryPersistenceProviderImpl(); p.save(state(id, ExecutionStatus.COMPLETED)).block(LIMIT);
        assertFalse(new SignalService(p, new OrchestrationEvents() {}).signal(id, "sig", null).block(LIMIT).delivered());
        event("[signal] Cannot signal terminal instance: {} (status={})", Level.WARN, null, id, ExecutionStatus.COMPLETED);
    }
    private void deliveredSignal(String suffix, boolean waiting) {
        String id = "instance" + suffix, name = "approval" + suffix; Payload payload = new Payload();
        var p = new InMemoryPersistenceProviderImpl(); p.save(state(id, ExecutionStatus.RUNNING)).block(LIMIT);
        var events = mock(OrchestrationEvents.class); var service = new SignalService(p, events);
        Mono<Object> waiter = waiting ? service.waitForSignal(id, name) : null;
        var result = service.signal(id, name, payload).block(LIMIT);
        assertTrue(result.delivered()); assertEquals(id, result.correlationId()); assertEquals(name, result.signalName());
        assertSame(payload, waiting ? waiter.block(LIMIT) : service.getPendingSignals(id).getFirst().payload());
        verify(events).onSignalDelivered("workflow", id, name);
        event(waiting ? "[signal] Signal '{}' delivered and resumed waiter for instance '{}'" : "[signal] Signal '{}' buffered for instance '{}' (no active waiter)", Level.INFO, null, name, id);
    }
    private void timer(String suffix, boolean absolute) {
        String id = "instance" + suffix, timer = "timer" + suffix; Payload payload = new Payload(); var service = new TimerService(new OrchestrationEvents() {});
        var entry = absolute ? service.scheduleAt(id, timer, Instant.EPOCH, payload).block(LIMIT) : service.schedule(id, timer, Duration.ofSeconds(2), payload).block(LIMIT);
        assertEquals(id, entry.correlationId()); assertEquals(timer, entry.timerId()); assertSame(payload, entry.data());
        assertSame(entry, service.getPendingTimers(id).getFirst());
        event("[timer] Scheduled timer '{}' for instance '{}', fires at {}", Level.INFO, null, timer, id, entry.fireAt());
    }
    private void cancelledTimer(String suffix) {
        String id = "instance" + suffix, name = "timer" + suffix; var service = new TimerService(new OrchestrationEvents() {});
        service.scheduleAt(id, name, Instant.EPOCH, new Payload()).block(LIMIT); assertTrue(service.cancel(id, name).block(LIMIT)); assertTrue(service.getPendingTimers(id).isEmpty());
        event("[timer] Cancelled timer '{}' for instance '{}'", Level.INFO, null, name, id);
    }
    private void childSpawn(String suffix) {
        String parent = "parent" + suffix, child = "child" + suffix; Map<String,Object> input = Map.of("raw", new Payload());
        var engine = mock(WorkflowEngine.class); var events = mock(OrchestrationEvents.class); var state = state("child-instance", ExecutionStatus.COMPLETED);
        when(engine.startWorkflow(eq(child), same(input), isNull(), eq("child:" + parent), eq(false))).thenReturn(Mono.just(state));
        var service = new ChildWorkflowService(engine, events); var result = service.spawn(parent, child, input).block(LIMIT);
        assertEquals(parent, result.parentCorrelationId()); assertEquals(List.of("child-instance"), service.getChildren(parent));
        verify(events).onChildWorkflowStarted("workflow", parent, child, "child-instance");
        event("[child-workflow] Spawning child '{}' from parent '{}'", Level.INFO, null, child, parent);
    }
    private void childCancel(String suffix) {
        String child = "child" + suffix; var engine = mock(WorkflowEngine.class); var failure = new IllegalStateException("unavailable" + suffix);
        when(engine.startWorkflow(anyString(), anyMap(), isNull(), anyString(), eq(false))).thenReturn(Mono.just(state(child, ExecutionStatus.RUNNING)));
        when(engine.cancelWorkflow(child)).thenReturn(Mono.error(failure)); var service = new ChildWorkflowService(engine, new OrchestrationEvents() {});
        service.spawn("parent", "workflow", Map.of()).block(LIMIT); service.cancelChildren("parent").block(LIMIT); verify(engine).cancelWorkflow(child);
        event("[child-workflow] Failed to cancel child '{}': {}", Level.WARN, null, child, failure.getMessage());
    }
    @SuppressWarnings("unchecked") private void checkpoint(String suffix) {
        String id = "checkpoint" + suffix; var failure = new IllegalStateException("write failed"); var p = mock(ExecutionPersistenceProvider.class);
        when(p.save(any())).thenReturn(Mono.error(failure)); var ctx = ExecutionContext.forWorkflow(id, "workflow", false);
        var executor = new WorkflowExecutor(new StepInvoker(new ArgumentResolver()), new OrchestrationEvents() {}, new NoOpEventPublisherImpl(), null, null, p);
        ((Mono<Void>) invoke(executor, "saveCheckpoint", new Class<?>[]{String.class, ExecutionContext.class}, "workflow", ctx)).block(LIMIT);
        var saved = org.mockito.ArgumentCaptor.forClass(ExecutionState.class); verify(p).save(saved.capture()); assertEquals(id, saved.getValue().correlationId());
        event("[orchestration] Failed to save workflow checkpoint: {}", Level.WARN, failure, id);
    }
    @SuppressWarnings("unchecked") private void deadLetter(String suffix) {
        String id = "dlq" + suffix; var failure = new IllegalStateException("queue unavailable"); var dlq = mock(DeadLetterService.class);
        when(dlq.deadLetter(any())).thenReturn(Mono.error(failure)); var ctx = ExecutionContext.forWorkflow(id, "workflow", false);
        var engine = new WorkflowEngine(new WorkflowRegistry(), null, null, new InMemoryPersistenceProviderImpl(), new OrchestrationEvents() {}, new NoOpEventPublisherImpl(), dlq);
        ((Mono<Void>) invoke(engine, "saveToDlq", new Class<?>[]{String.class, ExecutionContext.class, Throwable.class, Map.class}, "workflow", ctx, failure, Map.of())).block(LIMIT);
        var entry = org.mockito.ArgumentCaptor.forClass(DeadLetterEntry.class); verify(dlq).deadLetter(entry.capture()); assertEquals(id, entry.getValue().correlationId());
        event("[workflow] Failed to save to DLQ: {}", Level.WARN, failure, id);
    }
    private void asyncWorkflow(String suffix, boolean failed) {
        String id = "async" + suffix; var registry = new WorkflowRegistry(); registry.register(definition(id, TriggerMode.ASYNC)); var executor = mock(WorkflowExecutor.class);
        when(executor.execute(any(), any())).thenAnswer(inv -> Mono.just(inv.getArgument(1, ExecutionContext.class)));
        var p = mock(ExecutionPersistenceProvider.class); var failure = new IllegalStateException("persistence unavailable");
        when(p.save(any())).thenReturn(Mono.empty()); if (failed) when(p.save(any())).thenReturn(Mono.empty(), Mono.error(failure));
        var engine = new WorkflowEngine(registry, executor, null, p, new OrchestrationEvents() {}, new NoOpEventPublisherImpl());
        assertEquals(id, engine.startWorkflow(id, Map.of(), "raw-correlation", "api", false).block(LIMIT).executionName());
        event(failed ? "[workflow] Async workflow '{}' failed unexpectedly" : "[workflow] Async workflow '{}' completed with status {}", failed ? Level.ERROR : Level.INFO, failed ? failure : null,
                failed ? new Object[]{id} : new Object[]{id, ExecutionStatus.COMPLETED});
        var saved = org.mockito.ArgumentCaptor.forClass(ExecutionState.class); verify(p, atLeastOnce()).save(saved.capture()); assertTrue(saved.getAllValues().stream().allMatch(s -> s.executionName().equals(id)));
    }
    private RedisPersistenceProviderImpl redis(ReactiveRedisTemplate<String,String> template, String prefix) {
        return new RedisPersistenceProviderImpl(template, new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule()), prefix, null);
    }
    private void redisPrefix(String suffix) {
        String prefix = "tenant:" + suffix; redis(null, prefix);
        event("[orchestration] RedisPersistenceProvider initialized with prefix: {}", Level.INFO, null, prefix);
    }
    @SuppressWarnings("unchecked") private void redisSave(String suffix, boolean fail) {
        String id = "state" + suffix, prefix = "prefix:"; var template = (ReactiveRedisTemplate<String,String>) mock(ReactiveRedisTemplate.class);
        var ops = (ReactiveValueOperations<String,String>) mock(ReactiveValueOperations.class); when(template.opsForValue()).thenReturn(ops);
        var failure = new IllegalStateException("redis unavailable"); when(ops.set(anyString(), anyString())).thenReturn(fail ? Mono.error(failure) : Mono.just(true));
        var saved = state(id, ExecutionStatus.RUNNING); var publisher = redis(template, prefix).save(saved);
        if (fail) assertSame(failure, assertThrows(IllegalStateException.class, () -> publisher.block(LIMIT))); else publisher.block(LIMIT);
        verify(ops).set(eq(prefix + "state:" + id), contains("correlationId")); verify(ops).set(eq(prefix + "meta:" + id), contains("WORKFLOW"));
        ILoggingEvent e = event(fail ? "[orchestration] Failed to save to Redis: {}" : "[orchestration] Saved state to Redis: {}", fail ? Level.ERROR : Level.DEBUG, fail ? failure : null, id);
        assertEquals("raw" + BREAKS, e.getMDCPropertyMap().get("correlation"));
    }
    @SuppressWarnings("unchecked") private void redisMalformed(String suffix) {
        String id = "lookup" + suffix; var template = (ReactiveRedisTemplate<String,String>) mock(ReactiveRedisTemplate.class); var ops = (ReactiveValueOperations<String,String>) mock(ReactiveValueOperations.class);
        when(template.opsForValue()).thenReturn(ops); when(ops.get("prefix:state:" + id)).thenReturn(Mono.just("{"));
        assertTrue(redis(template, "prefix:").findById(id).block(LIMIT).isEmpty()); verify(ops).get("prefix:state:" + id);
        ILoggingEvent e = event("[orchestration] Failed to deserialize state: {}", Level.ERROR, null, id);
        assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class, ((ThrowableProxy)e.getThrowableProxy()).getThrowable());
    }
    private void redisMetadata(String suffix) {
        String key = "prefix:meta:entry" + suffix; var provider = redis(null, "prefix:");
        assertNull(invoke(provider, "parseMetaEntry", new Class<?>[]{String.class,String.class}, key, "unsupported|RUNNING"));
        ILoggingEvent e = event("[orchestration] Failed to parse meta entry: {}", Level.WARN, null, key);
        assertInstanceOf(IllegalArgumentException.class, ((ThrowableProxy)e.getThrowableProxy()).getThrowable());
    }
    @Test void nullPrefixRemainsNullDiagnosticAndDefaultStoragePrefix() {
        redis(null, null); event("[orchestration] RedisPersistenceProvider initialized with prefix: {}", Level.INFO, null, new Object[]{null});
    }
}
