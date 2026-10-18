package org.fireflyframework.example.service;

import org.fireflyframework.client.websocket.WebSocketClientHelper;
import org.fireflyframework.client.websocket.WebSocketClientHelper.WebSocketConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Example service demonstrating WebSocket client usage with:
 * - Automatic reconnection
 * - Heartbeat/ping-pong
 * - Message queuing
 * - Binary message support
 */
@Slf4j
@Service
public class WebSocketExampleService {

    private final WebSocketClientHelper wsClient;

    public WebSocketExampleService() {
        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .reconnectBackoff(Duration.ofSeconds(5))
            .maxReconnectAttempts(10)
            .enableHeartbeat(true)
            .heartbeatInterval(Duration.ofSeconds(30))
            .enableMessageQueue(true)
            .maxQueueSize(1000)
            .build();

        this.wsClient = new WebSocketClientHelper(
            "wss://api.example.com/ws",
            config
        );

        log.info("WebSocketExampleService initialized");
    }

    /**
     * Connect to the WebSocket server and start consuming messages.
     *
     * <p>{@link WebSocketClientHelper#receiveMessagesWithReconnection} establishes the
     * connection, delivers each inbound message to {@link #handleMessage(String)} and
     * transparently reconnects according to the configured reconnection policy.
     */
    public void connect() {
        log.info("Connecting to WebSocket server");

        wsClient.receiveMessagesWithReconnection(this::handleMessage)
            .doOnError(this::handleError)
            .subscribe();
    }

    /**
     * Disconnect from WebSocket server.
     */
    public void disconnect() {
        log.info("Disconnecting from WebSocket server");
        wsClient.disconnect();
    }

    /**
     * Send a text message.
     */
    public void sendMessage(String message) {
        log.info("Sending message: {}", message);
        wsClient.sendMessages(Flux.just(message))
            .doOnError(this::handleError)
            .subscribe();
    }

    /**
     * Send a binary message.
     */
    public void sendBinaryMessage(byte[] data) {
        log.info("Sending binary message ({} bytes)", data.length);
        wsClient.sendBinaryMessage(data)
            .doOnError(this::handleError)
            .subscribe();
    }

    /**
     * Check if connected.
     */
    public boolean isConnected() {
        return wsClient.isConnected();
    }

    /**
     * Get connection statistics.
     */
    public void logStatistics() {
        log.info("WebSocket Statistics:");
        log.info("  Connected: {}", wsClient.isConnected());
        log.info("  Queue size: {}", wsClient.getQueueSize());
        log.info("  Pending acks: {}", wsClient.getPendingAckCount());
        log.info("  Reconnect attempts: {}", wsClient.getReconnectAttempts());
    }

    /**
     * Handle incoming messages.
     */
    private void handleMessage(String message) {
        log.info("Received message: {}", message);

        // Process message here
        // For example, parse JSON and handle different message types
    }

    /**
     * Handle errors.
     */
    private void handleError(Throwable error) {
        log.error("WebSocket error: {}", error.getMessage(), error);

        // Handle error here
        // For example, notify monitoring system or trigger alerts
    }
}
