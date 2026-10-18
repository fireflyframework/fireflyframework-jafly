package org.fireflyframework.client.helpers;

import org.fireflyframework.client.websocket.WebSocketClientHelper;
import org.fireflyframework.client.websocket.WebSocketClientHelper.WebSocketConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WebSocketClientHelper.
 */
@DisplayName("WebSocket Client Helper Tests")
class WebSocketClientHelperTest {

    @AfterEach
    void cleanup() {
        // Clear connection pool after each test
        WebSocketClientHelper.clearPool();
    }

    @Test
    @DisplayName("Should create WebSocket helper with valid URL")
    void shouldCreateWebSocketHelperWithValidUrl() {
        // When
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws");

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getUrl()).isEqualTo("ws://localhost:8080/ws");
        assertThat(helper.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Should create WebSocket helper with wss URL")
    void shouldCreateWebSocketHelperWithWssUrl() {
        // When
        WebSocketClientHelper helper = new WebSocketClientHelper("wss://secure.example.com/ws");

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getUrl()).isEqualTo("wss://secure.example.com/ws");
    }

    @Test
    @DisplayName("Should create WebSocket helper with custom timeout")
    void shouldCreateWebSocketHelperWithCustomTimeout() {
        // When
        WebSocketClientHelper helper = new WebSocketClientHelper(
            "ws://localhost:8080/ws",
            Duration.ofSeconds(30)
        );

        // Then
        assertThat(helper).isNotNull();
    }

    @Test
    @DisplayName("Should create WebSocket helper with advanced configuration")
    void shouldCreateWebSocketHelperWithAdvancedConfig() {
        // Given
        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .maxReconnectAttempts(10)
            .reconnectBackoff(Duration.ofSeconds(5))
            .enableHeartbeat(true)
            .heartbeatInterval(Duration.ofSeconds(30))
            .enableMessageQueue(true)
            .maxQueueSize(500)
            .enableCompression(true)
            .enableBinaryMessages(true)
            .header("Authorization", "Bearer token123")
            .messageTimeout(Duration.ofSeconds(60))
            .build();

        // When
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws", config);

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getUrl()).isEqualTo("ws://localhost:8080/ws");
        assertThat(helper.isConnected()).isFalse();
        assertThat(helper.getQueueSize()).isZero();
        assertThat(helper.getPendingAckCount()).isZero();
        assertThat(helper.getReconnectAttempts()).isZero();
    }

    @Test
    @DisplayName("Should build WebSocket config with defaults")
    void shouldBuildWebSocketConfigWithDefaults() {
        // When
        WebSocketConfig config = WebSocketConfig.builder().build();

        // Then
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("Should build WebSocket config with custom values")
    void shouldBuildWebSocketConfigWithCustomValues() {
        // When
        WebSocketConfig config = WebSocketConfig.builder()
            .handshakeTimeout(Duration.ofSeconds(20))
            .enableReconnection(true)
            .maxReconnectAttempts(15)
            .reconnectBackoff(Duration.ofSeconds(3))
            .enableHeartbeat(true)
            .heartbeatInterval(Duration.ofSeconds(45))
            .enableMessageQueue(true)
            .maxQueueSize(2000)
            .enableCompression(true)
            .enableBinaryMessages(true)
            .header("X-Custom-Header", "value")
            .messageTimeout(Duration.ofMinutes(2))
            .build();

        // Then
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception for null URL")
    void shouldThrowExceptionForNullUrl() {
        // When/Then
        assertThatThrownBy(() -> new WebSocketClientHelper(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("WebSocket URL cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty URL")
    void shouldThrowExceptionForEmptyUrl() {
        // When/Then
        assertThatThrownBy(() -> new WebSocketClientHelper(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("WebSocket URL cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for invalid URL protocol")
    void shouldThrowExceptionForInvalidUrlProtocol() {
        // When/Then
        assertThatThrownBy(() -> new WebSocketClientHelper("http://localhost:8080/ws"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("WebSocket URL must start with ws:// or wss://");
    }

    @Test
    @DisplayName("Should get connection status")
    void shouldGetConnectionStatus() {
        // Given
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws");

        // Then
        assertThat(helper.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Should get queue size")
    void shouldGetQueueSize() {
        // Given
        WebSocketConfig config = WebSocketConfig.builder()
            .enableMessageQueue(true)
            .build();
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws", config);

        // Then
        assertThat(helper.getQueueSize()).isZero();
    }

    @Test
    @DisplayName("Should get pending acknowledgment count")
    void shouldGetPendingAckCount() {
        // Given
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws");

        // Then
        assertThat(helper.getPendingAckCount()).isZero();
    }

    @Test
    @DisplayName("Should get reconnect attempts")
    void shouldGetReconnectAttempts() {
        // Given
        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .build();
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws", config);

        // Then
        assertThat(helper.getReconnectAttempts()).isZero();
    }

    @Test
    @DisplayName("Should clear message queue")
    void shouldClearMessageQueue() {
        // Given
        WebSocketConfig config = WebSocketConfig.builder()
            .enableMessageQueue(true)
            .build();
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws", config);

        // When
        helper.clearQueue();

        // Then
        assertThat(helper.getQueueSize()).isZero();
    }

    @Test
    @DisplayName("Should disconnect WebSocket")
    void shouldDisconnectWebSocket() {
        // Given
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/ws");

        // When
        helper.disconnect();

        // Then
        assertThat(helper.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Should get pooled connection")
    void shouldGetPooledConnection() {
        // When
        WebSocketClientHelper helper1 = WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws");
        WebSocketClientHelper helper2 = WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws");

        // Then
        assertThat(helper1).isSameAs(helper2);
    }

    @Test
    @DisplayName("Should get pooled connection with config")
    void shouldGetPooledConnectionWithConfig() {
        // Given
        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .build();

        // When
        WebSocketClientHelper helper = WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws", config);

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getUrl()).isEqualTo("ws://localhost:8080/ws");
    }

    @Test
    @DisplayName("Should remove connection from pool")
    void shouldRemoveConnectionFromPool() {
        // Given
        WebSocketClientHelper helper = WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws");

        // When
        WebSocketClientHelper.removeFromPool("ws://localhost:8080/ws");

        // Then
        WebSocketClientHelper newHelper = WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws");
        assertThat(newHelper).isNotSameAs(helper);
    }

    @Test
    @DisplayName("Should clear connection pool")
    void shouldClearConnectionPool() {
        // Given
        WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws1");
        WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws2");

        // When
        WebSocketClientHelper.clearPool();

        // Then: New connections should be created
        WebSocketClientHelper helper1 = WebSocketClientHelper.getPooledConnection("ws://localhost:8080/ws1");
        assertThat(helper1).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate WebSocket helper API")
    void shouldDemonstrateWebSocketHelperApi() {
        // Given
        WebSocketClientHelper helper = new WebSocketClientHelper("ws://localhost:8080/notifications");

        // Then: Helper should be properly configured
        assertThat(helper).isNotNull();
        assertThat(helper.getUrl()).isEqualTo("ws://localhost:8080/notifications");
        assertThat(helper.isConnected()).isFalse();

        // Note: Actual WebSocket connection tests would require a running WebSocket server
        // For integration tests, use embedded WebSocket server or test containers
    }
}

