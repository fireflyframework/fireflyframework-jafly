# Migration Guide: fireflyframework-starter-domain v2.0

## Overview

Version 2.0 introduces significant architectural changes to improve modularity and separation of concerns:

1. **CQRS Extraction**: CQRS functionality moved to `fireflyframework-cqrs`
2. **Event Infrastructure Migration**: Event publishing/consumption moved to `fireflyframework-eda`
3. **SAGA Integration**: Simplified to focus on bridging step events to EDA infrastructure

This separation provides:
- **Better modularity**: Use CQRS, events, and SAGA independently
- **Clearer responsibilities**: Each library has a focused scope
- **Improved maintainability**: Easier to maintain and extend
- **Easier testing**: Test each concern separately

## What Changed

### 1. CQRS Migration (to fireflyframework-cqrs)

CQRS classes have moved from `org.fireflyframework.domain.*` to `org.fireflyframework.cqrs.*`:

| Old Package | New Package |
|------------|-------------|
| `org.fireflyframework.domain.command.*` | `org.fireflyframework.cqrs.command.*` |
| `org.fireflyframework.domain.query.*` | `org.fireflyframework.cqrs.query.*` |
| `org.fireflyframework.domain.authorization.*` | `org.fireflyframework.cqrs.authorization.*` |
| `org.fireflyframework.domain.validation.*` | `org.fireflyframework.cqrs.validation.*` |
| `org.fireflyframework.domain.annotations.*` | `org.fireflyframework.cqrs.annotations.*` |
| `org.fireflyframework.domain.config.Cqrs*` | `org.fireflyframework.cqrs.config.*` |

### 2. Event Infrastructure Migration (to fireflyframework-eda)

All event publishing and consumption functionality has been removed from `fireflyframework-starter-domain` and should now use `fireflyframework-eda`:

| Removed from fireflyframework-starter-domain | Use Instead (fireflyframework-eda) |
|-------------------------------|------------------------------|
| `DomainEventPublisher` | `EventPublisher` |
| `DomainEvent` interface | Plain POJOs with `Event<T>` wrapper |
| `@EventHandler` annotation | `@EventListener` with `Event<T>` parameter |
| Event filtering infrastructure | EDA filtering capabilities |
| Messaging platform adapters | EDA platform adapters |
| Event health indicators | EDA health indicators |

### 3. SAGA Integration Changes

The SAGA integration has been simplified:
- **Removed**: Direct SAGA orchestration (use `fireflyframework-orchestration`)
- **Removed**: `StepEventPublisherBridge` (orchestration engine provides its own EventGateway)
- **Changed**: Now uses `fireflyframework-eda` for event publishing instead of internal infrastructure

## Maven Dependencies

**Before (v1.x):**
```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-domain</artifactId>
    <version>1.x.x</version>
</dependency>
```

**After (v2.0):**
```xml
<!-- Core domain starter with CQRS and SAGA integration -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-domain</artifactId>
    <version>2.0.0-SNAPSHOT</version>
    <!-- fireflyframework-cqrs is included transitively -->
</dependency>

<!-- Event-driven architecture (required for event publishing) -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- SAGA orchestration (optional, only if using SAGAs) -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-orchestration</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Migration Steps

### Step 1: Update Maven Dependencies

Add the new dependencies to your `pom.xml`:

```xml
<!-- Add fireflyframework-eda for event publishing -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Add fireflyframework-orchestration if using SAGAs -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-orchestration</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2: Update CQRS Imports

Update all CQRS-related imports in your codebase:

```java
// OLD imports
import org.fireflyframework.domain.command.*;
import org.fireflyframework.domain.query.*;
import org.fireflyframework.domain.authorization.*;
import org.fireflyframework.domain.annotations.*;

// NEW imports
import org.fireflyframework.cqrs.command.*;
import org.fireflyframework.cqrs.query.*;
import org.fireflyframework.cqrs.authorization.*;
import org.fireflyframework.cqrs.annotations.*;
```

### Step 3: Migrate Event Publishing Code

Replace `DomainEventPublisher` with `EventPublisher` from fireflyframework-eda:

