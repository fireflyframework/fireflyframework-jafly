/*
 * Copyright 2026 Firefly Software Foundation
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

package org.fireflyframework.agentic.bridge.exception;

import org.fireflyframework.kernel.exception.FireflyInfrastructureException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Root exception for every failure originating in the agentic bridge.
 *
 * <p>Sits under {@link FireflyInfrastructureException} so existing
 * framework-wide error handlers (e.g. RFC 7807 mappers) catch it without any
 * extra wiring.</p>
 *
 * <p>Carries an immutable {@link AgenticErrorContext} describing the agent,
 * transport, request ID, HTTP status, and any other diagnostic metadata
 * available at the point of failure.</p>
 */
public class AgenticBridgeException extends FireflyInfrastructureException {

    private final AgenticErrorContext errorContext;

    public AgenticBridgeException(String message) {
        this(message, AgenticErrorContext.empty(), null);
    }

    public AgenticBridgeException(String message, Throwable cause) {
        this(message, AgenticErrorContext.empty(), cause);
    }

    public AgenticBridgeException(String message, AgenticErrorContext errorContext) {
        this(message, errorContext, null);
    }

    public AgenticBridgeException(String message, AgenticErrorContext errorContext, Throwable cause) {
        super(enrich(message, errorContext), "AGENTIC_BRIDGE_ERROR", cause);
        this.errorContext = errorContext == null ? AgenticErrorContext.empty() : errorContext;
    }

    public AgenticErrorContext getErrorContext() {
        return errorContext;
    }

    private static String enrich(String message, AgenticErrorContext context) {
        if (context == null || context.isEmpty()) {
            return message;
        }
        StringBuilder b = new StringBuilder(message == null ? "Agentic bridge error" : message);
        context.agentName().ifPresent(v -> b.append(" | agent=").append(v));
        context.transport().ifPresent(v -> b.append(" | transport=").append(v));
        context.endpoint().ifPresent(v -> b.append(" | endpoint=").append(v));
        context.httpStatus().ifPresent(v -> b.append(" | status=").append(v));
        context.requestId().ifPresent(v -> b.append(" | requestId=").append(v));
        return b.toString();
    }

    /**
     * Immutable, builder-friendly diagnostic context for bridge failures.
     */
    public static final class AgenticErrorContext {

        private static final AgenticErrorContext EMPTY = new AgenticErrorContext(null, null, null, null, null, Map.of());

        private final String agentName;
        private final String transport;
        private final String endpoint;
        private final Integer httpStatus;
        private final String requestId;
        private final Map<String, String> attributes;

        private AgenticErrorContext(String agentName, String transport, String endpoint,
                                    Integer httpStatus, String requestId, Map<String, String> attributes) {
            this.agentName = agentName;
            this.transport = transport;
            this.endpoint = endpoint;
            this.httpStatus = httpStatus;
            this.requestId = requestId;
            this.attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }

        public static AgenticErrorContext empty() {
            return EMPTY;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Optional<String> agentName() { return Optional.ofNullable(agentName); }
        public Optional<String> transport() { return Optional.ofNullable(transport); }
        public Optional<String> endpoint() { return Optional.ofNullable(endpoint); }
        public Optional<Integer> httpStatus() { return Optional.ofNullable(httpStatus); }
        public Optional<String> requestId() { return Optional.ofNullable(requestId); }
        public Map<String, String> attributes() { return attributes; }

        public boolean isEmpty() {
            return agentName == null && transport == null && endpoint == null
                    && httpStatus == null && requestId == null && attributes.isEmpty();
        }

        public static final class Builder {
            private String agentName;
            private String transport;
            private String endpoint;
            private Integer httpStatus;
            private String requestId;
            private final Map<String, String> attributes = new LinkedHashMap<>();

            public Builder agentName(String agentName) { this.agentName = agentName; return this; }
            public Builder transport(String transport) { this.transport = transport; return this; }
            public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
            public Builder httpStatus(Integer httpStatus) { this.httpStatus = httpStatus; return this; }
            public Builder requestId(String requestId) { this.requestId = requestId; return this; }
            public Builder attribute(String key, String value) {
                if (key != null && value != null) {
                    this.attributes.put(key, value);
                }
                return this;
            }

            public AgenticErrorContext build() {
                return new AgenticErrorContext(agentName, transport, endpoint, httpStatus, requestId, attributes);
            }
        }
    }
}
