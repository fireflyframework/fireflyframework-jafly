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

package org.fireflyframework.security.api.domain;

import java.time.Instant;
import java.util.Map;

/**
 * A security audit record emitted at authentication / authorization / token / key decision points.
 *
 * @param type        the event type (e.g. {@code AUTHENTICATION_SUCCESS}, {@code AUTHORIZATION_DENIED})
 * @param subject     the subject identifier, when known
 * @param tenantId    the tenant, when known
 * @param outcome     the outcome discriminator (e.g. {@code SUCCESS}, {@code FAILURE}, {@code DENIED})
 * @param resource    the resource acted upon, when applicable
 * @param action      the attempted action, when applicable
 * @param correlationId request/trace correlation id, when available
 * @param timestamp   the instant the event occurred
 * @param attributes  additional structured detail (must not contain secrets)
 */
public record SecurityAuditEvent(
        String type,
        String subject,
        String tenantId,
        String outcome,
        String resource,
        String action,
        String correlationId,
        Instant timestamp,
        Map<String, Object> attributes
) {

    public SecurityAuditEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
