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
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for dispatching HTTP callbacks to external endpoints.
 * Handles retries, circuit breaker, HMAC signing, and execution tracking.
 */
public interface CallbackDispatcher {

    /**
     * Dispatches a callback to the configured endpoint.
     *
     * @param configuration the callback configuration
     * @param eventType the type of event
     * @param eventId the event ID
     * @param payload the event payload to send
     * @return Mono that completes when the callback is dispatched
     */
    Mono<Void> dispatch(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload
    );
}
