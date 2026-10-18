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

package org.fireflyframework.client.integration;

import org.fireflyframework.client.ClientType;
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.GrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprehensive test suite for the redesigned ServiceClient framework.
 * 
 * <p>This test demonstrates the new simplified API and improved developer experience
 * across all client types (REST, gRPC) with banking domain examples.
 */
@DisplayName("New ServiceClient Framework - Banking Integration Tests")
class NewServiceClientTest {

    @Test
    @DisplayName("Should create REST client with simplified builder API")
    void shouldCreateRestClientWithSimplifiedBuilder() {
        // Given: Simple REST client creation
        RestClient client = ServiceClient.rest("customer-service")
            .baseUrl("http://customer-service:8080")
            .timeout(Duration.ofSeconds(30))
            .defaultHeader("Accept", "application/json")
            .build();

        // Then: Client should be properly configured
        assertThat(client).isNotNull();
        assertThat(client.getServiceName()).isEqualTo("customer-service");
        assertThat(client.getBaseUrl()).isEqualTo("http://customer-service:8080");
        assertThat(client.getClientType()).isEqualTo(ClientType.REST);
        assertThat(client.isReady()).isTrue();
    }

    @Test
    @DisplayName("Should demonstrate fluent request builder API for banking operations")
    void shouldDemonstrateFluentRequestBuilderApi() {
        // Given: A REST client for account service
        RestClient accountClient = ServiceClient.rest("account-service")
            .baseUrl("http://account-service:8080")
            .jsonContentType()
            .build();

        // When: Building a request with fluent API
        RestClient.RequestBuilder<Account> requestBuilder = accountClient.get("/accounts/{accountId}", Account.class)
            .withPathParam("accountId", "ACC-123456")
            .withQueryParam("includeBalance", true)
            .withQueryParam("includeTransactions", false)
            .withHeader("X-Customer-ID", "CUST-789")
            .withTimeout(Duration.ofSeconds(15));

        // Then: Request builder should be properly configured
        assertThat(requestBuilder).isNotNull();
        
        // Note: In a real test, we would mock the WebClient to verify the actual request
        // For this demonstration, we're just showing the API structure
    }

    @Test
    @DisplayName("Should create gRPC client with simplified configuration")
    void shouldCreateGrpcClientWithSimplifiedConfiguration() {
        // Given: gRPC client creation (simplified)
        GrpcClient grpcClient = ServiceClient.grpc("payment-service", PaymentServiceStub.class)
            .address("payment-service:9090")
            .usePlaintext()
            .timeout(Duration.ofSeconds(30))
            .stubFactory(channel -> new PaymentServiceStub())
            .build();

        // Then: Client should be properly configured
        assertThat(grpcClient).isNotNull();
        assertThat(grpcClient.getServiceName()).isEqualTo("payment-service");
        assertThat(grpcClient.getAddress()).isEqualTo("payment-service:9090"); // For gRPC, this returns address
        assertThat(grpcClient.getClientType()).isEqualTo(ClientType.GRPC);
        assertThat(grpcClient.isReady()).isTrue();
    }




    @Test
    @DisplayName("Should handle client type validation correctly")
    void shouldHandleClientTypeValidationCorrectly() {
        // Given: Different client types
        ServiceClient restClient = ServiceClient.rest("test-service")
            .baseUrl("http://test:8080")
            .build();

        // Then: Client types should be correctly identified
        assertThat(restClient.getClientType().isHttpBased()).isTrue();
        assertThat(restClient.getClientType().supportsStreaming()).isTrue();
        assertThat(restClient.getClientType().requiresSdkIntegration()).isFalse();
    }

    @Test
    @DisplayName("Should validate builder configuration properly")
    void shouldValidateBuilderConfigurationProperly() {
        // When: Creating REST client without base URL
        assertThrows(IllegalStateException.class, () -> {
            ServiceClient.rest("test-service").build();
        });

        // When: Creating client with invalid service name
        assertThrows(IllegalArgumentException.class, () -> {
            ServiceClient.rest("");
        });
    }


    // ========================================
    // Mock Classes for Testing
    // ========================================

    static class Account {
        private String accountId;
        private String customerId;
        private BigDecimal balance;
        private String currency;
        private String status;

        // Constructors, getters, setters...
        public Account() {}
        
        public Account(String accountId, String customerId, BigDecimal balance, String currency, String status) {
            this.accountId = accountId;
            this.customerId = customerId;
            this.balance = balance;
            this.currency = currency;
            this.status = status;
        }

        // Getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    static class PaymentServiceStub {
        // Mock gRPC stub
    }

}
