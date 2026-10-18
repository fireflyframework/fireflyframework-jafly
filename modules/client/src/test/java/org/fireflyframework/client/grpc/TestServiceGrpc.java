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

import io.grpc.*;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.StreamObserver;

/**
 * Test gRPC service stub for integration testing.
 * Simulates a generated gRPC stub class.
 */
public final class TestServiceGrpc {

    private static final String SERVICE_NAME = "TestService";

    private static final MethodDescriptor<GrpcClientIntegrationTest.TestRequest, GrpcClientIntegrationTest.TestResponse> METHOD_UNARY_CALL =
        MethodDescriptor.<GrpcClientIntegrationTest.TestRequest, GrpcClientIntegrationTest.TestResponse>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UnaryCall"))
            .setRequestMarshaller(new TestRequestMarshaller())
            .setResponseMarshaller(new TestResponseMarshaller())
            .build();

    private TestServiceGrpc() {
    }

    private static String generateFullMethodName(String serviceName, String methodName) {
        return serviceName + "/" + methodName;
    }

    public static TestServiceBlockingStub newBlockingStub(Channel channel) {
        return new TestServiceBlockingStub(channel);
    }

    public static TestServiceStub newStub(Channel channel) {
        return new TestServiceStub(channel);
    }

    public static abstract class TestServiceImplBase implements BindableService {
        public void unaryCall(GrpcClientIntegrationTest.TestRequest request,
                            StreamObserver<GrpcClientIntegrationTest.TestResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(METHOD_UNARY_CALL, responseObserver);
        }

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(METHOD_UNARY_CALL,
                    io.grpc.stub.ServerCalls.asyncUnaryCall(
                        new MethodHandlers<>(this, 0)))
                .build();
        }
    }

    public static final class TestServiceBlockingStub extends AbstractStub<TestServiceBlockingStub> {
        private TestServiceBlockingStub(Channel channel) {
            super(channel);
        }

        private TestServiceBlockingStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected TestServiceBlockingStub build(Channel channel, CallOptions callOptions) {
            return new TestServiceBlockingStub(channel, callOptions);
        }

        public GrpcClientIntegrationTest.TestResponse unaryCall(GrpcClientIntegrationTest.TestRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                getChannel(), METHOD_UNARY_CALL, getCallOptions(), request);
        }
    }

    public static final class TestServiceStub extends AbstractStub<TestServiceStub> {
        private TestServiceStub(Channel channel) {
            super(channel);
        }

        private TestServiceStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected TestServiceStub build(Channel channel, CallOptions callOptions) {
            return new TestServiceStub(channel, callOptions);
        }

        public void unaryCall(GrpcClientIntegrationTest.TestRequest request,
                            StreamObserver<GrpcClientIntegrationTest.TestResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                getChannel().newCall(METHOD_UNARY_CALL, getCallOptions()), request, responseObserver);
        }
    }

    private static final class MethodHandlers<Req, Resp> implements
        io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
        
        private final TestServiceImplBase serviceImpl;
        private final int methodId;

        MethodHandlers(TestServiceImplBase serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void invoke(Req request, StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case 0:
                    serviceImpl.unaryCall((GrpcClientIntegrationTest.TestRequest) request,
                        (StreamObserver<GrpcClientIntegrationTest.TestResponse>) responseObserver);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public StreamObserver<Req> invoke(StreamObserver<Resp> responseObserver) {
            throw new AssertionError();
        }
    }

    // Simple marshallers for test messages
    private static class TestRequestMarshaller implements MethodDescriptor.Marshaller<GrpcClientIntegrationTest.TestRequest> {
        @Override
        public java.io.InputStream stream(GrpcClientIntegrationTest.TestRequest value) {
            byte[] bytes = value.getMessage().getBytes();
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public GrpcClientIntegrationTest.TestRequest parse(java.io.InputStream stream) {
            try {
                byte[] bytes = stream.readAllBytes();
                String message = new String(bytes);
                return GrpcClientIntegrationTest.TestRequest.newBuilder()
                    .setMessage(message)
                    .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class TestResponseMarshaller implements MethodDescriptor.Marshaller<GrpcClientIntegrationTest.TestResponse> {
        @Override
        public java.io.InputStream stream(GrpcClientIntegrationTest.TestResponse value) {
            byte[] bytes = value.getMessage().getBytes();
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public GrpcClientIntegrationTest.TestResponse parse(java.io.InputStream stream) {
            try {
                byte[] bytes = stream.readAllBytes();
                String message = new String(bytes);
                return GrpcClientIntegrationTest.TestResponse.newBuilder()
                    .setMessage(message)
                    .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

