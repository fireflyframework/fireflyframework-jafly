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


package org.fireflyframework.notifications.interfaces.providers.push.v1;

import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationRequest;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationResponse;
import reactor.core.publisher.Mono;

/**
 * Port (outbound interface) for sending push notifications.
 * <p>
 * In hexagonal architecture, this interface represents an output port that defines
 * the contract for push notification delivery. Concrete implementations (adapters)
 * provide the actual infrastructure integration (e.g., Firebase Cloud Messaging).
 * <p>
 * The core domain and application layers depend only on this interface, never on
 * specific implementations, ensuring clean separation of concerns.
 */
public interface PushProvider {
    /**
     * Send a push notification using the provider's infrastructure.
     *
     * @param request Push notification request containing message details, recipient token, and optional data payload
     * @return A reactive response containing delivery status and message ID
     */
    Mono<PushNotificationResponse> sendPush(PushNotificationRequest request);
}
