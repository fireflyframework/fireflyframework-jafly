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

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DateValidator}.
 */
class DateValidatorTest {

    private DateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DateValidator();
        // Initialize with default values (pattern="yyyy-MM-dd", min="", max="", allowFuture=true, allowPast=true)
        validator.initialize(new org.fireflyframework.annotations.ValidDate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDate.class; 
            }
            @Override
            public String message() { return "Invalid date"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return true; }
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
            "2023-01-01, true",   // Valid ISO date
            "2023-12-31, true",   // Valid ISO date
            "2023-02-29, false",  // Invalid date (February 29 in non-leap year)
            "2024-02-29, true",   // Valid date (February 29 in leap year)
            "2023-13-01, false",  // Invalid month
            "2023-01-32, false",  // Invalid day
            "20230101, false",    // Wrong format
            "01/01/2023, false",  // Wrong format
            "2023/01/01, false"   // Wrong format
    })
    void shouldValidateDatesWithDefaultPattern(String dateStr, boolean expected) {
        assertThat(validator.isValid(dateStr, null)).isEqualTo(expected);
    }

    @Test
    void shouldValidateDifferentDateFormats() {
        // Create validator with dd/MM/yyyy pattern
        DateValidator ddMMyyyyValidator = new DateValidator();
        ddMMyyyyValidator.initialize(new org.fireflyframework.annotations.ValidDate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDate.class; 
            }
            @Override
            public String message() { return "Invalid date"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "dd/MM/yyyy"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return true; }
        });
        
        assertThat(ddMMyyyyValidator.isValid("01/01/2023", null)).isTrue();
        assertThat(ddMMyyyyValidator.isValid("31/12/2023", null)).isTrue();
        assertThat(ddMMyyyyValidator.isValid("29/02/2023", null)).isFalse(); // Invalid date
        assertThat(ddMMyyyyValidator.isValid("2023-01-01", null)).isFalse(); // Wrong format
    }

    @Test
    void shouldValidateMinMaxConstraints() {
        // Create validator with min and max constraints
        DateValidator minMaxValidator = new DateValidator();
        minMaxValidator.initialize(new org.fireflyframework.annotations.ValidDate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDate.class; 
            }
            @Override
            public String message() { return "Invalid date"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd"; }
            @Override
            public String min() { return "2023-01-01"; }
            @Override
            public String max() { return "2023-12-31"; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return true; }
        });
        
        assertThat(minMaxValidator.isValid("2022-12-31", null)).isFalse(); // Before min
        assertThat(minMaxValidator.isValid("2023-01-01", null)).isTrue();  // At min
        assertThat(minMaxValidator.isValid("2023-06-15", null)).isTrue();  // Between min and max
        assertThat(minMaxValidator.isValid("2023-12-31", null)).isTrue();  // At max
        assertThat(minMaxValidator.isValid("2024-01-01", null)).isFalse(); // After max
    }

    @Test
    void shouldValidateFuturePastConstraints() throws Exception {
        // Get current date in yyyy-MM-dd format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new Date());
        
        // Calculate a date in the past (1 year ago)
        Date pastDate = new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000);
        String pastDateStr = sdf.format(pastDate);
        
        // Calculate a date in the future (1 year from now)
        Date futureDate = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        String futureDateStr = sdf.format(futureDate);
        
        // Default validator allows both past and future dates
        assertThat(validator.isValid(pastDateStr, null)).isTrue();
        assertThat(validator.isValid(today, null)).isTrue();
        assertThat(validator.isValid(futureDateStr, null)).isTrue();
        
        // Create validator that doesn't allow future dates
        DateValidator noPastValidator = new DateValidator();
        noPastValidator.initialize(new org.fireflyframework.annotations.ValidDate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDate.class; 
            }
            @Override
            public String message() { return "Invalid date"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return false; }
        });
        
        assertThat(noPastValidator.isValid(pastDateStr, null)).isFalse();
        assertThat(noPastValidator.isValid(today, null)).isTrue();
        assertThat(noPastValidator.isValid(futureDateStr, null)).isTrue();
        
        // Create validator that doesn't allow past dates
        DateValidator noFutureValidator = new DateValidator();
        noFutureValidator.initialize(new org.fireflyframework.annotations.ValidDate() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDate.class; 
            }
            @Override
            public String message() { return "Invalid date"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return false; }
            @Override
            public boolean allowPast() { return true; }
        });
        
        assertThat(noFutureValidator.isValid(pastDateStr, null)).isTrue();
        assertThat(noFutureValidator.isValid(today, null)).isTrue();
        assertThat(noFutureValidator.isValid(futureDateStr, null)).isFalse();
    }

    @Test
    void shouldValidateWithCustomFormat() {
        assertThat(validator.isValidFormat("2023-01-01", "yyyy-MM-dd")).isTrue();
        assertThat(validator.isValidFormat("01/01/2023", "dd/MM/yyyy")).isTrue();
        assertThat(validator.isValidFormat("20230101", "yyyyMMdd")).isTrue();
        
        assertThat(validator.isValidFormat("2023-01-01", "dd/MM/yyyy")).isFalse();
        assertThat(validator.isValidFormat("01/01/2023", "yyyy-MM-dd")).isFalse();
        
        // Invalid inputs
        assertThat(validator.isValidFormat(null, "yyyy-MM-dd")).isFalse();
        assertThat(validator.isValidFormat("", "yyyy-MM-dd")).isFalse();
        assertThat(validator.isValidFormat("2023-01-01", null)).isFalse();
        assertThat(validator.isValidFormat("2023-01-01", "")).isFalse();
    }

    @Test
    void shouldFormatDate() throws Exception {
        // Create a date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = sdf.parse("2023-01-01");
        
        // Format with different patterns
        assertThat(validator.format(date, "yyyy-MM-dd")).isEqualTo("2023-01-01");
        assertThat(validator.format(date, "dd/MM/yyyy")).isEqualTo("01/01/2023");
        assertThat(validator.format(date, "MMMM d, yyyy")).isEqualTo("January 1, 2023");
        
        // Invalid inputs
        assertThat(validator.format(null, "yyyy-MM-dd")).isNull();
        assertThat(validator.format(date, null)).isNull();
        assertThat(validator.format(date, "")).isNull();
    }

    @Test
    void shouldParseDate() {
        // Parse with different patterns
        Date date1 = validator.parse("2023-01-01", "yyyy-MM-dd");
        Date date2 = validator.parse("01/01/2023", "dd/MM/yyyy");
        
        assertThat(date1).isNotNull();
        assertThat(date2).isNotNull();
        assertThat(date1).isEqualTo(date2);
        
        // Invalid inputs
        assertThat(validator.parse(null, "yyyy-MM-dd")).isNull();
        assertThat(validator.parse("", "yyyy-MM-dd")).isNull();
        assertThat(validator.parse("2023-01-01", null)).isNull();
        assertThat(validator.parse("2023-01-01", "")).isNull();
        assertThat(validator.parse("invalid-date", "yyyy-MM-dd")).isNull();
    }

    @Test
    void shouldConvertFormat() {
        assertThat(validator.convertFormat("2023-01-01", "yyyy-MM-dd", "dd/MM/yyyy")).isEqualTo("01/01/2023");
        assertThat(validator.convertFormat("01/01/2023", "dd/MM/yyyy", "yyyy-MM-dd")).isEqualTo("2023-01-01");
        assertThat(validator.convertFormat("2023-01-01", "yyyy-MM-dd", "MMMM d, yyyy")).isEqualTo("January 1, 2023");
        
        // Invalid inputs
        assertThat(validator.convertFormat(null, "yyyy-MM-dd", "dd/MM/yyyy")).isNull();
        assertThat(validator.convertFormat("", "yyyy-MM-dd", "dd/MM/yyyy")).isNull();
        assertThat(validator.convertFormat("2023-01-01", null, "dd/MM/yyyy")).isNull();
        assertThat(validator.convertFormat("2023-01-01", "", "dd/MM/yyyy")).isNull();
        assertThat(validator.convertFormat("2023-01-01", "yyyy-MM-dd", null)).isNull();
        assertThat(validator.convertFormat("2023-01-01", "yyyy-MM-dd", "")).isNull();
        assertThat(validator.convertFormat("invalid-date", "yyyy-MM-dd", "dd/MM/yyyy")).isNull();
    }

    @Test
    void shouldCheckFuturePastDates() throws Exception {
        // Get current date in yyyy-MM-dd format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        // Calculate a date in the past (1 year ago)
        Date pastDate = new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000);
        String pastDateStr = sdf.format(pastDate);
        
        // Calculate a date in the future (1 year from now)
        Date futureDate = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        String futureDateStr = sdf.format(futureDate);
        
        assertThat(validator.isFutureDate(futureDateStr, "yyyy-MM-dd")).isTrue();
        assertThat(validator.isFutureDate(pastDateStr, "yyyy-MM-dd")).isFalse();
        
        assertThat(validator.isPastDate(pastDateStr, "yyyy-MM-dd")).isTrue();
        assertThat(validator.isPastDate(futureDateStr, "yyyy-MM-dd")).isFalse();
        
        // Invalid inputs
        assertThat(validator.isFutureDate(null, "yyyy-MM-dd")).isFalse();
        assertThat(validator.isFutureDate("", "yyyy-MM-dd")).isFalse();
        assertThat(validator.isFutureDate("2023-01-01", null)).isFalse();
        assertThat(validator.isFutureDate("2023-01-01", "")).isFalse();
        assertThat(validator.isFutureDate("invalid-date", "yyyy-MM-dd")).isFalse();
        
        assertThat(validator.isPastDate(null, "yyyy-MM-dd")).isFalse();
        assertThat(validator.isPastDate("", "yyyy-MM-dd")).isFalse();
        assertThat(validator.isPastDate("2023-01-01", null)).isFalse();
        assertThat(validator.isPastDate("2023-01-01", "")).isFalse();
        assertThat(validator.isPastDate("invalid-date", "yyyy-MM-dd")).isFalse();
    }

    @Test
    void shouldCalculateDaysBetween() {
        assertThat(validator.daysBetween("2023-01-01", "2023-01-31", "yyyy-MM-dd")).isEqualTo(30);
        assertThat(validator.daysBetween("2023-01-31", "2023-01-01", "yyyy-MM-dd")).isEqualTo(30);
        assertThat(validator.daysBetween("2023-01-01", "2023-01-01", "yyyy-MM-dd")).isEqualTo(0);
        
        // Invalid inputs
        assertThat(validator.daysBetween(null, "2023-01-31", "yyyy-MM-dd")).isEqualTo(-1);
        assertThat(validator.daysBetween("2023-01-01", null, "yyyy-MM-dd")).isEqualTo(-1);
        assertThat(validator.daysBetween("2023-01-01", "2023-01-31", null)).isEqualTo(-1);
        assertThat(validator.daysBetween("invalid-date", "2023-01-31", "yyyy-MM-dd")).isEqualTo(-1);
        assertThat(validator.daysBetween("2023-01-01", "invalid-date", "yyyy-MM-dd")).isEqualTo(-1);
    }

    @Test
    void shouldAddDays() {
        assertThat(validator.addDays("2023-01-01", "yyyy-MM-dd", 30)).isEqualTo("2023-01-31");
        assertThat(validator.addDays("2023-01-31", "yyyy-MM-dd", -30)).isEqualTo("2023-01-01");
        assertThat(validator.addDays("2023-01-01", "yyyy-MM-dd", 0)).isEqualTo("2023-01-01");
        
        // Invalid inputs
        assertThat(validator.addDays(null, "yyyy-MM-dd", 30)).isNull();
        assertThat(validator.addDays("", "yyyy-MM-dd", 30)).isNull();
        assertThat(validator.addDays("2023-01-01", null, 30)).isNull();
        assertThat(validator.addDays("2023-01-01", "", 30)).isNull();
        assertThat(validator.addDays("invalid-date", "yyyy-MM-dd", 30)).isNull();
    }
}