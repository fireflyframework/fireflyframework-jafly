# Quick Start Guide

**Firefly Common Core Library**  
*Copyright (c) 2025 Firefly Software Foundation*  
*Licensed under the Apache License, Version 2.0*

Quick start guide for the Firefly Common Core library - infrastructure components for the **Firefly Framework**.

## Prerequisites

- **Java**: 21 or higher
- **Spring Boot**: 3.2.2 or compatible version
- **Maven**: 3.8+ or **Gradle**: 8.0+
- **Message Broker** (optional): Kafka, RabbitMQ, Redis, or any supported provider

## Step 1: Add Dependency

### Maven

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Optional: Add specific messaging providers -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### Gradle

Add to your `build.gradle`:

```gradle
implementation 'org.fireflyframework:fireflyframework-starter-core:1.0.0-SNAPSHOT'

// Optional: Add specific messaging providers
implementation 'org.springframework.kafka:spring-kafka'
```

## Step 2: Basic Configuration

Create `application.yml` in `src/main/resources`:

```yaml
# Minimal configuration to get started
spring:
  application:
    name: my-service

# Enable core messaging
messaging:
  enabled: true
  
# Enable actuator endpoints  
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics"
  endpoint:
    health:
      show-details: always
```

## Step 3: Your First Message Publisher

Create a simple REST controller that publishes messages:

```java
package com.example.demo;

import org.fireflyframework.core.messaging.annotation.PublishResult;
import org.fireflyframework.core.messaging.annotation.PublisherType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/events")
public class EventController {
    
    @PostMapping("/user-created")
    @PublishResult(
        publisherType = PublisherType.EVENT_BUS,  // Uses Spring's internal event bus
        eventType = "user.created"
    )
    public Mono<UserCreatedEvent> createUser(@RequestBody CreateUserRequest request) {
        // Your business logic here
        UserCreatedEvent event = new UserCreatedEvent(
            request.getUserId(), 
            request.getEmail(),
            java.time.Instant.now()
        );
        
        // The event will be automatically published after this method returns
        return Mono.just(event);
    }
}

// Request DTO
record CreateUserRequest(String userId, String email) {}

// Event DTO - this will be published
record UserCreatedEvent(String userId, String email, java.time.Instant timestamp) {}
```

## Step 4: Handle Events

Create an event handler to process published events:

```java
package com.example.demo;

import org.fireflyframework.core.messaging.annotation.EventListener;
import org.fireflyframework.core.messaging.annotation.EventPayload;
import org.fireflyframework.core.messaging.annotation.EventHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
public class UserEventHandler {
    
    @EventListener
    public Mono<Void> handleUserCreated(@EventPayload UserCreatedEvent event,
                                       @EventHeaders Map<String, Object> headers) {
        System.out.printf("User created: %s (%s) at %s%n", 
            event.userId(), 
            event.email(), 
            event.timestamp());
            
        // Process the event (e.g., send welcome email, update analytics, etc.)
        return Mono.empty();
    }
}
```

## Step 5: Run Your Application

Create your main application class:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

Run the application:

```bash
./mvnw spring-boot:run
```

## Step 6: Test Your Setup

Test the event publishing:

```bash
# Create a user and publish event
curl -X POST http://localhost:8080/api/events/user-created \
  -H "Content-Type: application/json" \
  -d '{"userId": "user123", "email": "test@example.com"}'

# Check health status
curl http://localhost:8080/actuator/health

# Check metrics
curl http://localhost:8080/actuator/metrics
```

You should see:
1. The event published successfully
2. The event handler processing the event (console output)
3. Health endpoints responding with status information

## Step 7: Add External Messaging (Optional)

### Add Kafka Support

Update `application.yml`:

```yaml
messaging:
  enabled: true
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
    default-topic: user-events
```

Update your controller to use Kafka:

```java
@PostMapping("/user-created-kafka")
@PublishResult(
    publisherType = PublisherType.KAFKA,
    destination = "user-events",
    eventType = "user.created"
)
public Mono<UserCreatedEvent> createUserWithKafka(@RequestBody CreateUserRequest request) {
    UserCreatedEvent event = new UserCreatedEvent(
        request.getUserId(), 
        request.getEmail(),
        java.time.Instant.now()
    );
    return Mono.just(event);
}
```

