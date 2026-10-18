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


package org.fireflyframework.web.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configuration properties for PII (Personally Identifiable Information) masking in logs.
 * This class provides comprehensive configuration options for detecting and masking various
 * types of sensitive data in log messages.
 * 
 * Example configuration:
 * <pre>
 * firefly:
 *   web:
 *     pii-masking:
 *       enabled: true
 *       mask-character: "*"
 *       preserve-length: true
 *       patterns:
 *         email: "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
 *         phone: "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"
 *         ssn: "\\b\\d{3}-?\\d{2}-?\\d{4}\\b"
 *         credit-card: "\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b"
 *       custom-patterns:
 *         api-key: "(?i)api[_-]?key[\"'\\s]*[:=][\"'\\s]*[a-zA-Z0-9]{20,}"
 *         jwt: "eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*"
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "firefly.web.pii-masking")
public class PiiMaskingProperties {

    /**
     * Whether PII masking is enabled globally
     */
    private boolean enabled = true;

    /**
     * Character to use for masking (default: *)
     */
    private String maskCharacter = "*";

    /**
     * Whether to preserve the original length of masked data
     */
    private boolean preserveLength = true;

    /**
     * Number of characters to show at the beginning and end (if preserve-length is false)
     */
    private int showCharacters = 3;

    /**
     * Built-in PII patterns that can be overridden
     */
    private Map<String, String> patterns = initializeDefaultPatterns();

    /**
     * Additional custom patterns for organization-specific PII
     */
    private Map<String, String> customPatterns = new HashMap<>();

    /**
     * Whether to mask headers (in addition to bodies and query params)
     */
    private boolean maskHeaders = true;

    /**
     * Whether to mask request/response bodies
     */
    private boolean maskBodies = true;

    /**
     * Whether to mask query parameters
     */
    private boolean maskQueryParams = true;

    /**
     * Whether to mask exception messages
     */
    private boolean maskExceptions = true;

    /**
     * Whether to automatically mask all application logs
     */
    private boolean autoMaskLogs = true;

    /**
     * Whether to enable stdout masking for System.out.println calls
     */
    private boolean enableStdoutMasking = true;

    /**
     * Case sensitivity for pattern matching
     */
    private boolean caseSensitive = false;

