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
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PINValidator}.
 */
class PINValidatorTest {

    private PINValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PINValidator();
        // Initialize with default values (minLength=4, maxLength=6, numericOnly=true, rejectCommonPINs=true)
        validator.initialize(new org.fireflyframework.annotations.ValidPIN() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPIN.class; 
            }
            @Override
            public String message() { return "Invalid PIN"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 4; }
            @Override
            public int maxLength() { return 6; }
            @Override
            public boolean numericOnly() { return true; }
            @Override
            public boolean rejectCommonPINs() { return true; }
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
    @ValueSource(strings = {
            "1234",     // 4 digits
            "123456",   // 6 digits
            "9876",     // 4 digits
            "654321"    // 6 digits
    })
    void shouldReturnTrueForValidPINs(String pin) {
        // Note: These PINs are valid in terms of format but might be rejected as common PINs
        // We'll test common PIN rejection separately
        PINValidator noRejectCommonValidator = new PINValidator();
        noRejectCommonValidator.initialize(new org.fireflyframework.annotations.ValidPIN() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPIN.class; 
            }
            @Override
            public String message() { return "Invalid PIN"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 4; }
            @Override
            public int maxLength() { return 6; }
            @Override
            public boolean numericOnly() { return true; }
            @Override
            public boolean rejectCommonPINs() { return false; }
        });
        
        assertThat(noRejectCommonValidator.isValid(pin, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123",      // Too short
            "1234567",  // Too long
            "12AB",     // Contains letters
            "12-34",    // Contains special characters
            "12 34"     // Contains spaces
    })
    void shouldReturnFalseForInvalidPINs(String pin) {
        assertThat(validator.isValid(pin, null)).isFalse();
    }

    @Test
    void shouldValidatePINLength() {
        // Default validator accepts 4-6 digits
        assertThat(validator.isValid("1234", null)).isTrue();
        assertThat(validator.isValid("12345", null)).isTrue();
        assertThat(validator.isValid("123456", null)).isTrue();
        
        assertThat(validator.isValid("123", null)).isFalse(); // Too short
        assertThat(validator.isValid("1234567", null)).isFalse(); // Too long
        
        // Create validator with custom length constraints (6-8 digits)
        PINValidator customLengthValidator = new PINValidator();
        customLengthValidator.initialize(new org.fireflyframework.annotations.ValidPIN() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPIN.class; 
            }
            @Override
            public String message() { return "Invalid PIN"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 6; }
            @Override
            public int maxLength() { return 8; }
            @Override
            public boolean numericOnly() { return true; }
            @Override
            public boolean rejectCommonPINs() { return true; }
        });
        
        assertThat(customLengthValidator.isValid("123456", null)).isTrue();
        assertThat(customLengthValidator.isValid("1234567", null)).isTrue();
        assertThat(customLengthValidator.isValid("12345678", null)).isTrue();
        
        assertThat(customLengthValidator.isValid("12345", null)).isFalse(); // Too short
        assertThat(customLengthValidator.isValid("123456789", null)).isFalse(); // Too long
    }

    @Test
    void shouldValidateNumericOnly() {
        // Default validator requires numeric only
        assertThat(validator.isValid("1234", null)).isTrue();
        assertThat(validator.isValid("12AB", null)).isFalse(); // Contains letters
        
        // Create validator that allows non-numeric characters
        PINValidator alphanumericValidator = new PINValidator();
        alphanumericValidator.initialize(new org.fireflyframework.annotations.ValidPIN() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPIN.class; 
            }
            @Override
            public String message() { return "Invalid PIN"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 4; }
            @Override
            public int maxLength() { return 6; }
            @Override
            public boolean numericOnly() { return false; }
            @Override
            public boolean rejectCommonPINs() { return true; }
        });
        
        assertThat(alphanumericValidator.isValid("12AB", null)).isTrue();
        assertThat(alphanumericValidator.isValid("A1B2", null)).isTrue();
    }

    @Test
    void shouldRejectCommonPINs() {
        // Common PINs that should be rejected
        assertThat(validator.isValid("1234", null)).isFalse(); // Sequential digits
        assertThat(validator.isValid("4321", null)).isFalse(); // Reverse sequential digits
        assertThat(validator.isValid("0000", null)).isFalse(); // Repeated digits
        assertThat(validator.isValid("1111", null)).isFalse(); // Repeated digits
        assertThat(validator.isValid("1212", null)).isFalse(); // Repeated pattern
        
        // Create validator that doesn't reject common PINs
        PINValidator noRejectCommonValidator = new PINValidator();
        noRejectCommonValidator.initialize(new org.fireflyframework.annotations.ValidPIN() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidPIN.class; 
            }
            @Override
            public String message() { return "Invalid PIN"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public int minLength() { return 4; }
            @Override
            public int maxLength() { return 6; }
            @Override
            public boolean numericOnly() { return true; }
            @Override
            public boolean rejectCommonPINs() { return false; }
        });
        
        assertThat(noRejectCommonValidator.isValid("1234", null)).isTrue();
        assertThat(noRejectCommonValidator.isValid("4321", null)).isTrue();
        assertThat(noRejectCommonValidator.isValid("0000", null)).isTrue();
    }

    @Test
    void shouldDetectWeakPIN() {
        // Weak PINs
        assertThat(validator.isWeakPIN("1234")).isTrue(); // Sequential digits
        assertThat(validator.isWeakPIN("4321")).isTrue(); // Reverse sequential digits
        assertThat(validator.isWeakPIN("0000")).isTrue(); // Repeated digits
        assertThat(validator.isWeakPIN("1111")).isTrue(); // Repeated digits
        assertThat(validator.isWeakPIN("1212")).isTrue(); // Repeated pattern
        
        // Stronger PINs
        assertThat(validator.isWeakPIN("1357")).isFalse(); // Non-sequential, non-repeating
        assertThat(validator.isWeakPIN("2580")).isFalse(); // Non-sequential, non-repeating
        assertThat(validator.isWeakPIN("9517")).isFalse(); // Non-sequential, non-repeating
        
        // Invalid inputs
        assertThat(validator.isWeakPIN(null)).isTrue();
        assertThat(validator.isWeakPIN("")).isTrue();
    }

    @Test
    void shouldGenerateSecurePIN() {
        // Generate 4-digit PIN
        String pin4 = validator.generateSecurePIN(4);
        assertThat(pin4).hasSize(4);
        assertThat(pin4).matches("\\d{4}");
        assertThat(validator.isWeakPIN(pin4)).isFalse();
        
        // Generate 6-digit PIN
        String pin6 = validator.generateSecurePIN(6);
        assertThat(pin6).hasSize(6);
        assertThat(pin6).matches("\\d{6}");
        assertThat(validator.isWeakPIN(pin6)).isFalse();
        
        // Invalid length
        assertThat(validator.generateSecurePIN(3)).isNull(); // Too short
        assertThat(validator.generateSecurePIN(7)).isNull(); // Too long
    }

    @Test
    void shouldMaskPIN() {
        assertThat(validator.maskPIN("1234")).isEqualTo("***4");
        assertThat(validator.maskPIN("123456")).isEqualTo("*****6");
        
        // Invalid inputs
        assertThat(validator.maskPIN(null)).isNull();
        assertThat(validator.maskPIN("")).isNull();
    }
}