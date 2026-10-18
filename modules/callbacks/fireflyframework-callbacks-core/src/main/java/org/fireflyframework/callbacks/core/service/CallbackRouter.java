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

package org.fireflyframework.callbacks.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Service for routing events to configured callback endpoints.
 */
public interface CallbackRouter {

    /**
     * Routes an event to all matching callback configurations.
     *
     * @param eventType the type of event (e.g., "customer.created")
     * @param eventId the unique event identifier
     * @param payload the event payload
     * @param headers the event headers
     * @return Mono emitting the number of callbacks triggered
     */
    Mono<Integer> routeEvent(String eventType, UUID eventId, JsonNode payload, Map<String, Object> headers);
}
