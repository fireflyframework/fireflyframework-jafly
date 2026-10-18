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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AmountValidator}.
 */
class AmountValidatorTest {

    private AmountValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AmountValidator();
        // Initialize with default values (EUR currency, min=0, max=Double.MAX_VALUE, allowZero=true)
        validator.initialize(new org.fireflyframework.annotations.ValidAmount() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidAmount.class; 
            }
            @Override
            public String message() { return "Invalid amount"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String currency() { return "EUR"; }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return Double.MAX_VALUE; }
            @Override
            public boolean allowZero() { return true; }
        });
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, true",      // Zero amount (allowed by default)
            "1.0, true",      // Positive integer
            "1.5, true",      // Positive decimal
            "1000.00, true",  // Larger amount with 2 decimal places
            "0.1, true",      // Small positive amount
            "0.01, true",     // Smallest valid amount for EUR (2 decimal places)
            "0.001, false",   // Too many decimal places for EUR
            "-1.0, false",    // Negative amount
            "1000000.00, true" // Large amount
    })
    void shouldValidateAmountsWithDefaultSettings(double amount, boolean expected) {
        assertThat(validator.isValid(amount, null)).isEqualTo(expected);
    }

    @Test
    void shouldValidateAmountWithCurrencySpecificDecimalPlaces() {
        // EUR allows 2 decimal places
        assertThat(validator.isValid(100.00, null)).isTrue();
        assertThat(validator.isValid(100.50, null)).isTrue();
        assertThat(validator.isValid(100.555, null)).isFalse(); // Too many decimal places
        
        // JPY allows 0 decimal places
        AmountValidator jpyValidator = new AmountValidator();
        jpyValidator.initialize(new org.fireflyframework.annotations.ValidAmount() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidAmount.class; 
            }
            @Override
            public String message() { return "Invalid amount"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String currency() { return "JPY"; }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return Double.MAX_VALUE; }
            @Override
            public boolean allowZero() { return true; }
        });
        
        assertThat(jpyValidator.isValid(100, null)).isTrue();
        assertThat(jpyValidator.isValid(100.0, null)).isTrue();
        assertThat(jpyValidator.isValid(100.5, null)).isFalse(); // JPY doesn't allow decimal places
    }

    @Test
    void shouldValidateMinMaxConstraints() {
        // Create validator with min=10, max=1000
        AmountValidator minMaxValidator = new AmountValidator();
        minMaxValidator.initialize(new org.fireflyframework.annotations.ValidAmount() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidAmount.class; 
            }
            @Override
            public String message() { return "Invalid amount"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String currency() { return "EUR"; }
            @Override
            public double min() { return 10.0; }
            @Override
            public double max() { return 1000.0; }
            @Override
            public boolean allowZero() { return true; }
        });
        
        assertThat(minMaxValidator.isValid(9.99, null)).isFalse(); // Below min
        assertThat(minMaxValidator.isValid(10.0, null)).isTrue();  // At min
        assertThat(minMaxValidator.isValid(500.0, null)).isTrue(); // Between min and max
        assertThat(minMaxValidator.isValid(1000.0, null)).isTrue(); // At max
        assertThat(minMaxValidator.isValid(1000.01, null)).isFalse(); // Above max
    }

    @Test
    void shouldValidateZeroAmount() {
        // Default validator allows zero
        assertThat(validator.isValid(0.0, null)).isTrue();
        
        // Create validator that doesn't allow zero
        AmountValidator noZeroValidator = new AmountValidator();
        noZeroValidator.initialize(new org.fireflyframework.annotations.ValidAmount() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidAmount.class; 
            }
            @Override
            public String message() { return "Invalid amount"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String currency() { return "EUR"; }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return Double.MAX_VALUE; }
            @Override
            public boolean allowZero() { return false; }
        });
        
        assertThat(noZeroValidator.isValid(0.0, null)).isFalse();
        assertThat(noZeroValidator.isValid(0.01, null)).isTrue();
    }

    @Test
    void shouldFormatAmount() {
        // EUR format
        assertThat(validator.format(1234.56, "EUR")).contains("1,234.56").contains("€");
        
        // USD format
        assertThat(validator.format(1234.56, "USD")).contains("1,234.56").contains("$");
        
        // JPY format (no decimal places)
        assertThat(validator.format(1234, "JPY")).contains("1,234").contains("¥");
        
        // Invalid inputs
        assertThat(validator.format(null, "EUR")).isNull();
        assertThat(validator.format(1234.56, null)).isNull();
        assertThat(validator.format(1234.56, "")).isNull();
    }

    @Test
    void shouldRoundAmount() {
        // EUR (2 decimal places)
        BigDecimal eurAmount = validator.round(1234.567, "EUR");
        assertThat(eurAmount).isEqualTo(new BigDecimal("1234.57")); // Rounded to 2 decimal places
        
        // JPY (0 decimal places)
        BigDecimal jpyAmount = validator.round(1234.56, "JPY");
        assertThat(jpyAmount).isEqualTo(new BigDecimal("1235")); // Rounded to 0 decimal places
        
        // Invalid inputs
        assertThat(validator.round(null, "EUR")).isNull();
        assertThat(validator.round(1234.56, null)).isNull();
        assertThat(validator.round(1234.56, "")).isNull();
    }

    @Test
    void shouldGetDecimalPlaces() {
        assertThat(validator.getDecimalPlaces("EUR")).isEqualTo(2);
        assertThat(validator.getDecimalPlaces("JPY")).isEqualTo(0);
        assertThat(validator.getDecimalPlaces("BTC")).isEqualTo(8);
        
        // Default for unknown currency
        assertThat(validator.getDecimalPlaces("XYZ")).isEqualTo(2);
        
        // Null or empty input
        assertThat(validator.getDecimalPlaces(null)).isEqualTo(2);
        assertThat(validator.getDecimalPlaces("")).isEqualTo(2);
    }
}