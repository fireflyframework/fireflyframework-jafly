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

package org.fireflyframework.webhooks.core.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Web filter for distributed tracing context propagation.
 *
 * <p>Extracts or generates a request ID and propagates it via Reactor Context.
 * Trace ID and span ID propagation is handled automatically by the observability
 * module's {@code Hooks.enableAutomaticContextPropagation()} and the Micrometer
 * tracing bridge -- no manual B3 header extraction or MDC management is needed.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TracingWebFilter implements WebFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_KEY = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Extract or generate request ID
        String requestId = extractHeader(exchange, REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        // Add request ID to response headers
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

        // Propagate request ID via Reactor Context.
        // Trace ID / span ID are automatically propagated by the observability
        // auto-configuration (Hooks.enableAutomaticContextPropagation bridges
        // Micrometer tracing context into Reactor Context and MDC transparently).
        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID_KEY, requestId));
    }

    private String extractHeader(ServerWebExchange exchange, String headerName) {
        String value = exchange.getRequest().getHeaders().getFirst(headerName);
        return (value != null && !value.isBlank()) ? value : null;
    }
}
