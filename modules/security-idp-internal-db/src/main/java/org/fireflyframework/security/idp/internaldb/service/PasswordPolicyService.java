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

package org.fireflyframework.security.idp.internaldb.service;

import org.fireflyframework.security.idp.internaldb.config.InternalDbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating passwords against configurable policy rules.
 *
 * <p>Policy rules are configured via {@code firefly.security.idp.internal-db.password-policy.*} properties.
 * Validation is enforced on user creation, password change, and password reset.
 */
@Slf4j
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final InternalDbProperties properties;

    /**
     * Validates a password against the configured policy.
     *
     * @param password the password to validate
     * @return list of violation messages (empty if valid)
     */
    public List<String> validate(String password) {
        InternalDbProperties.PasswordPolicyConfig policy = properties.getPasswordPolicy();
        List<String> violations = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            violations.add("Password is required");
            return violations;
        }

        if (password.length() < policy.getMinLength()) {
            violations.add("Password must be at least " + policy.getMinLength() + " characters");
        }

        if (policy.isRequireUppercase() && !password.chars().anyMatch(Character::isUpperCase)) {
            violations.add("Password must contain at least one uppercase letter");
        }

        if (policy.isRequireLowercase() && !password.chars().anyMatch(Character::isLowerCase)) {
            violations.add("Password must contain at least one lowercase letter");
        }

        if (policy.isRequireDigit() && !password.chars().anyMatch(Character::isDigit)) {
            violations.add("Password must contain at least one digit");
        }

        if (policy.isRequireSpecialChar() && password.chars().noneMatch(c -> "!@#$%^&*()-_=+[]{}|;:',.<>?/`~".indexOf(c) >= 0)) {
            violations.add("Password must contain at least one special character");
        }

        if (policy.getMaxLength() > 0 && password.length() > policy.getMaxLength()) {
            violations.add("Password must not exceed " + policy.getMaxLength() + " characters");
        }

        return violations;
    }

    /**
     * Validates and throws if the password does not meet policy requirements.
     *
     * @param password the password to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validateOrThrow(String password) {
        List<String> violations = validate(password);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Password policy violation: " + String.join("; ", violations));
        }
    }
}
