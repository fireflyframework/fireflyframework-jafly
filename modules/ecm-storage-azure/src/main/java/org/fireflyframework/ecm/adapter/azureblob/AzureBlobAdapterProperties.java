/*
 * Copyright (c) 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.azureblob;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Configuration properties for the Microsoft Azure Blob Storage ECM adapter.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "firefly.ecm.adapter.azure-blob")
public class AzureBlobAdapterProperties {

    @NotBlank(message = "Azure Storage account name is required")
    private String accountName;

    @NotBlank(message = "Azure Blob container name is required")
    private String containerName;

    private String accountKey;

    private String connectionString;

    private String sasToken;

    private Boolean managedIdentity = false;

    private String endpoint;

    private String pathPrefix = "documents/";

    private Boolean enableVersioning = true;

    private String accessTier = "Hot";

    @Min(0)
    @Max(10)
    private Integer maxRetries = 3;

    @Min(1)
    @Max(300)
    private Integer timeoutSeconds = 30;

    @Min(1024)
    private Long blockSize = 4194304L; // 4MB

    @Min(1024)
    private Long blockUploadThreshold = 268435456L; // 256MB

    private Boolean enableEncryption = true;

    private String encryptionKeyUrl;

    private Boolean enableSoftDelete = true;

    @Min(1)
    @Max(365)
    private Integer softDeleteRetentionDays = 7;
}
