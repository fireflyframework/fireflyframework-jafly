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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for sending templated emails.
 *
 * <p>The {@code templateId} identifies a template (e.g., "welcome-email"),
 * and {@code templateVariables} are substituted into the template before sending.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplateRequestDTO {

    private String templateId;

    @Builder.Default
    private Map<String, Object> templateVariables = new HashMap<>();

    private String from;
    private String to;

    @Builder.Default
    private List<String> cc = new ArrayList<>();

    @Builder.Default
    private List<String> bcc = new ArrayList<>();

    private String subject;
}
