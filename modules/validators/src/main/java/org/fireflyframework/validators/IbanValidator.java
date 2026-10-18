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

import org.fireflyframework.annotations.ValidIban;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validator for International Bank Account Numbers (IBAN).
 * 
 * This validator checks if an IBAN is valid according to the standard format
 * which includes:
 * - Country code validation
 * - Check digit validation
 * - Length validation based on country
 * - Character set validation
 */
public class IbanValidator implements ConstraintValidator<ValidIban, String> {
    
    // Map of country codes to their respective IBAN lengths
    private static final Map<String, Integer> COUNTRY_CODE_TO_LENGTH = new HashMap<>();
    
    // IBAN format: country code (2 letters) + check digits (2 digits) + BBAN (Basic Bank Account Number)
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
    
    static {
        // Initialize the map with country codes and their IBAN lengths
        COUNTRY_CODE_TO_LENGTH.put("AL", 28); // Albania
        COUNTRY_CODE_TO_LENGTH.put("AD", 24); // Andorra
        COUNTRY_CODE_TO_LENGTH.put("AT", 20); // Austria
        COUNTRY_CODE_TO_LENGTH.put("AZ", 28); // Azerbaijan
        COUNTRY_CODE_TO_LENGTH.put("BH", 22); // Bahrain
        COUNTRY_CODE_TO_LENGTH.put("BY", 28); // Belarus
        COUNTRY_CODE_TO_LENGTH.put("BE", 16); // Belgium
        COUNTRY_CODE_TO_LENGTH.put("BA", 20); // Bosnia and Herzegovina
        COUNTRY_CODE_TO_LENGTH.put("BR", 29); // Brazil
        COUNTRY_CODE_TO_LENGTH.put("BG", 22); // Bulgaria
        COUNTRY_CODE_TO_LENGTH.put("CR", 22); // Costa Rica
        COUNTRY_CODE_TO_LENGTH.put("HR", 21); // Croatia
        COUNTRY_CODE_TO_LENGTH.put("CY", 28); // Cyprus
        COUNTRY_CODE_TO_LENGTH.put("CZ", 24); // Czech Republic
        COUNTRY_CODE_TO_LENGTH.put("DK", 18); // Denmark
        COUNTRY_CODE_TO_LENGTH.put("DO", 28); // Dominican Republic
        COUNTRY_CODE_TO_LENGTH.put("EG", 29); // Egypt
        COUNTRY_CODE_TO_LENGTH.put("SV", 28); // El Salvador
        COUNTRY_CODE_TO_LENGTH.put("EE", 20); // Estonia
        COUNTRY_CODE_TO_LENGTH.put("FO", 18); // Faroe Islands
        COUNTRY_CODE_TO_LENGTH.put("FI", 18); // Finland
        COUNTRY_CODE_TO_LENGTH.put("FR", 27); // France
        COUNTRY_CODE_TO_LENGTH.put("GE", 22); // Georgia
        COUNTRY_CODE_TO_LENGTH.put("DE", 22); // Germany
        COUNTRY_CODE_TO_LENGTH.put("GI", 23); // Gibraltar
        COUNTRY_CODE_TO_LENGTH.put("GR", 27); // Greece
        COUNTRY_CODE_TO_LENGTH.put("GL", 18); // Greenland
        COUNTRY_CODE_TO_LENGTH.put("GT", 28); // Guatemala
        COUNTRY_CODE_TO_LENGTH.put("HU", 28); // Hungary
        COUNTRY_CODE_TO_LENGTH.put("IS", 26); // Iceland
        COUNTRY_CODE_TO_LENGTH.put("IQ", 23); // Iraq
        COUNTRY_CODE_TO_LENGTH.put("IE", 22); // Ireland
        COUNTRY_CODE_TO_LENGTH.put("IL", 23); // Israel
        COUNTRY_CODE_TO_LENGTH.put("IT", 27); // Italy
        COUNTRY_CODE_TO_LENGTH.put("JO", 30); // Jordan
        COUNTRY_CODE_TO_LENGTH.put("KZ", 20); // Kazakhstan
        COUNTRY_CODE_TO_LENGTH.put("XK", 20); // Kosovo
        COUNTRY_CODE_TO_LENGTH.put("KW", 30); // Kuwait
        COUNTRY_CODE_TO_LENGTH.put("LV", 21); // Latvia
        COUNTRY_CODE_TO_LENGTH.put("LB", 28); // Lebanon
        COUNTRY_CODE_TO_LENGTH.put("LI", 21); // Liechtenstein
        COUNTRY_CODE_TO_LENGTH.put("LT", 20); // Lithuania
        COUNTRY_CODE_TO_LENGTH.put("LU", 20); // Luxembourg
        COUNTRY_CODE_TO_LENGTH.put("MK", 19); // North Macedonia
        COUNTRY_CODE_TO_LENGTH.put("MT", 31); // Malta
        COUNTRY_CODE_TO_LENGTH.put("MR", 27); // Mauritania
        COUNTRY_CODE_TO_LENGTH.put("MU", 30); // Mauritius
        COUNTRY_CODE_TO_LENGTH.put("MD", 24); // Moldova
        COUNTRY_CODE_TO_LENGTH.put("MC", 27); // Monaco
        COUNTRY_CODE_TO_LENGTH.put("ME", 22); // Montenegro
        COUNTRY_CODE_TO_LENGTH.put("NL", 18); // Netherlands
        COUNTRY_CODE_TO_LENGTH.put("NO", 15); // Norway
        COUNTRY_CODE_TO_LENGTH.put("PK", 24); // Pakistan
        COUNTRY_CODE_TO_LENGTH.put("PS", 29); // Palestine
        COUNTRY_CODE_TO_LENGTH.put("PL", 28); // Poland
        COUNTRY_CODE_TO_LENGTH.put("PT", 25); // Portugal
        COUNTRY_CODE_TO_LENGTH.put("QA", 29); // Qatar
        COUNTRY_CODE_TO_LENGTH.put("RO", 24); // Romania
        COUNTRY_CODE_TO_LENGTH.put("RU", 33); // Russia
        COUNTRY_CODE_TO_LENGTH.put("LC", 32); // Saint Lucia
        COUNTRY_CODE_TO_LENGTH.put("SM", 27); // San Marino
        COUNTRY_CODE_TO_LENGTH.put("ST", 25); // Sao Tome and Principe
        COUNTRY_CODE_TO_LENGTH.put("SA", 24); // Saudi Arabia
        COUNTRY_CODE_TO_LENGTH.put("RS", 22); // Serbia
        COUNTRY_CODE_TO_LENGTH.put("SC", 31); // Seychelles
        COUNTRY_CODE_TO_LENGTH.put("SK", 24); // Slovakia
        COUNTRY_CODE_TO_LENGTH.put("SI", 19); // Slovenia
        COUNTRY_CODE_TO_LENGTH.put("ES", 24); // Spain
        COUNTRY_CODE_TO_LENGTH.put("SE", 24); // Sweden
        COUNTRY_CODE_TO_LENGTH.put("CH", 21); // Switzerland
        COUNTRY_CODE_TO_LENGTH.put("TL", 23); // Timor-Leste
        COUNTRY_CODE_TO_LENGTH.put("TN", 24); // Tunisia
        COUNTRY_CODE_TO_LENGTH.put("TR", 26); // Turkey
        COUNTRY_CODE_TO_LENGTH.put("UA", 29); // Ukraine
        COUNTRY_CODE_TO_LENGTH.put("AE", 23); // United Arab Emirates
        COUNTRY_CODE_TO_LENGTH.put("GB", 22); // United Kingdom
        COUNTRY_CODE_TO_LENGTH.put("VA", 22); // Vatican City
        COUNTRY_CODE_TO_LENGTH.put("VG", 24); // Virgin Islands, British
    }
    
