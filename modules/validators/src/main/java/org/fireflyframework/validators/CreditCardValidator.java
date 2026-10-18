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

import org.fireflyframework.annotations.ValidCreditCard;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Validator for credit card numbers.
 * 
 * This validator checks if a credit card number is valid according to:
 * - Card type specific patterns (Visa, MasterCard, American Express, etc.)
 * - Luhn algorithm validation
 */
public class CreditCardValidator implements ConstraintValidator<ValidCreditCard, String> {

    private CardType[] acceptedCardTypes;

    @Override
    public void initialize(ValidCreditCard constraintAnnotation) {
        this.acceptedCardTypes = constraintAnnotation.types();
    }

    // Credit card type patterns
    private static final Pattern VISA_PATTERN = Pattern.compile("^4[0-9]{12}(?:[0-9]{3})?$");
    private static final Pattern MASTERCARD_PATTERN = Pattern.compile("^5[1-5][0-9]{14}$");
    private static final Pattern AMEX_PATTERN = Pattern.compile("^3[47][0-9]{13}$");
    private static final Pattern DISCOVER_PATTERN = Pattern.compile("^6(?:011|5[0-9]{2})[0-9]{12}$");
    private static final Pattern DINERS_CLUB_PATTERN = Pattern.compile("^3(?:0[0-5]|[68][0-9])[0-9]{11}$");
    private static final Pattern JCB_PATTERN = Pattern.compile("^(?:2131|1800|35\\d{3})\\d{11}$");

    /**
     * Credit card types supported by this validator.
     */
    public enum CardType {
        VISA,
        MASTERCARD,
        AMERICAN_EXPRESS,
        DISCOVER,
        DINERS_CLUB,
        JCB,
        UNKNOWN
    }

    /**
     * Validates if the provided credit card number is valid.
     *
     * @param cardNumber the credit card number to validate
     * @return true if the credit card number is valid, false otherwise
     */
    public boolean isValidCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return false;
        }

        // Remove spaces and dashes
        String normalizedCardNumber = cardNumber.replaceAll("[\\s-]", "");

        // Check if it's a valid card type
        CardType cardType = getCardType(normalizedCardNumber);
        if (cardType == CardType.UNKNOWN) {
            return false;
        }

        // Validate using Luhn algorithm
        return validateLuhn(normalizedCardNumber);
    }

    /**
     * Determines the type of credit card based on the card number.
     *
     * @param cardNumber the credit card number
     * @return the card type or UNKNOWN if the card type cannot be determined
     */
    public CardType getCardType(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return CardType.UNKNOWN;
        }

        String normalizedCardNumber = cardNumber.replaceAll("[\\s-]", "");

        if (VISA_PATTERN.matcher(normalizedCardNumber).matches()) {
            return CardType.VISA;
        } else if (MASTERCARD_PATTERN.matcher(normalizedCardNumber).matches()) {
            return CardType.MASTERCARD;
        } else if (AMEX_PATTERN.matcher(normalizedCardNumber).matches()) {
            return CardType.AMERICAN_EXPRESS;
        } else if (DISCOVER_PATTERN.matcher(normalizedCardNumber).matches()) {
            return CardType.DISCOVER;
        } else if (DINERS_CLUB_PATTERN.matcher(normalizedCardNumber).matches()) {
            return CardType.DINERS_CLUB;
        } else if (JCB_PATTERN.matcher(normalizedCardNumber).matches()) {
            return CardType.JCB;
        } else {
            return CardType.UNKNOWN;
        }
    }

    /**
     * Validates a credit card number using the Luhn algorithm.
     *
     * @param cardNumber the normalized credit card number (no spaces or dashes)
     * @return true if the card number passes the Luhn check, false otherwise
     */
    private boolean validateLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        // Process from right to left
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            // Check if the character is a digit
            if (digit < 0 || digit > 9) {
                return false;
            }

            // Double every second digit
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        // If sum is divisible by 10, the number is valid
        return sum % 10 == 0;
    }

    /**
     * Masks a credit card number for display, showing only the last 4 digits.
     *
     * @param cardNumber the credit card number to mask
     * @return the masked card number or null if the input is invalid
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return null;
        }

        String normalizedCardNumber = cardNumber.replaceAll("[\\s-]", "");

        if (normalizedCardNumber.length() < 4) {
            return null;
        }

        int maskedLength = normalizedCardNumber.length() - 4;
        StringBuilder masked = new StringBuilder();

        for (int i = 0; i < maskedLength; i++) {
            masked.append("*");
        }

        masked.append(normalizedCardNumber.substring(maskedLength));

        return masked.toString();
    }

    /**
     * Validates if the provided credit card number is valid according to the annotation configuration.
     *
     * @param cardNumber the credit card number to validate
     * @param context the constraint validator context
     * @return true if the credit card number is valid, false otherwise
     */
    @Override
    public boolean isValid(String cardNumber, ConstraintValidatorContext context) {
        if (!isValidCreditCard(cardNumber)) {
            return false;
        }

        // If no specific card types are specified in the annotation, accept all valid cards
        if (acceptedCardTypes == null || acceptedCardTypes.length == 0) {
            return true;
        }

        // Check if the card type is among the accepted types
        CardType cardType = getCardType(cardNumber.replaceAll("[\\s-]", ""));
        return Arrays.asList(acceptedCardTypes).contains(cardType);
    }
}
