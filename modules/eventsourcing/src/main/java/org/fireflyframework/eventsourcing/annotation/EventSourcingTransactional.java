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

package org.fireflyframework.eventsourcing.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as transactional within the event sourcing context.
 * <p>
 * This annotation provides ACID guarantees for event sourcing operations,
 * ensuring that:
 * <ul>
 *   <li><b>Atomicity</b> - All events are saved or none (all-or-nothing)</li>
 *   <li><b>Consistency</b> - Aggregate version checks prevent conflicts</li>
 *   <li><b>Isolation</b> - Optimistic locking prevents concurrent modifications</li>
 *   <li><b>Durability</b> - Events are persisted before method returns</li>
 * </ul>
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * {@code
 * @Service
 * public class BankAccountService {
 *     
 *     @EventSourcingTransactional
 *     public Mono<BankAccount> transfer(UUID fromId, UUID toId, BigDecimal amount) {
 *         // Load both accounts
 *         return loadAccount(fromId)
 *             .zipWith(loadAccount(toId))
 *             .flatMap(tuple -> {
 *                 BankAccount from = tuple.getT1();
 *                 BankAccount to = tuple.getT2();
 *                 
 *                 // Execute business logic
 *                 from.withdraw(amount, "Transfer");
 *                 to.deposit(amount, "Transfer");
 *                 
 *                 // Both accounts saved in same transaction
 *                 return saveAccount(from)
 *                     .then(saveAccount(to))
 *                     .thenReturn(from);
 *             });
 *         // If any step fails, all changes are rolled back
 *     }
 * }
 * }
 * </pre>
 * <p>
 * <b>Transactional Guarantees:</b>
 * <table border="1">
 *   <tr>
 *     <th>Scenario</th>
 *     <th>Behavior</th>
 *   </tr>
 *   <tr>
 *     <td>Success</td>
 *     <td>All events committed to event store</td>
 *   </tr>
 *   <tr>
 *     <td>Business Rule Violation</td>
 *     <td>No events saved, exception propagated</td>
 *   </tr>
 *   <tr>
 *     <td>Concurrency Conflict</td>
 *     <td>Transaction rolled back, ConcurrencyException thrown</td>
 *   </tr>
 *   <tr>
 *     <td>Database Error</td>
 *     <td>Transaction rolled back, exception propagated</td>
 *   </tr>
 * </table>
 * <p>
 * <b>Event Publishing:</b>
 * <p>
 * When {@link #publishEvents()} is true (default), events are published to
 * external systems (Kafka, RabbitMQ, etc.) only after successful commit.
 * This implements the Transactional Outbox pattern for reliable messaging.
 * <p>
 * <b>Propagation Behavior:</b>
 * <p>
 * The {@link #propagation()} attribute controls how transactions are nested:
 * <ul>
 *   <li><b>REQUIRED</b> - Join existing transaction or create new one (default)</li>
 *   <li><b>REQUIRES_NEW</b> - Always create a new transaction, suspend existing</li>
 *   <li><b>MANDATORY</b> - Must be called within existing transaction</li>
 *   <li><b>NEVER</b> - Must not be called within a transaction</li>
 * </ul>
 * <p>
 * <b>Retry Strategy:</b>
 * <p>
 * For optimistic locking conflicts, you can configure automatic retries:
 * <pre>
 * {@code
 * @EventSourcingTransactional(
 *     retryOnConcurrencyConflict = true,
 *     maxRetries = 3,
 *     retryDelay = 100
 * )
 * public Mono<Account> withdraw(UUID accountId, BigDecimal amount) {
 *     // Will retry up to 3 times if version conflict occurs
 * }
 * }
 * </pre>
 * <p>
 * <b>Comparison with @Transactional:</b>
 * <table border="1">
 *   <tr>
 *     <th>Feature</th>
 *     <th>@Transactional</th>
 *     <th>@EventSourcingTransactional</th>
 *   </tr>
 *   <tr>
 *     <td>Database Transaction</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <td>Optimistic Locking</td>
 *     <td>Manual</td>
 *     <td>Automatic (version checking)</td>
 *   </tr>
 *   <tr>
 *     <td>Event Publishing</td>
 *     <td>Manual</td>
 *     <td>Automatic (Transactional Outbox)</td>
 *   </tr>
 *   <tr>
 *     <td>Concurrency Retry</td>
 *     <td>Manual</td>
 *     <td>Configurable automatic retry</td>
 *   </tr>
 * </table>
 *
 * @see org.springframework.transaction.annotation.Transactional
 * @see org.fireflyframework.eventsourcing.store.EventStore
 * @see org.fireflyframework.eventsourcing.store.ConcurrencyException
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventSourcingTransactional {

    /**
     * Transaction propagation behavior.
     * <p>
     * Defines how this transaction interacts with existing transactions.
     *
     * @return the propagation behavior
     */
    Propagation propagation() default Propagation.REQUIRED;

    /**
     * Whether to publish events to external systems after successful commit.
     * <p>
     * When true, implements the Transactional Outbox pattern:
     * <ol>
     *   <li>Events saved to event store in transaction</li>
     *   <li>Transaction committed</li>
     *   <li>Events published to message broker</li>
     * </ol>
     * <p>
     * When false, events are only stored, not published.
     *
     * @return true to publish events after commit, defaults to true
     */
    boolean publishEvents() default true;

    /**
     * Whether to automatically retry on concurrency conflicts.
     * <p>
     * When true, if a {@link org.fireflyframework.eventsourcing.store.ConcurrencyException}
     * occurs, the operation will be retried up to {@link #maxRetries()} times.
     *
     * @return true to enable automatic retry on conflicts, defaults to false
     */
    boolean retryOnConcurrencyConflict() default false;

    /**
     * Maximum number of retry attempts on concurrency conflicts.
     * <p>
     * Only used when {@link #retryOnConcurrencyConflict()} is true.
     *
     * @return maximum retry attempts, defaults to 3
     */
    int maxRetries() default 3;

    /**
     * Delay in milliseconds between retry attempts.
     * <p>
     * Only used when {@link #retryOnConcurrencyConflict()} is true.
     * Uses exponential backoff: delay * (2 ^ attemptNumber)
     *
     * @return retry delay in milliseconds, defaults to 100ms
     */
    long retryDelay() default 100;

    /**
     * Timeout for the transaction in seconds.
     * <p>
     * If the transaction takes longer than this, it will be rolled back.
     * Use -1 for no timeout (default).
     *
     * @return timeout in seconds, -1 for no timeout
     */
    int timeout() default -1;

    /**
     * Whether this transaction is read-only.
     * <p>
     * Read-only transactions can be optimized by the database and
     * will fail if any write operations are attempted.
     *
     * @return true for read-only transaction, defaults to false
     */
    boolean readOnly() default false;

    /**
     * Transaction isolation level.
     * <p>
     * Defines the data visibility between concurrent transactions.
     * Higher isolation levels provide more consistency but less concurrency.
     *
     * @return the isolation level, defaults to DEFAULT (database default)
     */
    Isolation isolation() default Isolation.DEFAULT;

    /**
     * Defines zero or more exception classes that must cause rollback.
     * <p>
     * By default, a transaction will be rolled back on {@link RuntimeException}
     * and {@link Error} but not on checked exceptions.
     * <p>
     * This can be overridden by specifying specific exception classes.
     *
     * @return array of exception classes that trigger rollback
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * Defines zero or more exception class names that must cause rollback.
     * <p>
     * Alternative to {@link #rollbackFor()} for specifying exceptions by name.
     *
     * @return array of exception class names that trigger rollback
     */
    String[] rollbackForClassName() default {};

    /**
     * Defines zero or more exception classes that must NOT cause rollback.
     * <p>
     * By default, a transaction will be rolled back on {@link RuntimeException}
     * and {@link Error}. This can be overridden to prevent rollback for specific exceptions.
     *
     * @return array of exception classes that should not trigger rollback
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

    /**
     * Defines zero or more exception class names that must NOT cause rollback.
     * <p>
     * Alternative to {@link #noRollbackFor()} for specifying exceptions by name.
     *
     * @return array of exception class names that should not trigger rollback
     */
    String[] noRollbackForClassName() default {};

    /**
     * Optional qualifier for the transaction manager to use.
     * <p>
     * If not specified, the default transaction manager will be used.
     *
     * @return the qualifier value for the transaction manager bean
     */
    String transactionManager() default "";

    /**
     * Transaction isolation levels.
     * <p>
     * Defines how changes made by one transaction are visible to other concurrent transactions.
     */
    enum Isolation {
        /**
         * Use the default isolation level of the underlying datastore.
         * <p>
         * For PostgreSQL, this is typically READ_COMMITTED.
         */
        DEFAULT,

        /**
         * Dirty reads, non-repeatable reads, and phantom reads can occur.
         * <p>
         * Lowest isolation level, highest concurrency.
         * Not supported by all databases.
         */
        READ_UNCOMMITTED,

        /**
         * Dirty reads are prevented; non-repeatable reads and phantom reads can occur.
         * <p>
         * This is the default for most databases including PostgreSQL.
         */
        READ_COMMITTED,

        /**
         * Dirty reads and non-repeatable reads are prevented; phantom reads can occur.
         * <p>
         * Provides snapshot isolation within a transaction.
         */
        REPEATABLE_READ,

        /**
         * Dirty reads, non-repeatable reads, and phantom reads are prevented.
         * <p>
         * Highest isolation level, lowest concurrency.
         * Transactions execute as if they were serialized.
         */
        SERIALIZABLE
    }

    /**
     * Transaction propagation types.
     */
    enum Propagation {
        /**
         * Support a current transaction; create a new one if none exists.
         * This is the default.
         */
        REQUIRED,

        /**
         * Create a new transaction, suspending the current transaction if one exists.
         */
        REQUIRES_NEW,

        /**
         * Support a current transaction; throw an exception if no current transaction exists.
         */
        MANDATORY,

        /**
         * Do not support a current transaction; throw an exception if a current transaction exists.
         */
        NEVER,

        /**
         * Support a current transaction; execute non-transactionally if none exists.
         */
        SUPPORTS,

        /**
         * Do not support a current transaction; rather always execute non-transactionally.
         */
        NOT_SUPPORTED
    }
}

