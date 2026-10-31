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

package org.fireflyframework.eda.listener;

/**
 * Result for the transport's acknowledgment owner. Only HANDLED permits acknowledgment.
 * Matched listeners passed routing/type selection; completed listeners actually succeeded.
 * A failure is internal state and must not be serialized into an event or logged as message content.
 */
public record EventDeliveryOutcome(Disposition disposition, int matchedListeners,
                                   int completedListeners, Throwable failure) {
    public enum Disposition { HANDLED, UNHANDLED, RETRY, DEAD_LETTER }
}
