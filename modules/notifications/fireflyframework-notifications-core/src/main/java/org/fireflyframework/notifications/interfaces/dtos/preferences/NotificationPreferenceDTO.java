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

package org.fireflyframework.notifications.interfaces.dtos.preferences;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO representing a user's notification channel preferences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDTO {

    private String userId;

    @Builder.Default
    private boolean emailEnabled = true;

    @Builder.Default
    private boolean smsEnabled = true;

    @Builder.Default
    private boolean pushEnabled = true;

    @Builder.Default
    private Map<String, Boolean> channels = new HashMap<>();

    /**
     * Check if a specific channel is enabled.
     * Falls back to the top-level channel toggle if no specific override exists.
     */
    public boolean isChannelEnabled(String channel) {
        if (channels.containsKey(channel)) {
            return channels.get(channel);
        }
        return switch (channel.toLowerCase()) {
            case "email" -> emailEnabled;
            case "sms" -> smsEnabled;
            case "push" -> pushEnabled;
            default -> true;
        };
    }
}
