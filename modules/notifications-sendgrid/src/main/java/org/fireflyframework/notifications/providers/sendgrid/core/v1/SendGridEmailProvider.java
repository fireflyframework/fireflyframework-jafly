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


package org.fireflyframework.notifications.providers.sendgrid.core.v1;

import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailAttachmentDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.fireflyframework.notifications.providers.sendgrid.properties.v1.SendGridProperties;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SendGridEmailProvider implements EmailProvider {

    private final SendGridProperties properties;

    private final SendGrid sendGrid;

    @Override
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        return Mono.fromCallable(() -> {
            try {
                Mail mail = buildMail(request);

                Request sendGridRequest = new Request();
                sendGridRequest.setMethod(Method.POST);
                sendGridRequest.setEndpoint("mail/send");
                sendGridRequest.setBody(mail.build());

                Response response = sendGrid.api(sendGridRequest);

                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    String messageId = getMessageIdFromHeaders(response.getHeaders());
                    return EmailResponseDTO.success(messageId);
                } else {
                    return EmailResponseDTO.error("SendGrid error: " + response.getBody());
                }
            } catch (IOException e) {
                return EmailResponseDTO.error(e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mail buildMail(EmailRequestDTO request) {
        Email from = new Email(request.getFrom());
        Email to = new Email(request.getTo());

        Content content;
        if (StringUtils.hasText(request.getHtml())) {
            content = new Content("text/html", request.getHtml());
        } else {
            content = new Content("text/plain",
                    StringUtils.hasText(request.getText()) ? request.getText() : "");
        }

        Mail mail = new Mail(from, request.getSubject(), to, content);

        if (request.getCc() != null) {
            for (String cc : request.getCc()) {
                if (StringUtils.hasText(cc)) {
                    mail.personalization.getFirst().addCc(new Email(cc));
                }
            }
        }

        if (request.getBcc() != null) {
            for (String bcc : request.getBcc()) {
                if (StringUtils.hasText(bcc)) {
                    mail.personalization.getFirst().addBcc(new Email(bcc));
                }
            }
        }

        if (request.getAttachments() != null) {
            for (EmailAttachmentDTO attachment : request.getAttachments()) {
                if (attachment != null && attachment.getContent() != null && attachment.getContent().length > 0) {
                    Attachments sgAttachment = new Attachments();
                    sgAttachment.setContent(Base64.getEncoder().encodeToString(attachment.getContent()));
                    sgAttachment.setType(attachment.getContentType());
                    sgAttachment.setFilename(attachment.getFilename());
                    sgAttachment.setDisposition("attachment");
                    mail.addAttachments(sgAttachment);
                }
            }
        }

        return mail;
    }

    private String getMessageIdFromHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String headerValue = headers.get("X-Message-Id");
        if (headerValue == null) {
            headerValue = headers.get("X-Message-ID");
        }
        return headerValue;
    }
}