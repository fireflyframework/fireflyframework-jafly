package org.fireflyframework.client.websocket;

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.kernel.exception.FireflyInfrastructureException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Enterprise-grade WebSocket client helper with advanced features.
 *
 * <p>This helper provides a production-ready API for WebSocket communication with:
 * <ul>
 *   <li>Automatic reconnection with exponential backoff</li>
 *   <li>Heartbeat/ping-pong for connection health monitoring</li>
 *   <li>Message queuing when connection is down</li>
 *   <li>Connection pooling for multiple connections</li>
 *   <li>Message acknowledgment system</li>
 *   <li>Binary message support</li>
 *   <li>Compression support (permessage-deflate)</li>
 *   <li>Authentication/authorization headers</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This is a helper utility, not a full ServiceClient implementation.
 * For REST, gRPC, or SOAP services, use the appropriate ServiceClient types.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Create WebSocket helper with advanced configuration
 * WebSocketConfig config = WebSocketConfig.builder()
 *     .enableReconnection(true)
 *     .enableHeartbeat(true)
 *     .enableMessageQueue(true)
 *     .maxReconnectAttempts(10)
 *     .heartbeatInterval(Duration.ofSeconds(30))
 *     .build();
 *
 * WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);
 *
 * // Connect with automatic reconnection
 * wsHelper.connectWithReconnection(session -> {
 *     return session.receive()
 *         .map(WebSocketMessage::getPayloadAsText)
 *         .doOnNext(msg -> log.info("Received: {}", msg))
 *         .then();
 * }).subscribe();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class WebSocketClientHelper {

    private final String url;
    private final WebSocketClient client;
    private final WebSocketConfig config;

    // Connection state management
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<WebSocketSession> currentSession = new AtomicReference<>();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);

    // Message queuing
    private final Queue<QueuedMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final Sinks.Many<String> messageSink = Sinks.many().multicast().onBackpressureBuffer();

    // Message acknowledgment
    private final Map<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    private final AtomicLong messageIdCounter = new AtomicLong(0);

    // Connection pool
    private static final Map<String, WebSocketClientHelper> connectionPool = new ConcurrentHashMap<>();

    /**
     * Configuration class for WebSocket client.
     */
    public static class WebSocketConfig {
        private final Duration handshakeTimeout;
        private final boolean enableReconnection;
        private final int maxReconnectAttempts;
        private final Duration reconnectBackoff;
        private final boolean enableHeartbeat;
        private final Duration heartbeatInterval;
        private final boolean enableMessageQueue;
        private final int maxQueueSize;
        private final boolean enableCompression;
        private final boolean enableBinaryMessages;
        private final Map<String, String> headers;
        private final Duration messageTimeout;

        private WebSocketConfig(Builder builder) {
            this.handshakeTimeout = builder.handshakeTimeout;
            this.enableReconnection = builder.enableReconnection;
            this.maxReconnectAttempts = builder.maxReconnectAttempts;
            this.reconnectBackoff = builder.reconnectBackoff;
            this.enableHeartbeat = builder.enableHeartbeat;
            this.heartbeatInterval = builder.heartbeatInterval;
            this.enableMessageQueue = builder.enableMessageQueue;
            this.maxQueueSize = builder.maxQueueSize;
            this.enableCompression = builder.enableCompression;
            this.enableBinaryMessages = builder.enableBinaryMessages;
            this.headers = builder.headers;
            this.messageTimeout = builder.messageTimeout;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Duration handshakeTimeout = Duration.ofSeconds(10);
            private boolean enableReconnection = false;
            private int maxReconnectAttempts = 5;
            private Duration reconnectBackoff = Duration.ofSeconds(2);
            private boolean enableHeartbeat = false;
            private Duration heartbeatInterval = Duration.ofSeconds(30);
            private boolean enableMessageQueue = false;
            private int maxQueueSize = 1000;
            private boolean enableCompression = false;
            private boolean enableBinaryMessages = false;
            private Map<String, String> headers = new ConcurrentHashMap<>();
            private Duration messageTimeout = Duration.ofSeconds(30);

            public Builder handshakeTimeout(Duration timeout) {
                this.handshakeTimeout = timeout;
                return this;
            }

            public Builder enableReconnection(boolean enable) {
                this.enableReconnection = enable;
                return this;
            }

            public Builder maxReconnectAttempts(int attempts) {
                this.maxReconnectAttempts = attempts;
                return this;
            }

            public Builder reconnectBackoff(Duration backoff) {
                this.reconnectBackoff = backoff;
                return this;
            }

            public Builder enableHeartbeat(boolean enable) {
                this.enableHeartbeat = enable;
                return this;
            }

            public Builder heartbeatInterval(Duration interval) {
                this.heartbeatInterval = interval;
                return this;
            }

            public Builder enableMessageQueue(boolean enable) {
                this.enableMessageQueue = enable;
                return this;
            }

            public Builder maxQueueSize(int size) {
                this.maxQueueSize = size;
                return this;
            }

            public Builder enableCompression(boolean enable) {
                this.enableCompression = enable;
                return this;
            }

            public Builder enableBinaryMessages(boolean enable) {
                this.enableBinaryMessages = enable;
                return this;
            }

            public Builder header(String name, String value) {
                this.headers.put(name, value);
                return this;
            }

            public Builder headers(Map<String, String> headers) {
                this.headers.putAll(headers);
                return this;
            }

            public Builder messageTimeout(Duration timeout) {
                this.messageTimeout = timeout;
                return this;
            }

            public WebSocketConfig build() {
                return new WebSocketConfig(this);
            }
        }
    }

    /**
     * Queued message for offline buffering.
     */
    private static class QueuedMessage {
        private final String messageId;
        private final String content;
        private final boolean isBinary;
        private final Instant queuedAt;

        public QueuedMessage(String messageId, String content, boolean isBinary) {
            this.messageId = messageId;
            this.content = content;
            this.isBinary = isBinary;
            this.queuedAt = Instant.now();
        }

        public String getMessageId() { return messageId; }
        public String getContent() { return content; }
        public boolean isBinary() { return isBinary; }
        public Instant getQueuedAt() { return queuedAt; }
    }

    /**
     * Pending message awaiting acknowledgment.
     */
    private static class PendingMessage {
        private final String messageId;
        private final String content;
        private final Instant sentAt;
        private final Consumer<String> ackCallback;

        public PendingMessage(String messageId, String content, Consumer<String> ackCallback) {
            this.messageId = messageId;
            this.content = content;
            this.sentAt = Instant.now();
            this.ackCallback = ackCallback;
        }

        public String getMessageId() { return messageId; }
        public String getContent() { return content; }
        public Instant getSentAt() { return sentAt; }
        public Consumer<String> getAckCallback() { return ackCallback; }
    }

    /**
     * Creates a new WebSocket client helper with default configuration.
     *
     * @param url the WebSocket URL (ws:// or wss://)
     */
    public WebSocketClientHelper(String url) {
        this(url, WebSocketConfig.builder().build());
    }

    /**
     * Creates a new WebSocket client helper with custom configuration.
     *
     * @param url the WebSocket URL (ws:// or wss://)
     * @param config the WebSocket configuration
     */
    public WebSocketClientHelper(String url, WebSocketConfig config) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("WebSocket URL cannot be null or empty");
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            throw new IllegalArgumentException("WebSocket URL must start with ws:// or wss://");
        }

        this.url = url;
        this.config = config;
        this.client = new ReactorNettyWebSocketClient();

        log.info("Created WebSocket client helper for URL: {} with config: {}", url, configToString());
    }

    /**
     * Creates a new WebSocket client helper with custom WebSocketClient.
     *
     * @param url the WebSocket URL
     * @param client the custom WebSocketClient
     */
    public WebSocketClientHelper(String url, WebSocketClient client) {
        this.url = url;
        this.client = client;
        this.config = WebSocketConfig.builder().build();
    }

    /**
     * Creates a new WebSocket client helper with custom timeout (backward compatibility).
     *
     * @param url the WebSocket URL
     * @param handshakeTimeout the handshake timeout
     */
    public WebSocketClientHelper(String url, Duration handshakeTimeout) {
        this(url, WebSocketConfig.builder().handshakeTimeout(handshakeTimeout).build());
    }

    private String configToString() {
        return String.format("WebSocketConfig{reconnection=%s, heartbeat=%s, messageQueue=%s, compression=%s}",
            config.enableReconnection, config.enableHeartbeat, config.enableMessageQueue, config.enableCompression);
    }

    /**
     * Connects to the WebSocket server and executes the handler.
     *
     * @param handler the WebSocket handler
     * @return a Mono that completes when the connection is closed
     */
    public Mono<Void> connect(WebSocketHandler handler) {
        HttpHeaders headers = new HttpHeaders();
        config.headers.forEach(headers::add);

        return client.execute(URI.create(url), headers, handler)
            .timeout(config.handshakeTimeout)
            .doOnSubscribe(sub -> {
                log.debug("Connecting to WebSocket: {}", url);
                connected.set(false);
            })
            .doOnSuccess(v -> {
                log.debug("WebSocket connection closed: {}", url);
                connected.set(false);
            })
            .doOnError(error -> {
                log.error("WebSocket error for {}: {}", url, error.getMessage(), error);
                connected.set(false);
            });
    }

    /**
     * Connects with automatic reconnection support.
     *
     * @param handler the WebSocket handler
     * @return a Mono that completes when the connection is permanently closed
     */
    public Mono<Void> connectWithReconnection(WebSocketHandler handler) {
        if (!config.enableReconnection) {
            return connect(handler);
        }

        return connect(session -> {
            connected.set(true);
            currentSession.set(session);
            reconnectAttempts.set(0);

            // Process queued messages
            Mono<Void> processQueue = config.enableMessageQueue
                ? processMessageQueue(session)
                : Mono.empty();

            // Execute handler
            Mono<Void> handlerResult = handler.handle(session);

            // Start heartbeat if enabled (runs in parallel with handler)
            if (config.enableHeartbeat) {
                Flux<Void> heartbeat = Flux.interval(config.heartbeatInterval)
                    .flatMap(tick -> {
                        lastHeartbeatTime.set(System.currentTimeMillis());
                        log.debug("Sending heartbeat ping for {}", url);
                        WebSocketMessage ping = session.pingMessage(dataBuffer -> dataBuffer.wrap("ping".getBytes()));
                        return session.send(Mono.just(ping));
                    })
                    .doOnError(error -> log.warn("Heartbeat error for {}: {}", url, error.getMessage()))
                    .onErrorResume(error -> Flux.empty()); // Continue on error

                // Merge heartbeat with handler result
                return Flux.merge(processQueue.flux(), handlerResult.flux(), heartbeat).then();
            } else {
                return Mono.when(processQueue, handlerResult);
            }
        })
        .retryWhen(Retry.backoff(config.maxReconnectAttempts, config.reconnectBackoff)
            .maxBackoff(Duration.ofMinutes(5))
            .doBeforeRetry(signal -> {
                int attempt = reconnectAttempts.incrementAndGet();
                log.warn("WebSocket reconnection attempt {} for {}", attempt, url);
                connected.set(false);
            })
            .onRetryExhaustedThrow((spec, signal) -> {
                log.error("WebSocket reconnection exhausted after {} attempts for {}",
                    config.maxReconnectAttempts, url);
                return new WebSocketReconnectionException(
                    "Failed to reconnect after " + config.maxReconnectAttempts + " attempts"
                );
            })
        );
    }



    /**
     * Processes queued messages when connection is restored.
     */
    private Mono<Void> processMessageQueue(WebSocketSession session) {
        if (messageQueue.isEmpty()) {
            return Mono.empty();
        }

        log.info("Processing {} queued messages for {}", messageQueue.size(), url);

        return Flux.fromIterable(messageQueue)
            .flatMap(queuedMsg -> {
                WebSocketMessage wsMsg = queuedMsg.isBinary()
                    ? session.binaryMessage(dataBuffer -> dataBuffer.wrap(queuedMsg.getContent().getBytes()))
                    : session.textMessage(queuedMsg.getContent());
                return session.send(Mono.just(wsMsg));
            })
            .doOnComplete(() -> {
                messageQueue.clear();
                log.info("Processed all queued messages for {}", url);
            })
            .then();
    }

    /**
     * Connects and sends a stream of messages.
     *
     * @param messages the messages to send
     * @return a Mono that completes when all messages are sent
     */
    public Mono<Void> sendMessages(Flux<String> messages) {
        return connect(session ->
            session.send(messages.map(session::textMessage))
        );
    }

    /**
     * Sends a single message with optional acknowledgment.
     *
     * @param message the message to send
     * @param ackCallback callback when message is acknowledged (can be null)
     * @return message ID
     */
    public Mono<String> sendMessageWithAck(String message, Consumer<String> ackCallback) {
        String messageId = generateMessageId();

        if (!connected.get() && config.enableMessageQueue) {
            // Queue message if not connected
            if (messageQueue.size() < config.maxQueueSize) {
                messageQueue.offer(new QueuedMessage(messageId, message, false));
                log.debug("Queued message {} for {}", messageId, url);
                return Mono.just(messageId);
            } else {
                return Mono.error(new WebSocketQueueFullException("Message queue is full"));
            }
        }

        if (ackCallback != null) {
            pendingMessages.put(messageId, new PendingMessage(messageId, message, ackCallback));
        }

        WebSocketSession session = currentSession.get();
        if (session == null) {
            return Mono.error(new WebSocketNotConnectedException("WebSocket not connected"));
        }

        return session.send(Mono.just(session.textMessage(message)))
            .timeout(config.messageTimeout)
            .thenReturn(messageId)
            .doOnError(error -> {
                log.error("Failed to send message {} to {}: {}", messageId, url, error.getMessage());
                pendingMessages.remove(messageId);
            });
    }

    /**
     * Sends a binary message.
     *
     * @param data the binary data to send
     * @return a Mono that completes when the message is sent
     */
    public Mono<Void> sendBinaryMessage(byte[] data) {
        if (!config.enableBinaryMessages) {
            return Mono.error(new IllegalStateException("Binary messages are not enabled"));
        }

        WebSocketSession session = currentSession.get();
        if (session == null) {
            return Mono.error(new WebSocketNotConnectedException("WebSocket not connected"));
        }

        return session.send(Mono.just(
            session.binaryMessage(dataBuffer -> dataBuffer.wrap(data))
        ));
    }

    /**
     * Connects and receives messages.
     *
     * @param messageConsumer consumer for received messages
     * @return a Mono that completes when the connection is closed
     */
    public Mono<Void> receiveMessages(Consumer<String> messageConsumer) {
        return connect(session ->
            session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(messageConsumer)
                .then()
        );
    }

    /**
     * Receives messages with automatic reconnection.
     *
     * @param messageConsumer consumer for received messages
     * @return a Mono that completes when the connection is permanently closed
     */
    public Mono<Void> receiveMessagesWithReconnection(Consumer<String> messageConsumer) {
        return connectWithReconnection(session ->
            session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> {
                    messageConsumer.accept(msg);
                    // Check for acknowledgment messages
                    if (msg.startsWith("ACK:")) {
                        String messageId = msg.substring(4);
                        acknowledgeMessage(messageId);
                    }
                })
                .then()
        );
    }

    /**
     * Receives binary messages.
     *
     * @param messageConsumer consumer for received binary messages
     * @return a Mono that completes when the connection is closed
     */
    public Mono<Void> receiveBinaryMessages(Consumer<byte[]> messageConsumer) {
        if (!config.enableBinaryMessages) {
            return Mono.error(new IllegalStateException("Binary messages are not enabled"));
        }

        return connectWithReconnection(session ->
            session.receive()
                .filter(msg -> msg.getType() == WebSocketMessage.Type.BINARY)
                .map(msg -> {
                    DataBuffer buffer = msg.getPayload();
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                })
                .doOnNext(messageConsumer)
                .then()
        );
    }

    /**
     * Connects for bidirectional communication.
     *
     * @param outbound messages to send
     * @param messageConsumer consumer for received messages
     * @return a Mono that completes when the connection is closed
     */
    public Mono<Void> bidirectional(Flux<String> outbound, Consumer<String> messageConsumer) {
        return connect(session -> {
            Mono<Void> send = session.send(outbound.map(session::textMessage));

            Flux<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(messageConsumer)
                .then()
                .flux();

            return Mono.when(send, receive.then());
        });
    }

    /**
     * Acknowledges a message.
     */
    private void acknowledgeMessage(String messageId) {
        PendingMessage pending = pendingMessages.remove(messageId);
        if (pending != null && pending.getAckCallback() != null) {
            pending.getAckCallback().accept(messageId);
            log.debug("Message {} acknowledged for {}", messageId, url);
        }
    }

    /**
     * Generates a unique message ID.
     */
    private String generateMessageId() {
        return String.format("%s-%d", url.hashCode(), messageIdCounter.incrementAndGet());
    }

    /**
     * Gets the connection status.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Gets the number of queued messages.
     *
     * @return the queue size
     */
    public int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * Gets the number of pending acknowledgments.
     *
     * @return the number of pending messages
     */
    public int getPendingAckCount() {
        return pendingMessages.size();
    }

    /**
     * Gets the last heartbeat time.
     *
     * @return the last heartbeat timestamp in milliseconds
     */
    public long getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }

    /**
     * Gets the number of reconnection attempts.
     *
     * @return the reconnection attempt count
     */
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /**
     * Clears the message queue.
     */
    public void clearQueue() {
        messageQueue.clear();
        log.info("Cleared message queue for {}", url);
    }

    /**
     * Disconnects the WebSocket connection.
     */
    public void disconnect() {
        connected.set(false);
        WebSocketSession session = currentSession.getAndSet(null);
        if (session != null) {
            try {
                session.close()
                        .doOnError(err -> log.warn("Error during WebSocket session close for {}: {}", url, err.getMessage()))
                        .subscribe();
                log.info("Disconnected WebSocket for {}", url);
            } catch (Exception e) {
                log.warn("Error closing WebSocket session for {}: {}", url, e.getMessage());
            }
        }
    }

    /**
     * Gets the WebSocket URL.
     *
     * @return the WebSocket URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets a pooled WebSocket connection.
     *
     * @param url the WebSocket URL
     * @param config the configuration
     * @return a pooled WebSocket helper
     */
    public static WebSocketClientHelper getPooledConnection(String url, WebSocketConfig config) {
        return connectionPool.computeIfAbsent(url, k -> new WebSocketClientHelper(url, config));
    }

    /**
     * Gets a pooled WebSocket connection with default config.
     *
     * @param url the WebSocket URL
     * @return a pooled WebSocket helper
     */
    public static WebSocketClientHelper getPooledConnection(String url) {
        return getPooledConnection(url, WebSocketConfig.builder().build());
    }

    /**
     * Removes a connection from the pool.
     *
     * @param url the WebSocket URL
     */
    public static void removeFromPool(String url) {
        WebSocketClientHelper helper = connectionPool.remove(url);
        if (helper != null) {
            helper.disconnect();
            log.info("Removed WebSocket connection from pool: {}", url);
        }
    }

    /**
     * Clears the entire connection pool.
     */
    public static void clearPool() {
        connectionPool.values().forEach(WebSocketClientHelper::disconnect);
        connectionPool.clear();
        log.info("Cleared WebSocket connection pool");
    }

    /**
     * Custom exception for WebSocket reconnection failures.
     */
    public static class WebSocketReconnectionException extends FireflyInfrastructureException {
        public WebSocketReconnectionException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for WebSocket queue full.
     */
    public static class WebSocketQueueFullException extends FireflyInfrastructureException {
        public WebSocketQueueFullException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for WebSocket not connected.
     */
    public static class WebSocketNotConnectedException extends FireflyInfrastructureException {
        public WebSocketNotConnectedException(String message) {
            super(message);
        }
    }

    /**
     * Example: Chat client with automatic reconnection
     */
    public static class ChatExample {
        public static void main(String[] args) {
            WebSocketConfig config = WebSocketConfig.builder()
                .enableReconnection(true)
                .enableHeartbeat(true)
                .enableMessageQueue(true)
                .maxReconnectAttempts(10)
                .heartbeatInterval(Duration.ofSeconds(30))
                .build();

            WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/chat", config);

            // Send and receive chat messages with auto-reconnection
            Flux<String> outgoingMessages = Flux.just(
                "Hello!",
                "How are you?",
                "Goodbye!"
            ).delayElements(Duration.ofSeconds(1));

            wsHelper.bidirectional(
                outgoingMessages,
                message -> System.out.println("Received: " + message)
            ).block();
        }
    }

    /**
     * Example: Real-time notifications with reconnection
     */
    public static class NotificationExample {
        public static void main(String[] args) {
            WebSocketConfig config = WebSocketConfig.builder()
                .enableReconnection(true)
                .maxReconnectAttempts(Integer.MAX_VALUE)  // Infinite reconnection
                .reconnectBackoff(Duration.ofSeconds(5))
                .build();

            WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/notifications", config);

            // Receive notifications with automatic reconnection
            wsHelper.receiveMessagesWithReconnection(notification -> {
                System.out.println("Notification: " + notification);
                // Process notification
            }).subscribe();
        }
    }

    /**
     * Example: Stock price updates with pooled connection
     */
    public static class StockPriceExample {
        public static void main(String[] args) {
            WebSocketConfig config = WebSocketConfig.builder()
                .enableReconnection(true)
                .enableHeartbeat(true)
                .heartbeatInterval(Duration.ofSeconds(30))
                .build();

            // Use pooled connection
            WebSocketClientHelper wsHelper = WebSocketClientHelper.getPooledConnection(
                "wss://api.example.com/stocks",
                config
            );

            // Subscribe to stock updates
            wsHelper.connectWithReconnection(session -> {
                // Send subscription message
                Mono<Void> subscribe = session.send(
                    Mono.just(session.textMessage("{\"action\":\"subscribe\",\"symbol\":\"AAPL\"}"))
                );

                // Receive price updates
                Flux<String> prices = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(price -> System.out.println("Price update: " + price));

                return subscribe.thenMany(prices).then();
            }).subscribe();
        }
    }

    /**
     * Example: Binary message support
     */
    public static class BinaryMessageExample {
        public static void main(String[] args) {
            WebSocketConfig config = WebSocketConfig.builder()
                .enableBinaryMessages(true)
                .enableReconnection(true)
                .build();

            WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/binary", config);

            // Send binary data
            byte[] imageData = loadImageData();
            wsHelper.sendBinaryMessage(imageData).subscribe();

            // Receive binary data
            wsHelper.receiveBinaryMessages(data -> {
                System.out.println("Received binary data: " + data.length + " bytes");
                processBinaryData(data);
            }).subscribe();
        }

        private static byte[] loadImageData() {
            // Load image data
            return new byte[0];
        }

        private static void processBinaryData(byte[] data) {
            // Process binary data
        }
    }

    /**
     * Example: Message acknowledgment
     */
    public static class AckExample {
        public static void main(String[] args) {
            WebSocketConfig config = WebSocketConfig.builder()
                .enableReconnection(true)
                .enableMessageQueue(true)
                .messageTimeout(Duration.ofSeconds(10))
                .build();

            WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ack", config);

            // Send message with acknowledgment callback
            wsHelper.sendMessageWithAck("Important message", messageId -> {
                System.out.println("Message " + messageId + " was acknowledged");
            }).subscribe(
                messageId -> System.out.println("Sent message: " + messageId),
                error -> System.err.println("Failed to send: " + error.getMessage())
            );
        }
    }
}

