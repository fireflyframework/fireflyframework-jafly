# Integrations Guide

**Firefly Common Core Library**  
*Copyright (c) 2025 Firefly Software Foundation*  
*Licensed under the Apache License, Version 2.0*

This guide covers how to integrate Firefly Common Core with various external systems, cloud platforms, and enterprise services.

## Table of Contents

- [Cloud Platforms](#cloud-platforms)
- [Messaging Systems](#messaging-systems)
- [Service Discovery](#service-discovery)
- [Configuration Management](#configuration-management)
- [Databases](#databases)
- [Monitoring & Observability](#monitoring--observability)
- [Security & Authentication](#security--authentication)
- [API Gateways](#api-gateways)
- [Container Orchestration](#container-orchestration)
- [CI/CD Integration](#cicd-integration)

## Cloud Platforms

### Amazon Web Services (AWS)

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-aws</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-aws-messaging</artifactId>
</dependency>
```

#### Configuration
```yaml
# application.yml
cloud:
  aws:
    region:
      static: us-east-1
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}

messaging:
  enabled: true
  sqs:
    enabled: true
    region: us-east-1
    endpoint: https://sqs.us-east-1.amazonaws.com
  kinesis:
    enabled: true
    region: us-east-1
    endpoint: https://kinesis.us-east-1.amazonaws.com
```

#### Example Integration
```java
@Service
public class AwsIntegrationService {
    
    @PublishResult(
        publisherType = PublisherType.SQS,
        destination = "order-processing-queue"
    )
    public Mono<OrderEvent> publishOrderToSqs(Order order) {
        return Mono.just(new OrderEvent(order.getId(), order.getStatus()));
    }
    
    @PublishResult(
        publisherType = PublisherType.KINESIS,
        destination = "analytics-stream"
    )
    public Mono<AnalyticsEvent> publishAnalyticsToKinesis(AnalyticsData data) {
        return Mono.just(new AnalyticsEvent(data.getUserId(), data.getAction()));
    }
}
```

#### IAM Policy Requirements
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sqs:SendMessage",
                "sqs:ReceiveMessage",
                "sqs:DeleteMessage",
                "kinesis:PutRecord",
                "kinesis:PutRecords",
                "kinesis:GetRecords",
                "kinesis:DescribeStream"
            ],
            "Resource": "*"
        }
    ]
}
```

### Google Cloud Platform (GCP)

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-gcp-starter-pubsub</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  cloud:
    gcp:
      project-id: your-project-id
      credentials:
        location: classpath:service-account.json

messaging:
  enabled: true
  google-pubsub:
    enabled: true
    project-id: your-project-id
    credentials-location: classpath:service-account.json
```

#### Example Integration
```java
@Component
public class GcpPubSubIntegration {
    
    @EventListener(eventTypes = "order.created")
    public Mono<Void> handleOrderCreated(@EventPayload OrderCreatedEvent event) {
        // Automatically received from Pub/Sub subscription
        return orderProcessingService.processOrder(event.getOrderId());
    }
    
    @PublishResult(
        publisherType = PublisherType.GOOGLE_PUBSUB,
        destination = "notification-topic"
    )
    public Mono<NotificationEvent> sendNotification(String userId, String message) {
        return Mono.just(new NotificationEvent(userId, message));
    }
}
```

### Microsoft Azure

#### Prerequisites
```xml
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>azure-spring-boot-starter-servicebus</artifactId>
</dependency>
```

#### Configuration
```yaml
azure:
  servicebus:
    connection-string: ${AZURE_SERVICE_BUS_CONNECTION_STRING}

messaging:
  enabled: true
  azure-service-bus:
    enabled: true
    connection-string: ${AZURE_SERVICE_BUS_CONNECTION_STRING}
    namespace: your-servicebus-namespace
```

#### Example Integration
```java
@Service
public class AzureServiceBusIntegration {
    
    @PublishResult(
        publisherType = PublisherType.AZURE_SERVICE_BUS,
        destination = "order-events-topic"
    )
    public Mono<OrderEvent> publishOrderEvent(Order order) {
        return Mono.just(new OrderEvent(order.getId(), order.getStatus(), order.getTimestamp()));
    }
}
```

## Messaging Systems

### Apache Kafka

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

#### Configuration
```yaml
messaging:
  enabled: true
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    client-id: firefly-app
    security-protocol: SASL_SSL
    sasl-mechanism: PLAIN
    sasl-username: ${KAFKA_USERNAME}
    sasl-password: ${KAFKA_PASSWORD}
    properties:
      enable.auto.commit: false
      max.poll.records: 500
      session.timeout.ms: 30000
```

#### Multi-Cluster Configuration
```yaml
messaging:
  kafka-connections:
    primary:
      enabled: true
      bootstrap-servers: primary-cluster:9092
      client-id: firefly-primary
    analytics:
      enabled: true
      bootstrap-servers: analytics-cluster:9092
      client-id: firefly-analytics
      default-topic: analytics-events
```

#### Example Usage
```java
@Service
public class KafkaIntegration {
    
    // Publish to primary cluster
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        connectionId = "primary",
        destination = "order-events"
    )
    public Mono<OrderEvent> publishOrderEvent(Order order) {
        return Mono.just(new OrderEvent(order.getId(), order.getStatus()));
    }
    
    // Publish to analytics cluster
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        connectionId = "analytics",
        destination = "user-behavior"
    )
    public Mono<UserBehaviorEvent> publishUserBehavior(String userId, String action) {
        return Mono.just(new UserBehaviorEvent(userId, action, Instant.now()));
    }
}
```

### RabbitMQ

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

#### Configuration
```yaml
messaging:
  enabled: true
  rabbitmq:
    enabled: true
    host: localhost
    port: 5672
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: /firefly
    connection-timeout: 30000
    ssl:
      enabled: false
    exchanges:
      - name: order-exchange
        type: topic
        durable: true
      - name: notification-exchange
        type: direct
        durable: true
```

#### Example Integration
```java
@Service
public class RabbitMqIntegration {
    
    @PublishResult(
        publisherType = PublisherType.RABBITMQ,
        destination = "order-exchange",
        eventType = "order.created"
    )
    public Mono<OrderEvent> publishOrderCreated(Order order) {
        return Mono.just(new OrderEvent(order.getId(), order.getStatus()));
    }
    
    @EventListener(
        eventTypes = "payment.processed",
        destinations = "payment-queue"
    )
    public Mono<Void> handlePaymentProcessed(@EventPayload PaymentEvent event) {
        return orderService.updateOrderPaymentStatus(event.getOrderId(), event.getStatus());
    }
}
```

### Redis

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD}
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-wait: -1ms
        max-idle: 8
        min-idle: 0

messaging:
  enabled: true
  redis:
    enabled: true
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD}
    database: 0
```

#### Example Integration
```java
@Service
public class RedisIntegration {
    
    // Publish real-time notifications
    @PublishResult(
        publisherType = PublisherType.REDIS,
        destination = "realtime-notifications"
    )
    public Mono<NotificationEvent> publishRealtimeNotification(String userId, String message) {
        return Mono.just(new NotificationEvent(userId, message, Instant.now()));
    }
    
    // Cache invalidation events
    @PublishResult(
        publisherType = PublisherType.REDIS,
        destination = "cache-invalidation"
    )
    public Mono<CacheInvalidationEvent> publishCacheInvalidation(String cacheKey) {
        return Mono.just(new CacheInvalidationEvent(cacheKey, Instant.now()));
    }
}
```

## Service Discovery

### Eureka

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

#### Configuration
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    fetch-registry: true
    register-with-eureka: true
  instance:
    hostname: localhost
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 30
    lease-expiration-duration-in-seconds: 90
```

#### Example Integration
```java
@Service
public class EurekaIntegration {
    
    private final ServiceRegistryHelper serviceRegistry;
    private final WebClient.Builder webClientBuilder;
    
    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return Mono.fromCallable(() -> serviceRegistry.getServiceUri("payment-service"))
            .flatMap(uri -> webClientBuilder.build()
                .post()
                .uri(uri.resolve("/api/payments"))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentResult.class))
            .onErrorResume(ServiceNotFoundException.class, 
                          ex -> Mono.just(PaymentResult.failed("Payment service unavailable")));
    }
    
    @EventListener
    public void onServiceRegistered(ServiceRegisteredEvent event) {
        log.info("Service registered: {}", event.getServiceId());
    }
}
```

### Consul

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-consul-discovery</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        enabled: true
        register: true
        deregister: true
        health-check-interval: 15s
        health-check-path: /actuator/health
        instance-id: ${spring.application.name}-${server.port}
        tags:
          - firefly
          - microservice
```

#### Health Check Integration
```java
@Component
public class ConsulHealthCheck implements HealthIndicator {
    
    @Override
    public Health health() {
        // Custom health check logic for Consul
        try {
            // Check critical dependencies
            boolean databaseHealthy = checkDatabaseHealth();
            boolean messagingHealthy = checkMessagingHealth();
            
            if (databaseHealthy && messagingHealthy) {
                return Health.up()
                    .withDetail("database", "UP")
                    .withDetail("messaging", "UP")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", databaseHealthy ? "UP" : "DOWN")
                    .withDetail("messaging", messagingHealthy ? "UP" : "DOWN")
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
    
    private boolean checkDatabaseHealth() {
        // Implementation
        return true;
    }
    
    private boolean checkMessagingHealth() {
        // Implementation
        return true;
    }
}
```

## Configuration Management

### Spring Cloud Config

#### Configuration
```yaml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
      retry:
        max-attempts: 3
        initial-interval: 1000
        max-interval: 2000
      discovery:
        enabled: true
        service-id: config-server
```

#### Git Repository Structure
```
config-repo/
├── application.yml                 # Default configuration
├── application-dev.yml             # Development environment
├── application-prod.yml            # Production environment
├── firefly-order-service.yml       # Service-specific config
├── firefly-order-service-dev.yml   # Service + env specific
└── firefly-order-service-prod.yml
```

#### Dynamic Configuration Update
```java
@Component
@RefreshScope
public class DynamicConfigExample {
    
    @Value("${business.order.max-items:10}")
    private int maxOrderItems;
    
    @Value("${business.payment.timeout:30000}")
    private int paymentTimeout;
    
    public boolean isOrderValid(Order order) {
        return order.getItems().size() <= maxOrderItems;
    }
    
    public Duration getPaymentTimeout() {
        return Duration.ofMillis(paymentTimeout);
    }
}
```

### HashiCorp Vault

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  cloud:
    vault:
      host: localhost
      port: 8200
      scheme: https
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        application-name: firefly-app
```

#### Secret Management
```java
@Configuration
public class VaultConfiguration {
    
    @Bean
    @ConfigurationProperties("database")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @ConfigurationProperties("messaging.kafka")
    public KafkaProperties kafkaProperties() {
        return new KafkaProperties();
    }
}

// Vault secrets structure:
// secret/firefly-app/database -> username, password, url
// secret/firefly-app/messaging/kafka -> username, password
```

## Databases

### PostgreSQL with R2DBC

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/firefly_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
      max-create-connection-time: 2m
```

#### Repository Integration
```java
@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    
    @Query("SELECT * FROM orders WHERE customer_id = :customerId ORDER BY created_at DESC")
    Flux<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    
    @Query("SELECT * FROM orders WHERE status = :status AND created_at >= :since")
    Flux<Order> findByStatusSince(OrderStatus status, Instant since);
}

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    
    @PublishResult(
        publisherType = PublisherType.KAFKA,
        destination = "order-events",
        eventType = "order.created"
    )
    public Mono<OrderCreatedEvent> createOrder(CreateOrderRequest request) {
        return orderRepository.save(Order.from(request))
            .map(order -> new OrderCreatedEvent(order.getId(), order.getCustomerId(), order.getTotal()));
    }
}
```

### MongoDB Reactive

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://${MONGO_USERNAME}:${MONGO_PASSWORD}@localhost:27017/firefly_db
      auto-index-creation: true
```

#### Document and Repository
```java
@Document(collection = "events")
public class EventDocument {
    @Id
    private String id;
    private String eventType;
    private String aggregateId;
    private Object payload;
    private Instant timestamp;
    private Map<String, Object> metadata;
    
    // Constructors, getters, setters
}

@Repository
public interface EventRepository extends ReactiveMongoRepository<EventDocument, String> {
    
    Flux<EventDocument> findByAggregateIdOrderByTimestamp(String aggregateId);
    
    Flux<EventDocument> findByEventTypeAndTimestampBetween(String eventType, Instant start, Instant end);
}
```

## Monitoring & Observability

### Prometheus

#### Configuration
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
```

#### Custom Metrics
```java
@Component
public class BusinessMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter orderCounter;
    private final Timer paymentProcessingTimer;
    private final Gauge activeUsersGauge;
    
    public BusinessMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.orderCounter = Counter.builder("business_orders_total")
            .description("Total number of orders processed")
            .tag("service", "order-service")
            .register(meterRegistry);
            
        this.paymentProcessingTimer = Timer.builder("business_payment_processing_duration")
            .description("Time taken to process payments")
            .register(meterRegistry);
            
        this.activeUsersGauge = Gauge.builder("business_active_users")
            .description("Number of active users")
            .register(meterRegistry, this, BusinessMetricsCollector::getActiveUserCount);
    }
    
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        orderCounter.increment(Tags.of(
            "region", event.getRegion(),
            "customer_type", event.getCustomerType()
        ));
    }
    
    public Timer.Sample startPaymentTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordPaymentProcessingTime(Timer.Sample sample, String paymentMethod) {
        sample.stop(Timer.builder("business_payment_processing_duration")
            .tag("payment_method", paymentMethod)
            .register(meterRegistry));
    }
    
    private double getActiveUserCount() {
        // Implementation to get active user count
        return 0.0;
    }
}
```

### Grafana Dashboards

#### Example Dashboard JSON
```json
{
  "dashboard": {
    "id": null,
    "title": "Firefly Common Core Metrics",
    "tags": ["firefly", "microservices"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Order Processing Rate",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(business_orders_total[5m])",
            "legendFormat": "Orders/sec"
          }
        ]
      },
      {
        "id": 2,
        "title": "Payment Processing Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(business_payment_processing_duration_bucket[5m]))",
            "legendFormat": "95th percentile"
          },
          {
            "expr": "histogram_quantile(0.50, rate(business_payment_processing_duration_bucket[5m]))",
            "legendFormat": "50th percentile"
          }
        ]
      }
    ]
  }
}
```

### Distributed Tracing (Zipkin/Jaeger)

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-zipkin</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  zipkin:
    base-url: http://zipkin-server:9411
    sender:
      type: web
  sleuth:
    sampler:
      probability: 1.0  # Sample 100% in development, reduce in production
    zipkin:
      base-url: http://zipkin-server:9411
```

#### Custom Spans
```java
@Service
public class TracedOrderService {
    
    private final Tracer tracer;
    
    @PublishResult(publisherType = PublisherType.KAFKA, destination = "orders")
    public Mono<OrderEvent> processOrder(CreateOrderRequest request) {
        return Mono.fromCallable(() -> tracer.nextSpan().name("order-validation"))
            .flatMap(span -> {
                span.tag("order.customer_id", request.getCustomerId());
                span.tag("order.items_count", String.valueOf(request.getItems().size()));
                
                try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
                    return validateOrder(request)
                        .flatMap(this::persistOrder)
                        .map(order -> new OrderEvent(order.getId(), order.getStatus()))
                        .doOnError(error -> span.tag("error", error.getMessage()))
                        .doFinally(signalType -> span.end());
                }
            });
    }
    
    private Mono<Order> validateOrder(CreateOrderRequest request) {
        // Validation logic with automatic span creation
        return Mono.just(Order.from(request));
    }
    
    private Mono<Order> persistOrder(Order order) {
        // Persistence logic with automatic span creation
        return orderRepository.save(order);
    }
}
```

## Security & Authentication

### OAuth 2.0 / JWT Integration

#### Prerequisites
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

#### Configuration
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.firefly.com/oauth2
          jwk-set-uri: https://auth.firefly.com/.well-known/jwks.json

webclient:
  enabled: true
  skip-headers:
    - authorization  # Don't propagate auth headers by default
```

#### Security Configuration
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/api/public/**").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .csrf().disable()
            .build();
    }
    
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation("https://auth.firefly.com/oauth2");
    }
}
```

#### Service-to-Service Authentication
```java
@Component
public class AuthenticatedWebClientCustomizer {
    
    @Value("${app.service-account.client-id}")
    private String clientId;
    
    @Value("${app.service-account.client-secret}")
    private String clientSecret;
    
    @Bean
    @LoadBalanced
    public WebClient authenticatedWebClient(WebClient.Builder builder) {
        return builder
            .filter(clientCredentialsFilter())
            .build();
    }
    
    private ExchangeFilterFunction clientCredentialsFilter() {
        return (request, next) -> {
            return getAccessToken()
                .flatMap(token -> next.exchange(
                    ClientRequest.from(request)
                        .header("Authorization", "Bearer " + token)
                        .build()
                ));
        };
    }
    
    private Mono<String> getAccessToken() {
        // Implementation to get OAuth2 client credentials token
        return Mono.just("access-token");
    }
}
```

## API Gateways

### Spring Cloud Gateway Integration

#### Configuration
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: firefly-order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=2
            - AddRequestHeader=X-Service-Name, order-service
        - id: firefly-payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
          filters:
            - StripPrefix=2
            - CircuitBreaker=payment-cb
```

#### Custom Filters
```java
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        log.info("Gateway request: {} {} from {}",
            request.getMethod(),
            request.getURI(),
            request.getRemoteAddress());
        
        return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                ServerHttpResponse response = exchange.getResponse();
                log.info("Gateway response: {} for {} {}",
                    response.getStatusCode(),
                    request.getMethod(),
                    request.getURI());
            });
    }
    
    @Override
    public int getOrder() {
        return -1; // High precedence
    }
}
```

## Container Orchestration

### Kubernetes Deployment

#### Deployment YAML
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: firefly-order-service
  labels:
    app: firefly-order-service
    component: microservice
spec:
  replicas: 3
  selector:
    matchLabels:
      app: firefly-order-service
  template:
    metadata:
      labels:
        app: firefly-order-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: firefly-order-service
          image: firefly/order-service:1.0.0
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "kubernetes"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: database-credentials
                  key: url
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: "kafka-cluster:9092"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: firefly-order-service
  labels:
    app: firefly-order-service
spec:
  selector:
    app: firefly-order-service
  ports:
    - port: 80
      targetPort: 8080
      name: http
  type: ClusterIP
```

#### ConfigMap for Application Properties
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: firefly-order-service-config
data:
  application-kubernetes.yml: |
    spring:
      cloud:
        kubernetes:
          discovery:
            enabled: true
          config:
            enabled: true
            
    messaging:
      enabled: true
      kafka:
        enabled: true
        bootstrap-servers: kafka-cluster:9092
        
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      endpoint:
        health:
          show-details: always
```

### Docker Compose Integration

#### docker-compose.yml
```yaml
version: '3.8'
services:
  firefly-order-service:
    image: firefly/order-service:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DATABASE_URL=postgresql://postgres:5432/firefly_db
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - REDIS_URL=redis://redis:6379
    depends_on:
      - postgres
      - kafka
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - firefly-network
      
  postgres:
    image: postgres:13
    environment:
      POSTGRES_DB: firefly_db
      POSTGRES_USER: firefly
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - firefly-network
      
  kafka:
    image: confluentinc/cp-kafka:latest
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on:
      - zookeeper
    networks:
      - firefly-network
      
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    networks:
      - firefly-network
      
  redis:
    image: redis:6-alpine
    networks:
      - firefly-network
      
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    networks:
      - firefly-network

volumes:
  postgres_data:

networks:
  firefly-network:
    driver: bridge
```

## CI/CD Integration

### GitHub Actions Workflow

```yaml
name: Build and Deploy Firefly Service

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:13
        env:
          POSTGRES_PASSWORD: password
          POSTGRES_DB: test_db
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          
      - name: Run tests
        run: mvn clean test
        env:
          DATABASE_URL: postgresql://localhost:5432/test_db
          DATABASE_USERNAME: postgres
          DATABASE_PASSWORD: password
          
      - name: Run integration tests
        run: mvn clean verify -P integration-tests
        env:
          SPRING_PROFILES_ACTIVE: integration
          
  build-and-deploy:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Build application
        run: mvn clean package -DskipTests
        
      - name: Build Docker image
        run: |
          docker build -t firefly/order-service:${{ github.sha }} .
          docker tag firefly/order-service:${{ github.sha }} firefly/order-service:latest
          
      - name: Push to registry
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker push firefly/order-service:${{ github.sha }}
          docker push firefly/order-service:latest
          
      - name: Deploy to Kubernetes
        uses: azure/k8s-deploy@v1
        with:
          manifests: |
            k8s/deployment.yml
            k8s/service.yml
          images: |
            firefly/order-service:${{ github.sha }}
          kubectl-version: 'latest'
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'your-registry.com'
        K8S_NAMESPACE = 'firefly-services'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh 'mvn clean test'
                    }
                    post {
                        always {
                            publishTestResults testResultsPattern: 'target/surefire-reports/*.xml'
                        }
                    }
                }
                
                stage('Integration Tests') {
                    steps {
                        sh 'mvn clean verify -P integration-tests'
                    }
                    post {
                        always {
                            publishTestResults testResultsPattern: 'target/failsafe-reports/*.xml'
                        }
                    }
                }
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
                
                script {
                    def image = docker.build("${DOCKER_REGISTRY}/firefly/order-service:${BUILD_NUMBER}")
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-registry-credentials') {
                        image.push()
                        image.push('latest')
                    }
                }
            }
        }
        
        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                kubernetesDeploy(
                    configs: 'k8s/*.yml',
                    kubeconfigId: 'k8s-config',
                    enableConfigSubstitution: true
                )
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
        
        success {
            slackSend(
                color: 'good',
                message: "✅ Build succeeded: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            )
        }
        
        failure {
            slackSend(
                color: 'danger',
                message: "❌ Build failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            )
        }
    }
}
```

This comprehensive integrations guide provides practical examples for connecting Firefly Common Core with various external systems, cloud platforms, and enterprise services. Each integration includes the necessary dependencies, configuration, and code examples to get started quickly.