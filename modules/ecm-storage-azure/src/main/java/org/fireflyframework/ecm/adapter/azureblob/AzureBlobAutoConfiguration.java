/*
 * Copyright (c) 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.azureblob;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Spring Boot auto-configuration for Azure Blob Storage ECM adapters.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({BlobServiceClient.class, BlobContainerClient.class})
@ConditionalOnProperty(name = "firefly.ecm.adapter-type", havingValue = "azure-blob")
@EnableConfigurationProperties(AzureBlobAdapterProperties.class)
public class AzureBlobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BlobServiceClient blobServiceClient(AzureBlobAdapterProperties properties) {
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();

        if (properties.getEndpoint() != null) {
            builder.endpoint(properties.getEndpoint());
        } else {
            builder.endpoint(String.format("https://%s.blob.core.windows.net", properties.getAccountName()));
        }

        if (properties.getConnectionString() != null) {
            log.info("Using connection string for Azure Blob");
            builder.connectionString(properties.getConnectionString());
        } else if (properties.getAccountKey() != null) {
            log.info("Using account key for Azure Blob");
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
                properties.getAccountName(), properties.getAccountKey());
            builder.credential(credential);
        } else if (properties.getSasToken() != null) {
            log.info("Using SAS token for Azure Blob");
            builder.sasToken(properties.getSasToken());
        } else if (properties.getManagedIdentity()) {
            log.info("Using Managed Identity for Azure Blob");
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();
            builder.credential(credential);
        } else {
            throw new IllegalArgumentException("Azure Blob auth not configured");
        }

        BlobServiceClient client = builder.buildClient();
        log.info("Azure Blob Service Client configured for account: {}", properties.getAccountName());
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient,
                                                   AzureBlobAdapterProperties properties) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(properties.getContainerName());
        if (!containerClient.exists()) {
            log.info("Creating Azure Blob container: {}", properties.getContainerName());
            containerClient.create();
        }
        return containerClient;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean("azureBlobCircuitBreaker")
    @ConditionalOnMissingBean(name = "azureBlobCircuitBreaker")
    public CircuitBreaker azureBlobCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        return CircuitBreaker.of("azureBlobStorage", config);
    }

    @Bean("azureBlobRetry")
    @ConditionalOnMissingBean(name = "azureBlobRetry")
    public Retry azureBlobRetry(AzureBlobAdapterProperties properties) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(properties.getMaxRetries())
            .waitDuration(Duration.ofSeconds(2))
            .retryOnException(throwable -> true)
            .build();

        return Retry.of("azureBlobStorage", config);
    }
}
