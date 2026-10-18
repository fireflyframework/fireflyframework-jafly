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

package org.fireflyframework.eventsourcing.examples.ledger;

import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.examples.ledger.events.*;
import org.fireflyframework.eventsourcing.projection.ProjectionService;
import org.fireflyframework.eventsourcing.store.EventStore;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Projection service for Account Ledger read model.
 * <p>
 * This service listens to AccountLedger events and builds/updates the read model.
 * It demonstrates the read/write separation pattern where:
 * - Write model (AccountLedger aggregate) handles commands and generates events
 * - Read model (AccountLedgerReadModel) is optimized for queries
 * <p>
 * The projection is eventually consistent - there may be a small delay between
 * an event being saved and the read model being updated.
 */
@Service
@Slf4j
public class AccountLedgerProjectionService extends ProjectionService<AccountLedgerReadModel> {

    private final AccountLedgerRepository repository;
    private final DatabaseClient databaseClient;
    private final EventStore eventStore;
    private final String projectionName = "account-ledger-projection";

    public AccountLedgerProjectionService(AccountLedgerRepository repository,
                                         DatabaseClient databaseClient,
                                         EventStore eventStore,
                                         MeterRegistry meterRegistry) {
        super(meterRegistry);
        this.repository = repository;
        this.databaseClient = databaseClient;
        this.eventStore = eventStore;
    }

    @Override
    public String getProjectionName() {
        return projectionName;
    }

