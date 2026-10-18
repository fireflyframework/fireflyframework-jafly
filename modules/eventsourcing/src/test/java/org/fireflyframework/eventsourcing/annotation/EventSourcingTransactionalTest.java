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

package org.fireflyframework.eventsourcing.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for @EventSourcingTransactional annotation metadata.
 */
class EventSourcingTransactionalTest {

    @Test
    void shouldHaveDefaultValues() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("defaultTransactional");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertEquals(EventSourcingTransactional.Propagation.REQUIRED, annotation.propagation());
        assertTrue(annotation.publishEvents());
        assertFalse(annotation.retryOnConcurrencyConflict());
        assertEquals(3, annotation.maxRetries());
        assertEquals(100, annotation.retryDelay());
        assertEquals(-1, annotation.timeout());
        assertFalse(annotation.readOnly());
    }

    @Test
    void shouldReadCustomPropagation() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("requiresNewTransaction");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertEquals(EventSourcingTransactional.Propagation.REQUIRES_NEW, annotation.propagation());
    }

    @Test
    void shouldReadRetryConfiguration() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("withRetry");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertTrue(annotation.retryOnConcurrencyConflict());
        assertEquals(5, annotation.maxRetries());
        assertEquals(200, annotation.retryDelay());
    }

    @Test
    void shouldReadReadOnlyFlag() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("readOnlyMethod");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertTrue(annotation.readOnly());
        assertFalse(annotation.publishEvents());
    }

    @Test
    void shouldReadTimeoutConfiguration() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("withTimeout");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertEquals(30, annotation.timeout());
    }

    @Test
    void shouldSupportAllPropagationModes() {
        // Verify all propagation modes are available
        EventSourcingTransactional.Propagation[] modes = EventSourcingTransactional.Propagation.values();

        assertEquals(6, modes.length);
        assertTrue(containsPropagation(modes, EventSourcingTransactional.Propagation.REQUIRED));
        assertTrue(containsPropagation(modes, EventSourcingTransactional.Propagation.REQUIRES_NEW));
        assertTrue(containsPropagation(modes, EventSourcingTransactional.Propagation.MANDATORY));
        assertTrue(containsPropagation(modes, EventSourcingTransactional.Propagation.NEVER));
        assertTrue(containsPropagation(modes, EventSourcingTransactional.Propagation.SUPPORTS));
        assertTrue(containsPropagation(modes, EventSourcingTransactional.Propagation.NOT_SUPPORTED));
    }

    @Test
    void shouldBeApplicableToMethods() {
        // Verify annotation can be applied to methods
        assertTrue(EventSourcingTransactional.class.isAnnotationPresent(
            java.lang.annotation.Target.class
        ));

        java.lang.annotation.Target target = EventSourcingTransactional.class.getAnnotation(
            java.lang.annotation.Target.class
        );

        boolean hasMethodTarget = false;
        for (java.lang.annotation.ElementType type : target.value()) {
            if (type == java.lang.annotation.ElementType.METHOD) {
                hasMethodTarget = true;
                break;
            }
        }
        assertTrue(hasMethodTarget);
    }

    @Test
    void shouldBeApplicableToTypes() {
        // Verify annotation can be applied to types (classes)
        java.lang.annotation.Target target = EventSourcingTransactional.class.getAnnotation(
            java.lang.annotation.Target.class
        );

        boolean hasTypeTarget = false;
        for (java.lang.annotation.ElementType type : target.value()) {
            if (type == java.lang.annotation.ElementType.TYPE) {
                hasTypeTarget = true;
                break;
            }
        }
        assertTrue(hasTypeTarget);
    }

    @Test
    void shouldReadIsolationLevel() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("withIsolation");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertEquals(EventSourcingTransactional.Isolation.SERIALIZABLE, annotation.isolation());
    }

    @Test
    void shouldSupportAllIsolationLevels() {
        // Verify all isolation levels are available
        EventSourcingTransactional.Isolation[] levels = EventSourcingTransactional.Isolation.values();

        assertEquals(5, levels.length);
        assertTrue(containsIsolation(levels, EventSourcingTransactional.Isolation.DEFAULT));
        assertTrue(containsIsolation(levels, EventSourcingTransactional.Isolation.READ_UNCOMMITTED));
        assertTrue(containsIsolation(levels, EventSourcingTransactional.Isolation.READ_COMMITTED));
        assertTrue(containsIsolation(levels, EventSourcingTransactional.Isolation.REPEATABLE_READ));
        assertTrue(containsIsolation(levels, EventSourcingTransactional.Isolation.SERIALIZABLE));
    }

    @Test
    void shouldHaveDefaultIsolationLevel() throws NoSuchMethodException {
        // Given
        Method method = TestClass.class.getMethod("defaultTransactional");

        // When
        EventSourcingTransactional annotation = method.getAnnotation(EventSourcingTransactional.class);

        // Then
        assertNotNull(annotation);
        assertEquals(EventSourcingTransactional.Isolation.DEFAULT, annotation.isolation());
    }

    private boolean containsPropagation(
            EventSourcingTransactional.Propagation[] modes,
            EventSourcingTransactional.Propagation target) {
        for (EventSourcingTransactional.Propagation mode : modes) {
            if (mode == target) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIsolation(
            EventSourcingTransactional.Isolation[] levels,
            EventSourcingTransactional.Isolation target) {
        for (EventSourcingTransactional.Isolation level : levels) {
            if (level == target) {
                return true;
            }
        }
        return false;
    }

    // Test class with annotated methods

    static class TestClass {

        @EventSourcingTransactional
        public void defaultTransactional() {
        }

        @EventSourcingTransactional(propagation = EventSourcingTransactional.Propagation.REQUIRES_NEW)
        public void requiresNewTransaction() {
        }

        @EventSourcingTransactional(
            retryOnConcurrencyConflict = true,
            maxRetries = 5,
            retryDelay = 200
        )
        public void withRetry() {
        }

        @EventSourcingTransactional(
            readOnly = true,
            publishEvents = false
        )
        public void readOnlyMethod() {
        }

        @EventSourcingTransactional(timeout = 30)
        public void withTimeout() {
        }

        @EventSourcingTransactional(isolation = EventSourcingTransactional.Isolation.SERIALIZABLE)
        public void withIsolation() {
        }
    }
}

