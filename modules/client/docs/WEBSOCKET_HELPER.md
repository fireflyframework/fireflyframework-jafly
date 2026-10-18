# WebSocket Client Helper Guide

Enterprise-grade guide for using the WebSocket client helper in the Firefly Common Client Library.

---

## Table of Contents

1. [Overview](#overview)
2. [When to Use WebSocket](#when-to-use-websocket)
3. [Quick Start](#quick-start)
4. [Advanced Configuration](#advanced-configuration)
5. [Automatic Reconnection](#automatic-reconnection)
6. [Heartbeat & Ping-Pong](#heartbeat--ping-pong)
7. [Message Queuing](#message-queuing)
8. [Connection Pooling](#connection-pooling)
9. [Message Acknowledgment](#message-acknowledgment)
10. [Binary Messages](#binary-messages)
11. [Compression Support](#compression-support)
12. [Authentication & Headers](#authentication--headers)
13. [Error Handling](#error-handling)
14. [Best Practices](#best-practices)
15. [Complete Examples](#complete-examples)

---

## Overview

The `WebSocketClientHelper` provides an enterprise-grade reactive API for WebSocket communication with production-ready features for reliability, scalability, and observability.

**Enterprise Features**:
- ✅ **Automatic reconnection** with exponential backoff
- ✅ **Heartbeat/ping-pong** for connection health monitoring
- ✅ **Message queuing** when connection is down
- ✅ **Connection pooling** for resource efficiency
- ✅ **Message acknowledgment** system for reliability
- ✅ **Binary message support** for efficient data transfer
- ✅ **Compression support** (permessage-deflate)
- ✅ **Authentication/authorization** headers
- ✅ **Reactive programming** with `Mono<T>` and `Flux<T>`
- ✅ **Thread-safe** operations
- ✅ **Production-ready** error handling

---

## When to Use WebSocket

### ✅ Use WebSocket When:

- You need **real-time bidirectional** communication
- You're building **chat applications**
- You need **live notifications** or updates
- You're implementing **real-time dashboards**
- You need **streaming data** (stock prices, sensor data, IoT)
- You want to **reduce HTTP polling** overhead
- You need **low-latency** communication
- You're building **collaborative applications** (Google Docs-style)

### ❌ Consider Alternatives When:

- Simple request/response is sufficient → **Use REST Client**
- You only need server-to-client streaming → **Use Server-Sent Events (SSE)**
- You need RPC-style communication → **Use gRPC Client**
- You need SOAP/XML services → **Use SOAP Client**

---

## Quick Start

### Basic Connection

```java
import org.fireflyframework.client.websocket.WebSocketClientHelper;

// Create WebSocket helper
WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/notifications");

// Receive messages
wsHelper.receiveMessages(message -> {
    System.out.println("Received: " + message);
}).subscribe();
```

### With Secure Connection (WSS)

```java
// Connect to secure WebSocket
WebSocketClientHelper wsHelper = new WebSocketClientHelper("wss://secure.example.com/ws");

wsHelper.receiveMessages(message -> {
    System.out.println("Secure message: " + message);
}).subscribe();
```

### Production-Ready Setup

```java
import org.fireflyframework.client.websocket.WebSocketClientHelper.WebSocketConfig;
import java.time.Duration;

// Configure for production
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .maxReconnectAttempts(Integer.MAX_VALUE)  // Infinite reconnection
    .reconnectBackoff(Duration.ofSeconds(5))
    .enableHeartbeat(true)
    .heartbeatInterval(Duration.ofSeconds(30))
    .enableMessageQueue(true)
    .maxQueueSize(1000)
    .messageTimeout(Duration.ofSeconds(30))
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper(
    "wss://api.example.com/ws",
    config
);

// Connect with automatic reconnection
wsHelper.receiveMessagesWithReconnection(message -> {
    System.out.println("Message: " + message);
}).subscribe();
```

---

## Advanced Configuration

### Configuration Options

The `WebSocketConfig` class provides comprehensive configuration options:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `handshakeTimeout` | `Duration` | 10s | WebSocket handshake timeout |
| `enableReconnection` | `boolean` | `false` | Enable automatic reconnection |
| `maxReconnectAttempts` | `int` | 5 | Maximum reconnection attempts |
| `reconnectBackoff` | `Duration` | 2s | Initial backoff duration for reconnection |
| `enableHeartbeat` | `boolean` | `false` | Enable heartbeat/ping-pong |
| `heartbeatInterval` | `Duration` | 30s | Interval between heartbeat pings |
| `enableMessageQueue` | `boolean` | `false` | Enable message queuing when offline |
| `maxQueueSize` | `int` | 1000 | Maximum queued messages |
| `enableCompression` | `boolean` | `false` | Enable permessage-deflate compression |
| `enableBinaryMessages` | `boolean` | `false` | Enable binary message support |
| `messageTimeout` | `Duration` | 30s | Timeout for sending messages |
| `headers` | `Map<String, String>` | Empty | Custom headers for authentication |

### Configuration Examples

#### Minimal Configuration
```java
WebSocketConfig config = WebSocketConfig.builder()
    .handshakeTimeout(Duration.ofSeconds(15))
    .build();
```

#### High-Availability Configuration
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .maxReconnectAttempts(Integer.MAX_VALUE)  // Never give up
    .reconnectBackoff(Duration.ofSeconds(2))
    .enableHeartbeat(true)
    .heartbeatInterval(Duration.ofSeconds(30))
    .enableMessageQueue(true)
    .maxQueueSize(5000)
    .build();
```

#### Performance-Optimized Configuration
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableCompression(true)  // Reduce bandwidth
    .enableBinaryMessages(true)  // Efficient binary transfer
    .messageTimeout(Duration.ofSeconds(10))
    .handshakeTimeout(Duration.ofSeconds(5))
    .build();
```

#### Secure Configuration with Authentication
```java
WebSocketConfig config = WebSocketConfig.builder()
    .header("Authorization", "Bearer " + jwtToken)
    .header("X-API-Key", apiKey)
    .header("X-Client-Version", "1.0.0")
    .enableReconnection(true)
    .build();
```

---

## Automatic Reconnection

### Enable Reconnection

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .maxReconnectAttempts(10)
    .reconnectBackoff(Duration.ofSeconds(2))
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);

// Connect with automatic reconnection
wsHelper.connectWithReconnection(session -> {
    return session.receive()
        .map(WebSocketMessage::getPayloadAsText)
        .doOnNext(msg -> System.out.println("Message: " + msg))
        .then();
}).subscribe();
```

### Infinite Reconnection

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .maxReconnectAttempts(Integer.MAX_VALUE)  // Never stop trying
    .reconnectBackoff(Duration.ofSeconds(5))
    .build();
```

### Exponential Backoff

The reconnection uses exponential backoff automatically:
- Attempt 1: 2 seconds
- Attempt 2: 4 seconds
- Attempt 3: 8 seconds
- Attempt 4: 16 seconds
- Attempt 5: 32 seconds
- Maximum: 5 minutes (capped)

### Monitor Reconnection Attempts

```java
int attempts = wsHelper.getReconnectAttempts();
System.out.println("Reconnection attempts: " + attempts);
```

---

## Heartbeat & Ping-Pong

### Enable Heartbeat

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableHeartbeat(true)
    .heartbeatInterval(Duration.ofSeconds(30))
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);
```

### How It Works

1. **Automatic Ping**: Sends WebSocket PING frames every `heartbeatInterval`
2. **Server Response**: Server responds with PONG frames
3. **Connection Health**: Detects dead connections quickly
4. **Firewall Friendly**: Keeps connection alive through firewalls/proxies

### Monitor Heartbeat

```java
long lastHeartbeat = wsHelper.getLastHeartbeatTime();
long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat;

if (timeSinceLastHeartbeat > 60000) {
    System.out.println("Warning: No heartbeat in last 60 seconds");
}
```

### Best Practices

✅ **GOOD**: Use heartbeat for long-lived connections
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableHeartbeat(true)
    .heartbeatInterval(Duration.ofSeconds(30))  // Every 30 seconds
    .build();
```

❌ **BAD**: Too frequent heartbeats waste bandwidth
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableHeartbeat(true)
    .heartbeatInterval(Duration.ofSeconds(1))  // Too frequent!
    .build();
```

---

## Message Queuing

### Enable Message Queue

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableMessageQueue(true)
    .maxQueueSize(1000)
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);
```

### How It Works

1. **Offline Buffering**: Messages are queued when connection is down
2. **Automatic Replay**: Queued messages are sent when connection is restored
3. **FIFO Order**: Messages are sent in the order they were queued
4. **Queue Limit**: Prevents memory overflow with `maxQueueSize`

### Send Message with Queuing

```java
// Message will be queued if not connected
wsHelper.sendMessageWithAck("Important message", messageId -> {
    System.out.println("Message delivered: " + messageId);
}).subscribe(
    messageId -> System.out.println("Sent or queued: " + messageId),
    error -> System.err.println("Failed: " + error.getMessage())
);
```

### Monitor Queue

```java
int queueSize = wsHelper.getQueueSize();
System.out.println("Messages in queue: " + queueSize);

// Clear queue if needed
wsHelper.clearQueue();
```

### Queue Full Handling

```java
wsHelper.sendMessageWithAck("Message", null)
    .subscribe(
        messageId -> System.out.println("Sent: " + messageId),
        error -> {
            if (error instanceof WebSocketClientHelper.WebSocketQueueFullException) {
                System.err.println("Queue is full! Message dropped.");
            }
        }
    );
```

---

## Binary Messages

### Enable Binary Support

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableBinaryMessages(true)
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/binary", config);
```

### Send Binary Data

```java
// Send image data
byte[] imageData = Files.readAllBytes(Paths.get("image.png"));

wsHelper.sendBinaryMessage(imageData)
    .subscribe(
        unused -> System.out.println("Binary data sent successfully"),
        error -> System.err.println("Failed to send: " + error.getMessage())
    );
```

### Receive Binary Data

```java
wsHelper.receiveBinaryMessages(data -> {
    System.out.println("Received binary data: " + data.length + " bytes");

    // Save to file
    try {
        Files.write(Paths.get("received.bin"), data);
    } catch (IOException e) {
        e.printStackTrace();
    }
}).subscribe();
```

### Use Cases for Binary Messages

1. **File Transfer**: Send/receive files efficiently
2. **Image Streaming**: Real-time image/video streaming
3. **Protocol Buffers**: Efficient serialization with Protobuf
4. **Audio Streaming**: Real-time audio communication
5. **IoT Data**: Sensor data in binary format

### Example: Image Streaming

```java
@Service
public class ImageStreamingService {

    private final WebSocketClientHelper wsHelper;

    public ImageStreamingService() {
        WebSocketConfig config = WebSocketConfig.builder()
            .enableBinaryMessages(true)
            .enableCompression(true)  // Compress images
            .build();

        this.wsHelper = new WebSocketClientHelper("ws://localhost:8080/images", config);
    }

    public Mono<Void> streamImage(BufferedImage image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            return wsHelper.sendBinaryMessage(imageBytes);
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    public void receiveImages(Consumer<BufferedImage> imageConsumer) {
        wsHelper.receiveBinaryMessages(data -> {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                BufferedImage image = ImageIO.read(bais);
                imageConsumer.accept(image);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).subscribe();
    }
}
```

---

## Compression Support

### Enable Compression

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableCompression(true)  // Enable permessage-deflate
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);
```

### How It Works

1. **Negotiation**: Client and server negotiate compression during handshake
2. **Automatic**: Messages are automatically compressed/decompressed
3. **Transparent**: No code changes needed for compressed messages
4. **Bandwidth Savings**: Typically 60-80% reduction for text messages

### When to Use Compression

✅ **Use compression when**:
- Sending large text messages (JSON, XML)
- Bandwidth is limited (mobile networks)
- Sending repetitive data
- Cost optimization (cloud egress fees)

❌ **Don't use compression when**:
- Messages are already compressed (images, videos)
- Messages are very small (<100 bytes)
- CPU is constrained
- Low-latency is critical

### Example: Large JSON Messages

```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableCompression(true)
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/data", config);

// Large JSON payload (will be compressed automatically)
String largeJson = objectMapper.writeValueAsString(largeDataObject);

wsHelper.sendMessageWithAck(largeJson, null)
    .subscribe(
        messageId -> System.out.println("Large JSON sent (compressed): " + messageId)
    );
```

---

## Authentication & Headers

### Basic Authentication

```java
String credentials = Base64.getEncoder().encodeToString("user:password".getBytes());

WebSocketConfig config = WebSocketConfig.builder()
    .header("Authorization", "Basic " + credentials)
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("wss://secure.example.com/ws", config);
```

### Bearer Token (JWT)

```java
String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";

WebSocketConfig config = WebSocketConfig.builder()
    .header("Authorization", "Bearer " + jwtToken)
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("wss://api.example.com/ws", config);
```

### API Key Authentication

```java
WebSocketConfig config = WebSocketConfig.builder()
    .header("X-API-Key", "your-api-key-here")
    .header("X-Client-ID", "client-123")
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("wss://api.example.com/ws", config);
```

### Multiple Headers

```java
WebSocketConfig config = WebSocketConfig.builder()
    .header("Authorization", "Bearer " + jwtToken)
    .header("X-API-Key", apiKey)
    .header("X-Client-Version", "1.0.0")
    .header("X-Request-ID", UUID.randomUUID().toString())
    .header("User-Agent", "MyApp/1.0")
    .build();
```

### Dynamic Token Refresh

```java
@Service
public class SecureWebSocketService {

    private final TokenProvider tokenProvider;
    private WebSocketClientHelper wsHelper;

    public void connect() {
        // Get fresh token
        String token = tokenProvider.getAccessToken();

        WebSocketConfig config = WebSocketConfig.builder()
            .header("Authorization", "Bearer " + token)
            .enableReconnection(true)
            .build();

        wsHelper = new WebSocketClientHelper("wss://api.example.com/ws", config);

        wsHelper.connectWithReconnection(session -> {
            // Connection logic
            return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(this::processMessage)
                .then();
        }).subscribe();
    }

    public void reconnectWithNewToken() {
        // Disconnect old connection
        wsHelper.disconnect();

        // Reconnect with new token
        connect();
    }
}
```

---

## Error Handling

### Handle Connection Errors

```java
wsHelper.connectWithReconnection(session -> {
    return session.receive()
        .map(WebSocketMessage::getPayloadAsText)
        .doOnNext(msg -> System.out.println("Message: " + msg))
        .then();
})
.doOnError(error -> {
    if (error instanceof WebSocketClientHelper.WebSocketReconnectionException) {
        System.err.println("Reconnection failed: " + error.getMessage());
    } else if (error instanceof TimeoutException) {
        System.err.println("Connection timeout");
    } else {
        System.err.println("WebSocket error: " + error.getMessage());
    }
})
.subscribe();
```

### Retry with Custom Logic

```java
import reactor.util.retry.Retry;

wsHelper.connect(session -> {
    return session.receive()
        .map(WebSocketMessage::getPayloadAsText)
        .doOnNext(msg -> System.out.println("Message: " + msg))
        .then();
})
.retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
    .maxBackoff(Duration.ofMinutes(1))
    .filter(throwable -> {
        // Only retry on specific errors
        return !(throwable instanceof IllegalArgumentException);
    })
    .doBeforeRetry(signal -> {
        System.out.println("Retrying... Attempt: " + signal.totalRetries());
    })
)
.subscribe();
```

### Timeout Handling

```java
wsHelper.connect(session -> {
    return session.receive()
        .map(WebSocketMessage::getPayloadAsText)
        .doOnNext(msg -> System.out.println("Message: " + msg))
        .then();
})
.timeout(Duration.ofMinutes(5))
.doOnError(TimeoutException.class, error -> {
    System.err.println("Connection timed out after 5 minutes");
    // Reconnect or cleanup
})
.subscribe();
```

### Graceful Shutdown

```java
@Service
public class WebSocketService {

    private final WebSocketClientHelper wsHelper;

    public WebSocketService() {
        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .build();

        this.wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("Shutting down WebSocket connection...");

        // Disconnect gracefully
        wsHelper.disconnect();

        // Clear any queued messages
        wsHelper.clearQueue();

        System.out.println("WebSocket connection closed");
    }
}
```


---

## Best Practices

### 1. Always Use Reconnection in Production

❌ **BAD**: No reconnection
```java
WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws");
// Connection lost = service down
```

✅ **GOOD**: Automatic reconnection
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .maxReconnectAttempts(Integer.MAX_VALUE)
    .reconnectBackoff(Duration.ofSeconds(5))
    .build();

WebSocketClientHelper wsHelper = new WebSocketClientHelper("ws://localhost:8080/ws", config);
```

### 2. Enable Heartbeat for Long-Lived Connections

❌ **BAD**: No heartbeat (connection may die silently)
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .build();
```

✅ **GOOD**: Heartbeat enabled
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableReconnection(true)
    .enableHeartbeat(true)
    .heartbeatInterval(Duration.ofSeconds(30))
    .build();
```

### 3. Use Message Queuing for Critical Messages

❌ **BAD**: Messages lost when offline
```java
wsHelper.sendMessages(Flux.just("Important message")).subscribe();
// Message lost if connection is down
```

✅ **GOOD**: Messages queued when offline
```java
WebSocketConfig config = WebSocketConfig.builder()
    .enableMessageQueue(true)
    .maxQueueSize(1000)
    .build();

wsHelper.sendMessageWithAck("Important message", messageId -> {
    System.out.println("Delivered: " + messageId);
}).subscribe();
```

### 4. Use Connection Pooling for Shared Connections

❌ **BAD**: Creating new connections everywhere
```java
public void sendNotification(String message) {
    WebSocketClientHelper ws = new WebSocketClientHelper(url);  // New connection!
    ws.sendMessages(Mono.just(message)).subscribe();
}
```

✅ **GOOD**: Reuse pooled connection
```java
@Service
public class NotificationService {

    public void sendNotification(String message) {
        WebSocketClientHelper ws = WebSocketClientHelper.getPooledConnection(url);
        ws.sendMessageWithAck(message, null).subscribe();
    }
}
```

### 5. Handle Backpressure

❌ **BAD**: No backpressure handling
```java
wsHelper.receiveMessages(message -> {
    slowDatabaseOperation(message);  // Blocks!
}).subscribe();
```

✅ **GOOD**: Handle backpressure
```java
wsHelper.receiveMessagesWithReconnection(message -> message)
    .onBackpressureBuffer(1000)  // Buffer up to 1000 messages
    .flatMap(msg -> processAsync(msg), 10)  // Process 10 concurrently
    .subscribe();
```

### 6. Use Structured Messages (JSON)

❌ **BAD**: String parsing
```java
wsHelper.sendMessages(Mono.just("USER:john|MESSAGE:Hello")).subscribe();
```

✅ **GOOD**: JSON messages
```java
ChatMessage msg = new ChatMessage("john", "Hello");
String json = objectMapper.writeValueAsString(msg);
wsHelper.sendMessageWithAck(json, null).subscribe();
```

### 7. Implement Graceful Shutdown

❌ **BAD**: Abrupt disconnection
```java
// Application shuts down, connection drops
```

✅ **GOOD**: Graceful shutdown
```java
@PreDestroy
public void shutdown() {
    wsHelper.disconnect();
    wsHelper.clearQueue();
    WebSocketClientHelper.clearPool();
}
```

### 8. Monitor Connection Health

✅ **GOOD**: Monitor metrics
```java
@Scheduled(fixedRate = 60000)  // Every minute
public void monitorWebSocket() {
    boolean connected = wsHelper.isConnected();
    int queueSize = wsHelper.getQueueSize();
    int pendingAcks = wsHelper.getPendingAckCount();
    int reconnectAttempts = wsHelper.getReconnectAttempts();

    log.info("WebSocket status: connected={}, queue={}, pending={}, attempts={}",
        connected, queueSize, pendingAcks, reconnectAttempts);

    // Alert if unhealthy
    if (!connected && reconnectAttempts > 5) {
        alertOps("WebSocket connection unstable");
    }
}
```

---

## Complete Examples

### Example 1: Real-Time Chat Application

```java
import org.fireflyframework.client.websocket.WebSocketClientHelper;
import org.fireflyframework.client.websocket.WebSocketClientHelper.WebSocketConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Sinks;

@Service
public class ChatService {

    private final WebSocketClientHelper wsHelper;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<ChatMessage> messageSink;

    public ChatService() {
        this.objectMapper = new ObjectMapper();
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();

        // Configure WebSocket with all enterprise features
        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .maxReconnectAttempts(Integer.MAX_VALUE)
            .reconnectBackoff(Duration.ofSeconds(5))
            .enableHeartbeat(true)
            .heartbeatInterval(Duration.ofSeconds(30))
            .enableMessageQueue(true)
            .maxQueueSize(500)
            .header("Authorization", "Bearer " + getAuthToken())
            .build();

        this.wsHelper = new WebSocketClientHelper("wss://chat.example.com/ws", config);

        // Start receiving messages
        startReceiving();
    }

    private void startReceiving() {
        wsHelper.receiveMessagesWithReconnection(json -> {
            try {
                ChatMessage message = objectMapper.readValue(json, ChatMessage.class);
                messageSink.tryEmitNext(message);
            } catch (Exception e) {
                log.error("Failed to parse message: {}", json, e);
            }
        }).subscribe();
    }

    public Mono<String> sendMessage(String userId, String text) {
        ChatMessage message = new ChatMessage(userId, text, Instant.now());

        try {
            String json = objectMapper.writeValueAsString(message);

            return wsHelper.sendMessageWithAck(json, messageId -> {
                log.info("Message {} delivered", messageId);
            });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    public Flux<ChatMessage> getMessageStream() {
        return messageSink.asFlux();
    }

    @PreDestroy
    public void shutdown() {
        wsHelper.disconnect();
        messageSink.tryEmitComplete();
    }

    private String getAuthToken() {
        // Get JWT token
        return "your-jwt-token";
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class ChatMessage {
    private String userId;
    private String text;
    private Instant timestamp;
}
```

### Example 2: Stock Price Monitor with Binary Data

```java
@Service
public class StockPriceMonitor {

    private final WebSocketClientHelper wsHelper;
    private final Sinks.Many<StockPrice> priceSink;

    public StockPriceMonitor() {
        this.priceSink = Sinks.many().multicast().onBackpressureBuffer();

        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .enableHeartbeat(true)
            .enableBinaryMessages(true)  // Efficient binary protocol
            .enableCompression(true)
            .build();

        this.wsHelper = new WebSocketClientHelper("wss://stream.example.com/stocks", config);

        startMonitoring();
    }

    private void startMonitoring() {
        wsHelper.receiveBinaryMessages(data -> {
            try {
                // Deserialize binary data (e.g., Protocol Buffers)
                StockPrice price = StockPrice.parseFrom(data);
                priceSink.tryEmitNext(price);
            } catch (Exception e) {
                log.error("Failed to parse stock price", e);
            }
        }).subscribe();
    }

    public Mono<Void> subscribe(String symbol) {
        SubscribeRequest request = SubscribeRequest.newBuilder()
            .setAction("subscribe")
            .setSymbol(symbol)
            .build();

        return wsHelper.sendBinaryMessage(request.toByteArray());
    }

    public Flux<StockPrice> getPriceStream(String symbol) {
        return priceSink.asFlux()
            .filter(price -> symbol.equals(price.getSymbol()));
    }
}
```

### Example 3: IoT Sensor Data Collection

```java
@Service
public class IoTDataCollector {

    private final WebSocketClientHelper wsHelper;
    private final Map<String, SensorData> latestData;

    public IoTDataCollector() {
        this.latestData = new ConcurrentHashMap<>();

        WebSocketConfig config = WebSocketConfig.builder()
            .enableReconnection(true)
            .maxReconnectAttempts(Integer.MAX_VALUE)
            .reconnectBackoff(Duration.ofSeconds(10))
            .enableHeartbeat(true)
            .heartbeatInterval(Duration.ofSeconds(60))
            .enableMessageQueue(true)
            .maxQueueSize(10000)  // Large queue for IoT data
            .enableCompression(true)
            .build();

        this.wsHelper = new WebSocketClientHelper("wss://iot.example.com/sensors", config);

        startCollecting();
    }

    private void startCollecting() {
        wsHelper.receiveMessagesWithReconnection(json -> {
            try {
                SensorData data = objectMapper.readValue(json, SensorData.class);
                latestData.put(data.getSensorId(), data);

                // Process data
                processData(data);
            } catch (Exception e) {
                log.error("Failed to process sensor data", e);
            }
        }).subscribe();
    }

    public Mono<String> sendCommand(String sensorId, String command) {
        CommandMessage msg = new CommandMessage(sensorId, command);
        String json = objectMapper.writeValueAsString(msg);

        return wsHelper.sendMessageWithAck(json, messageId -> {
            log.info("Command {} sent to sensor {}", command, sensorId);
        });
    }

    public SensorData getLatestData(String sensorId) {
        return latestData.get(sensorId);
    }

    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    public void monitorHealth() {
        log.info("WebSocket health: connected={}, queue={}, reconnects={}",
            wsHelper.isConnected(),
            wsHelper.getQueueSize(),
            wsHelper.getReconnectAttempts()
        );
    }
}
```

---

## Summary

The `WebSocketClientHelper` provides enterprise-grade WebSocket communication with:

✅ **Reliability**: Automatic reconnection, message queuing, acknowledgments
✅ **Performance**: Binary messages, compression, connection pooling
✅ **Observability**: Connection status, queue metrics, heartbeat monitoring
✅ **Security**: Authentication headers, secure WSS connections
✅ **Developer Experience**: Reactive API, type-safe configuration, comprehensive error handling

For more information, see:
- [REST Client Guide](REST_CLIENT.md)
- [gRPC Client Guide](GRPC_CLIENT.md)
- [GraphQL Client Guide](GRAPHQL_CLIENT.md)
- [OAuth2 Helper Guide](OAUTH2_HELPER.md)
- [Multipart Upload Helper Guide](MULTIPART_HELPER.md)