    @Override
    public Mono<Void> handleEvent(StoredEventEnvelope envelope) {
        return Mono.defer(() -> {
            if (envelope.getEvent() instanceof AccountOpenedEvent event) {
                return handleAccountOpened(event, envelope);
            } else if (envelope.getEvent() instanceof MoneyDepositedEvent event) {
                return handleMoneyDeposited(event, envelope);
            } else if (envelope.getEvent() instanceof MoneyWithdrawnEvent event) {
                return handleMoneyWithdrawn(event, envelope);
            } else if (envelope.getEvent() instanceof AccountFrozenEvent event) {
                return handleAccountFrozen(event, envelope);
            } else if (envelope.getEvent() instanceof AccountUnfrozenEvent event) {
                return handleAccountUnfrozen(event, envelope);
            } else if (envelope.getEvent() instanceof AccountClosedEvent event) {
                return handleAccountClosed(event, envelope);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<Long> getCurrentPosition() {
        return databaseClient.sql(
                        "SELECT position FROM projection_positions WHERE projection_name = :projectionName")
                .bind("projectionName", projectionName)
                .map(row -> row.get("position", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Void> updatePosition(long position) {
        return databaseClient.sql("""
                    INSERT INTO projection_positions (projection_name, position, last_updated)
                    VALUES (:projectionName, :position, :lastUpdated)
                    ON CONFLICT (projection_name)
                    DO UPDATE SET position = :position, last_updated = :lastUpdated
                    """)
                .bind("projectionName", projectionName)
                .bind("position", position)
                .bind("lastUpdated", Instant.now())
                .then()
                .doOnSuccess(v -> getMetrics().updatePosition(position, 0L));
    }

    @Override
    protected Mono<Void> clearProjectionData() {
        log.info("Clearing account ledger projection data");
        return databaseClient.sql("DELETE FROM account_ledger_read_model")
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.info("Deleted {} account ledger read models", count))
                .then();
    }

    @Override
    protected Mono<Long> getLatestGlobalSequenceFromEventStore() {
        return eventStore.getCurrentGlobalSequence();
    }
    
    private Mono<Void> handleAccountOpened(AccountOpenedEvent event, StoredEventEnvelope envelope) {
        log.debug("Handling AccountOpenedEvent: accountId={}, accountNumber={}",
                event.getAggregateId(), event.getAccountNumber());

        AccountLedgerReadModel readModel = AccountLedgerReadModel.builder()
                .accountId(event.getAggregateId())
                .accountNumber(event.getAccountNumber())
                .accountType(event.getAccountType())
                .customerId(event.getCustomerId())
                .balance(event.getInitialDeposit())
                .currency(event.getCurrency())
                .frozen(false)
                .closed(false)
                .openedAt(envelope.getCreatedAt())
                .lastTransactionAt(envelope.getCreatedAt())
                .version(envelope.getAggregateVersion())
                .lastUpdated(Instant.now())
                .lastUpdatedBy(envelope.getMetadata().getOrDefault("userId", "system").toString())
                .status("ACTIVE")
                .build();

        return repository.save(readModel)
                .doOnSuccess(saved -> log.debug("Created account ledger read model: accountId={}", saved.getAccountId()))
                .then();
    }
    
    private Mono<Void> handleMoneyDeposited(MoneyDepositedEvent event, StoredEventEnvelope envelope) {
        log.debug("Handling MoneyDepositedEvent: accountId={}, amount={}",
                event.getAggregateId(), event.getAmount());

        return repository.findById(event.getAggregateId())
                .flatMap(readModel -> {
                    readModel.setBalance(readModel.getBalance().add(event.getAmount()));
                    readModel.setLastTransactionAt(envelope.getCreatedAt());
                    readModel.setVersion(envelope.getAggregateVersion());
                    readModel.setLastUpdated(Instant.now());
                    readModel.setLastUpdatedBy(event.getDepositedBy());
                    return repository.save(readModel);
                })
                .doOnSuccess(saved -> log.debug("Updated balance after deposit: accountId={}, newBalance={}",
                        saved.getAccountId(), saved.getBalance()))
                .then();
    }

    private Mono<Void> handleMoneyWithdrawn(MoneyWithdrawnEvent event, StoredEventEnvelope envelope) {
        log.debug("Handling MoneyWithdrawnEvent: accountId={}, amount={}",
                event.getAggregateId(), event.getAmount());

        return repository.findById(event.getAggregateId())
                .flatMap(readModel -> {
                    readModel.setBalance(readModel.getBalance().subtract(event.getAmount()));
                    readModel.setLastTransactionAt(envelope.getCreatedAt());
                    readModel.setVersion(envelope.getAggregateVersion());
                    readModel.setLastUpdated(Instant.now());
                    readModel.setLastUpdatedBy(event.getWithdrawnBy());
                    return repository.save(readModel);
                })
                .doOnSuccess(saved -> log.debug("Updated balance after withdrawal: accountId={}, newBalance={}",
                        saved.getAccountId(), saved.getBalance()))
                .then();
    }

    private Mono<Void> handleAccountFrozen(AccountFrozenEvent event, StoredEventEnvelope envelope) {
        log.debug("Handling AccountFrozenEvent: accountId={}, reason={}",
                event.getAggregateId(), event.getReason());

        return repository.findById(event.getAggregateId())
                .flatMap(readModel -> {
                    readModel.setFrozen(true);
                    readModel.setStatus("FROZEN");
                    readModel.setVersion(envelope.getAggregateVersion());
                    readModel.setLastUpdated(Instant.now());
                    readModel.setLastUpdatedBy(event.getFrozenBy());
                    return repository.save(readModel);
                })
                .doOnSuccess(saved -> log.debug("Account frozen in read model: accountId={}", saved.getAccountId()))
                .then();
    }

    private Mono<Void> handleAccountUnfrozen(AccountUnfrozenEvent event, StoredEventEnvelope envelope) {
        log.debug("Handling AccountUnfrozenEvent: accountId={}, reason={}",
                event.getAggregateId(), event.getReason());

        return repository.findById(event.getAggregateId())
                .flatMap(readModel -> {
                    readModel.setFrozen(false);
                    readModel.setStatus("ACTIVE");
                    readModel.setVersion(envelope.getAggregateVersion());
                    readModel.setLastUpdated(Instant.now());
                    readModel.setLastUpdatedBy(event.getUnfrozenBy());
                    return repository.save(readModel);
                })
                .doOnSuccess(saved -> log.debug("Account unfrozen in read model: accountId={}", saved.getAccountId()))
                .then();
    }

    private Mono<Void> handleAccountClosed(AccountClosedEvent event, StoredEventEnvelope envelope) {
        log.debug("Handling AccountClosedEvent: accountId={}, reason={}",
                event.getAggregateId(), event.getReason());

        return repository.findById(event.getAggregateId())
                .flatMap(readModel -> {
                    readModel.setClosed(true);
                    readModel.setStatus("CLOSED");
                    readModel.setClosedAt(envelope.getCreatedAt());
                    readModel.setVersion(envelope.getAggregateVersion());
                    readModel.setLastUpdated(Instant.now());
                    readModel.setLastUpdatedBy(event.getClosedBy());
                    return repository.save(readModel);
                })
                .doOnSuccess(saved -> log.debug("Account closed in read model: accountId={}", saved.getAccountId()))
                .then();
    }
}

