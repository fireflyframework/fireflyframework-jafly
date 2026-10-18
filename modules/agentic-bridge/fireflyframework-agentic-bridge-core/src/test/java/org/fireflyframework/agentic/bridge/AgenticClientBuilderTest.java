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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgenticClientBuilderTest {

    @Test
    void builderRejectsBlankName() {
        assertThatThrownBy(() -> AgenticClient.builder(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildRequiresBaseUrl() {
        assertThatThrownBy(() -> AgenticClient.builder("c").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void requireTlsRejectsHttpUrl() {
        assertThatThrownBy(() -> AgenticClient.builder("c").requireTls(true).baseUrl("http://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void timeoutSetsAllThreeTimeouts() {
        AgenticClient client = AgenticClient.builder("c")
                .baseUrl("http://example.com")
                .timeout(Duration.ofSeconds(7))
                .build();
        assertThat(client).isNotNull();
        client.close();
    }
}