### Add Multiple Providers

```yaml
messaging:
  enabled: true
  
  # Kafka configuration
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
    default-topic: events
  
  # RabbitMQ configuration
  rabbitmq:
    enabled: true
    host: localhost
    port: 5672
    username: guest
    password: guest
    default-exchange: events
  
  # Redis configuration
  redis:
    enabled: true
    host: localhost
    port: 6379
    default-channel: events
```

Use different publishers for different endpoints:

```java
@PostMapping("/kafka-events")
@PublishResult(publisherType = PublisherType.KAFKA, destination = "kafka-topic")
public Mono<Event> publishToKafka(@RequestBody Event event) {
    return Mono.just(event);
}

@PostMapping("/rabbitmq-events")
@PublishResult(publisherType = PublisherType.RABBITMQ, destination = "rabbitmq-exchange")
public Mono<Event> publishToRabbitMQ(@RequestBody Event event) {
    return Mono.just(event);
}

@PostMapping("/redis-events")
@PublishResult(publisherType = PublisherType.REDIS, destination = "redis-channel")
public Mono<Event> publishToRedis(@RequestBody Event event) {
    return Mono.just(event);
}
```

## Step 8: Add Observability

### Enable Prometheus Metrics

Update `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      service: ${spring.application.name}
      environment: ${spring.profiles.active:default}
```

### Add Custom Metrics

```java
@Component
public class CustomMetrics {
    private final Counter eventCounter;
    private final Timer eventProcessingTime;
    
    public CustomMetrics(MeterRegistry meterRegistry) {
        this.eventCounter = Counter.builder("events.published")
            .description("Number of events published")
            .tag("service", "demo")
            .register(meterRegistry);
            
        this.eventProcessingTime = Timer.builder("events.processing.time")
            .description("Time to process events")
            .register(meterRegistry);
    }
    
    public void recordEventPublished() {
        eventCounter.increment();
    }
    
    public Timer.Sample startTimer() {
        return Timer.start(eventProcessingTime);
    }
}
```

Use metrics in your handlers:

```java
@Component
public class UserEventHandler {
    
    private final CustomMetrics metrics;
    
    public UserEventHandler(CustomMetrics metrics) {
        this.metrics = metrics;
    }
    
    @EventListener
    public Mono<Void> handleUserCreated(@EventPayload UserCreatedEvent event,
                                       @EventHeaders Map<String, Object> headers) {
        Timer.Sample sample = metrics.startTimer();
        
        return processEvent(event)
            .doFinally(signal -> {
                sample.stop();
                metrics.recordEventPublished();
            });
    }
    
    private Mono<Void> processEvent(UserCreatedEvent event) {
        // Your event processing logic
        return Mono.empty();
    }
}
```

## Step 9: Add Health Checks

Create custom health indicators:

```java
@Component
public class MessagingHealthIndicator implements HealthIndicator {
    
    private final EventPublisherFactory publisherFactory;
    
    public MessagingHealthIndicator(EventPublisherFactory publisherFactory) {
        this.publisherFactory = publisherFactory;
    }
    
    @Override
    public Health health() {
        try {
            // Check if default publisher is available
            EventPublisher publisher = publisherFactory.createPublisher(
                PublisherType.EVENT_BUS, "default");
            
            boolean isHealthy = publisher.isAvailable();
            
            return isHealthy ? 
                Health.up().withDetail("messaging", "Available").build() :
                Health.down().withDetail("messaging", "Not Available").build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("messaging", "Error: " + e.getMessage())
                .build();
        }
    }
}
```

## Step 10: Add Configuration Management (Optional)

### Enable Spring Cloud Config

Add dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

Update `application.yml`:

```yaml
spring:
  application:
    name: my-service
  config:
    import: "configserver:http://config-server:8888"

cloud:
  config:
    enabled: true
    uri: http://config-server:8888
    fail-fast: false
    retry:
      enabled: true
      max-attempts: 3
```

### Enable Service Discovery

Add Eureka dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Update configuration:

