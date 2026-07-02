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

import org.fireflyframework.security.api.annotation.Secure;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Framework-neutral view of a {@link Secure} requirement, decoupling the authorization evaluator
 * from the annotation type so it can also be built programmatically (e.g. from a URL registry).
 */
public record SecureRequirement(
        List<String> roles,
        List<String> scopes,
        List<String> permissions,
        boolean requireAllRoles,
        boolean requireAllScopes,
        boolean requireAllPermissions,
        String expression
) {

    public SecureRequirement {
        roles = roles == null ? List.of() : List.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        expression = expression == null ? "" : expression;
    }

    public static SecureRequirement from(Secure secure) {
        return from(secure, UnaryOperator.identity());
    }

    /**
     * Builds a requirement from the annotation, resolving each declared role/scope/permission through
     * {@code resolver} (e.g. Spring property placeholders), so a service can externalise per-product
     * authority names — {@code @Secure(roles = "${idp.security.admin-role}")}. Literal values without a
     * placeholder pass through unchanged; the SpEL {@code expression} is left verbatim.
     */
    public static SecureRequirement from(Secure secure, UnaryOperator<String> resolver) {
        return new SecureRequirement(
                resolveEach(secure.roles(), resolver),
                resolveEach(secure.scopes(), resolver),
                resolveEach(secure.permissions(), resolver),
                secure.requireAllRoles(),
                secure.requireAllScopes(),
                secure.requireAllPermissions(),
                secure.expression());
    }

    private static List<String> resolveEach(String[] values, UnaryOperator<String> resolver) {
        return Arrays.stream(values).map(resolver).toList();
    }

    public boolean isEmpty() {
        return roles.isEmpty() && scopes.isEmpty() && permissions.isEmpty() && expression.isBlank();
    }
}
