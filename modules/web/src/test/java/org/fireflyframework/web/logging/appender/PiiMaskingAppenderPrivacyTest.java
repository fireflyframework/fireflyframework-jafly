/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.web.logging.appender;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.logstash.logback.encoder.LogstashEncoder;
import org.fireflyframework.web.logging.config.PiiMaskingProperties;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.logback.StructuredLogEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class PiiMaskingAppenderPrivacyTest {
    private static final String PRIVATE = "RAW_PRIVATE_SENTINEL_9EA1";

    @ParameterizedTest
    @MethodSource("formats")
    void completeEncodedEventHasNoUnmaskedChannel(String format) throws Exception {
        try (var sink = new Sink(format)) {
            var service = service(properties());
            var appender = sink.masker(service);
            var captured = new ch.qos.logback.core.read.ListAppender<ILoggingEvent>();
            captured.start();
            appender.addAppender(captured);
            appender.doAppend(event(sink.context, "template " + PRIVATE + " {}"));
            assertThat(sink.text()).isNotBlank().doesNotContain(PRIVATE).contains("template");
            var encoded = new ObjectMapper().readTree(sink.text());
            assertThat(encoded.get("count").isInt()).isTrue();
            assertThat(encoded.get("count").asInt()).isEqualTo(7);
            assertThat(captured.list).hasSize(1);
            var snapshot = captured.list.getFirst();
            assertThat(snapshot.getMessage()).doesNotContain(PRIVATE);
            assertThat(snapshot.getArgumentArray()).isNull();
            assertThat(snapshot.getMDCPropertyMap().toString()).doesNotContain(PRIVATE);
            assertThat(snapshot.getLoggerContextVO().getPropertyMap().toString()).doesNotContain(PRIVATE);
            assertThat(sink.context.getProperty("context-key-" + PRIVATE)).isEqualTo("context-value-" + PRIVATE);
            captured.stop();
            assertThat(sink.status()).doesNotContain(PRIVATE);
        }
    }

    @ParameterizedTest
    @MethodSource("failureCases")
    void unavailableOrFaultingMaskerNeverForwardsTheOriginalOrLeaksItsException(String format, String fault) {
        try (var sink = new Sink(format)) {
            PiiMaskingService service = fault.equals("missing") ? null : mock(PiiMaskingService.class, invocation -> {
                String method = invocation.getMethod().getName();
                if (method.equals("isReady")) return !List.of("unready", "null-stats", "stats-throws").contains(fault);
                if (method.equals("getMaskingStats")) {
                    if (fault.equals("null-stats")) return null;
                    if (fault.equals("stats-throws")) throw new IllegalStateException(PRIVATE);
                    if (fault.equals("ambiguous-disabled")) return Map.of("enabled", "false");
                    return Map.of("enabled", true);
                }
                if (method.startsWith("mask")) {
                    if (fault.equals("throwing")) throw new IllegalStateException(PRIVATE);
                    if (fault.equals("ambiguous-disabled")) return invocation.getArgument(0);
                    return null;
                }
                return null;
            });
            sink.masker(service).doAppend(event(sink.context, "private " + PRIVATE));
            assertThat(sink.text()).isEmpty();
            assertThat(sink.status()).doesNotContain(PRIVATE);
            assertThat(sink.context.getStatusManager().getCopyOfStatusList())
                    .allSatisfy(status -> assertThat(status.getThrowable()).isNull());
        }
    }

    @ParameterizedTest
    @MethodSource("internalFaultCases")
    void genuineInternalMaskingFailureAndPartialRuleInitializationFailClosed(String format, String fault) {
        try (var sink = new Sink(format)) {
            var properties = properties();
            if (fault.equals("internal")) properties.setMaskCharacter(null);
            else properties.getCustomPatterns().put("private-rule-" + PRIVATE, "[" + PRIVATE);
            var service = service(properties);
            assertThat(service.isReady()).isTrue();
            sink.masker(service).doAppend(event(sink.context, PRIVATE));
            assertThat(sink.text()).isEmpty();
            assertThat(sink.status()).doesNotContain(PRIVATE);
        }
    }

    @ParameterizedTest
    @MethodSource("messageCases")
    void unchangedEmptyAndNullMessagesDoNotBypassOtherEventChannels(String format, String message) {
        try (var sink = new Sink(format)) {
            sink.masker(service(properties())).doAppend(event(sink.context, message));
            assertThat(sink.text()).isNotBlank().doesNotContain(PRIVATE);
            assertThat(sink.status()).doesNotContain(PRIVATE);
        }
    }

    @ParameterizedTest
    @MethodSource("formats")
    void unsafeStructuredObjectsAreOmittedWithoutCallingTheirToString(String format) {
        try (var sink = new Sink(format)) {
            var called = new AtomicBoolean();
            var event = event(sink.context, "ordinary");
            event.addKeyValuePair(new KeyValuePair("unsafe", new Object() {
                @Override public String toString() {
                    called.set(true);
                    throw new IllegalStateException(PRIVATE);
                }
            }));
            sink.masker(service(properties())).doAppend(event);
            assertThat(called).isFalse();
            assertThat(sink.text()).isEmpty();
            assertThat(sink.status()).doesNotContain(PRIVATE);
        }
    }

    @ParameterizedTest
    @MethodSource("formats")
    void unsafeSourceAccessCannotLeakThroughStatusOrChildren(String format) {
        try (var sink = new Sink(format)) {
            var event = spy(event(sink.context, PRIVATE));
            doThrow(new IllegalStateException(PRIVATE)).when(event).getMDCPropertyMap();
            sink.masker(service(properties())).doAppend(event);
            assertThat(sink.text()).isEmpty();
            assertThat(sink.status()).doesNotContain(PRIVATE);
            assertThat(sink.context.getStatusManager().getCopyOfStatusList())
                    .allSatisfy(status -> assertThat(status.getThrowable()).isNull());
        }
    }

    @ParameterizedTest
    @MethodSource("formats")
    void explicitDisabledMaskingRemainsAnUnprotectedOptOut(String format) {
        try (var sink = new Sink(format)) {
            var properties = properties();
            properties.setEnabled(false);
            sink.masker(service(properties)).doAppend(event(sink.context, PRIVATE));
            assertThat(sink.text()).contains(PRIVATE);
        }
    }

    @ParameterizedTest
    @MethodSource("formats")
    void snapshotIsDetachedBeforeARealAsyncAppenderDefersEncoding(String format) throws Exception {
        try (var sink = new Sink(format)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var encoded = new CountDownLatch(1);
            var blocker = new AppenderBase<ILoggingEvent>() {
                @Override protected void append(ILoggingEvent event) {
                    entered.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Test sink timed out");
                        sink.output.doAppend(event);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Test sink interrupted");
                    } finally {
                        encoded.countDown();
                    }
                }
            };
            blocker.setContext(sink.context);
            blocker.start();
            var async = new AsyncAppender();
            async.setContext(sink.context);
            async.setQueueSize(4);
            async.setNeverBlock(true);
            async.setMaxFlushTime(1000);
            async.addAppender(blocker);
            async.start();
            var appender = sink.masker(service(properties()));
            appender.detachAppender(sink.output);
            appender.addAppender(async);
            var event = event(sink.context, "before {} " + PRIVATE);
            var mdc = event.getMDCPropertyMap();
            mdc.put("phase", "BEFORE_PHASE");
            var pairs = event.getKeyValuePairs();
            pairs.add(new KeyValuePair("phaseKV", "BEFORE_PHASE"));
            try {
                appender.doAppend(event);
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                mdc.put("phase", "AFTER_PHASE");
                pairs.add(new KeyValuePair("newKV", "AFTER_PHASE"));
                event.getMarkerList().getFirst().add(MarkerFactory.getDetachedMarker("AFTER_PHASE"));
                event.getArgumentArray()[0] = "AFTER_PHASE";
                release.countDown();
                assertThat(encoded.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(sink.text()).contains("BEFORE_PHASE").doesNotContain("AFTER_PHASE", PRIVATE);
            } finally {
                release.countDown();
                async.stop();
                blocker.stop();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("formats")
    void maskerInitializationDiagnosticsNeverSerializeConfiguredRulesOrProviderErrors(String format) {
        Logger logger = (Logger) LoggerFactory.getLogger(PiiMaskingService.class);
        Level prior = logger.getLevel();
        boolean additive = logger.isAdditive();
        try (var sink = new Sink(format)) {
            logger.setLevel(Level.DEBUG);
            logger.setAdditive(false);
            logger.addAppender(sink.output);
            var properties = properties();
            properties.getCustomPatterns().put("private-name-" + PRIVATE, "[" + PRIVATE);
            service(properties);
            assertThat(sink.text()).isNotBlank().doesNotContain(PRIVATE);
            assertThat(sink.status()).doesNotContain(PRIVATE);
        } finally {
            logger.detachAppender("privacy-byte-sink");
            logger.setLevel(prior);
            logger.setAdditive(additive);
        }
    }

    @Test
    void strictLoggingPathDoesNotRecursivelyLogPatternNames() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PiiMaskingService.class);
        var captured = new ch.qos.logback.core.read.ListAppender<ILoggingEvent>();
        Level prior = logger.getLevel();
        boolean additive = logger.isAdditive();
        captured.start();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        logger.addAppender(captured);
        try {
            var properties = properties();
            properties.setPatterns(Map.of("private-name-" + PRIVATE, java.util.regex.Pattern.quote(PRIVATE)));
            var service = service(properties);
            captured.list.clear();
            var method = PiiMaskingService.class.getMethod("maskLogData", String.class);
            assertThat(method.invoke(service, PRIVATE)).isNotEqualTo(PRIVATE);
            assertThat(captured.list).isEmpty();
        } finally {
            logger.detachAppender(captured);
            logger.setLevel(prior);
            logger.setAdditive(additive);
            captured.stop();
        }
    }

    @Test
    void explicitLegacyGlobalContextExportIsOutsideTheEventMaskingBoundary() {
        try (var sink = new Sink("legacy", true)) {
            sink.masker(service(properties())).doAppend(event(sink.context, "ordinary"));
            assertThat(sink.text()).contains("context-value-" + PRIVATE);
            assertThat(sink.context.getProperty("context-key-" + PRIVATE)).isEqualTo("context-value-" + PRIVATE);
        }
    }

    @Test
    void legacyNonLoggingMaskResultsRemainCompatible() throws Exception {
        var disabled = properties();
        disabled.setEnabled(false);
        assertThat(service(disabled).maskPiiData(PRIVATE)).isEqualTo(PRIVATE);
        assertThat(service(disabled).maskPiiData(null)).isNull();
        var invalidValue = properties();
        invalidValue.setMaskCharacter(null);
        assertThat(service(invalidValue).maskPiiData(PRIVATE)).isEqualTo(PRIVATE);
        var partial = properties();
        partial.getCustomPatterns().put("invalid", "[");
        var partialService = service(partial);
        assertThat(partialService.isReady()).isTrue();
        assertThat(partialService.maskPiiData(PRIVATE)).isNotEqualTo(PRIVATE);
        var strict = PiiMaskingService.class.getMethod("maskLogData", String.class);
        try {
            strict.invoke(partialService, PRIVATE);
            throw new AssertionError("Strict logging masking must refuse an incomplete rule set");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class)
                    .hasMessage("PII logging mask is unavailable").hasNoCause();
        }
    }

    static Stream<String> formats() { return Stream.of("ecs", "legacy"); }
    static Stream<Arguments> failureCases() {
        return formats().flatMap(format -> Stream.of("missing", "unready", "throwing", "null-result", "null-stats", "stats-throws", "ambiguous-disabled")
                .map(fault -> Arguments.of(format, fault)));
    }
    static Stream<Arguments> internalFaultCases() {
        return formats().flatMap(format -> Stream.of("internal", "partial-rule").map(fault -> Arguments.of(format, fault)));
    }
    static Stream<Arguments> messageCases() {
        return formats().flatMap(format -> Stream.of("ordinary", "", null).map(message -> Arguments.of(format, message)));
    }

    private PiiMaskingProperties properties() {
        var result = new PiiMaskingProperties();
        result.setPatterns(Map.of("qualification", java.util.regex.Pattern.quote(PRIVATE)));
        result.setCustomPatterns(new LinkedHashMap<>());
        return result;
    }
    private PiiMaskingService service(PiiMaskingProperties properties) { return new PiiMaskingService(properties); }

    private LoggingEvent event(LoggerContext context, String message) {
        var failure = new IllegalStateException("throwable " + PRIVATE, new RuntimeException("cause " + PRIVATE));
        failure.addSuppressed(new IllegalArgumentException("suppressed " + PRIVATE));
        failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("class" + PRIVATE, "method" + PRIVATE, "file" + PRIVATE, 7)});
        var event = new LoggingEvent(getClass().getName(), context.getLogger("logger-" + PRIVATE), Level.INFO,
                message, failure, new Object[] {"argument " + PRIVATE});
        event.setThreadName("thread-" + PRIVATE);
        event.setMDCPropertyMap(new LinkedHashMap<>(Map.of("mdc-" + PRIVATE, "value " + PRIVATE)));
        event.setKeyValuePairs(new ArrayList<>(List.of(new KeyValuePair("kv-" + PRIVATE, "value " + PRIVATE), new KeyValuePair("count", 7))));
        Marker marker = MarkerFactory.getDetachedMarker("marker-" + PRIVATE);
        marker.add(MarkerFactory.getDetachedMarker("nested-" + PRIVATE));
        event.addMarker(marker);
        event.setCallerData(new StackTraceElement[] {new StackTraceElement("caller" + PRIVATE, "method" + PRIVATE, "file" + PRIVATE, 9)});
        return event;
    }

    private static final class Sink implements AutoCloseable {
        private final LoggerContext context = new LoggerContext();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final OutputStreamAppender<ILoggingEvent> output = new OutputStreamAppender<>();
        private AsyncAppender sharedAsync;
        private Sink(String format) { this(format, false); }
        private Sink(String format, boolean globalContextDiagnostic) {
            context.setName("context-" + PRIVATE);
            context.putProperty("context-key-" + PRIVATE, "context-value-" + PRIVATE);
            context.putObject(org.springframework.core.env.Environment.class.getName(),
                    new org.springframework.mock.env.MockEnvironment());
            Encoder<ILoggingEvent> encoder;
            if (format.equals("ecs")) {
                var value = new StructuredLogEncoder();
                value.setFormat("ecs");
                value.setCharset(StandardCharsets.UTF_8);
                encoder = value;
            } else {
                try {
                    var configurator = new JoranConfigurator();
                    configurator.setContext(context);
                    configurator.doConfigure(new ByteArrayInputStream(("<configuration>"
                            + "<include resource=\"logback-firefly.xml\"/>"
                            + "<root level=\"INFO\"><appender-ref ref=\"FIREFLY_ASYNC\"/></root>"
                            + "</configuration>").getBytes(StandardCharsets.UTF_8)));
                    sharedAsync = (AsyncAppender) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FIREFLY_ASYNC");
                    assertThat(sharedAsync).isNotNull();
                } catch (Exception failure) {
                    throw new AssertionError("Actual shared logging configuration failed", failure);
                }
                var configured = (OutputStreamAppender<ILoggingEvent>) sharedAsync.getAppender("FIREFLY_JSON");
                var value = (LogstashEncoder) configured.getEncoder();
                assertThat(value.isIncludeContext()).isFalse();
                value.stop();
                if (globalContextDiagnostic) value.setIncludeContext(true);
                value.setIncludeCallerData(true);
                value.setIncludeNonStructuredArguments(true);
                encoder = value;
            }
            encoder.setContext(context);
            encoder.start();
            assertThat(encoder.isStarted()).isTrue();
            output.setName("privacy-byte-sink");
            output.setContext(context);
            output.setEncoder(encoder);
            output.setOutputStream(bytes);
            output.start();
            assertThat(output.isStarted()).isTrue();
        }
        private PiiMaskingAppender masker(PiiMaskingService service) {
            var appender = new PiiMaskingAppender();
            appender.setContext(context);
            appender.setPiiMaskingService(service);
            appender.addAppender(output);
            appender.start();
            return appender;
        }
        private String text() { return bytes.toString(StandardCharsets.UTF_8); }
        private String status() {
            return context.getStatusManager().getCopyOfStatusList().stream()
                    .map(value -> value.getMessage() + (value.getThrowable() == null ? "" : value.getThrowable().toString()))
                    .reduce("", String::concat);
        }
        @Override public void close() {
            output.stop();
            if (sharedAsync != null) sharedAsync.stop();
            context.stop();
        }
    }
}
