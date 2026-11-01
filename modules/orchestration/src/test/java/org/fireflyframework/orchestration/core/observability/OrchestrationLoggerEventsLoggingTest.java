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

package org.fireflyframework.orchestration.core.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.orchestration.core.context.TccPhase;
import org.fireflyframework.orchestration.core.model.ExecutionPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class OrchestrationLoggerEventsLoggingTest {
    private static final String BREAKS="\r\n\u000b\f\u0085\u2028\u2029";
    private static final String VISIBLE="\\r\\n\\u000b\\f\\u0085\\u2028\\u2029";
    private final OrchestrationLoggerEventsImpl events=new OrchestrationLoggerEventsImpl();
    private Logger logger;
    private Level previousLevel;
    private boolean previousAdditive;
    private ListAppender<ILoggingEvent> capture;
    private static final class Failure extends IllegalStateException {
        private int messageReads;
        Failure(String message) { super(message); }
        @Override public String getMessage() { messageReads++;return super.getMessage(); }
        @Override public String toString() { throw new AssertionError("Throwable rendering is not a diagnostic field"); }
    }
    private record Sample(String name,String correlation,String step,ExecutionPattern pattern,TccPhase phase,String message,Failure failure) {
        static Sample ordinary() { return new Sample("checkout","corr-1","step-1",ExecutionPattern.SAGA,TccPhase.CONFIRM,"business refusal",new Failure("business refusal")); }
        static Sample text(String suffix) { return new Sample("name"+suffix,"correlation"+suffix,"step"+suffix,ExecutionPattern.SAGA,TccPhase.CONFIRM,"failure"+suffix,new Failure("failure"+suffix)); }
    }
    private record Callback(String name,Level level,String template,boolean usesFailure,BiConsumer<OrchestrationLoggerEventsImpl,Sample> invoke,Function<Sample,Object[]> arguments,String ordinaryMessage) { }
    private static final List<Callback> CALLBACKS=List.of(
        new Callback("start",Level.INFO,"[orchestration] started name={} correlationId={} pattern={}",false,
            (e,s)->e.onStart(s.name(),s.correlation(),s.pattern()),s->new Object[]{s.name(),s.correlation(),s.pattern()},
            "[orchestration] started name=checkout correlationId=corr-1 pattern=SAGA"),
        new Callback("step started",Level.INFO,"[orchestration] step.started name={} correlationId={} stepId={}",false,
            (e,s)->e.onStepStarted(s.name(),s.correlation(),s.step()),s->new Object[]{s.name(),s.correlation(),s.step()},
            "[orchestration] step.started name=checkout correlationId=corr-1 stepId=step-1"),
        new Callback("step success",Level.INFO,"[orchestration] step.success name={} correlationId={} stepId={} attempts={} latencyMs={}",false,
            (e,s)->e.onStepSuccess(s.name(),s.correlation(),s.step(),2,17L),s->new Object[]{s.name(),s.correlation(),s.step(),2,17L},
            "[orchestration] step.success name=checkout correlationId=corr-1 stepId=step-1 attempts=2 latencyMs=17"),
        new Callback("step failed",Level.WARN,"[orchestration] step.failed name={} correlationId={} stepId={} attempts={} error={}",true,
            (e,s)->e.onStepFailed(s.name(),s.correlation(),s.step(),s.failure(),2),s->new Object[]{s.name(),s.correlation(),s.step(),2,s.message()},
            "[orchestration] step.failed name=checkout correlationId=corr-1 stepId=step-1 attempts=2 error=business refusal"),
        new Callback("completed",Level.INFO,"[orchestration] completed name={} correlationId={} pattern={} success={} durationMs={}",false,
            (e,s)->e.onCompleted(s.name(),s.correlation(),s.pattern(),true,47L),s->new Object[]{s.name(),s.correlation(),s.pattern(),true,47L},
            "[orchestration] completed name=checkout correlationId=corr-1 pattern=SAGA success=true durationMs=47"),
        new Callback("compensation started",Level.WARN,"[orchestration] compensation.started name={} correlationId={}",false,
            (e,s)->e.onCompensationStarted(s.name(),s.correlation()),s->new Object[]{s.name(),s.correlation()},
            "[orchestration] compensation.started name=checkout correlationId=corr-1"),
        new Callback("step compensated",Level.INFO,"[orchestration] step.compensated name={} correlationId={} stepId={}",false,
            (e,s)->e.onStepCompensated(s.name(),s.correlation(),s.step()),s->new Object[]{s.name(),s.correlation(),s.step()},
            "[orchestration] step.compensated name=checkout correlationId=corr-1 stepId=step-1"),
        new Callback("compensation failed",Level.ERROR,"[orchestration] step.compensation.failed name={} correlationId={} stepId={} error={}",true,
            (e,s)->e.onStepCompensationFailed(s.name(),s.correlation(),s.step(),s.failure()),s->new Object[]{s.name(),s.correlation(),s.step(),s.message()},
            "[orchestration] step.compensation.failed name=checkout correlationId=corr-1 stepId=step-1 error=business refusal"),
        new Callback("phase started",Level.INFO,"[orchestration] tcc.phase.started name={} correlationId={} phase={}",false,
            (e,s)->e.onPhaseStarted(s.name(),s.correlation(),s.phase()),s->new Object[]{s.name(),s.correlation(),s.phase()},
            "[orchestration] tcc.phase.started name=checkout correlationId=corr-1 phase=CONFIRM"),
        new Callback("phase completed",Level.INFO,"[orchestration] tcc.phase.completed name={} correlationId={} phase={} durationMs={}",false,
            (e,s)->e.onPhaseCompleted(s.name(),s.correlation(),s.phase(),47L),s->new Object[]{s.name(),s.correlation(),s.phase(),47L},
            "[orchestration] tcc.phase.completed name=checkout correlationId=corr-1 phase=CONFIRM durationMs=47"),
        new Callback("phase failed",Level.ERROR,"[orchestration] tcc.phase.failed name={} correlationId={} phase={} error={}",true,
            (e,s)->e.onPhaseFailed(s.name(),s.correlation(),s.phase(),s.failure()),s->new Object[]{s.name(),s.correlation(),s.phase(),s.message()},
            "[orchestration] tcc.phase.failed name=checkout correlationId=corr-1 phase=CONFIRM error=business refusal"),
        new Callback("dead lettered",Level.ERROR,"[orchestration] dead-lettered name={} correlationId={} stepId={} error={}",true,
            (e,s)->e.onDeadLettered(s.name(),s.correlation(),s.step(),s.failure()),s->new Object[]{s.name(),s.correlation(),s.step(),s.message()},
            "[orchestration] dead-lettered name=checkout correlationId=corr-1 stepId=step-1 error=business refusal")
    );
    @BeforeEach void captureEvents() {
        logger=(Logger)LoggerFactory.getLogger(OrchestrationLoggerEventsImpl.class);
        previousLevel=logger.getLevel();previousAdditive=logger.isAdditive();logger.setLevel(Level.TRACE);logger.setAdditive(false);
        capture=new ListAppender<>();capture.setContext(logger.getLoggerContext());capture.start();logger.addAppender(capture);
    }
    @AfterEach void restoreLogger() {
        logger.detachAppender(capture);capture.stop();logger.setLevel(previousLevel);logger.setAdditive(previousAdditive);
    }
    private ILoggingEvent emit(Callback callback,Sample actual,Sample expected) {
        capture.list.clear();callback.invoke().accept(events,actual);assertEquals(1,capture.list.size());var event=capture.list.getFirst();
        assertEquals(callback.level(),event.getLevel());assertEquals(callback.template(),event.getMessage());
        assertArrayEquals(callback.arguments().apply(expected),event.getArgumentArray());assertNull(event.getThrowableProxy());
        if(actual.failure()!=null)assertEquals(callback.usesFailure()?1:0,actual.failure().messageReads);
        return event;
    }
    @TestFactory Stream<DynamicTest> eachCallbackEscapesLineSeparators() {
        return CALLBACKS.stream().map(c->DynamicTest.dynamicTest(c.name(),()->{
            var event=emit(c,Sample.text(BREAKS),Sample.text(VISIBLE));String formatted=event.getFormattedMessage();
            for(char boundary:BREAKS.toCharArray())assertFalse(formatted.indexOf(boundary)>=0);
            assertTrue(formatted.contains("name"+VISIBLE));assertTrue(formatted.contains("correlation"+VISIBLE));
        }));
    }
    @TestFactory Stream<DynamicTest> eachCallbackPreservesOrdinaryTextAndTypedArguments() {
        return CALLBACKS.stream().map(c->DynamicTest.dynamicTest(c.name(),()->
            assertEquals(c.ordinaryMessage(),emit(c,Sample.ordinary(),Sample.ordinary()).getFormattedMessage())));
    }
    @TestFactory Stream<DynamicTest> eachCallbackPreservesNullFields() {
        return CALLBACKS.stream().map(c->DynamicTest.dynamicTest(c.name(),()->{
            var s=new Sample(null,null,null,null,null,"business refusal",new Failure("business refusal"));
            assertTrue(emit(c,s,s).getFormattedMessage().contains("name=null correlationId=null"));
        }));
    }
    @TestFactory Stream<DynamicTest> failureCallbacksPreserveNullMessages() {
        return CALLBACKS.stream().filter(Callback::usesFailure).map(c->DynamicTest.dynamicTest(c.name(),()->{
            var s=new Sample("checkout","corr-1","step-1",ExecutionPattern.SAGA,TccPhase.CONFIRM,null,new Failure(null));
            assertTrue(emit(c,s,s).getFormattedMessage().endsWith("error=null"));
        }));
    }
    @TestFactory Stream<DynamicTest> failureCallbacksPreserveNullThrowableFailure() {
        return CALLBACKS.stream().filter(Callback::usesFailure).map(c->DynamicTest.dynamicTest(c.name(),()->{
            capture.list.clear();var s=new Sample("checkout","corr-1","step-1",ExecutionPattern.SAGA,TccPhase.CONFIRM,null,null);
            assertThrows(NullPointerException.class,()->c.invoke().accept(events,s));assertTrue(capture.list.isEmpty());
        }));
    }
}
