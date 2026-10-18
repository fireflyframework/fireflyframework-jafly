# Quick Start

Build your first event-sourced application with Firefly in six steps.

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL running locally (or use Testcontainers)
- A Spring Boot 3.x project

## Step 1: Add Dependency

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eventsourcing</artifactId>
    <version>26.02.06</version>
</dependency>
```

The library brings in R2DBC, Flyway, Jackson, and Spring Boot Actuator transitively.

## Step 2: Configure R2DBC and Flyway

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/myapp
    username: postgres
    password: postgres

  flyway:
    enabled: true
    url: jdbc:postgresql://localhost:5432/myapp
    user: postgres
    password: postgres
    locations: classpath:db/migration

firefly:
  eventsourcing:
    enabled: true
    event-scan-packages: "com.example.myapp"
    store:
      type: r2dbc
    snapshot:
      enabled: true
      threshold: 50
    publisher:
      enabled: false
```

Flyway requires a JDBC URL (it does not support R2DBC). The library ships with 8 migration scripts that create the `events`, `snapshots`, `event_outbox`, and `projection_positions` tables automatically.

The `event-scan-packages` property tells the `EventTypeRegistry` where to scan for `@DomainEvent` classes. The default is `org.fireflyframework`.

## Step 3: Define Domain Events

Create events that extend `AbstractDomainEvent` and are annotated with `@DomainEvent`:

```java
package com.example.myapp.events;

import org.fireflyframework.eventsourcing.annotation.DomainEvent;
import org.fireflyframework.eventsourcing.domain.AbstractDomainEvent;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

@DomainEvent("task.created")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreatedEvent extends AbstractDomainEvent {
    private String title;
    private String description;
    private String assignee;
}

@DomainEvent("task.completed")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompletedEvent extends AbstractDomainEvent {
    private String completedBy;
}
```

The `@DomainEvent` annotation does two things:
1. Sets the event type identifier used for serialization (e.g., `"task.created"`)
2. Bridges to `@JsonTypeName` so Jackson can deserialize events polymorphically

The `@SuperBuilder` annotation from Lombok enables the builder pattern, including the metadata helpers inherited from `AbstractDomainEvent` (`.correlationId(...)`, `.userId(...)`, `.source(...)`).

## Step 4: Create the Aggregate

```java
package com.example.myapp.aggregate;

import com.example.myapp.events.*;
import org.fireflyframework.eventsourcing.aggregate.AggregateRoot;
import lombok.Getter;
import java.util.UUID;

@Getter
public class Task extends AggregateRoot {

    private String title;
    private String description;
    private String assignee;
    private boolean completed;

    // Constructor for loading from event store
    public Task(UUID id) {
        super(id, "Task");
    }

    // Constructor for creating a new task (command)
    public Task(UUID id, String title, String description, String assignee) {
        super(id, "Task");

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        applyChange(TaskCreatedEvent.builder()
                .aggregateId(id)
                .title(title)
                .description(description)
                .assignee(assignee)
                .build());
    }

    // Command method
    public void complete(String completedBy) {
        if (completed) {
            throw new IllegalStateException("Task is already completed");
        }

        applyChange(TaskCompletedEvent.builder()
                .aggregateId(getId())
                .completedBy(completedBy)
                .build());
    }

    // Event handlers (private, named "on", single event parameter)
    private void on(TaskCreatedEvent event) {
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.assignee = event.getAssignee();
        this.completed = false;
    }

    private void on(TaskCompletedEvent event) {
        this.completed = true;
    }
}
```

Key rules:
- Call `super(id, "AggregateType")` in every constructor. The version starts at `-1`.
- Use `applyChange(event)` to produce new events. This calls the handler and increments the version.
- Event handler methods must be named `on`, accept a single event parameter, and can be `private`.
- Command methods validate business rules; event handlers only update state.

## Step 5: Create the Service

```java
package com.example.myapp.service;

import com.example.myapp.aggregate.Task;
import org.fireflyframework.eventsourcing.store.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final EventStore eventStore;

    public Mono<Task> createTask(String title, String description, String assignee) {
        UUID taskId = UUID.randomUUID();
        Task task = new Task(taskId, title, description, assignee);

        // expectedVersion = -1 because this is a new aggregate
        return eventStore.appendEvents(
                taskId, "Task", task.getUncommittedEvents(), -1L
            )
            .doOnSuccess(stream -> task.markEventsAsCommitted())
            .thenReturn(task);
    }

    public Mono<Task> completeTask(UUID taskId, String completedBy) {
        return loadTask(taskId)
            .doOnNext(task -> task.complete(completedBy))
            .flatMap(task -> eventStore.appendEvents(
                    taskId, "Task", task.getUncommittedEvents(),
                    task.getCurrentVersion() - task.getUncommittedEventCount()
                )
                .doOnSuccess(stream -> task.markEventsAsCommitted())
                .thenReturn(task));
    }

    public Mono<Task> getTask(UUID taskId) {
        return loadTask(taskId);
    }

    private Mono<Task> loadTask(UUID taskId) {
        return eventStore.loadEventStream(taskId, "Task")
            .map(stream -> {
                Task task = new Task(taskId);
                task.loadFromHistory(stream.getEvents());
                return task;
            });
    }
}
```

The `expectedVersion` for `appendEvents`:
- Use `-1L` when creating a new aggregate (no events exist yet)
- Use `task.getCurrentVersion() - task.getUncommittedEventCount()` when updating an existing aggregate (this gives the version before the new events were applied)

## Step 6: Run and Verify

Start your application. Flyway will create the database tables. The `EventTypeRegistry` will scan for `@DomainEvent` classes and register them with Jackson.

Test with a REST controller or a test:

```java
@SpringBootTest
class TaskServiceTest {

    @Autowired
    TaskService taskService;

    @Test
    void createAndCompleteTask() {
        Task task = taskService.createTask("Write docs", "Write the quick start guide", "dev-1")
            .block();

        assertThat(task.getTitle()).isEqualTo("Write docs");
        assertThat(task.getCurrentVersion()).isEqualTo(0L); // one event applied, version = 0

        Task completed = taskService.completeTask(task.getId(), "dev-1").block();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getCurrentVersion()).isEqualTo(1L); // two events total, version = 1
    }
}
```

## Next Steps

- [Architecture](architecture.md) -- understand the system design
- [API Reference](api-reference.md) -- explore the full API
- [Account Ledger Tutorial](tutorial-account-ledger.md) -- see a complete example with snapshots and projections
- [Configuration](configuration.md) -- tune all properties
