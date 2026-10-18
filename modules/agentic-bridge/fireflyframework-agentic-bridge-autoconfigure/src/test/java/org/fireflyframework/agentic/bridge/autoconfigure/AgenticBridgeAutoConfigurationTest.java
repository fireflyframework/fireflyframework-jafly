/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.autoconfigure;

import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticBridgeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    AgenticBridgeAutoConfiguration.class));

    @Test
    void registersClientAndBridgeWhenBaseUrlSet() {
        runner.withPropertyValues(
                        "firefly.agentic-bridge.primary.base-url=http://localhost:9999",
                        "firefly.agentic-bridge.primary.name=primary")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AgenticClient.class);
                    assertThat(ctx).hasSingleBean(AgenticBridge.class);
                    AgenticClient client = ctx.getBean(AgenticClient.class);
                    assertThat(client.name()).isEqualTo("primary");
                });
    }

    @Test
    void disabledFlagSkipsBeans() {
        runner.withPropertyValues(
                        "firefly.agentic-bridge.enabled=false",
                        "firefly.agentic-bridge.primary.base-url=http://localhost:9999")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AgenticClient.class));
    }

    @Test
    void missingBaseUrlFailsFast() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void apiKeyAuthIsApplied() {
        runner.withPropertyValues(
                        "firefly.agentic-bridge.primary.base-url=http://localhost:9999",
                        "firefly.agentic-bridge.primary.auth.type=API_KEY",
                        "firefly.agentic-bridge.primary.auth.api-key=secret")
                .run(ctx -> assertThat(ctx).hasSingleBean(AgenticClient.class));
    }
}
