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
package org.fireflyframework.cache.adapter.redis;

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

class RedisCacheAdapterLoggingTest {
    private static final String NAME = "cache\r\nregion\u000bvertical\fform\u0085next\u2028line\u2029paragraph";
    private final Logger logger = (Logger) LoggerFactory.getLogger(RedisCacheAdapterImpl.class);
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

    private record Fixture(RedisCacheAdapterImpl adapter, org.springframework.data.redis.core.ReactiveValueOperations<String,Object> values, org.fireflyframework.cache.serialization.CacheSerializer serializer) { }
    @SuppressWarnings("unchecked") private Fixture fixture() {
        var template = mock(org.springframework.data.redis.core.ReactiveRedisTemplate.class);
        var values = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
        var serializer = mock(org.fireflyframework.cache.serialization.CacheSerializer.class);
        when(template.opsForValue()).thenReturn(values);
        return new Fixture(new RedisCacheAdapterImpl(NAME, template, mock(org.springframework.data.redis.connection.ReactiveRedisConnectionFactory.class), RedisCacheConfig.builder().build(), serializer), values, serializer);
    }
    @Test void bothReadOverloadsKeepHitsWithoutExtraKeyRendering() {
        for (boolean typed : new boolean[]{false,true}) {
            events.list.clear(); var f = fixture(); var key = new Key();
            when(f.values().get(anyString())).thenReturn(Mono.just("serialized"));
            when(f.serializer().deserialize(any(), any())).thenReturn("value");
            assertEquals(Optional.of("value"), typed ? f.adapter().get(key, String.class).block() : f.adapter().get(key).block());
            safeEvent(Level.DEBUG, false); assertEquals(1, key.renders);
            assertTrue(events.list.getLast().getFormattedMessage().contains("cache\\r\\nregion"));
        }
    }
    @Test void bothReadOverloadsKeepMissesWithoutExtraKeyRendering() {
        for (boolean typed : new boolean[]{false,true}) {
            events.list.clear(); var f = fixture(); var key = new Key(); when(f.values().get(anyString())).thenReturn(Mono.empty());
            assertEquals(Optional.empty(), typed ? f.adapter().get(key, String.class).block() : f.adapter().get(key).block());
            safeEvent(Level.DEBUG, false); assertEquals(1, key.renders);
        }
    }
    @Test void bothDeserializerFailuresKeepEmptyFallbackAndTypeOnlyWarnings() {
        for (boolean typed : new boolean[]{false,true}) {
            events.list.clear(); var f = fixture(); var key = new Key(); when(f.values().get(anyString())).thenReturn(Mono.just("serialized"));
            when(f.serializer().deserialize(any(), any())).thenThrow(new IllegalStateException("PRIVATE_MESSAGE\r\nforged"));
            assertEquals(Optional.empty(), typed ? f.adapter().get(key, String.class).block() : f.adapter().get(key).block());
            safeEvent(Level.WARN, true); assertEquals(1, key.renders);
        }
    }
    @Test void serializationFailureRetainsWrappedCauseWithoutRenderingKey() {
        var f = fixture(); var key = new Key(); var failure = new IllegalStateException("PRIVATE_MESSAGE\u2028forged");
        when(f.serializer().serialize(any())).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> f.adapter().put(key, "value").block()).getCause());
        safeEvent(Level.ERROR, true); assertEquals(0, key.renders);
    }
}
