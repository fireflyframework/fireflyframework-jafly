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

package org.fireflyframework.security.spi;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Externalized authorization decision point (ABAC). Implementations (embedded engine, OPA, Cerbos)
 * evaluate whether {@code principal} may perform {@code action} on {@code resource} given
 * {@code context}. Must be <strong>fail-closed</strong>: any error yields a denial/indeterminate.
 */
public interface PolicyDecisionPort {

    Mono<Decision> authorize(SecurityPrincipal principal, String action, String resource, Map<String, Object> context);
}
