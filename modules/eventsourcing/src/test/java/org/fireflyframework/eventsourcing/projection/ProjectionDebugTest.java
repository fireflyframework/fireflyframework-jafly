/*
 * Copyright 2024-2026 Firefly Software Foundation
 */

package org.fireflyframework.eventsourcing.projection;

import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.examples.AccountBalanceProjectionService;
import org.fireflyframework.eventsourcing.store.EventStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test to isolate balance calculation issues.
 */
@SpringBootTest(classes = ProjectionDebugTest.TestApplication.class)
@ActiveProfiles("test")
@Testcontainers
class ProjectionDebugTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_eventsourcing")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/test-schema.sql");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> 
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }
    
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Import({
        org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration.class
    })
    static class TestApplication {
        
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public EventStore mockEventStore() {
            return org.mockito.Mockito.mock(EventStore.class);
        }
        
        @org.springframework.context.annotation.Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
        
        @org.springframework.context.annotation.Bean
        public AccountBalanceProjectionService accountBalanceProjectionService(
                MeterRegistry meterRegistry,
                org.springframework.data.r2dbc.core.R2dbcEntityTemplate r2dbcTemplate,
                EventStore eventStore) {
            return new AccountBalanceProjectionService(meterRegistry, r2dbcTemplate, eventStore);
        }
    }
    
    @Autowired
    private AccountBalanceProjectionService projectionService;
    
    private UUID testAccountId;
    
    @BeforeEach
    void setUp() {
        testAccountId = UUID.randomUUID();
        projectionService.resetProjection().block(Duration.ofSeconds(5));
    }
    
    @Test
    void debugStepByStepBalanceCalculation() {
        // Step 1: Create account
        StoredEventEnvelope accountCreated = createAccountCreatedEvent(testAccountId, "USD", 1L);
        StepVerifier.create(projectionService.handleEvent(accountCreated))
                .verifyComplete();
        
        StepVerifier.create(projectionService.getAccountBalance(testAccountId))
                .assertNext(balance -> {
                    System.out.println("After account creation: " + balance.getBalance());
                    assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
                })
                .verifyComplete();
        
        // Step 2: Deposit 100
        StoredEventEnvelope deposit1 = createMoneyDepositedEvent(testAccountId, new BigDecimal("100.00"), 2L);
        StepVerifier.create(projectionService.handleEvent(deposit1))
                .verifyComplete();
                
        StepVerifier.create(projectionService.getAccountBalance(testAccountId))
                .assertNext(balance -> {
                    System.out.println("After deposit 100: " + balance.getBalance());
                    assertThat(balance.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
                })
                .verifyComplete();
        
        // Step 3: Withdraw 30
        StoredEventEnvelope withdrawal1 = createMoneyWithdrawnEvent(testAccountId, new BigDecimal("30.00"), 3L);
        StepVerifier.create(projectionService.handleEvent(withdrawal1))
                .verifyComplete();
                
        StepVerifier.create(projectionService.getAccountBalance(testAccountId))
                .assertNext(balance -> {
                    System.out.println("After withdrawal 30: " + balance.getBalance());
                    assertThat(balance.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
                })
                .verifyComplete();
        
        // Step 4: Deposit 50
        StoredEventEnvelope deposit2 = createMoneyDepositedEvent(testAccountId, new BigDecimal("50.00"), 4L);
        StepVerifier.create(projectionService.handleEvent(deposit2))
                .verifyComplete();
                
        StepVerifier.create(projectionService.getAccountBalance(testAccountId))
                .assertNext(balance -> {
                    System.out.println("After deposit 50: " + balance.getBalance());
                    assertThat(balance.getBalance()).isEqualByComparingTo(new BigDecimal("120.00"));
                })
                .verifyComplete();
        
        // Step 5: Withdraw 20
        StoredEventEnvelope withdrawal2 = createMoneyWithdrawnEvent(testAccountId, new BigDecimal("20.00"), 5L);
        StepVerifier.create(projectionService.handleEvent(withdrawal2))
                .verifyComplete();
                
        StepVerifier.create(projectionService.getAccountBalance(testAccountId))
                .assertNext(balance -> {
                    System.out.println("Final balance: " + balance.getBalance());
                    assertThat(balance.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
                })
                .verifyComplete();
    }
    
    private StoredEventEnvelope createAccountCreatedEvent(UUID accountId, String currency, long globalSequence) {
        return StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .event(new AccountBalanceProjectionService.AccountCreatedEvent(accountId, currency))
                .aggregateId(accountId)
                .aggregateType("Account")
                .aggregateVersion(1L)
                .globalSequence(globalSequence)
                .eventType("AccountCreated")
                .createdAt(Instant.now())
                .build();
    }
    
    private StoredEventEnvelope createMoneyDepositedEvent(UUID accountId, BigDecimal amount, long globalSequence) {
        return StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .event(new AccountBalanceProjectionService.MoneyDepositedEvent(accountId, amount))
                .aggregateId(accountId)
                .aggregateType("Account")
                .aggregateVersion(globalSequence)
                .globalSequence(globalSequence)
                .eventType("MoneyDeposited")
                .createdAt(Instant.now())
                .build();
    }
    
    private StoredEventEnvelope createMoneyWithdrawnEvent(UUID accountId, BigDecimal amount, long globalSequence) {
        return StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .event(new AccountBalanceProjectionService.MoneyWithdrawnEvent(accountId, amount))
                .aggregateId(accountId)
                .aggregateType("Account")
                .aggregateVersion(globalSequence)
                .globalSequence(globalSequence)
                .eventType("MoneyWithdrawn")
                .createdAt(Instant.now())
                .build();
    }
}