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

package org.fireflyframework.notifications.core.services.preferences;

import org.fireflyframework.notifications.interfaces.dtos.preferences.NotificationPreferenceDTO;
import reactor.core.publisher.Mono;

/**
 * Service for managing user notification preferences.
 *
 * <p>Implementations may store preferences in memory, R2DBC, or a cache backend.
 * The default in-memory implementation is provided for development and testing.
 */
public interface NotificationPreferenceService {

    /**
     * Get notification preferences for a user.
     * Returns default (all channels enabled) if no preferences are stored.
     */
    Mono<NotificationPreferenceDTO> getPreferences(String userId);

    /**
     * Update notification preferences for a user.
     */
    Mono<NotificationPreferenceDTO> updatePreferences(String userId, NotificationPreferenceDTO preferences);

    /**
     * Check if a specific channel is enabled for a user.
     */
    Mono<Boolean> isChannelEnabled(String userId, String channel);
}
