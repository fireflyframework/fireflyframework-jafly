# REST Client Guide

Complete guide for using the REST client to communicate with HTTP/REST services.

## Table of Contents

- [Quick Start](#quick-start)
- [Creating a REST Client](#creating-a-rest-client)
- [Configuration Options](#configuration-options)
- [Making Requests](#making-requests)
- [Advanced Features](#advanced-features)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

```java
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import reactor.core.publisher.Mono;

@Service
public class UserService {
    
    private final RestClient userClient;
    
    public UserService() {
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

---

## Creating a REST Client

### Basic Configuration

```java
RestClient client = ServiceClient.rest("my-service")
    .baseUrl("http://localhost:8080")      // Required
    .build();
```

### Full Configuration

```java
RestClient client = ServiceClient.rest("my-service")
    .baseUrl("http://localhost:8080")                          // Required: Base URL
    .timeout(Duration.ofSeconds(30))                           // Request timeout
    .maxConnections(100)                                       // Connection pool size
    .jsonContentType()                                         // Set JSON headers
    .defaultHeader("Authorization", "Bearer token")            // Default headers
    .defaultHeader("X-API-Key", "your-api-key")
    .build();
```

---

## Configuration Options

### Required Options

| Method | Description | Example |
|--------|-------------|---------|
| `baseUrl(String)` | Base URL of the service | `.baseUrl("http://api.example.com")` |

### Optional Options

| Method | Description | Default | Example |
|--------|-------------|---------|---------|
| `timeout(Duration)` | Request timeout | 30s | `.timeout(Duration.ofSeconds(45))` |
| `maxConnections(int)` | Connection pool size | 100 | `.maxConnections(200)` |
| `defaultHeader(String, String)` | Add default header | None | `.defaultHeader("X-Client", "MyApp")` |
| `jsonContentType()` | Set JSON content type | None | `.jsonContentType()` |
| `xmlContentType()` | Set XML content type | None | `.xmlContentType()` |
| `webClient(WebClient)` | Custom WebClient | Auto-created | `.webClient(customWebClient)` |
| `circuitBreakerManager(...)` | Custom circuit breaker | Auto-created | `.circuitBreakerManager(manager)` |

### Content Type Helpers

```java
// JSON (most common)
RestClient jsonClient = ServiceClient.rest("api")
    .baseUrl("http://api.example.com")
    .jsonContentType()  // Sets Content-Type: application/json, Accept: application/json
    .build();

// XML
RestClient xmlClient = ServiceClient.rest("api")
    .baseUrl("http://api.example.com")
    .xmlContentType()   // Sets Content-Type: application/xml, Accept: application/xml
    .build();

// Custom
RestClient customClient = ServiceClient.rest("api")
    .baseUrl("http://api.example.com")
    .defaultHeader("Content-Type", "application/vnd.api+json")
    .defaultHeader("Accept", "application/vnd.api+json")
    .build();
```

---

## Making Requests

### GET Requests

#### Simple GET

```java
Mono<User> user = client.get("/users/{id}", User.class)
    .withPathParam("id", "123")
    .execute();
```

#### GET with Query Parameters

```java
Mono<List<User>> users = client.get("/users", new TypeReference<List<User>>() {})
    .withQueryParam("page", 0)
    .withQueryParam("size", 10)
    .withQueryParam("sort", "name,asc")
    .execute();
```

#### GET with Multiple Path Parameters

```java
Mono<Order> order = client.get("/customers/{customerId}/orders/{orderId}", Order.class)
    .withPathParam("customerId", "C123")
    .withPathParam("orderId", "O456")
    .execute();
```

#### GET with Custom Headers

```java
Mono<User> user = client.get("/users/{id}", User.class)
    .withPathParam("id", "123")
    .withHeader("Accept-Language", "es-ES")
    .withHeader("X-Request-ID", UUID.randomUUID().toString())
    .execute();
```

### POST Requests

#### Simple POST

```java
CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");

Mono<User> created = client.post("/users", User.class)
    .withBody(request)
    .execute();
```

#### POST with Headers

```java
Mono<User> created = client.post("/users", User.class)
    .withBody(request)
    .withHeader("X-Idempotency-Key", UUID.randomUUID().toString())
    .withHeader("X-Request-ID", requestId)
    .execute();
```

#### POST with Path and Query Parameters

```java
Mono<Order> order = client.post("/customers/{customerId}/orders", Order.class)
    .withPathParam("customerId", "C123")
    .withQueryParam("notify", true)
    .withBody(orderRequest)
    .execute();
```

### PUT Requests

```java
UpdateUserRequest updateRequest = new UpdateUserRequest();
updateRequest.setName("Jane Doe");
updateRequest.setEmail("jane@example.com");

Mono<User> updated = client.put("/users/{id}", User.class)
    .withPathParam("id", "123")
    .withBody(updateRequest)
    .execute();
```

### PATCH Requests

```java
// Partial update
Map<String, Object> patch = Map.of(
    "status", "active",
    "lastLogin", Instant.now()
);

Mono<User> patched = client.patch("/users/{id}", User.class)
    .withPathParam("id", "123")
    .withBody(patch)
    .execute();
```

### DELETE Requests

```java
// DELETE with response
Mono<DeleteResponse> response = client.delete("/users/{id}", DeleteResponse.class)
    .withPathParam("id", "123")
    .execute();

// DELETE without response (Void)
Mono<Void> deleted = client.delete("/users/{id}", Void.class)
    .withPathParam("id", "123")
    .execute();
```

---

## Advanced Features

### Streaming Responses

```java
// Server-Sent Events
Flux<Event> events = client.stream("/events", Event.class);

events
    .doOnNext(event -> log.info("Received: {}", event))
    .doOnError(error -> log.error("Stream error", error))
    .subscribe();
```

### Custom Timeouts per Request

```java
Mono<User> user = client.get("/users/{id}", User.class)
    .withPathParam("id", "123")
    .withTimeout(Duration.ofSeconds(10))  // Override default timeout
    .execute();
```

### Working with Generic Types

```java
// For List<T>
Mono<List<User>> users = client.get("/users", new TypeReference<List<User>>() {})
    .execute();

// For Map<String, T>
Mono<Map<String, User>> userMap = client.get("/users/map", new TypeReference<Map<String, User>>() {})
    .execute();

// For complex nested types
Mono<Response<List<Order>>> response = client.get("/orders",
    new TypeReference<Response<List<Order>>>() {})
    .execute();
```

### Dynamic JSON Responses (Without DTOs)

For cases where you don't have or don't want to create DTOs, use `DynamicJsonResponse`:

```java
import org.fireflyframework.client.dynamic.DynamicJsonResponse;

// ✅ No DTO needed - Access fields dynamically
Mono<String> userName = client.get("/users/{id}", DynamicJsonResponse.class)
    .withPathParam("id", "123")
    .execute()
    .map(response -> response.getString("name"));

// Access nested fields with dot notation
Mono<String> city = client.get("/users/{id}", DynamicJsonResponse.class)
    .withPathParam("id", "123")
    .execute()
    .map(response -> response.getString("address.city"));

// Work with arrays using Flux
Flux<String> productNames = client.get("/products", DynamicJsonResponse.class)
    .execute()
    .flatMapMany(response -> response.toFlux("products"))
    .map(product -> product.getString("name"));

// Use DynamicObject for cleaner DTO-like interface
Mono<String> email = client.get("/users/{id}", DynamicJsonResponse.class)
    .withPathParam("id", "123")
    .execute()
    .map(DynamicJsonResponse::toDynamicObject)
    .map(user -> user.getString("email"));
```

**When to use DynamicJsonResponse**:
- ✅ Rapid prototyping without creating DTOs
- ✅ Working with third-party APIs with complex/changing schemas
- ✅ Only need to access a few fields from a large response
- ✅ Exploring unknown API structures

**When NOT to use**:
- ❌ Production code with well-defined schemas (use DTOs)
- ❌ When type safety is critical
- ❌ Performance-critical paths (DTOs are more efficient)

**Available methods**:
```java
DynamicJsonResponse response = ...;

// Primitive types
String name = response.getString("name");
Integer age = response.getInt("age");
Long id = response.getLong("id");
Double price = response.getDouble("price");
Boolean active = response.getBoolean("active");

// Nested objects
DynamicJsonResponse address = response.getObject("address");
String city = response.getString("address.city");  // Dot notation

// Arrays
List<String> tags = response.getList("tags", String.class);
List<DynamicJsonResponse> items = response.getObjectList("items");

// Convert to Flux for reactive processing
Flux<DynamicJsonResponse> itemsFlux = response.toFlux("items");

// Null-safe access with Optional
Optional<String> email = response.getStringOpt("email");

// Check field existence
if (response.has("premium")) {
    Boolean isPremium = response.getBoolean("premium");
}

// Convert to DTO when needed
User user = response.toObject(User.class);

// Use DynamicObject for cleaner interface
DynamicObject obj = response.toDynamicObject();
String name = obj.getString("name");
Integer age = obj.getInt("age");

// Generate dynamic class at runtime
Object dynamicInstance = response.toDynamicClass("User");
```

**Complete example**:
```java
@Service
public class GitHubService {

    private final RestClient client;

    public GitHubService() {
        this.client = ServiceClient.rest("github-api")
            .baseUrl("https://api.github.com")
            .build();
    }

    // Get repository info without creating a DTO
    public Mono<String> getRepoInfo(String owner, String repo) {
        return client.get("/repos/{owner}/{repo}", DynamicJsonResponse.class)
            .withPathParam("owner", owner)
            .withPathParam("repo", repo)
            .execute()
            .map(response -> {
                String name = response.getString("name");
                String description = response.getString("description");
                Integer stars = response.getInt("stargazers_count");
                Integer forks = response.getInt("forks_count");

                return String.format("%s: %s (⭐ %d, 🍴 %d)",
                    name, description, stars, forks);
            });
    }

    // Process array of commits
    public Flux<String> getCommitMessages(String owner, String repo) {
        return client.get("/repos/{owner}/{repo}/commits", DynamicJsonResponse.class)
            .withPathParam("owner", owner)
            .withPathParam("repo", repo)
            .execute()
            .flatMapMany(response -> response.toFlux("$"))  // Root is array
            .map(commit -> commit.getString("commit.message"));
    }
}
```

### Multiple Headers and Parameters

```java
Map<String, String> headers = Map.of(
    "X-Client-Version", "1.0.0",
    "X-Request-ID", requestId,
    "Accept-Language", "en-US"
);

Map<String, Object> queryParams = Map.of(
    "page", 0,
    "size", 20,
    "sort", "createdAt,desc"
);

Mono<List<User>> users = client.get("/users", new TypeReference<List<User>>() {})
    .withHeaders(headers)
    .withQueryParams(queryParams)
    .execute();
```

### Error Handling

```java
import org.fireflyframework.client.exception.*;

Mono<User> user = client.get("/users/{id}", User.class)
    .withPathParam("id", "123")
    .execute()
    .onErrorMap(ServiceNotFoundException.class, 
        ex -> new UserNotFoundException("User not found: 123"))
    .onErrorMap(ServiceUnavailableException.class,
        ex -> new ServiceDownException("User service is down"))
    .onErrorMap(ServiceAuthenticationException.class,
        ex -> new UnauthorizedException("Invalid credentials"))
    .retry(3)
    .timeout(Duration.ofSeconds(10));
```

### Health Checks

```java
// Check if service is ready
boolean ready = client.isReady();

// Perform health check
Mono<Void> healthCheck = client.healthCheck()
    .doOnSuccess(v -> log.info("Service is healthy"))
    .doOnError(e -> log.error("Service is unhealthy", e));
```

### Lifecycle Management

```java
// Get service information
String serviceName = client.getServiceName();
String baseUrl = client.getBaseUrl();
ClientType type = client.getClientType();  // Returns ClientType.REST

// Shutdown client (releases resources)
client.shutdown();
```

---

## Best Practices

### 1. Use Specific Types

```java
// ✅ GOOD - Type-safe
private final RestClient userClient;

// ❌ BAD - Requires casting
private final ServiceClient userClient;
```

### 2. Configure Once, Use Many Times

```java
@Configuration
public class ClientConfig {
    
    @Bean
    public RestClient userClient() {
        return ServiceClient.rest("user-service")
            .baseUrl("${user.service.url}")
            .timeout(Duration.ofSeconds(30))
            .jsonContentType()
            .build();
    }
}
```

### 3. Use Meaningful Service Names

```java
// ✅ GOOD - Clear purpose
ServiceClient.rest("user-service")
ServiceClient.rest("payment-gateway")
ServiceClient.rest("notification-service")

// ❌ BAD - Unclear
ServiceClient.rest("service1")
ServiceClient.rest("api")
```

### 4. Handle Errors Appropriately

```java
public Mono<User> getUser(String id) {
    return userClient.get("/users/{id}", User.class)
        .withPathParam("id", id)
        .execute()
        .onErrorMap(ServiceNotFoundException.class, 
            ex -> new UserNotFoundException("User " + id + " not found"))
        .doOnError(error -> log.error("Failed to get user {}", id, error));
}
```

### 5. Use Request IDs for Tracing

```java
public Mono<User> createUser(CreateUserRequest request) {
    String requestId = UUID.randomUUID().toString();
    
    return userClient.post("/users", User.class)
        .withBody(request)
        .withHeader("X-Request-ID", requestId)
        .execute()
        .doOnSuccess(user -> log.info("Created user {} with request ID {}", user.getId(), requestId));
}
```

---

## Troubleshooting

### Connection Refused

**Problem**: `Connection refused` error

**Solution**:
- Verify the `baseUrl` is correct
- Ensure the service is running
- Check network connectivity
- Verify firewall rules

### Timeout Errors

**Problem**: Requests timing out

**Solution**:
```java
// Increase timeout
RestClient client = ServiceClient.rest("slow-service")
    .baseUrl("http://slow-service:8080")
    .timeout(Duration.ofSeconds(60))  // Increase from default 30s
    .build();
```

### SSL/TLS Errors

**Problem**: SSL handshake failures

**Solution**:
```yaml
# In application.yml
firefly:
  service-client:
    security:
      tls-enabled: true
      trust-store-path: /path/to/truststore.jks
      trust-store-password: password
```

### Circuit Breaker Opening

**Problem**: Circuit breaker opens frequently

**Solution**:
```yaml
firefly:
  service-client:
    circuit-breaker:
      failure-rate-threshold: 60.0      # Increase from 50%
      minimum-number-of-calls: 10       # Increase from 5
```

### JSON Parsing Errors

**Problem**: Cannot deserialize response

**Solution**:
- Verify response type matches expected class
- Check JSON structure
- Use `TypeReference` for generic types
- Enable logging to see raw response:

```yaml
firefly:
  service-client:
    rest:
      logging-enabled: true
```

---

## What's Included

✅ **HTTP Verbs**: GET, POST, PUT, DELETE, PATCH  
✅ **Path Parameters**: Dynamic URL segments  
✅ **Query Parameters**: URL query strings  
✅ **Headers**: Default and per-request headers  
✅ **Request Bodies**: JSON, XML, or custom formats  
✅ **Response Types**: POJOs, Lists, Maps, generics  
✅ **Streaming**: Server-Sent Events support  
✅ **Circuit Breaker**: Automatic failure detection  
✅ **Health Checks**: Service availability monitoring  
✅ **Timeouts**: Configurable request timeouts  
✅ **Connection Pooling**: Efficient resource usage  
✅ **Compression**: Automatic gzip support  
✅ **Reactive**: Non-blocking Mono/Flux responses  

## What's NOT Included (But We Provide Helpers!)

The REST client focuses on standard HTTP operations. For specialized use cases, we provide dedicated helper utilities with their own comprehensive documentation:

### 🔌 WebSocket Support

✅ **Helper Available**: `WebSocketClientHelper`

```java
import org.fireflyframework.client.websocket.WebSocketClientHelper;

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/notifications");
wsHelper.receiveMessages(message -> {
    System.out.println("Notification: " + message);
}).subscribe();
```

**When to use**: Real-time bidirectional communication, live updates, chat applications.

📖 **[Complete WebSocket Guide →](WEBSOCKET_HELPER.md)**

---

### 📁 File Uploads

✅ **Helper Available**: `MultipartUploadHelper`

```java
import org.fireflyframework.client.multipart.MultipartUploadHelper;

MultipartUploadHelper uploader = new MultipartUploadHelper("http://localhost:8080");
Mono<UploadResponse> response = uploader.uploadFile(
    "/api/upload",
    new File("/path/to/document.pdf"),
    "document",
    UploadResponse.class
);
```

**When to use**: File uploads, document management, image uploads.

📖 **[Complete File Upload Guide →](MULTIPART_HELPER.md)**

---

### 🔐 OAuth2 Authentication

✅ **Helper Available**: `OAuth2ClientHelper`

```java
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "client-id",
    "client-secret"
);

// Get token and use with RestClient
oauth2.getClientCredentialsToken().flatMap(token ->
    restClient.get("/protected/resource", Resource.class)
        .withHeader("Authorization", "Bearer " + token)
        .execute()
).subscribe();
```

**When to use**: OAuth2 client credentials, password grant, token refresh.

**For production**: Use Spring Security OAuth2 Client for full OAuth2/OIDC support.

📖 **[Complete OAuth2 Guide →](OAUTH2_HELPER.md)**

---

### 🔍 GraphQL

✅ **Helper Available**: `GraphQLClientHelper`

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper;

GraphQLClientHelper graphql = new GraphQLClientHelper("https://api.example.com/graphql");

String query = """
    query GetUser($id: ID!) {
        user(id: $id) {
            id
            name
            email
        }
    }
""";

Mono<User> user = graphql.query(query, Map.of("id", "123"), "user", User.class);
```

**When to use**: GraphQL APIs, flexible data fetching.

**For production**: Consider Spring for GraphQL or Netflix DGS Framework for complex GraphQL needs.

📖 **[Complete GraphQL Guide →](GRAPHQL_CLIENT.md)**

---

**Next Steps**:

**Core Clients**:
- [gRPC Client Guide](GRPC_CLIENT.md)
- [SOAP Client Guide](SOAP_CLIENT.md)

**Helper Utilities**:
- [GraphQL Client Guide](GRAPHQL_CLIENT.md)
- [WebSocket Helper Guide](WEBSOCKET_HELPER.md)
- [OAuth2 Helper Guide](OAUTH2_HELPER.md)
- [File Upload Helper Guide](MULTIPART_HELPER.md)

**Configuration**:
- [Configuration Reference](CONFIGURATION.md)

