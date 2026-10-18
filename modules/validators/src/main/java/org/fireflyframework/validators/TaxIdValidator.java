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

import org.fireflyframework.annotations.ValidTaxId;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validator for tax identification numbers.
 * 
 * This validator checks if a tax ID is valid according to country-specific rules.
 * Supported tax ID types include:
 * - US TIN (Tax Identification Number)
 * - Mexico RFC (Registro Federal de Contribuyentes)
 * - Argentina CUIT (Código Único de Identificación Tributaria)
 * - Spain NIF (Número de Identificación Fiscal)
 * - And others depending on the country
 */
public class TaxIdValidator implements ConstraintValidator<ValidTaxId, String> {

    private String countryCode;

    // Regex patterns for different tax ID formats
    private static final Pattern US_EIN_PATTERN = Pattern.compile("^\\d{2}-\\d{7}$");
    private static final Pattern US_SSN_TIN_PATTERN = Pattern.compile("^\\d{3}-\\d{2}-\\d{4}$");
    private static final Pattern MEXICO_RFC_PATTERN = Pattern.compile("^[A-Z&Ñ]{3,4}\\d{6}[A-Z\\d]{3}$");
    private static final Pattern ARGENTINA_CUIT_PATTERN = Pattern.compile("^\\d{2}-\\d{8}-\\d{1}$");
    private static final Pattern SPAIN_NIF_PATTERN = Pattern.compile("^[0-9A-Z][0-9]{7}[A-Z]$");
    private static final Pattern BRAZIL_CNPJ_PATTERN = Pattern.compile("^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$");
    private static final Pattern UK_UTR_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern FRANCE_SIRET_PATTERN = Pattern.compile("^\\d{14}$");
    private static final Pattern GERMANY_STEUERNUMMER_PATTERN = Pattern.compile("^\\d{10,11}$");
    private static final Pattern ITALY_CODICE_FISCALE_PATTERN = Pattern.compile("^[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]$");
    private static final Pattern CANADA_BN_PATTERN = Pattern.compile("^\\d{9}$");

    // Map of country codes to their validation functions
    private static final Map<String, java.util.function.Predicate<String>> COUNTRY_VALIDATORS = new HashMap<>();

