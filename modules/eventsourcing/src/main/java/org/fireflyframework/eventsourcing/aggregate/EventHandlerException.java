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

package org.fireflyframework.eventsourcing.aggregate;

import org.fireflyframework.kernel.exception.FireflyException;

/**
 * Exception thrown when there's an issue with event handler methods in aggregates.
 * <p>
 * This exception occurs when:
 * - No event handler method can be found for an event type
 * - Event handler method invocation fails
 * - Event handler method has invalid signature
 * - Reflection issues when accessing event handlers
 */
public class EventHandlerException extends FireflyException {

    public EventHandlerException(String message) {
        super(message);
    }

    public EventHandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventHandlerException(Throwable cause) {
        super(cause);
    }
}