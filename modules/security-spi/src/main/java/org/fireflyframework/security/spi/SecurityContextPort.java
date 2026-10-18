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

/**
 * Stack-neutral accessor for the current validated principal. The WebFlux binding implements this
 * over {@code ReactiveSecurityContextHolder}; application code depends on this port, not on Spring.
 */
public interface SecurityContextPort {

    /** @return the current principal, or an empty {@link Mono} when unauthenticated. */
    Mono<SecurityPrincipal> currentPrincipal();
}
