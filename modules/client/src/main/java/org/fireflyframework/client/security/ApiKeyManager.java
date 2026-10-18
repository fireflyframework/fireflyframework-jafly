package org.fireflyframework.client.security;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * API key management with rotation, expiration, and secure storage.
 * 
 * <p>This manager handles API keys for service-to-service authentication,
 * supporting key rotation, expiration, and multiple key strategies.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Static API key
 * ApiKeyManager keyManager = ApiKeyManager.builder()
 *     .serviceName("payment-service")
 *     .apiKey("static-api-key-12345")
 *     .headerName("X-API-Key")
 *     .build();
 * 
 * // Dynamic API key with rotation
 * ApiKeyManager keyManager = ApiKeyManager.builder()
 *     .serviceName("user-service")
 *     .apiKeySupplier(() -> fetchApiKeyFromVault())
 *     .rotationInterval(Duration.ofHours(1))
 *     .headerName("Authorization")
 *     .headerPrefix("ApiKey ")
 *     .build();
 * 
 * // Get current API key
 * String key = keyManager.getCurrentApiKey();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
@Builder
public class ApiKeyManager {

    /**
     * Service name for logging and identification.
     */
    @Getter
    private final String serviceName;

    /**
     * Static API key (if not using dynamic supplier).
     */
    private final String apiKey;

    /**
     * Dynamic API key supplier (for rotation).
     */
    private final Supplier<String> apiKeySupplier;

    /**
     * HTTP header name for the API key.
     */
    @Builder.Default
    @Getter
    private final String headerName = "X-API-Key";

    /**
     * Optional prefix for the header value (e.g., "Bearer ", "ApiKey ").
     */
    @Builder.Default
    @Getter
    private final String headerPrefix = "";

    /**
     * Key rotation interval (only for dynamic keys).
     */
    @Builder.Default
    private final Duration rotationInterval = Duration.ofHours(24);

    /**
     * Whether to enable automatic key rotation.
     */
    @Builder.Default
    private final boolean autoRotate = false;

    /**
     * Whether to cache the API key.
     */
    @Builder.Default
    private final boolean cacheEnabled = true;

    /**
     * Cache expiration duration.
     */
    @Builder.Default
    private final Duration cacheExpiration = Duration.ofMinutes(5);

    /**
     * Cached API key.
     */
    private volatile String cachedApiKey;

    /**
     * Timestamp of last key fetch.
     */
    private volatile Instant lastFetchTime;

    /**
     * Timestamp of last key rotation.
     */
    private volatile Instant lastRotationTime;

    /**
     * Additional metadata for the API key.
     */
    @Builder.Default
    private final Map<String, String> metadata = new ConcurrentHashMap<>();

    /**
     * Gets the current API key, handling rotation and caching.
     *
     * @return the current API key
     */
    public String getCurrentApiKey() {
        if (apiKey != null) {
            // Static API key
            return apiKey;
        }

        if (apiKeySupplier == null) {
            throw new IllegalStateException("No API key or supplier configured for service: " + serviceName);
        }

        // Check if rotation is needed
        if (autoRotate && shouldRotate()) {
            rotateApiKey();
        }

        // Check cache
        if (cacheEnabled && isCacheValid()) {
            return cachedApiKey;
        }

        // Fetch new key
        return fetchAndCacheApiKey();
    }

    /**
     * Gets the full header value (with prefix if configured).
     *
     * @return the header value
     */
    public String getHeaderValue() {
        String key = getCurrentApiKey();
        return headerPrefix.isEmpty() ? key : headerPrefix + key;
    }

    /**
     * Manually rotates the API key.
     */
    public void rotateApiKey() {
        log.info("Rotating API key for service: {}", serviceName);
        cachedApiKey = null;
        lastRotationTime = Instant.now();
        fetchAndCacheApiKey();
    }

    /**
     * Clears the cached API key.
     */
    public void clearCache() {
        log.debug("Clearing API key cache for service: {}", serviceName);
        cachedApiKey = null;
        lastFetchTime = null;
    }

    /**
     * Checks if the API key is valid (not null or empty).
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        try {
            String key = getCurrentApiKey();
            return key != null && !key.trim().isEmpty();
        } catch (Exception e) {
            log.error("Failed to validate API key for service: {}", serviceName, e);
            return false;
        }
    }

    /**
     * Gets metadata value.
     *
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Sets metadata value.
     *
     * @param key the metadata key
     * @param value the metadata value
     */
    public void setMetadata(String key, String value) {
        metadata.put(key, value);
    }

    /**
     * Checks if key rotation is needed.
     *
     * @return true if rotation is needed, false otherwise
     */
    private boolean shouldRotate() {
        if (lastRotationTime == null) {
            return true;
        }
        Duration timeSinceRotation = Duration.between(lastRotationTime, Instant.now());
        return timeSinceRotation.compareTo(rotationInterval) >= 0;
    }

    /**
     * Checks if the cached key is still valid.
     *
     * @return true if cache is valid, false otherwise
     */
    private boolean isCacheValid() {
        if (cachedApiKey == null || lastFetchTime == null) {
            return false;
        }
        Duration timeSinceFetch = Duration.between(lastFetchTime, Instant.now());
        return timeSinceFetch.compareTo(cacheExpiration) < 0;
    }

    /**
     * Fetches a new API key and caches it.
     *
     * @return the fetched API key
     */
    private String fetchAndCacheApiKey() {
        try {
            log.debug("Fetching API key for service: {}", serviceName);
            String key = apiKeySupplier.get();
            
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalStateException("API key supplier returned null or empty key");
            }

            if (cacheEnabled) {
                cachedApiKey = key;
                lastFetchTime = Instant.now();
                log.debug("API key cached for service: {}", serviceName);
            }

            return key;
        } catch (Exception e) {
            log.error("Failed to fetch API key for service: {}", serviceName, e);
            throw new RuntimeException("Failed to fetch API key", e);
        }
    }

    /**
     * Creates a simple API key manager with static key.
     *
     * @param serviceName the service name
     * @param apiKey the API key
     * @return configured ApiKeyManager
     */
    public static ApiKeyManager simple(String serviceName, String apiKey) {
        return ApiKeyManager.builder()
            .serviceName(serviceName)
            .apiKey(apiKey)
            .build();
    }

    /**
     * Creates an API key manager with dynamic supplier.
     *
     * @param serviceName the service name
     * @param apiKeySupplier the API key supplier
     * @return configured ApiKeyManager
     */
    public static ApiKeyManager dynamic(String serviceName, Supplier<String> apiKeySupplier) {
        return ApiKeyManager.builder()
            .serviceName(serviceName)
            .apiKeySupplier(apiKeySupplier)
            .autoRotate(true)
            .build();
    }

    /**
     * Creates an API key manager for Bearer token authentication.
     *
     * @param serviceName the service name
     * @param token the bearer token
     * @return configured ApiKeyManager
     */
    public static ApiKeyManager bearer(String serviceName, String token) {
        return ApiKeyManager.builder()
            .serviceName(serviceName)
            .apiKey(token)
            .headerName("Authorization")
            .headerPrefix("Bearer ")
            .build();
    }
}

