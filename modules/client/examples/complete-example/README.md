# Firefly Common Client - Complete Example

This is a complete example application demonstrating all features of the Firefly Common Client Library.

## Features Demonstrated

This example showcases:

- ✅ **REST Client** - Full CRUD operations with JSONPlaceholder API
- ✅ **GraphQL Client** - Queries, mutations, and batch operations
- ✅ **OAuth2 Client** - Token management with multi-scope caching
- ✅ **Multipart Upload** - File uploads with progress tracking
- ✅ **WebSocket Client** - Real-time communication with reconnection
- ✅ **Circuit Breaker** - Resilience patterns
- ✅ **Retry Logic** - Automatic retry with exponential backoff
- ✅ **Observability** - Metrics, health checks, and logging
- ✅ **Security** - TLS/SSL configuration support

## Prerequisites

- Java 21 or higher
- Maven 3.8 or higher
- Internet connection (for API calls)

## Quick Start

### 1. Build the Project

```bash
mvn clean install
```

### 2. Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

### 3. Access Health Checks

```bash
curl http://localhost:8080/actuator/health
```

### 4. Access Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

## Project Structure

```
complete-example/
├── src/
│   ├── main/
│   │   ├── java/org/fireflyframework/example/
│   │   │   ├── ExampleApplication.java          # Main application
│   │   │   ├── model/                            # Data models
│   │   │   │   ├── User.java
│   │   │   │   ├── Address.java
│   │   │   │   ├── Company.java
│   │   │   │   ├── Geo.java
│   │   │   │   ├── CreateUserRequest.java
│   │   │   │   └── UploadResponse.java
│   │   │   └── service/                          # Service examples
│   │   │       ├── UserService.java              # REST client example
│   │   │       ├── GraphQLExampleService.java    # GraphQL client example
│   │   │       ├── OAuth2ExampleService.java     # OAuth2 client example
│   │   │       ├── FileUploadExampleService.java # Multipart upload example
│   │   │       └── WebSocketExampleService.java  # WebSocket client example
│   │   └── resources/
│   │       └── application.yml                   # Configuration
│   └── test/
│       └── java/                                 # Tests (to be added)
├── pom.xml                                       # Maven configuration
└── README.md                                     # This file
```

## Service Examples

### UserService (REST Client)

Demonstrates REST client usage with JSONPlaceholder API:

```java
@Autowired
private UserService userService;

// Get a user
User user = userService.getUser(1L);

// Get all users
List<User> users = userService.getAllUsers();

// Create a user
CreateUserRequest request = CreateUserRequest.builder()
    .name("John Doe")
    .email("john@example.com")
    .build();
User newUser = userService.createUser(request);

// Update a user
User updatedUser = userService.updateUser(1L, request);

// Delete a user
userService.deleteUser(1L);
```

### GraphQLExampleService

Demonstrates GraphQL client usage:

```java
@Autowired
private GraphQLExampleService graphqlService;

// Query a user
User user = graphqlService.getUser("123");

// Create a user (mutation)
User newUser = graphqlService.createUser("Jane Doe", "jane@example.com");

// Batch query
Map<String, User> users = graphqlService.batchGetUsers("1", "2", "3");
```

### OAuth2ExampleService

Demonstrates OAuth2 token management:

```java
@Autowired
private OAuth2ExampleService oauth2Service;

// Get access token
String token = oauth2Service.getAccessToken();

// Get token with specific scopes
String token = oauth2Service.getAccessTokenWithScopes("read", "write");

// Get ID token (OpenID Connect)
String idToken = oauth2Service.getIdToken();

// Check if valid token exists
boolean hasToken = oauth2Service.hasValidToken("read");

// Clear cache
oauth2Service.clearCache();
```

### FileUploadExampleService

Demonstrates file upload with progress tracking:

```java
@Autowired
private FileUploadExampleService uploadService;

// Upload a file
File file = new File("/path/to/file.pdf");
UploadResponse response = uploadService.uploadFile(file);

// Upload large file (chunked)
UploadResponse response = uploadService.uploadLargeFile(file);

// Upload multiple files in parallel
List<File> files = Arrays.asList(file1, file2, file3);
List<UploadResponse> responses = uploadService.uploadMultipleFiles(files);

// Upload with metadata
UploadResponse response = uploadService.uploadFileWithMetadata(
    file, 
    "Important document", 
    "legal"
);

// Validate file
boolean isValid = uploadService.validateFile(file);
```

### WebSocketExampleService

Demonstrates WebSocket client usage:

```java
@Autowired
private WebSocketExampleService wsService;

// Connect
wsService.connect();

// Send message
wsService.sendMessage("Hello, WebSocket!");

// Send binary message
byte[] data = "Binary data".getBytes();
wsService.sendBinaryMessage(data);

// Check connection
boolean connected = wsService.isConnected();

// Get statistics
wsService.logStatistics();

// Disconnect
wsService.disconnect();
```

## Configuration

The application is configured via `application.yml`. Key configuration sections:

### Firefly Service Client

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

### OAuth2

```yaml
oauth2:
  token-endpoint: https://auth.example.com/oauth/token
  client-id: demo-client
  client-secret: demo-secret
```

### Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

## Testing

Run tests with:

```bash
mvn test
```

## Monitoring

### Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Service client health
curl http://localhost:8080/actuator/health/serviceClient
```

### Metrics

```bash
# All metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/http.client.requests

# Prometheus format
curl http://localhost:8080/actuator/prometheus
```

## Production Deployment

For production deployment:

1. **Enable TLS/SSL**:

```yaml
firefly:
  service-client:
    security:
      tls-enabled: true
      trust-store-path: ${TRUST_STORE_PATH}
      trust-store-password: ${TRUST_STORE_PASSWORD}
```

2. **Set environment variables**:

```bash
export TRUST_STORE_PATH=/path/to/truststore.jks
export TRUST_STORE_PASSWORD=changeit
export OAUTH2_CLIENT_SECRET=production-secret
```

3. **Adjust circuit breaker settings**:

```yaml
firefly:
  service-client:
    circuit-breaker:
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 20
      wait-duration-in-open-state: 120s
```

4. **Configure logging**:

```yaml
logging:
  level:
    root: WARN
    org.fireflyframework: INFO
```

## Troubleshooting

### Connection Timeout

If you experience connection timeouts, increase the timeout:

```yaml
firefly:
  service-client:
    rest:
      default-timeout: 60s
```

### Circuit Breaker Opens Too Quickly

Adjust the failure rate threshold:

```yaml
firefly:
  service-client:
    circuit-breaker:
      failure-rate-threshold: 75.0
```

### OAuth2 Token Errors

Check your OAuth2 configuration:

```yaml
oauth2:
  token-endpoint: https://auth.example.com/oauth/token
  client-id: your-client-id
  client-secret: your-client-secret
```

## Next Steps

- Read the [full documentation](../../docs/README.md)
- Explore [security features](../../docs/SECURITY.md)
- Learn about [observability](../../docs/OBSERVABILITY.md)
- Check the [migration guide](../../docs/MIGRATION_GUIDE.md)

## License

This example is part of the Firefly Common Client Library.

## Support

For issues and questions:
- GitHub Issues: https://github.org/fireflyframework-oss/fireflyframework-client/issues
- Documentation: https://github.org/fireflyframework-oss/fireflyframework-client/docs

