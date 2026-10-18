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
 * Unit tests for {@link InterestRateValidator}.
 */
class InterestRateValidatorTest {

    private InterestRateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InterestRateValidator();
        // Initialize with default values (min=0, max=100, decimalPlaces=4, allowZero=true)
        validator.initialize(new org.fireflyframework.annotations.ValidInterestRate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidInterestRate.class; 
            }
            @Override
            public String message() { return "Invalid interest rate"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return 100.0; }
            @Override
            public int decimalPlaces() { return 4; }
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
            "0.0, true",       // Zero rate (allowed by default)
            "1.0, true",       // Positive integer
            "1.5, true",       // Positive decimal
            "5.25, true",      // Typical interest rate
            "0.1, true",       // Small positive rate
            "0.0001, true",    // Smallest valid rate with 4 decimal places
            "0.00001, false",  // Too many decimal places
            "-1.0, false",     // Negative rate
            "100.0, true",     // Maximum allowed rate
            "100.0001, false", // Above maximum
            "99.9999, true"    // Just below maximum
    })
    void shouldValidateRatesWithDefaultSettings(double rate, boolean expected) {
        assertThat(validator.isValid(rate, null)).isEqualTo(expected);
    }

    @Test
    void shouldValidateDecimalPlaces() {
        // Default validator allows 4 decimal places
        assertThat(validator.isValid(5.2500, null)).isTrue();
        assertThat(validator.isValid(5.25, null)).isTrue();
        assertThat(validator.isValid(5.0, null)).isTrue();
        assertThat(validator.isValid(5.25001, null)).isFalse(); // Too many decimal places
        
        // Create validator with 2 decimal places
        InterestRateValidator twoDecimalValidator = new InterestRateValidator();
        twoDecimalValidator.initialize(new org.fireflyframework.annotations.ValidInterestRate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidInterestRate.class; 
            }
            @Override
            public String message() { return "Invalid interest rate"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return 100.0; }
            @Override
            public int decimalPlaces() { return 2; }
            @Override
            public boolean allowZero() { return true; }
        });
        
        assertThat(twoDecimalValidator.isValid(5.25, null)).isTrue();
        assertThat(twoDecimalValidator.isValid(5.0, null)).isTrue();
        assertThat(twoDecimalValidator.isValid(5.251, null)).isFalse(); // Too many decimal places
    }

    @Test
    void shouldValidateMinMaxConstraints() {
        // Create validator with min=1, max=20
        InterestRateValidator minMaxValidator = new InterestRateValidator();
        minMaxValidator.initialize(new org.fireflyframework.annotations.ValidInterestRate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidInterestRate.class; 
            }
            @Override
            public String message() { return "Invalid interest rate"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public double min() { return 1.0; }
            @Override
            public double max() { return 20.0; }
            @Override
            public int decimalPlaces() { return 4; }
            @Override
            public boolean allowZero() { return true; }
        });
        
        assertThat(minMaxValidator.isValid(0.99, null)).isFalse(); // Below min
        assertThat(minMaxValidator.isValid(1.0, null)).isTrue();   // At min
        assertThat(minMaxValidator.isValid(10.0, null)).isTrue();  // Between min and max
        assertThat(minMaxValidator.isValid(20.0, null)).isTrue();  // At max
        assertThat(minMaxValidator.isValid(20.01, null)).isFalse(); // Above max
    }

    @Test
    void shouldValidateZeroRate() {
        // Default validator allows zero
        assertThat(validator.isValid(0.0, null)).isTrue();
        
        // Create validator that doesn't allow zero
        InterestRateValidator noZeroValidator = new InterestRateValidator();
        noZeroValidator.initialize(new org.fireflyframework.annotations.ValidInterestRate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidInterestRate.class; 
            }
            @Override
            public String message() { return "Invalid interest rate"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return 100.0; }
            @Override
            public int decimalPlaces() { return 4; }
            @Override
            public boolean allowZero() { return false; }
        });
        
        assertThat(noZeroValidator.isValid(0.0, null)).isFalse();
        assertThat(noZeroValidator.isValid(0.0001, null)).isTrue();
    }

    @Test
    void shouldFormatAsPercentage() {
        assertThat(validator.formatAsPercentage(5.25)).contains("5.25%");
        assertThat(validator.formatAsPercentage(0.5)).contains("0.5%");
        assertThat(validator.formatAsPercentage(10.0)).contains("10%");
        
        // Invalid input
        assertThat(validator.formatAsPercentage(null)).isNull();
    }

    @Test
    void shouldRoundRate() {
        // Round to 4 decimal places (default)
        BigDecimal roundedRate = validator.round(5.25678);
        assertThat(roundedRate).isEqualTo(new BigDecimal("5.2568"));
        
        // Round to 2 decimal places
        InterestRateValidator twoDecimalValidator = new InterestRateValidator();
        twoDecimalValidator.initialize(new org.fireflyframework.annotations.ValidInterestRate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidInterestRate.class; 
            }
            @Override
            public String message() { return "Invalid interest rate"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public double min() { return 0.0; }
            @Override
            public double max() { return 100.0; }
            @Override
            public int decimalPlaces() { return 2; }
            @Override
            public boolean allowZero() { return true; }
        });
        
        BigDecimal roundedToTwoDecimals = twoDecimalValidator.round(5.25678);
        assertThat(roundedToTwoDecimals).isEqualTo(new BigDecimal("5.26")); // Rounded up
        
        // Invalid input
        assertThat(validator.round(null)).isNull();
    }

    @Test
    void shouldConvertAnnualToMonthly() {
        // 12% annual = 1% monthly
        BigDecimal monthlyRate = validator.annualToMonthly(12.0);
        assertThat(monthlyRate).isEqualTo(new BigDecimal("1.0000"));
        
        // 6% annual = 0.5% monthly
        monthlyRate = validator.annualToMonthly(6.0);
        assertThat(monthlyRate).isEqualTo(new BigDecimal("0.5000"));
        
        // Invalid input
        assertThat(validator.annualToMonthly(null)).isNull();
    }

    @Test
    void shouldConvertAnnualToDaily() {
        // 365% annual = 1% daily
        BigDecimal dailyRate = validator.annualToDaily(365.0);
        assertThat(dailyRate).isEqualTo(new BigDecimal("1.0000"));
        
        // 36.5% annual = 0.1% daily
        dailyRate = validator.annualToDaily(36.5);
        assertThat(dailyRate).isEqualTo(new BigDecimal("0.1000"));
        
        // Invalid input
        assertThat(validator.annualToDaily(null)).isNull();
    }

    @Test
    void shouldCalculateEffectiveRate() {
        // 10% nominal rate compounded annually (1 time per year) = 10% effective rate
        BigDecimal effectiveRate = validator.calculateEffectiveRate(10.0, 1);
        assertThat(effectiveRate).isEqualTo(new BigDecimal("10.0000"));
        
        // 10% nominal rate compounded quarterly (4 times per year) ≈ 10.38% effective rate
        effectiveRate = validator.calculateEffectiveRate(10.0, 4);
        assertThat(effectiveRate).isEqualByComparingTo(new BigDecimal("10.38"));
        
        // 10% nominal rate compounded monthly (12 times per year) ≈ 10.47% effective rate
        effectiveRate = validator.calculateEffectiveRate(10.0, 12);
        assertThat(effectiveRate).isEqualByComparingTo(new BigDecimal("10.47"));
        
        // Invalid inputs
        assertThat(validator.calculateEffectiveRate(null, 12)).isNull();
        assertThat(validator.calculateEffectiveRate(10.0, 0)).isNull();
        assertThat(validator.calculateEffectiveRate(10.0, -1)).isNull();
    }
}