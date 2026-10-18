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

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.notifications.interfaces.dtos.preferences.NotificationPreferenceDTO;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link NotificationPreferenceService}.
 *
 * <p>Suitable for development, testing, and single-instance deployments.
 * For production multi-instance deployments, use a persistent-backed implementation.
 */
@Slf4j
public class InMemoryNotificationPreferenceService implements NotificationPreferenceService {

    private final ConcurrentHashMap<String, NotificationPreferenceDTO> store = new ConcurrentHashMap<>();

    @Override
    public Mono<NotificationPreferenceDTO> getPreferences(String userId) {
        return Mono.justOrEmpty(store.get(userId))
                .switchIfEmpty(Mono.just(NotificationPreferenceDTO.builder()
                        .userId(userId)
                        .emailEnabled(true)
                        .smsEnabled(true)
                        .pushEnabled(true)
                        .build()));
    }

    @Override
    public Mono<NotificationPreferenceDTO> updatePreferences(String userId, NotificationPreferenceDTO preferences) {
        preferences.setUserId(userId);
        store.put(userId, preferences);
        log.debug("Updated notification preferences for user: {}", userId);
        return Mono.just(preferences);
    }

    @Override
    public Mono<Boolean> isChannelEnabled(String userId, String channel) {
        return getPreferences(userId)
                .map(prefs -> prefs.isChannelEnabled(channel));
    }
}
