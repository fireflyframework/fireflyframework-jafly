/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.conversation.Conversation;
import org.fireflyframework.agentic.bridge.conversation.ConversationManager;
import org.fireflyframework.agentic.bridge.conversation.ConversationMessage;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST-backed implementation of {@link ConversationManager}.
 */
public final class RestConversationManager implements ConversationManager {

    private final WebClient webClient;
    private final AuthStrategy auth;
    private final ObjectMapper mapper;

    public RestConversationManager(WebClient webClient, AuthStrategy auth, ObjectMapper mapper) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Mono<Conversation> create() {
        return webClient.post()
                .uri("/agents/conversations")
                .headers(h -> auth.headers().forEach(h::add))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(payload -> {
                    Object id = payload.get("conversation_id");
                    if (id == null) {
                        throw new AgenticTransportException("Conversation create response missing conversation_id",
                                AgenticErrorContext.builder().transport("rest").endpoint("/agents/conversations").build(), null);
                    }
                    return new Conversation(id.toString(), Instant.now());
                })
                .onErrorMap(this::translate);
    }

    @Override
    public Flux<ConversationMessage> history(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId");
        return webClient.get()
                .uri("/agents/conversations/{id}", conversationId)
                .headers(h -> auth.headers().forEach(h::add))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMapMany(payload -> {
                    Object messages = payload.get("messages");
                    if (!(messages instanceof List<?> list)) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(list)
                            .map(item -> mapper.convertValue(item, new TypeReference<Map<String, Object>>() {}))
                            .map(ConversationMessage::new);
                })
                .onErrorMap(this::translate);
    }

    @Override
    public Mono<Void> delete(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId");
        return webClient.delete()
                .uri("/agents/conversations/{id}", conversationId)
                .headers(h -> auth.headers().forEach(h::add))
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorMap(this::translate);
    }

    private Throwable translate(Throwable err) {
        if (err instanceof AgenticBridgeException) return err;
        return new AgenticTransportException("Conversation API failure",
                AgenticErrorContext.builder().transport("rest").build(), err);
    }
}
