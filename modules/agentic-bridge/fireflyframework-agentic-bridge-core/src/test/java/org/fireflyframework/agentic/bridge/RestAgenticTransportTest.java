/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.catalog.AgentCatalog;
import org.fireflyframework.agentic.bridge.exception.AgentNotFoundException;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.streaming.ChunkEvent;
import org.fireflyframework.agentic.bridge.streaming.DoneEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class RestAgenticTransportTest {

    private WireMockServer wireMock;
    private AgenticClient client;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        client = AgenticClient.builder("test")
                .baseUrl("http://localhost:" + wireMock.port())
                .auth(AuthStrategy.bearer("ci-token"))
                .build();
    }

    @AfterEach
    void stop() {
        client.close();
        wireMock.stop();
    }

    @Test
    void invokeReturnsTypedResponse() {
        wireMock.stubFor(post(urlEqualTo("/agents/summariser/run"))
                .withHeader("Authorization", equalTo("Bearer ci-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"agent_name\":\"summariser\",\"output\":\"hi\",\"success\":true}")));

        StepVerifier.create(client.invoke("summariser", AgentRequest.of("hello")))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.agentName()).isEqualTo("summariser");
                    assertThat(response.output()).isEqualTo("hi");
                })
                .verifyComplete();
    }

    @Test
    void notFoundMapsToTypedException() {
        wireMock.stubFor(post(urlEqualTo("/agents/missing/run"))
                .willReturn(aResponse().withStatus(404).withBody("{\"detail\":\"not found\"}")));

        StepVerifier.create(client.invoke("missing", AgentRequest.of("x")))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AgentNotFoundException.class))
                .verify();
    }

    @Test
    void streamDecodesSseChunksUntilDone() {
        String body = "data: {\"text\":\"hello \"}\n\n" +
                "data: {\"text\":\"world\"}\n\n" +
                "data: [DONE]\n\n";
        wireMock.stubFor(post(urlPathEqualTo("/agents/writer/stream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body)));

        StepVerifier.create(client.stream("writer", AgentRequest.of("draft")))
                .assertNext(event -> assertThat(event).isInstanceOfSatisfying(ChunkEvent.class,
                        c -> assertThat(c.text()).isEqualTo("hello ")))
                .assertNext(event -> assertThat(event).isInstanceOfSatisfying(ChunkEvent.class,
                        c -> assertThat(c.text()).isEqualTo("world")))
                .assertNext(event -> assertThat(event).isEqualTo(DoneEvent.INSTANCE))
                .verifyComplete();
    }

    @Test
    void catalogRoundTrips() {
        wireMock.stubFor(get(urlEqualTo("/agents/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"name\":\"writer\",\"description\":\"writes things\",\"model\":\"gpt-4o-mini\"},{\"name\":\"summariser\",\"model\":\"claude-haiku\"}]")));

        StepVerifier.create(client.catalog())
                .assertNext(cat -> {
                    assertThat(cat.size()).isEqualTo(2);
                    assertThat(cat.contains("writer")).isTrue();
                    assertThat(cat.find("summariser")).isPresent()
                            .get().extracting(d -> d.model()).isEqualTo("claude-haiku");
                })
                .verifyComplete();
    }

    @Test
    void conversationLifecycle() {
        wireMock.stubFor(post(urlEqualTo("/agents/conversations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"conversation_id\":\"abc-123\"}")));
        wireMock.stubFor(delete(urlEqualTo("/agents/conversations/abc-123"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"cleared\"}")));

        StepVerifier.create(client.conversations().create()
                        .flatMap(conv -> client.conversations().delete(conv.id())))
                .verifyComplete();
    }

    @Test
    void healthReportsUpOn200() {
        wireMock.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));

        StepVerifier.create(client.health())
                .assertNext(h -> assertThat(h.status().name()).isEqualTo("UP"))
                .verifyComplete();
    }

    @Test
    void healthReportsDownOn5xx() {
        wireMock.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(503)));

        StepVerifier.create(client.health())
                .assertNext(h -> assertThat(h.status().name()).isEqualTo("DOWN"))
                .verifyComplete();
    }
}
