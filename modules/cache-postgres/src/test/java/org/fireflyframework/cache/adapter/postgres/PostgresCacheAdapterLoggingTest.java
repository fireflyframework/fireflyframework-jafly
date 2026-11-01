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
package org.fireflyframework.cache.adapter.postgres;

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

class PostgresCacheAdapterLoggingTest {
    private static final String NAME = "cache\r\nregion\u000bvertical\fform\u0085next\u2028line\u2029paragraph";
    private final Logger logger = (Logger) LoggerFactory.getLogger(PostgresCacheAdapterImpl.class);
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

    @Test void readEvictAndExistsErrorsKeepTheirFallbacksAndTypeOnlyWarnings() {
        var factory = mock(io.r2dbc.spi.ConnectionFactory.class); var failure = new IllegalStateException("PRIVATE_MESSAGE\r\nforged");
        when(factory.create()).thenAnswer(invocation -> Mono.error(failure));
        var adapter = new PostgresCacheAdapterImpl(NAME, factory, PostgresCacheConfig.builder().keyPrefix("synthetic-service").build(), mock(org.fireflyframework.cache.serialization.CacheSerializer.class));
        var key = new Key(); assertEquals(Optional.empty(), adapter.get(key).block()); safeEvent(Level.WARN, true); assertEquals(1,key.renders);
        assertEquals(false, adapter.evict(key).block()); safeEvent(Level.WARN, true); assertEquals(2,key.renders);
        assertEquals(false, adapter.exists(key).block()); safeEvent(Level.WARN, true); assertEquals(3,key.renders);
        adapter.close(); safeEvent(Level.INFO, false);
    }
    @Test @SuppressWarnings({"rawtypes","unchecked"}) void deserializerFailureRetainsEmptyReadWithoutRenderingKeyAgain() {
        var factory = mock(io.r2dbc.spi.ConnectionFactory.class); var connection = mock(io.r2dbc.spi.Connection.class);
        var statement = mock(io.r2dbc.spi.Statement.class, RETURNS_SELF); var result = mock(io.r2dbc.spi.Result.class); var row = mock(io.r2dbc.spi.Row.class);
        when(factory.create()).thenAnswer(invocation -> Mono.just(connection)); when(connection.close()).thenReturn(Mono.empty());
        when(connection.createStatement(anyString())).thenReturn(statement); when(statement.execute()).thenAnswer(invocation -> reactor.core.publisher.Flux.just(result));
        when(row.get("value")).thenReturn("serialized".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(result.map(any(java.util.function.BiFunction.class))).thenAnswer(invocation -> reactor.core.publisher.Flux.just(((java.util.function.BiFunction)invocation.getArgument(0)).apply(row,mock(io.r2dbc.spi.RowMetadata.class))));
        var serializer = mock(org.fireflyframework.cache.serialization.CacheSerializer.class);
        when(serializer.deserialize(any(),any())).thenThrow(new IllegalStateException("PRIVATE_MESSAGE\r\nforged"));
        var adapter = new PostgresCacheAdapterImpl(NAME,factory,PostgresCacheConfig.builder().keyPrefix("synthetic-service").build(),serializer); var key = new Key();
        assertEquals(Optional.empty(),adapter.get(key,String.class).block()); safeEvent(Level.WARN,true); assertEquals(1,key.renders);
        verify(connection).close();
    }
}
