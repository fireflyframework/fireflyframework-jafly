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

package org.fireflyframework.rules.core.dsl.function;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of user-defined {@link RuleFunction} implementations available to the rule DSL.
 * <p>
 * The registry is consulted before the built-in function catalog, allowing applications to
 * extend or override the engine without modifying its source.
 *
 * <h3>Registration</h3>
 * <pre>{@code
 * @Configuration
 * class MyRulesConfig {
 *     @Bean
 *     CommandLineRunner registerCustomFunctions(CustomFunctionRegistry registry) {
 *         return args -> registry.register("my_score", a -> myScoring((Number) a[0]));
 *     }
 * }
 * }</pre>
 *
 * <p>Lookups are case-insensitive. The registry is thread-safe.
 */
@Component
public class CustomFunctionRegistry {

    private final Map<String, RuleFunction> functions = new ConcurrentHashMap<>();

    /**
     * Register a function under the given name. If a function with the same name (ignoring case)
     * is already registered, it is replaced.
     *
     * @param name     the function name as referenced from the DSL; must not be {@code null} or blank
     * @param function the function implementation; must not be {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code function} is {@code null}
     */
    public void register(String name, RuleFunction function) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Function name must not be blank");
        }
        if (function == null) {
            throw new IllegalArgumentException("Function must not be null");
        }
        functions.put(name.toLowerCase(Locale.ROOT), function);
    }

    /**
     * Remove a registered function by name. No-op if the name is not registered.
     *
     * @return {@code true} if the function existed and was removed
     */
    public boolean unregister(String name) {
        if (name == null) return false;
        return functions.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    /**
     * Look up a registered function by name (case-insensitive).
     */
    public Optional<RuleFunction> lookup(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(functions.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Return the names of all currently registered functions (case folded to lower-case).
     * The returned set is an immutable snapshot.
     */
    public Set<String> registeredNames() {
        return Collections.unmodifiableSet(Set.copyOf(functions.keySet()));
    }

    /**
     * Whether a function with the given name (case-insensitive) is registered.
     */
    public boolean contains(String name) {
        return lookup(name).isPresent();
    }
}
