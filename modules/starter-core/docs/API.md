# API Reference

**Firefly Common Core Library**  
*Copyright (c) 2025 Firefly Software Foundation*  
*Licensed under the Apache License, Version 2.0*

This document provides comprehensive API reference for all Firefly Common Core components with real method signatures and practical examples.

## Table of Contents

- [Messaging API](#messaging-api)
- [Configuration API](#configuration-api)
- [Service Discovery API](#service-discovery-api)
- [WebClient API](#webclient-api)
- [Actuator API](#actuator-api)
- [Orchestration Integration](#orchestration-integration)
- [Serialization API](#serialization-api)
- [Health API](#health-api)
- [Metrics API](#metrics-api)

## Messaging API

### Core Interfaces

#### EventPublisher Interface

```java
package org.fireflyframework.core.messaging.publisher;

public interface EventPublisher {
    /**
     * Publishes an event to the specified destination.
     * 
     * @param destination the destination to publish to (topic, queue, etc.)
     * @param eventType the type of event, used for routing or filtering
     * @param payload the event payload, which can be any object that can be serialized
     * @param transactionId the transaction ID for tracing (can be null)
     * @return a Mono that completes when the event is published successfully
     */
    Mono<Void> publish(String destination, String eventType, Object payload, String transactionId);
    
    /**
     * Publishes an event using the provided serializer.
     * 
     * @param destination the destination to publish to
     * @param eventType the type of event
     * @param payload the event payload
     * @param transactionId the transaction ID for tracing
     * @param serializer the serializer to use for converting the payload
     * @return a Mono that completes when the event is published
     */
    default Mono<Void> publish(String destination, String eventType, Object payload, 
                              String transactionId, MessageSerializer serializer) {
        return publish(destination, eventType, payload, transactionId);
    }
    
    /**
     * Checks if this publisher is available and properly configured.
     * 
     * @return true if the publisher is available and properly configured
     */
    boolean isAvailable();
}
```

#### ConnectionAwarePublisher Interface

```java
package org.fireflyframework.core.messaging.publisher;

public interface ConnectionAwarePublisher {
    /**
     * Sets the connection ID for this publisher.
     * 
     * @param connectionId the connection ID to use
     */
    void setConnectionId(String connectionId);
    
    /**
     * Gets the connection ID for this publisher.
     * 
     * @return the connection ID
     */
    String getConnectionId();
}
```

### Annotations

#### @PublishResult Annotation

```java
package org.fireflyframework.core.messaging.annotation;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublishResult {
    /**
     * The type of publisher to use for publishing the result.
     */
    PublisherType publisherType() default PublisherType.EVENT_BUS;
    
    /**
     * The destination to publish to (topic, queue, channel, etc.).
     * If empty, the default destination for the publisher type will be used.
     */
    String destination() default "";
    
    /**
     * The event type to use when publishing.
     * Used for routing and filtering on the consumer side.
     */
    String eventType() default "";
    
    /**
     * The connection ID to use for this publisher.
     * Allows using different configurations for the same publisher type.
     */
    String connectionId() default "default";
    
    /**
     * The serializer bean name to use for this publication.
     * If specified, this serializer will be used instead of the default.
     */
    String serializer() default "";
    
    /**
     * Whether to publish the result asynchronously.
     * If true, the method will not wait for the publication to complete.
     */
    boolean async() default true;
    
    /**
     * The timeout in milliseconds for the publication operation.
     * If 0, the default timeout will be used.
     */
    long timeoutMs() default 0;
}
```

#### @EventListener Annotation

```java
package org.fireflyframework.core.messaging.annotation;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
    /**
     * The event types this listener should handle.
     * If empty, the listener will handle all events.
     */
    String[] eventTypes() default {};
    
    /**
     * The topics or channels to listen on.
     * If empty, will listen on default channels.
     */
    String[] destinations() default {};
    
    /**
     * The connection ID to use for this listener.
     */
    String connectionId() default "default";
    
    /**
     * Error handling strategy for failed message processing.
     */
    ErrorHandlingStrategy errorStrategy() default ErrorHandlingStrategy.LOG_AND_CONTINUE;
    
    /**
     * Maximum number of retry attempts for failed messages.
     */
    int maxRetries() default 3;
    
    /**
     * Delay between retry attempts in milliseconds.
     */
    long retryDelayMs() default 1000;
}
```

### Publisher Types

```java
package org.fireflyframework.core.messaging.annotation;

public enum PublisherType {
    EVENT_BUS,          // Spring Application Event Bus
    KAFKA,              // Apache Kafka
    RABBITMQ,           // RabbitMQ
    SQS,                // Amazon Simple Queue Service
    GOOGLE_PUBSUB,      // Google Cloud Pub/Sub
    AZURE_SERVICE_BUS,  // Azure Service Bus
    REDIS,              // Redis Pub/Sub
    JMS,                // JMS/ActiveMQ
    KINESIS             // AWS Kinesis
}
```

### EventPublisherFactory

```java
package org.fireflyframework.core.messaging.publisher;

@Component
public class EventPublisherFactory {
    
    /**
     * Creates a publisher for the specified type and connection.
     * 
     * @param publisherType the type of publisher to create
     * @param connectionId the connection ID to use
     * @return the configured event publisher
     * @throws IllegalArgumentException if the publisher type is not supported
     */
    public EventPublisher createPublisher(PublisherType publisherType, String connectionId);
    
    /**
     * Gets all available publisher types.
     * 
     * @return a set of available publisher types
     */
    public Set<PublisherType> getAvailablePublisherTypes();
    
    /**
     * Checks if a specific publisher type is available.
     * 
     * @param publisherType the publisher type to check
     * @return true if the publisher type is available
     */
    public boolean isPublisherAvailable(PublisherType publisherType);
}
```

### Usage Examples

#### Basic Message Publishing

```java
@RestController
public class OrderController {
    
    @PostMapping("/orders")
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        destination = "order-events",
        eventType = "order.created"
    )
    public Mono<OrderCreatedEvent> createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request)
            .map(order -> new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getTotal(),
                Instant.now()
            ));
    }
}
```

#### Multiple Publishers

```java
@Service
public class NotificationService {
    
    // Publish to Kafka for event sourcing
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        destination = "notification-events",
        eventType = "notification.sent"
    )
    public Mono<NotificationEvent> sendNotification(NotificationRequest request) {
        return Mono.just(new NotificationEvent(request.getUserId(), request.getMessage()));
    }
    
    // Publish to Redis for real-time updates
    @PublishResult(
        publisherType = PublisherType.REDIS,
        destination = "realtime-notifications",
        eventType = "notification.realtime"
    )
    public Mono<RealtimeNotification> sendRealtimeNotification(RealtimeNotificationRequest request) {
        return Mono.just(new RealtimeNotification(request.getUserId(), request.getData()));
    }
}
```

#### Event Handling

```java
@Component
public class OrderEventHandler {
    
    @EventListener(eventTypes = "order.created")
    public Mono<Void> handleOrderCreated(@EventPayload OrderCreatedEvent event,
                                       @EventHeaders Map<String, Object> headers) {
        String transactionId = (String) headers.get("transactionId");
        
        return inventoryService.reserveItems(event.getOrderId(), event.getItems())
            .doOnSuccess(result -> log.info("Reserved items for order {} with transaction {}", 
                                           event.getOrderId(), transactionId))
            .then();
    }
    
    @EventListener(
        eventTypes = {"notification.sent", "notification.failed"},
        maxRetries = 5,
        retryDelayMs = 2000
    )
    public Mono<Void> handleNotificationEvents(@EventPayload NotificationEvent event) {
        return auditService.recordNotificationEvent(event);
    }
}
```

## Configuration API

### MessagingProperties

```java
package org.fireflyframework.core.messaging.config;

@Configuration
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {
    
    private boolean enabled = false;
    private boolean resilience = true;
    private int publishTimeoutSeconds = 5;
    private String applicationName;
    private String defaultConnectionId = "default";
    
    // Kafka configuration
    private KafkaConfig kafka = new KafkaConfig();
    private Map<String, KafkaConfig> kafkaConnections = new ConcurrentHashMap<>();
    
    // Other messaging providers...
    private RabbitMqConfig rabbitmq = new RabbitMqConfig();
    private SqsConfig sqs = new SqsConfig();
    
    /**
     * Gets Kafka configuration for the specified connection ID.
     */
    public KafkaConfig getKafkaConfig(String connectionId) {
        if (connectionId == null || connectionId.isEmpty() || connectionId.equals(defaultConnectionId)) {
            return kafka;
        }
        return kafkaConnections.getOrDefault(connectionId, kafka);
    }
    
    // Similar methods for other providers...
    public RabbitMqConfig getRabbitMqConfig(String connectionId) { /* ... */ }
    public SqsConfig getSqsConfig(String connectionId) { /* ... */ }
    
    // Nested configuration classes
    public static class KafkaConfig {
        private boolean enabled = false;
        private String defaultTopic = "events";
        private String bootstrapServers = "localhost:9092";
        private String clientId = "messaging-publisher";
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
        private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";
        private String securityProtocol = "PLAINTEXT";
        private String saslMechanism = "";
        private String saslUsername = "";
        private String saslPassword = "";
        private Map<String, String> properties = new HashMap<>();
        
        // Getters and setters...
    }
}
```

## Service Discovery API

### ServiceRegistryHelper

```java
package org.fireflyframework.core.config.registry;

@Component
public class ServiceRegistryHelper {
    
    private final DiscoveryClient discoveryClient;
    private final Environment environment;
    
    /**
     * Gets all instances of a service.
     * 
     * @param serviceId the service ID to look up
     * @return list of service instances
     */
    public List<ServiceInstance> getServiceInstances(String serviceId);
    
    /**
     * Gets a single service instance using load balancing.
     * 
     * @param serviceId the service ID to look up
     * @return the selected service instance
     * @throws ServiceNotFoundException if no instances are found
     */
    public ServiceInstance getInstance(String serviceId);
    
    /**
     * Gets the URI for a service using load balancing.
     * 
     * @param serviceId the service ID to look up
     * @return the service URI
     * @throws ServiceNotFoundException if no instances are found
     */
    public URI getServiceUri(String serviceId);
    
    /**
     * Checks if a service is available.
     * 
     * @param serviceId the service ID to check
     * @return true if at least one instance is available
     */
    public boolean isServiceAvailable(String serviceId);
    
    /**
     * Gets all registered services.
     * 
     * @return list of service IDs
     */
    public List<String> getServices();
    
    /**
     * Registers a callback for service discovery events.
     * 
     * @param listener the event listener
     */
    public void addServiceDiscoveryListener(ServiceDiscoveryEventListener listener);
}
```

### Usage Example

```java
@Service
public class PaymentService {
    
    private final ServiceRegistryHelper serviceRegistry;
    private final WebClient.Builder webClientBuilder;
    
    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return Mono.fromCallable(() -> serviceRegistry.getServiceUri("payment-processor"))
            .flatMap(uri -> webClientBuilder.build()
                .post()
                .uri(uri.resolve("/process"))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentResult.class))
            .onErrorResume(ServiceNotFoundException.class, 
                          ex -> Mono.just(PaymentResult.failed("Service unavailable")));
    }
}
```

## WebClient API

### WebClientProperties

```java
package org.fireflyframework.core.config;

@Configuration
@ConfigurationProperties(prefix = "webclient")
public class WebClientProperties {
    
    private boolean enabled = true;
    private List<String> skipHeaders;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int writeTimeoutMs = 10000;
    private int maxInMemorySize = 16777216; // 16MB
    
    private final Ssl ssl = new Ssl();
    private final Proxy proxy = new Proxy();
    private final ConnectionPool connectionPool = new ConnectionPool();
    private final Http2 http2 = new Http2();
    private final Codec codec = new Codec();
    
    // Getters and conversion methods
    public Duration getConnectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }
    
    public Duration getReadTimeout() {
        return Duration.ofMillis(readTimeoutMs);
    }
    
    public Duration getWriteTimeout() {
        return Duration.ofMillis(writeTimeoutMs);
    }
    
    // Nested configuration classes
    public static class Ssl {
        private boolean enabled = false;
        private boolean useDefaultSslContext = true;
        private String trustStorePath = "";
        private String trustStorePassword = "";
        private String trustStoreType = "JKS";
        private String keyStorePath = "";
        private String keyStorePassword = "";
        private String keyStoreType = "JKS";
        private boolean verifyHostname = true;
        private List<String> enabledProtocols;
        private List<String> enabledCipherSuites;
        // Getters and setters...
    }
    
    public static class ConnectionPool {
        private boolean enabled = true;
        private int maxConnections = 500;
        private int maxPendingAcquires = 1000;
        private long maxIdleTimeMs = 30000;
        private long maxLifeTimeMs = 60000;
        private boolean metricsEnabled = true;
        // Getters and setters...
    }
}
```

### WebClient Builder Customization

```java
@Configuration
public class WebClientConfig {
    
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder(WebClientProperties properties) {
        return WebClient.builder()
            .exchangeStrategies(exchangeStrategies(properties))
            .clientConnector(clientConnector(properties))
            .defaultHeaders(headers -> configureDefaultHeaders(headers, properties))
            .filter(loggingFilter())
            .filter(tracingFilter());
    }
    
    @Bean
    @LoadBalanced
    public WebClient loadBalancedWebClient(WebClient.Builder builder) {
        return builder.build();
    }
    
    private ExchangeStrategies exchangeStrategies(WebClientProperties properties) {
        return ExchangeStrategies.builder()
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize());
                if (properties.getCodec().isEnabled()) {
                    configurer.defaultCodecs().enableLoggingRequestDetails(
                        properties.getCodec().isEnableLoggingFormData());
                }
            })
            .build();
    }
}
```

## Actuator API

### Health Indicators

#### Custom Health Indicator Interface

```java
package org.springframework.boot.actuator.health;

public interface HealthIndicator {
    /**
     * Return an indication of health.
     * 
     * @return the health
     */
    Health health();
}
```

#### MessagingHealthIndicator

```java
package org.fireflyframework.core.actuator.health;

@Component
@ConditionalOnProperty(prefix = "management.health.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessagingHealthIndicator implements HealthIndicator {
    
    private final EventPublisherFactory publisherFactory;
    private final MessagingProperties messagingProperties;
    
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        for (PublisherType type : PublisherType.values()) {
            try {
                EventPublisher publisher = publisherFactory.createPublisher(type, "default");
                boolean available = publisher.isAvailable();
                
                builder.withDetail(type.name().toLowerCase(), 
                    Map.of(
                        "available", available,
                        "status", available ? "UP" : "DOWN"
                    ));
                    
                if (!available) {
                    builder.down();
                }
            } catch (Exception e) {
                builder.down()
                       .withDetail(type.name().toLowerCase(), 
                         Map.of(
                             "available", false,
                             "status", "ERROR",
                             "error", e.getMessage()
                         ));
            }
        }
        
        return builder.build();
    }
}
```

### Custom Health Indicator Example

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5 second timeout
            
            if (isValid) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("connection.pool.active", getActiveConnections())
                    .withDetail("connection.pool.max", getMaxConnections())
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "Connection validation failed")
                    .build();
            }
        } catch (SQLException e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private int getActiveConnections() {
        // Implementation to get active connection count
        return 0;
    }
    
    private int getMaxConnections() {
        // Implementation to get max connection count
        return 0;
    }
}
```

## Orchestration Integration

The orchestration engine (`fireflyframework-orchestration`) is the consolidated module that provides
Saga, TCC, and Workflow orchestration patterns. It includes its own auto-configuration and event
publishing via `OrchestrationEventPublisher` and `EventGateway`. No starter-level bridge is needed.

When `fireflyframework-orchestration` is present on the classpath, the `OrchestrationAutoConfiguration`
automatically configures the orchestration engine, including event publishing, persistence, and
monitoring. The orchestration engine manages its own event lifecycle internally.

### OrchestrationEventPublisher

```java
package org.fireflyframework.orchestration.event;

/**
 * Interface for publishing orchestration events (saga, TCC, and workflow).
 * Implemented internally by the orchestration engine and published
 * through the configured EventGateway.
 */
public interface OrchestrationEventPublisher {
    Mono<Void> publish(OrchestrationEvent event);
}
```

### Configuration

```yaml
firefly:
  orchestration:
    enabled: true
    saga:
      default-timeout: 30s
    tcc:
      default-timeout: 30s
    workflow:
      default-timeout: 60s
```

See the [fireflyframework-orchestration](../../fireflyframework-orchestration/) documentation for
full API details on Saga, TCC, and Workflow patterns.

## Serialization API

### MessageSerializer Interface

```java
package org.fireflyframework.core.messaging.serialization;

public interface MessageSerializer {
    /**
     * Serializes the given payload to bytes.
     * 
     * @param payload the payload to serialize
     * @return the serialized bytes
     * @throws SerializationException if serialization fails
     */
    byte[] serialize(Object payload) throws SerializationException;
    
    /**
     * Deserializes the given bytes to an object of the specified type.
     * 
     * @param data the bytes to deserialize
     * @param type the target type
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    <T> T deserialize(byte[] data, Class<T> type) throws SerializationException;
    
    /**
     * Gets the serialization format of this serializer.
     * 
     * @return the serialization format
     */
    SerializationFormat getFormat();
    
    /**
     * Gets the content type for this serializer.
     * 
     * @return the content type (e.g., "application/json")
     */
    String getContentType();
    
    /**
     * Checks if this serializer can handle the given type.
     * 
     * @param type the type to check
     * @return true if this serializer can handle the type
     */
    boolean canSerialize(Class<?> type);
}
```

### SerializerFactory

```java
package org.fireflyframework.core.messaging.serialization;

@Component
public class SerializerFactory {
    
    private final Map<SerializationFormat, MessageSerializer> serializers;
    private final MessageSerializer defaultSerializer;
    
    /**
     * Gets a serializer for the specified format.
     * 
     * @param format the serialization format
     * @return the serializer
     * @throws IllegalArgumentException if no serializer is available for the format
     */
    public MessageSerializer getSerializer(SerializationFormat format);
    
    /**
     * Gets the default serializer.
     * 
     * @return the default serializer
     */
    public MessageSerializer getDefaultSerializer();
    
    /**
     * Gets all available serialization formats.
     * 
     * @return set of available formats
     */
    public Set<SerializationFormat> getAvailableFormats();
    
    /**
     * Checks if a serializer is available for the specified format.
     * 
     * @param format the format to check
     * @return true if a serializer is available
     */
    public boolean isSerializerAvailable(SerializationFormat format);
}
```

### Serialization Formats

```java
package org.fireflyframework.core.messaging.serialization;

public enum SerializationFormat {
    JSON("application/json"),
    AVRO("avro/binary"),
    PROTOBUF("application/x-protobuf"),
    JAVA("application/x-java-serialized-object"),
    STRING("text/plain");
    
    private final String contentType;
    
    SerializationFormat(String contentType) {
        this.contentType = contentType;
    }
    
    public String getContentType() {
        return contentType;
    }
}
```

## Metrics API

### Custom Metrics Example

```java
@Component
public class BusinessMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter orderProcessedCounter;
    private final Timer orderProcessingTimer;
    private final Gauge activeOrdersGauge;
    
    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.orderProcessedCounter = Counter.builder("business.orders.processed")
            .description("Total number of processed orders")
            .tag("service", "order-service")
            .register(meterRegistry);
            
        this.orderProcessingTimer = Timer.builder("business.orders.processing.time")
            .description("Time taken to process orders")
            .distributionStatisticExpiry(Duration.ofMinutes(5))
            .register(meterRegistry);
            
        this.activeOrdersGauge = Gauge.builder("business.orders.active")
            .description("Number of currently active orders")
            .register(meterRegistry, this, BusinessMetrics::getActiveOrderCount);
    }
    
    /**
     * Records that an order has been processed.
     */
    public void recordOrderProcessed() {
        orderProcessedCounter.increment();
    }
    
    /**
     * Records that an order has been processed with specific tags.
     */
    public void recordOrderProcessed(String region, String customerType) {
        Counter.builder("business.orders.processed")
            .tag("region", region)
            .tag("customer.type", customerType)
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * Times an order processing operation.
     */
    public Timer.Sample startOrderProcessingTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Records the completion of an order processing operation.
     */
    public void recordOrderProcessingTime(Timer.Sample sample) {
        sample.stop(orderProcessingTimer);
    }
    
    private double getActiveOrderCount() {
        // Implementation to get current active order count
        return 0.0;
    }
}
```

### Usage in Service

```java
@Service
public class OrderProcessingService {
    
    private final BusinessMetrics metrics;
    
    public Mono<Order> processOrder(OrderRequest request) {
        Timer.Sample sample = metrics.startOrderProcessingTimer();
        
        return orderRepository.save(request)
            .doOnSuccess(order -> {
                sample.stop();
                metrics.recordOrderProcessed();
                metrics.recordOrderProcessed(
                    order.getCustomer().getRegion(),
                    order.getCustomer().getType()
                );
            })
            .doOnError(error -> sample.stop());
    }
}
```

This API reference provides comprehensive coverage of all major components in the Firefly Common Core library with real method signatures and practical usage examples. For additional details, refer to the specific configuration options in [CONFIGURATION.md](CONFIGURATION.md).