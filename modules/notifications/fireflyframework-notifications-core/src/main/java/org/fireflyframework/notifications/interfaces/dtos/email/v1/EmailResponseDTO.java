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


package org.fireflyframework.notifications.interfaces.dtos.email.v1;

import org.fireflyframework.notifications.interfaces.enums.EmailStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponseDTO {
    private String messageId;
    private EmailStatusEnum status;
    private String errorMessage;
    private long timestamp;

    public static EmailResponseDTO success(String messageId) {
        return EmailResponseDTO.builder()
                .messageId(messageId)
                .status(EmailStatusEnum.SENT)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static EmailResponseDTO error(String errorMessage) {
        return EmailResponseDTO.builder()
                .status(EmailStatusEnum.FAILED)
                .errorMessage(errorMessage != null ? errorMessage : "Unknown error")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}