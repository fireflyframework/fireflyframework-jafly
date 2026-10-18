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

package org.fireflyframework.eventsourcing.aggregate;

import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for event-sourced aggregates that provides the foundation for
 * implementing Domain-Driven Design aggregates with Event Sourcing.
 * <p>
 * <b>What is an Aggregate?</b><br>
 * An aggregate is a cluster of domain objects that can be treated as a single unit
 * for data changes. It enforces business rules and maintains consistency boundaries.
 * In Event Sourcing, aggregates generate events to record state changes.
 * <p>
 * <b>Key Responsibilities:</b>
 * <ul>
 *   <li><b>Business Logic</b> - Validates commands and enforces business rules</li>
 *   <li><b>Event Generation</b> - Produces events when state changes occur</li>
 *   <li><b>State Reconstruction</b> - Rebuilds current state from event history</li>
 *   <li><b>Consistency Boundary</b> - Ensures all invariants are maintained</li>
 *   <li><b>Optimistic Concurrency</b> - Tracks version for conflict detection</li>
 * </ul>
 * <p>
 * <b>How to Use:</b>
 * <ol>
 *   <li>Extend this class and define your aggregate's state as private fields</li>
 *   <li>Create command methods (public) that validate and generate events</li>
 *   <li>Create event handler methods (private) named "on" that update state</li>
 *   <li>Use {@link #applyChange(Event)} to apply new events</li>
 *   <li>Never modify state directly - always through events!</li>
 * </ol>
 * <p>
 * <b>Complete Example:</b>
 * <pre>
 * {@code
 * public class BankAccount extends AggregateRoot {
 *     // State (derived from events)
 *     private String accountNumber;
 *     private BigDecimal balance;
 *     private AccountStatus status;
 *
 *     // Constructor for creating new aggregates
 *     public BankAccount(UUID id, String accountNumber, BigDecimal initialBalance) {
 *         super(id, "BankAccount");
 *
 *         // Validate business rules
 *         if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
 *             throw new IllegalArgumentException("Initial balance cannot be negative");
 *         }
 *
 *         // Generate event
 *         applyChange(AccountCreatedEvent.builder()
 *             .aggregateId(id)
 *             .accountNumber(accountNumber)
 *             .initialBalance(initialBalance)
 *             .build());
 *     }
 *
 *     // Constructor for loading from event store
 *     public BankAccount(UUID id) {
 *         super(id, "BankAccount");
 *     }
 *
 *     // Command method - validates and generates events
 *     public void withdraw(BigDecimal amount, String reason) {
 *         // 1. Validate business rules
 *         if (status != AccountStatus.ACTIVE) {
 *             throw new AccountNotActiveException("Account is not active");
 *         }
 *         if (amount.compareTo(BigDecimal.ZERO) <= 0) {
 *             throw new IllegalArgumentException("Amount must be positive");
 *         }
 *         if (balance.compareTo(amount) < 0) {
 *             throw new InsufficientFundsException("Insufficient funds");
 *         }
 *
 *         // 2. Generate event (what happened)
 *         applyChange(MoneyWithdrawnEvent.builder()
 *             .aggregateId(getId())
 *             .amount(amount)
 *             .reason(reason)
 *             .build());
 *     }
 *
 *     // Event handler - updates state (no validation!)
 *     private void on(AccountCreatedEvent event) {
 *         this.accountNumber = event.getAccountNumber();
 *         this.balance = event.getInitialBalance();
 *         this.status = AccountStatus.ACTIVE;
 *     }
 *
 *     // Event handler - updates state
 *     private void on(MoneyWithdrawnEvent event) {
 *         this.balance = this.balance.subtract(event.getAmount());
 *     }
 *
 *     // Getters for read-only access
 *     public BigDecimal getBalance() { return balance; }
 *     public String getAccountNumber() { return accountNumber; }
 * }
 * }
 * </pre>
 * <p>
 * <b>Event Handler Convention:</b><br>
 * Event handlers must be named "on" and accept a single event parameter.
 * They can be private or protected. The framework uses reflection to find
 * and invoke the appropriate handler.
 * <pre>
 * {@code
 * // Option 1: Method named "on" with event type parameter
 * private void on(MoneyWithdrawnEvent event) { ... }
 *
 * // Option 2: Method named "on" + EventClassName
 * private void onMoneyWithdrawnEvent(MoneyWithdrawnEvent event) { ... }
 * }
 * </pre>
 * <p>
 * <b>Best Practices:</b>
 * <ul>
 *   <li>Keep aggregates small and focused on a single business concept</li>
 *   <li>Never modify state directly - always use events via applyChange()</li>
 *   <li>Command methods should validate, event handlers should not</li>
 *   <li>Make event handlers private to prevent external state manipulation</li>
 *   <li>Use meaningful aggregate type names (e.g., "BankAccount", not "Account")</li>
 *   <li>Don't load other aggregates within an aggregate - use eventual consistency</li>
 *   <li>Provide both constructors: one for creation, one for loading from history</li>
 * </ul>
 * <p>
 * <b>Common Patterns:</b>
 * <pre>
 * {@code
 * // Pattern 1: Creation
 * BankAccount account = new BankAccount(UUID.randomUUID(), "12345", BigDecimal.valueOf(1000));
 * eventStore.appendEvents(account.getId(), "BankAccount", account.getUncommittedEvents(), -1);
 * account.markEventsAsCommitted();
 *
 * // Pattern 2: Loading and modifying
 * EventStream stream = eventStore.loadEventStream(accountId, "BankAccount");
 * BankAccount account = new BankAccount(accountId);
 * account.loadFromHistory(stream.getEvents());
 * account.withdraw(BigDecimal.valueOf(100), "ATM Withdrawal");
 * eventStore.appendEvents(accountId, "BankAccount", account.getUncommittedEvents(), account.getVersion());
 * account.markEventsAsCommitted();
 * }
 * </pre>
 *
 * @see Event
 * @see StoredEventEnvelope
 * @see org.fireflyframework.eventsourcing.store.EventStore
 */
