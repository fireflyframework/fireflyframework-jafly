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


package org.fireflyframework.validators;

import org.fireflyframework.annotations.ValidPasswordStrength;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for password strength.
 * <p>
 * This validator checks if a password is strong enough according to:
 * - Length constraints
 * - Character type constraints (uppercase, lowercase, digits, special characters)
 * - Common/weak password detection
 * <p>
 * For password strength analysis and generation utilities, see {@link PasswordStrengthUtils}.
 */
public class PasswordStrengthValidator implements ConstraintValidator<ValidPasswordStrength, String> {

    private int minLength;
    private int maxLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecialChar;
    private int minCharTypes;
    private boolean rejectCommonPasswords;

    @Override
    public void initialize(ValidPasswordStrength constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.maxLength = constraintAnnotation.maxLength();
        this.requireUppercase = constraintAnnotation.requireUppercase();
        this.requireLowercase = constraintAnnotation.requireLowercase();
        this.requireDigit = constraintAnnotation.requireDigit();
        this.requireSpecialChar = constraintAnnotation.requireSpecialChar();
        this.minCharTypes = constraintAnnotation.minCharTypes();
        this.rejectCommonPasswords = constraintAnnotation.rejectCommonPasswords();
    }

    /**
     * Validates if the provided password is strong enough.
     *
     * @param password the password to validate
     * @param context the constraint validator context
     * @return true if the password is strong enough, false otherwise
     */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        // Check length constraints
        if (password.length() < minLength || password.length() > maxLength) {
            return false;
        }

        // Check if common passwords should be rejected
        if (rejectCommonPasswords && PasswordStrengthUtils.isCommonPassword(password)) {
            return false;
        }

        // Check character type constraints
        int charTypesCount = 0;

        boolean hasUppercase = PasswordStrengthUtils.UPPERCASE_PATTERN.matcher(password).find();
        boolean hasLowercase = PasswordStrengthUtils.LOWERCASE_PATTERN.matcher(password).find();
        boolean hasDigit = PasswordStrengthUtils.DIGIT_PATTERN.matcher(password).find();
        boolean hasSpecialChar = PasswordStrengthUtils.SPECIAL_CHAR_PATTERN.matcher(password).find();

        if (hasUppercase) charTypesCount++;
        if (hasLowercase) charTypesCount++;
        if (hasDigit) charTypesCount++;
        if (hasSpecialChar) charTypesCount++;

        // Check if the password has the required character types
        if (requireUppercase && !hasUppercase) {
            return false;
        }
        if (requireLowercase && !hasLowercase) {
            return false;
        }
        if (requireDigit && !hasDigit) {
            return false;
        }
        if (requireSpecialChar && !hasSpecialChar) {
            return false;
        }

        // Check if the password has the minimum number of character types
        return charTypesCount >= minCharTypes;
    }

    /**
     * @deprecated Use {@link PasswordStrengthUtils#isCommonPassword(String)} instead.
     */
    @Deprecated(forRemoval = true)
    public boolean isCommonPassword(String password) {
        return PasswordStrengthUtils.isCommonPassword(password);
    }

    /**
     * @deprecated Use {@link PasswordStrengthUtils#calculateStrengthScore(String)} instead.
     */
    @Deprecated(forRemoval = true)
    public int calculateStrengthScore(String password) {
        return PasswordStrengthUtils.calculateStrengthScore(password);
    }

    /**
     * @deprecated Use {@link PasswordStrengthUtils#getStrengthCategory(String)} instead.
     */
    @Deprecated(forRemoval = true)
    public PasswordStrength getStrengthCategory(String password) {
        PasswordStrengthUtils.PasswordStrength category = PasswordStrengthUtils.getStrengthCategory(password);
        return category == null ? null : PasswordStrength.valueOf(category.name());
    }

    /**
     * @deprecated Use {@link PasswordStrengthUtils#generateSecurePassword(int, int, int, boolean, boolean, boolean, boolean)} instead.
     */
    @Deprecated(forRemoval = true)
    public String generateSecurePassword(int length) {
        return PasswordStrengthUtils.generateSecurePassword(
                length, minLength, maxLength,
                requireUppercase, requireLowercase, requireDigit, requireSpecialChar);
    }

    /**
     * @deprecated Use {@link PasswordStrengthUtils.PasswordStrength} instead.
     */
    @Deprecated(forRemoval = true)
    public enum PasswordStrength {
        VERY_WEAK,
        WEAK,
        MEDIUM,
        STRONG,
        VERY_STRONG
    }
}
