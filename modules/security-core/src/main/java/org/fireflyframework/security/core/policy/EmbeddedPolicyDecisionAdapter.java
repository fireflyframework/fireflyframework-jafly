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

package org.fireflyframework.security.core.policy;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Zero-dependency, in-process default {@link PolicyDecisionPort}. Combines registered
 * {@link PolicyRule}s with deny-overrides semantics and is strictly fail-closed:
 *
 * <ul>
 *   <li>No rules registered &rarr; PERMIT (ABAC not in use; defer to RBAC at the PEP).</li>
 *   <li>Any rule DENIES &rarr; DENY (deny overrides).</li>
 *   <li>Otherwise at least one rule PERMITS &rarr; PERMIT.</li>
 *   <li>Rules registered but none permit (all abstain) &rarr; DENY.</li>
 *   <li>A rule errors &rarr; treated as INDETERMINATE (never PERMIT).</li>
 * </ul>
 */
public class EmbeddedPolicyDecisionAdapter implements PolicyDecisionPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPolicyDecisionAdapter.class);

    private final List<PolicyRule> rules;

    public EmbeddedPolicyDecisionAdapter(List<PolicyRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @Override
    public Mono<Decision> authorize(SecurityPrincipal principal, String action, String resource, Map<String, Object> context) {
        if (rules.isEmpty()) {
            return Mono.just(Decision.permit());
        }
        return Flux.fromIterable(rules)
                .concatMap(rule -> rule.evaluate(principal, action, resource, context)
                        .onErrorResume(ex -> {
                            log.warn("Policy rule '{}' errored; treating as indeterminate: {}", rule.name(), ex.getMessage());
                            return Mono.just(Decision.indeterminate("rule error: " + rule.name()));
                        }))
                .collectList()
                .map(this::combine);
    }

    private Decision combine(List<Decision> decisions) {
        if (decisions.stream().anyMatch(d -> d.effect() == Decision.Effect.DENY)) {
            return Decision.deny("denied by policy");
        }
        if (decisions.stream().anyMatch(Decision::granted)) {
            return Decision.permit();
        }
        return Decision.deny("no policy rule permitted access");
    }
}