    private Map<String, String> initializeDefaultPatterns() {
        Map<String, String> defaultPatterns = new HashMap<>();
        
        // Email addresses
        defaultPatterns.put("email", "\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b");
        
        // US Phone numbers (various formats)
        defaultPatterns.put("phone-us", "\\b(?:\\+?1[-\\s]?)?(?:\\(?[0-9]{3}\\)?[-\\s]?)?[0-9]{3}[-\\s]?[0-9]{4}\\b");
        
        // European Telephone Numbers (Mobile and Landline)
        
        // Spain - Mobile: +34 6XX XXX XXX, Landline: +34 9XX XXX XXX
        defaultPatterns.put("phone-spain", "\\b(?:\\+34[-\\s]?)?(?:[679][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // France - Mobile: +33 6XX XX XX XX, Landline: +33 [1-5]XX XX XX XX
        defaultPatterns.put("phone-france", "\\b(?:\\+33[-\\s]?)?(?:[1-9][0-9]{2}[-\\s]?[0-9]{2}[-\\s]?[0-9]{2}[-\\s]?[0-9]{2})\\b");
        
        // Germany - Mobile: +49 1XX XXX XXXX, Landline: +49 XXX XXXXXXX
        defaultPatterns.put("phone-germany", "\\b(?:\\+49[-\\s]?)?(?:[1-9][0-9]{1,4}[-\\s]?[0-9]{3,}[-\\s]?[0-9]{3,})\\b");
        
        // Italy - Mobile: +39 3XX XXX XXXX, Landline: +39 0X XXX XXXX
        defaultPatterns.put("phone-italy", "\\b(?:\\+39[-\\s]?)?(?:[0-9]{2,4}[-\\s]?[0-9]{3,4}[-\\s]?[0-9]{3,4})\\b");
        
        // United Kingdom - Mobile: +44 7XXX XXXXXX, Landline: +44 1XXX XXXXXX / +44 20 XXXX XXXX
        defaultPatterns.put("phone-uk", "\\b(?:\\+44[-\\s]?)?(?:[1-9][0-9]{2,4}[-\\s]?[0-9]{3,6}[-\\s]?[0-9]{3,6})\\b");
        
        // Netherlands - Mobile: +31 6 XXXX XXXX, Landline: +31 XX XXX XXXX
        defaultPatterns.put("phone-netherlands", "\\b(?:\\+31[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{4}|6[-\\s]?[0-9]{4}[-\\s]?[0-9]{4})\\b");
        
        // Belgium - Mobile: +32 4XX XX XX XX, Landline: +32 X XXX XX XX
        defaultPatterns.put("phone-belgium", "\\b(?:\\+32[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{2}[-\\s]?[0-9]{2})\\b");
        
        // Portugal - Mobile: +351 9X XXX XXXX, Landline: +351 2XX XXX XXX
        defaultPatterns.put("phone-portugal", "\\b(?:\\+351[-\\s]?)?(?:[2-9][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // Austria - Mobile: +43 6XX XXX XXX, Landline: +43 1 XXX XXXX
        defaultPatterns.put("phone-austria", "\\b(?:\\+43[-\\s]?)?(?:[1-9][0-9]{2,3}[-\\s]?[0-9]{3,4}[-\\s]?[0-9]{3,4})\\b");
        
        // Switzerland - Mobile: +41 7X XXX XX XX, Landline: +41 XX XXX XX XX
        defaultPatterns.put("phone-switzerland", "\\b(?:\\+41[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{2}[-\\s]?[0-9]{2})\\b");
        
        // Sweden - Mobile: +46 70 XXX XX XX, Landline: +46 8 XXX XX XX
        defaultPatterns.put("phone-sweden", "\\b(?:\\+46[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{2}[-\\s]?[0-9]{2})\\b");
        
        // Norway - Mobile: +47 4XX XX XXX, Landline: +47 XX XX XX XX
        defaultPatterns.put("phone-norway", "\\b(?:\\+47[-\\s]?)?(?:[2-9][0-9]{2}[-\\s]?[0-9]{2}[-\\s]?[0-9]{3})\\b");
        
        // Denmark - Mobile: +45 XX XX XX XX, Landline: +45 XX XX XX XX (8 digits)
        defaultPatterns.put("phone-denmark", "\\b(?:\\+45[-\\s]?)?(?:[2-9][0-9][-\\s]?[0-9]{2}[-\\s]?[0-9]{2}[-\\s]?[0-9]{2})\\b");
        
        // Finland - Mobile: +358 4X XXX XXXX, Landline: +358 X XXX XXXX
        defaultPatterns.put("phone-finland", "\\b(?:\\+358[-\\s]?)?(?:[1-9][0-9]?[-\\s]?[0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Poland - Mobile: +48 5XX XXX XXX, Landline: +48 XX XXX XX XX
        defaultPatterns.put("phone-poland", "\\b(?:\\+48[-\\s]?)?(?:[1-9][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // Czech Republic - Mobile: +420 6XX XXX XXX, Landline: +420 XXX XXX XXX
        defaultPatterns.put("phone-czech", "\\b(?:\\+420[-\\s]?)?(?:[2-9][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // Hungary - Mobile: +36 30 XXX XXXX, Landline: +36 1 XXX XXXX
        defaultPatterns.put("phone-hungary", "\\b(?:\\+36[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Ireland - Mobile: +353 8X XXX XXXX, Landline: +353 X XXX XXXX
        defaultPatterns.put("phone-ireland", "\\b(?:\\+353[-\\s]?)?(?:[1-9][0-9]?[-\\s]?[0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Romania - Mobile: +40 7XX XXX XXX, Landline: +40 XXX XXX XXX
        defaultPatterns.put("phone-romania", "\\b(?:\\+40[-\\s]?)?(?:[2-7][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // Bulgaria - Mobile: +359 8X XXX XXXX, Landline: +359 X XXX XXXX
        defaultPatterns.put("phone-bulgaria", "\\b(?:\\+359[-\\s]?)?(?:[2-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Croatia - Mobile: +385 9X XXX XXXX, Landline: +385 X XXX XXXX
        defaultPatterns.put("phone-croatia", "\\b(?:\\+385[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Slovenia - Mobile: +386 XX XXX XXX, Landline: +386 X XXX XX XX
        defaultPatterns.put("phone-slovenia", "\\b(?:\\+386[-\\s]?)?(?:[1-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{2,4})\\b");
        
        // Slovakia - Mobile: +421 9XX XXX XXX, Landline: +421 XX XXX XXXX
        defaultPatterns.put("phone-slovakia", "\\b(?:\\+421[-\\s]?)?(?:[2-9][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // Greece - Mobile: +30 69X XXX XXXX, Landline: +30 21X XXX XXXX
        defaultPatterns.put("phone-greece", "\\b(?:\\+30[-\\s]?)?(?:[2-9][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Lithuania - Mobile: +370 6XX XX XXX, Landline: +370 X XXX XXXX
        defaultPatterns.put("phone-lithuania", "\\b(?:\\+370[-\\s]?)?(?:[3-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{2,4})\\b");
        
        // Latvia - Mobile: +371 2X XXX XXX, Landline: +371 6XXX XXXX
        defaultPatterns.put("phone-latvia", "\\b(?:\\+371[-\\s]?)?(?:[2-6][0-9]{3}[-\\s]?[0-9]{3,4})\\b");
        
        // Estonia - Mobile: +372 5XXX XXXX, Landline: +372 XXX XXXX
        defaultPatterns.put("phone-estonia", "\\b(?:\\+372[-\\s]?)?(?:[3-9][0-9]{2,3}[-\\s]?[0-9]{3,4})\\b");
        
        // Luxembourg - Mobile: +352 6XX XXX XXX, Landline: +352 XX XX XX
        defaultPatterns.put("phone-luxembourg", "\\b(?:\\+352[-\\s]?)?(?:[2-9][0-9]{2}[-\\s]?[0-9]{3}[-\\s]?[0-9]{3}|[2-9][0-9][-\\s]?[0-9]{2}[-\\s]?[0-9]{2})\\b");
        
        // Malta - Mobile: +356 7XXX XXXX, Landline: +356 21XX XXXX
        defaultPatterns.put("phone-malta", "\\b(?:\\+356[-\\s]?)?(?:[2-9][0-9]{3}[-\\s]?[0-9]{4})\\b");
        
        // Cyprus - Mobile: +357 9X XXX XXX, Landline: +357 2X XXX XXX
        defaultPatterns.put("phone-cyprus", "\\b(?:\\+357[-\\s]?)?(?:[2-9][0-9][-\\s]?[0-9]{3}[-\\s]?[0-9]{3})\\b");
        
        // Iceland - Mobile: +354 XXX XXXX, Landline: +354 XXX XXXX (7 digits)
        defaultPatterns.put("phone-iceland", "\\b(?:\\+354[-\\s]?)?(?:[4-9][0-9]{2}[-\\s]?[0-9]{4})\\b");
        
        // General European phone pattern (covers most formats with country codes)
        defaultPatterns.put("phone-european", "\\b(?:\\+[3-4][0-9][-\\s]?)?(?:[1-9][0-9]{1,4}[-\\s]?[0-9]{2,4}[-\\s]?[0-9]{2,4}[-\\s]?[0-9]{0,4})\\b");
        
        // US Social Security Numbers
        defaultPatterns.put("ssn", "\\b(?!000|666)[0-8][0-9]{2}-?(?!00)[0-9]{2}-?(?!0000)[0-9]{4}\\b");
        
        // Credit Card Numbers (major formats)
        defaultPatterns.put("credit-card", "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3[0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b");
        
        // IP Addresses
        defaultPatterns.put("ip-address", "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");
        
        // MAC Addresses
        defaultPatterns.put("mac-address", "\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b");
        
        // JWT Tokens (supports both 2-part and 3-part tokens)
        defaultPatterns.put("jwt", "\\beyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*(?:\\.[a-zA-Z0-9_-]*)?\\b");
        
        // API Keys (common patterns) - exclude JWT tokens that start with eyJ
        defaultPatterns.put("api-key", "(?i)(?:api[_-]?key|token|secret)[\"'\\s]*[:=][\"'\\s]*(?!eyJ)[a-zA-Z0-9]{20,}");
        
        // URLs with potential sensitive info
        defaultPatterns.put("sensitive-url", "https?://[^\\s]*(?:password|token|key|secret)=[^&\\s]*");
        
        // European National Identity Cards
        
        // Spain - DNI (Documento Nacional de Identidad): 8 digits + letter
        defaultPatterns.put("spanish-dni", "\\b[0-9]{8}[A-Za-z]\\b");
        
        // Spain - NIE (Número de Identificación de Extranjero): Letter (X,Y,Z) + 7 digits + letter
        defaultPatterns.put("spanish-nie", "\\b[XYZxyz][0-9]{7}[A-Za-z]\\b");
        
        // France - CNI (Carte Nationale d'Identité): Various formats, commonly 2 digits + 2 letters + 5 digits
        defaultPatterns.put("french-cni", "\\b[0-9]{2}[A-Za-z]{2}[0-9]{5}\\b");
        
        // Germany - Personalausweis: 10 digits (new format) or letter + 8 digits (old format)
        defaultPatterns.put("german-id", "\\b(?:[0-9]{10}|[A-Za-z][0-9]{8})\\b");
        
        // Italy - Codice Fiscale: 16 characters (letters and numbers)
        defaultPatterns.put("italian-cf", "\\b[A-Za-z]{6}[0-9]{2}[A-Za-z][0-9]{2}[A-Za-z][0-9]{3}[A-Za-z]\\b");
        
        // Portugal - Cartão de Cidadão: 8 digits + space + digit + space + 2 letters + digit
        defaultPatterns.put("portuguese-cc", "\\b[0-9]{8}\\s[0-9]\\s[A-Za-z]{2}[0-9]\\b");
        
        // Netherlands - BSN (Burgerservicenummer): 9 digits
        defaultPatterns.put("dutch-bsn", "\\b[0-9]{9}\\b");
        
        // Belgium - National Register Number: 11 digits (YY.MM.DD-XXX.XX format)
        defaultPatterns.put("belgian-nrn", "\\b[0-9]{2}\\.[0-9]{2}\\.[0-9]{2}-[0-9]{3}\\.[0-9]{2}\\b");
        
        // Austria - Sozialversicherungsnummer: 10 digits (DDMMYY/XXXX format)
        defaultPatterns.put("austrian-svn", "\\b[0-9]{6}/[0-9]{4}\\b");
        
        // Switzerland - AHV/AVS Number: 13 digits (756.XXXX.XXXX.XX format)
        defaultPatterns.put("swiss-ahv", "\\b756\\.[0-9]{4}\\.[0-9]{4}\\.[0-9]{2}\\b");
        
        // Sweden - Personnummer: 10 or 12 digits (YYMMDD-XXXX or YYYYMMDD-XXXX)
        defaultPatterns.put("swedish-pn", "\\b(?:[0-9]{6}|[0-9]{8})-[0-9]{4}\\b");
        
        // Norway - Fødselsnummer: 11 digits (DDMMYY-XXXXX)
        defaultPatterns.put("norwegian-fn", "\\b[0-9]{6}-[0-9]{5}\\b");
        
        // Denmark - CPR Number: 10 digits (DDMMYY-XXXX)
        defaultPatterns.put("danish-cpr", "\\b[0-9]{6}-[0-9]{4}\\b");
        
        // Finland - Henkilötunnus: 10 characters (DDMMYY±XXXX where ± is century marker)
        defaultPatterns.put("finnish-ht", "\\b[0-9]{6}[A+-][0-9]{3}[A-Za-z0-9]\\b");
        
        // United Kingdom - National Insurance Number: 2 letters + 6 digits + letter
        defaultPatterns.put("uk-nino", "\\b[A-Za-z]{2}[0-9]{6}[A-Za-z]\\b");
        
        // Ireland - PPS Number: 7 digits + 1 or 2 letters
        defaultPatterns.put("irish-pps", "\\b[0-9]{7}[A-Za-z]{1,2}\\b");
        
        return defaultPatterns;
    }

    // Getters and setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMaskCharacter() {
        return maskCharacter;
    }

    public void setMaskCharacter(String maskCharacter) {
        this.maskCharacter = maskCharacter;
    }

    public boolean isPreserveLength() {
        return preserveLength;
    }

    public void setPreserveLength(boolean preserveLength) {
        this.preserveLength = preserveLength;
    }

    public int getShowCharacters() {
        return showCharacters;
    }

    public void setShowCharacters(int showCharacters) {
        this.showCharacters = showCharacters;
    }

    public Map<String, String> getPatterns() {
        return patterns;
    }

    public void setPatterns(Map<String, String> patterns) {
        this.patterns = patterns;
    }

    public Map<String, String> getCustomPatterns() {
        return customPatterns;
    }

    public void setCustomPatterns(Map<String, String> customPatterns) {
        this.customPatterns = customPatterns;
    }

    public boolean isMaskHeaders() {
        return maskHeaders;
    }

    public void setMaskHeaders(boolean maskHeaders) {
        this.maskHeaders = maskHeaders;
    }

    public boolean isMaskBodies() {
        return maskBodies;
    }

    public void setMaskBodies(boolean maskBodies) {
        this.maskBodies = maskBodies;
    }

    public boolean isMaskQueryParams() {
        return maskQueryParams;
    }

    public void setMaskQueryParams(boolean maskQueryParams) {
        this.maskQueryParams = maskQueryParams;
    }

    public boolean isMaskExceptions() {
        return maskExceptions;
    }

    public void setMaskExceptions(boolean maskExceptions) {
        this.maskExceptions = maskExceptions;
    }

    public boolean isAutoMaskLogs() {
        return autoMaskLogs;
    }

    public void setAutoMaskLogs(boolean autoMaskLogs) {
        this.autoMaskLogs = autoMaskLogs;
    }

    public boolean isEnableStdoutMasking() {
        return enableStdoutMasking;
    }

    public void setEnableStdoutMasking(boolean enableStdoutMasking) {
        this.enableStdoutMasking = enableStdoutMasking;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Gets all patterns (built-in + custom) as a single map
     */
    public Map<String, String> getAllPatterns() {
        Map<String, String> allPatterns = new HashMap<>(patterns);
        allPatterns.putAll(customPatterns);
        return allPatterns;
    }
}