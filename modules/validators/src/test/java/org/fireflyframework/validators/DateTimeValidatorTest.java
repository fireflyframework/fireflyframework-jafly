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
 * Unit tests for {@link DateTimeValidator}.
 */
class DateTimeValidatorTest {

    private DateTimeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DateTimeValidator();
        // Initialize with default values
        validator.initialize(new org.fireflyframework.annotations.ValidDateTime() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDateTime.class; 
            }
            @Override
            public String message() { return "Invalid date-time"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd HH:mm:ss"; }
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
            "2023-01-01 12:00:00, true",  // Valid date-time
            "2023-01-01 24:00:00, false", // Invalid hour
            "2023-01-01 12:60:00, false", // Invalid minute
            "2023-01-01 12:00:60, false", // Invalid second
            "2023-02-30 12:00:00, false", // Invalid date
            "invalid-date-time, false"    // Completely invalid
    })
    void shouldValidateDateTimesWithDefaultPattern(String dateTimeStr, boolean expected) {
        assertThat(validator.isValid(dateTimeStr, null)).isEqualTo(expected);
    }

    @Test
    void shouldValidateDifferentDateTimeFormats() {
        // Create validator with different pattern
        DateTimeValidator customValidator = new DateTimeValidator();
        customValidator.initialize(new org.fireflyframework.annotations.ValidDateTime() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDateTime.class; 
            }
            @Override
            public String message() { return "Invalid date-time"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "dd/MM/yyyy HH:mm"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return true; }
        });
        
        assertThat(customValidator.isValid("01/01/2023 12:00", null)).isTrue();
        assertThat(customValidator.isValid("31/12/2023 23:59", null)).isTrue();
        assertThat(customValidator.isValid("29/02/2024 12:00", null)).isTrue(); // Leap year
        
        assertThat(customValidator.isValid("29/02/2023 12:00", null)).isFalse(); // Not a leap year
        assertThat(customValidator.isValid("01/01/2023 24:00", null)).isFalse(); // Invalid hour
        assertThat(customValidator.isValid("01/01/2023 12:60", null)).isFalse(); // Invalid minute
    }

    @Test
    void shouldValidateMinMaxConstraints() {
        // Create validator with min=2023-01-01 00:00:00, max=2023-12-31 23:59:59
        DateTimeValidator minMaxValidator = new DateTimeValidator();
        minMaxValidator.initialize(new org.fireflyframework.annotations.ValidDateTime() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDateTime.class; 
            }
            @Override
            public String message() { return "Invalid date-time"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd HH:mm:ss"; }
            @Override
            public String min() { return "2023-01-01 00:00:00"; }
            @Override
            public String max() { return "2023-12-31 23:59:59"; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return true; }
        });
        
        assertThat(minMaxValidator.isValid("2022-12-31 23:59:59", null)).isFalse(); // Before min
        assertThat(minMaxValidator.isValid("2023-01-01 00:00:00", null)).isTrue();  // At min
        assertThat(minMaxValidator.isValid("2023-06-15 12:30:45", null)).isTrue();  // Between min and max
        assertThat(minMaxValidator.isValid("2023-12-31 23:59:59", null)).isTrue();  // At max
        assertThat(minMaxValidator.isValid("2024-01-01 00:00:00", null)).isFalse(); // After max
    }

    @Test
    void shouldValidateFuturePastConstraints() throws Exception {
        // Get current date-time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // Calculate a date-time in the past (1 hour ago)
        Date pastDateTime = new Date(System.currentTimeMillis() - 60 * 60 * 1000);
        String pastDateTimeStr = sdf.format(pastDateTime);
        
        // Calculate a date-time in the future (1 hour from now)
        Date futureDateTime = new Date(System.currentTimeMillis() + 60 * 60 * 1000);
        String futureDateTimeStr = sdf.format(futureDateTime);
        
        // Current date-time
        String now = sdf.format(new Date());
        
        // Default validator allows both past and future
        assertThat(validator.isValid(pastDateTimeStr, null)).isTrue();
        assertThat(validator.isValid(now, null)).isTrue();
        assertThat(validator.isValid(futureDateTimeStr, null)).isTrue();
        
        // Create validator that doesn't allow past date-times
        DateTimeValidator noPastValidator = new DateTimeValidator();
        noPastValidator.initialize(new org.fireflyframework.annotations.ValidDateTime() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDateTime.class; 
            }
            @Override
            public String message() { return "Invalid date-time"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd HH:mm:ss"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return true; }
            @Override
            public boolean allowPast() { return false; }
        });
        
        assertThat(noPastValidator.isValid(pastDateTimeStr, null)).isFalse();
        assertThat(noPastValidator.isValid(now, null)).isTrue();
        assertThat(noPastValidator.isValid(futureDateTimeStr, null)).isTrue();
        
        // Create validator that doesn't allow future date-times
        DateTimeValidator noFutureValidator = new DateTimeValidator();
        noFutureValidator.initialize(new org.fireflyframework.annotations.ValidDateTime() {
            @Override
            public Class<?>[] groups() { return new Class<?>[0]; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() { 
                return org.fireflyframework.annotations.ValidDateTime.class; 
            }
            @Override
            public String message() { return "Invalid date-time"; }
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() { 
                return new Class[0]; 
            }
            @Override
            public String pattern() { return "yyyy-MM-dd HH:mm:ss"; }
            @Override
            public String min() { return ""; }
            @Override
            public String max() { return ""; }
            @Override
            public boolean allowFuture() { return false; }
            @Override
            public boolean allowPast() { return true; }
        });
        
        assertThat(noFutureValidator.isValid(pastDateTimeStr, null)).isTrue();
        assertThat(noFutureValidator.isValid(now, null)).isTrue();
        assertThat(noFutureValidator.isValid(futureDateTimeStr, null)).isFalse();
    }

    @Test
    void shouldValidateWithCustomFormat() {
        assertThat(validator.isValidFormat("2023-01-01 12:00:00", "yyyy-MM-dd HH:mm:ss")).isTrue();
        assertThat(validator.isValidFormat("01/01/2023 12:00", "dd/MM/yyyy HH:mm")).isTrue();
        assertThat(validator.isValidFormat("20230101120000", "yyyyMMddHHmmss")).isTrue();
        
        assertThat(validator.isValidFormat("2023-01-01 12:00:00", "dd/MM/yyyy HH:mm")).isFalse();
        assertThat(validator.isValidFormat("01/01/2023 12:00", "yyyy-MM-dd HH:mm:ss")).isFalse();
        
        // Invalid inputs
        assertThat(validator.isValidFormat(null, "yyyy-MM-dd HH:mm:ss")).isFalse();
        assertThat(validator.isValidFormat("", "yyyy-MM-dd HH:mm:ss")).isFalse();
        assertThat(validator.isValidFormat("2023-01-01 12:00:00", null)).isFalse();
        assertThat(validator.isValidFormat("2023-01-01 12:00:00", "")).isFalse();
    }

    @Test
    void shouldFormatDateTime() throws Exception {
        // Create a date-time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date dateTime = sdf.parse("2023-01-01 12:30:45");
        
        // Format with different patterns
        assertThat(validator.format(dateTime, "yyyy-MM-dd HH:mm:ss")).isEqualTo("2023-01-01 12:30:45");
        assertThat(validator.format(dateTime, "dd/MM/yyyy HH:mm")).isEqualTo("01/01/2023 12:30");
        assertThat(validator.format(dateTime, "MMMM d, yyyy h:mm a")).isEqualTo("January 1, 2023 12:30 PM");
        
        // Invalid inputs
        assertThat(validator.format(null, "yyyy-MM-dd HH:mm:ss")).isNull();
        assertThat(validator.format(dateTime, null)).isNull();
        assertThat(validator.format(dateTime, "")).isNull();
    }

    @Test
    void shouldParseDateTime() {
        // Parse with different patterns
        Date dateTime1 = validator.parse("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss");
        Date dateTime2 = validator.parse("01/01/2023 12:30", "dd/MM/yyyy HH:mm");
        
        assertThat(dateTime1).isNotNull();
        assertThat(dateTime2).isNotNull();
        
        // Invalid inputs
        assertThat(validator.parse(null, "yyyy-MM-dd HH:mm:ss")).isNull();
        assertThat(validator.parse("", "yyyy-MM-dd HH:mm:ss")).isNull();
        assertThat(validator.parse("2023-01-01 12:30:45", null)).isNull();
        assertThat(validator.parse("2023-01-01 12:30:45", "")).isNull();
        assertThat(validator.parse("invalid-date-time", "yyyy-MM-dd HH:mm:ss")).isNull();
    }

    @Test
    void shouldConvertFormat() {
        assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm"))
            .isEqualTo("01/01/2023 12:30");
        assertThat(validator.convertFormat("01/01/2023 12:30", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm:ss"))
            .isEqualTo("2023-01-01 12:30:00");
        assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", "MMMM d, yyyy h:mm a"))
            .isEqualTo("January 1, 2023 12:30 PM");
        
        // Invalid inputs
        assertThat(validator.convertFormat(null, "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")).isNull();
        assertThat(validator.convertFormat("", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")).isNull();
        assertThat(validator.convertFormat("2023-01-01 12:30:45", null, "dd/MM/yyyy HH:mm")).isNull();
        assertThat(validator.convertFormat("2023-01-01 12:30:45", "", "dd/MM/yyyy HH:mm")).isNull();
        assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", null)).isNull();
        assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", "")).isNull();
        assertThat(validator.convertFormat("invalid-date-time", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")).isNull();
    }

    @Test
    void shouldCheckFuturePastDateTimes() throws Exception {
        // Get current date-time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // Calculate a date-time in the past (1 hour ago)
        Date pastDateTime = new Date(System.currentTimeMillis() - 60 * 60 * 1000);
        String pastDateTimeStr = sdf.format(pastDateTime);
        
        // Calculate a date-time in the future (1 hour from now)
        Date futureDateTime = new Date(System.currentTimeMillis() + 60 * 60 * 1000);
        String futureDateTimeStr = sdf.format(futureDateTime);
        
        assertThat(validator.isFutureDateTime(futureDateTimeStr, "yyyy-MM-dd HH:mm:ss")).isTrue();
        assertThat(validator.isFutureDateTime(pastDateTimeStr, "yyyy-MM-dd HH:mm:ss")).isFalse();
        
        assertThat(validator.isPastDateTime(pastDateTimeStr, "yyyy-MM-dd HH:mm:ss")).isTrue();
        assertThat(validator.isPastDateTime(futureDateTimeStr, "yyyy-MM-dd HH:mm:ss")).isFalse();
        
        // Invalid inputs
        assertThat(validator.isFutureDateTime(null, "yyyy-MM-dd HH:mm:ss")).isFalse();
        assertThat(validator.isFutureDateTime("", "yyyy-MM-dd HH:mm:ss")).isFalse();
        assertThat(validator.isFutureDateTime("2023-01-01 12:30:45", null)).isFalse();
        assertThat(validator.isFutureDateTime("2023-01-01 12:30:45", "")).isFalse();
        assertThat(validator.isFutureDateTime("invalid-date-time", "yyyy-MM-dd HH:mm:ss")).isFalse();
        
        assertThat(validator.isPastDateTime(null, "yyyy-MM-dd HH:mm:ss")).isFalse();
        assertThat(validator.isPastDateTime("", "yyyy-MM-dd HH:mm:ss")).isFalse();
        assertThat(validator.isPastDateTime("2023-01-01 12:30:45", null)).isFalse();
        assertThat(validator.isPastDateTime("2023-01-01 12:30:45", "")).isFalse();
        assertThat(validator.isPastDateTime("invalid-date-time", "yyyy-MM-dd HH:mm:ss")).isFalse();
    }

    @Test
    void shouldAddHoursAndMinutes() {
        String dateTimeStr = "2023-01-01 12:00:00";
        String pattern = "yyyy-MM-dd HH:mm:ss";
        
        // Add hours
        assertThat(validator.addHours(dateTimeStr, pattern, 1)).isEqualTo("2023-01-01 13:00:00");
        assertThat(validator.addHours(dateTimeStr, pattern, 24)).isEqualTo("2023-01-02 12:00:00");
        assertThat(validator.addHours(dateTimeStr, pattern, -1)).isEqualTo("2023-01-01 11:00:00");
        assertThat(validator.addHours(dateTimeStr, pattern, -24)).isEqualTo("2022-12-31 12:00:00");
        
        // Add minutes
        assertThat(validator.addMinutes(dateTimeStr, pattern, 30)).isEqualTo("2023-01-01 12:30:00");
        assertThat(validator.addMinutes(dateTimeStr, pattern, 60)).isEqualTo("2023-01-01 13:00:00");
        assertThat(validator.addMinutes(dateTimeStr, pattern, -30)).isEqualTo("2023-01-01 11:30:00");
        assertThat(validator.addMinutes(dateTimeStr, pattern, -60)).isEqualTo("2023-01-01 11:00:00");
        
        // Invalid inputs
        assertThat(validator.addHours(null, pattern, 1)).isNull();
        assertThat(validator.addHours("", pattern, 1)).isNull();
        assertThat(validator.addHours(dateTimeStr, null, 1)).isNull();
        assertThat(validator.addHours(dateTimeStr, "", 1)).isNull();
        assertThat(validator.addHours("invalid-date-time", pattern, 1)).isNull();
        
        assertThat(validator.addMinutes(null, pattern, 30)).isNull();
        assertThat(validator.addMinutes("", pattern, 30)).isNull();
        assertThat(validator.addMinutes(dateTimeStr, null, 30)).isNull();
        assertThat(validator.addMinutes(dateTimeStr, "", 30)).isNull();
        assertThat(validator.addMinutes("invalid-date-time", pattern, 30)).isNull();
    }

    @Test
    void shouldCalculateMinutesBetween() {
        String dateTime1Str = "2023-01-01 12:00:00";
        String dateTime2Str = "2023-01-01 12:30:00";
        String pattern = "yyyy-MM-dd HH:mm:ss";
        
        assertThat(validator.minutesBetween(dateTime1Str, dateTime2Str, pattern)).isEqualTo(30);
        assertThat(validator.minutesBetween(dateTime2Str, dateTime1Str, pattern)).isEqualTo(30);
        assertThat(validator.minutesBetween(dateTime1Str, dateTime1Str, pattern)).isEqualTo(0);
        
        // Larger time differences
        String dateTime3Str = "2023-01-01 14:00:00";
        assertThat(validator.minutesBetween(dateTime1Str, dateTime3Str, pattern)).isEqualTo(120);
        
        // Invalid inputs
        assertThat(validator.minutesBetween(null, dateTime2Str, pattern)).isEqualTo(-1);
        assertThat(validator.minutesBetween(dateTime1Str, null, pattern)).isEqualTo(-1);
        assertThat(validator.minutesBetween(dateTime1Str, dateTime2Str, null)).isEqualTo(-1);
        assertThat(validator.minutesBetween("invalid-date-time", dateTime2Str, pattern)).isEqualTo(-1);
        assertThat(validator.minutesBetween(dateTime1Str, "invalid-date-time", pattern)).isEqualTo(-1);
    }
}