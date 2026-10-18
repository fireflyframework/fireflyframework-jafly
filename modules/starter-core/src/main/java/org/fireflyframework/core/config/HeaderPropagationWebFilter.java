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

package org.fireflyframework.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the allow-listed inbound request headers into the reactive context so that
 * {@link HeaderPropagationExchangeFilter} can re-apply them to outbound WebClient calls.
 *
 * <p>Runs just after {@code TransactionFilter} (which owns {@code X-Transaction-Id}). It only
 * reads headers and writes an immutable snapshot to the context — it never mutates the request
 * or response — so its ordering relative to other filters is not sensitive.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class HeaderPropagationWebFilter implements WebFilter {

    /** Reactive-context key holding the captured {@code header-name -> value} snapshot. */
    public static final String PROPAGATED_HEADERS_CONTEXT_KEY = "firefly.propagated-headers";

    private final HeaderPropagationProperties properties;

    public HeaderPropagationWebFilter(HeaderPropagationProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator") || path.startsWith("/admin")) {
            return chain.filter(exchange);
        }

        Map<String, String> captured = capture(exchange.getRequest());
        if (captured.isEmpty()) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange)
                .contextWrite(context -> context.put(PROPAGATED_HEADERS_CONTEXT_KEY, Map.copyOf(captured)));
    }

    private Map<String, String> capture(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        // Preserve the configured order; keys stored verbatim (allow-list matched case-insensitively).
        Map<String, String> captured = new LinkedHashMap<>();
        for (String name : properties.getAllowedHeaders()) {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) {
                continue;
            }
            String value = values.get(0);
            if (value != null && !value.isBlank()) {
                captured.put(name, value);
            }
        }
        return captured;
    }
}
