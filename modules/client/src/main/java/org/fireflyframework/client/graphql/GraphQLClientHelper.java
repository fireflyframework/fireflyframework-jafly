package org.fireflyframework.client.graphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.kernel.exception.FireflyInfrastructureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Helper class for GraphQL client operations.
 * 
 * <p>This helper provides a simplified API for GraphQL queries and mutations
 * while maintaining compatibility with the Firefly Common Client library's
 * reactive patterns.
 *
 * <p><strong>Note:</strong> For production applications with complex GraphQL needs,
 * consider using dedicated GraphQL clients like:
 * <ul>
 *   <li>Spring for GraphQL</li>
 *   <li>Netflix DGS Framework</li>
 *   <li>GraphQL Java</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Create GraphQL helper
 * GraphQLClientHelper graphql = new GraphQLClientHelper("https://api.example.com/graphql");
 *
 * // Execute query
 * String query = """
 *     query GetUser($id: ID!) {
 *         user(id: $id) {
 *             id
 *             name
 *             email
 *         }
 *     }
 * """;
 *
 * Map<String, Object> variables = Map.of("id", "123");
 *
 * Mono<User> user = graphql.query(query, variables, "user", User.class);
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class GraphQLClientHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private final String endpoint;
    private final WebClient webClient;
    private final Duration timeout;
    private final Map<String, String> defaultHeaders;
    private final boolean enableRetry;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final Map<String, String> queryCache;
    private final boolean enableQueryCache;

    /**
     * Creates a new GraphQL client helper with default configuration.
     *
     * @param endpoint the GraphQL endpoint URL
     */
    public GraphQLClientHelper(String endpoint) {
        this(endpoint, GraphQLConfig.builder().build());
    }

    /**
     * Creates a new GraphQL client helper with custom configuration.
     *
     * @param endpoint the GraphQL endpoint URL
     * @param timeout the request timeout
     * @param defaultHeaders default headers to include in all requests
     */
    public GraphQLClientHelper(String endpoint, Duration timeout, Map<String, String> defaultHeaders) {
        this(endpoint, GraphQLConfig.builder()
            .timeout(timeout)
            .defaultHeaders(defaultHeaders)
            .build());
    }

    /**
     * Creates a new GraphQL client helper with advanced configuration.
     *
     * @param endpoint the GraphQL endpoint URL
     * @param config the GraphQL configuration
     */
    public GraphQLClientHelper(String endpoint, GraphQLConfig config) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("GraphQL endpoint cannot be null or empty");
        }

        this.endpoint = endpoint;
        this.timeout = config.timeout;
        this.defaultHeaders = new HashMap<>(config.defaultHeaders);
        this.enableRetry = config.enableRetry;
        this.maxRetries = config.maxRetries;
        this.retryBackoff = config.retryBackoff;
        this.enableQueryCache = config.enableQueryCache;
        this.queryCache = config.enableQueryCache ? new ConcurrentHashMap<>() : null;
        this.webClient = createWebClient();

        log.info("Created GraphQL client helper for endpoint: {} with config: {}", endpoint, config);
    }

    private WebClient createWebClient() {
        return WebClient.builder()
            .baseUrl(endpoint)
            .defaultHeaders(headers -> {
                headers.setContentType(MediaType.APPLICATION_JSON);
                defaultHeaders.forEach(headers::add);
            })
            .build();
    }

    /**
     * Executes a GraphQL query.
     *
     * @param query the GraphQL query string
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<GraphQLResponse<R>> query(String query) {
        return query(query, null);
    }

    /**
     * Executes a GraphQL query with variables.
     *
     * @param query the GraphQL query string
     * @param variables the query variables
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<GraphQLResponse<R>> query(String query, Map<String, Object> variables) {
        return execute(query, variables, null);
    }

    /**
     * Executes a GraphQL query and extracts a specific field.
     *
     * @param query the GraphQL query string
     * @param variables the query variables
     * @param dataPath the path to the data field (e.g., "user" or "data.user")
     * @param responseType the response type class
     * @param <R> the response type
     * @return a Mono containing the extracted data
     */
    public <R> Mono<R> query(String query, Map<String, Object> variables, String dataPath, Class<R> responseType) {
        return execute(query, variables, null)
            .map(response -> extractData(response, dataPath, responseType));
    }

    /**
     * Executes a GraphQL mutation.
     *
     * @param mutation the GraphQL mutation string
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<GraphQLResponse<R>> mutate(String mutation) {
        return mutate(mutation, null);
    }

    /**
     * Executes a GraphQL mutation with variables.
     *
     * @param mutation the GraphQL mutation string
     * @param variables the mutation variables
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<GraphQLResponse<R>> mutate(String mutation, Map<String, Object> variables) {
        return execute(mutation, variables, null);
    }

    /**
     * Executes a GraphQL mutation and extracts a specific field.
     *
     * @param mutation the GraphQL mutation string
     * @param variables the mutation variables
     * @param dataPath the path to the data field
     * @param responseType the response type class
     * @param <R> the response type
     * @return a Mono containing the extracted data
     */
    public <R> Mono<R> mutate(String mutation, Map<String, Object> variables, String dataPath, Class<R> responseType) {
        return execute(mutation, variables, null)
            .map(response -> extractData(response, dataPath, responseType));
    }

    /**
     * Executes a GraphQL operation with custom headers.
     *
     * @param query the GraphQL query or mutation
     * @param variables the variables
     * @param headers custom headers for this request
     * @param <R> the response type
     * @return a Mono containing the response
     */
    @SuppressWarnings("unchecked")
    public <R> Mono<GraphQLResponse<R>> execute(String query, Map<String, Object> variables,
                                                  Map<String, String> headers) {
        // Check query cache
        if (enableQueryCache && variables == null && headers == null) {
            String cacheKey = generateCacheKey(query);
            String cachedResponse = queryCache.get(cacheKey);
            if (cachedResponse != null) {
                log.debug("Returning cached GraphQL response");
                return Mono.just((GraphQLResponse<R>) parseResponse(cachedResponse));
            }
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        if (variables != null && !variables.isEmpty()) {
            requestBody.put("variables", variables);
        }

        WebClient.RequestHeadersSpec<?> spec = webClient.post()
            .bodyValue(requestBody);

        if (headers != null && !headers.isEmpty()) {
            spec = spec.headers(httpHeaders -> headers.forEach(httpHeaders::add));
        }

        Mono<String> responseMono = spec.retrieve()
            .onStatus(status -> status.isError(), this::handleErrorResponse)
            .bodyToMono(String.class)
            .timeout(timeout);

        // Apply retry if enabled
        if (enableRetry) {
            responseMono = responseMono.retryWhen(
                Retry.backoff(maxRetries, retryBackoff)
                    .filter(this::isRetryableError)
                    .doBeforeRetry(signal -> log.warn("Retrying GraphQL request, attempt: {}", signal.totalRetries() + 1))
            );
        }

        return responseMono
            .map(jsonResponse -> {
                // Cache successful responses
                if (enableQueryCache && variables == null && headers == null) {
                    String cacheKey = generateCacheKey(query);
                    queryCache.put(cacheKey, jsonResponse);
                }
                return (GraphQLResponse<R>) parseResponse(jsonResponse);
            })
            .doOnSubscribe(sub -> log.debug("Executing GraphQL operation"))
            .doOnSuccess(response -> log.debug("GraphQL operation completed"))
            .doOnError(error -> log.error("GraphQL operation failed: {}", error.getMessage()));
    }

    /**
     * Executes multiple GraphQL queries in batch.
     *
     * @param queries list of queries to execute
     * @param <R> the response type
     * @return a Flux containing all responses
     */
    public <R> Flux<GraphQLResponse<R>> executeBatch(List<GraphQLRequest> queries) {
        return Flux.fromIterable(queries)
            .flatMap(request -> execute(request.getQuery(), request.getVariables(), request.getHeaders()));
    }

    /**
     * Executes a GraphQL subscription (for real-time updates).
     * Note: This requires WebSocket support on the GraphQL server.
     *
     * @param subscription the GraphQL subscription query
     * @param variables the variables
     * @param <R> the response type
     * @return a Flux containing subscription updates
     */
    public <R> Flux<GraphQLResponse<R>> subscribe(String subscription, Map<String, Object> variables) {
        // This is a placeholder for subscription support
        // Full implementation would require WebSocket client
        log.warn("GraphQL subscriptions require WebSocket support - not fully implemented");
        return Flux.error(new UnsupportedOperationException(
            "GraphQL subscriptions require WebSocket support. Use WebSocketClientHelper for real-time updates."
        ));
    }

    /**
     * Clears the query cache.
     */
    public void clearCache() {
        if (queryCache != null) {
            queryCache.clear();
            log.info("GraphQL query cache cleared");
        }
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of cached queries
     */
    public int getCacheSize() {
        return queryCache != null ? queryCache.size() : 0;
    }

    private String generateCacheKey(String query) {
        return String.valueOf(query.hashCode());
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof WebClientResponseException) {
            WebClientResponseException webClientError = (WebClientResponseException) error;
            HttpStatus status = (HttpStatus) webClientError.getStatusCode();
            // Retry on 5xx errors and 429 (Too Many Requests)
            return status.is5xxServerError() || status == HttpStatus.TOO_MANY_REQUESTS;
        }
        // Retry on timeout and connection errors
        return error instanceof java.util.concurrent.TimeoutException ||
               error instanceof java.net.ConnectException;
    }

    private Mono<? extends Throwable> handleErrorResponse(ClientResponse response) {
        return response.bodyToMono(String.class)
            .flatMap(body -> {
                String errorMessage = String.format("GraphQL request failed with status %s: %s",
                    response.statusCode(), body);
                return Mono.error(new GraphQLException(errorMessage));
            });
    }

    @SuppressWarnings("unchecked")
    private <R> GraphQLResponse<R> parseResponse(String jsonResponse) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);

            GraphQLResponse<R> response = new GraphQLResponse<>();

            if (root.has("data")) {
                // Cast to R - this is safe because we control the type through the API
                Object data = OBJECT_MAPPER.convertValue(root.get("data"), Object.class);
                response.setData((R) data);
            }

            if (root.has("errors")) {
                response.setErrors(OBJECT_MAPPER.convertValue(root.get("errors"), GraphQLError[].class));
            }

            return response;

        } catch (Exception e) {
            throw new GraphQLException("Failed to parse GraphQL response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <R> R extractData(GraphQLResponse<?> response, String dataPath, Class<R> responseType) {
        if (response.hasErrors()) {
            throw new GraphQLException("GraphQL query returned errors: " + response.getErrors()[0].getMessage());
        }
        
        Object data = response.getData();
        if (data == null) {
            return null;
        }
        
        // Navigate the data path
        if (dataPath != null && !dataPath.trim().isEmpty()) {
            String[] pathParts = dataPath.split("\\.");
            for (String part : pathParts) {
                if (data instanceof Map) {
                    data = ((Map<String, Object>) data).get(part);
                } else {
                    throw new GraphQLException("Cannot navigate path: " + dataPath);
                }
            }
        }
        
        return OBJECT_MAPPER.convertValue(data, responseType);
    }

    /**
     * Gets the GraphQL endpoint URL.
     *
     * @return the endpoint URL
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * GraphQL response wrapper.
     *
     * @param <T> the data type
     */
    public static class GraphQLResponse<T> {
        private T data;
        private GraphQLError[] errors;

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        
        public GraphQLError[] getErrors() { return errors; }
        public void setErrors(GraphQLError[] errors) { this.errors = errors; }
        
        public boolean hasErrors() {
            return errors != null && errors.length > 0;
        }
    }

    /**
     * GraphQL error.
     */
    public static class GraphQLError {
        private String message;
        private Object[] locations;
        private String[] path;
        private Map<String, Object> extensions;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Object[] getLocations() { return locations; }
        public void setLocations(Object[] locations) { this.locations = locations; }
        
        public String[] getPath() { return path; }
        public void setPath(String[] path) { this.path = path; }
        
        public Map<String, Object> getExtensions() { return extensions; }
        public void setExtensions(Map<String, Object> extensions) { this.extensions = extensions; }
    }

    /**
     * GraphQL exception.
     */
    public static class GraphQLException extends FireflyInfrastructureException {
        public GraphQLException(String message) {
            super(message);
        }

        public GraphQLException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * GraphQL request builder for batch operations.
     */
    public static class GraphQLRequest {
        private final String query;
        private final Map<String, Object> variables;
        private final Map<String, String> headers;

        private GraphQLRequest(Builder builder) {
            this.query = builder.query;
            this.variables = builder.variables;
            this.headers = builder.headers;
        }

        public String getQuery() { return query; }
        public Map<String, Object> getVariables() { return variables; }
        public Map<String, String> getHeaders() { return headers; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String query;
            private Map<String, Object> variables = new HashMap<>();
            private Map<String, String> headers = new HashMap<>();

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder variables(Map<String, Object> variables) {
                this.variables = variables;
                return this;
            }

            public Builder variable(String key, Object value) {
                this.variables.put(key, value);
                return this;
            }

            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }

            public Builder header(String key, String value) {
                this.headers.put(key, value);
                return this;
            }

            public GraphQLRequest build() {
                if (query == null || query.trim().isEmpty()) {
                    throw new IllegalArgumentException("Query cannot be null or empty");
                }
                return new GraphQLRequest(this);
            }
        }
    }

    /**
     * GraphQL client configuration.
     */
    public static class GraphQLConfig {
        private final Duration timeout;
        private final Map<String, String> defaultHeaders;
        private final boolean enableRetry;
        private final int maxRetries;
        private final Duration retryBackoff;
        private final boolean enableQueryCache;

        private GraphQLConfig(Builder builder) {
            this.timeout = builder.timeout;
            this.defaultHeaders = builder.defaultHeaders;
            this.enableRetry = builder.enableRetry;
            this.maxRetries = builder.maxRetries;
            this.retryBackoff = builder.retryBackoff;
            this.enableQueryCache = builder.enableQueryCache;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return String.format("GraphQLConfig{timeout=%s, retry=%s, maxRetries=%d, cache=%s}",
                timeout, enableRetry, maxRetries, enableQueryCache);
        }

        public static class Builder {
            private Duration timeout = Duration.ofSeconds(30);
            private Map<String, String> defaultHeaders = new HashMap<>();
            private boolean enableRetry = false;
            private int maxRetries = 3;
            private Duration retryBackoff = Duration.ofMillis(500);
            private boolean enableQueryCache = false;

            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public Builder defaultHeaders(Map<String, String> headers) {
                this.defaultHeaders = new HashMap<>(headers);
                return this;
            }

            public Builder defaultHeader(String key, String value) {
                this.defaultHeaders.put(key, value);
                return this;
            }

            public Builder enableRetry(boolean enable) {
                this.enableRetry = enable;
                return this;
            }

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder retryBackoff(Duration backoff) {
                this.retryBackoff = backoff;
                return this;
            }

            public Builder enableQueryCache(boolean enable) {
                this.enableQueryCache = enable;
                return this;
            }

            public GraphQLConfig build() {
                return new GraphQLConfig(this);
            }
        }
    }

    /**
     * Example: Query user data
     */
    public static class UserQueryExample {
        public static void main(String[] args) {
            // Advanced configuration
            GraphQLConfig config = GraphQLConfig.builder()
                .timeout(Duration.ofSeconds(60))
                .enableRetry(true)
                .maxRetries(3)
                .enableQueryCache(true)
                .defaultHeader("Authorization", "Bearer token")
                .build();

            GraphQLClientHelper graphql = new GraphQLClientHelper(
                "https://api.example.com/graphql",
                config
            );

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

            graphql.query(query, variables, "user", User.class)
                .subscribe(user -> System.out.println("User: " + user.getName()));
        }

        static class User {
            private String id;
            private String name;
            private String email;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
    }
}

