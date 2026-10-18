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

package org.fireflyframework.webhooks.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Main application class for Firefly Webhook Management Platform.
 * <p>
 * This platform provides a unified webhook ingestion endpoint for multiple
 * providers with message queue integration using lib-common-eda.
 */
@SpringBootApplication
@EnableWebFlux
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {
        "org.fireflyframework.webhooks.web",
        "org.fireflyframework.webhooks.core",
        "org.fireflyframework.web"
})
@OpenAPIDefinition(
        info = @Info(
                title = "Firefly Webhook Management Platform",
                version = "1.0.0",
                description = "Unified webhook ingestion platform for multiple providers with message queue integration",
                contact = @Contact(
                        name = "Firefly Platform Team",
                        email = "platform@getfirefly.io"
                )
        ),
        servers = {
                @Server(
                        url = "http://localhost:8080",
                        description = "Local Development Environment"
                ),
                @Server(
                        url = "https://webhooks.getfirefly.io",
                        description = "Production Environment"
                )
        }
)
public class WebhookManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookManagementApplication.class, args);
    }
}
