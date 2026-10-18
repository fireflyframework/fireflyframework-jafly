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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for inbound-to-outbound HTTP header propagation.
 *
 * <p>When a service receives a request, a curated allow-list of headers (correlation,
 * trusted identity resolved by an upstream gateway/BFF, locale, idempotency) is captured
 * into the reactive context by {@link HeaderPropagationWebFilter} and re-applied to every
 * outbound WebClient call by {@link HeaderPropagationExchangeFilter}. This keeps tenant and
 * user context coherent across the {@code exp -> domain -> core} call chain without each
 * service having to thread the values manually.</p>
 *
 * <p>This is an allow-list on purpose: anything not listed (cookies, {@code Authorization},
 * {@code Host}, content/hop-by-hop headers, {@code X-Forwarded-*}) is never forwarded, so a
 * downstream call cannot accidentally leak the caller's credentials or corrupt framing.</p>
 *
 * <p>{@code X-Transaction-Id} is intentionally absent: it is already propagated by the
 * dedicated {@code TransactionFilter}/WebClient filter and must not be duplicated here.</p>
 */
@ConfigurationProperties(prefix = "firefly.webclient.propagation")
@Getter
@Setter
public class HeaderPropagationProperties {

    /**
     * Whether inbound-to-outbound header propagation is enabled.
     */
    private boolean enabled = true;

    /**
     * Case-insensitive allow-list of header names captured from the inbound request and
     * re-applied to outbound WebClient calls. Only these headers are propagated.
     */
    private List<String> allowedHeaders = List.of(
            // Correlation / distributed tracing
            "X-Request-Id",
            "traceparent",
            "tracestate",
            "b3",
            "X-B3-TraceId",
            "X-B3-SpanId",
            "X-B3-ParentSpanId",
            "X-B3-Sampled",
            // Trusted identity (resolved upstream by the gateway/BFF, not by the client)
            "X-Tenant-Id",
            "X-User-Id",
            "X-User-Name",
            "X-User-Roles",
            // Locale and idempotency
            "Accept-Language",
            "X-Idempotency-Key"
    );
}
