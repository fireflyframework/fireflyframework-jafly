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

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Product extension seam. Implementations enrich a validated principal with domain-specific
 * attributes (e.g. a product mapping the subject to its own party/contract model). The framework
 * itself contributes nothing here — this is how products re-introduce their domain without the
 * framework knowing about it. Contributed attributes are merged into {@code SecurityPrincipal.attributes}.
 */
public interface PrincipalAttributeContributorPort {

    Mono<Map<String, Object>> contribute(SecurityPrincipal principal);
}
