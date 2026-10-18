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

package org.fireflyframework.core.messaging.integration;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.core.argument.FromStep;
import org.fireflyframework.orchestration.core.argument.Input;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Customer Registration Saga demonstrating CQRS + orchestration engine integration with fireflyframework-core.
 * This saga orchestrates a complex customer onboarding process using CQRS commands and queries
 * within saga steps, with automatic compensation on failures.
 *
 * The saga demonstrates the integration of:
 * - fireflyframework-orchestration for saga orchestration
 * - fireflyframework-cqrs for CQRS commands and queries
 * - fireflyframework-core messaging for event publishing
 */
@Component
@Saga(name = "customer-registration")
public class CustomerRegistrationSagaTest {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public CustomerRegistrationSagaTest(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    /**
     * Step 1: Validate customer data using CQRS Query
     * This step validates email uniqueness and phone number format
     */
    @SagaStep(id = "validate-customer", retry = 3, backoffMs = 1000)
    public Mono<CustomerValidationResult> validateCustomer(@Input CustomerRegistrationRequest request) {
        ValidateCustomerQuery query = ValidateCustomerQuery.builder()
            .email(request.getEmail())
            .phoneNumber(request.getPhoneNumber())
            .correlationId(request.getCorrelationId())
            .build();
            
        // Mock the query result for testing - in a real implementation, this would call the actual query handler
        CustomerValidationResult mockResult = CustomerValidationResult.builder()
            .customerId(request.getCustomerId())
            .isValid(true)
            .validationErrors(new java.util.ArrayList<>())
            .build();
        
        return Mono.just(mockResult);
    }

    /**
     * Step 2: Create customer profile using CQRS Command
     * This step creates the customer profile in the system
     * Compensation: deleteProfile
     */
    @SagaStep(id = "create-profile",
              dependsOn = "validate-customer",
              compensate = "deleteProfile",
              timeoutMs = 30000)
    public Mono<CustomerProfileResult> createProfile(
            @FromStep("validate-customer") CustomerValidationResult validation) {

        if (!validation.isValid()) {
            return Mono.error(new CustomerValidationException(validation.getValidationErrors()));
        }

        // Create customer profile command
        CreateCustomerProfileCommand command = CreateCustomerProfileCommand.builder()
            .customerId(validation.getCustomerId())
            .firstName("John")  // Mock data for test
            .lastName("Doe")    // Mock data for test
            .email("john.doe@example.com")  // Mock data for test
            .phoneNumber("+1-555-123-4567") // Mock data for test
            .correlationId("CORR-" + java.util.UUID.randomUUID().toString())
            .build();

        // Mock the command result for testing - in a real implementation, this would call the actual command handler
        CustomerProfileResult mockResult = CustomerProfileResult.builder()
            .customerId(command.customerId)
            .profileId("PROFILE-" + java.util.UUID.randomUUID().toString())
            .email(command.email)
            .status("CREATED")
            .build();
        
        return Mono.just(mockResult);
    }

    /**
     * Step 3: Create initial account using CQRS Command
     * This step creates the customer's initial bank account
     * Compensation: closeAccount
     */
    @SagaStep(id = "create-account",
              dependsOn = "create-profile",
              compensate = "closeAccount")
    public Mono<AccountCreationResult> createInitialAccount(
            @FromStep("create-profile") CustomerProfileResult profile) {

        CreateAccountCommand command = CreateAccountCommand.builder()
            .customerId(profile.getCustomerId())
            .accountType("CHECKING")
            .initialDeposit(new BigDecimal("1000.00"))  // Mock data
            .currency("USD")
            .correlationId("CORR-" + java.util.UUID.randomUUID().toString())
            .build();

        // Mock the command result for testing - in a real implementation, this would call the actual command handler
        AccountCreationResult mockResult = new AccountCreationResult(
            command.customerId,
            "ACC-" + java.util.UUID.randomUUID().toString(),
            command.accountType,
            command.initialDeposit
        );
        
        return Mono.just(mockResult);
    }

    /**
     * Step 4: Send welcome notification
     * This step sends a welcome email to the customer
     * No compensation needed as this is a notification
     */
    @SagaStep(id = "send-welcome", dependsOn = "create-account")
    public Mono<NotificationResult> sendWelcomeNotification(
            @FromStep("create-profile") CustomerProfileResult profile,
            @FromStep("create-account") AccountCreationResult account) {
        
        // For testing purposes, simulate sending a notification
        return Mono.just(NotificationResult.builder()
            .notificationId("NOTIF-" + java.util.UUID.randomUUID().toString())
            .customerId(profile.getCustomerId())
            .type("WELCOME_EMAIL")
            .status("SENT")
            .sentAt(java.time.Instant.now())
            .build());
    }

    // Compensation Methods

    /**
     * Compensation for create-profile step
     * Deletes the customer profile if subsequent steps fail
     */
    public Mono<Void> deleteProfile(@FromStep("create-profile") CustomerProfileResult profile) {
        // In a real implementation, this would call a DeleteCustomerProfileCommand
        System.out.println("Compensating: Deleting customer profile " + profile.getProfileId());
        return Mono.empty();
    }

    /**
     * Compensation for create-account step
     * Closes the created account
     */
    public Mono<Void> closeAccount(@FromStep("create-account") AccountCreationResult account) {
        // In a real implementation, this would call a CloseAccountCommand
        System.out.println("Compensating: Closing account " + account.getAccountNumber());
        return Mono.empty();
    }

    // Exception classes for saga flow control
    public static class CustomerValidationException extends RuntimeException {
        public CustomerValidationException(java.util.List<String> errors) {
            super("Customer validation failed: " + String.join(", ", errors));
        }
    }

    // Data classes (same as in fireflyframework-domain for compatibility)
    public static class CustomerRegistrationRequest {
        private String customerId;
        private String email;
        private String phoneNumber;
        private String correlationId;
        
        // Getters/setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }

    public static class CustomerValidationResult {
        private String customerId;
        private boolean isValid;
        private java.util.List<String> validationErrors;
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private CustomerValidationResult result = new CustomerValidationResult();
            public Builder customerId(String customerId) { result.customerId = customerId; return this; }
            public Builder isValid(boolean isValid) { result.isValid = isValid; return this; }
            public Builder validationErrors(java.util.List<String> errors) { result.validationErrors = errors; return this; }
            public CustomerValidationResult build() { return result; }
        }
        
        // Getters
        public String getCustomerId() { return customerId; }
        public boolean isValid() { return isValid; }
        public java.util.List<String> getValidationErrors() { return validationErrors; }
    }

    public static class CustomerProfileResult {
        private String customerId;
        private String profileId;
        private String email;
        private String status;
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private CustomerProfileResult result = new CustomerProfileResult();
            public Builder customerId(String customerId) { result.customerId = customerId; return this; }
            public Builder profileId(String profileId) { result.profileId = profileId; return this; }
            public Builder email(String email) { result.email = email; return this; }
            public Builder status(String status) { result.status = status; return this; }
            public CustomerProfileResult build() { return result; }
        }
        
        // Getters
        public String getCustomerId() { return customerId; }
        public String getProfileId() { return profileId; }
        public String getEmail() { return email; }
        public String getStatus() { return status; }
    }

