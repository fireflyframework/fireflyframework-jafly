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

package org.fireflyframework.callbacks.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Main Spring Boot application for Firefly Callback Management Platform.
 * <p>
 * This microservice provides comprehensive outbound webhook management,
 * allowing Firefly services to automatically send events to third-party systems.
 * <p>
 * Key Features:
 * - Event-driven architecture using lib-common-eda
 * - Configurable callback endpoints with retry logic
 * - HMAC signature support for secure delivery
 * - Circuit breaker pattern for failing endpoints
 * - Comprehensive execution tracking and monitoring
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "org.fireflyframework.callbacks",
        "org.fireflyframework.eda"
})
@EnableR2dbcRepositories(basePackages = "org.fireflyframework.callbacks.models.repository")
public class CallbackManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallbackManagementApplication.class, args);
    }
}
