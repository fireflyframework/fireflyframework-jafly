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
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validator for bank account numbers.
 * 
 * This validator checks if an account number is valid according to various
 * country-specific formats, with a focus on European banking systems.
 */
public class AccountNumberValidator implements ConstraintValidator<ValidAccountNumber, String> {

    private String countryCode;

    @Override
    public void initialize(ValidAccountNumber constraintAnnotation) {
        this.countryCode = constraintAnnotation.countryCode();
    }

    // UK account number format: 8 digits
    private static final Pattern UK_ACCOUNT_NUMBER_PATTERN = Pattern.compile("^\\d{8}$");

    // Map of country codes to their respective account number patterns
    private static final Map<String, Pattern> COUNTRY_PATTERNS = new HashMap<>();

    static {
        // Initialize patterns for different countries
        COUNTRY_PATTERNS.put("GB", UK_ACCOUNT_NUMBER_PATTERN); // United Kingdom: 8 digits
        COUNTRY_PATTERNS.put("FR", Pattern.compile("^\\d{10}$")); // France: 10 digits
        COUNTRY_PATTERNS.put("DE", Pattern.compile("^\\d{10}$")); // Germany: 10 digits
        COUNTRY_PATTERNS.put("ES", Pattern.compile("^\\d{10}$")); // Spain: 10 digits
        COUNTRY_PATTERNS.put("IT", Pattern.compile("^\\d{12}$")); // Italy: 12 digits
        COUNTRY_PATTERNS.put("NL", Pattern.compile("^\\d{10}$")); // Netherlands: 10 digits
        COUNTRY_PATTERNS.put("BE", Pattern.compile("^\\d{12}$")); // Belgium: 12 digits
        COUNTRY_PATTERNS.put("CH", Pattern.compile("^\\d{4,11}$")); // Switzerland: 4-11 digits
    }

    /**
     * Validates if the provided account number is valid for the specified country.
     *
     * @param accountNumber the account number to validate
     * @param countryCode the ISO 3166-1 alpha-2 country code (e.g., "GB" for United Kingdom)
     * @return true if the account number is valid for the country, false otherwise
     */
    public boolean isValidForCountry(String accountNumber, String countryCode) {
        if (accountNumber == null || accountNumber.isEmpty() || 
            countryCode == null || countryCode.isEmpty()) {
            return false;
        }

        // Normalize the account number and country code
        String normalizedAccountNumber = normalize(accountNumber);
        String normalizedCountryCode = countryCode.trim().toUpperCase();

        // Check if we have a pattern for this country
        Pattern pattern = COUNTRY_PATTERNS.get(normalizedCountryCode);
        if (pattern == null) {
            return false; // Unsupported country
        }

        // Validate against the country-specific pattern
        return pattern.matcher(normalizedAccountNumber).matches();
    }

    /**
     * Validates if the provided account number is valid for the UK.
     *
     * @param accountNumber the account number to validate
     * @return true if the account number is valid for the UK, false otherwise
     */
    public boolean isValidUkAccountNumber(String accountNumber) {
        return isValidForCountry(accountNumber, "GB");
    }

    /**
     * Validates if the provided account number is valid according to the annotation configuration.
     *
     * @param accountNumber the account number to validate
     * @param context the constraint validator context
     * @return true if the account number is valid, false otherwise
     */
    @Override
    public boolean isValid(String accountNumber, ConstraintValidatorContext context) {
        return isValidForCountry(accountNumber, this.countryCode);
    }

    /**
     * Normalizes an account number by removing spaces and other separators.
     *
     * @param accountNumber the account number to normalize
     * @return the normalized account number (digits only) or null if input is null/empty
     */
    public String normalize(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return null;
        }

        return accountNumber.replaceAll("[\\s-]", "");
    }

    /**
     * Formats a UK account number with standard spacing (e.g., "12345678" to "12345678").
     * This method is primarily for consistency with other formatters, as UK account numbers
     * are typically displayed without separators.
     *
     * @param accountNumber the account number to format
     * @return the formatted account number or null if invalid
     */
    public String formatUkAccountNumber(String accountNumber) {
        if (!isValidUkAccountNumber(accountNumber)) {
            return null;
        }

        return normalize(accountNumber);
    }

    /**
     * Validates if the provided sort code and account number combination is valid for the UK.
     * This method uses the SortCodeValidator to validate the sort code.
     *
     * @param sortCode the sort code to validate
     * @param accountNumber the account number to validate
     * @return true if both the sort code and account number are valid, false otherwise
     */
    public boolean isValidUkBankAccount(String sortCode, String accountNumber) {
        SortCodeValidator sortCodeValidator = new SortCodeValidator();
        return sortCodeValidator.isValidSortCode(sortCode) && isValidUkAccountNumber(accountNumber);
    }

    /**
     * Formats a UK bank account as a single string with sort code and account number.
     * Format: "XX-XX-XX XXXXXXXX" (sort code with hyphens, space, account number)
     *
     * @param sortCode the sort code
     * @param accountNumber the account number
     * @return the formatted bank account or null if either part is invalid
     */
    public String formatUkBankAccount(String sortCode, String accountNumber) {
        if (!isValidUkBankAccount(sortCode, accountNumber)) {
            return null;
        }

        SortCodeValidator sortCodeValidator = new SortCodeValidator();
        String formattedSortCode = sortCodeValidator.format(sortCode);
        String normalizedAccountNumber = normalize(accountNumber);

        return formattedSortCode + " " + normalizedAccountNumber;
    }
}
