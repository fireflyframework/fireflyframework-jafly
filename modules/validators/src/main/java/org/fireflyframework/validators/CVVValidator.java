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

import org.fireflyframework.annotations.ValidCVV;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for Card Verification Values (CVV).
 * 
 * This validator checks if a CVV is valid according to:
 * - Length constraints (3 digits for standard cards, 4 digits for American Express)
 * - Character constraints (numeric only)
 */
public class CVVValidator implements ConstraintValidator<ValidCVV, String> {

    private ValidCVV.CardType cardType;
    
    // Pattern for 3-digit CVV (Visa, MasterCard, Discover, etc.)
    private static final Pattern THREE_DIGIT_CVV_PATTERN = Pattern.compile("^\\d{3}$");
    
    // Pattern for 4-digit CVV (American Express)
    private static final Pattern FOUR_DIGIT_CVV_PATTERN = Pattern.compile("^\\d{4}$");

    @Override
    public void initialize(ValidCVV constraintAnnotation) {
        this.cardType = constraintAnnotation.cardType();
    }

    /**
     * Validates if the provided CVV is valid.
     *
     * @param cvv the CVV to validate
     * @param context the constraint validator context
     * @return true if the CVV is valid, false otherwise
     */
    @Override
    public boolean isValid(String cvv, ConstraintValidatorContext context) {
        if (cvv == null || cvv.isEmpty()) {
            return false;
        }
        
        // Remove any spaces or non-digit characters
        String normalizedCVV = cvv.replaceAll("\\D", "");

        return switch (cardType) {
            case STANDARD -> THREE_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches();
            case AMEX -> FOUR_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches();
            default -> THREE_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches() ||
                    FOUR_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches();
        };
    }

    /**
     * Validates if the provided CVV is valid for a specific card type.
     *
     * @param cvv the CVV to validate
     * @param cardType the card type
     * @return true if the CVV is valid for the card type, false otherwise
     */
    public boolean isValidForCardType(String cvv, ValidCVV.CardType cardType) {
        if (cvv == null || cvv.isEmpty()) {
            return false;
        }
        
        // Remove any spaces or non-digit characters
        String normalizedCVV = cvv.replaceAll("\\D", "");

        return switch (cardType) {
            case STANDARD -> THREE_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches();
            case AMEX -> FOUR_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches();
            default -> THREE_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches() ||
                    FOUR_DIGIT_CVV_PATTERN.matcher(normalizedCVV).matches();
        };
    }

    /**
     * Validates if the provided CVV is valid for a specific credit card number.
     * This method attempts to determine the card type from the card number.
     *
     * @param cvv the CVV to validate
     * @param cardNumber the credit card number
     * @return true if the CVV is valid for the card type, false otherwise
     */
    public boolean isValidForCardNumber(String cvv, String cardNumber) {
        if (cvv == null || cvv.isEmpty() || cardNumber == null || cardNumber.isEmpty()) {
            return false;
        }
        
        // Remove any spaces or non-digit characters
        String normalizedCardNumber = cardNumber.replaceAll("\\D", "");
        
        // Determine card type from card number
        ValidCVV.CardType detectedCardType = detectCardTypeFromNumber(normalizedCardNumber);
        
        // Validate CVV for the detected card type
        return isValidForCardType(cvv, detectedCardType);
    }

    /**
     * Detects the card type from a credit card number.
     *
     * @param cardNumber the credit card number
     * @return the detected card type or ANY if the card type cannot be determined
     */
    private ValidCVV.CardType detectCardTypeFromNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return ValidCVV.CardType.ANY;
        }
        
        // American Express starts with 34 or 37 and has 15 digits
        if ((cardNumber.startsWith("34") || cardNumber.startsWith("37")) && cardNumber.length() == 15) {
            return ValidCVV.CardType.AMEX;
        }
        
        // Visa starts with 4
        if (cardNumber.startsWith("4")) {
            return ValidCVV.CardType.STANDARD;
        }
        
        // MasterCard starts with 51-55 or 2221-2720
        if ((cardNumber.startsWith("51") || cardNumber.startsWith("52") || 
             cardNumber.startsWith("53") || cardNumber.startsWith("54") || 
             cardNumber.startsWith("55")) || 
            (cardNumber.length() >= 4 && 
             Integer.parseInt(cardNumber.substring(0, 4)) >= 2221 && 
             Integer.parseInt(cardNumber.substring(0, 4)) <= 2720)) {
            return ValidCVV.CardType.STANDARD;
        }
        
        // Discover starts with 6011, 622126-622925, 644-649, or 65
        if (cardNumber.startsWith("6011") || 
            (cardNumber.length() >= 6 && 
             Integer.parseInt(cardNumber.substring(0, 6)) >= 622126 && 
             Integer.parseInt(cardNumber.substring(0, 6)) <= 622925) || 
            (cardNumber.length() >= 3 && 
             Integer.parseInt(cardNumber.substring(0, 3)) >= 644 && 
             Integer.parseInt(cardNumber.substring(0, 3)) <= 649) || 
            cardNumber.startsWith("65")) {
            return ValidCVV.CardType.STANDARD;
        }
        
        // Default to ANY if the card type cannot be determined
        return ValidCVV.CardType.ANY;
    }

    /**
     * Masks a CVV for display, showing only asterisks.
     *
     * @param cvv the CVV to mask
     * @return the masked CVV or null if invalid
     */
    public String maskCVV(String cvv) {
        if (cvv == null || cvv.isEmpty()) {
            return null;
        }
        
        // Remove any spaces or non-digit characters
        String normalizedCVV = cvv.replaceAll("\\D", "");
        
        // Check if the CVV is valid
        if (!isValid(normalizedCVV, null)) {
            return null;
        }
        
        // Mask all digits with asterisks
        return "*".repeat(normalizedCVV.length());
    }
}