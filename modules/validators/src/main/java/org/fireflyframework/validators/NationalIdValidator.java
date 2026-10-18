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

import org.fireflyframework.annotations.ValidNationalId;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validator for national identification document numbers.
 * 
 * This validator checks if a national ID is valid according to country-specific rules.
 * Supported document types include:
 * - Spanish DNI/NIE
 * - Brazilian CPF
 * - US SSN
 * - UK National Insurance Number
 * - And others depending on the country
 */
public class NationalIdValidator implements ConstraintValidator<ValidNationalId, String> {

    private String countryCode;

    // Regex patterns for different national ID formats
    private static final Pattern SPANISH_DNI_PATTERN = Pattern.compile("^\\d{8}[A-Z]$");
    private static final Pattern SPANISH_NIE_PATTERN = Pattern.compile("^[XYZ]\\d{7}[A-Z]$");
    private static final Pattern US_SSN_PATTERN = Pattern.compile("^(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}$");
    private static final Pattern BRAZIL_CPF_PATTERN = Pattern.compile("^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$");
    private static final Pattern UK_NINO_PATTERN = Pattern.compile("^[A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z]\\d{6}[A-D]$");
    private static final Pattern MEXICO_RFC_PATTERN = Pattern.compile("^[A-Z&Ñ]{3,4}\\d{6}[A-Z\\d]{3,8}$");
    private static final Pattern ARGENTINA_DNI_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern COLOMBIA_CC_PATTERN = Pattern.compile("^\\d{8,10}$");
    private static final Pattern CHILE_RUT_PATTERN = Pattern.compile("^\\d{7,8}-[0-9K]$");
    private static final Pattern FRANCE_INSEE_PATTERN = Pattern.compile("^\\d{13}$");
    private static final Pattern GERMANY_PERSONALAUSWEIS_PATTERN = Pattern.compile("^[A-Z0-9]{9}$");
    private static final Pattern ITALY_CODICE_FISCALE_PATTERN = Pattern.compile("^[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]$");

    // Map of country codes to their validation functions
    private static final Map<String, java.util.function.Predicate<String>> COUNTRY_VALIDATORS = new HashMap<>();

