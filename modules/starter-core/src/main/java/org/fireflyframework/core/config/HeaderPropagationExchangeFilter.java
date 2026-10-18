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

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Re-applies the allow-listed inbound headers captured by {@link HeaderPropagationWebFilter}
 * (from the reactive context) onto every outbound WebClient request.
 *
 * <p>Exposed as a bean so it can be attached both to the framework's own {@code WebClient}
 * beans and to SDK-generated {@code ApiClient} WebClients (which do not inherit the framework
 * builder), keeping tenant/user/correlation context coherent across the whole call chain.</p>
 *
 * <p>Headers already present on the outgoing request are left untouched, so an explicit
 * per-call override always wins over the propagated value.</p>
 */
public class HeaderPropagationExchangeFilter implements ExchangeFilterFunction {

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(ctx -> {
            Map<String, String> headers = (Map<String, String>) ctx.getOrDefault(
                    HeaderPropagationWebFilter.PROPAGATED_HEADERS_CONTEXT_KEY, Map.<String, String>of());
            if (headers.isEmpty()) {
                return next.exchange(request);
            }

            ClientRequest.Builder builder = ClientRequest.from(request);
            headers.forEach((name, value) -> {
                if (!request.headers().containsKey(name)) {
                    builder.header(name, value);
                }
            });
            return next.exchange(builder.build());
        });
    }
}
