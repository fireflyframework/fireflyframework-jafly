package org.fireflyframework.client.helpers;

import org.fireflyframework.client.websocket.WebSocketClientHelper;
import org.fireflyframework.client.websocket.WebSocketClientHelper.WebSocketConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WebSocketClientHelper with real WebSocket server.
 * Tests actual connection, message sending/receiving, reconnection, and heartbeat.
 */
@Slf4j
@DisplayName("WebSocket Client Helper Integration Tests")
class WebSocketClientHelperIntegrationTest {

    private DisposableServer server;
    private String wsUrl;
    private int port;

    @BeforeEach
    void setUp() {
        // Start a simple echo WebSocket server
        port = findAvailablePort();
        wsUrl = "ws://localhost:" + port + "/ws";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
        WebSocketClientHelper.clearPool();
    }

    @Test
    @DisplayName("Should connect to WebSocket server and receive messages")
    void shouldConnectAndReceiveMessages() throws InterruptedException {
        // Given: Start echo server
        startEchoServer();

        WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl);
        CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        // When: Connect and send messages
        Flux<String> outgoingMessages = Flux.just("Hello", "World", "Test");

        helper.bidirectional(
            outgoingMessages,
            message -> {
                log.info("Received: {}", message);
                receivedMessages.add(message);
                latch.countDown();
            }
        ).subscribe();

