/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.data.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DataConfigurationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.application.name=test-data-config",
    "firefly.data.eda.enabled=false",
    "firefly.data.cqrs.enabled=true",
    "firefly.data.orchestration.enabled=true",
    "firefly.data.orchestration-engine.enabled=false"
})
class DataConfigurationTest {

    @EnableConfigurationProperties(DataConfiguration.class)
    static class TestConfig {
    }

    @Autowired
    private DataConfiguration dataConfiguration;

    @Test
    void dataConfiguration_ShouldLoadPropertiesCorrectly() {
        assertNotNull(dataConfiguration);
        
        // Test EDA config
        assertNotNull(dataConfiguration.getEda());
        assertFalse(dataConfiguration.getEda().isEnabled());
        
        // Test CQRS config
        assertNotNull(dataConfiguration.getCqrs());
        assertTrue(dataConfiguration.getCqrs().isEnabled());
        
        // Test orchestration config  
        assertNotNull(dataConfiguration.getOrchestration());
        assertTrue(dataConfiguration.getOrchestration().isEnabled());
        
        // Test orchestration engine config
        assertNotNull(dataConfiguration.getOrchestrationEngine());
        assertFalse(dataConfiguration.getOrchestrationEngine().isEnabled());
    }

    @Test
    void dataConfiguration_ShouldHaveDefaults() {
        DataConfiguration defaultConfig = new DataConfiguration();
        
        // All should be enabled by default
        assertTrue(defaultConfig.getEda().isEnabled());
        assertTrue(defaultConfig.getCqrs().isEnabled());
        assertTrue(defaultConfig.getOrchestration().isEnabled());
        assertTrue(defaultConfig.getOrchestrationEngine().isEnabled());
    }
}