**Before:**
```java
@Service
public class AccountService {
    private final DomainEventPublisher eventPublisher;

    private Mono<Account> publishEvent(Account account) {
        AccountCreatedEvent event = new AccountCreatedEvent(account.getId());
        return eventPublisher.publish(event).thenReturn(account);
    }
}
```

**After:**
```java
@Service
public class AccountService {
    private final EventPublisher eventPublisher;

    private Mono<Account> publishEvent(Account account) {
        AccountCreatedEvent event = new AccountCreatedEvent(account.getId());

        Map<String, Object> headers = Map.of(
            "event_type", "account.created",
            "aggregate_id", account.getId()
        );

        return eventPublisher.publish(event, "account-events", headers)
            .thenReturn(account);
    }
}
```

### Step 4: Migrate Event Listeners

Replace `@EventHandler` with `@EventListener` and use `Event<T>` wrapper:

**Before:**
```java
@Component
public class AccountEventHandler {
    @EventHandler(eventType = "account.created")
    public Mono<Void> handleAccountCreated(AccountCreatedEvent event) {
        // Handle event
        return Mono.empty();
    }
}
```

**After:**
```java
@Component
public class AccountEventHandler {
    @EventListener
    public Mono<Void> handleAccountCreated(Event<AccountCreatedEvent> event) {
        AccountCreatedEvent payload = event.getPayload();
        // Handle event
        return Mono.empty();
    }
}
```

### Step 5: Update Configuration Properties

**Before:**
```yaml
firefly:
  events:
    enabled: true
    adapter: KAFKA
    default-topic: domain.events
    kafka:
      bootstrap-servers: localhost:9092

  step-events:
    enabled: true
    publisher-type: KAFKA
    topic-name: saga-steps
```

**After:**
```yaml
firefly:
  # Event publishing configuration (fireflyframework-eda)
  eda:
    enabled: true
    default-publisher-type: KAFKA
    kafka:
      bootstrap-servers: localhost:9092

  # SAGA step events configuration
  stepevents:
    enabled: true
    topic: saga-step-events
```

### Step 6: Verify Tests

Run your tests to ensure everything still works:

```bash
mvn clean test
```

### Step 7: Update Documentation References

Update any internal documentation or comments that reference:
- Domain events in fireflyframework-starter-domain → point to fireflyframework-eda
- CQRS in fireflyframework-starter-domain → point to fireflyframework-cqrs
- SAGA orchestration → point to fireflyframework-orchestration

## Compatibility

### Backward Compatibility

- **CQRS Configuration**: All CQRS configuration properties remain unchanged
- **CQRS Functionality**: All CQRS features work exactly as before
- **Integration**: fireflyframework-starter-domain still depends on fireflyframework-cqrs transitively

### Breaking Changes

#### 1. Package Names
- **CQRS imports**: Must update from `org.fireflyframework.domain.*` to `org.fireflyframework.cqrs.*`

#### 2. Event Publishing API
- **DomainEventPublisher removed**: Use `EventPublisher` from fireflyframework-eda
- **DomainEvent interface removed**: Use plain POJOs
- **@EventHandler removed**: Use `@EventListener` with `Event<T>` parameter
- **Event filtering**: Use fireflyframework-eda filtering capabilities

#### 3. Configuration Properties
- **firefly.events.*** removed**: Use `firefly.eda.*` from fireflyframework-eda
- **firefly.step-events.*** renamed**: Use `firefly.stepevents.*`

#### 4. Dependencies
- **fireflyframework-eda required**: Must add explicitly for event publishing
- **fireflyframework-orchestration required**: Must add explicitly for SAGA orchestration

#### 5. Removed Components
- All domain event infrastructure (publishers, listeners, filters, adapters)
- Event health indicators (moved to fireflyframework-eda)
- Event metrics (moved to fireflyframework-eda)
- Messaging platform adapters (moved to fireflyframework-eda)

## Complete Migration Examples

### Example 1: CQRS Handler Migration

