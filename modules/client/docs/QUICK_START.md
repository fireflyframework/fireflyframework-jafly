# Quick Start Guide

Get started with Firefly Common Client Library in 5 minutes!

## Table of Contents

- [Installation](#installation)
- [Basic Usage](#basic-usage)
- [REST Client](#rest-client)
- [gRPC Client](#grpc-client)
- [SOAP Client](#soap-client)
- [Helpers](#helpers)
- [Configuration](#configuration)
- [Next Steps](#next-steps)

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

Add the dependency to your `build.gradle`:

```gradle
implementation 'org.fireflyframework:fireflyframework-client:1.0.0'
```

---

## Basic Usage

### 1. Enable Auto-Configuration

Add `@EnableServiceClient` to your Spring Boot application:

```java
@SpringBootApplication
@EnableServiceClient
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 2. Configure Properties

Add configuration to `application.yml`:

```yaml
firefly:
  service-client:
    environment: DEVELOPMENT
    rest:
      default-timeout: 30s
      max-connections: 100
    circuit-breaker:
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 10
```

---

## REST Client

### Simple GET Request

```java
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.rest.RestClient;

@Service
public class UserService {
    
    private final RestClient userClient;
    
    public UserService() {
        this.userClient = ServiceClient.rest("user-service")
            .baseUrl("https://api.example.com")
            .build();
    }
    
    public User getUser(String userId) {
        return userClient.get("/users/{id}")
            .pathVariable("id", userId)
            .retrieve(User.class)
            .block();
    }
}
```

### POST Request with Body

```java
public User createUser(CreateUserRequest request) {
    return userClient.post("/users")
        .body(request)
        .retrieve(User.class)
        .block();
}
```

### PUT Request

```java
public User updateUser(String userId, UpdateUserRequest request) {
    return userClient.put("/users/{id}")
        .pathVariable("id", userId)
        .body(request)
        .retrieve(User.class)
        .block();
}
```

### DELETE Request

```java
public void deleteUser(String userId) {
    userClient.delete("/users/{id}")
        .pathVariable("id", userId)
        .execute()
        .block();
}
```

### With Headers

```java
public User getUserWithAuth(String userId, String token) {
    return userClient.get("/users/{id}")
        .pathVariable("id", userId)
        .header("Authorization", "Bearer " + token)
        .retrieve(User.class)
        .block();
}
```

### With Query Parameters

```java
public List<User> searchUsers(String query, int page, int size) {
    return userClient.get("/users/search")
        .queryParam("q", query)
        .queryParam("page", page)
        .queryParam("size", size)
        .retrieveList(User.class)
        .block();
}
```

---

## gRPC Client

### Simple gRPC Call

```java
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.grpc.GrpcClient;

@Service
public class PaymentService {
    
    private final GrpcClient<PaymentServiceGrpc.PaymentServiceBlockingStub> paymentClient;
    
    public PaymentService() {
        this.paymentClient = ServiceClient.grpc("payment-service", PaymentServiceGrpc.PaymentServiceBlockingStub.class)
            .host("payment-service")
            .port(9090)
            .usePlaintext()
            .build();
    }
    
    public PaymentResponse processPayment(PaymentRequest request) {
        return paymentClient.execute(stub -> stub.processPayment(request));
    }
}
```

### With Metadata (Headers)

```java
public PaymentResponse processPaymentWithAuth(PaymentRequest request, String token) {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
    
    return paymentClient.execute(stub -> stub.processPayment(request), metadata);
}
```

---

## SOAP Client

### Simple SOAP Call

```java
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.soap.SoapClient;

@Service
public class LegacyService {
    
    private final SoapClient legacyClient;
    
    public LegacyService() {
        this.legacyClient = ServiceClient.soap("legacy-service")
            .wsdlUrl("https://legacy.example.com/service?wsdl")
            .build();
    }
    
    public LegacyResponse callLegacyService(LegacyRequest request) {
        return legacyClient.call("getLegacyData", request, LegacyResponse.class);
    }
}
```

### With Authentication

```java
public LegacyService() {
    this.legacyClient = ServiceClient.soap("legacy-service")
        .wsdlUrl("https://legacy.example.com/service?wsdl&user=admin&password=secret")
        .build();
}
```

---

## Helpers

### GraphQL Client

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper;

@Service
public class GraphQLService {
    
    private final GraphQLClientHelper graphql;
    
    public GraphQLService() {
        this.graphql = GraphQLClientHelper.builder()
            .endpoint("https://api.example.com/graphql")
            .enableCache(true)
            .enableRetry(true)
            .build();
    }
    
    public User getUser(String userId) {
        String query = """
            query GetUser($id: ID!) {
                user(id: $id) {
                    id
                    name
                    email
                }
            }
            """;
        
        Map<String, Object> variables = Map.of("id", userId);
        
        return graphql.query(query, variables, User.class).block();
    }
}
```

### OAuth2 Client

```java
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;

@Service
public class OAuth2Service {
    
    private final OAuth2ClientHelper oauth2;
    
    public OAuth2Service() {
        this.oauth2 = OAuth2ClientHelper.builder()
            .tokenEndpoint("https://auth.example.com/oauth/token")
            .clientId("my-client-id")
            .clientSecret("my-client-secret")
            .build();
    }
    
    public String getAccessToken() {
        return oauth2.getToken("read write").getAccessToken();
    }
}
```

### Multipart Upload

```java
import org.fireflyframework.client.multipart.MultipartUploadHelper;

@Service
public class FileUploadService {
    
    private final MultipartUploadHelper upload;
    
    public FileUploadService() {
        this.upload = MultipartUploadHelper.builder()
            .baseUrl("https://upload.example.com")
            .enableProgressTracking(true)
            .enableCompression(true)
            .build();
    }
    
    public UploadResponse uploadFile(File file) {
        return upload.uploadFile("/upload", file, UploadResponse.class, progress -> {
            System.out.println("Progress: " + progress.getPercentage() + "%");
        }).block();
    }
}
```

### WebSocket Client

```java
import org.fireflyframework.client.websocket.WebSocketClientHelper;

@Service
public class WebSocketService {
    
    private final WebSocketClientHelper ws;
    
    public WebSocketService() {
        this.ws = WebSocketClientHelper.builder()
            .url("wss://api.example.com/ws")
            .enableReconnection(true)
            .enableHeartbeat(true)
            .build();
    }
    
    public void connect() {
        ws.connect(
            message -> System.out.println("Received: " + message),
            error -> System.err.println("Error: " + error.getMessage())
        );
    }
    
    public void sendMessage(String message) {
        ws.sendMessage(message);
    }
}
```

---

## Configuration

### Complete Configuration Example

```yaml
firefly:
  service-client:
    # Environment (DEVELOPMENT, STAGING, PRODUCTION)
    environment: PRODUCTION
    
    # REST Configuration
    rest:
      default-timeout: 30s
      max-connections: 100
      logging-enabled: false
    
    # gRPC Configuration
    grpc:
      default-timeout: 30s
      use-plaintext-by-default: false
    
    # SOAP Configuration
    soap:
      default-timeout: 60s
      wsdl-cache-enabled: true
      wsdl-cache-expiration: 1h
    
    # Circuit Breaker Configuration
    circuit-breaker:
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 10
      sliding-window-size: 20
      wait-duration-in-open-state: 60s
    
    # Security Configuration
    security:
      tls-enabled: true
      trust-store-path: ${TRUST_STORE_PATH}
      trust-store-password: ${TRUST_STORE_PASSWORD}
      key-store-path: ${KEY_STORE_PATH}
      key-store-password: ${KEY_STORE_PASSWORD}

# Actuator Configuration (for health checks and metrics)
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Environment Variables

```bash
# Security
export TRUST_STORE_PATH=/path/to/truststore.jks
export TRUST_STORE_PASSWORD=changeit
export KEY_STORE_PATH=/path/to/keystore.jks
export KEY_STORE_PASSWORD=changeit

# OAuth2
export OAUTH2_CLIENT_ID=my-client-id
export OAUTH2_CLIENT_SECRET=my-client-secret

# API Keys
export API_KEY=my-api-key-12345
```

---

## Next Steps

### 1. Explore Advanced Features

- [Security Features](SECURITY.md) - Certificate pinning, mTLS, JWT validation
- [Observability](OBSERVABILITY.md) - Metrics, tracing, health checks
- [Integration Testing](INTEGRATION_TESTING.md) - WireMock, test containers

### 2. Read Helper Documentation

- [GraphQL Helper](GRAPHQL_HELPER.md)
- [OAuth2 Helper](OAUTH2_HELPER.md)
- [Multipart Upload Helper](MULTIPART_HELPER.md)
- [WebSocket Helper](WEBSOCKET_HELPER.md)

### 3. Check Examples

Browse the [examples directory](../examples/) for complete working examples.

---

## Common Patterns

### Pattern 1: Service Client with Circuit Breaker

```java
@Service
public class ResilientUserService {
    
    private final RestClient userClient;
    
    public ResilientUserService() {
        this.userClient = ServiceClient.rest("user-service")
            .baseUrl("https://api.example.com")
            .circuitBreaker(cb -> cb
                .failureRateThreshold(50.0)
                .minimumNumberOfCalls(10)
            )
            .build();
    }
    
    public User getUser(String userId) {
        return userClient.get("/users/{id}")
            .pathVariable("id", userId)
            .retrieve(User.class)
            .block();
    }
}
```

### Pattern 2: Service Client with Retry

```java
@Service
public class RetryablePaymentService {
    
    private final RestClient paymentClient;
    
    public RetryablePaymentService() {
        this.paymentClient = ServiceClient.rest("payment-service")
            .baseUrl("https://payment.example.com")
            .retry(retry -> retry
                .maxAttempts(3)
                .backoff(Duration.ofSeconds(1), Duration.ofSeconds(10))
            )
            .build();
    }
}
```

### Pattern 3: Service Client with Authentication

```java
@Service
public class AuthenticatedService {
    
    private final RestClient apiClient;
    private final OAuth2ClientHelper oauth2;
    
    public AuthenticatedService() {
        this.oauth2 = OAuth2ClientHelper.builder()
            .tokenEndpoint("https://auth.example.com/oauth/token")
            .clientId("client-id")
            .clientSecret("client-secret")
            .build();
        
        this.apiClient = ServiceClient.rest("api-service")
            .baseUrl("https://api.example.com")
            .defaultHeader("Authorization", () -> "Bearer " + oauth2.getToken().getAccessToken())
            .build();
    }
}
```

---

## Troubleshooting

### Issue: Timeout Errors

**Solution**: Increase timeout in configuration:

```yaml
firefly:
  service-client:
    rest:
      default-timeout: 60s
```

### Issue: Circuit Breaker Opens Too Quickly

**Solution**: Adjust circuit breaker settings:

```yaml
firefly:
  service-client:
    circuit-breaker:
      failure-rate-threshold: 75.0  # Increase threshold
      minimum-number-of-calls: 20   # Require more calls
```

### Issue: SSL/TLS Errors

**Solution**: Configure trust store:

```yaml
firefly:
  service-client:
    security:
      tls-enabled: true
      trust-store-path: /path/to/truststore.jks
      trust-store-password: ${TRUST_STORE_PASSWORD}
```

---

## Getting Help

- 📖 [Full Documentation](README.md)
- 🐛 [Report Issues](https://github.org/fireflyframework-oss/fireflyframework-client/issues)
- 💬 [Discussions](https://github.org/fireflyframework-oss/fireflyframework-client/discussions)

---

**Happy Coding! 🚀**

