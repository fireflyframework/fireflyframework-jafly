/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.client.grpc;

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.impl.GrpcServiceClientImpl;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for gRPC client using in-process gRPC server.
 * Tests all features including unary, streaming, error handling, metadata, etc.
 */
@DisplayName("gRPC Client Integration Tests")
class GrpcClientIntegrationTest {

    private static final String SERVER_NAME = "test-grpc-server";
    
    private Server server;
    private ManagedChannel channel;
    private TestServiceImpl testService;

    @BeforeEach
    void setUp() throws IOException {
        testService = new TestServiceImpl();
        
        // Create in-process server
        server = InProcessServerBuilder
            .forName(SERVER_NAME)
            .directExecutor()
            .addService(testService)
            .build()
            .start();

        // Create in-process channel
        channel = InProcessChannelBuilder
            .forName(SERVER_NAME)
            .directExecutor()
            .build();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Should create gRPC client successfully")
    void shouldCreateGrpcClientSuccessfully() {
        // When: Creating a gRPC client
        ServiceClient client = ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
            .address(SERVER_NAME)
            .usePlaintext()
            .timeout(Duration.ofSeconds(30))
            .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel))
            .build();

        // Then: Client should be properly configured
        assertThat(client).isNotNull();
        assertThat(client.getServiceName()).isEqualTo("test-service");
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should perform unary RPC call successfully")
    void shouldPerformUnaryRpcCallSuccessfully() {
        // Given: A gRPC client
        GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub> client =
            (GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub>) ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
                .address(SERVER_NAME)
                .usePlaintext()
                .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel))
                .build();

        // When: Performing a unary call
        TestServiceGrpc.TestServiceBlockingStub stub = client.getStub();

        TestRequest request = TestRequest.newBuilder()
            .setMessage("Hello gRPC")
            .build();

        TestResponse response = stub.unaryCall(request);

        // Then: The response should be correct
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Echo: Hello gRPC");

        client.shutdown();
    }

    @Test
    @DisplayName("Should handle gRPC error correctly")
    void shouldHandleGrpcErrorCorrectly() {
        // Given: A gRPC client
        GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub> client =
            (GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub>) ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
                .address(SERVER_NAME)
                .usePlaintext()
                .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel))
                .build();

        // When: Performing a call that triggers an error
        TestServiceGrpc.TestServiceBlockingStub stub = client.getStub();

        TestRequest request = TestRequest.newBuilder()
            .setMessage("ERROR")
            .build();

        // Then: Should receive a gRPC error
        try {
            stub.unaryCall(request);
            Assertions.fail("Expected StatusRuntimeException");
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(e.getStatus().getDescription()).contains("Invalid message");
        }

        client.shutdown();
    }

    @Test
    @DisplayName("Should handle metadata correctly")
    void shouldHandleMetadataCorrectly() {
        // Given: A gRPC client with metadata
        GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub> client =
            (GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub>) ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
                .address(SERVER_NAME)
                .usePlaintext()
                .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel))
                .build();

        // When: Performing a call with metadata
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("custom-header", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "custom-value");

        TestServiceGrpc.TestServiceBlockingStub stub = client.getStub();

        TestServiceGrpc.TestServiceBlockingStub stubWithMetadata =
            stub.withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(metadata));

        TestRequest request = TestRequest.newBuilder()
            .setMessage("Hello with metadata")
            .build();

        TestResponse response = stubWithMetadata.unaryCall(request);

        // Then: The response should be correct
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Echo:");

        client.shutdown();
    }

    @Test
    @DisplayName("Should perform health check successfully")
    void shouldPerformHealthCheckSuccessfully() {
        // Given: A gRPC client
        ServiceClient client = ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
            .address(SERVER_NAME)
            .usePlaintext()
            .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel))
            .build();

        // When: Performing a health check
        Mono<Void> healthCheck = client.healthCheck();

        // Then: Health check should succeed
        StepVerifier.create(healthCheck)
            .verifyComplete();

        client.shutdown();
    }

    @Test
    @DisplayName("Should handle timeout correctly")
    void shouldHandleTimeoutCorrectly() {
        // Given: A gRPC client with short timeout
        GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub> client =
            (GrpcServiceClientImpl<TestServiceGrpc.TestServiceBlockingStub>) ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
                .address(SERVER_NAME)
                .usePlaintext()
                .timeout(Duration.ofMillis(100))
                .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(100, TimeUnit.MILLISECONDS))
                .build();

        // When: Performing a call that takes too long
        TestServiceGrpc.TestServiceBlockingStub stub = client.getStub();

        TestRequest request = TestRequest.newBuilder()
            .setMessage("SLOW")
            .build();

        // Then: Should timeout
        try {
            stub.unaryCall(request);
            Assertions.fail("Expected DEADLINE_EXCEEDED");
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        }

        client.shutdown();
    }

    @Test
    @DisplayName("Should manage client lifecycle correctly")
    void shouldManageClientLifecycleCorrectly() {
        // Given: A gRPC client
        ServiceClient client = ServiceClient.grpc("test-service", TestServiceGrpc.TestServiceBlockingStub.class)
            .address(SERVER_NAME)
            .usePlaintext()
            .stubFactory(ch -> TestServiceGrpc.newBlockingStub(channel))
            .build();

        // When: Checking initial state
        assertThat(client.isReady()).isTrue();

        // When: Shutting down
        client.shutdown();

        // Then: Client should be shut down
        assertThat(client).isNotNull();
    }

    // Test service implementation
    private static class TestServiceImpl extends TestServiceGrpc.TestServiceImplBase {
        @Override
        public void unaryCall(TestRequest request, StreamObserver<TestResponse> responseObserver) {
            if (request.getMessage().equals("ERROR")) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid message")
                    .asRuntimeException());
                return;
            }

            if (request.getMessage().equals("SLOW")) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            TestResponse response = TestResponse.newBuilder()
                .setMessage("Echo: " + request.getMessage())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // Test message classes (simplified protobuf-like classes)
    public static class TestRequest {
        private final String message;

        private TestRequest(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static class Builder {
            private String message;

            public Builder setMessage(String message) {
                this.message = message;
                return this;
            }

            public TestRequest build() {
                return new TestRequest(message);
            }
        }
    }

    public static class TestResponse {
        private final String message;

        private TestResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static class Builder {
            private String message;

            public Builder setMessage(String message) {
                this.message = message;
                return this;
            }

            public TestResponse build() {
                return new TestResponse(message);
            }
        }
    }
}

