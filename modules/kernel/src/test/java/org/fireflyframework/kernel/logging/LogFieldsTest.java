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
package org.fireflyframework.kernel.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LogFieldsTest {
    static Stream<Arguments> lineBreaks() {
        return Stream.of(
                Arguments.of("\r", "\\r"), Arguments.of("\n", "\\n"),
                Arguments.of("\u000b", "\\u000b"), Arguments.of("\f", "\\f"),
                Arguments.of("\u0085", "\\u0085"), Arguments.of("\u2028", "\\u2028"),
                Arguments.of("\u2029", "\\u2029"));
    }

    @ParameterizedTest
    @MethodSource("lineBreaks")
    void lineBreaksRemainVisibleInsideOneField(String lineBreak, String expected) {
        assertEquals("before" + expected + "after", LogFields.text("before" + lineBreak + "after"));
    }

    @Test
    void ordinaryTextKeepsIdentityUnicodeWhitespaceAndLiteralEscapeSequences() {
        for (String value : List.of("alice@example.invalid", "tenant-1/user-2", "李 é ", "a\tb", "literal\\n")) {
            assertSame(value, LogFields.text(value));
        }
    }

    @Test
    void nullAndEmptyTextStayDistinct() {
        assertNull(LogFields.text(null));
        assertEquals("", LogFields.text(""));
    }

    @Test
    void consecutiveLineBreaksAreEscapedWithoutTrimmingOrCollapsing() {
        assertEquals(" \\r\\n\\n ", LogFields.text(" \r\n\n "));
    }

    @Test
    void roleListsPreserveOrdinaryOrderNullElementsAndEmptyLists() {
        assertNull(LogFields.values(null));
        assertEquals("[]", LogFields.values(List.of()));
        assertEquals("[reader, null, maker]", LogFields.values(Arrays.asList("reader", null, "maker")));
    }

    @Test
    void unavailableRoleIterationIsOpaqueAndCannotInterruptTheCaller() {
        String privatePayload = "synthetic private collection payload";
        for (boolean failBeforeFirstElement : List.of(true, false)) {
            var values = new java.util.AbstractCollection<String>() {
                @Override
                public java.util.Iterator<String> iterator() {
                    if (failBeforeFirstElement) throw new IllegalStateException(privatePayload);
                    return new java.util.Iterator<>() {
                        private boolean visited;
                        @Override
                        public boolean hasNext() {
                            if (visited) throw new IllegalStateException(privatePayload);
                            return true;
                        }
                        @Override
                        public String next() {
                            visited = true;
                            return "partial role";
                        }
                    };
                }
                @Override
                public int size() { return 1; }
                @Override
                public String toString() { throw new AssertionError("Custom rendering is forbidden"); }
            };
            String result = assertDoesNotThrow(() -> LogFields.values(values));
            assertEquals("[unavailable]", result);
            assertFalse(result.contains(privatePayload));
            assertFalse(result.contains("partial role"));
        }
        var vmFailure = new java.util.AbstractCollection<String>() {
            @Override
            public java.util.Iterator<String> iterator() { throw new InternalError("synthetic VM failure"); }
            @Override
            public int size() { return 1; }
        };
        assertThrows(InternalError.class, () -> LogFields.values(vmFailure));
    }

    @Test
    void roleNamesUseTheSameVisibleLineBreakEscapes() {
        assertEquals("[reader\\nrole, maker\\u2028role]", LogFields.values(List.of("reader\nrole", "maker\u2028role")));
    }

    @Test
    void errorTypeExposesOnlyTheStableFullClassName() {
        var error = new IllegalArgumentException("synthetic credential response", new RuntimeException("private cause"));
        assertEquals("java.lang.IllegalArgumentException", LogFields.errorType(error));
        assertNull(LogFields.errorType(null));
        assertEquals("synthetic credential response", error.getMessage());
        assertEquals("private cause", error.getCause().getMessage());
    }

    @Test
    void actualSlf4jEventKeepsLevelAuditAndMdcWithoutRawErrorPayload() {
        Logger logger = (Logger) LoggerFactory.getLogger("LogFieldsTest.owned");
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        logger.addAppender(appender);
        try {
            MDC.put("correlationId", "correlation-42");
            var error = new IllegalStateException("synthetic private provider response");
            logger.warn("Provider failure user={} roles={} errorType={}",
                    LogFields.text("user\nname"), LogFields.values(List.of("reader\rrole")), LogFields.errorType(error));
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(Level.WARN, event.getLevel());
            assertEquals("Provider failure user=user\\nname roles=[reader\\rrole] errorType=java.lang.IllegalStateException", event.getFormattedMessage());
            assertEquals("correlation-42", event.getMDCPropertyMap().get("correlationId"));
            assertNull(event.getThrowableProxy());
            assertFalse(event.getFormattedMessage().contains("private provider response"));
            assertEquals("correlation-42", MDC.get("correlationId"));
            var auditId = java.util.UUID.fromString("7ddce240-8657-4f4d-a63d-f1778f739463");
            logger.info("Audit id={} attempts={} user={}", auditId, 3, LogFields.text(null));
            ILoggingEvent audit = appender.list.get(1);
            assertSame(auditId, audit.getArgumentArray()[0]);
            assertEquals(3, audit.getArgumentArray()[1]);
            assertNull(audit.getArgumentArray()[2]);
            assertEquals("Audit id=" + auditId + " attempts=3 user=null", audit.getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
            if (previousContext == null) MDC.clear();
            else MDC.setContextMap(previousContext);
        }
    }
}
