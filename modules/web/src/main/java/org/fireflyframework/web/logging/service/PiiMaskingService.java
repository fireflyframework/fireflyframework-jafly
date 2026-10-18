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


package org.fireflyframework.web.logging.service;

import org.fireflyframework.web.logging.config.PiiMaskingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for masking PII (Personally Identifiable Information) in log messages.
 * This service provides comprehensive PII detection and masking capabilities using
 * configurable regex patterns and multiple masking strategies.
 * 
 * Features:
 * - Pre-compiled regex patterns for performance
 * - Configurable masking strategies (preserve length, partial reveal, etc.)
 * - Built-in patterns for common PII types
 * - Support for custom organizational patterns
 * - Thread-safe pattern caching
 * - Graceful error handling for invalid patterns
 */
@Service
public class PiiMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingService.class);

    private final PiiMaskingProperties properties;
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();
    private final boolean patternsInitialized;

    public PiiMaskingService(PiiMaskingProperties properties) {
        this.properties = properties;
        this.patternsInitialized = initializePatterns();
    }

    /**
     * Masks PII data in the provided text according to configuration.
     * 
     * @param text the text to mask
     * @return the text with PII data masked, or original text if masking is disabled
     */
    public String maskPiiData(String text) {
        if (!properties.isEnabled() || !patternsInitialized || text == null || text.isEmpty()) {
            return text;
        }

        try {
            String maskedText = text;
            
            // Apply each compiled pattern
            for (Map.Entry<String, Pattern> entry : compiledPatterns.entrySet()) {
                String patternName = entry.getKey();
                Pattern pattern = entry.getValue();
                
                try {
                    maskedText = maskWithPattern(maskedText, pattern, patternName);
                } catch (Exception e) {
                    log.warn("Error applying PII masking pattern '{}': {}", patternName, e.getMessage());
                    // Continue with other patterns
                }
            }
            
            return maskedText;
        } catch (Exception e) {
            log.error("Unexpected error during PII masking: {}", e.getMessage(), e);
            return text; // Return original text on error
        }
    }

    /**
     * Masks headers specifically (may have different rules than body content).
     * 
     * @param headers map of headers to mask
     * @return map with masked header values
     */
    public Map<String, String> maskHeaders(Map<String, String> headers) {
        if (!properties.isEnabled() || !properties.isMaskHeaders() || headers == null) {
            return headers;
        }

        Map<String, String> maskedHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            maskedHeaders.put(key, maskPiiData(value));
        }
        
        return maskedHeaders;
    }

    /**
     * Masks request/response body content.
     * 
     * @param body the body content to mask
     * @return the masked body content
     */
    public String maskBody(String body) {
        if (!properties.isEnabled() || !properties.isMaskBodies()) {
            return body;
        }
        return maskPiiData(body);
    }

    /**
     * Masks query parameters.
     * 
     * @param queryParams map of query parameters to mask
     * @return map with masked parameter values
     */
    public Map<String, String> maskQueryParams(Map<String, String> queryParams) {
        if (!properties.isEnabled() || !properties.isMaskQueryParams() || queryParams == null) {
            return queryParams;
        }

        Map<String, String> maskedParams = new HashMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            maskedParams.put(key, maskPiiData(value));
        }
        
        return maskedParams;
    }

    /**
     * Masks exception messages.
     * 
     * @param message the exception message to mask
     * @return the masked exception message
     */
    public String maskExceptionMessage(String message) {
        if (!properties.isEnabled() || !properties.isMaskExceptions()) {
            return message;
        }
        return maskPiiData(message);
    }

    /**
     * Initializes and compiles all regex patterns from configuration.
     * 
     * @return true if patterns were successfully initialized, false otherwise
     */
    private boolean initializePatterns() {
        try {
            Map<String, String> allPatterns = properties.getAllPatterns();
            int flags = properties.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
            
            for (Map.Entry<String, String> entry : allPatterns.entrySet()) {
                String patternName = entry.getKey();
                String patternString = entry.getValue();
                
                try {
                    Pattern compiledPattern = Pattern.compile(patternString, flags);
                    compiledPatterns.put(patternName, compiledPattern);
                    log.debug("Successfully compiled PII pattern: {}", patternName);
                } catch (PatternSyntaxException e) {
                    log.error("Invalid regex pattern for '{}': {}. Pattern: {}", 
                             patternName, e.getMessage(), patternString);
                    // Continue with other patterns
                }
            }
            
            log.info("PII masking initialized with {} valid patterns", compiledPatterns.size());
            return !compiledPatterns.isEmpty();
            
        } catch (Exception e) {
            log.error("Failed to initialize PII masking patterns: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Applies a specific pattern to mask matching text.
     * 
     * @param text the text to process
     * @param pattern the compiled regex pattern
     * @param patternName the name of the pattern (for logging)
     * @return the text with matches masked
     */
    private String maskWithPattern(String text, Pattern pattern, String patternName) {
        Matcher matcher = pattern.matcher(text);
        
        StringBuffer result = new StringBuffer();
        int matchCount = 0;
        
        while (matcher.find()) {
            matchCount++;
            String matched = matcher.group();
            String masked = createMaskedValue(matched, patternName);
            matcher.appendReplacement(result, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(result);
        
        if (matchCount > 0) {
            log.debug("Masked {} occurrences of pattern '{}' in text", matchCount, patternName);
        }
        
        return result.toString();
    }

    /**
     * Creates a masked version of the matched text according to configuration.
     * 
     * @param original the original matched text
     * @param patternName the pattern name that matched
     * @return the masked version
     */
    private String createMaskedValue(String original, String patternName) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        String maskChar = properties.getMaskCharacter();
        
        if (properties.isPreserveLength()) {
            // Replace all characters with mask character, preserving length
            return maskChar.repeat(original.length());
        } else {
            // Show partial characters at beginning and end
            int showChars = properties.getShowCharacters();
            if (original.length() <= showChars * 2) {
                // If string is too short, mask completely
                return maskChar.repeat(Math.max(3, original.length()));
            } else {
                // Show first and last characters, mask middle
                String start = original.substring(0, showChars);
                String end = original.substring(original.length() - showChars);
                int middleLength = Math.max(3, original.length() - (showChars * 2));
                return start + maskChar.repeat(middleLength) + end;
            }
        }
    }

    /**
     * Gets the current masking statistics for monitoring purposes.
     * 
     * @return map containing masking statistics
     */
    public Map<String, Object> getMaskingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", properties.isEnabled());
        stats.put("patternsLoaded", compiledPatterns.size());
        stats.put("maskCharacter", properties.getMaskCharacter());
        stats.put("preserveLength", properties.isPreserveLength());
        stats.put("caseSensitive", properties.isCaseSensitive());
        stats.put("maskHeaders", properties.isMaskHeaders());
        stats.put("maskBodies", properties.isMaskBodies());
        stats.put("maskQueryParams", properties.isMaskQueryParams());
        stats.put("maskExceptions", properties.isMaskExceptions());
        return stats;
    }

    /**
     * Validates if masking is properly configured and functional.
     * 
     * @return true if masking is ready to use, false otherwise
     */
    public boolean isReady() {
        return properties.isEnabled() && patternsInitialized && !compiledPatterns.isEmpty();
    }
}