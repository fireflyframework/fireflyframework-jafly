# Firefly Common Client Library - Documentation

> **Best-in-class service client framework for REST, gRPC, and SOAP**

## 📚 Documentation Index

This documentation is organized by client type to help you quickly find what you need.

### Getting Started
- **[Quick Start Guide](#quick-start)** - Get up and running in 5 minutes
- **[Installation](#installation)** - Add the library to your project

### Core Client Types
- **[REST Client Guide](REST_CLIENT.md)** - Complete guide for HTTP/REST services
- **[gRPC Client Guide](GRPC_CLIENT.md)** - Complete guide for gRPC services
- **[SOAP Client Guide](SOAP_CLIENT.md)** - Complete guide for SOAP/WSDL services

### Helper Utilities
- **[GraphQL Client Guide](GRAPHQL_CLIENT.md)** - GraphQL queries and mutations
- **[WebSocket Helper Guide](WEBSOCKET_HELPER.md)** - Real-time bidirectional communication
- **[OAuth2 Helper Guide](OAUTH2_HELPER.md)** - OAuth2 authentication flows
- **[File Upload Helper Guide](MULTIPART_HELPER.md)** - Multipart file uploads

### Configuration & Advanced Topics
- **[Configuration Reference](CONFIGURATION.md)** - All configuration options

---

## Quick Start

### Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Your First REST Client

```java
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;

@Service
public class UserService {
    
    private final RestClient userClient;
    
    public UserService() {
        // ✅ CORRECT: Use RestClient type, not ServiceClient
        this.userClient = ServiceClient.rest("user-service")
            .baseUrl("http://localhost:8080")
            .jsonContentType()
            .build();
    }
    
    public Mono<User> getUser(String id) {
        return userClient.get("/users/{id}", User.class)
            .withPathParam("id", id)
            .execute();
    }
}
```

### Your First gRPC Client

```java
import org.fireflyframework.client.GrpcClient;
import org.fireflyframework.client.ServiceClient;

@Service
public class PaymentService {
    
    private final GrpcClient<PaymentServiceStub> paymentClient;
    
    public PaymentService() {
        // ✅ CORRECT: Use GrpcClient<T> type, not ServiceClient
        this.paymentClient = ServiceClient.grpc("payment-service", PaymentServiceStub.class)
            .address("localhost:9090")
            .stubFactory(channel -> PaymentServiceGrpc.newStub(channel))
            .build();
    }
    
    public Mono<PaymentResponse> processPayment(PaymentRequest request) {
        return paymentClient.unary(stub -> stub.processPayment(request));
    }
}
```

### Your First SOAP Client

```java
import org.fireflyframework.client.SoapClient;
import org.fireflyframework.client.ServiceClient;

@Service
public class EquifaxService {
    
    private final SoapClient equifaxClient;
    
    public EquifaxService() {
        // ✅ CORRECT: Use SoapClient type, not ServiceClient
        this.equifaxClient = ServiceClient.soap("equifax-spain")
            .wsdlUrl("https://uat2.equifax.es/icflex/api?WSDL")
            .credentials("username", "password")
            .defaultHeader("dptOrchestrationCode", "equifax-comm360")
            .build();
    }
    
    public Mono<CreditReport> getCreditReport(CreditRequest request) {
        return equifaxClient.invokeAsync("GetCreditReport", request, CreditReport.class);
    }
}
```

---

## ⚠️ Common Mistakes to Avoid

### ❌ WRONG: Using ServiceClient type

```java
// ❌ DON'T DO THIS - You'll need to cast later!
private ServiceClient serviceClient;

public SpainEquifaxClient(ServiceClient serviceClient) {
    this.serviceClient = ServiceClient.rest("equifax-spain")
        .baseUrl("https://uat2.equifax.es/icflex/api")
        .build();
}

// Later you'll be forced to do this ugly cast:
RestServiceClientImpl restClient = (RestServiceClientImpl) serviceClient;  // 😢
```

### ✅ CORRECT: Using specific client types

```java
// ✅ DO THIS - Type-safe, no casting needed!
private final RestClient restClient;

public SpainEquifaxClient() {
    this.restClient = ServiceClient.rest("equifax-spain")
        .baseUrl("https://uat2.equifax.es/icflex/api")
        .defaultHeader("Authorization", "Basic " + credentials)
        .defaultHeader("dptOrchestrationCode", "equifax-comm360")
        .jsonContentType()
        .build();
}

// Use it directly - no casting!
Mono<Response> response = restClient.get("/endpoint", Response.class).execute();
```

---

## 🎯 Which Client Type Should I Use?

| Protocol | Client Type | When to Use | Example |
|----------|-------------|-------------|---------|
| **REST/HTTP** | `RestClient` | HTTP APIs, RESTful services | User service, Payment API |
| **gRPC** | `GrpcClient<T>` | High-performance RPC, streaming | Real-time notifications, Analytics |
| **SOAP** | `SoapClient` | Legacy SOAP/WSDL services | Enterprise APIs, Government services |

---

## 📖 Detailed Guides

### REST Client
👉 **[Read the complete REST Client Guide](REST_CLIENT.md)**

Covers:
- HTTP verbs (GET, POST, PUT, DELETE, PATCH)
- Request/response handling
- Headers and authentication
- Query parameters and path variables
- Streaming responses
- File uploads/downloads

### gRPC Client
👉 **[Read the complete gRPC Client Guide](GRPC_CLIENT.md)**

Covers:
- Stub configuration
- Unary calls
- Server streaming
- Client streaming
- Bidirectional streaming
- Metadata and interceptors

### SOAP Client
👉 **[Read the complete SOAP Client Guide](SOAP_CLIENT.md)**

Covers:
- WSDL configuration
- WS-Security authentication
- Operation invocation
- MTOM/XOP attachments
- SSL/TLS configuration
- Custom headers and namespaces

---

## 🔧 Configuration

### Basic Configuration

```yaml
firefly:
  service-client:
    enabled: true
    default-timeout: 30s
    environment: DEVELOPMENT
```

### Per-Protocol Configuration

```yaml
firefly:
  service-client:
    rest:
      max-connections: 100
      response-timeout: 30s
      compression-enabled: true
    
    grpc:
      keep-alive-time: 5m
      max-inbound-message-size: 4194304
    
    soap:
      default-timeout: 30s
      mtom-enabled: false
      schema-validation-enabled: true
```

👉 **[See complete configuration reference](CONFIGURATION.md)**

---

## 🛡️ Built-in Resilience

All clients include:

- ✅ **Circuit Breaker** - Automatic failure detection and recovery
- ✅ **Health Checks** - Monitor service availability
- ✅ **Timeouts** - Configurable request timeouts
- ✅ **Retry Logic** - Automatic retry with backoff
- ✅ **Metrics** - Performance monitoring and observability

---

## 🧪 Testing

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    private MockWebServer mockWebServer;
    private RestClient client;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        client = ServiceClient.rest("user-service")
            .baseUrl(mockWebServer.url("/").toString())
            .build();
    }
    
    @Test
    void shouldGetUser() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"id\":\"123\",\"name\":\"John\"}"));
        
        StepVerifier.create(client.get("/users/123", User.class).execute())
            .assertNext(user -> assertThat(user.getName()).isEqualTo("John"))
            .verifyComplete();
    }
}
```

👉 **[Read the complete Testing Guide](TESTING.md)**

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.org/fireflyframework-oss/fireflyframework-client/issues)
- **Documentation**: This directory
- **Examples**: See `/examples` directory in the repository

---

**Built with ❤️ by Firefly Software Foundation**