    static {
        COUNTRY_VALIDATORS.put("ES", NationalIdValidator::validateSpanishId);
        COUNTRY_VALIDATORS.put("US", id -> US_SSN_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("BR", NationalIdValidator::validateBrazilianCPF);
        COUNTRY_VALIDATORS.put("GB", id -> UK_NINO_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("MX", id -> MEXICO_RFC_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("AR", id -> ARGENTINA_DNI_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("CO", id -> COLOMBIA_CC_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("CL", NationalIdValidator::validateChileanRUT);
        COUNTRY_VALIDATORS.put("FR", id -> FRANCE_INSEE_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("DE", id -> GERMANY_PERSONALAUSWEIS_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("IT", NationalIdValidator::validateItalianCodiceFiscale);
    }

    @Override
    public void initialize(ValidNationalId constraintAnnotation) {
        this.countryCode = constraintAnnotation.country();
    }

    /**
     * Validates if the provided national ID is valid.
     *
     * @param nationalId the national ID to validate
     * @param context the constraint validator context
     * @return true if the national ID is valid, false otherwise
     */
    @Override
    public boolean isValid(String nationalId, ConstraintValidatorContext context) {
        if (nationalId == null || nationalId.isEmpty()) {
            return false;
        }

        // If country code is specified, use the specific validator
        if (countryCode != null && !countryCode.isEmpty()) {
            java.util.function.Predicate<String> validator = COUNTRY_VALIDATORS.get(countryCode.toUpperCase());
            if (validator != null) {
                return validator.test(nationalId);
            }
        }

        // If no country code is specified or not found, try to determine from format
        return detectAndValidate(nationalId);
    }

    /**
     * Attempts to detect the country format and validate accordingly.
     *
     * @param nationalId the national ID to validate
     * @return true if the national ID is valid for any supported country, false otherwise
     */
    private boolean detectAndValidate(String nationalId) {
        // Try Spanish DNI/NIE
        if (validateSpanishId(nationalId)) {
            return true;
        }

        // Try US SSN
        if (US_SSN_PATTERN.matcher(nationalId).matches()) {
            return true;
        }

        // Try Brazilian CPF
        if (validateBrazilianCPF(nationalId)) {
            return true;
        }

        // Try UK National Insurance Number
        if (UK_NINO_PATTERN.matcher(nationalId).matches()) {
            return true;
        }

        // Try Chilean RUT
        if (validateChileanRUT(nationalId)) {
            return true;
        }

        // Try Italian Codice Fiscale
        if (validateItalianCodiceFiscale(nationalId)) {
            return true;
        }

        // Try Mexico RFC
        if (MEXICO_RFC_PATTERN.matcher(nationalId).matches()) {
            return true;
        }

        // Try Argentina DNI
        return ARGENTINA_DNI_PATTERN.matcher(nationalId).matches();

        // No valid format detected
    }

    /**
     * Validates a Spanish DNI or NIE.
     *
     * @param id the ID to validate
     * @return true if the ID is a valid Spanish DNI or NIE, false otherwise
     */
    private static boolean validateSpanishId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        // Remove any spaces or hyphens
        id = id.replaceAll("[\\s-]", "").toUpperCase();

        // Check if it's a DNI or NIE
        boolean isDNI = SPANISH_DNI_PATTERN.matcher(id).matches();
        boolean isNIE = SPANISH_NIE_PATTERN.matcher(id).matches();

        if (!isDNI && !isNIE) {
            return false;
        }

        // Validation logic for Spanish DNI/NIE
        String digits;
        if (isDNI) {
            digits = id.substring(0, 8);
        } else {
            // For NIE, replace the first letter with its corresponding number
            char firstChar = id.charAt(0);
            String replacement = (firstChar == 'X') ? "0" : (firstChar == 'Y') ? "1" : "2";
            digits = replacement + id.substring(1, 8);
        }

        // Calculate the check letter
        String letters = "TRWAGMYFPDXBNJZSQVHLCKE";
        int mod = Integer.parseInt(digits) % 23;
        char expectedLetter = letters.charAt(mod);

        // Compare with the actual letter
        return id.charAt(id.length() - 1) == expectedLetter;
    }

    /**
     * Validates a Brazilian CPF.
     *
     * @param cpf the CPF to validate
     * @return true if the CPF is valid, false otherwise
     */
    private static boolean validateBrazilianCPF(String cpf) {
        if (cpf == null || cpf.isEmpty()) {
            return false;
        }

        // Remove any non-digit characters
        cpf = cpf.replaceAll("\\D", "");

        // CPF must have 11 digits
        if (cpf.length() != 11) {
            return false;
        }

        // Check if all digits are the same (invalid CPF)
        boolean allDigitsSame = true;
        for (int i = 1; i < cpf.length(); i++) {
            if (cpf.charAt(i) != cpf.charAt(0)) {
                allDigitsSame = false;
                break;
            }
        }
        if (allDigitsSame) {
            return false;
        }

        // Calculate first verification digit
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
        }
        int remainder = sum % 11;
        int firstVerificationDigit = (remainder < 2) ? 0 : 11 - remainder;

        // Check first verification digit
        if (Character.getNumericValue(cpf.charAt(9)) != firstVerificationDigit) {
            return false;
        }

        // Calculate second verification digit
        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
        }
        remainder = sum % 11;
        int secondVerificationDigit = (remainder < 2) ? 0 : 11 - remainder;

        // Check second verification digit
        return Character.getNumericValue(cpf.charAt(10)) == secondVerificationDigit;
    }

    /**
     * Validates a Chilean RUT.
     *
     * @param rut the RUT to validate
     * @return true if the RUT is valid, false otherwise
     */
    private static boolean validateChileanRUT(String rut) {
        if (rut == null || rut.isEmpty()) {
            return false;
        }

        // Check basic format
        if (!CHILE_RUT_PATTERN.matcher(rut).matches()) {
            return false;
        }

        // Split into number and verification digit
        String[] parts = rut.split("-");
        String number = parts[0].replaceAll("\\D", "");
        String verifier = parts[1];

        // Calculate verification digit
        int sum = 0;
        int multiplier = 2;
        for (int i = number.length() - 1; i >= 0; i--) {
            sum += Character.getNumericValue(number.charAt(i)) * multiplier;
            multiplier = multiplier == 7 ? 2 : multiplier + 1;
        }
        int remainder = sum % 11;
        String expectedVerifier = (remainder == 0) ? "0" : (remainder == 1) ? "K" : String.valueOf(11 - remainder);

        // Compare with the actual verifier
        return verifier.equals(expectedVerifier);
    }

    /**
     * Validates an Italian Codice Fiscale.
     *
     * @param cf the Codice Fiscale to validate
     * @return true if the Codice Fiscale is valid, false otherwise
     */
    private static boolean validateItalianCodiceFiscale(String cf) {
        if (cf == null || cf.isEmpty()) {
            return false;
        }

        // Check basic format
        return ITALY_CODICE_FISCALE_PATTERN.matcher(cf.toUpperCase()).matches();

        // More detailed validation could be added here
        // For now, we just check the pattern
    }

    /**
     * Gets the country code from a national ID if possible.
     *
     * @param nationalId the national ID
     * @return the detected country code or null if not detected
     */
    public String detectCountry(String nationalId) {
        if (nationalId == null || nationalId.isEmpty()) {
            return null;
        }

        // Try to detect country from format
        if (validateSpanishId(nationalId)) {
            return "ES";
        } else if (US_SSN_PATTERN.matcher(nationalId).matches()) {
            return "US";
        } else if (validateBrazilianCPF(nationalId)) {
            return "BR";
        } else if (UK_NINO_PATTERN.matcher(nationalId).matches()) {
            return "GB";
        } else if (validateChileanRUT(nationalId)) {
            return "CL";
        } else if (validateItalianCodiceFiscale(nationalId)) {
            return "IT";
        }

        return null;
    }

    /**
     * Formats a national ID according to the country's standard format.
     *
     * @param nationalId the national ID to format
     * @param countryCode the country code
     * @return the formatted national ID or null if invalid
     */
    public String format(String nationalId, String countryCode) {
        if (nationalId == null || nationalId.isEmpty() || countryCode == null || countryCode.isEmpty()) {
            return null;
        }

        // Normalize the ID (remove spaces, hyphens, etc.)
        String normalized = nationalId.replaceAll("[\\s-.]", "").toUpperCase();

        switch (countryCode.toUpperCase()) {
            case "US":
                // Format as XXX-XX-XXXX
                if (normalized.length() == 9) {
                    return normalized.substring(0, 3) + "-" + 
                           normalized.substring(3, 5) + "-" + 
                           normalized.substring(5);
                }
                break;
            case "BR":
                // Format as XXX.XXX.XXX-XX
                if (normalized.length() == 11) {
                    return normalized.substring(0, 3) + "." + 
                           normalized.substring(3, 6) + "." + 
                           normalized.substring(6, 9) + "-" + 
                           normalized.substring(9);
                }
                break;
            case "CL":
                // Format as XXXXXXXX-X
                if (normalized.length() >= 8 && normalized.length() <= 9) {
                    String number = normalized.substring(0, normalized.length() - 1);
                    String verifier = normalized.substring(normalized.length() - 1);
                    return number + "-" + verifier;
                }
                break;
        }

        return null;
    }
}
