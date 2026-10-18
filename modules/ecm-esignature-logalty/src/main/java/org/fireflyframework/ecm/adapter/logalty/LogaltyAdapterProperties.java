/*
 * Copyright (c) 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.logalty;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Duration;

/**
 * Configuration properties for the Logalty eSignature adapter.
 *
 * <p>Logalty is a Spanish Trust Service Provider (TSP) that offers qualified electronic
 * signature services compliant with eIDAS regulation. This adapter enables integration
 * with Logalty's signature platform for EU-compliant digital signatures.</p>
 *
 * @author Firefly Software Foundation.
 * @version 1.0
 * @since 1.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "firefly.ecm.adapter.logalty")
public class LogaltyAdapterProperties {

    /**
     * Logalty API client ID for authentication.
     */
    @NotBlank(message = "Logalty client ID is required")
    private String clientId;

    /**
     * Logalty API client secret for authentication.
     */
    @NotBlank(message = "Logalty client secret is required")
    private String clientSecret;

    /**
     * Logalty API username for authentication (alternative to OAuth).
     */
    private String username;

    /**
     * Logalty API password for authentication (alternative to OAuth).
     */
    private String password;

    /**
     * Base URL for Logalty API.
     * Default: https://api.logalty.com (production)
     * Sandbox: https://api-sandbox.logalty.com
     */
    private String baseUrl = "https://api.logalty.com";

    /**
     * API version to use.
     */
    private String apiVersion = "v1";

    /**
     * Webhook URL for receiving signature status updates from Logalty.
     */
    private String webhookUrl;

    /**
     * Webhook secret for validating incoming webhook requests.
     */
    private String webhookSecret;

    /**
     * Connection timeout for HTTP requests.
     */
    private Duration connectionTimeout = Duration.ofSeconds(30);

    /**
     * Read timeout for HTTP requests.
     */
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * Maximum number of retry attempts for failed requests.
     */
    @Min(0)
    @Max(10)
    private Integer maxRetries = 3;

    /**
     * OAuth token expiration time in seconds.
     */
    @Min(300)
    @Max(86400)
    private Integer tokenExpiration = 3600;

    /**
     * Default email subject for signature requests.
     */
    private String defaultEmailSubject = "Firma requerida / Signature required";

    /**
     * Default email message for signature requests.
     */
    private String defaultEmailMessage = "Por favor, revise y firme el documento adjunto / Please review and sign the attached document.";

    /**
     * Enable embedded signing (signing within application).
     */
    private Boolean enableEmbeddedSigning = false;

    /**
     * Return URL after signature completion (for embedded signing).
     */
    private String returnUrl;

    /**
     * Enable document retention in Logalty's archive.
     */
    private Boolean enableDocumentRetention = true;

    /**
     * Document retention period in days.
     */
    @Min(1)
    @Max(3650)
    private Integer documentRetentionDays = 365;

    /**
     * Enable automatic signature reminders.
     */
    private Boolean enableReminders = true;

    /**
     * Reminder frequency in days.
     */
    @Min(1)
    @Max(30)
    private Integer reminderFrequencyDays = 3;

    /**
     * Use sandbox/testing environment.
     */
    private Boolean sandboxMode = false;

    /**
     * Default signature type: SIMPLE, ADVANCED, or QUALIFIED.
     */
    private String defaultSignatureType = "ADVANCED";

    /**
     * Enable biometric signature capture.
     */
    private Boolean enableBiometricSignature = false;

    /**
     * Enable SMS OTP verification for signers.
     */
    private Boolean enableSmsVerification = false;

    /**
     * Enable video identification for signers.
     */
    private Boolean enableVideoIdentification = false;
}