```yaml
service:
  registry:
    enabled: true
    type: EUREKA
    eureka:
      service-url: http://eureka-server:8761/eureka/
      register: true
      fetch-registry: true
      health-check-enabled: true

eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    prefer-ip-address: true
```

## Complete Example Application

Here's a complete working example combining all features:

```java
@SpringBootApplication
@EnableEurekaClient  // If using Eureka
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api")
public class ApiController {
    
    private final CustomMetrics metrics;
    
    public ApiController(CustomMetrics metrics) {
        this.metrics = metrics;
    }
    
    @PostMapping("/orders")
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        destination = "order-events",
        eventType = "order.created"
    )
    public Mono<OrderCreatedEvent> createOrder(@RequestBody CreateOrderRequest request) {
        metrics.recordEventPublished();
        
        OrderCreatedEvent event = new OrderCreatedEvent(
            request.orderId(),
            request.customerId(), 
            request.amount(),
            Instant.now()
        );
        
        return Mono.just(event);
    }
    
    @GetMapping("/health-check")
    public Mono<Map<String, String>> healthCheck() {
        return Mono.just(Map.of("status", "OK", "timestamp", Instant.now().toString()));
    }
}

@Component
public class OrderEventHandler {
    
    private final CustomMetrics metrics;
    
    public OrderEventHandler(CustomMetrics metrics) {
        this.metrics = metrics;
    }
    
    @EventListener
    public Mono<Void> handleOrderCreated(@EventPayload OrderCreatedEvent event) {
        Timer.Sample sample = metrics.startTimer();
        
        return processOrder(event)
            .doFinally(signal -> sample.stop())
            .then();
    }
    
    private Mono<Void> processOrder(OrderCreatedEvent event) {
        // Process order logic
        System.out.printf("Processing order: %s for customer: %s amount: %s%n",
            event.orderId(), event.customerId(), event.amount());
        return Mono.empty();
    }
}

// DTOs
record CreateOrderRequest(String orderId, String customerId, BigDecimal amount) {}
record OrderCreatedEvent(String orderId, String customerId, BigDecimal amount, Instant timestamp) {}
```

Complete `application.yml`:

```yaml
spring:
  application:
    name: order-service
  profiles:
    active: development

# Messaging configuration
messaging:
  enabled: true
  resilience: true
  publish-timeout-seconds: 5
  
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
    default-topic: order-events
    client-id: ${spring.application.name}
  
  serialization:
    default-format: JSON

# Service discovery
service:
  registry:
    enabled: true
    type: EUREKA
    eureka:
      service-url: http://localhost:8761/eureka/
      register: true
      fetch-registry: true

# Cloud configuration
cloud:
  config:
    enabled: false  # Set to true if using config server

# Web configuration  
server:
  port: 8080

# Actuator configuration
management:
  endpoints:
    web:
      exposure:
        include: "*"
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      service: ${spring.application.name}
      environment: ${spring.profiles.active}
  
  health:
    show-components: true

# Logging configuration
logging:
  level:
    org.fireflyframework.core: INFO
    org.springframework.kafka: WARN
    org.apache.kafka: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

## Next Steps

1. **Explore Advanced Features**:
   - [Configuration Reference](CONFIGURATION.md) - Detailed configuration options
   - [API Documentation](API.md) - Complete API reference
   - [Integrations Guide](INTEGRATIONS.md) - Advanced integration patterns

2. **Production Readiness**:
   - Configure multiple messaging providers for redundancy
   - Set up proper monitoring with Prometheus + Grafana
   - Enable distributed tracing with Zipkin
   - Configure proper security and authentication

3. **CQRS and Saga Integration**:
   - Add `fireflyframework-cqrs` for command/query separation
   - Add `fireflyframework-orchestration` for saga, TCC, and workflow orchestration
   - Implement complex business workflows

4. **Testing**:
   - Write integration tests with embedded messaging systems
   - Use `@SpringBootTest` with test profiles
   - Test resilience patterns with fault injection

This quick start guide gets you up and running with Firefly Common Core. The library provides extensive configuration options and advanced features that you can explore as your application grows in complexity.