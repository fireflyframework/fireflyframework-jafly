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

package org.fireflyframework.security.core.audit;

import org.fireflyframework.security.api.domain.SecurityAuditEvent;
import org.fireflyframework.security.spi.AuditEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default {@link AuditEventPort}: emits structured audit lines to a dedicated logger. Never throws
 * and never blocks the request path. Production deployments swap in JDBC/Kafka/SIEM adapters.
 */
public class LoggingAuditEventAdapter implements AuditEventPort {

    private static final Logger log = LoggerFactory.getLogger("org.fireflyframework.security.audit");

    @Override
    public Mono<Void> emit(SecurityAuditEvent event) {
        return Mono.fromRunnable(() -> log.info(
                "security-audit type={} outcome={} subject={} tenant={} action={} resource={} correlationId={}",
                event.type(), event.outcome(), event.subject(), event.tenantId(),
                event.action(), event.resource(), event.correlationId()));
    }
}
