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

import org.fireflyframework.annotations.ValidAmount;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validator for monetary amounts.
 * 
 * This validator checks if a monetary amount is valid according to:
 * - Positivity (or zero if allowed)
 * - Min/max constraints
 * - Currency-specific decimal place limits
 */
public class AmountValidator implements ConstraintValidator<ValidAmount, Number> {

    private String currencyCode;
    private double min;
    private double max;
    private boolean allowZero;

    // Map of currency codes to their decimal places
    private static final Map<String, Integer> CURRENCY_DECIMAL_PLACES = new HashMap<>();

    static {
        // Major currencies
        CURRENCY_DECIMAL_PLACES.put("EUR", 2); // Euro
        CURRENCY_DECIMAL_PLACES.put("USD", 2); // US Dollar
        CURRENCY_DECIMAL_PLACES.put("GBP", 2); // British Pound
        CURRENCY_DECIMAL_PLACES.put("JPY", 0); // Japanese Yen (no decimal places)
        CURRENCY_DECIMAL_PLACES.put("CHF", 2); // Swiss Franc
        CURRENCY_DECIMAL_PLACES.put("CAD", 2); // Canadian Dollar
        CURRENCY_DECIMAL_PLACES.put("AUD", 2); // Australian Dollar
        CURRENCY_DECIMAL_PLACES.put("CNY", 2); // Chinese Yuan
        CURRENCY_DECIMAL_PLACES.put("HKD", 2); // Hong Kong Dollar
        CURRENCY_DECIMAL_PLACES.put("SGD", 2); // Singapore Dollar

        // European currencies
        CURRENCY_DECIMAL_PLACES.put("SEK", 2); // Swedish Krona
        CURRENCY_DECIMAL_PLACES.put("NOK", 2); // Norwegian Krone
        CURRENCY_DECIMAL_PLACES.put("DKK", 2); // Danish Krone
        CURRENCY_DECIMAL_PLACES.put("PLN", 2); // Polish Złoty
        CURRENCY_DECIMAL_PLACES.put("CZK", 2); // Czech Koruna
        CURRENCY_DECIMAL_PLACES.put("HUF", 2); // Hungarian Forint
        CURRENCY_DECIMAL_PLACES.put("RON", 2); // Romanian Leu

        // Latin American currencies
        CURRENCY_DECIMAL_PLACES.put("MXN", 2); // Mexican Peso
        CURRENCY_DECIMAL_PLACES.put("BRL", 2); // Brazilian Real
        CURRENCY_DECIMAL_PLACES.put("ARS", 2); // Argentine Peso
        CURRENCY_DECIMAL_PLACES.put("CLP", 0); // Chilean Peso (no decimal places)
        CURRENCY_DECIMAL_PLACES.put("COP", 2); // Colombian Peso

        // Middle Eastern currencies
        CURRENCY_DECIMAL_PLACES.put("AED", 2); // UAE Dirham
        CURRENCY_DECIMAL_PLACES.put("SAR", 2); // Saudi Riyal
        CURRENCY_DECIMAL_PLACES.put("QAR", 2); // Qatari Riyal
        CURRENCY_DECIMAL_PLACES.put("ILS", 2); // Israeli New Shekel

        // Cryptocurrencies
        CURRENCY_DECIMAL_PLACES.put("BTC", 8); // Bitcoin
        CURRENCY_DECIMAL_PLACES.put("ETH", 18); // Ethereum
        CURRENCY_DECIMAL_PLACES.put("XRP", 6); // Ripple
    }

    @Override
    public void initialize(ValidAmount constraintAnnotation) {
        this.currencyCode = constraintAnnotation.currency();
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.allowZero = constraintAnnotation.allowZero();
    }