@Getter
@Slf4j
public abstract class AggregateRoot {

    /**
     * The unique identifier of this aggregate.
     */
    private final UUID id;

    /**
     * The type name of this aggregate (e.g., "Account", "Order").
     */
    private final String aggregateType;

    /**
     * Current version of the aggregate for optimistic concurrency control.
     */
    @Getter(AccessLevel.PACKAGE)
    private long version;

    /**
     * List of uncommitted events that have been applied but not yet persisted.
     */
    private final List<Event> uncommittedEvents = new ArrayList<>();

    /**
     * Whether this aggregate has been marked for deletion.
     */
    private boolean deleted = false;

    /**
     * Constructs a new aggregate with the specified ID and type.
     *
     * @param id the unique identifier
     * @param aggregateType the aggregate type name
     */
    protected AggregateRoot(UUID id, String aggregateType) {
        if (id == null) {
            throw new IllegalArgumentException("Aggregate ID cannot be null");
        }
        if (aggregateType == null || aggregateType.trim().isEmpty()) {
            throw new IllegalArgumentException("Aggregate type cannot be null or empty");
        }

        this.id = id;
        this.aggregateType = aggregateType.trim();
        this.version = -1L;

        log.debug("Created new aggregate: id={}, type={}", id, aggregateType);
    }

    /**
     * Applies a new event to this aggregate.
     * <p>
     * This method:
     * 1. Adds the event to the uncommitted events list
     * 2. Applies the event to update the aggregate state
     * 3. Increments the version number
     *
     * @param event the event to apply
     * @throws IllegalArgumentException if the event is null
     * @throws EventHandlerException if no event handler is found
     */
    protected void applyChange(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (!id.equals(event.getAggregateId())) {
            throw new IllegalArgumentException(
                "Event aggregate ID (" + event.getAggregateId() +
                ") does not match aggregate ID (" + id + ")"
            );
        }

        EventSourcingLoggingContext.setAggregateContext(id, aggregateType, version);
        EventSourcingLoggingContext.setEventType(event.getEventType());

        log.debug("Applying new event: aggregateId={}, type={}, eventType={}, currentVersion={}",
                id, aggregateType, event.getEventType(), version);

        uncommittedEvents.add(event);
        applyEvent(event);
        version++;

        log.debug("Event applied successfully. New version: {}, uncommitted events: {}",
                version, uncommittedEvents.size());

        EventSourcingLoggingContext.clearEventType();
    }

