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

package org.fireflyframework.security.test;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * A configurable {@link PolicyDecisionPort} test double that returns a predetermined decision and
 * records the last request it received, so tests can assert PEP wiring without a real policy engine.
 */
public class FakePolicyDecisionPort implements PolicyDecisionPort {

    private volatile Decision next;
    private volatile String lastAction;
    private volatile String lastResource;

    public FakePolicyDecisionPort(Decision next) {
        this.next = next;
    }

    public static FakePolicyDecisionPort permitAll() {
        return new FakePolicyDecisionPort(Decision.permit());
    }

    public static FakePolicyDecisionPort denyAll(String reason) {
        return new FakePolicyDecisionPort(Decision.deny(reason));
    }

    public FakePolicyDecisionPort returning(Decision decision) {
        this.next = decision;
        return this;
    }

    public String lastAction() {
        return lastAction;
    }

    public String lastResource() {
        return lastResource;
    }

    @Override
    public Mono<Decision> authorize(SecurityPrincipal principal, String action, String resource, Map<String, Object> context) {
        this.lastAction = action;
        this.lastResource = resource;
        return Mono.just(next);
    }
}
