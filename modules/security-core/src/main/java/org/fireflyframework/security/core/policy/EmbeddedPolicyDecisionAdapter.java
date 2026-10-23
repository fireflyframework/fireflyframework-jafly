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
 * {@link PolicyRule}s with deny-overrides semantics. The legacy constructor uses these rules:
 *
 * <ul>
 *   <li>No rules registered &rarr; PERMIT (ABAC not in use; defer to RBAC at the PEP).</li>
 *   <li>Any rule DENIES &rarr; DENY (deny overrides).</li>
 *   <li>Otherwise at least one rule PERMITS &rarr; PERMIT.</li>
 *   <li>Rules registered but none permit (all abstain) &rarr; DENY.</li>
 *   <li>A rule errors &rarr; treated as INDETERMINATE (never PERMIT).</li>
 * </ul>
 *
 * <p>The strict constructor requires an explicit permit, denies failed or empty rule publishers,
 * and retains permit obligations for the enforcing caller. The original constructor preserves
 * the legacy no-rules deferral to a separate RBAC gate.
 */
public class EmbeddedPolicyDecisionAdapter implements PolicyDecisionPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPolicyDecisionAdapter.class);

    private final List<PolicyRule> rules;
    private final boolean strict;

    public EmbeddedPolicyDecisionAdapter(List<PolicyRule> rules) {
        this(rules, false);
    }

    /** Requires an explicit policy result and retains obligations for the enforcing caller. */
    public EmbeddedPolicyDecisionAdapter(List<PolicyRule> rules, boolean strict) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.strict = strict;
    }

    @Override
    public Mono<Decision> authorize(SecurityPrincipal principal, String action, String resource, Map<String, Object> context) {
        if (rules.isEmpty()) {
            return Mono.just(strict ? Decision.deny("no policy rules configured") : Decision.permit());
        }
        return Flux.fromIterable(rules)
                .concatMap(rule -> evaluate(rule, principal, action, resource, context)
                        .onErrorResume(ex -> {
                            if (strict) {
                                log.warn("Policy rule '{}' failed; denying access", rule.name());
                                return Mono.just(Decision.deny("policy rule failed"));
                            }
                            log.warn("Policy rule '{}' errored; treating as indeterminate: {}", rule.name(), ex.getMessage());
                            return Mono.just(Decision.indeterminate("rule error: " + rule.name()));
                        }))
                .collectList()
                .map(this::combine);
    }

    private Mono<Decision> evaluate(PolicyRule rule, SecurityPrincipal principal,
                                    String action, String resource, Map<String, Object> context) {
        if (!strict) {
            return rule.evaluate(principal, action, resource, context);
        }
        return Mono.defer(() -> rule.evaluate(principal, action, resource, context))
                .defaultIfEmpty(Decision.deny("policy rule returned no decision"))
                .map(decision -> decision.effect() == null
                        ? Decision.deny("policy rule returned an invalid decision") : decision);
    }

    private Decision combine(List<Decision> decisions) {
        if (decisions.stream().anyMatch(d -> d.effect() == Decision.Effect.DENY)) {
            return Decision.deny("denied by policy");
        }
        if (decisions.stream().anyMatch(Decision::granted)) {
            if (strict) {
                return Decision.permit(decisions.stream().filter(Decision::granted)
                        .flatMap(decision -> decision.obligations().stream()).toList());
            }
            return Decision.permit();
        }
        return Decision.deny("no policy rule permitted access");
    }
}
