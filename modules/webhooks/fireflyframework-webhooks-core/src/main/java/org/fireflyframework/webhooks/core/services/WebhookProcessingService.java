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

package org.fireflyframework.webhooks.core.services;

import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import reactor.core.publisher.Mono;

/**
 * Service interface for processing webhook events.
 * <p>
 * This service coordinates the ingestion, persistence, and publishing
 * of webhook events from various providers.
 */
public interface WebhookProcessingService {

    /**
     * Processes a webhook event.
     * <p>
     * This method:
     * 1. Validates the webhook event
     * 2. Persists it using event sourcing (if enabled)
     * 3. Publishes it to the message queue
     *
     * @param eventDto the webhook event data
     * @return a Mono containing the webhook response
     */
    Mono<WebhookResponseDTO> processWebhook(WebhookEventDTO eventDto);
}
