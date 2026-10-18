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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequestDTO {

    /**
     * The sender email address.
     */
    @Email
    @NotBlank
    private String from;

    /**
     * The primary recipient email address.
     */
    @Email
    @NotBlank
    private String to;

    /**
     * List of CC recipients.
     */
    @Builder.Default
    private List<String> cc = new ArrayList<>();

    /**
     * List of BCC recipients.
     */
    @Builder.Default
    private List<String> bcc = new ArrayList<>();

    /**
     * Subject line of the email.
     */
    @NotBlank
    private String subject;

    /**
     * Plain text content.
     */
    private String text;

    /**
     * HTML content.
     */
    private String html;

    /**
     * A list of attachments.
     */
    @Builder.Default
    private List<EmailAttachmentDTO> attachments = new ArrayList<>();

    /**
     * Convenience method to add a single attachment.
     */
    public void addAttachment(EmailAttachmentDTO attachment) {
        this.attachments.add(attachment);
    }

    /**
     * Convenience method to add a CC email address.
     */
    public void addCc(String ccEmail) {
        this.cc.add(ccEmail);
    }

    /**
     * Convenience method to add a BCC email address.
     */
    public void addBcc(String bccEmail) {
        this.bcc.add(bccEmail);
    }
}