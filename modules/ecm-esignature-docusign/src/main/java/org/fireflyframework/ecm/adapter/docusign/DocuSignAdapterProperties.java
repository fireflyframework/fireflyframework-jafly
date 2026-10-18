/*
 * Copyright 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.docusign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "firefly.ecm.adapter.docusign")
public class DocuSignAdapterProperties {

    @NotBlank
    private String integrationKey;

    @NotBlank
    private String userId;

    @NotBlank
    private String accountId;

    @NotBlank
    private String privateKey;

    private String baseUrl = "https://na3.docusign.net/restapi";

    private String authServer = "https://account.docusign.com";

    private String webhookUrl;

    private String webhookSecret;

    private Boolean sandboxMode = false;

    @NotNull
    private Duration connectionTimeout = Duration.ofSeconds(30);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(60);

    private Integer maxRetries = 3;

    private Long jwtExpiration = 3600L;

    private Boolean enablePolling = true;

    @NotNull
    private Duration pollingInterval = Duration.ofMinutes(5);

    private String defaultEmailSubject = "Please sign this document";

    private String defaultEmailMessage = "Please review and sign the attached document(s).";
}
