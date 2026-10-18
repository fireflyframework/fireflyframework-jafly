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

import org.fireflyframework.annotations.ValidSortCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for UK bank sort codes.
 * 
 * This validator checks if a sort code is valid according to the UK banking standard,
 * which defines sort codes as 6-digit numbers used to identify banks and branches.
 * Sort codes are typically formatted as "XX-XX-XX" where X is a digit.
 */
public class SortCodeValidator implements ConstraintValidator<ValidSortCode, String> {

    @Override
    public void initialize(ValidSortCode constraintAnnotation) {
        // No initialization needed
    }

    // Sort code format: 6 digits, optionally separated by hyphens or spaces
    private static final Pattern SORT_CODE_PATTERN = Pattern.compile("^\\d{2}[-\\s]?\\d{2}[-\\s]?\\d{2}$");

    /**
     * Validates if the provided sort code is valid.
     *
     * @param sortCode the sort code to validate
     * @return true if the sort code is valid, false otherwise
     */
    public boolean isValidSortCode(String sortCode) {
        if (sortCode == null || sortCode.isEmpty()) {
            return false;
        }

        return SORT_CODE_PATTERN.matcher(sortCode).matches();
    }

    /**
     * Normalizes a sort code by removing any separators.
     *
     * @param sortCode the sort code to normalize
     * @return the normalized sort code (6 digits without separators) or null if invalid
     */
    public String normalize(String sortCode) {
        if (!isValidSortCode(sortCode)) {
            return null;
        }

        return sortCode.replaceAll("[\\s-]", "");
    }

    /**
     * Formats a sort code with hyphens (XX-XX-XX).
     *
     * @param sortCode the sort code to format
     * @return the formatted sort code or null if invalid
     */
    public String format(String sortCode) {
        String normalized = normalize(sortCode);
        if (normalized == null) {
            return null;
        }

        return normalized.substring(0, 2) + "-" + 
               normalized.substring(2, 4) + "-" + 
               normalized.substring(4, 6);
    }

    /**
     * Extracts the bank identifier from a sort code (first 2 digits).
     *
     * @param sortCode the sort code
     * @return the bank identifier or null if the sort code is invalid
     */
    public String getBankIdentifier(String sortCode) {
        String normalized = normalize(sortCode);
        if (normalized == null) {
            return null;
        }

        return normalized.substring(0, 2);
    }

    /**
     * Extracts the branch identifier from a sort code (last 4 digits).
     *
     * @param sortCode the sort code
     * @return the branch identifier or null if the sort code is invalid
     */
    public String getBranchIdentifier(String sortCode) {
        String normalized = normalize(sortCode);
        if (normalized == null) {
            return null;
        }

        return normalized.substring(2, 6);
    }

    /**
     * Validates if the provided sort code is valid according to the annotation configuration.
     *
     * @param sortCode the sort code to validate
     * @param context the constraint validator context
     * @return true if the sort code is valid, false otherwise
     */
    @Override
    public boolean isValid(String sortCode, ConstraintValidatorContext context) {
        return isValidSortCode(sortCode);
    }
}