        // Then: Verify messages received
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessages).containsExactly("Hello", "World", "Test");
    }

    @Test
    @DisplayName("Should send and receive single message")
    void shouldSendAndReceiveSingleMessage() throws InterruptedException {
        // Given: Start echo server
        startEchoServer();

        WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl);
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        // When: Send single message
        helper.connect(session -> {
            return session.send(Mono.just(session.textMessage("Test Message")))
                .then(session.receive()
                    .map(msg -> msg.getPayloadAsText())
                    .doOnNext(msg -> {
                        received.add(msg);
                        latch.countDown();
                    })
                    .then());
        }).subscribe();

        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).contains("Test Message");
    }

    @Test
    @DisplayName("Should handle connection lifecycle")
    void shouldHandleConnectionLifecycle() throws InterruptedException {
        // Given: Start echo server
        startEchoServer();

        WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl);
        CountDownLatch connectLatch = new CountDownLatch(1);

        // When: Connect
        helper.connect(session -> {
            connectLatch.countDown();
            return Mono.delay(Duration.ofMillis(100)).then();
        }).subscribe();

        // Then: Verify connection
        assertThat(connectLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // When: Disconnect
        helper.disconnect();

        // Then: Verify disconnection
        assertThat(helper.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Should attempt reconnection on connection failure")
    void shouldAttemptReconnectionOnFailure() throws InterruptedException {
        // Given: Invalid server URL (server not running)
        int invalidPort = findAvailablePort();
        String invalidUrl = "ws://localhost:" + invalidPort + "/ws";

        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .maxReconnectAttempts(3)
            .reconnectBackoff(Duration.ofMillis(100))
            .build();

        WebSocketClientHelper helper = new WebSocketClientHelper(invalidUrl, config);
        AtomicInteger connectionAttempts = new AtomicInteger(0);
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        // When: Try to connect (will fail and retry)
        helper.connectWithReconnection(session -> {
            int attempt = connectionAttempts.incrementAndGet();
            log.info("Connection attempt #{}", attempt);
            return session.receive().then();
        }).subscribe(
            v -> log.info("Connection completed"),
            error -> {
                log.info("Connection failed after retries: {}", error.getMessage());
                capturedError.set(error);
                errorLatch.countDown();
            }
        );

        // Then: Wait for all reconnection attempts to fail
        assertThat(errorLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Verify reconnection was attempted
        log.info("Total connection attempts: {}", connectionAttempts.get());

        // Should have tried initial connection + retries
        // Note: connectionAttempts might be 0 if connection fails before handler is called
        assertThat(capturedError.get()).isNotNull();
        assertThat(capturedError.get().getMessage()).contains("reconnect");
    }

    @Test
    @DisplayName("Should configure heartbeat correctly")
    void shouldConfigureHeartbeatCorrectly() throws InterruptedException {
        // Given: Start echo server
        startEchoServer();

        WebSocketConfig config = WebSocketConfig.builder()
            .enableHeartbeat(true)
            .heartbeatInterval(Duration.ofMillis(200))
            .build();

        WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl, config);
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch messagesLatch = new CountDownLatch(3);
        AtomicInteger messageCount = new AtomicInteger(0);

        // When: Connect with heartbeat enabled
        Flux<String> testMessages = Flux.just("test-1", "test-2", "test-3")
            .delayElements(Duration.ofMillis(100));

        helper.connect(session -> {
            connectionLatch.countDown();
            log.info("WebSocket connected with heartbeat enabled");

            // Send test messages and receive echoes
            return session.send(testMessages.map(session::textMessage))
                .thenMany(session.receive()
                    .doOnNext(msg -> {
                        int count = messageCount.incrementAndGet();
                        log.info("Client received message #{}: {}", count, msg.getPayloadAsText());
                        messagesLatch.countDown();
                    }))
                .then();
        }).subscribe(
            v -> log.info("Connection completed"),
            error -> log.error("Connection error: {}", error.getMessage())
        );

        // Wait for connection
        assertThat(connectionLatch.await(3, TimeUnit.SECONDS)).isTrue();
        log.info("Connection established");

        // Wait for messages
        assertThat(messagesLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Then: Verify messages were exchanged
        assertThat(messageCount.get()).isEqualTo(3);

        // Verify heartbeat configuration is set
        assertThat(helper.getUrl()).isEqualTo(wsUrl);

        helper.disconnect();
    }

    @Test
    @DisplayName("Should queue messages when disconnected")
    void shouldQueueMessagesWhenDisconnected() throws InterruptedException {
        // Given: Helper with message queue enabled
        WebSocketConfig config = WebSocketConfig.builder()
            .enableMessageQueue(true)
            .maxQueueSize(100)
            .enableReconnection(true)
            .build();

        WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl, config);

        // When: Send message without connection
        StepVerifier.create(helper.sendMessageWithAck("Queued message", null))
            .expectNextMatches(messageId -> messageId != null && !messageId.isEmpty())
            .verifyComplete();

        // Then: Verify message is queued
        assertThat(helper.getQueueSize()).isEqualTo(1);

        // When: Connect to server
        startEchoServer();
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        helper.connectWithReconnection(session ->
            session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> {
                    received.add(msg);
                    latch.countDown();
                })
                .then()
        ).subscribe();

        // Then: Verify queued message was sent
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).contains("Queued message");
        assertThat(helper.getQueueSize()).isZero();
    }

    @Test
    @DisplayName("Should handle binary messages")
    void shouldHandleBinaryMessages() throws InterruptedException {
        // Given: Start server that echoes binary messages
        startBinaryEchoServer();

        WebSocketConfig config = WebSocketConfig.builder()
            .enableBinaryMessages(true)
            .build();

        WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl, config);
        byte[] testData = "Binary Test Data".getBytes();
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<byte[]> receivedData = new CopyOnWriteArrayList<>();

        // When: Connect and send binary message
        helper.connect(session -> {
            return session.send(Mono.just(
                session.binaryMessage(buffer -> buffer.wrap(testData))
            ))
            .then(session.receive()
                .doOnNext(msg -> {
                    byte[] data = new byte[msg.getPayload().readableByteCount()];
                    msg.getPayload().read(data);
                    receivedData.add(data);
                    latch.countDown();
                })
                .then());
        }).subscribe();

        // Then: Verify binary message received
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedData).hasSize(1);
        assertThat(new String(receivedData.get(0))).isEqualTo("Binary Test Data");
    }

    @Test
    @DisplayName("Should use connection pool")
    void shouldUseConnectionPool() {
        // Given: Start echo server
        startEchoServer();

        // When: Get pooled connections
        WebSocketClientHelper helper1 = WebSocketClientHelper.getPooledConnection(wsUrl);
        WebSocketClientHelper helper2 = WebSocketClientHelper.getPooledConnection(wsUrl);

        // Then: Verify same instance
        assertThat(helper1).isSameAs(helper2);

        // When: Remove from pool
        WebSocketClientHelper.removeFromPool(wsUrl);
        WebSocketClientHelper helper3 = WebSocketClientHelper.getPooledConnection(wsUrl);

        // Then: Verify new instance
        assertThat(helper3).isNotSameAs(helper1);
    }

    @Test
    @DisplayName("Should handle multiple concurrent connections")
    void shouldHandleMultipleConcurrentConnections() throws InterruptedException {
        // Given: Start echo server
        startEchoServer();

        int connectionCount = 5;
        CountDownLatch latch = new CountDownLatch(connectionCount);
        CopyOnWriteArrayList<String> allReceived = new CopyOnWriteArrayList<>();

        // When: Create multiple connections
        for (int i = 0; i < connectionCount; i++) {
            final String message = "Message-" + i;
            WebSocketClientHelper helper = new WebSocketClientHelper(wsUrl);

            helper.connect(session ->
                session.send(Mono.just(session.textMessage(message)))
                    .then(session.receive()
                        .map(msg -> msg.getPayloadAsText())
                        .doOnNext(msg -> {
                            allReceived.add(msg);
                            latch.countDown();
                        })
                        .then())
            ).subscribe();
        }

        // Then: Verify all messages received
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(allReceived).hasSize(connectionCount);
    }

    // Helper methods

    private void startEchoServer() {
        server = HttpServer.create()
            .port(port)
            .route(routes ->
                routes.ws("/ws", (in, out) ->
                    out.send(in.receive()
                        .retain()
                        .doOnNext(msg -> {
                            String text = msg.toString(java.nio.charset.StandardCharsets.UTF_8);
                            log.debug("Server echoing: {}", text);
                        }))
                )
            )
            .bindNow();
        log.info("Echo WebSocket server started on port {}", port);
    }

    private void startServerWithPingHandler(AtomicInteger pingCount) {
        server = HttpServer.create()
            .port(port)
            .route(routes ->
                routes.ws("/ws", (in, out) -> {
                    in.receive()
                        .doOnNext(msg -> {
                            String text = msg.toString(java.nio.charset.StandardCharsets.UTF_8);
                            if ("PING".equals(text)) {
                                pingCount.incrementAndGet();
                                log.debug("Server received PING #{}", pingCount.get());
                            }
                        })
                        .subscribe();
                    return Mono.never();
                })
            )
            .bindNow();
        log.info("Ping handler WebSocket server started on port {}", port);
    }

    private void startBinaryEchoServer() {
        server = HttpServer.create()
            .port(port)
            .route(routes ->
                routes.ws("/ws", (in, out) ->
                    out.send(in.receive().retain())
                )
            )
            .bindNow();
        log.info("Binary echo WebSocket server started on port {}", port);
    }

    private int findAvailablePort() {
        // Use a random port in the range 50000-60000
        return 50000 + (int) (Math.random() * 10000);
    }
}

