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

package org.fireflyframework.security.core.authz;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

/**
 * Evaluates a {@link SecureRequirement} against a {@link SecurityPrincipal}.
 *
 * <p>Semantics (all <strong>fail-closed</strong>):
 * <ul>
 *   <li>A {@code null} principal is always denied.</li>
 *   <li>Across dimensions (roles, scopes, permissions, expression) <em>all</em> declared dimensions must pass (AND).</li>
 *   <li>Within a dimension, {@code requireAll*} chooses ALL vs ANY matching (ANY by default).</li>
 *   <li>An empty requirement permits any authenticated principal (i.e. "require authentication").</li>
 *   <li>A failing or throwing SpEL expression denies.</li>
 * </ul>
 */
public class SecureAuthorizationEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SecureAuthorizationEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();

    public Decision evaluate(SecurityPrincipal principal, SecureRequirement requirement) {
        if (principal == null) {
            return Decision.deny("no authenticated principal");
        }
        if (!requirement.roles().isEmpty()) {
            boolean ok = requirement.requireAllRoles()
                    ? principal.hasAllAuthorities(requirement.roles())
                    : principal.hasAnyAuthority(requirement.roles());
            if (!ok) {
                return Decision.deny("missing required roles");
            }
        }
        if (!requirement.scopes().isEmpty()) {
            boolean ok = requirement.requireAllScopes()
                    ? requirement.scopes().stream().allMatch(principal::hasScope)
                    : requirement.scopes().stream().anyMatch(principal::hasScope);
            if (!ok) {
                return Decision.deny("missing required scopes");
            }
        }
        if (!requirement.permissions().isEmpty()) {
            boolean ok = requirement.requireAllPermissions()
                    ? principal.hasAllAuthorities(requirement.permissions())
                    : principal.hasAnyAuthority(requirement.permissions());
            if (!ok) {
                return Decision.deny("missing required permissions");
            }
        }
        if (!requirement.expression().isBlank() && !evaluateExpression(principal, requirement.expression())) {
            return Decision.deny("authorization expression not satisfied");
        }
        return Decision.permit();
    }

    private boolean evaluateExpression(SecurityPrincipal principal, String expression) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(principal);
            context.setVariable("principal", principal);
            Expression expr = parser.parseExpression(expression);
            return Boolean.TRUE.equals(expr.getValue(context, Boolean.class));
        } catch (RuntimeException ex) {
            log.warn("Denying: @Secure expression failed to evaluate [{}]: {}", expression, ex.getMessage());
            return false;
        }
    }

    /** Convenience: evaluate against a list of granted authorities (no scopes/expression). */
    public Decision evaluateAuthorities(List<String> grantedAuthorities, SecureRequirement requirement) {
        return evaluate(SecurityPrincipal.builder().subject("anon")
                .authorities(new java.util.LinkedHashSet<>(grantedAuthorities)).build(), requirement);
    }
}
