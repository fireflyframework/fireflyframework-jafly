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

package org.fireflyframework.security.webflux.context;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.webflux.authentication.PrincipalSupport;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

/**
 * {@link SecurityContextPort} backed by Spring Security's {@link ReactiveSecurityContextHolder}.
 * Application code depends on the port, keeping it free of Spring Security imports.
 */
public class ReactorSecurityContextAdapter implements SecurityContextPort {

    @Override
    public Mono<SecurityPrincipal> currentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(PrincipalSupport::extract);
    }
}
