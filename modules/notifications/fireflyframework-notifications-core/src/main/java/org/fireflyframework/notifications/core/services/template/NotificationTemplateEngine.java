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

package org.fireflyframework.notifications.core.services.template;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Reactive template engine for rendering notification templates.
 *
 * <p>Templates are resolved by ID and rendered with variable substitution.
 * Implementations may use FreeMarker, Mustache, Thymeleaf, or any other engine.
 */
public interface NotificationTemplateEngine {

    /**
     * Render a template by its ID with the given variables.
     *
     * @param templateId the template identifier (e.g., "welcome-email", "password-reset")
     * @param variables  the variable map for template substitution
     * @return a Mono emitting the rendered content (typically HTML)
     */
    Mono<String> render(String templateId, Map<String, Object> variables);
}
