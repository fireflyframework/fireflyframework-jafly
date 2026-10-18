package org.fireflyframework.client.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.kernel.exception.FireflySecurityException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Helper class for OAuth2 authentication flows.
 * 
 * <p>This helper provides simplified OAuth2 token management for use with
 * the Firefly Common Client library. It supports common OAuth2 flows:
 * <ul>
 *   <li>Client Credentials</li>
 *   <li>Password Grant (Resource Owner Password Credentials)</li>
 *   <li>Refresh Token</li>
 * </ul>
 *
 * <p><strong>Note:</strong> For production applications, consider using
 * Spring Security OAuth2 Client for full OAuth2/OIDC support including
 * Authorization Code flow with PKCE.
 *
 * <p>Example usage with RestClient:
 * <pre>{@code
 * // Create OAuth2 helper
 * OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
 *     "https://auth.example.com/oauth/token",
 *     "client-id",
 *     "client-secret"
 * );
 *
 * // Get access token
 * Mono<String> accessToken = oauth2.getClientCredentialsToken();
 *
 * // Use with RestClient
 * RestClient client = ServiceClient.rest("api-service")
 *     .baseUrl("https://api.example.com")
 *     .build();
 *
 * accessToken.flatMap(token ->
 *     client.get("/protected/resource", Resource.class)
 *         .withHeader("Authorization", "Bearer " + token)
 *         .execute()
 * ).subscribe();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class OAuth2ClientHelper {

    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final WebClient webClient;
    private final OAuth2Config config;

    // Token cache - supports multiple scopes
    private final Map<String, CachedToken> tokenCache;

    // Refresh token storage
    private volatile String cachedRefreshToken;

    /**
     * Creates a new OAuth2 client helper with default configuration.
     *
     * @param tokenEndpoint the OAuth2 token endpoint URL
     * @param clientId the client ID
     * @param clientSecret the client secret
     */
    public OAuth2ClientHelper(String tokenEndpoint, String clientId, String clientSecret) {
        this(tokenEndpoint, clientId, clientSecret, OAuth2Config.builder().build());
    }

    /**
     * Creates a new OAuth2 client helper with advanced configuration.
     *
     * @param tokenEndpoint the OAuth2 token endpoint URL
     * @param clientId the client ID
     * @param clientSecret the client secret
     * @param config the OAuth2 configuration
     */
    public OAuth2ClientHelper(String tokenEndpoint, String clientId, String clientSecret, OAuth2Config config) {
        if (tokenEndpoint == null || tokenEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Token endpoint cannot be null or empty");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }

        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.config = config;
        this.tokenCache = new ConcurrentHashMap<>();
        this.webClient = createWebClient();

        log.info("Created OAuth2 client helper for endpoint: {} with config: {}", tokenEndpoint, config);
    }

    private WebClient createWebClient() {
        return WebClient.builder()
            .defaultHeaders(headers -> {
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                config.defaultHeaders.forEach(headers::add);
            })
            .build();
    }

    /**
     * Gets an access token using the Client Credentials flow.
     * 
     * <p>This flow is used for machine-to-machine authentication where
     * the client is acting on its own behalf, not on behalf of a user.
     *
     * @return a Mono containing the access token
     */
    public Mono<String> getClientCredentialsToken() {
        return getClientCredentialsToken(null);
    }

    /**
     * Gets an access token using the Client Credentials flow with scopes.
     *
     * @param scope the requested scope (space-separated)
     * @return a Mono containing the access token
     */
    public Mono<String> getClientCredentialsToken(String scope) {
        String cacheKey = "client_credentials:" + (scope != null ? scope : "");

        // Check cache
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("Using cached access token for scope: {}", scope);
            return Mono.just(cached.accessToken);
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        if (scope != null && !scope.trim().isEmpty()) {
            formData.add("scope", scope);
        }

        return requestToken(formData)
            .doOnSuccess(response -> cacheToken(cacheKey, response))
            .map(TokenResponse::getAccessToken);
    }

    /**
     * Gets an access token using the Password Grant flow.
     * 
     * <p><strong>Warning:</strong> This flow is deprecated in OAuth 2.1.
     * Use only for legacy systems. Prefer Authorization Code with PKCE for user authentication.
     *
     * @param username the username
     * @param password the password
     * @return a Mono containing the access token
     */
    public Mono<String> getPasswordGrantToken(String username, String password) {
        return getPasswordGrantToken(username, password, null);
    }

    /**
     * Gets an access token using the Password Grant flow with scopes.
     *
     * @param username the username
     * @param password the password
     * @param scope the requested scope (space-separated)
     * @return a Mono containing the access token
     */
    public Mono<String> getPasswordGrantToken(String username, String password, String scope) {
        String cacheKey = "password:" + username + ":" + (scope != null ? scope : "");

        // Check cache
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("Using cached access token for user: {}", username);
            return Mono.just(cached.accessToken);
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("username", username);
        formData.add("password", password);
        if (scope != null && !scope.trim().isEmpty()) {
            formData.add("scope", scope);
        }

        return requestToken(formData)
            .doOnSuccess(response -> {
                cacheToken(cacheKey, response);
                // Store refresh token if provided
                if (response.getRefreshToken() != null) {
                    cachedRefreshToken = response.getRefreshToken();
                }
            })
            .map(TokenResponse::getAccessToken);
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param refreshToken the refresh token
     * @return a Mono containing the new access token
     */
    public Mono<String> refreshToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);

        return requestToken(formData)
            .doOnSuccess(response -> {
                // Update cached refresh token if a new one is provided
                if (response.getRefreshToken() != null) {
                    cachedRefreshToken = response.getRefreshToken();
                }
                // Cache the new access token
                String cacheKey = "refreshed:" + System.currentTimeMillis();
                cacheToken(cacheKey, response);
            })
            .map(TokenResponse::getAccessToken);
    }

    /**
     * Automatically refreshes the token using the cached refresh token.
     *
     * @return a Mono containing the new access token
     */
    public Mono<String> autoRefreshToken() {
        if (cachedRefreshToken == null) {
            return Mono.error(new OAuth2Exception("No refresh token available"));
        }
        return refreshToken(cachedRefreshToken);
    }

    /**
     * Gets the full token response (including refresh token, expires_in, etc.).
     *
     * @param grantType the grant type
     * @param additionalParams additional parameters
     * @return a Mono containing the token response
     */
    public Mono<TokenResponse> getTokenResponse(String grantType, Map<String, String> additionalParams) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", grantType);
        additionalParams.forEach(formData::add);
        
        return requestToken(formData);
    }

    /**
     * Gets a token with automatic retry on failure.
     *
     * @param tokenSupplier the token supplier function
     * @return a Mono containing the access token
     */
    public Mono<String> getTokenWithRetry(Supplier<Mono<String>> tokenSupplier) {
        if (!config.enableRetry) {
            return tokenSupplier.get();
        }

        return tokenSupplier.get()
            .retryWhen(Retry.backoff(config.maxRetries, config.retryBackoff)
                .filter(this::isRetryableError)
                .doBeforeRetry(signal ->
                    log.warn("Retrying OAuth2 token request, attempt: {}", signal.totalRetries() + 1))
            );
    }

    private Mono<TokenResponse> requestToken(MultiValueMap<String, String> formData) {
        String basicAuth = Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes());

        Mono<TokenResponse> request = webClient.post()
            .uri(tokenEndpoint)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .onStatus(status -> status.isError(), this::handleErrorResponse)
            .bodyToMono(TokenResponse.class)
            .timeout(config.timeout)
            .doOnSubscribe(sub -> log.debug("Requesting OAuth2 token from: {}", tokenEndpoint))
            .doOnSuccess(response -> log.debug("Successfully obtained OAuth2 token"))
            .doOnError(error -> log.error("Failed to obtain OAuth2 token: {}", error.getMessage()));

        // Apply retry if enabled
        if (config.enableRetry) {
            request = request.retryWhen(
                Retry.backoff(config.maxRetries, config.retryBackoff)
                    .filter(this::isRetryableError)
                    .doBeforeRetry(signal ->
                        log.warn("Retrying OAuth2 token request, attempt: {}", signal.totalRetries() + 1))
            );
        }

        return request;
    }

    private void cacheToken(String cacheKey, TokenResponse response) {
        int expiresIn = response.getExpiresIn() != null ? response.getExpiresIn() : 3600;
        int bufferSeconds = config.tokenExpirationBuffer;
        Instant expiresAt = Instant.now().plusSeconds(expiresIn - bufferSeconds);

        CachedToken cachedToken = new CachedToken(response.getAccessToken(), expiresAt);
        tokenCache.put(cacheKey, cachedToken);

        log.debug("Cached access token for key: {}, expires at: {}", cacheKey, expiresAt);
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
                String errorMessage = String.format("OAuth2 token request failed with status %s: %s",
                    response.statusCode(), body);
                return Mono.error(new OAuth2Exception(errorMessage));
            });
    }

    /**
     * Clears all cached tokens.
     */
    public void clearCache() {
        tokenCache.clear();
        cachedRefreshToken = null;
        log.debug("Cleared OAuth2 token cache");
    }

    /**
     * Clears a specific cached token by key.
     *
     * @param cacheKey the cache key
     */
    public void clearCache(String cacheKey) {
        tokenCache.remove(cacheKey);
        log.debug("Cleared OAuth2 token cache for key: {}", cacheKey);
    }

    /**
     * Gets the number of cached tokens.
     *
     * @return the cache size
     */
    public int getCacheSize() {
        return tokenCache.size();
    }

    /**
     * Checks if a token is cached and valid for the given key.
     *
     * @param cacheKey the cache key
     * @return true if a valid token is cached
     */
    public boolean hasValidToken(String cacheKey) {
        CachedToken cached = tokenCache.get(cacheKey);
        return cached != null && cached.isValid();
    }

    /**
     * Gets the cached refresh token.
     *
     * @return the refresh token, or null if not available
     */
    public String getCachedRefreshToken() {
        return cachedRefreshToken;
    }

    /**
     * Cached token with expiration.
     */
    private static class CachedToken {
        private final String accessToken;
        private final Instant expiresAt;

        public CachedToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        public boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    /**
     * OAuth2 token response.
     */
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private Integer expiresIn;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("scope")
        private String scope;

        @JsonProperty("id_token")
        private String idToken;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }

        public Integer getExpiresIn() { return expiresIn; }
        public void setExpiresIn(Integer expiresIn) { this.expiresIn = expiresIn; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }

        public String getIdToken() { return idToken; }
        public void setIdToken(String idToken) { this.idToken = idToken; }
    }

    /**
     * OAuth2 configuration.
     */
    public static class OAuth2Config {
        private final Duration timeout;
        private final boolean enableRetry;
        private final int maxRetries;
        private final Duration retryBackoff;
        private final int tokenExpirationBuffer;
        private final Map<String, String> defaultHeaders;

        private OAuth2Config(Builder builder) {
            this.timeout = builder.timeout;
            this.enableRetry = builder.enableRetry;
            this.maxRetries = builder.maxRetries;
            this.retryBackoff = builder.retryBackoff;
            this.tokenExpirationBuffer = builder.tokenExpirationBuffer;
            this.defaultHeaders = new HashMap<>(builder.defaultHeaders);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Duration timeout = Duration.ofSeconds(30);
            private boolean enableRetry = false;
            private int maxRetries = 3;
            private Duration retryBackoff = Duration.ofMillis(500);
            private int tokenExpirationBuffer = 60;
            private Map<String, String> defaultHeaders = new HashMap<>();

            /**
             * Sets the request timeout.
             *
             * @param timeout the timeout duration
             * @return this builder
             */
            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            /**
             * Enables automatic retry on failures.
             *
             * @param enable true to enable retry
             * @return this builder
             */
            public Builder enableRetry(boolean enable) {
                this.enableRetry = enable;
                return this;
            }

            /**
             * Sets the maximum number of retry attempts.
             *
             * @param maxRetries the max retries
             * @return this builder
             */
            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            /**
             * Sets the retry backoff duration.
             *
             * @param backoff the backoff duration
             * @return this builder
             */
            public Builder retryBackoff(Duration backoff) {
                this.retryBackoff = backoff;
                return this;
            }

            /**
             * Sets the token expiration buffer in seconds.
             * Tokens are considered expired this many seconds before actual expiration.
             *
             * @param bufferSeconds the buffer in seconds
             * @return this builder
             */
            public Builder tokenExpirationBuffer(int bufferSeconds) {
                this.tokenExpirationBuffer = bufferSeconds;
                return this;
            }

            /**
             * Adds a default header to all requests.
             *
             * @param name the header name
             * @param value the header value
             * @return this builder
             */
            public Builder defaultHeader(String name, String value) {
                this.defaultHeaders.put(name, value);
                return this;
            }

            /**
             * Builds the OAuth2 configuration.
             *
             * @return the configuration
             */
            public OAuth2Config build() {
                return new OAuth2Config(this);
            }
        }

        @Override
        public String toString() {
            return String.format("OAuth2Config{timeout=%s, enableRetry=%s, maxRetries=%d, retryBackoff=%s, tokenExpirationBuffer=%d}",
                timeout, enableRetry, maxRetries, retryBackoff, tokenExpirationBuffer);
        }
    }

    /**
     * OAuth2 exception.
     */
    public static class OAuth2Exception extends FireflySecurityException {
        public OAuth2Exception(String message) {
            super(message);
        }

        public OAuth2Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Example: API client with OAuth2 authentication
     */
    public static class ApiClientExample {
        public static void main(String[] args) {
            // Create OAuth2 helper with advanced config
            OAuth2Config config = OAuth2Config.builder()
                .timeout(Duration.ofMinutes(1))
                .enableRetry(true)
                .maxRetries(3)
                .retryBackoff(Duration.ofSeconds(1))
                .tokenExpirationBuffer(120)
                .build();

            OAuth2ClientHelper oauth2 = new OAuth2ClientHelper(
                "https://auth.example.com/oauth/token",
                "my-client-id",
                "my-client-secret",
                config
            );

            // Get token and make API call
            oauth2.getClientCredentialsToken("api.read api.write")
                .doOnNext(token -> System.out.println("Access token: " + token))
                .subscribe();
        }
    }
}