    /**
     * Validates if the provided amount is valid.
     *
     * @param amount the amount to validate
     * @param context the constraint validator context
     * @return true if the amount is valid, false otherwise
     */
    @Override
    public boolean isValid(Number amount, ConstraintValidatorContext context) {
        if (amount == null) {
            return false;
        }

        BigDecimal bdAmount = new BigDecimal(amount.toString());

        // Check if zero is allowed
        if (bdAmount.compareTo(BigDecimal.ZERO) == 0) {
            return allowZero;
        }

        // Check if amount is positive
        if (bdAmount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        // Check min/max constraints
        if (bdAmount.doubleValue() < min || bdAmount.doubleValue() > max) {
            return false;
        }

        // Check decimal places for the currency
        return validateDecimalPlaces(bdAmount, currencyCode);
    }

    /**
     * Validates if the amount has the correct number of decimal places for the currency.
     *
     * @param amount the amount to validate
     * @param currencyCode the currency code
     * @return true if the amount has the correct number of decimal places, false otherwise
     */
    private boolean validateDecimalPlaces(BigDecimal amount, String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty()) {
            return true; // No currency specified, so no decimal place validation
        }

        // Get the number of decimal places for the currency
        Integer maxDecimalPlaces = CURRENCY_DECIMAL_PLACES.get(currencyCode.toUpperCase());
        if (maxDecimalPlaces == null) {
            maxDecimalPlaces = 2; // Default to 2 decimal places if currency not found
        }

        // Special case for currencies with 0 decimal places (like JPY)
        if (maxDecimalPlaces == 0) {
            // Check if the amount is effectively an integer (has no fractional part)
            return amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0;
        }

        // Get the scale (number of decimal places) of the amount
        int scale = amount.scale();

        // Check if the scale is less than or equal to the maximum allowed
        return scale <= maxDecimalPlaces;
    }

    /**
     * Formats an amount according to the currency's standard format.
     *
     * @param amount the amount to format
     * @param currencyCode the currency code
     * @return the formatted amount or null if invalid
     */
    public String format(Number amount, String currencyCode) {
        if (amount == null || currencyCode == null || currencyCode.isEmpty()) {
            return null;
        }

        BigDecimal bdAmount = new BigDecimal(amount.toString());

        // Get the number of decimal places for the currency
        Integer decimalPlaces = CURRENCY_DECIMAL_PLACES.get(currencyCode.toUpperCase());
        if (decimalPlaces == null) {
            decimalPlaces = 2; // Default to 2 decimal places if currency not found
        }

        // Set the scale (number of decimal places) of the amount
        bdAmount = bdAmount.setScale(decimalPlaces, java.math.RoundingMode.HALF_UP);

        // Format the amount with the currency symbol
        java.util.Locale locale = java.util.Locale.US; // Use US locale for consistent formatting
        java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance(locale);
        java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
        formatter.setCurrency(currency);
        formatter.setMaximumFractionDigits(decimalPlaces);
        formatter.setMinimumFractionDigits(decimalPlaces);

        return formatter.format(bdAmount);
    }

    /**
     * Rounds an amount to the appropriate number of decimal places for the currency.
     *
     * @param amount the amount to round
     * @param currencyCode the currency code
     * @return the rounded amount or null if invalid
     */
    public BigDecimal round(Number amount, String currencyCode) {
        if (amount == null || currencyCode == null || currencyCode.isEmpty()) {
            return null;
        }

        BigDecimal bdAmount = new BigDecimal(amount.toString());

        // Get the number of decimal places for the currency
        Integer decimalPlaces = CURRENCY_DECIMAL_PLACES.get(currencyCode.toUpperCase());
        if (decimalPlaces == null) {
            decimalPlaces = 2; // Default to 2 decimal places if currency not found
        }

        // Set the scale (number of decimal places) of the amount
        return bdAmount.setScale(decimalPlaces, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Gets the number of decimal places for a currency.
     *
     * @param currencyCode the currency code
     * @return the number of decimal places or 2 if the currency is not found
     */
    public int getDecimalPlaces(String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty()) {
            return 2; // Default to 2 decimal places
        }

        Integer decimalPlaces = CURRENCY_DECIMAL_PLACES.get(currencyCode.toUpperCase());
        return decimalPlaces != null ? decimalPlaces : 2;
    }
}
