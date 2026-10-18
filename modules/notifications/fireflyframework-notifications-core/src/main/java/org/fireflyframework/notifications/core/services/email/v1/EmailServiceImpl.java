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

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.notifications.core.services.template.NotificationTemplateEngine;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailTemplateRequestDTO;
import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired
    private EmailProvider emailProvider;

    @Autowired(required = false)
    private NotificationTemplateEngine templateEngine;

    @Override
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        return emailProvider.sendEmail(request);
    }

    @Override
    public Mono<EmailResponseDTO> sendTemplateEmail(EmailTemplateRequestDTO request) {
        if (templateEngine == null) {
            return Mono.error(new UnsupportedOperationException(
                    "Template email not supported. Configure a NotificationTemplateEngine bean."));
        }

        return templateEngine.render(request.getTemplateId(), request.getTemplateVariables())
                .flatMap(renderedHtml -> {
                    EmailRequestDTO emailRequest = EmailRequestDTO.builder()
                            .from(request.getFrom())
                            .to(request.getTo())
                            .cc(request.getCc())
                            .bcc(request.getBcc())
                            .subject(request.getSubject())
                            .html(renderedHtml)
                            .build();
                    return emailProvider.sendEmail(emailRequest);
                })
                .doOnError(e -> log.error("Failed to send template email '{}': {}",
                        request.getTemplateId(), e.getMessage()));
    }
}