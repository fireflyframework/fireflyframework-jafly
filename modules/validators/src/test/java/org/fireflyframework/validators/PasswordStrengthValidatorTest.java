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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PasswordStrengthValidator}.
 */
class PasswordStrengthValidatorTest {

    private PasswordStrengthValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordStrengthValidator();
        // Initialize with default values (minLength=8, maxLength=128, requireUppercase=true, requireLowercase=true,
        // requireDigit=true, requireSpecialChar=true, minCharTypes=3, rejectCommonPasswords=true)
        validator.initialize(new org.fireflyframework.annotations.ValidPasswordStrength() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPasswordStrength.class; 
            }
            @Override
            public String message() { return "Invalid password strength"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 8; }
            @Override
            public int maxLength() { return 128; }
            @Override
            public boolean requireUppercase() { return true; }
            @Override
            public boolean requireLowercase() { return true; }
            @Override
            public boolean requireDigit() { return true; }
            @Override
            public boolean requireSpecialChar() { return true; }
            @Override
            public int minCharTypes() { return 3; }
            @Override
            public boolean rejectCommonPasswords() { return true; }
        });
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyInput() {
        assertThat(validator.isValid("", null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "Password1!, true",     // Valid password with all required character types
            "Abcdef1!, true",       // Valid password with all required character types
            "ABCDEF1!, false",      // Missing lowercase
            "abcdef1!, false",      // Missing uppercase
            "Abcdefgh!, false",     // Missing digit
            "Abcdefg1, false",      // Missing special character
            "Abc1!, false",         // Too short
            "password, false",      // Common password and missing required character types
            "12345678, false",      // Common password and missing required character types
            "qwerty123, false"      // Common password and missing required character types
    })
    void shouldValidatePasswordsWithDefaultSettings(String password, boolean expected) {
        assertThat(validator.isValid(password, null)).isEqualTo(expected);
    }

    @Test
    void shouldValidatePasswordLength() {
        // Default validator requires minimum 8 characters
        assertThat(validator.isValid("Abcd1!", null)).isFalse(); // Too short
        assertThat(validator.isValid("Abcdef1!", null)).isTrue(); // Exactly 8 characters

        // Create validator with custom length constraints (12-16 characters)
        PasswordStrengthValidator customLengthValidator = new PasswordStrengthValidator();
        customLengthValidator.initialize(new org.fireflyframework.annotations.ValidPasswordStrength() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPasswordStrength.class; 
            }
            @Override
            public String message() { return "Invalid password strength"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 12; }
            @Override
            public int maxLength() { return 16; }
            @Override
            public boolean requireUppercase() { return true; }
            @Override
            public boolean requireLowercase() { return true; }
            @Override
            public boolean requireDigit() { return true; }
            @Override
            public boolean requireSpecialChar() { return true; }
            @Override
            public int minCharTypes() { return 3; }
            @Override
            public boolean rejectCommonPasswords() { return true; }
        });

        assertThat(customLengthValidator.isValid("Abcdef1!", null)).isFalse(); // Too short
        assertThat(customLengthValidator.isValid("Abcdefghijk1!", null)).isTrue(); // Exactly 12 characters
        assertThat(customLengthValidator.isValid("Abcdefghijklmn1!", null)).isTrue(); // Exactly 16 characters
        assertThat(customLengthValidator.isValid("Abcdefghijklmnop1!", null)).isFalse(); // Too long
    }

    @Test
    void shouldValidateCharacterTypeRequirements() {
        // Default validator requires uppercase, lowercase, digit, and special character
        assertThat(validator.isValid("Abcdefg1!", null)).isTrue(); // All required character types
        assertThat(validator.isValid("ABCDEFG1!", null)).isFalse(); // Missing lowercase
        assertThat(validator.isValid("abcdefg1!", null)).isFalse(); // Missing uppercase
        assertThat(validator.isValid("Abcdefgh!", null)).isFalse(); // Missing digit
        assertThat(validator.isValid("Abcdefg1", null)).isFalse(); // Missing special character

        // Create validator with custom character type requirements (only require uppercase and digit)
        PasswordStrengthValidator customCharTypesValidator = new PasswordStrengthValidator();
        customCharTypesValidator.initialize(new org.fireflyframework.annotations.ValidPasswordStrength() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPasswordStrength.class; 
            }
            @Override
            public String message() { return "Invalid password strength"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 8; }
            @Override
            public int maxLength() { return 128; }
            @Override
            public boolean requireUppercase() { return true; }
            @Override
            public boolean requireLowercase() { return false; }
            @Override
            public boolean requireDigit() { return true; }
            @Override
            public boolean requireSpecialChar() { return false; }
            @Override
            public int minCharTypes() { return 2; }
            @Override
            public boolean rejectCommonPasswords() { return true; }
        });

        assertThat(customCharTypesValidator.isValid("ABCDEFG1", null)).isTrue(); // Has uppercase and digit
        assertThat(customCharTypesValidator.isValid("abcdefg1", null)).isFalse(); // Missing uppercase
        assertThat(customCharTypesValidator.isValid("ABCDEFGH", null)).isFalse(); // Missing digit
    }

    @Test
    void shouldValidateMinCharTypes() {
        // Create validator with custom min character types (require only 2 character types)
        PasswordStrengthValidator minCharTypesValidator = new PasswordStrengthValidator();
        minCharTypesValidator.initialize(new org.fireflyframework.annotations.ValidPasswordStrength() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPasswordStrength.class; 
            }
            @Override
            public String message() { return "Invalid password strength"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 8; }
            @Override
            public int maxLength() { return 128; }
            @Override
            public boolean requireUppercase() { return true; }
            @Override
            public boolean requireLowercase() { return false; }
            @Override
            public boolean requireDigit() { return true; }
            @Override
            public boolean requireSpecialChar() { return false; }
            @Override
            public int minCharTypes() { return 2; }
            @Override
            public boolean rejectCommonPasswords() { return true; }
        });

        assertThat(minCharTypesValidator.isValid("Abcdefg1", null)).isTrue(); // 3 character types (uppercase, lowercase, digit)
        assertThat(minCharTypesValidator.isValid("ABCDEFG1", null)).isTrue(); // 2 character types (uppercase, digit)

        // Create validator with custom min character types (require all 4 character types)
        PasswordStrengthValidator allCharTypesValidator = new PasswordStrengthValidator();
        allCharTypesValidator.initialize(new org.fireflyframework.annotations.ValidPasswordStrength() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPasswordStrength.class; 
            }
            @Override
            public String message() { return "Invalid password strength"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 8; }
            @Override
            public int maxLength() { return 128; }
            @Override
            public boolean requireUppercase() { return true; }
            @Override
            public boolean requireLowercase() { return true; }
            @Override
            public boolean requireDigit() { return true; }
            @Override
            public boolean requireSpecialChar() { return true; }
            @Override
            public int minCharTypes() { return 4; }
            @Override
            public boolean rejectCommonPasswords() { return true; }
        });

        assertThat(allCharTypesValidator.isValid("Abcdefg1", null)).isFalse(); // 3 character types
        assertThat(allCharTypesValidator.isValid("Abcdefg1!", null)).isTrue(); // All 4 character types
    }

    @Test
    void shouldRejectCommonPasswords() {
        // Default validator rejects common passwords
        assertThat(validator.isValid("password1!", null)).isFalse(); // Common password with added characters
        assertThat(validator.isValid("Password1!", null)).isTrue(); // Not a common password

        // Create validator that doesn't reject common passwords
        PasswordStrengthValidator noRejectCommonValidator = new PasswordStrengthValidator();
        noRejectCommonValidator.initialize(new org.fireflyframework.annotations.ValidPasswordStrength() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPasswordStrength.class; 
            }
            @Override
            public String message() { return "Invalid password strength"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 8; }
            @Override
            public int maxLength() { return 128; }
            @Override
            public boolean requireUppercase() { return false; }
            @Override
            public boolean requireLowercase() { return true; }
            @Override
            public boolean requireDigit() { return true; }
            @Override
            public boolean requireSpecialChar() { return true; }
            @Override
            public int minCharTypes() { return 3; }
            @Override
            public boolean rejectCommonPasswords() { return false; }
        });

        // Still needs to meet character type requirements
        assertThat(noRejectCommonValidator.isValid("Password1!", null)).isTrue();
        assertThat(noRejectCommonValidator.isValid("password1!", null)).isTrue(); // Common password but meets requirements
    }

    @Test
    void shouldDetectCommonPassword() {
        // Common passwords
        assertThat(validator.isCommonPassword("password")).isTrue();
        assertThat(validator.isCommonPassword("123456")).isTrue();
        assertThat(validator.isCommonPassword("qwerty")).isTrue();
        assertThat(validator.isCommonPassword("admin")).isTrue();

        // Non-common passwords
        assertThat(validator.isCommonPassword("Tr0ub4dor&3")).isFalse();
        assertThat(validator.isCommonPassword("correcthorsebatterystaple")).isFalse();

        // Case insensitive
        assertThat(validator.isCommonPassword("PASSWORD")).isTrue();

        // Invalid inputs
        assertThat(validator.isCommonPassword(null)).isTrue();
        assertThat(validator.isCommonPassword("")).isTrue();
    }

    @Test
    void shouldCalculateStrengthScore() {
        // Very weak passwords
        assertThat(validator.calculateStrengthScore("123456")).isLessThan(20);

        // Weak passwords
        assertThat(validator.calculateStrengthScore("password1")).isBetween(20, 39);

        // Medium strength passwords
        assertThat(validator.calculateStrengthScore("Password1")).isBetween(40, 59);

        // Strong passwords
        assertThat(validator.calculateStrengthScore("Password1!")).isBetween(60, 79);

        // Very strong passwords
        assertThat(validator.calculateStrengthScore("Tr0ub4dor&3X9Z")).isGreaterThanOrEqualTo(80);

        // Invalid inputs
        assertThat(validator.calculateStrengthScore(null)).isEqualTo(-1);
        assertThat(validator.calculateStrengthScore("")).isEqualTo(-1);
    }

    @Test
    void shouldCategorizePasswordStrength() {
        // Very weak passwords
        assertThat(validator.getStrengthCategory("123456")).isEqualTo(PasswordStrengthValidator.PasswordStrength.VERY_WEAK);

        // Weak passwords
        assertThat(validator.getStrengthCategory("password1")).isEqualTo(PasswordStrengthValidator.PasswordStrength.WEAK);

        // Medium strength passwords
        assertThat(validator.getStrengthCategory("Password1")).isEqualTo(PasswordStrengthValidator.PasswordStrength.MEDIUM);

        // Strong passwords
        assertThat(validator.getStrengthCategory("Password1!")).isEqualTo(PasswordStrengthValidator.PasswordStrength.STRONG);

        // Very strong passwords
        assertThat(validator.getStrengthCategory("Tr0ub4dor&3X9Z")).isEqualTo(PasswordStrengthValidator.PasswordStrength.VERY_STRONG);

        // Invalid inputs
        assertThat(validator.getStrengthCategory(null)).isNull();
        assertThat(validator.getStrengthCategory("")).isNull();
    }

    @Test
    void shouldGenerateSecurePassword() {
        // Generate passwords of different lengths
        String password8 = validator.generateSecurePassword(8);
        String password12 = validator.generateSecurePassword(12);
        String password16 = validator.generateSecurePassword(16);

        // Check length
        assertThat(password8).hasSize(8);
        assertThat(password12).hasSize(12);
        assertThat(password16).hasSize(16);

        // Check that generated passwords are valid
        assertThat(validator.isValid(password8, null)).isTrue();
        assertThat(validator.isValid(password12, null)).isTrue();
        assertThat(validator.isValid(password16, null)).isTrue();

        // Check that generated passwords are not common
        assertThat(validator.isCommonPassword(password8)).isFalse();
        assertThat(validator.isCommonPassword(password12)).isFalse();
        assertThat(validator.isCommonPassword(password16)).isFalse();

        // Check that generated passwords have required character types
        assertThat(password8).matches(".*[A-Z].*"); // Uppercase
        assertThat(password8).matches(".*[a-z].*"); // Lowercase
        assertThat(password8).matches(".*\\d.*"); // Digit
        assertThat(password8).matches(".*[^a-zA-Z0-9].*"); // Special character

        // Invalid length
        assertThat(validator.generateSecurePassword(7)).isNull(); // Too short
        assertThat(validator.generateSecurePassword(129)).isNull(); // Too long
    }
}
