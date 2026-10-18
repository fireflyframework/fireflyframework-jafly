# gRPC Client Guide

Complete guide for using the gRPC client to communicate with gRPC services.

## Table of Contents

- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Creating a gRPC Client](#creating-a-grpc-client)
- [Configuration Options](#configuration-options)
- [Making Calls](#making-calls)
- [Streaming Operations](#streaming-operations)
- [Advanced Features](#advanced-features)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

```java
import org.fireflyframework.client.GrpcClient;
import org.fireflyframework.client.ServiceClient;
import com.example.grpc.UserServiceGrpc;
import com.example.grpc.UserServiceGrpc.UserServiceStub;

@Service
public class UserService {
    
    private final GrpcClient<UserServiceStub> grpcClient;
    
    public UserService() {
        this.grpcClient = ServiceClient.grpc("user-service", UserServiceStub.class)
            .address("localhost:9090")
            .stubFactory(channel -> UserServiceGrpc.newStub(channel))
            .build();
    }
    
    public Mono<UserResponse> getUser(String id) {
        UserRequest request = UserRequest.newBuilder()
            .setId(id)
            .build();
            
        return grpcClient.unary(stub -> stub.getUser(request));
    }
}
```

---

## Prerequisites

### 1. Generate gRPC Stubs

You need to generate Java classes from your `.proto` files. Add to your `pom.xml`:

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.21.7:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.51.0:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2. Example .proto File

```protobuf
syntax = "proto3";

package com.example.grpc;

option java_multiple_files = true;
option java_package = "com.example.grpc";

service UserService {
  rpc GetUser (UserRequest) returns (UserResponse);
  rpc StreamUsers (StreamRequest) returns (stream UserResponse);
}

message UserRequest {
  string id = 1;
}

message UserResponse {
  string id = 1;
  string name = 2;
  string email = 3;
}
```

This generates:
- `UserServiceGrpc` - Service class with stub factories
- `UserServiceStub` - Async stub for non-blocking calls
- `UserServiceBlockingStub` - Blocking stub for synchronous calls
- `UserRequest`, `UserResponse` - Message classes

---

## Creating a gRPC Client

### Basic Configuration

```java
GrpcClient<UserServiceStub> client = ServiceClient.grpc("user-service", UserServiceStub.class)
    .address("localhost:9090")                                    // Required
    .stubFactory(channel -> UserServiceGrpc.newStub(channel))    // Required
    .build();
```

### Full Configuration

```java
GrpcClient<UserServiceStub> client = ServiceClient.grpc("user-service", UserServiceStub.class)
    .address("localhost:9090")                                    // Required: host:port
    .stubFactory(channel -> UserServiceGrpc.newStub(channel))    // Required: stub factory
    .timeout(Duration.ofSeconds(30))                             // Call timeout
    .usePlaintext()                                              // Disable TLS (dev only)
    .build();
```

### Production Configuration (with TLS)

```java
GrpcClient<PaymentServiceStub> client = ServiceClient.grpc("payment-service", PaymentServiceStub.class)
    .address("payment-service.prod.example.com:9090")
    .stubFactory(channel -> PaymentServiceGrpc.newStub(channel))
    .useTransportSecurity()                                      // Enable TLS
    .timeout(Duration.ofSeconds(45))
    .build();
```

---

## Configuration Options

### Required Options

| Method | Description | Example |
|--------|-------------|---------|
| `address(String)` | gRPC service address (host:port) | `.address("localhost:9090")` |
| `stubFactory(Function)` | Factory to create stub from channel | `.stubFactory(ch -> MyServiceGrpc.newStub(ch))` |

### Optional Options

| Method | Description | Default | Example |
|--------|-------------|---------|---------|
| `timeout(Duration)` | Call timeout | 30s | `.timeout(Duration.ofSeconds(45))` |
| `usePlaintext()` | Disable TLS (dev only) | false | `.usePlaintext()` |
| `useTransportSecurity()` | Enable TLS | false | `.useTransportSecurity()` |
| `channel(ManagedChannel)` | Custom channel | Auto-created | `.channel(customChannel)` |
| `circuitBreakerManager(...)` | Custom circuit breaker | Auto-created | `.circuitBreakerManager(manager)` |

---

## Making Calls

### Unary Calls (Request → Response)

```java
public Mono<UserResponse> getUser(String userId) {
    UserRequest request = UserRequest.newBuilder()
        .setId(userId)
        .build();
    
    return grpcClient.unary(stub -> stub.getUser(request));
}
```

### Direct Stub Access (No Circuit Breaker)

```java
public UserResponse getUserSync(String userId) {
    UserRequest request = UserRequest.newBuilder()
        .setId(userId)
        .build();
    
    // Direct access - no circuit breaker protection
    UserServiceStub stub = grpcClient.getStub();
    return stub.getUser(request);
}
```

### Using execute() (Alias for unary)

```java
public Mono<UserResponse> getUser(String userId) {
    UserRequest request = UserRequest.newBuilder()
        .setId(userId)
        .build();
    
    // execute() is an alias for unary()
    return grpcClient.execute(stub -> stub.getUser(request));
}
```

---

## Streaming Operations

### Server Streaming (Request → Stream of Responses)

```java
public Flux<UserEvent> streamUserEvents(String userId) {
    StreamRequest request = StreamRequest.newBuilder()
        .setUserId(userId)
        .build();
    
    return grpcClient.serverStream(stub -> 
        stub.streamUserEvents(request)
    );
}

// Usage
streamUserEvents("user123")
    .doOnNext(event -> log.info("Received event: {}", event))
    .doOnComplete(() -> log.info("Stream completed"))
    .subscribe();
```

### Client Streaming (Stream of Requests → Response)

```java
public Mono<UploadResponse> uploadData(Flux<DataChunk> chunks) {
    return grpcClient.clientStream(
        stub -> stub.uploadData(),
        chunks
    );
}

// Usage
Flux<DataChunk> chunks = Flux.just(chunk1, chunk2, chunk3);
uploadData(chunks)
    .doOnSuccess(response -> log.info("Upload complete: {}", response))
    .subscribe();
```

### Bidirectional Streaming (Stream ↔ Stream)

```java
public Flux<ChatMessage> chat(Flux<ChatMessage> messages) {
    return grpcClient.bidiStream(
        stub -> stub.chat(),
        messages
    );
}

// Usage
Flux<ChatMessage> outgoing = Flux.interval(Duration.ofSeconds(1))
    .map(i -> ChatMessage.newBuilder()
        .setMessage("Message " + i)
        .build());

chat(outgoing)
    .doOnNext(incoming -> log.info("Received: {}", incoming.getMessage()))
    .subscribe();
```

---

## Advanced Features

### Custom Timeouts per Call

```java
// Override default timeout for specific call
public Mono<UserResponse> getUser(String userId) {
    UserRequest request = UserRequest.newBuilder()
        .setId(userId)
        .build();
    
    // Note: Timeout is set at client level, not per-call
    // For per-call timeouts, create a new client or use stub directly
    return grpcClient.unary(stub -> 
        stub.withDeadlineAfter(10, TimeUnit.SECONDS)
            .getUser(request)
    );
}
```

### Error Handling

```java
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public Mono<UserResponse> getUser(String userId) {
    UserRequest request = UserRequest.newBuilder()
        .setId(userId)
        .build();
    
    return grpcClient.unary(stub -> stub.getUser(request))
        .onErrorMap(StatusRuntimeException.class, ex -> {
            Status status = ex.getStatus();
            return switch (status.getCode()) {
                case NOT_FOUND -> new UserNotFoundException("User not found: " + userId);
                case UNAVAILABLE -> new ServiceUnavailableException("Service unavailable");
                case UNAUTHENTICATED -> new UnauthorizedException("Authentication failed");
                case DEADLINE_EXCEEDED -> new TimeoutException("Request timeout");
                default -> new ServiceException("gRPC error: " + status.getDescription());
            };
        })
        .retry(3);
}
```

### Health Checks

```java
// Check if client is ready
boolean ready = grpcClient.isReady();

// Perform health check
Mono<Void> healthCheck = grpcClient.healthCheck()
    .doOnSuccess(v -> log.info("gRPC service is healthy"))
    .doOnError(e -> log.error("gRPC service is unhealthy", e));
```

### Lifecycle Management

```java
// Get service information
String serviceName = grpcClient.getServiceName();
String address = grpcClient.getAddress();
ManagedChannel channel = grpcClient.getChannel();
ClientType type = grpcClient.getClientType();  // Returns ClientType.GRPC

// Shutdown client (closes channel)
grpcClient.shutdown();
```

### Using Blocking Stub

```java
// For blocking/synchronous calls
GrpcClient<UserServiceBlockingStub> blockingClient = 
    ServiceClient.grpc("user-service", UserServiceBlockingStub.class)
        .address("localhost:9090")
        .stubFactory(channel -> UserServiceGrpc.newBlockingStub(channel))
        .build();

// Synchronous call
UserResponse response = blockingClient.getStub().getUser(request);
```

---

## Best Practices

### 1. Use Specific Stub Types

```java
// ✅ GOOD - Type-safe with specific stub
private final GrpcClient<UserServiceStub> userClient;

// ❌ BAD - Requires casting
private final ServiceClient userClient;
```

### 2. Reuse Clients

```java
@Configuration
public class GrpcClientConfig {
    
    @Bean
    public GrpcClient<UserServiceStub> userServiceClient() {
        return ServiceClient.grpc("user-service", UserServiceStub.class)
            .address("${user.service.grpc.address}")
            .stubFactory(channel -> UserServiceGrpc.newStub(channel))
            .timeout(Duration.ofSeconds(30))
            .build();
    }
}
```

### 3. Use Async Stubs for Reactive Code

```java
// ✅ GOOD - Async stub for reactive code
GrpcClient<UserServiceStub> client = ServiceClient.grpc("user-service", UserServiceStub.class)
    .stubFactory(channel -> UserServiceGrpc.newStub(channel))
    .build();

// ❌ BAD - Blocking stub in reactive code
GrpcClient<UserServiceBlockingStub> client = ServiceClient.grpc("user-service", UserServiceBlockingStub.class)
    .stubFactory(channel -> UserServiceGrpc.newBlockingStub(channel))
    .build();
```

### 4. Handle Streaming Errors

```java
public Flux<Event> streamEvents(String userId) {
    return grpcClient.serverStream(stub -> stub.streamEvents(request))
        .doOnError(error -> log.error("Stream error for user {}", userId, error))
        .onErrorResume(error -> {
            log.warn("Resuming stream after error");
            return Flux.empty();
        });
}
```

### 5. Use Plaintext Only in Development

```java
// ✅ GOOD - Environment-specific
@Bean
public GrpcClient<UserServiceStub> userServiceClient(
        @Value("${grpc.use-plaintext:false}") boolean usePlaintext) {
    
    var builder = ServiceClient.grpc("user-service", UserServiceStub.class)
        .address("${user.service.address}")
        .stubFactory(channel -> UserServiceGrpc.newStub(channel));
    
    if (usePlaintext) {
        builder.usePlaintext();
    } else {
        builder.useTransportSecurity();
    }
    
    return builder.build();
}
```

---

## Troubleshooting

### Connection Refused

**Problem**: `UNAVAILABLE: io exception` or connection refused

**Solution**:
- Verify the address is correct (host:port)
- Ensure the gRPC service is running
- Check network connectivity
- Verify firewall rules allow gRPC port

### Deadline Exceeded

**Problem**: `DEADLINE_EXCEEDED` errors

**Solution**:
```java
// Increase timeout
GrpcClient<UserServiceStub> client = ServiceClient.grpc("user-service", UserServiceStub.class)
    .address("localhost:9090")
    .timeout(Duration.ofSeconds(60))  // Increase from default 30s
    .stubFactory(channel -> UserServiceGrpc.newStub(channel))
    .build();
```

### SSL/TLS Errors

**Problem**: SSL handshake failures

**Solution**:
```java
// For development with self-signed certs
GrpcClient<UserServiceStub> client = ServiceClient.grpc("user-service", UserServiceStub.class)
    .address("localhost:9090")
    .usePlaintext()  // Disable TLS for development
    .stubFactory(channel -> UserServiceGrpc.newStub(channel))
    .build();
```

### Stub Not Found

**Problem**: Cannot find `UserServiceGrpc` class

**Solution**:
- Ensure you've run `mvn compile` to generate stubs
- Check that `.proto` files are in `src/main/proto/`
- Verify protobuf-maven-plugin is configured correctly
- Check generated classes in `target/generated-sources/protobuf/`

### Message Too Large

**Problem**: `RESOURCE_EXHAUSTED: grpc: received message larger than max`

**Solution**:
```yaml
# In application.yml
firefly:
  service-client:
    grpc:
      max-inbound-message-size: 16777216  # 16MB (default is 4MB)
```

---

## What's Included

✅ **Unary Calls**: Single request → single response  
✅ **Server Streaming**: Single request → stream of responses  
✅ **Client Streaming**: Stream of requests → single response  
✅ **Bidirectional Streaming**: Stream ↔ stream  
✅ **Circuit Breaker**: Automatic failure detection  
✅ **Health Checks**: Service availability monitoring  
✅ **Timeouts**: Configurable call timeouts  
✅ **TLS Support**: Transport security  
✅ **Reactive**: Non-blocking Mono/Flux responses  
✅ **Direct Stub Access**: For advanced use cases  

## What's NOT Included

❌ **Stub Generation**: Use protobuf-maven-plugin  
❌ **Server Implementation**: This is client-only  
❌ **Load Balancing**: Use gRPC's built-in load balancing  
❌ **Service Discovery**: Integrate with Consul/Eureka separately  

---

**Next Steps**:

**Core Clients**:
- [REST Client Guide](REST_CLIENT.md)
- [SOAP Client Guide](SOAP_CLIENT.md)

**Helper Utilities**:
- [GraphQL Client Guide](GRAPHQL_CLIENT.md)
- [WebSocket Helper Guide](WEBSOCKET_HELPER.md)
- [OAuth2 Helper Guide](OAUTH2_HELPER.md)
- [File Upload Helper Guide](MULTIPART_HELPER.md)

**Configuration**:
- [Configuration Reference](CONFIGURATION.md)

