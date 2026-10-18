package org.fireflyframework.example.service;

import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import org.fireflyframework.client.oauth2.OAuth2ClientHelper.OAuth2Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Example service demonstrating OAuth2 client usage with:
 * - Multi-scope token caching
 * - Automatic token refresh
 * - Retry with exponential backoff
 * - ID token support
 */
@Slf4j
@Service
public class OAuth2ExampleService {

    private final OAuth2ClientHelper oauth2Client;

    public OAuth2ExampleService(
        @Value("${oauth2.token-endpoint:https://auth.example.com/oauth/token}") String tokenEndpoint,
        @Value("${oauth2.client-id:demo-client}") String clientId,
        @Value("${oauth2.client-secret:demo-secret}") String clientSecret
    ) {
        OAuth2Config config = OAuth2Config.builder()
            .timeout(Duration.ofSeconds(30))
            .enableRetry(true)
            .maxRetries(3)
            .retryBackoff(Duration.ofSeconds(1))
            .tokenExpirationBuffer(120) // Refresh 2 minutes before expiration
            .build();

        this.oauth2Client = new OAuth2ClientHelper(
            tokenEndpoint,
            clientId,
            clientSecret,
            config
        );

        log.info("OAuth2ExampleService initialized");
    }

    /**
     * Get an access token with default scope (client credentials grant).
     */
    public String getAccessToken() {
        log.info("Getting OAuth2 access token");

        return oauth2Client.getClientCredentialsToken().block();
    }

    /**
     * Get an access token with specific scopes.
     */
    public String getAccessTokenWithScopes(String... scopes) {
        log.info("Getting OAuth2 access token with scopes: {}", String.join(" ", scopes));

        return oauth2Client.getClientCredentialsToken(String.join(" ", scopes)).block();
    }

    /**
     * Get ID token (for OpenID Connect).
     */
    public String getIdToken() {
        log.info("Getting OAuth2 ID token");

        return oauth2Client
            .getTokenResponse("client_credentials", Map.of("scope", "openid profile email"))
            .map(OAuth2ClientHelper.TokenResponse::getIdToken)
            .block();
    }

    /**
     * Check if a valid token exists for a scope.
     */
    public boolean hasValidToken(String scope) {
        return oauth2Client.hasValidToken(scope);
    }

    /**
     * Clear token cache.
     */
    public void clearCache() {
        log.info("Clearing OAuth2 token cache");
        oauth2Client.clearCache();
    }

    /**
     * Get cache statistics.
     */
    public int getCacheSize() {
        return oauth2Client.getCacheSize();
    }
}
