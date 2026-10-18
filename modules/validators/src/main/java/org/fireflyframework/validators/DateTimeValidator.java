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

import org.fireflyframework.annotations.ValidDateTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Validator for date-times.
 * 
 * This validator checks if a date-time is valid according to:
 * - Format pattern
 * - Min/max constraints
 * - Future/past constraints
 */
public class DateTimeValidator implements ConstraintValidator<ValidDateTime, String> {

    private String pattern;
    private String min;
    private String max;
    private boolean allowFuture;
    private boolean allowPast;

    @Override
    public void initialize(ValidDateTime constraintAnnotation) {
        this.pattern = constraintAnnotation.pattern();
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.allowFuture = constraintAnnotation.allowFuture();
        this.allowPast = constraintAnnotation.allowPast();
    }

    /**
     * Validates if the provided date-time is valid.
     *
     * @param dateTimeStr the date-time string to validate
     * @param context the constraint validator context
     * @return true if the date-time is valid, false otherwise
     */
    @Override
    public boolean isValid(String dateTimeStr, ConstraintValidatorContext context) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return false;
        }

        try {
            // Parse the date-time using the specified pattern
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, java.util.Locale.US);
            sdf.setLenient(false); // Strict parsing
            Date dateTime = sdf.parse(dateTimeStr);

            // Check min date-time constraint
            if (!min.isEmpty()) {
                Date minDateTime = sdf.parse(min);
                if (dateTime.before(minDateTime)) {
                    return false;
                }
            }

            // Check max date-time constraint
            if (!max.isEmpty()) {
                Date maxDateTime = sdf.parse(max);
                if (dateTime.after(maxDateTime)) {
                    return false;
                }
            }

            // Check future/past constraints
            Date now = new Date();

            // Special case for "now" values - add a small tolerance (1 second)
            long timeDifference = Math.abs(dateTime.getTime() - now.getTime());
            boolean isApproximatelyNow = timeDifference < 1000; // 1 second tolerance

            if (isApproximatelyNow) {
                return true; // Consider "now" values as valid regardless of allowFuture/allowPast
            }

            if (!allowFuture && dateTime.after(now)) {
                return false;
            }
            if (!allowPast && dateTime.before(now)) {
                return false;
            }

            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Validates if the provided date-time is valid according to the specified pattern.
     *
     * @param dateTimeStr the date-time string to validate
     * @param pattern the date-time format pattern
     * @return true if the date-time is valid, false otherwise
     */
    public boolean isValidFormat(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.isEmpty() || pattern == null || pattern.isEmpty()) {
            return false;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, java.util.Locale.US);
            sdf.setLenient(false); // Strict parsing
            sdf.parse(dateTimeStr);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Formats a date-time according to the specified pattern.
     *
     * @param dateTime the date-time to format
     * @param pattern the date-time format pattern
     * @return the formatted date-time or null if invalid
     */
    public String format(Date dateTime, String pattern) {
        if (dateTime == null || pattern == null || pattern.isEmpty()) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(pattern, java.util.Locale.US);
        return sdf.format(dateTime);
    }

    /**
     * Parses a date-time string according to the specified pattern.
     *
     * @param dateTimeStr the date-time string to parse
     * @param pattern the date-time format pattern
     * @return the parsed date-time or null if invalid
     */
    public Date parse(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.isEmpty() || pattern == null || pattern.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, java.util.Locale.US);
            sdf.setLenient(false); // Strict parsing
            return sdf.parse(dateTimeStr);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Converts a date-time from one format to another.
     *
     * @param dateTimeStr the date-time string to convert
     * @param fromPattern the source date-time format pattern
     * @param toPattern the target date-time format pattern
     * @return the converted date-time string or null if invalid
     */
    public String convertFormat(String dateTimeStr, String fromPattern, String toPattern) {
        if (dateTimeStr == null || dateTimeStr.isEmpty() || 
            fromPattern == null || fromPattern.isEmpty() || 
            toPattern == null || toPattern.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat fromSdf = new SimpleDateFormat(fromPattern, java.util.Locale.US);
            fromSdf.setLenient(false); // Strict parsing
            Date dateTime = fromSdf.parse(dateTimeStr);

            SimpleDateFormat toSdf = new SimpleDateFormat(toPattern, java.util.Locale.US);
            return toSdf.format(dateTime);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Checks if a date-time is in the future.
     *
     * @param dateTimeStr the date-time string to check
     * @param pattern the date-time format pattern
     * @return true if the date-time is in the future, false otherwise or if invalid
     */
    public boolean isFutureDateTime(String dateTimeStr, String pattern) {
        Date dateTime = parse(dateTimeStr, pattern);
        if (dateTime == null) {
            return false;
        }

        return dateTime.after(new Date());
    }

    /**
     * Checks if a date-time is in the past.
     *
     * @param dateTimeStr the date-time string to check
     * @param pattern the date-time format pattern
     * @return true if the date-time is in the past, false otherwise or if invalid
     */
    public boolean isPastDateTime(String dateTimeStr, String pattern) {
        Date dateTime = parse(dateTimeStr, pattern);
        if (dateTime == null) {
            return false;
        }

        return dateTime.before(new Date());
    }

    /**
     * Adds a specified number of hours to a date-time.
     *
     * @param dateTimeStr the date-time string
     * @param pattern the date-time format pattern
     * @param hours the number of hours to add (can be negative)
     * @return the new date-time string or null if invalid
     */
    public String addHours(String dateTimeStr, String pattern, int hours) {
        Date dateTime = parse(dateTimeStr, pattern);
        if (dateTime == null) {
            return null;
        }

        // Convert to LocalDateTime for easier calculation
        LocalDateTime localDateTime = dateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime newLocalDateTime = localDateTime.plusHours(hours);

        // Convert back to string
        return newLocalDateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Adds a specified number of minutes to a date-time.
     *
     * @param dateTimeStr the date-time string
     * @param pattern the date-time format pattern
     * @param minutes the number of minutes to add (can be negative)
     * @return the new date-time string or null if invalid
     */
    public String addMinutes(String dateTimeStr, String pattern, int minutes) {
        Date dateTime = parse(dateTimeStr, pattern);
        if (dateTime == null) {
            return null;
        }

        // Convert to LocalDateTime for easier calculation
        LocalDateTime localDateTime = dateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime newLocalDateTime = localDateTime.plusMinutes(minutes);

        // Convert back to string
        return newLocalDateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Calculates the difference in minutes between two date-times.
     *
     * @param dateTime1Str the first date-time string
     * @param dateTime2Str the second date-time string
     * @param pattern the date-time format pattern
     * @return the difference in minutes or -1 if invalid
     */
    public long minutesBetween(String dateTime1Str, String dateTime2Str, String pattern) {
        Date dateTime1 = parse(dateTime1Str, pattern);
        Date dateTime2 = parse(dateTime2Str, pattern);

        if (dateTime1 == null || dateTime2 == null) {
            return -1;
        }

        // Convert to LocalDateTime for easier calculation
        LocalDateTime localDateTime1 = dateTime1.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime localDateTime2 = dateTime2.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        return Math.abs(java.time.temporal.ChronoUnit.MINUTES.between(localDateTime1, localDateTime2));
    }
}
