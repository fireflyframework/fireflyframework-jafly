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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Typed projection of the JSON {@code AgentResponse} returned by the agentic
 * REST layer.
 *
 * <p>{@code output} is intentionally untyped because agents may return text,
 * structured Pydantic objects, or arbitrary JSON. Callers can decode it into
 * a domain type via {@link #outputAs(ObjectMapper, Class)}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AgentResponse {

    private final String agentName;
    private final Object output;
    private final boolean success;
    private final String error;
    private final Map<String, Object> metadata;

    @JsonCreator
    public AgentResponse(
            @JsonProperty("agent_name") String agentName,
            @JsonProperty("output") Object output,
            @JsonProperty("success") Boolean success,
            @JsonProperty("error") String error,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.agentName = agentName;
        this.output = output;
        this.success = success == null || success;
        this.error = error;
        this.metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    @JsonProperty("agent_name")
    public String agentName() {
        return agentName;
    }

    @JsonProperty("output")
    public Object output() {
        return output;
    }

    @JsonProperty("success")
    public boolean success() {
        return success;
    }

    @JsonProperty("error")
    public String errorRaw() {
        return error;
    }

    public Optional<String> error() {
        return Optional.ofNullable(error);
    }

    @JsonProperty("metadata")
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Returns the {@code output} as a string. If the underlying value is
     * already a string it is returned verbatim; otherwise its JSON
     * representation is produced via the supplied mapper.
     */
    public String outputAsString(ObjectMapper mapper) {
        if (output == null) {
            return null;
        }
        if (output instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise agent output to string", e);
        }
    }

    /**
     * Decodes the {@code output} payload into the supplied type using the
     * provided Jackson mapper.
     */
    public <T> T outputAs(ObjectMapper mapper, Class<T> type) {
        if (output == null) {
            return null;
        }
        if (type.isInstance(output)) {
            return type.cast(output);
        }
        return mapper.convertValue(output, TypeFactory.defaultInstance().constructType(type));
    }
}
