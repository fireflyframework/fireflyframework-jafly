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

package org.fireflyframework.eventsourcing.examples;

import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.projection.ProjectionService;
import org.fireflyframework.eventsourcing.store.EventStore;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Example implementation of a projection service that maintains account balance read models.
 * This demonstrates how to extend the framework's ProjectionService to create domain-specific projections.
 */
@Service
@Slf4j
public class AccountBalanceProjectionService extends ProjectionService<AccountBalanceProjection> {
    
    private final R2dbcEntityTemplate r2dbcTemplate;
    private final EventStore eventStore;
    private final String projectionName = "account-balance-projection";
    
    /**
     * Constructor for AccountBalanceProjectionService.
     *
     * @param meterRegistry the meter registry for metrics
     * @param r2dbcTemplate the R2DBC template for database operations
     * @param eventStore the event store for accessing events
     */
    public AccountBalanceProjectionService(MeterRegistry meterRegistry,
                                         R2dbcEntityTemplate r2dbcTemplate,
                                         EventStore eventStore) {
        super(meterRegistry);
        this.r2dbcTemplate = r2dbcTemplate;
        this.eventStore = eventStore;
    }
    
    @Override
    public Mono<Void> handleEvent(StoredEventEnvelope envelope) {
        return handleEventType(envelope, "AccountCreated", this::handleAccountCreated)
                .switchIfEmpty(handleEventType(envelope, "MoneyDeposited", this::handleMoneyDeposited))
                .switchIfEmpty(handleEventType(envelope, "MoneyWithdrawn", this::handleMoneyWithdrawn))
                .switchIfEmpty(handleEventType(envelope, "MoneyTransferred", this::handleMoneyTransferred))
                .switchIfEmpty(Mono.empty()); // Ignore other event types
    }
    
    private Mono<Void> handleAccountCreated(StoredEventEnvelope envelope) {
        if (envelope.getEvent() instanceof AccountCreatedEvent event) {
            AccountBalanceProjection projection = AccountBalanceProjection.builder()
                    .accountId(envelope.getAggregateId())
                    .balance(BigDecimal.ZERO)
                    .currency(event.getCurrency())
                    .lastUpdated(envelope.getCreatedAt())
                    .version(0L)
                    .build();
                    
            return r2dbcTemplate.insert(AccountBalanceProjection.class)
                    .using(projection)
                    .then()
                    .doOnSuccess(v -> log.debug("Created balance projection for account {}", 
                               envelope.getAggregateId()));
        }
        return Mono.empty();
    }
    
    private Mono<Void> handleMoneyDeposited(StoredEventEnvelope envelope) {
        if (envelope.getEvent() instanceof MoneyDepositedEvent event) {
            return updateBalance(envelope.getAggregateId(), 
                               balance -> balance.add(event.getAmount()),
                               envelope.getCreatedAt())
                    .doOnSuccess(v -> log.debug("Deposited {} to account {}", 
                               event.getAmount(), envelope.getAggregateId()));
        }
        return Mono.empty();
    }
    
    private Mono<Void> handleMoneyWithdrawn(StoredEventEnvelope envelope) {
        if (envelope.getEvent() instanceof MoneyWithdrawnEvent event) {
            return updateBalance(envelope.getAggregateId(), 
                               balance -> balance.subtract(event.getAmount()),
                               envelope.getCreatedAt())
                    .doOnSuccess(v -> log.debug("Withdrew {} from account {}", 
                               event.getAmount(), envelope.getAggregateId()));
        }
        return Mono.empty();
    }
    
    private Mono<Void> handleMoneyTransferred(StoredEventEnvelope envelope) {
        if (envelope.getEvent() instanceof MoneyTransferredEvent event) {
            // Handle both source and destination account updates
            Mono<Void> updateSource = updateBalance(event.getSourceAccountId(), 
                                                  balance -> balance.subtract(event.getAmount()),
                                                  envelope.getCreatedAt());
            
            Mono<Void> updateDestination = updateBalance(event.getDestinationAccountId(), 
                                                       balance -> balance.add(event.getAmount()),
                                                       envelope.getCreatedAt());
            
            return Mono.when(updateSource, updateDestination)
                    .doOnSuccess(v -> log.debug("Transferred {} from {} to {}", 
                               event.getAmount(), event.getSourceAccountId(), event.getDestinationAccountId()));
        }
        return Mono.empty();
    }
    
    private Mono<Void> updateBalance(UUID accountId, 
                                   java.util.function.Function<BigDecimal, BigDecimal> balanceUpdater,
                                   Instant lastUpdated) {
        return r2dbcTemplate.select(AccountBalanceProjection.class)
                .matching(org.springframework.data.relational.core.query.Query.query(
                    org.springframework.data.relational.core.query.Criteria.where("account_id").is(accountId)))
                .one()
                .flatMap(projection -> {
                    BigDecimal newBalance = balanceUpdater.apply(projection.getBalance());
                    AccountBalanceProjection updated = projection.toBuilder()
                            .balance(newBalance)
                            .lastUpdated(lastUpdated)
                            .version(projection.getVersion() + 1)
                            .build();
                            
                    return r2dbcTemplate.update(AccountBalanceProjection.class)
                            .matching(org.springframework.data.relational.core.query.Query.query(
                                org.springframework.data.relational.core.query.Criteria.where("id").is(projection.getId())
                                    .and("version").is(projection.getVersion())))
                            .apply(org.springframework.data.relational.core.query.Update.update("balance", newBalance)
                                    .set("last_updated", lastUpdated)
                                    .set("version", updated.getVersion()))
                            .then();
                })
                .onErrorResume(error -> {
                    log.error("Failed to update balance for account {}: {}", accountId, error.getMessage());
                    return Mono.error(error);
                });
    }
    
