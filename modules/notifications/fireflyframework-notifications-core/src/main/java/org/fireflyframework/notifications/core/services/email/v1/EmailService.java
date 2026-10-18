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


package org.fireflyframework.notifications.core.services.email.v1;

import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailTemplateRequestDTO;
import reactor.core.publisher.Mono;

public interface EmailService {
    Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request);

    /**
     * Send a templated email. The template is rendered before sending.
     *
     * @param request the template email request (templateId + variables + recipients)
     * @return a Mono emitting the email response
     */
    default Mono<EmailResponseDTO> sendTemplateEmail(EmailTemplateRequestDTO request) {
        return Mono.error(new UnsupportedOperationException(
                "Template email not supported. Configure a NotificationTemplateEngine bean."));
    }
}