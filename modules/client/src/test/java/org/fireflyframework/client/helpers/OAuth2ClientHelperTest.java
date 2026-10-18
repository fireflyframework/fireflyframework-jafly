package org.fireflyframework.client.helpers;

import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OAuth2ClientHelper.
 */
@DisplayName("OAuth2 Client Helper Tests")
class OAuth2ClientHelperTest {

    @Test
    @DisplayName("Should create OAuth2 helper with valid configuration")
    void shouldCreateOAuth2HelperWithValidConfiguration() {
        // When
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "client-id",
            "client-secret"
        );

        // Then
        assertThat(helper).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception for null token endpoint")
    void shouldThrowExceptionForNullTokenEndpoint() {
        // When/Then
        assertThatThrownBy(() -> new OAuth2ClientHelper(null, "client-id", "client-secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Token endpoint cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty token endpoint")
    void shouldThrowExceptionForEmptyTokenEndpoint() {
        // When/Then
        assertThatThrownBy(() -> new OAuth2ClientHelper("", "client-id", "client-secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Token endpoint cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for null client ID")
    void shouldThrowExceptionForNullClientId() {
        // When/Then
        assertThatThrownBy(() -> new OAuth2ClientHelper("https://auth.example.com/token", null, "client-secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Client ID cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty client ID")
    void shouldThrowExceptionForEmptyClientId() {
        // When/Then
        assertThatThrownBy(() -> new OAuth2ClientHelper("https://auth.example.com/token", "", "client-secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Client ID cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for null client secret")
    void shouldThrowExceptionForNullClientSecret() {
        // When/Then
        assertThatThrownBy(() -> new OAuth2ClientHelper("https://auth.example.com/token", "client-id", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Client secret cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty client secret")
    void shouldThrowExceptionForEmptyClientSecret() {
        // When/Then
        assertThatThrownBy(() -> new OAuth2ClientHelper("https://auth.example.com/token", "client-id", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Client secret cannot be null or empty");
    }

    @Test
    @DisplayName("Should demonstrate OAuth2 helper API")
    void shouldDemonstrateOAuth2HelperApi() {
        // Given
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "my-client-id",
            "my-client-secret"
        );

        // Then: Helper should be properly configured
        assertThat(helper).isNotNull();
        
        // Note: Actual OAuth2 token requests would require a running OAuth2 server
        // For integration tests, use WireMock to mock the token endpoint
    }

    @Test
    @DisplayName("Should clear token cache")
    void shouldClearTokenCache() {
        // Given
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "client-id",
            "client-secret"
        );

        // When
        helper.clearCache();

        // Then: Cache should be cleared
        assertThat(helper.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create OAuth2 helper with advanced config")
    void shouldCreateOAuth2HelperWithAdvancedConfig() {
        // Given
        OAuth2ClientHelper.OAuth2Config config = OAuth2ClientHelper.OAuth2Config.builder()
            .timeout(Duration.ofMinutes(2))
            .enableRetry(true)
            .maxRetries(5)
            .retryBackoff(Duration.ofSeconds(1))
            .tokenExpirationBuffer(120)
            .defaultHeader("User-Agent", "MyApp/1.0")
            .build();

        // When
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "client-id",
            "client-secret",
            config
        );

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should build OAuth2 config with defaults")
    void shouldBuildOAuth2ConfigWithDefaults() {
        // When
        OAuth2ClientHelper.OAuth2Config config = OAuth2ClientHelper.OAuth2Config.builder()
            .build();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.toString()).contains("timeout=PT30S");
        assertThat(config.toString()).contains("enableRetry=false");
    }

    @Test
    @DisplayName("Should build OAuth2 config with custom values")
    void shouldBuildOAuth2ConfigWithCustomValues() {
        // When
        OAuth2ClientHelper.OAuth2Config config = OAuth2ClientHelper.OAuth2Config.builder()
            .timeout(Duration.ofMinutes(5))
            .enableRetry(true)
            .maxRetries(10)
            .retryBackoff(Duration.ofMillis(2000))
            .tokenExpirationBuffer(300)
            .defaultHeader("X-Custom", "value")
            .build();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.toString()).contains("timeout=PT5M");
        assertThat(config.toString()).contains("enableRetry=true");
        assertThat(config.toString()).contains("maxRetries=10");
    }

    @Test
    @DisplayName("Should check cache size")
    void shouldCheckCacheSize() {
        // Given
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "client-id",
            "client-secret"
        );

        // When/Then
        assertThat(helper.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should check if token is valid")
    void shouldCheckIfTokenIsValid() {
        // Given
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "client-id",
            "client-secret"
        );

        // When/Then
        assertThat(helper.hasValidToken("client_credentials:")).isFalse();
    }

    @Test
    @DisplayName("Should get cached refresh token")
    void shouldGetCachedRefreshToken() {
        // Given
        OAuth2ClientHelper helper = new OAuth2ClientHelper(
            "https://auth.example.com/oauth/token",
            "client-id",
            "client-secret"
        );

        // When/Then
        assertThat(helper.getCachedRefreshToken()).isNull();
    }

    @Test
    @DisplayName("Should create OAuth2 exception")
    void shouldCreateOAuth2Exception() {
        // When
        OAuth2ClientHelper.OAuth2Exception exception =
            new OAuth2ClientHelper.OAuth2Exception("Test error");

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo("Test error");
    }

    @Test
    @DisplayName("Should create OAuth2 exception with cause")
    void shouldCreateOAuth2ExceptionWithCause() {
        // Given
        Throwable cause = new RuntimeException("Root cause");

        // When
        OAuth2ClientHelper.OAuth2Exception exception =
            new OAuth2ClientHelper.OAuth2Exception("Test error", cause);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}

