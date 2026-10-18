/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.invocation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wire-format request body for an agent invocation.
 *
 * <p>The on-the-wire JSON matches exactly the {@code AgentRequest} pydantic
 * model on the agentic side: {@code prompt} may be a string or a list of
 * multimodal parts; {@code deps}, {@code model_settings}, and
 * {@code conversation_id} map one-to-one with their Python counterparts.</p>
 *
 * <p>Instances are immutable. Use {@link #builder()} or the static
 * convenience factories ({@link #of(String)}, {@link #of(List)}) and the
 * {@code with*} mutators to derive new instances.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AgentRequest {

    private final Object prompt;
    private final Object deps;
    private final Map<String, Object> modelSettings;
    private final String conversationId;

    @JsonCreator
    AgentRequest(
            @JsonProperty("prompt") Object prompt,
            @JsonProperty("deps") Object deps,
            @JsonProperty("model_settings") Map<String, Object> modelSettings,
            @JsonProperty("conversation_id") String conversationId) {
        if (prompt == null) {
            this.prompt = "";
        } else if (prompt instanceof String || prompt instanceof List) {
            this.prompt = prompt;
        } else {
            throw new IllegalArgumentException("AgentRequest prompt must be a String or List<MultiModalPart>, got " + prompt.getClass());
        }
        this.deps = deps;
        this.modelSettings = modelSettings == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(modelSettings));
        this.conversationId = conversationId;
    }

    public static AgentRequest of(String prompt) {
        return new AgentRequest(prompt, null, null, null);
    }

    public static AgentRequest of(List<MultiModalPart> parts) {
        Objects.requireNonNull(parts, "parts");
        return new AgentRequest(List.copyOf(parts), null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("prompt")
    public Object prompt() {
        return prompt;
    }

    @JsonProperty("deps")
    public Object deps() {
        return deps;
    }

    @JsonProperty("model_settings")
    public Map<String, Object> modelSettings() {
        return modelSettings;
    }

    @JsonProperty("conversation_id")
    public String conversationId() {
        return conversationId;
    }

    public Optional<String> conversationIdOptional() {
        return Optional.ofNullable(conversationId);
    }

    public AgentRequest withPrompt(String newPrompt) {
        return new AgentRequest(newPrompt, deps, modelSettings, conversationId);
    }

    public AgentRequest withPrompt(List<MultiModalPart> parts) {
        return new AgentRequest(List.copyOf(parts), deps, modelSettings, conversationId);
    }

    public AgentRequest withDeps(Object newDeps) {
        return new AgentRequest(prompt, newDeps, modelSettings, conversationId);
    }

    public AgentRequest withModelSettings(Map<String, Object> settings) {
        return new AgentRequest(prompt, deps, settings, conversationId);
    }

    public AgentRequest withConversationId(String id) {
        return new AgentRequest(prompt, deps, modelSettings, id);
    }

    public static final class Builder {
        private Object prompt = "";
        private Object deps;
        private Map<String, Object> modelSettings;
        private String conversationId;

        public Builder prompt(String text) {
            this.prompt = text;
            return this;
        }

        public Builder prompt(List<MultiModalPart> parts) {
            this.prompt = List.copyOf(parts);
            return this;
        }

        public Builder deps(Object deps) {
            this.deps = deps;
            return this;
        }

        public Builder modelSetting(String key, Object value) {
            if (this.modelSettings == null) {
                this.modelSettings = new LinkedHashMap<>();
            }
            this.modelSettings.put(key, value);
            return this;
        }

        public Builder modelSettings(Map<String, Object> settings) {
            if (settings != null) {
                if (this.modelSettings == null) {
                    this.modelSettings = new LinkedHashMap<>();
                }
                this.modelSettings.putAll(settings);
            }
            return this;
        }

        public Builder conversationId(String id) {
            this.conversationId = id;
            return this;
        }

        public AgentRequest build() {
            return new AgentRequest(prompt, deps, modelSettings, conversationId);
        }
    }
}
