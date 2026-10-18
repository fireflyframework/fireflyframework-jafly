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


package org.fireflyframework.notifications.interfaces.dtos.sms.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SMSResponseDTO {
    private String messageId;      // Provider's message ID
    private String status;         // Delivery status
    private String errorMessage;   // Error message if any
    private long timestamp;        // When the message was sent

    public static SMSResponseDTO success(String messageId) {
        return SMSResponseDTO.builder()
                .messageId(messageId)
                .status("SENT")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SMSResponseDTO error(String errorMessage) {
        return SMSResponseDTO.builder()
                .status("FAILED")
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}