**Before:**
```java
package com.example.banking.commands;

import org.fireflyframework.domain.command.Command;
import org.fireflyframework.domain.command.CommandHandler;
import org.fireflyframework.domain.annotations.CommandHandlerComponent;

@CommandHandlerComponent
public class CreateAccountHandler extends CommandHandler<CreateAccountCommand, AccountResult> {
    @Override
    protected Mono<AccountResult> doHandle(CreateAccountCommand command) {
        return processCommand(command);
    }
}
```

**After:**
```java
package com.example.banking.commands;

import org.fireflyframework.cqrs.command.Command;           // Changed
import org.fireflyframework.cqrs.command.CommandHandler;    // Changed
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent; // Changed

@CommandHandlerComponent
public class CreateAccountHandler extends CommandHandler<CreateAccountCommand, AccountResult> {
    @Override
    protected Mono<AccountResult> doHandle(CreateAccountCommand command) {
        return processCommand(command);  // Business logic unchanged
    }
}
```

### Example 2: Event Publishing Migration

**Before:**
```java
@Service
public class OrderService {
    private final DomainEventPublisher eventPublisher;

    public Mono<Order> createOrder(CreateOrderCommand command) {
        return processOrder(command)
            .flatMap(this::publishOrderCreatedEvent);
    }

    private Mono<Order> publishOrderCreatedEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );

        return eventPublisher.publish(event)
            .thenReturn(order);
    }
}
```

**After:**
```java
@Service
public class OrderService {
    private final EventPublisher eventPublisher;  // Changed to fireflyframework-eda

    public Mono<Order> createOrder(CreateOrderCommand command) {
        return processOrder(command)
            .flatMap(this::publishOrderCreatedEvent);
    }

    private Mono<Order> publishOrderCreatedEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );

        // Now requires topic and headers
        Map<String, Object> headers = Map.of(
            "event_type", "order.created",
            "aggregate_id", order.getId(),
            "source", "order-service"
        );

        return eventPublisher.publish(event, "order-events", headers)
            .thenReturn(order);
    }
}
```

### Example 3: Event Listener Migration

**Before:**
```java
@Component
public class OrderEventHandler {

    @EventHandler(eventType = "order.created")
    public Mono<Void> handleOrderCreated(OrderCreatedEvent event) {
        return notificationService.sendConfirmation(
            event.getCustomerId(),
            event.getOrderId()
        );
    }
}
```

**After:**
```java
@Component
public class OrderEventHandler {

    @EventListener  // Changed to Spring's @EventListener
    public Mono<Void> handleOrderCreated(Event<OrderCreatedEvent> event) {
        OrderCreatedEvent payload = event.getPayload();  // Extract payload

        return notificationService.sendConfirmation(
            payload.getCustomerId(),
            payload.getOrderId()
        );
    }
}
```

### Example 4: SAGA Step Event Configuration

**Before:**
```yaml
firefly:
  step-events:
    enabled: true
    publisher-type: KAFKA
    topic-name: saga-steps
    include-metadata: true

  events:
    adapter: KAFKA
    kafka:
      bootstrap-servers: localhost:9092
```

**After:**
```yaml
firefly:
  stepevents:
    enabled: true
    topic: saga-step-events

  eda:
    enabled: true
    default-publisher-type: KAFKA
    kafka:
      bootstrap-servers: localhost:9092
```

## Automated Migration Scripts

### Script 1: Update CQRS Imports

```bash
#!/bin/bash
# update-cqrs-imports.sh

find . -type f -name "*.java" -exec sed -i '' \
  -e 's/import com\.firefly\.common\.domain\.command\./import org.fireflyframework.cqrs.command./g' \
  -e 's/import com\.firefly\.common\.domain\.query\./import org.fireflyframework.cqrs.query./g' \
  -e 's/import com\.firefly\.common\.domain\.authorization\./import org.fireflyframework.cqrs.authorization./g' \
  -e 's/import com\.firefly\.common\.domain\.validation\./import org.fireflyframework.cqrs.validation./g' \
  -e 's/import com\.firefly\.common\.domain\.annotations\./import org.fireflyframework.cqrs.annotations./g' \
  -e 's/import com\.firefly\.common\.domain\.tracing\./import org.fireflyframework.cqrs.tracing./g' \
  -e 's/import com\.firefly\.common\.domain\.context\./import org.fireflyframework.cqrs.context./g' \
  {} \;

echo "CQRS import statements updated!"
```

