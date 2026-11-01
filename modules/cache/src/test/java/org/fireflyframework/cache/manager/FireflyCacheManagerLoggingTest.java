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
package org.fireflyframework.cache.manager;

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

class FireflyCacheManagerLoggingTest {
    private static final String NAME = "cache\r\nregion\u000bvertical\fform\u0085next\u2028line\u2029paragraph";
    private final Logger logger = (Logger) LoggerFactory.getLogger(FireflyCacheManagerImpl.class);
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

    private org.fireflyframework.cache.core.CacheAdapter primary() {
        var cache = mock(org.fireflyframework.cache.core.CacheAdapter.class);
        when(cache.isAvailable()).thenReturn(true); return cache;
    }
    @Test void genericGetErrorsKeepEmptyFallbackWithoutRenderingKeysOrPayload() {
        var primary = primary(); var key = new Key();
        when(primary.get(key)).thenReturn(Mono.error(new IllegalStateException("PRIVATE_MESSAGE\r\nforged")));
        assertEquals(Optional.empty(), new FireflyCacheManagerImpl(primary).get(key).block());
        safeEvent(Level.ERROR, true); assertEquals(0, key.renders);
    }
    @Test void typedGetAndOtherFallbacksRetainResultsAndErrorLevels() {
        var primary = primary(); var key = new Key(); var failure = new IllegalStateException("PRIVATE_MESSAGE\u2028forged");
        when(primary.get(key, String.class)).thenReturn(Mono.error(failure));
        when(primary.put(key, "value")).thenReturn(Mono.error(failure));
        when(primary.evict(key)).thenReturn(Mono.error(failure));
        when(primary.exists(key)).thenReturn(Mono.error(failure));
        when(primary.clear()).thenReturn(Mono.error(failure));
        when(primary.keys()).thenReturn(Mono.error(failure));
        when(primary.size()).thenReturn(Mono.error(failure));
        var manager = new FireflyCacheManagerImpl(primary);
        assertEquals(Optional.empty(), manager.get(key, String.class).block()); safeEvent(Level.ERROR, true);
        assertNull(manager.put(key, "value").block()); safeEvent(Level.ERROR, true);
        assertEquals(false, manager.evict(key).block()); safeEvent(Level.ERROR, true);
        assertEquals(false, manager.exists(key).block()); safeEvent(Level.ERROR, true);
        assertNull(manager.clear().block()); safeEvent(Level.ERROR, true);
        assertEquals(java.util.Set.of(), manager.keys().block()); safeEvent(Level.ERROR, true);
        assertEquals(0L, manager.size().block()); safeEvent(Level.ERROR, true);
        assertEquals(0, key.renders);
    }
    @Test void disabledLogsDoNotReadErrorPayloadOrRenderTheGenericKey() {
        logger.setLevel(Level.OFF); var primary = primary(); var key = new Key();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var error = new IllegalStateException() { @Override public String getMessage() { reads.incrementAndGet(); return "PRIVATE_MESSAGE"; } };
        when(primary.get(key)).thenReturn(Mono.error(error));
        assertEquals(Optional.empty(), new FireflyCacheManagerImpl(primary).get(key).block());
        assertEquals(0, key.renders); assertEquals(0, reads.get()); assertTrue(events.list.isEmpty());
    }
}
