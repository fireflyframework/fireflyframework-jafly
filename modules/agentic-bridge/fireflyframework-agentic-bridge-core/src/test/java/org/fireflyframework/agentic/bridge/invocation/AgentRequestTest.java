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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void textPromptSerialisesToFlatString() throws Exception {
        AgentRequest req = AgentRequest.of("Summarise the document.");
        String json = mapper.writeValueAsString(req);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.get("prompt").asText()).isEqualTo("Summarise the document.");
    }

    @Test
    void multiModalPromptSerialisesAsList() throws Exception {
        AgentRequest req = AgentRequest.of(List.of(
                MultiModalPart.text("Describe this image:"),
                MultiModalPart.imageUrl("https://example.com/picture.png")));
        String json = mapper.writeValueAsString(req);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.get("prompt").isArray()).isTrue();
        assertThat(tree.get("prompt").get(1).get("type").asText()).isEqualTo("image_url");
        assertThat(tree.get("prompt").get(1).get("content").asText()).isEqualTo("https://example.com/picture.png");
    }

    @Test
    void conversationIdAndModelSettingsAreEmitted() throws Exception {
        AgentRequest req = AgentRequest.builder()
                .prompt("hello")
                .conversationId("conv-1")
                .modelSetting("temperature", 0.2)
                .build();
        JsonNode tree = mapper.readTree(mapper.writeValueAsString(req));
        assertThat(tree.get("conversation_id").asText()).isEqualTo("conv-1");
        assertThat(tree.get("model_settings").get("temperature").asDouble()).isEqualTo(0.2);
    }
}