### Script 2: Update Event Publishing Imports

```bash
#!/bin/bash
# update-event-imports.sh

find . -type f -name "*.java" -exec sed -i '' \
  -e 's/import com\.firefly\.common\.domain\.events\.DomainEventPublisher/import org.fireflyframework.eda.publisher.EventPublisher/g' \
  -e 's/import com\.firefly\.common\.domain\.events\.DomainEvent/\/\/ MANUAL MIGRATION NEEDED: Remove DomainEvent interface/g' \
  -e 's/@EventHandler/@EventListener \/\/ MANUAL MIGRATION NEEDED: Update method signature to use Event<T>/g' \
  {} \;

echo "Event import statements updated!"
echo "WARNING: Manual migration needed for event handlers and DomainEvent implementations"
```

### Script 3: Update Configuration Files

```bash
#!/bin/bash
# update-config.sh

find . -type f -name "application*.yml" -exec sed -i '' \
  -e 's/firefly\.events\./firefly.eda./g' \
  -e 's/firefly\.step-events\./firefly.stepevents./g' \
  -e 's/publisher-type:/default-publisher-type:/g' \
  -e 's/topic-name:/topic:/g' \
  {} \;

echo "Configuration files updated!"
```

### Run All Scripts

```bash
chmod +x update-cqrs-imports.sh update-event-imports.sh update-config.sh
./update-cqrs-imports.sh
./update-event-imports.sh
./update-config.sh

echo "Automated migration complete!"
echo "Please review changes and complete manual migration steps"
echo "Run tests: mvn clean test"
```

## Manual Migration Checklist

After running automated scripts, complete these manual steps:

- [ ] Update `pom.xml` to add `fireflyframework-eda` dependency
- [ ] Update `pom.xml` to add `fireflyframework-orchestration` dependency (if using SAGAs)
- [ ] Review all event publishing code and add topic + headers parameters
- [ ] Update event listener methods to use `Event<T>` wrapper
- [ ] Remove `DomainEvent` interface implementations from event classes
- [ ] Update configuration files (`application.yml`, `application-*.yml`)
- [ ] Update test configuration files
- [ ] Review and update integration tests
- [ ] Run `mvn clean test` to verify all tests pass
- [ ] Update internal documentation and comments
- [ ] Review health check endpoints and metrics

## Support

If you encounter any issues during migration:

1. Check the [fireflyframework-starter-domain README](README.md)
2. Check the [fireflyframework-cqrs README](../fireflyframework-cqrs/README.md)
3. Check the [fireflyframework-eda README](../fireflyframework-eda/README.md)
4. Check the [fireflyframework-orchestration README](../fireflyframework-orchestration/README.md)
5. Contact the Firefly Platform Team

## What's in fireflyframework-starter-domain v2.0

The starter now focuses on:

- **CQRS Framework** (via fireflyframework-cqrs dependency):
  - CommandBus and QueryBus
  - Handler auto-discovery
  - Validation and authorization
  - ExecutionContext and multi-tenancy

- **SAGA Integration**:
  - Integration with fireflyframework-orchestration (orchestration engine handles event publishing internally via EventGateway)

- **Observability**:
  - JSON logging configuration
  - Metrics collection
  - Health indicators
  - Distributed tracing

## What Moved to Other Libraries

- **Event Publishing** → `fireflyframework-eda`
  - EventPublisher interface
  - Multi-platform support (Kafka, RabbitMQ, SQS, Kinesis)
  - Event listeners and handlers
  - Resilience patterns for events
  - Event health indicators and metrics

- **Service Communication** → `fireflyframework-client`
  - ServiceClient framework
  - REST and gRPC support
  - Circuit breakers and retries
  - Health checks

- **SAGA Orchestration** → `fireflyframework-orchestration`
  - Saga, TCC, and Workflow orchestration engine
  - Step definitions
  - Compensation logic
  - Transaction management
  - EventGateway for event publishing