    /**
     * Loads the aggregate state from a stream of historical events.
     * <p>
     * This method reconstructs the aggregate state by:
     * 1. Applying each event in sequence
     * 2. Setting the aggregate version to match the last event
     * 3. Clearing any uncommitted events
     *
     * @param events the historical events to apply
     * @throws IllegalArgumentException if events belong to a different aggregate
     */
    public void loadFromHistory(List<StoredEventEnvelope> events) {
        if (events == null || events.isEmpty()) {
            log.debug("No events to load for aggregate: id={}, type={}", id, aggregateType);
            return;
        }

        EventSourcingLoggingContext.setAggregateContext(id, aggregateType);
        log.info("Loading aggregate from history: id={}, type={}, eventCount={}",
                id, aggregateType, events.size());

        // Validate all events belong to this aggregate
        for (StoredEventEnvelope envelope : events) {
            if (!id.equals(envelope.getAggregateId())) {
                throw new IllegalArgumentException(
                    "Event aggregate ID (" + envelope.getAggregateId() +
                    ") does not match aggregate ID (" + id + ")"
                );
            }
            if (!aggregateType.equals(envelope.getAggregateType())) {
                throw new IllegalArgumentException(
                    "Event aggregate type (" + envelope.getAggregateType() +
                    ") does not match aggregate type (" + aggregateType + ")"
                );
            }
        }

        // Apply events in order
        long startVersion = version;
        for (StoredEventEnvelope envelope : events) {
            if (log.isTraceEnabled()) {
                log.trace("Applying historical event: eventType={}, version={}",
                        envelope.getEvent().getEventType(), envelope.getAggregateVersion());
            }
            applyEvent(envelope.getEvent());
            version = envelope.getAggregateVersion();
        }

        // Clear uncommitted events after loading from history
        uncommittedEvents.clear();

        log.info("Aggregate loaded from history: id={}, type={}, version={} (from {} to {})",
                id, aggregateType, version, startVersion, version);
        EventSourcingLoggingContext.clearAggregateContext();
    }

    /**
     * Gets an immutable copy of the uncommitted events.
     *
     * @return list of uncommitted events
     */
    public List<Event> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    /**
     * Marks all uncommitted events as committed.
     * <p>
     * This should be called after successfully persisting events
     * to the event store.
     */
    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    /**
     * Checks if there are any uncommitted events.
     *
     * @return true if there are uncommitted events
     */
    public boolean hasUncommittedEvents() {
        return !uncommittedEvents.isEmpty();
    }

    /**
     * Gets the number of uncommitted events.
     *
     * @return the count of uncommitted events
     */
    public int getUncommittedEventCount() {
        return uncommittedEvents.size();
    }

    /**
     * Marks this aggregate as deleted.
     * <p>
     * This is a soft delete - the aggregate and its events remain
     * in the event store, but the aggregate is marked as deleted.
     */
    protected void markAsDeleted() {
        this.deleted = true;
    }

    /**
     * Gets the current version of the aggregate.
     * <p>
     * This is useful for snapshot creation and optimistic concurrency control.
     *
     * @return the current version
     */
    public long getCurrentVersion() {
        return this.version;
    }

    /**
     * Sets the version of the aggregate.
     * <p>
     * This should only be used when restoring from a snapshot.
     * Normal version management is handled automatically by the framework.
     *
     * @param version the version to set
     */
    protected void setCurrentVersion(long version) {
        this.version = version;
    }

    /**
     * Applies an event to update the aggregate state.
     * <p>
     * This method uses reflection to find and invoke the appropriate
     * event handler method. Handler methods should be named "on" + EventClassName
     * and take a single parameter of the event type.
     *
     * @param event the event to apply
     * @throws EventHandlerException if no handler is found or invocation fails
     */
    private void applyEvent(Event event) {
        try {
            String methodName = "on";
            String eventClassName = event.getClass().getSimpleName();
            
            // Try to find a method named "on" + EventClassName
            try {
                var method = this.getClass().getDeclaredMethod(methodName, event.getClass());
                method.setAccessible(true);
                method.invoke(this, event);
                return;
            } catch (NoSuchMethodException e) {
                // Try with event class simple name
                try {
                    String alternativeMethodName = "on" + eventClassName;
                    var method = this.getClass().getDeclaredMethod(alternativeMethodName, event.getClass());
                    method.setAccessible(true);
                    method.invoke(this, event);
                    return;
                } catch (ReflectiveOperationException e2) {
                    // Continue to search in parent classes
                }
            } catch (ReflectiveOperationException e) {
                // Continue to search
            }

            // If no exact match found, look for methods that accept the event type
            var methods = this.getClass().getDeclaredMethods();
            for (var method : methods) {
                if (method.getName().equals(methodName) && 
                    method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                    
                    try {
                        method.setAccessible(true);
                        method.invoke(this, event);
                        return;
                    } catch (Exception e2) {
                        // Continue searching
                    }
                }
            }

            throw new EventHandlerException(
                "No event handler found for " + event.getClass().getSimpleName() + 
                " in aggregate " + this.getClass().getSimpleName()
            );
            
        } catch (Exception e) {
            if (e instanceof EventHandlerException) {
                throw e;
            }
            throw new EventHandlerException(
                "Failed to apply event " + event.getClass().getSimpleName() + 
                " to aggregate " + this.getClass().getSimpleName(), e
            );
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AggregateRoot that = (AggregateRoot) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s{id=%s, version=%d, uncommittedEvents=%d}", 
            getClass().getSimpleName(), id, version, uncommittedEvents.size());
    }
}