    @Override
    public Mono<Long> getCurrentPosition() {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT position FROM projection_positions WHERE projection_name = :projectionName")
                .bind("projectionName", projectionName)
                .map(row -> row.get("position", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }
    
    @Override
    public Mono<Void> updatePosition(long position) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("""
                    INSERT INTO projection_positions (projection_name, position, last_updated) 
                    VALUES (:projectionName, :position, :lastUpdated)
                    ON CONFLICT (projection_name) 
                    DO UPDATE SET position = :position, last_updated = :lastUpdated
                    """)
                .bind("projectionName", projectionName)
                .bind("position", position)
                .bind("lastUpdated", Instant.now())
                .then()
                .doOnSuccess(v -> getMetrics().updatePosition(position, 0L)); // Update metrics
    }
    
    @Override
    public String getProjectionName() {
        return projectionName;
    }
    
    @Override
    protected Mono<Void> clearProjectionData() {
        return r2dbcTemplate.getDatabaseClient()
                .sql("DELETE FROM account_balance_projections")
                .then()
                .then(r2dbcTemplate.getDatabaseClient()
                        .sql("DELETE FROM projection_positions WHERE projection_name = :projectionName")
                        .bind("projectionName", projectionName)
                        .then())
                .doOnSuccess(v -> log.info("Cleared all data for projection {}", projectionName));
    }
    
    @Override
    protected Mono<Long> getLatestGlobalSequenceFromEventStore() {
        return eventStore.getCurrentGlobalSequence();
    }
    
    /**
     * Gets the current balance for an account.
     *
     * @param accountId the account ID to get the balance for
     * @return a Mono containing the account balance projection
     */
    public Mono<AccountBalanceProjection> getAccountBalance(UUID accountId) {
        return r2dbcTemplate.select(AccountBalanceProjection.class)
                .matching(org.springframework.data.relational.core.query.Query.query(
                    org.springframework.data.relational.core.query.Criteria.where("account_id").is(accountId)))
                .one()
                .doOnSuccess(balance -> log.debug("Retrieved balance for account {}: {}", 
                           accountId, balance.getBalance()));
    }
    
    // Example event classes (these would typically be in a separate package)
    
    /**
     * Event representing account creation.
     */
    public static class AccountCreatedEvent implements org.fireflyframework.eventsourcing.domain.Event {
        private final UUID aggregateId;
        private final String currency;
        
        /**
         * Constructor for AccountCreatedEvent.
         *
         * @param aggregateId the account ID
         * @param currency the account currency
         */
        public AccountCreatedEvent(UUID aggregateId, String currency) {
            this.aggregateId = aggregateId;
            this.currency = currency;
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "AccountCreated";
        }
        
        /**
         * Gets the account currency.
         *
         * @return the currency code
         */
        public String getCurrency() {
            return currency;
        }
    }
    
    /**
     * Event representing money deposited to an account.
     */
    public static class MoneyDepositedEvent implements org.fireflyframework.eventsourcing.domain.Event {
        private final UUID aggregateId;
        private final BigDecimal amount;
        
        /**
         * Constructor for MoneyDepositedEvent.
         *
         * @param aggregateId the account ID
         * @param amount the amount deposited
         */
        public MoneyDepositedEvent(UUID aggregateId, BigDecimal amount) {
            this.aggregateId = aggregateId;
            this.amount = amount;
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "MoneyDeposited";
        }
        
        /**
         * Gets the deposited amount.
         *
         * @return the amount deposited
         */
        public BigDecimal getAmount() {
            return amount;
        }
    }
    
    /**
     * Event representing money withdrawn from an account.
     */
    public static class MoneyWithdrawnEvent implements org.fireflyframework.eventsourcing.domain.Event {
        private final UUID aggregateId;
        private final BigDecimal amount;
        
        /**
         * Constructor for MoneyWithdrawnEvent.
         *
         * @param aggregateId the account ID
         * @param amount the amount withdrawn
         */
        public MoneyWithdrawnEvent(UUID aggregateId, BigDecimal amount) {
            this.aggregateId = aggregateId;
            this.amount = amount;
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "MoneyWithdrawn";
        }
        
        /**
         * Gets the withdrawn amount.
         *
         * @return the amount withdrawn
         */
        public BigDecimal getAmount() {
            return amount;
        }
    }
    
    /**
     * Event representing money transferred between accounts.
     */
    public static class MoneyTransferredEvent implements org.fireflyframework.eventsourcing.domain.Event {
        private final UUID aggregateId;
        private final UUID sourceAccountId;
        private final UUID destinationAccountId;
        private final BigDecimal amount;
        
        /**
         * Constructor for MoneyTransferredEvent.
         *
         * @param aggregateId the aggregate ID
         * @param sourceAccountId the source account ID
         * @param destinationAccountId the destination account ID
         * @param amount the amount transferred
         */
        public MoneyTransferredEvent(UUID aggregateId, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount) {
            this.aggregateId = aggregateId;
            this.sourceAccountId = sourceAccountId;
            this.destinationAccountId = destinationAccountId;
            this.amount = amount;
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "MoneyTransferred";
        }
        
        /**
         * Gets the source account ID.
         *
         * @return the source account ID
         */
        public UUID getSourceAccountId() {
            return sourceAccountId;
        }
        
        /**
         * Gets the destination account ID.
         *
         * @return the destination account ID
         */
        public UUID getDestinationAccountId() {
            return destinationAccountId;
        }
        
        /**
         * Gets the transferred amount.
         *
         * @return the amount transferred
         */
        public BigDecimal getAmount() {
            return amount;
        }
    }
}