    static {
        COUNTRY_VALIDATORS.put("US", TaxIdValidator::validateUSTaxId);
        COUNTRY_VALIDATORS.put("MX", id -> MEXICO_RFC_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("AR", TaxIdValidator::validateArgentinaCUIT);
        COUNTRY_VALIDATORS.put("ES", TaxIdValidator::validateSpainNIF);
        COUNTRY_VALIDATORS.put("BR", TaxIdValidator::validateBrazilCNPJ);
        COUNTRY_VALIDATORS.put("GB", id -> UK_UTR_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("FR", id -> FRANCE_SIRET_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("DE", id -> GERMANY_STEUERNUMMER_PATTERN.matcher(id).matches());
        COUNTRY_VALIDATORS.put("IT", id -> ITALY_CODICE_FISCALE_PATTERN.matcher(id.toUpperCase()).matches());
        COUNTRY_VALIDATORS.put("CA", id -> CANADA_BN_PATTERN.matcher(id).matches());
    }

    @Override
    public void initialize(ValidTaxId constraintAnnotation) {
        this.countryCode = constraintAnnotation.country();
    }

    /**
     * Validates if the provided tax ID is valid.
     *
     * @param taxId the tax ID to validate
     * @param context the constraint validator context
     * @return true if the tax ID is valid, false otherwise
     */
    @Override
    public boolean isValid(String taxId, ConstraintValidatorContext context) {
        if (taxId == null || taxId.isEmpty()) {
            return false;
        }

        // If country code is specified, use the specific validator
        if (countryCode != null && !countryCode.isEmpty()) {
            java.util.function.Predicate<String> validator = COUNTRY_VALIDATORS.get(countryCode.toUpperCase());
            if (validator != null) {
                return validator.test(taxId);
            }
        }

        // If no country code is specified or not found, try to determine from format
        return detectAndValidate(taxId);
    }

    /**
     * Attempts to detect the country format and validate accordingly.
     *
     * @param taxId the tax ID to validate
     * @return true if the tax ID is valid for any supported country, false otherwise
     */
    private boolean detectAndValidate(String taxId) {
        // Try US TIN
        if (validateUSTaxId(taxId)) {
            return true;
        }

        // Try Mexico RFC
        if (MEXICO_RFC_PATTERN.matcher(taxId).matches()) {
            return true;
        }

        // Try Argentina CUIT
        if (validateArgentinaCUIT(taxId)) {
            return true;
        }

        // Try Spain NIF
        if (validateSpainNIF(taxId)) {
            return true;
        }

        // Try Brazil CNPJ
        if (validateBrazilCNPJ(taxId)) {
            return true;
        }

        // Try UK UTR
        return UK_UTR_PATTERN.matcher(taxId).matches();

        // No valid format detected
    }

    /**
     * Validates a US Tax Identification Number (TIN).
     * This could be an EIN (XX-XXXXXXX) or SSN used as TIN (XXX-XX-XXXX).
     *
     * @param tin the TIN to validate
     * @return true if the TIN is valid, false otherwise
     */
    private static boolean validateUSTaxId(String tin) {
        if (tin == null || tin.isEmpty()) {
            return false;
        }

        // Check if it's an EIN or SSN format
        boolean isEIN = US_EIN_PATTERN.matcher(tin).matches();
        boolean isSSN = US_SSN_TIN_PATTERN.matcher(tin).matches();

        return isEIN || isSSN;
    }

    /**
     * Validates an Argentina CUIT (Código Único de Identificación Tributaria).
     *
     * @param cuit the CUIT to validate
     * @return true if the CUIT is valid, false otherwise
     */
    private static boolean validateArgentinaCUIT(String cuit) {
        if (cuit == null || cuit.isEmpty()) {
            return false;
        }

        // Remove non-digit characters for validation
        String normalizedCuit = cuit.replaceAll("\\D", "");

        // CUIT must have 11 digits
        if (normalizedCuit.length() != 11) {
            return false;
        }

        // Calculate verification digit
        int[] multipliers = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int sum = 0;

        for (int i = 0; i < 10; i++) {
            int digit = Character.getNumericValue(normalizedCuit.charAt(i));
            sum += digit * multipliers[i];
        }

        int remainder = sum % 11;
        int verificationDigit = 11 - remainder;

        // If remainder is 0, verification digit is 0
        if (verificationDigit == 11) {
            verificationDigit = 0;
        }

        // Compare with the actual verification digit
        int actualDigit = Character.getNumericValue(normalizedCuit.charAt(10));

        // Return true if the verification digit matches
        return actualDigit == verificationDigit;
    }

    /**
     * Validates a Spain NIF (Número de Identificación Fiscal).
     *
     * @param nif the NIF to validate
     * @return true if the NIF is valid, false otherwise
     */
    private static boolean validateSpainNIF(String nif) {
        if (nif == null || nif.isEmpty()) {
            return false;
        }

        // Remove any spaces or hyphens
        nif = nif.replaceAll("[\\s-]", "").toUpperCase();

        // Check basic format
        if (!SPAIN_NIF_PATTERN.matcher(nif).matches()) {
            return false;
        }

        // Validation logic for Spanish NIF
        String digits = nif.substring(0, 8);
        char letter = nif.charAt(8);

        // Calculate the check letter
        String letters = "TRWAGMYFPDXBNJZSQVHLCKE";
        int mod = Integer.parseInt(digits) % 23;
        char expectedLetter = letters.charAt(mod);

        // Compare with the actual letter
        return letter == expectedLetter;
    }

    /**
     * Validates a Brazilian CNPJ (Cadastro Nacional da Pessoa Jurídica).
     *
     * @param cnpj the CNPJ to validate
     * @return true if the CNPJ is valid, false otherwise
     */
    private static boolean validateBrazilCNPJ(String cnpj) {
        if (cnpj == null || cnpj.isEmpty()) {
            return false;
        }

        // Remove any non-digit characters
        cnpj = cnpj.replaceAll("\\D", "");

        // CNPJ must have 14 digits
        if (cnpj.length() != 14) {
            return false;
        }

        // Check if all digits are the same (invalid CNPJ)
        boolean allDigitsSame = true;
        for (int i = 1; i < cnpj.length(); i++) {
            if (cnpj.charAt(i) != cnpj.charAt(0)) {
                allDigitsSame = false;
                break;
            }
        }
        if (allDigitsSame) {
            return false;
        }

        // Calculate first verification digit
        int sum = 0;
        int weight = 2;
        for (int i = 11; i >= 0; i--) {
            sum += Character.getNumericValue(cnpj.charAt(i)) * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        int firstVerificationDigit = remainder < 2 ? 0 : 11 - remainder;

        // Check first verification digit
        if (Character.getNumericValue(cnpj.charAt(12)) != firstVerificationDigit) {
            return false;
        }

        // Calculate second verification digit
        sum = 0;
        weight = 2;
        for (int i = 12; i >= 0; i--) {
            sum += Character.getNumericValue(cnpj.charAt(i)) * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        remainder = sum % 11;
        int secondVerificationDigit = remainder < 2 ? 0 : 11 - remainder;

        // Check second verification digit
        return Character.getNumericValue(cnpj.charAt(13)) == secondVerificationDigit;
    }

    /**
     * Gets the country code from a tax ID if possible.
     *
     * @param taxId the tax ID
     * @return the detected country code or null if not detected
     */
    public String detectCountry(String taxId) {
        if (taxId == null || taxId.isEmpty()) {
            return null;
        }

        // Try to detect country from format
        if (validateUSTaxId(taxId)) {
            return "US";
        } else if (MEXICO_RFC_PATTERN.matcher(taxId).matches()) {
            return "MX";
        } else if (validateArgentinaCUIT(taxId)) {
            return "AR";
        } else if (validateSpainNIF(taxId)) {
            return "ES";
        } else if (validateBrazilCNPJ(taxId)) {
            return "BR";
        } else if (UK_UTR_PATTERN.matcher(taxId).matches()) {
            return "GB";
        }

        return null;
    }

    /**
     * Formats a tax ID according to the country's standard format.
     *
     * @param taxId the tax ID to format
     * @param countryCode the country code
     * @return the formatted tax ID or null if invalid
     */
    public String format(String taxId, String countryCode) {
        if (taxId == null || taxId.isEmpty() || countryCode == null || countryCode.isEmpty()) {
            return null;
        }

        // Normalize the ID (remove spaces, hyphens, etc.)
        String normalized = taxId.replaceAll("[\\s-./]", "").toUpperCase();

        switch (countryCode.toUpperCase()) {
            case "US":
                // Check if it's an EIN (9 digits) or SSN (9 digits)
                if (normalized.length() == 9) {
                    // If first two digits are typical for EIN, format as EIN
                    if (normalized.startsWith("1") || normalized.startsWith("2") || 
                        normalized.startsWith("3") || normalized.startsWith("8")) {
                        return normalized.substring(0, 2) + "-" + normalized.substring(2);
                    } else {
                        // Otherwise format as SSN
                        return normalized.substring(0, 3) + "-" + 
                               normalized.substring(3, 5) + "-" + 
                               normalized.substring(5);
                    }
                }
                break;
            case "AR":
                // Format as XX-XXXXXXXX-X
                if (normalized.length() == 11) {
                    return normalized.substring(0, 2) + "-" + 
                           normalized.substring(2, 10) + "-" + 
                           normalized.substring(10);
                }
                break;
            case "BR":
                // Format as XX.XXX.XXX/XXXX-XX
                if (normalized.length() == 14) {
                    return normalized.substring(0, 2) + "." + 
                           normalized.substring(2, 5) + "." + 
                           normalized.substring(5, 8) + "/" + 
                           normalized.substring(8, 12) + "-" + 
                           normalized.substring(12);
                }
                break;
        }

        return null;
    }
}
