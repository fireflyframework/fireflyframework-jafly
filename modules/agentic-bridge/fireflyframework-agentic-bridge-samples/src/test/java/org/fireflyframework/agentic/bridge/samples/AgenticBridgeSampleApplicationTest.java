/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.samples;

import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "firefly.agentic-bridge.primary.base-url=http://localhost:65535",
        "firefly.agentic-bridge.primary.auth.type=NONE",
        "management.health.firefly-agentic-bridge.enabled=false"
})
class AgenticBridgeSampleApplicationTest {

    @Autowired
    AgenticClient client;

    @Autowired
    AgenticBridge bridge;

    @Test
    void contextWiresClientAndBridge() {
        assertThat(client).isNotNull();
        assertThat(bridge).isNotNull();
        assertThat(bridge.client()).isSameAs(client);
    }
}
