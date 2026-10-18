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

package org.fireflyframework.security.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative method/class-level authorization on top of validated authentication.
 *
 * <p>Semantics are <strong>fail-closed</strong>: a method annotated with {@code @Secure} denies
 * unless the current {@code SecurityPrincipal} satisfies the declared requirements. When multiple
 * dimensions are present (roles, scopes, permissions, expression) <em>all</em> must pass.
 * Within a single dimension, {@code requireAll*} switches between ANY (default) and ALL matching.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Secure {

    /** Required authorities/roles. */
    String[] roles() default {};

    /** Required OAuth2 scopes. */
    String[] scopes() default {};

    /** Required fine-grained permissions. */
    String[] permissions() default {};

    /** If {@code true}, every declared role must be held; otherwise any one suffices. */
    boolean requireAllRoles() default false;

    /** If {@code true}, every declared scope must be present; otherwise any one suffices. */
    boolean requireAllScopes() default false;

    /** If {@code true}, every declared permission must be held; otherwise any one suffices. */
    boolean requireAllPermissions() default false;

    /** Optional SpEL expression evaluated against the principal; must resolve to {@code true}. */
    String expression() default "";
}
