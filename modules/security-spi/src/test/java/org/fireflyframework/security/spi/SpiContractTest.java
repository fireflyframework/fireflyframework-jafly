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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guard: every driven port is an interface, and every method that performs I/O returns a
 * reactive type ({@link Mono}/{@link Flux}). Pure mapping ports ({@link AuthorityMappingPort}) are
 * exempt and explicitly listed.
 */
class SpiContractTest {

    private static final List<Class<?>> REACTIVE_PORTS = List.of(
            TokenValidationPort.class, PolicyDecisionPort.class, RelationshipPort.class,
            KeyManagementPort.class, SecretsPort.class, AuditEventPort.class,
            TokenIntrospectionCachePort.class, RevocationPort.class, IssuerRegistryPort.class,
            TenantResolverPort.class, SecurityContextPort.class, PrincipalAttributeContributorPort.class);

    @Test
    void allPortsAreInterfaces() {
        REACTIVE_PORTS.forEach(p -> assertThat(p.isInterface()).as(p.getSimpleName()).isTrue());
        assertThat(AuthorityMappingPort.class.isInterface()).isTrue();
    }

    @Test
    void reactivePortMethodsReturnPublishers() {
        for (Class<?> port : REACTIVE_PORTS) {
            for (Method m : port.getDeclaredMethods()) {
                Class<?> rt = m.getReturnType();
                assertThat(Mono.class.isAssignableFrom(rt) || Flux.class.isAssignableFrom(rt))
                        .as("%s.%s returns a reactive type", port.getSimpleName(), m.getName())
                        .isTrue();
            }
        }
    }
}
