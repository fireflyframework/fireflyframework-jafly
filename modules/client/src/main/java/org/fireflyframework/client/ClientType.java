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

package org.fireflyframework.client;

/**
 * Enumeration of supported service client types.
 * 
 * <p>This enum identifies the underlying communication protocol and implementation
 * used by a ServiceClient instance, allowing for type-specific behavior and optimizations.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public enum ClientType {
    
    /**
     * REST/HTTP-based service client using WebClient.
     *
     * <p>Supports standard HTTP methods (GET, POST, PUT, DELETE, PATCH)
     * with JSON/XML serialization, form data, and file uploads.
     */
    REST("REST"),

    /**
     * gRPC-based service client using Protocol Buffers.
     *
     * <p>Supports unary and streaming gRPC calls with efficient binary
     * serialization and built-in load balancing.
     */
    GRPC("gRPC"),

    /**
     * SOAP/Web Services client using JAX-WS and Apache CXF.
     *
     * <p>Supports SOAP 1.1 and 1.2 protocols with WSDL-based service discovery,
     * WS-Security, MTOM attachments, and automatic XML marshalling/unmarshalling.
     * Provides a modern reactive API over traditional SOAP services.
     */
    SOAP("SOAP");
    

    private final String displayName;

    ClientType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable display name for this client type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this client type supports HTTP-style operations.
     *
     * @return true for REST and SOAP clients, false for others
     */
    public boolean isHttpBased() {
        return this == REST || this == SOAP;
    }

    /**
     * Returns true if this client type supports streaming operations.
     *
     * @return true for gRPC and REST clients
     */
    public boolean supportsStreaming() {
        return this == GRPC || this == REST;
    }

    /**
     * Returns true if this client type uses XML-based protocols.
     *
     * @return true for SOAP clients, false for others
     */
    public boolean isXmlBased() {
        return this == SOAP;
    }

    /**
     * Returns true if this client type supports WSDL-based service discovery.
     *
     * @return true for SOAP clients, false for others
     */
    public boolean supportsWsdl() {
        return this == SOAP;
    }

    /**
     * Returns true if this client type requires custom SDK integration.
     *
     * @return always false since SDK clients are no longer supported
     */
    public boolean requiresSdkIntegration() {
        return false;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
