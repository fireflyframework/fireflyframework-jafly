/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.conversation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive client for the {@code /agents/conversations} CRUD endpoints
 * exposed by the agentic REST layer.
 *
 * <p>All methods are non-blocking. {@link #create()} issues a {@code POST}
 * request returning the server-generated identifier; {@link #history(String)}
 * fetches the message log; {@link #delete(String)} clears the server-side
 * state.</p>
 */
public interface ConversationManager {

    /**
     * Asks the agentic service to create a new conversation and return the
     * generated identifier.
     */
    Mono<Conversation> create();

    /**
     * Fetches the message history of an existing conversation.
     */
    Flux<ConversationMessage> history(String conversationId);

    /**
     * Clears the server-side state of an existing conversation.
     */
    Mono<Void> delete(String conversationId);
}
