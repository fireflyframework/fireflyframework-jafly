/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.catalog;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata describing a single registered agent, returned by
 * {@code GET /agents/} on the agentic side.
 *
 * <p>Only {@code name} is guaranteed to be present. Every other field is
 * optional, and the {@code attributes} map captures whatever extra keys the
 * agentic registry produced.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AgentDescriptor {

    private final String name;
    private final String description;
    private final String model;
    private final Map<String, Object> attributes;

    @JsonCreator
    public AgentDescriptor(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonAlias({"model", "model_name"})
            @JsonProperty("model") String model) {
        this.name = name;
        this.description = description;
        this.model = model;
        this.attributes = new LinkedHashMap<>();
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("description")
    public String description() {
        return description;
    }

    @JsonProperty("model")
    public String model() {
        return model;
    }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @JsonAnySetter
    void putAttribute(String key, Object value) {
        if (key == null) {
            return;
        }
        switch (key) {
            case "name", "description", "model", "model_name" -> { /* already mapped */ }
            default -> attributes.put(key, value);
        }
    }
}