    public static class AccountCreationResult {
        private String customerId;
        private String accountNumber;
        private String accountType;
        private BigDecimal balance;
        
        public AccountCreationResult(String customerId, String accountNumber, String accountType, BigDecimal balance) {
            this.customerId = customerId;
            this.accountNumber = accountNumber;
            this.accountType = accountType;
            this.balance = balance;
        }
        
        // Getters
        public String getCustomerId() { return customerId; }
        public String getAccountNumber() { return accountNumber; }
        public String getAccountType() { return accountType; }
        public BigDecimal getBalance() { return balance; }
    }

    public static class NotificationResult {
        private String notificationId;
        private String customerId;
        private String type;
        private String status;
        private java.time.Instant sentAt;
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private NotificationResult result = new NotificationResult();
            public Builder notificationId(String notificationId) { result.notificationId = notificationId; return this; }
            public Builder customerId(String customerId) { result.customerId = customerId; return this; }
            public Builder type(String type) { result.type = type; return this; }
            public Builder status(String status) { result.status = status; return this; }
            public Builder sentAt(java.time.Instant sentAt) { result.sentAt = sentAt; return this; }
            public NotificationResult build() { return result; }
        }
        
        // Getters
        public String getNotificationId() { return notificationId; }
        public String getCustomerId() { return customerId; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public java.time.Instant getSentAt() { return sentAt; }
    }

    // Mock CQRS classes for testing
    public static class ValidateCustomerQuery implements org.fireflyframework.cqrs.query.Query<CustomerValidationResult> {
        private String email;
        private String phoneNumber;
        private String correlationId;
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private ValidateCustomerQuery query = new ValidateCustomerQuery();
            public Builder email(String email) { query.email = email; return this; }
            public Builder phoneNumber(String phoneNumber) { query.phoneNumber = phoneNumber; return this; }
            public Builder correlationId(String correlationId) { query.correlationId = correlationId; return this; }
            public ValidateCustomerQuery build() { return query; }
        }
        
        // Getters
        public String getEmail() { return email; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getCorrelationId() { return correlationId; }
    }

    public static class CreateCustomerProfileCommand implements org.fireflyframework.cqrs.command.Command<CustomerProfileResult> {
        private String customerId;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
        private String correlationId;
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private CreateCustomerProfileCommand command = new CreateCustomerProfileCommand();
            public Builder customerId(String customerId) { command.customerId = customerId; return this; }
            public Builder firstName(String firstName) { command.firstName = firstName; return this; }
            public Builder lastName(String lastName) { command.lastName = lastName; return this; }
            public Builder email(String email) { command.email = email; return this; }
            public Builder phoneNumber(String phoneNumber) { command.phoneNumber = phoneNumber; return this; }
            public Builder correlationId(String correlationId) { command.correlationId = correlationId; return this; }
            public CreateCustomerProfileCommand build() { return command; }
        }
    }

    public static class CreateAccountCommand implements org.fireflyframework.cqrs.command.Command<AccountCreationResult> {
        private String customerId;
        private String accountType;
        private BigDecimal initialDeposit;
        private String currency;
        private String correlationId;
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private CreateAccountCommand command = new CreateAccountCommand();
            public Builder customerId(String customerId) { command.customerId = customerId; return this; }
            public Builder accountType(String accountType) { command.accountType = accountType; return this; }
            public Builder initialDeposit(BigDecimal initialDeposit) { command.initialDeposit = initialDeposit; return this; }
            public Builder currency(String currency) { command.currency = currency; return this; }
            public Builder correlationId(String correlationId) { command.correlationId = correlationId; return this; }
            public CreateAccountCommand build() { return command; }
        }
    }
}