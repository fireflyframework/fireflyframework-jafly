# OAuth2 Client Helper Guide

Complete guide for using the **best-in-class** OAuth2 client helper in the Firefly Common Client Library.

---

## Table of Contents

1. [Overview](#overview)
2. [When to Use OAuth2 Helper](#when-to-use-oauth2-helper)
3. [Quick Start](#quick-start)
4. [Advanced Configuration](#advanced-configuration)
5. [Client Credentials Flow](#client-credentials-flow)
6. [Password Grant Flow](#password-grant-flow)
7. [Refresh Token Flow](#refresh-token-flow)
8. [Token Caching](#token-caching)
9. [Automatic Retry Logic](#automatic-retry-logic)
10. [Integration with REST Client](#integration-with-rest-client)
11. [Best Practices](#best-practices)
12. [Complete Examples](#complete-examples)

---

## Overview

The `OAuth2ClientHelper` provides a **production-ready**, enterprise-grade API for OAuth2 authentication flows with advanced features like automatic retry, intelligent token caching, and seamless integration with REST clients.

**Key Features**:
- ✅ **Client Credentials Flow** - Machine-to-machine authentication
- ✅ **Password Grant Flow** - User authentication (legacy systems)
- ✅ **Refresh Token Flow** - Automatic token refresh
- ✅ **NEW: Multi-scope token caching** - Cache tokens per scope
- ✅ **NEW: Automatic retry with exponential backoff** - Resilient token requests
- ✅ **NEW: Configurable timeout and retry policies** - Production-ready configuration
- ✅ **NEW: Automatic refresh token management** - Store and reuse refresh tokens
- ✅ **Reactive programming with `Mono<T>`** - Non-blocking operations
- ✅ **Seamless integration with RestClient** - Easy authentication
- ✅ **Thread-safe token management** - Concurrent request support
- ✅ **ID Token support** - OpenID Connect compatibility

**Note**: For production applications with complex OAuth2/OIDC needs (Authorization Code Flow, PKCE, Social Login), use **Spring Security OAuth2 Client**.

---

## When to Use OAuth2 Helper

### ✅ Use OAuth2 Helper When:

- You need machine-to-machine authentication (Client Credentials)
- You're authenticating users with username/password (Password Grant)
- You need to refresh access tokens
- You want simple OAuth2 integration with REST clients
- You're building microservice-to-microservice authentication

### ❌ Consider Alternatives When:

- You need Authorization Code Flow (use Spring Security OAuth2)
- You need PKCE support (use Spring Security OAuth2)
- You need OpenID Connect (use Spring Security OAuth2)
- You need social login (Google, Facebook, etc.)

---

## Quick Start

### Basic Setup

```java
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import reactor.core.publisher.Mono;

// Create OAuth2 helper with default configuration
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "your-client-id",
    "your-client-secret"
);

// Get access token (automatically cached)
Mono<String> tokenMono = oauth2.getClientCredentialsToken();

tokenMono.subscribe(token -> {
    System.out.println("Access Token: " + token);
});
```

### ✨ Production-Ready Setup (Recommended)

```java
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import org.fireflyframework.client.oauth2.OAuth2ClientHelper.OAuth2Config;
import java.time.Duration;

// Create advanced configuration
OAuth2Config config = OAuth2Config.builder()
    .timeout(Duration.ofMinutes(2))           // Request timeout
    .enableRetry(true)                        // Enable automatic retry
    .maxRetries(3)                            // Max retry attempts
    .retryBackoff(Duration.ofSeconds(1))      // Backoff between retries
    .tokenExpirationBuffer(120)               // Refresh 2 min before expiration
    .defaultHeader("User-Agent", "MyApp/1.0") // Custom headers
    .build();

// Create OAuth2 helper with advanced config
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "your-client-id",
    "your-client-secret",
    config
);

// Get token with automatic retry on failures
oauth2.getClientCredentialsToken("api.read api.write")
    .subscribe(token -> System.out.println("Token: " + token));
```

### With REST Client Integration

```java
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;

RestClient restClient = ServiceClient.rest("api-service")
    .baseUrl("https://api.example.com")
    .build();

// Get token and make authenticated request
oauth2.getClientCredentialsToken().flatMap(token ->
    restClient.get("/protected/resource", Resource.class)
        .withHeader("Authorization", "Bearer " + token)
        .execute()
).subscribe(resource -> {
    System.out.println("Resource: " + resource);
});
```

---

## Advanced Configuration

### OAuth2Config Builder

The `OAuth2Config` class provides advanced configuration options for production environments:

```java
OAuth2Config config = OAuth2Config.builder()
    .timeout(Duration.ofMinutes(2))           // Request timeout (default: 30s)
    .enableRetry(true)                        // Enable automatic retry (default: false)
    .maxRetries(3)                            // Max retry attempts (default: 3)
    .retryBackoff(Duration.ofSeconds(1))      // Backoff between retries (default: 500ms)
    .tokenExpirationBuffer(120)               // Refresh buffer in seconds (default: 60s)
    .defaultHeader("User-Agent", "MyApp/1.0") // Add custom headers
    .defaultHeader("X-Custom", "value")       // Multiple headers supported
    .build();

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "client-id",
    "client-secret",
    config
);
```

### Configuration Options Reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `timeout` | `Duration` | `30s` | Request timeout for token requests |
| `enableRetry` | `boolean` | `false` | Enable automatic retry on failures |
| `maxRetries` | `int` | `3` | Maximum number of retry attempts |
| `retryBackoff` | `Duration` | `500ms` | Initial backoff duration (exponential) |
| `tokenExpirationBuffer` | `int` | `60` | Seconds before expiration to refresh token |
| `defaultHeader` | `String, String` | - | Add custom headers to all requests |

### Basic Configuration

```java
// Simple configuration (no retry, default timeout)
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",  // Token endpoint
    "client-id",                              // Client ID
    "client-secret"                           // Client secret
);
```

### Validation

The helper validates all required parameters:

```java
// ✅ Valid configuration
new OAuth2ClientHelper("https://auth.example.com/token", "client-id", "secret");

// ❌ Invalid configurations (will throw IllegalArgumentException)
new OAuth2ClientHelper(null, "client-id", "secret");        // Null endpoint
new OAuth2ClientHelper("", "client-id", "secret");          // Empty endpoint
new OAuth2ClientHelper("https://...", null, "secret");      // Null client ID
new OAuth2ClientHelper("https://...", "", "secret");        // Empty client ID
new OAuth2ClientHelper("https://...", "client-id", null);   // Null secret
new OAuth2ClientHelper("https://...", "client-id", "");     // Empty secret
```

---

## Client Credentials Flow

The Client Credentials flow is used for machine-to-machine authentication.

### Basic Usage

```java
// Get token without scope
Mono<String> token = oauth2.getClientCredentialsToken();

token.subscribe(accessToken -> {
    System.out.println("Token: " + accessToken);
});
```

### With Scope

```java
// Get token with specific scope
Mono<String> token = oauth2.getClientCredentialsToken("read:users write:users");

token.subscribe(accessToken -> {
    System.out.println("Token with scope: " + accessToken);
});
```

### Get Full Token Response

```java
// Get complete token response (includes expires_in, token_type, etc.)
Mono<TokenResponse> response = oauth2.getTokenResponse(
    Map.of("grant_type", "client_credentials")
);

response.subscribe(tokenResponse -> {
    System.out.println("Access Token: " + tokenResponse.getAccessToken());
    System.out.println("Token Type: " + tokenResponse.getTokenType());
    System.out.println("Expires In: " + tokenResponse.getExpiresIn());
    System.out.println("Scope: " + tokenResponse.getScope());
});
```

---

## Password Grant Flow

The Password Grant flow authenticates users with username and password.

**Warning**: This flow is considered less secure. Use Authorization Code Flow when possible.

### Basic Usage

```java
// Authenticate user
Mono<String> token = oauth2.getPasswordGrantToken(
    "john.doe@example.com",  // Username
    "password123",            // Password
    null                      // No scope
);

token.subscribe(accessToken -> {
    System.out.println("User token: " + accessToken);
});
```

### With Scope

```java
// Authenticate with specific scope
Mono<String> token = oauth2.getPasswordGrantToken(
    "john.doe@example.com",
    "password123",
    "profile email"
);

token.subscribe(accessToken -> {
    System.out.println("User token: " + accessToken);
});
```

---

## Refresh Token Flow

Use refresh tokens to obtain new access tokens without re-authentication.

### Basic Usage

```java
// Refresh access token with explicit refresh token
Mono<String> newToken = oauth2.refreshToken("refresh-token-here");

newToken.subscribe(accessToken -> {
    System.out.println("New access token: " + accessToken);
});
```

### ✨ Automatic Refresh (NEW)

The helper automatically stores refresh tokens from password grant responses:

```java
// Password grant returns a refresh token
oauth2.getPasswordGrantToken("user@example.com", "password", null)
    .subscribe(token -> {
        System.out.println("Access token: " + token);
        // Refresh token is automatically stored internally
    });

// Later, use automatic refresh
oauth2.autoRefreshToken()
    .subscribe(newToken -> {
        System.out.println("Refreshed token: " + newToken);
    });

// Or get the cached refresh token
String refreshToken = oauth2.getCachedRefreshToken();
if (refreshToken != null) {
    oauth2.refreshToken(refreshToken).subscribe(...);
}
```

### Complete Flow with Refresh

```java
// Initial authentication
oauth2.getPasswordGrantToken("user@example.com", "password", null)
    .flatMap(token -> {
        // Use token for API calls
        return makeApiCall(token);
    })
    .onErrorResume(error -> {
        // If token expired, automatically refresh it
        return oauth2.autoRefreshToken()
            .flatMap(newToken -> makeApiCall(newToken));
    })
    .subscribe();
```

---

## Token Caching

The OAuth2 helper automatically caches tokens **per scope** to reduce unnecessary token requests.

### 💾 Multi-Scope Caching (NEW)

Tokens are cached separately for each scope combination:

```java
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(endpoint, clientId, clientSecret);

// Request token with scope "read"
oauth2.getClientCredentialsToken("read").subscribe(token1 -> {
    System.out.println("Read token: " + token1);
});

// Request token with scope "write" - different cache entry
oauth2.getClientCredentialsToken("write").subscribe(token2 -> {
    System.out.println("Write token: " + token2);
});

// Request token with scope "read" again - returns cached token
oauth2.getClientCredentialsToken("read").subscribe(token3 -> {
    System.out.println("Cached read token: " + token3);  // Same as token1
});
```

### Automatic Caching

```java
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(endpoint, clientId, clientSecret);

// First call - requests token from server
oauth2.getClientCredentialsToken().subscribe(token1 -> {
    System.out.println("Token 1: " + token1);
});

// Second call - returns cached token (if not expired)
oauth2.getClientCredentialsToken().subscribe(token2 -> {
    System.out.println("Token 2: " + token2);  // Same as token1
});
```

### Cache Management

```java
// Clear all cached tokens
oauth2.clearCache();

// Clear specific cached token by key
oauth2.clearCache("client_credentials:read write");

// Check cache size
int cacheSize = oauth2.getCacheSize();
System.out.println("Cached tokens: " + cacheSize);

// Check if a valid token is cached
boolean hasToken = oauth2.hasValidToken("client_credentials:");
if (!hasToken) {
    // Request new token
    oauth2.getClientCredentialsToken().subscribe(...);
}
```

### Token Expiration Buffer

Tokens are considered expired **before** their actual expiration time:

```java
OAuth2Config config = OAuth2Config.builder()
    .tokenExpirationBuffer(120)  // Refresh 2 minutes before expiration
    .build();

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(endpoint, clientId, clientSecret, config);

// If token expires in 90 seconds, it's considered expired
// A new token will be requested automatically
```

### Cache Expiration

Tokens are automatically invalidated based on the `expires_in` field from the OAuth2 server:

```java
// Token cached with expiration
oauth2.getClientCredentialsToken().subscribe(token -> {
    // Token is cached for duration specified by server (e.g., 3600 seconds)
});

// After expiration, new token is automatically requested
Thread.sleep(3700000);  // Wait for expiration
oauth2.getClientCredentialsToken().subscribe(token -> {
    // New token is fetched automatically
});
```

---

## 🔄 Automatic Retry Logic

The OAuth2 helper supports automatic retry with exponential backoff for resilient token requests.

### Retryable Errors

The helper automatically retries on the following errors:

- **5xx Server Errors** - Server-side failures (500, 502, 503, 504)
- **429 Too Many Requests** - Rate limiting
- **Timeout Exceptions** - Request timeouts
- **Connection Errors** - Network connectivity issues

**Non-retryable errors** (fail immediately):
- **4xx Client Errors** (except 429) - Invalid credentials, bad request
- **OAuth2 Errors** - Invalid grant, unsupported grant type

### Enable Retry

```java
OAuth2Config config = OAuth2Config.builder()
    .enableRetry(true)                    // Enable automatic retry
    .maxRetries(3)                        // Max 3 retry attempts
    .retryBackoff(Duration.ofSeconds(1))  // Start with 1s backoff (exponential)
    .build();

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "client-id",
    "client-secret",
    config
);

// Token requests will automatically retry on failures
oauth2.getClientCredentialsToken()
    .subscribe(
        token -> System.out.println("Token: " + token),
        error -> System.err.println("Failed after retries: " + error)
    );
```

### Retry Behavior

**Exponential Backoff**:
- Attempt 1: Immediate
- Attempt 2: Wait 1 second
- Attempt 3: Wait 2 seconds
- Attempt 4: Wait 4 seconds

```java
OAuth2Config config = OAuth2Config.builder()
    .enableRetry(true)
    .maxRetries(5)                         // Up to 5 retries
    .retryBackoff(Duration.ofMillis(500))  // Start with 500ms
    .build();

// Retry schedule: 0ms, 500ms, 1000ms, 2000ms, 4000ms, 8000ms
```

### Custom Retry Logic

For advanced retry scenarios, use the `getTokenWithRetry` method:

```java
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(endpoint, clientId, clientSecret);

// Custom retry logic
Mono<String> tokenWithRetry = oauth2.getTokenWithRetry(() ->
    oauth2.getClientCredentialsToken("custom.scope")
);

tokenWithRetry.subscribe(token -> {
    System.out.println("Token obtained with retry: " + token);
});
```

### Production Example

```java
// Production-ready configuration with retry
OAuth2Config config = OAuth2Config.builder()
    .timeout(Duration.ofMinutes(2))
    .enableRetry(true)
    .maxRetries(3)
    .retryBackoff(Duration.ofSeconds(1))
    .tokenExpirationBuffer(120)
    .build();

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "client-id",
    "client-secret",
    config
);

// Resilient token request with automatic retry
oauth2.getClientCredentialsToken("api.read api.write")
    .timeout(Duration.ofMinutes(5))  // Overall timeout including retries
    .subscribe(
        token -> {
            // Success - use token
            System.out.println("Token: " + token);
        },
        error -> {
            // Failed after all retries
            log.error("Failed to obtain token after retries", error);
        }
    );
```

---

## Integration with REST Client

### Basic Integration

```java
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(tokenEndpoint, clientId, clientSecret);
RestClient restClient = ServiceClient.rest("api").baseUrl(apiUrl).build();

// Make authenticated request
oauth2.getClientCredentialsToken().flatMap(token ->
    restClient.get("/users", UserList.class)
        .withHeader("Authorization", "Bearer " + token)
        .execute()
).subscribe(users -> {
    System.out.println("Users: " + users);
});
```

### Reusable Service Class

```java
@Component
public class ProtectedApiService {
    
    private final OAuth2ClientHelper oauth2;
    private final RestClient restClient;
    
    public ProtectedApiService(
        @Value("${oauth2.token-endpoint}") String tokenEndpoint,
        @Value("${oauth2.client-id}") String clientId,
        @Value("${oauth2.client-secret}") String clientSecret,
        @Value("${api.base-url}") String apiBaseUrl
    ) {
        this.oauth2 = new OAuth2ClientHelper(tokenEndpoint, clientId, clientSecret);
        this.restClient = ServiceClient.rest("protected-api")
            .baseUrl(apiBaseUrl)
            .build();
    }
    
    public Mono<User> getUser(String userId) {
        return oauth2.getClientCredentialsToken().flatMap(token ->
            restClient.get("/users/{id}", User.class)
                .withPathParam("id", userId)
                .withHeader("Authorization", "Bearer " + token)
                .execute()
        );
    }
    
    public Mono<User> createUser(User user) {
        return oauth2.getClientCredentialsToken().flatMap(token ->
            restClient.post("/users", User.class)
                .withBody(user)
                .withHeader("Authorization", "Bearer " + token)
                .execute()
        );
    }
}
```

### With Error Handling

```java
oauth2.getClientCredentialsToken()
    .flatMap(token ->
        restClient.get("/protected/resource", Resource.class)
            .withHeader("Authorization", "Bearer " + token)
            .execute()
    )
    .doOnError(error -> {
        if (error instanceof UnauthorizedException) {
            // Token might be invalid, clear cache
            oauth2.clearCache();
        }
    })
    .retry(1)  // Retry once with fresh token
    .subscribe(resource -> {
        System.out.println("Resource: " + resource);
    });
```

---

## Best Practices

### 1. Use Production-Ready Configuration

❌ **Bad**:
```java
// No retry, default timeout, no custom configuration
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/token",
    "client-id",
    "client-secret"
);
```

✅ **Good**:
```java
// Production-ready with retry, custom timeout, and buffer
OAuth2Config config = OAuth2Config.builder()
    .timeout(Duration.ofMinutes(2))
    .enableRetry(true)
    .maxRetries(3)
    .retryBackoff(Duration.ofSeconds(1))
    .tokenExpirationBuffer(120)
    .defaultHeader("User-Agent", "MyApp/1.0")
    .build();

OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/token",
    "client-id",
    "client-secret",
    config
);
```

### 2. Store Credentials Securely

❌ **Bad**:
```java
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/token",
    "hardcoded-client-id",      // Hardcoded credentials
    "hardcoded-secret"
);
```

✅ **Good**:
```java
@Configuration
public class OAuth2Configuration {

    @Bean
    public OAuth2ClientHelper oauth2Helper(
        @Value("${oauth2.token-endpoint}") String tokenEndpoint,
        @Value("${oauth2.client-id}") String clientId,
        @Value("${oauth2.client-secret}") String clientSecret
    ) {
        OAuth2Config config = OAuth2Config.builder()
            .timeout(Duration.ofMinutes(2))
            .enableRetry(true)
            .maxRetries(3)
            .retryBackoff(Duration.ofSeconds(1))
            .tokenExpirationBuffer(120)
            .build();

        return new OAuth2ClientHelper(tokenEndpoint, clientId, clientSecret, config);
    }
}
```

### 3. Reuse OAuth2 Helper Instances

❌ **Bad**:
```java
public Mono<User> getUser(String id) {
    OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(...);  // New instance each time
    return oauth2.getClientCredentialsToken().flatMap(...);
}
```

✅ **Good**:
```java
@Component
public class UserService {
    private final OAuth2ClientHelper oauth2;  // Singleton instance

    public UserService(OAuth2ClientHelper oauth2) {
        this.oauth2 = oauth2;
    }

    public Mono<User> getUser(String id) {
        return oauth2.getClientCredentialsToken().flatMap(...);
    }
}
```

### 4. Handle Token Expiration Gracefully

❌ **Bad**:
```java
// No error handling
public Mono<Resource> getResource() {
    return oauth2.getClientCredentialsToken()
        .flatMap(token -> makeApiCall(token));
}
```

✅ **Good**:
```java
// Automatic retry with fresh token on 401
public Mono<Resource> getResource() {
    return oauth2.getClientCredentialsToken()
        .flatMap(token -> makeApiCall(token))
        .onErrorResume(UnauthorizedException.class, error -> {
            // Clear cache and retry with fresh token
            oauth2.clearCache();
            return oauth2.getClientCredentialsToken()
                .flatMap(token -> makeApiCall(token));
        });
}
```

### 5. Use Appropriate Grant Types

- **Client Credentials**: For service-to-service communication
- **Password Grant**: Only when Authorization Code flow is not possible
- **Refresh Token**: To extend user sessions without re-authentication

### 6. Leverage Multi-Scope Caching

❌ **Bad**:
```java
// Requesting new token for each scope
oauth2.getClientCredentialsToken("read").subscribe(...);
oauth2.getClientCredentialsToken("write").subscribe(...);
oauth2.getClientCredentialsToken("read").subscribe(...);  // Requests new token again
```

✅ **Good**:
```java
// Tokens are cached per scope automatically
oauth2.getClientCredentialsToken("read").subscribe(...);   // Requests from server
oauth2.getClientCredentialsToken("write").subscribe(...);  // Requests from server
oauth2.getClientCredentialsToken("read").subscribe(...);   // Returns cached token
```

### 7. Monitor Cache Performance

```java
// Check cache metrics
int cacheSize = oauth2.getCacheSize();
log.info("OAuth2 cache size: {}", cacheSize);

// Clear cache periodically if needed
@Scheduled(cron = "0 0 * * * *")  // Every hour
public void clearOAuth2Cache() {
    oauth2.clearCache();
    log.info("OAuth2 cache cleared");
}
```

### 8. Implement Proper Error Handling

```java
oauth2.getClientCredentialsToken()
    .timeout(Duration.ofMinutes(5))  // Overall timeout
    .doOnError(error -> {
        log.error("Failed to obtain OAuth2 token", error);
        // Send alert, metrics, etc.
    })
    .onErrorResume(error -> {
        // Fallback or circuit breaker logic
        return Mono.error(new ServiceUnavailableException("Auth service down"));
    })
    .subscribe();
```

---

## Complete Examples

### Example 1: Production-Ready Microservice Authentication

```java
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import org.fireflyframework.client.oauth2.OAuth2ClientHelper.OAuth2Config;
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Service
public class OrderService {

    private final OAuth2ClientHelper oauth2;
    private final RestClient paymentClient;
    private final RestClient inventoryClient;

    public OrderService(
        @Value("${oauth2.token-endpoint}") String tokenEndpoint,
        @Value("${oauth2.client-id}") String clientId,
        @Value("${oauth2.client-secret}") String clientSecret
    ) {
        // Production-ready OAuth2 configuration
        OAuth2Config config = OAuth2Config.builder()
            .timeout(Duration.ofMinutes(2))
            .enableRetry(true)
            .maxRetries(3)
            .retryBackoff(Duration.ofSeconds(1))
            .tokenExpirationBuffer(120)
            .defaultHeader("User-Agent", "OrderService/1.0")
            .build();

        this.oauth2 = new OAuth2ClientHelper(tokenEndpoint, clientId, clientSecret, config);

        this.paymentClient = ServiceClient.rest("payment-service")
            .baseUrl("https://payment.example.com")
            .build();

        this.inventoryClient = ServiceClient.rest("inventory-service")
            .baseUrl("https://inventory.example.com")
            .build();
    }

    public Mono<Order> createOrder(OrderRequest request) {
        return oauth2.getClientCredentialsToken("orders:write")
            .flatMap(token -> {
                // Check inventory
                return inventoryClient.get("/inventory/{productId}", InventoryStatus.class)
                    .withPathParam("productId", request.getProductId())
                    .withHeader("Authorization", "Bearer " + token)
                    .execute()
                    .flatMap(inventory -> {
                        if (!inventory.isAvailable()) {
                            return Mono.error(new OutOfStockException());
                        }
                        
                        // Process payment
                        return paymentClient.post("/payments", PaymentResponse.class)
                            .withBody(new PaymentRequest(request.getAmount()))
                            .withHeader("Authorization", "Bearer " + token)
                            .execute();
                    })
                    .map(payment -> new Order(request, payment));
            });
    }
}
```

---

## What's Included

✅ **Client Credentials Flow**: Machine-to-machine authentication  
✅ **Password Grant Flow**: User authentication with credentials  
✅ **Refresh Token Flow**: Token refresh without re-authentication  
✅ **Token Caching**: Automatic caching with expiration  
✅ **Reactive API**: Full `Mono<T>` support  
✅ **Thread-Safe**: Safe for concurrent use  

## What's NOT Included

❌ **Authorization Code Flow**: Use Spring Security OAuth2  
❌ **PKCE Support**: Use Spring Security OAuth2  
❌ **OpenID Connect**: Use Spring Security OAuth2  
❌ **Social Login**: Use Spring Security OAuth2  
❌ **Token Introspection**: Implement custom logic  
❌ **JWT Parsing**: Use dedicated JWT libraries  

**For Production**: Use **Spring Security OAuth2 Client** for full OAuth2/OIDC support.

---

**Next Steps**:
- [REST Client Guide](REST_CLIENT.md)
- [GraphQL Client Guide](GRAPHQL_CLIENT.md)
- [WebSocket Helper Guide](WEBSOCKET_HELPER.md)
- [File Upload Helper Guide](MULTIPART_HELPER.md)
- [Configuration Reference](CONFIGURATION.md)

