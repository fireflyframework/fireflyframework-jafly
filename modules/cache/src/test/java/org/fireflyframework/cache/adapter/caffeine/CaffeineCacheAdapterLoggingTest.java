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
package org.fireflyframework.cache.adapter.caffeine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaffeineCacheAdapterLoggingTest {
    private static final String NAME = "cache\r\nregion\u000bvertical\fform\u0085next\u2028line\u2029paragraph";
    private final Logger logger = (Logger) LoggerFactory.getLogger(CaffeineCacheAdapterImpl.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;
    private boolean previousAdditive;

    @BeforeEach void capture() {
        previousLevel = logger.getLevel(); previousAdditive = logger.isAdditive();
        logger.setLevel(Level.DEBUG); logger.setAdditive(false); events.start(); logger.addAppender(events);
    }
    @AfterEach void release() {
        logger.detachAppender(events); events.stop(); logger.setLevel(previousLevel); logger.setAdditive(previousAdditive);
    }
    private void safeEvent(Level expected, boolean error) {
        assertFalse(events.list.isEmpty());
        var event = events.list.getLast(); String message = event.getFormattedMessage();
        assertEquals(expected, event.getLevel()); assertNull(event.getThrowableProxy());
        assertFalse(message.contains("PRIVATE_"), message);
        assertFalse(message.matches("(?s).*[\r\n\u000b\f\u0085\u2028\u2029].*"), message);
        if (error) assertTrue(message.contains("type=java.lang.IllegalStateException"), message);
    }
    private static final class Key {
        int renders;
        @Override public String toString() { renders++; return "PRIVATE_KEY\r\nforged\u2028entry"; }
    }

    private CaffeineCacheAdapterImpl adapter() { return new CaffeineCacheAdapterImpl(NAME, CaffeineCacheConfig.builder().maximumSize(10L).build()); }
    @Test void successfulOperationsLogKeyPresenceAndEscapeOnlyTheCacheName() {
        var cache = adapter(); var key = new Key(); cache.put(key, "value").block();
        safeEvent(Level.DEBUG, false); assertEquals(1, key.renders);
        assertTrue(events.list.getLast().getFormattedMessage().contains("cache\\r\\nregion"));
        assertEquals(Optional.of("value"), cache.get(key).block());
        cache.close(); safeEvent(Level.INFO, false);
    }
    @Test void keyFailureKeepsEmptyReadAndOneBusinessRenderingWithoutErrorPayload() {
        var renders = new java.util.concurrent.atomic.AtomicInteger();
        Object key = new Object() { @Override public String toString() { if (renders.incrementAndGet() == 1) throw new IllegalStateException("PRIVATE_MESSAGE\r\nforged"); return "PRIVATE_KEY"; } };
        assertEquals(Optional.empty(), adapter().get(key).block()); safeEvent(Level.WARN, true); assertEquals(1, renders.get());
    }
    @Test void putFailurePreservesOriginalWrappedCauseWithoutLoggingIt() {
        var failure = new IllegalStateException("PRIVATE_MESSAGE\u2028forged");
        Object key = new Object() { @Override public String toString() { throw failure; } };
        var error = assertThrows(RuntimeException.class, () -> adapter().put(key, "value").block());
        assertSame(failure, error.getCause()); safeEvent(Level.ERROR, true);
    }
    @Test void disabledLoggingAddsNoGenericKeyRendering() {
        logger.setLevel(Level.OFF); var key = new Key(); adapter().put(key, "value").block();
        assertEquals(1, key.renders); assertTrue(events.list.isEmpty());
    }
}
