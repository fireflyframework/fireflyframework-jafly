# GraphQL Client Guide

Complete guide for using the GraphQL client helper in the Firefly Common Client Library.

---

## Table of Contents

1. [Overview](#overview)
2. [When to Use GraphQL Client](#when-to-use-graphql-client)
3. [Quick Start](#quick-start)
4. [Configuration](#configuration)
5. [Executing Queries](#executing-queries)
6. [Executing Mutations](#executing-mutations)
7. [Working with Variables](#working-with-variables)
8. [Error Handling](#error-handling)
9. [Advanced Usage](#advanced-usage)
10. [🚀 Batch Operations](#-batch-operations)
11. [💾 Query Caching](#-query-caching)
12. [🔄 Automatic Retry Logic](#-automatic-retry-logic)
13. [Best Practices](#best-practices)
14. [Complete Examples](#complete-examples)

---

## Overview

The `GraphQLClientHelper` provides a **production-ready, enterprise-grade** API for interacting with GraphQL endpoints with advanced features like retry logic, query caching, and batch operations.

**Key Features**:
- ✅ Reactive programming with `Mono<T>` and `Flux<T>`
- ✅ Type-safe response handling
- ✅ Variable binding support
- ✅ Custom headers per request
- ✅ Configurable timeouts
- ✅ Automatic error parsing
- ✅ Data extraction utilities
- ✅ **NEW: Automatic retry with exponential backoff**
- ✅ **NEW: Query caching for improved performance**
- ✅ **NEW: Batch operations support**
- ✅ **NEW: Builder pattern for advanced configuration**
- ✅ **NEW: Java Time API support (LocalDate, LocalDateTime, etc.)**
- ✅ **NEW: Smart error handling with retryable error detection**

**Note**: For production applications with complex GraphQL needs, consider using dedicated frameworks like:
- Spring for GraphQL
- Netflix DGS Framework
- GraphQL Java

---

## When to Use GraphQL Client

### ✅ Use GraphQL Client When:

- You need to consume third-party GraphQL APIs
- You want flexible data fetching with queries
- You need to perform mutations on GraphQL endpoints
- You're building a GraphQL client for microservices
- You want reactive, non-blocking GraphQL operations

### ❌ Consider Alternatives When:

- You need GraphQL subscriptions (use WebSocket helper + GraphQL)
- You're building a GraphQL server (use Spring for GraphQL)
- You need advanced features like schema introspection, code generation
- You require federation or stitching capabilities

---

## Quick Start

### Basic Setup

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper;
import reactor.core.publisher.Mono;

// Create GraphQL client
GraphQLClientHelper graphql = new GraphQLClientHelper("https://api.example.com/graphql");

// Execute a simple query
String query = """
    query {
        users {
            id
            name
            email
        }
    }
""";

Mono<GraphQLResponse<Object>> response = graphql.query(query);
```

### With Type-Safe Response

```java
// Define your response type
public class User {
    private String id;
    private String name;
    private String email;
    
    // getters and setters
}

// Execute query with type extraction
String query = """
    query GetUsers {
        users {
            id
            name
            email
        }
    }
""";

Mono<List<User>> users = graphql.query(query, null, "users", new TypeReference<List<User>>() {});
```

---

## Configuration

### Basic Configuration

```java
// Simple configuration
GraphQLClientHelper graphql = new GraphQLClientHelper("https://api.example.com/graphql");
```

### Advanced Configuration (Legacy)

```java
import java.time.Duration;
import java.util.Map;

// With custom timeout and headers
Duration timeout = Duration.ofSeconds(60);
Map<String, String> defaultHeaders = Map.of(
    "Authorization", "Bearer your-token-here",
    "X-API-Version", "v1",
    "X-Client-Name", "my-app"
);

GraphQLClientHelper graphql = new GraphQLClientHelper(
    "https://api.example.com/graphql",
    timeout,
    defaultHeaders
);
```

### ✨ Production-Ready Configuration (Recommended)

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper.GraphQLConfig;
import java.time.Duration;

// Enterprise-grade configuration with all features
GraphQLConfig config = GraphQLConfig.builder()
    .timeout(Duration.ofMinutes(2))           // 2 minute timeout for complex queries
    .enableRetry(true)                        // Enable automatic retry on failures
    .maxRetries(3)                            // Retry up to 3 times
    .retryBackoff(Duration.ofSeconds(1))      // 1 second initial backoff (exponential)
    .enableQueryCache(true)                   // Cache query results for performance
    .defaultHeader("Authorization", "Bearer token")
    .defaultHeader("X-Client-Name", "my-service")
    .defaultHeader("X-API-Version", "v2")
    .build();

GraphQLClientHelper graphql = new GraphQLClientHelper(
    "https://api.example.com/graphql",
    config
);
```

### Configuration Options Reference

| Option | Default | Description |
|--------|---------|-------------|
| `timeout` | 30 seconds | Maximum time to wait for a response |
| `enableRetry` | false | Enable automatic retry on 5xx errors and timeouts |
| `maxRetries` | 3 | Maximum number of retry attempts |
| `retryBackoff` | 500ms | Initial backoff duration (uses exponential backoff) |
| `enableQueryCache` | false | Cache query results (only for queries without variables/headers) |
| `defaultHeaders` | empty | Headers included in all requests |

**Retry Behavior**:
- Retries on: 5xx server errors, 429 Too Many Requests, timeouts, connection errors
- Does NOT retry on: 4xx client errors (except 429), GraphQL errors in response
- Uses exponential backoff: 1s, 2s, 4s, 8s, etc.

### With OAuth2 Integration

```java
import org.fireflyframework.client.oauth2.OAuth2ClientHelper;

// Setup OAuth2
OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
    "https://auth.example.com/oauth/token",
    "client-id",
    "client-secret"
);

// Get token and create GraphQL client
oauth2.getClientCredentialsToken().flatMap(token -> {
    Map<String, String> headers = Map.of("Authorization", "Bearer " + token);
    GraphQLClientHelper graphql = new GraphQLClientHelper(
        "https://api.example.com/graphql",
        Duration.ofSeconds(30),
        headers
    );
    
    return graphql.query("{ users { id name } }");
}).subscribe();
```

---

## Executing Queries

### Simple Query

```java
String query = """
    query {
        currentUser {
            id
            name
            email
        }
    }
""";

graphql.query(query)
    .subscribe(response -> {
        if (!response.hasErrors()) {
            System.out.println("Data: " + response.getData());
        }
    });
```

### Query with Variables

```java
String query = """
    query GetUser($id: ID!) {
        user(id: $id) {
            id
            name
            email
            posts {
                title
                content
            }
        }
    }
""";

Map<String, Object> variables = Map.of("id", "123");

graphql.query(query, variables)
    .subscribe(response -> {
        System.out.println("User: " + response.getData());
    });
```

### Query with Data Extraction

```java
String query = """
    query GetUser($id: ID!) {
        user(id: $id) {
            id
            name
            email
        }
    }
""";

Map<String, Object> variables = Map.of("id", "123");

// Extract directly to User object
Mono<User> user = graphql.query(query, variables, "user", User.class);

user.subscribe(u -> System.out.println("User name: " + u.getName()));
```

### Nested Data Extraction

```java
String query = """
    query {
        data {
            user {
                id
                name
            }
        }
    }
""";

// Navigate nested path: data.user
Mono<User> user = graphql.query(query, null, "data.user", User.class);
```

---

## Executing Mutations

### Simple Mutation

```java
String mutation = """
    mutation {
        createUser(input: {
            name: "John Doe"
            email: "john@example.com"
        }) {
            id
            name
            email
        }
    }
""";

graphql.mutate(mutation)
    .subscribe(response -> {
        System.out.println("Created user: " + response.getData());
    });
```

### Mutation with Variables

```java
String mutation = """
    mutation CreateUser($input: CreateUserInput!) {
        createUser(input: $input) {
            id
            name
            email
            createdAt
        }
    }
""";

Map<String, Object> variables = Map.of(
    "input", Map.of(
        "name", "John Doe",
        "email", "john@example.com",
        "role", "USER"
    )
);

graphql.mutate(mutation, variables)
    .subscribe(response -> {
        System.out.println("User created: " + response.getData());
    });
```

### Mutation with Type-Safe Response

```java
String mutation = """
    mutation UpdateUser($id: ID!, $input: UpdateUserInput!) {
        updateUser(id: $id, input: $input) {
            id
            name
            email
            updatedAt
        }
    }
""";

Map<String, Object> variables = Map.of(
    "id", "123",
    "input", Map.of("name", "Jane Doe")
);

Mono<User> updatedUser = graphql.mutate(mutation, variables, "updateUser", User.class);

updatedUser.subscribe(user -> {
    System.out.println("Updated: " + user.getName());
});
```

---

## Working with Variables

### Simple Variables

```java
Map<String, Object> variables = Map.of(
    "id", "123",
    "name", "John Doe",
    "active", true
);
```

### Complex Variables

```java
Map<String, Object> variables = Map.of(
    "input", Map.of(
        "user", Map.of(
            "name", "John Doe",
            "email", "john@example.com",
            "age", 30
        ),
        "preferences", Map.of(
            "theme", "dark",
            "notifications", true
        ),
        "tags", List.of("developer", "java", "graphql")
    )
);
```

### Variables with Null Values

```java
Map<String, Object> variables = new HashMap<>();
variables.put("id", "123");
variables.put("name", null);  // Explicitly null
variables.put("email", "john@example.com");
```

---

## Error Handling

### Check for Errors

```java
graphql.query(query, variables)
    .subscribe(response -> {
        if (response.hasErrors()) {
            for (GraphQLError error : response.getErrors()) {
                System.err.println("GraphQL Error: " + error.getMessage());
                System.err.println("Path: " + Arrays.toString(error.getPath()));
            }
        } else {
            System.out.println("Success: " + response.getData());
        }
    });
```

### Handle Errors with Reactive Operators

```java
graphql.query(query, variables, "user", User.class)
    .doOnError(error -> {
        if (error instanceof GraphQLException) {
            System.err.println("GraphQL error: " + error.getMessage());
        }
    })
    .onErrorReturn(new User())  // Fallback user
    .subscribe(user -> {
        System.out.println("User: " + user.getName());
    });
```

### Retry on Error

```java
graphql.query(query, variables)
    .retry(3)  // Retry up to 3 times
    .subscribe(response -> {
        System.out.println("Response: " + response.getData());
    });
```

---

## Advanced Usage

### Custom Headers per Request

```java
Map<String, String> customHeaders = Map.of(
    "X-Request-ID", UUID.randomUUID().toString(),
    "X-Correlation-ID", "correlation-123"
);

graphql.execute(query, variables, customHeaders)
    .subscribe(response -> {
        System.out.println("Response: " + response.getData());
    });
```

### Combining Multiple Queries

```java
Mono<User> userMono = graphql.query(userQuery, userVars, "user", User.class);
Mono<List<Post>> postsMono = graphql.query(postsQuery, postsVars, "posts",
    new TypeReference<List<Post>>() {});

Mono.zip(userMono, postsMono)
    .subscribe(tuple -> {
        User user = tuple.getT1();
        List<Post> posts = tuple.getT2();
        System.out.println("User: " + user.getName() + ", Posts: " + posts.size());
    });
```

### Reactive Chaining

```java
// Chain GraphQL operations
graphql.query(getUserQuery, Map.of("id", "123"), "user", User.class)
    .flatMap(user -> {
        // Use user data to fetch posts
        Map<String, Object> vars = Map.of("userId", user.getId());
        return graphql.query(getPostsQuery, vars, "posts", new TypeReference<List<Post>>() {});
    })
    .subscribe(posts -> {
        System.out.println("User's posts: " + posts.size());
    });
```

---

## 🚀 Batch Operations

Execute multiple GraphQL queries in parallel for improved performance.

### Using GraphQLRequest Builder

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper.GraphQLRequest;
import java.util.List;

// Build multiple requests
List<GraphQLRequest> requests = List.of(
    GraphQLRequest.builder()
        .query("query GetUser($id: ID!) { user(id: $id) { id name } }")
        .variable("id", "123")
        .header("X-Request-ID", "req-1")
        .build(),

    GraphQLRequest.builder()
        .query("query GetPosts($limit: Int!) { posts(limit: $limit) { id title } }")
        .variable("limit", 10)
        .header("X-Request-ID", "req-2")
        .build(),

    GraphQLRequest.builder()
        .query("query GetComments { comments { id text } }")
        .build()
);

// Execute all requests in parallel
graphql.executeBatch(requests)
    .collectList()
    .subscribe(responses -> {
        System.out.println("Received " + responses.size() + " responses");
        responses.forEach(response -> {
            if (!response.hasErrors()) {
                System.out.println("Data: " + response.getData());
            }
        });
    });
```

### Batch with Error Handling

```java
graphql.executeBatch(requests)
    .doOnNext(response -> {
        if (response.hasErrors()) {
            System.err.println("Query failed: " + response.getErrors()[0].getMessage());
        } else {
            System.out.println("Query succeeded: " + response.getData());
        }
    })
    .collectList()
    .subscribe(
        responses -> System.out.println("All queries completed"),
        error -> System.err.println("Batch failed: " + error.getMessage())
    );
```

---

## 💾 Query Caching

Improve performance by caching query results.

### Enable Query Caching

```java
GraphQLConfig config = GraphQLConfig.builder()
    .enableQueryCache(true)
    .build();

GraphQLClientHelper graphql = new GraphQLClientHelper(
    "https://api.example.com/graphql",
    config
);
```

### How Caching Works

- ✅ **Cached**: Queries without variables or custom headers
- ❌ **NOT Cached**: Queries with variables or custom headers
- Cache key is based on query string hash
- Cache is in-memory and per-instance

### Example

```java
String query = "query { users { id name } }";

// First call - hits the server
graphql.query(query).subscribe(response -> {
    System.out.println("First call: " + response.getData());
});

// Second call - returns cached result (no server hit)
graphql.query(query).subscribe(response -> {
    System.out.println("Second call (cached): " + response.getData());
});

// Clear cache when needed
graphql.clearCache();
System.out.println("Cache size: " + graphql.getCacheSize());
```

### Cache Management

```java
// Check cache size
int size = graphql.getCacheSize();
System.out.println("Cached queries: " + size);

// Clear cache
graphql.clearCache();

// Cache is automatically cleared when:
// - clearCache() is called
// - Instance is garbage collected
```

**Best Practices**:
- Use caching for static/reference data queries
- Don't cache user-specific or frequently changing data
- Clear cache periodically in long-running applications
- Monitor cache size in production

---

## 🔄 Automatic Retry Logic

Handle transient failures automatically with exponential backoff.

### Enable Retry

```java
GraphQLConfig config = GraphQLConfig.builder()
    .enableRetry(true)
    .maxRetries(3)
    .retryBackoff(Duration.ofSeconds(1))
    .build();

GraphQLClientHelper graphql = new GraphQLClientHelper(
    "https://api.example.com/graphql",
    config
);
```

### Retryable Errors

The client automatically retries on:
- ✅ 5xx Server Errors (500, 502, 503, 504)
- ✅ 429 Too Many Requests
- ✅ Timeout exceptions
- ✅ Connection errors

Does NOT retry on:
- ❌ 4xx Client Errors (except 429)
- ❌ GraphQL errors in response body
- ❌ Invalid queries

### Retry Behavior

```
Attempt 1: Immediate
Attempt 2: Wait 1 second (backoff)
Attempt 3: Wait 2 seconds (exponential)
Attempt 4: Wait 4 seconds (exponential)
```

### Example with Retry

```java
GraphQLConfig config = GraphQLConfig.builder()
    .enableRetry(true)
    .maxRetries(5)
    .retryBackoff(Duration.ofMillis(500))
    .build();

GraphQLClientHelper graphql = new GraphQLClientHelper(
    "https://unreliable-api.example.com/graphql",
    config
);

// This will automatically retry up to 5 times on failures
graphql.query(query, variables, "data", MyData.class)
    .doOnError(error -> {
        System.err.println("All retries exhausted: " + error.getMessage());
    })
    .subscribe(data -> {
        System.out.println("Success after retries: " + data);
    });
```

**Best Practices**:
- Enable retry for production environments
- Use reasonable maxRetries (3-5)
- Set appropriate backoff duration (500ms-2s)
- Monitor retry metrics in production
- Combine with circuit breaker for better resilience

---

## Best Practices

### 1. Use Variables Instead of String Interpolation

❌ **Bad**:
```java
String query = "query { user(id: \"" + userId + "\") { name } }";
```

✅ **Good**:
```java
String query = "query GetUser($id: ID!) { user(id: $id) { name } }";
Map<String, Object> variables = Map.of("id", userId);
```

### 2. Extract Data to Typed Objects

❌ **Bad**:
```java
graphql.query(query).subscribe(response -> {
    Map<String, Object> data = (Map<String, Object>) response.getData();
    String name = (String) data.get("name");  // Unsafe casting
});
```

✅ **Good**:
```java
graphql.query(query, variables, "user", User.class)
    .subscribe(user -> {
        String name = user.getName();  // Type-safe
    });
```

### 3. Handle Errors Properly

❌ **Bad**:
```java
graphql.query(query).subscribe(response -> {
    // Assumes no errors
    processData(response.getData());
});
```

✅ **Good**:
```java
graphql.query(query).subscribe(response -> {
    if (response.hasErrors()) {
        handleErrors(response.getErrors());
    } else {
        processData(response.getData());
    }
});
```

### 4. Use Production-Ready Configuration

❌ **Bad** (Development only):
```java
GraphQLClientHelper graphql = new GraphQLClientHelper("https://api.example.com/graphql");
```

✅ **Good** (Production-ready):
```java
GraphQLConfig config = GraphQLConfig.builder()
    .timeout(Duration.ofMinutes(2))
    .enableRetry(true)
    .maxRetries(3)
    .enableQueryCache(true)  // For static data
    .defaultHeader("Authorization", "Bearer " + token)
    .build();

GraphQLClientHelper graphql = new GraphQLClientHelper(
    "https://api.example.com/graphql",
    config
);
```

### 5. Use Batch Operations for Multiple Queries

❌ **Bad** (Sequential):
```java
graphql.query(query1).subscribe(r1 -> {
    graphql.query(query2).subscribe(r2 -> {
        graphql.query(query3).subscribe(r3 -> {
            // Process results
        });
    });
});
```

✅ **Good** (Parallel):
```java
List<GraphQLRequest> requests = List.of(
    GraphQLRequest.builder().query(query1).build(),
    GraphQLRequest.builder().query(query2).build(),
    GraphQLRequest.builder().query(query3).build()
);

graphql.executeBatch(requests)
    .collectList()
    .subscribe(responses -> {
        // Process all results
    });
```

### 6. Use Fragments for Reusable Fields

```java
String userFragment = """
    fragment UserFields on User {
        id
        name
        email
        createdAt
    }
""";

String query = """
    query GetUser($id: ID!) {
        user(id: $id) {
            ...UserFields
            posts {
                title
            }
        }
    }
""" + userFragment;
```

### 5. Set Appropriate Timeouts

```java
// For quick queries
GraphQLClientHelper quickClient = new GraphQLClientHelper(
    endpoint,
    Duration.ofSeconds(5),
    headers
);

// For complex queries
GraphQLClientHelper complexClient = new GraphQLClientHelper(
    endpoint,
    Duration.ofSeconds(60),
    headers
);
```

### 6. Reuse Client Instances

❌ **Bad**:
```java
public Mono<User> getUser(String id) {
    GraphQLClientHelper graphql = new GraphQLClientHelper(endpoint);  // New instance each time
    return graphql.query(query, Map.of("id", id), "user", User.class);
}
```

✅ **Good**:
```java
@Component
public class UserService {
    private final GraphQLClientHelper graphql;

    public UserService() {
        this.graphql = new GraphQLClientHelper(endpoint);  // Reuse instance
    }

    public Mono<User> getUser(String id) {
        return graphql.query(query, Map.of("id", id), "user", User.class);
    }
}
```

---

## Complete Examples

### Example 1: GitHub API Integration

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Map;

public class GitHubGraphQLClient {

    private final GraphQLClientHelper graphql;

    public GitHubGraphQLClient(String token) {
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer " + token
        );

        this.graphql = new GraphQLClientHelper(
            "https://api.github.com/graphql",
            Duration.ofSeconds(30),
            headers
        );
    }

    public Mono<Repository> getRepository(String owner, String name) {
        String query = """
            query GetRepository($owner: String!, $name: String!) {
                repository(owner: $owner, name: $name) {
                    id
                    name
                    description
                    stargazerCount
                    forkCount
                    url
                    createdAt
                    updatedAt
                }
            }
        """;

        Map<String, Object> variables = Map.of(
            "owner", owner,
            "name", name
        );

        return graphql.query(query, variables, "repository", Repository.class);
    }

    public Mono<User> getViewer() {
        String query = """
            query {
                viewer {
                    login
                    name
                    email
                    bio
                    avatarUrl
                    repositories(first: 10) {
                        totalCount
                        nodes {
                            name
                            stargazerCount
                        }
                    }
                }
            }
        """;

        return graphql.query(query, null, "viewer", User.class);
    }

    public Mono<Issue> createIssue(String repositoryId, String title, String body) {
        String mutation = """
            mutation CreateIssue($input: CreateIssueInput!) {
                createIssue(input: $input) {
                    issue {
                        id
                        number
                        title
                        body
                        url
                        createdAt
                    }
                }
            }
        """;

        Map<String, Object> variables = Map.of(
            "input", Map.of(
                "repositoryId", repositoryId,
                "title", title,
                "body", body
            )
        );

        return graphql.mutate(mutation, variables, "createIssue.issue", Issue.class);
    }

    // DTOs
    public static class Repository {
        private String id;
        private String name;
        private String description;
        private int stargazerCount;
        private int forkCount;
        private String url;
        private String createdAt;
        private String updatedAt;

        // getters and setters
    }

    public static class User {
        private String login;
        private String name;
        private String email;
        private String bio;
        private String avatarUrl;

        // getters and setters
    }

    public static class Issue {
        private String id;
        private int number;
        private String title;
        private String body;
        private String url;
        private String createdAt;

        // getters and setters
    }
}
```

### Example 2: E-commerce Product Catalog

```java
import org.fireflyframework.client.graphql.GraphQLClientHelper;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

public class ProductCatalogClient {

    private final GraphQLClientHelper graphql;

    public ProductCatalogClient(String endpoint) {
        this.graphql = new GraphQLClientHelper(endpoint);
    }

    public Mono<List<Product>> searchProducts(String searchTerm, int limit) {
        String query = """
            query SearchProducts($search: String!, $limit: Int!) {
                products(search: $search, first: $limit) {
                    edges {
                        node {
                            id
                            name
                            description
                            price
                            currency
                            imageUrl
                            inStock
                            category {
                                id
                                name
                            }
                        }
                    }
                }
            }
        """;

        Map<String, Object> variables = Map.of(
            "search", searchTerm,
            "limit", limit
        );

        return graphql.query(query, variables, "products.edges",
            new TypeReference<List<ProductEdge>>() {})
            .map(edges -> edges.stream()
                .map(edge -> edge.node)
                .collect(Collectors.toList()));
    }

    public Mono<Order> createOrder(String userId, List<OrderItem> items) {
        String mutation = """
            mutation CreateOrder($input: CreateOrderInput!) {
                createOrder(input: $input) {
                    order {
                        id
                        orderNumber
                        status
                        totalAmount
                        currency
                        createdAt
                        items {
                            productId
                            quantity
                            price
                        }
                    }
                }
            }
        """;

        Map<String, Object> variables = Map.of(
            "input", Map.of(
                "userId", userId,
                "items", items.stream()
                    .map(item -> Map.of(
                        "productId", item.getProductId(),
                        "quantity", item.getQuantity()
                    ))
                    .collect(Collectors.toList())
            )
        );

        return graphql.mutate(mutation, variables, "createOrder.order", Order.class);
    }

    // DTOs
    public static class Product {
        private String id;
        private String name;
        private String description;
        private double price;
        private String currency;
        private String imageUrl;
        private boolean inStock;
        private Category category;

        // getters and setters
    }

    public static class Category {
        private String id;
        private String name;

        // getters and setters
    }

    public static class ProductEdge {
        private Product node;

        // getters and setters
    }

    public static class Order {
        private String id;
        private String orderNumber;
        private String status;
        private double totalAmount;
        private String currency;
        private String createdAt;
        private List<OrderItemResponse> items;

        // getters and setters
    }

    public static class OrderItem {
        private String productId;
        private int quantity;

        // getters and setters
    }

    public static class OrderItemResponse {
        private String productId;
        private int quantity;
        private double price;

        // getters and setters
    }
}
```

---

## What's Included

✅ **Query Execution**: Full support for GraphQL queries
✅ **Mutation Execution**: Create, update, delete operations
✅ **Variables**: Type-safe variable binding
✅ **Error Handling**: Automatic GraphQL error parsing
✅ **Data Extraction**: Navigate and extract nested data
✅ **Custom Headers**: Per-request header customization
✅ **Timeouts**: Configurable request timeouts
✅ **Reactive**: Full `Mono<T>` and `Flux<T>` support

## What's NOT Included

❌ **Subscriptions**: Use WebSocket helper for real-time subscriptions
❌ **Schema Introspection**: Use dedicated GraphQL tools
❌ **Code Generation**: Use GraphQL code generators
❌ **Federation**: Use Apollo Federation or similar
❌ **Batching**: Implement custom batching logic
❌ **Caching**: Implement custom caching strategy

**For Production**: Consider using **Spring for GraphQL** or **Netflix DGS Framework** for advanced GraphQL features.

---

**Next Steps**:
- [REST Client Guide](REST_CLIENT.md)
- [gRPC Client Guide](GRPC_CLIENT.md)
- [SOAP Client Guide](SOAP_CLIENT.md)
- [WebSocket Helper Guide](WEBSOCKET_HELPER.md)
- [OAuth2 Helper Guide](OAUTH2_HELPER.md)
- [File Upload Helper Guide](MULTIPART_HELPER.md)
- [Configuration Reference](CONFIGURATION.md)

