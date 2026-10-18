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

import org.fireflyframework.annotations.ValidAccountNumber;
import org.fireflyframework.annotations.ValidBic;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for Bank Identifier Codes (BIC), also known as SWIFT codes.
 * 
 * This validator checks if a BIC is valid according to the standard format
 * which includes:
 * - Bank code (4 letters)
 * - Country code (2 letters)
 * - Location code (2 letters or digits)
 * - Optional branch code (3 letters or digits)
 */
public class BicValidator implements ConstraintValidator<ValidBic, String> {

    @Override
    public void initialize(ValidBic constraintAnnotation) {
        // No initialization needed
    }

    @ValidAccountNumber()
    // BIC format: bank code (4 letters) + country code (2 letters) + location code (2 letters/digits) + optional branch code (3 letters/digits)
    private static final Pattern BIC_8_PATTERN = Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}$");
    private static final Pattern BIC_11_PATTERN = Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}[A-Z0-9]{3}$");

    /**
     * Validates if the provided BIC is valid.
     *
     * @param bic the BIC to validate
     * @return true if the BIC is valid, false otherwise
     */
    public boolean isValidBic(String bic) {
        if (bic == null || bic.isEmpty()) {
            return false;
        }

        // Remove spaces and convert to uppercase
        String normalizedBic = bic.replaceAll("\\s", "").toUpperCase();

        // BIC can be either 8 or 11 characters long
        int length = normalizedBic.length();
        if (length != 8 && length != 11) {
            return false;
        }

        // Check format based on length
        if (length == 8) {
            return BIC_8_PATTERN.matcher(normalizedBic).matches();
        } else {
            return BIC_11_PATTERN.matcher(normalizedBic).matches();
        }
    }

    /**
     * Extracts the bank code from a valid BIC.
     *
     * @param bic the BIC to extract from
     * @return the bank code or null if the BIC is invalid
     */
    public String getBankCode(String bic) {
        if (!isValidBic(bic)) {
            return null;
        }

        String normalizedBic = bic.replaceAll("\\s", "").toUpperCase();
        return normalizedBic.substring(0, 4);
    }

    /**
     * Extracts the country code from a valid BIC.
     *
     * @param bic the BIC to extract from
     * @return the country code or null if the BIC is invalid
     */
    public String getCountryCode(String bic) {
        if (!isValidBic(bic)) {
            return null;
        }

        String normalizedBic = bic.replaceAll("\\s", "").toUpperCase();
        return normalizedBic.substring(4, 6);
    }

    /**
     * Extracts the location code from a valid BIC.
     *
     * @param bic the BIC to extract from
     * @return the location code or null if the BIC is invalid
     */
    public String getLocationCode(String bic) {
        if (!isValidBic(bic)) {
            return null;
        }

        String normalizedBic = bic.replaceAll("\\s", "").toUpperCase();
        return normalizedBic.substring(6, 8);
    }

    /**
     * Extracts the branch code from a valid BIC.
     *
     * @param bic the BIC to extract from
     * @return the branch code, "XXX" if not specified, or null if the BIC is invalid
     */
    public String getBranchCode(String bic) {
        if (!isValidBic(bic)) {
            return null;
        }

        String normalizedBic = bic.replaceAll("\\s", "").toUpperCase();
        if (normalizedBic.length() == 8) {
            return "XXX"; // Default branch code
        } else {
            return normalizedBic.substring(8, 11);
        }
    }

    /**
     * Validates if the provided BIC is valid according to the annotation configuration.
     *
     * @param bic the BIC to validate
     * @param context the constraint validator context
     * @return true if the BIC is valid, false otherwise
     */
    @Override
    public boolean isValid(String bic, ConstraintValidatorContext context) {
        return isValidBic(bic);
    }
}
