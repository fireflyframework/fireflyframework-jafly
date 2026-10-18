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
 * Unit tests for {@link CVVValidator}.
 */
class CVVValidatorTest {

    private CVVValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CVVValidator();
        // Initialize with default values (cardType=ANY)
        validator.initialize(new org.fireflyframework.annotations.ValidCVV() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidCVV.class; 
            }
            @Override
            public String message() { return "Invalid CVV"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public org.fireflyframework.annotations.ValidCVV.CardType cardType() { 
                return org.fireflyframework.annotations.ValidCVV.CardType.ANY; 
            }
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
            "123",  // 3 digits (standard cards)
            "1234"  // 4 digits (American Express)
    })
    void shouldReturnTrueForValidCVVs(String cvv) {
        assertThat(validator.isValid(cvv, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12",    // Too short
            "12345", // Too long
            "12A",   // Contains letters
            "1-3",   // Contains special characters
            "1 2"    // Contains spaces
    })
    void shouldReturnFalseForInvalidCVVs(String cvv) {
        assertThat(validator.isValid(cvv, null)).isFalse();
    }

    @Test
    void shouldValidateStandardCardCVV() {
        // Create validator for standard cards (Visa, MasterCard, Discover)
        CVVValidator standardValidator = new CVVValidator();
        standardValidator.initialize(new org.fireflyframework.annotations.ValidCVV() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidCVV.class; 
            }
            @Override
            public String message() { return "Invalid CVV"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public org.fireflyframework.annotations.ValidCVV.CardType cardType() { 
                return org.fireflyframework.annotations.ValidCVV.CardType.STANDARD; 
            }
        });
        
        assertThat(standardValidator.isValid("123", null)).isTrue();
        assertThat(standardValidator.isValid("1234", null)).isFalse(); // Too long for standard cards
    }

    @Test
    void shouldValidateAmexCardCVV() {
        // Create validator for American Express cards
        CVVValidator amexValidator = new CVVValidator();
        amexValidator.initialize(new org.fireflyframework.annotations.ValidCVV() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidCVV.class; 
            }
            @Override
            public String message() { return "Invalid CVV"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public org.fireflyframework.annotations.ValidCVV.CardType cardType() { 
                return org.fireflyframework.annotations.ValidCVV.CardType.AMEX; 
            }
        });
        
        assertThat(amexValidator.isValid("1234", null)).isTrue();
        assertThat(amexValidator.isValid("123", null)).isFalse(); // Too short for Amex
    }

    @Test
    void shouldValidateForCardType() {
        // Test isValidForCardType method
        assertThat(validator.isValidForCardType("123", org.fireflyframework.annotations.ValidCVV.CardType.STANDARD)).isTrue();
        assertThat(validator.isValidForCardType("1234", org.fireflyframework.annotations.ValidCVV.CardType.AMEX)).isTrue();
        
        assertThat(validator.isValidForCardType("123", org.fireflyframework.annotations.ValidCVV.CardType.AMEX)).isFalse();
        assertThat(validator.isValidForCardType("1234", org.fireflyframework.annotations.ValidCVV.CardType.STANDARD)).isFalse();
        
        // ANY card type accepts both 3 and 4 digits
        assertThat(validator.isValidForCardType("123", org.fireflyframework.annotations.ValidCVV.CardType.ANY)).isTrue();
        assertThat(validator.isValidForCardType("1234", org.fireflyframework.annotations.ValidCVV.CardType.ANY)).isTrue();
        
        // Invalid inputs
        assertThat(validator.isValidForCardType(null, org.fireflyframework.annotations.ValidCVV.CardType.ANY)).isFalse();
        assertThat(validator.isValidForCardType("", org.fireflyframework.annotations.ValidCVV.CardType.ANY)).isFalse();
    }

    @Test
    void shouldValidateForCardNumber() {
        // Test isValidForCardNumber method
        // Visa (starts with 4) - should have 3-digit CVV
        assertThat(validator.isValidForCardNumber("123", "4111111111111111")).isTrue();
        assertThat(validator.isValidForCardNumber("1234", "4111111111111111")).isFalse();
        
        // American Express (starts with 34 or 37) - should have 4-digit CVV
        assertThat(validator.isValidForCardNumber("1234", "341111111111111")).isTrue();
        assertThat(validator.isValidForCardNumber("123", "341111111111111")).isFalse();
        
        // MasterCard (starts with 51-55) - should have 3-digit CVV
        assertThat(validator.isValidForCardNumber("123", "5111111111111111")).isTrue();
        assertThat(validator.isValidForCardNumber("1234", "5111111111111111")).isFalse();
        
        // Invalid inputs
        assertThat(validator.isValidForCardNumber(null, "4111111111111111")).isFalse();
        assertThat(validator.isValidForCardNumber("123", null)).isFalse();
        assertThat(validator.isValidForCardNumber("", "4111111111111111")).isFalse();
        assertThat(validator.isValidForCardNumber("123", "")).isFalse();
    }

    @Test
    void shouldMaskCVV() {
        assertThat(validator.maskCVV("123")).isEqualTo("***");
        assertThat(validator.maskCVV("1234")).isEqualTo("****");
        
        // Invalid inputs
        assertThat(validator.maskCVV(null)).isNull();
        assertThat(validator.maskCVV("")).isNull();
        assertThat(validator.maskCVV("12")).isNull(); // Too short
        assertThat(validator.maskCVV("12345")).isNull(); // Too long
    }
}