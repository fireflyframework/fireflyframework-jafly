# Logging Utilities

The Firefly Common Core logging utilities provide advanced structured logging capabilities with performance optimization, MDC support, and integration with observability platforms.

## Features

### Structured Logging
- **JSON Output**: Structured log messages in JSON format
- **Logstash Integration**: Compatible with Logstash for log aggregation
- **Custom Fields**: Add arbitrary key-value pairs to log entries
- **Fluent Builder API**: Easy-to-use builder pattern for log construction

### MDC Support
- **Request Tracing**: Automatic correlation ID, request ID, and user ID tracking
- **Context Preservation**: Thread-safe context management across async operations
- **Scoped Operations**: Execute operations within specific MDC contexts

### Performance Optimization
- **JSON Validation Caching**: Cache JSON validation results for improved performance
- **Recursive Processing Limits**: Prevent stack overflow with configurable depth limits
- **Concurrent Data Structures**: Thread-safe operations with high concurrency
- **Metrics Collection**: Performance metrics and cache statistics

### Actuator Integration
- **Health Endpoints**: Logging system health indicators
- **Metrics Endpoints**: Logging performance and usage metrics
- **Configuration Management**: Runtime configuration updates

## Core Components

### LoggingUtils
The main utility class providing structured logging capabilities:

```java
import org.fireflyframework.core.logging.LoggingUtils;

// Simple structured log entry
Map<String, Object> logEntry = LoggingUtils.structuredLog(
    "User login attempt", 
    "userId", "12345"
);

// Fluent builder API
LoggingUtils.log("Payment processed")
    .with("amount", 150.00)
    .with("currency", "USD")
    .with("merchantId", "merchant-123")
    .with("timestamp", Instant.now())
    .info(logger);
```

### MDC Context Management
Manage diagnostic context for request correlation:

```java
// Set individual MDC values
LoggingUtils.setUserId("user-123");
LoggingUtils.setRequestId("req-456");
LoggingUtils.setCorrelationId("corr-789");

// Scoped MDC operations
String result = LoggingUtils.withMdc("transactionId", "txn-123", () -> {
    logger.info("Processing transaction");
    return processTransaction();
});

// Multiple MDC values
Map<String, String> context = Map.of(
    "userId", "user-123",
    "sessionId", "sess-456"
);

LoggingUtils.withMdc(context, () -> {
    logger.info("User action performed");
    return performAction();
});
```

### Structured Log Builder
Fluent API for building complex log entries:

```java
LoggingUtils.log("Order processing completed")
    .with("orderId", order.getId())
    .with("customerId", order.getCustomerId())
    .with("amount", order.getTotalAmount())
    .with("processingTime", processingTime)
    .with("status", "SUCCESS")
    .info(logger);

// Error logging with exception
LoggingUtils.log("Order processing failed")
    .with("orderId", order.getId())
    .with("errorCode", "PAYMENT_DECLINED")
    .error(logger, exception);
```

## Configuration

### Application Properties
```yaml
firefly:
  logging:
    # Enable structured logging
    structured: true
    
    # Maximum recursion depth for JSON processing
    max-recursion-depth: 10
    
    # Enable performance metrics collection
    enable-metrics: true
    
    # Cache configuration
    cache:
      max-size: 10000
      expire-after-access: 30m
```

### Logback Configuration
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeContext>true</includeContext>
            <includeMdc>true</includeMdc>
            <customFields>{"service":"my-service"}</customFields>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Advanced Features

### JSON Processing
Automatic JSON validation and parsing with caching:

```java
// Automatic JSON detection and processing
LoggingUtils.log("API response received")
    .with("response", jsonResponse) // Automatically validated and parsed
    .with("endpoint", "/api/users")
    .info(logger);
```

### Performance Metrics
Monitor logging performance and cache effectiveness:

```java
// Get performance metrics
Map<String, Object> metrics = LoggingUtils.getMetrics();
System.out.println("Cache hit rate: " + metrics.get("cacheHitRate"));
System.out.println("JSON validations: " + metrics.get("jsonValidations"));

// Get detailed cache statistics
Map<String, Object> cacheStats = LoggingUtils.getCacheStats();
System.out.println("Cache size: " + cacheStats.get("size"));
System.out.println("Hit count: " + cacheStats.get("hitCount"));
```

### Cache Management
Control caching behavior for optimal performance:

```java
// Clear all caches
LoggingUtils.clearCaches();

// Reset performance metrics
LoggingUtils.resetMetrics();
```

## Integration Examples

### Spring Boot Application
```java
@RestController
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return LoggingUtils.withMdc("userId", id, () -> {
            logger.info("Fetching user details");
            
            return userService.findById(id)
                .doOnSuccess(user -> 
                    LoggingUtils.log("User retrieved successfully")
                        .with("userId", user.getId())
                        .with("username", user.getUsername())
                        .info(logger)
                )
                .doOnError(error -> 
                    LoggingUtils.log("Failed to retrieve user")
                        .with("userId", id)
                        .with("errorType", error.getClass().getSimpleName())
                        .error(logger, error)
                );
        });
    }
}
```

### Reactive Streams Integration
```java
@Service
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        return Mono.fromCallable(() -> request.getTransactionId())
            .flatMap(txnId -> LoggingUtils.withMdc("transactionId", txnId, () -> 
                processPaymentInternal(request)
                    .doOnSubscribe(subscription -> 
                        LoggingUtils.log("Payment processing started")
                            .with("amount", request.getAmount())
                            .with("currency", request.getCurrency())
                            .info(logger)
                    )
                    .doOnSuccess(result -> 
                        LoggingUtils.log("Payment processed successfully")
                            .with("resultCode", result.getCode())
                            .with("processingTime", result.getProcessingTime())
                            .info(logger)
                    )
            ));
    }
}
```

## Best Practices

1. **Use Structured Logging**: Always prefer structured logs over plain text messages
2. **Include Context**: Use MDC for request correlation and tracing
3. **Performance Awareness**: Monitor cache hit rates and adjust configuration as needed
4. **Consistent Field Names**: Use consistent field naming conventions across services
5. **Sensitive Data**: Never log sensitive information (passwords, tokens, PII)
6. **Log Levels**: Use appropriate log levels (ERROR for exceptions, INFO for business events, DEBUG for debugging)
7. **Exception Handling**: Always include stack traces with error logs

## Monitoring and Observability

### Actuator Endpoints
- `/actuator/logging` - Logging configuration and statistics
- `/actuator/metrics/logging.*` - Logging performance metrics
- `/actuator/health/logging` - Logging system health status

### Metrics Available
- `logging.cache.hit.rate` - Cache hit rate percentage
- `logging.json.validations` - Total JSON validations performed
- `logging.structured.logs` - Count of structured log entries created
- `logging.mdc.operations` - MDC context operations count

### Log Analysis
Use log aggregation tools like ELK Stack or Splunk to analyze structured logs:

```json
{
  "@timestamp": "2024-08-23T11:52:00.123Z",
  "level": "INFO",
  "message": "Payment processed successfully",
  "transactionId": "txn-123",
  "amount": 150.00,
  "currency": "USD",
  "processingTime": 245,
  "service": "payment-service"
}
```