    /**
     * Validates if the provided IBAN is valid.
     *
     * @param iban the IBAN to validate
     * @return true if the IBAN is valid, false otherwise
     */
    @Override
    public boolean isValid(String iban, ConstraintValidatorContext context) {
        if (iban == null || iban.isEmpty()) {
            return false;
        }
        
        // Remove spaces and convert to uppercase
        String normalizedIban = iban.replaceAll("\\s", "").toUpperCase();
        
        // Check basic format
        if (!IBAN_PATTERN.matcher(normalizedIban).matches()) {
            return false;
        }
        
        // Check country code and length
        String countryCode = normalizedIban.substring(0, 2);
        if (!COUNTRY_CODE_TO_LENGTH.containsKey(countryCode)) {
            return false;
        }
        
        int expectedLength = COUNTRY_CODE_TO_LENGTH.get(countryCode);
        if (normalizedIban.length() != expectedLength) {
            return false;
        }
        
        // Check digits validation using MOD 97-10 algorithm (ISO 7064)
        return validateCheckDigits(normalizedIban);
    }
    
    /**
     * Validates the check digits of an IBAN using the MOD 97-10 algorithm.
     *
     * @param iban the normalized IBAN (uppercase, no spaces)
     * @return true if the check digits are valid, false otherwise
     */
    private boolean validateCheckDigits(String iban) {
        // Move the first 4 characters to the end
        String rearrangedIban = iban.substring(4) + iban.substring(0, 4);
        
        // Convert letters to numbers (A=10, B=11, ..., Z=35)
        StringBuilder numericIban = new StringBuilder();
        for (char c : rearrangedIban.toCharArray()) {
            if (Character.isLetter(c)) {
                numericIban.append(Character.getNumericValue(c));
            } else {
                numericIban.append(c);
            }
        }
        
        // Calculate MOD 97-10
        long remainder = 0;
        for (int i = 0; i < numericIban.length(); i++) {
            remainder = (remainder * 10 + Character.getNumericValue(numericIban.charAt(i))) % 97;
        }
        
        // If the remainder is 1, the IBAN is valid
        return remainder == 1;
    }
}