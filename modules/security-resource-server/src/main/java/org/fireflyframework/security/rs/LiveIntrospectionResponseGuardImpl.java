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

package org.fireflyframework.security.rs;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;

/** Validates bounded wire types before the native introspector performs claim coercion. */
public final class LiveIntrospectionResponseGuardImpl implements ExchangeFilterFunction {
    public static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(MAX_RESPONSE_BYTES).maxNestingDepth(16).maxTokenCount(4096)
                    .maxNameLength(256).maxStringLength(MAX_RESPONSE_BYTES).maxNumberLength(32).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.defer(() -> next.exchange(request))
                .flatMap(response -> DataBufferUtils.join(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),
                                MAX_RESPONSE_BYTES)
                        .switchIfEmpty(Mono.error(refused()))
                        .map(buffer -> {
                            byte[] bytes;
                            try {
                                bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                            try {
                                if (response.statusCode().value() != 200) {
                                    throw refused();
                                }
                                MediaType type = response.headers().contentType().orElseThrow(LiveIntrospectionResponseGuardImpl::refused);
                                if (!MediaType.APPLICATION_JSON.isCompatibleWith(type)
                                        || (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))) {
                                    throw refused();
                                }
                                String body = StandardCharsets.UTF_8.newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                        .decode(ByteBuffer.wrap(bytes)).toString();
                                validate(mapper.readTree(body));
                                return response.mutate().body(body).build();
                            } catch (Exception error) {
                                throw refused();
                            }
                        }))
                .onErrorMap(error -> refused());
    }

    private static void validate(JsonNode root) {
        if (!root.isObject() || root.get("active") == null || !root.get("active").isBoolean()) {
            throw refused();
        }
        if (!root.get("active").asBoolean()) {
            return;
        }
        string(root.get("iss"));
        string(root.get("sub"));
        string(root.get("client_id"));
        JsonNode audience = root.get("aud");
        if (audience == null) {
            throw refused();
        }
        if (audience.isString()) {
            string(audience);
        } else if (audience.isArray() && audience.size() > 0 && audience.size() <= 32) {
            var seen = new HashSet<String>();
            for (int i = 0; i < audience.size(); i++) {
                if (!seen.add(string(audience.get(i)))) {
                    throw refused();
                }
            }
        } else {
            throw refused();
        }
        time(root.get("exp"));
        for (String optional : new String[]{"iat", "nbf"}) {
            if (root.get(optional) != null) {
                time(root.get(optional));
            }
        }
        for (String optional : new String[]{"jti", "scope"}) {
            if (root.get(optional) != null) {
                string(root.get(optional));
            }
        }
    }

    private static String string(JsonNode node) {
        if (node == null || !node.isString()) {
            throw refused();
        }
        return LiveActiveJwtDecoderImpl.text(node.stringValue());
    }

    private static void time(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
            throw refused();
        }
        Instant.ofEpochSecond(node.asLong());
    }

    private static OAuth2IntrospectionException refused() {
        return new OAuth2IntrospectionException("Live introspection response refused");
    }
}
