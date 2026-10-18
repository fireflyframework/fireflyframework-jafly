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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for password strength analysis and secure password generation.
 * <p>
 * This class provides static methods for evaluating password strength and generating
 * secure random passwords. For password validation via Jakarta Validation annotations,
 * see {@link PasswordStrengthValidator}.
 */
public final class PasswordStrengthUtils {

    private PasswordStrengthUtils() {}

    static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[^a-zA-Z0-9].*");

    static final Set<String> COMMON_PASSWORDS = new HashSet<>(Arrays.asList(
        "password", "123456", "12345678", "qwerty", "abc123", "monkey", "1234567", "letmein",
        "trustno1", "dragon", "baseball", "111111", "iloveyou", "master", "sunshine", "ashley",
        "bailey", "passw0rd", "shadow", "123123", "654321", "superman", "qazwsx", "michael",
        "football", "welcome", "jesus", "ninja", "mustang", "password1", "123456789", "adobe123",
        "admin", "1234567890", "photoshop", "1234", "12345", "princess", "azerty", "000000",
        "access", "696969", "batman", "1qaz2wsx", "login", "qwertyuiop", "solo", "starwars",
        "121212", "flower", "hottie", "loveme", "zaq1zaq1", "hello", "freedom", "whatever",
        "666666", "!@#$%^&*", "charlie", "aa123456", "donald", "password123", "qwerty123",
        "secret", "admin123", "temp123", "test123", "123qwe", "zxcvbnm", "asdfghjkl", "pokemon",
        "jordan", "computer", "michelle", "jessica", "pepper", "1111", "zxcvbn", "asdfgh",
        "hunter", "silver", "minecraft", "purple", "jordan23", "ferrari", "cookie", "justin",
        "test", "hockey", "dallas", "chelsea", "summer", "internet", "soccer", "liverpool"
    ));

    /**
     * Checks if a password is a common/weak password.
     *
     * @param password the password to check
     * @return true if the password is common/weak, false otherwise
     */
    public static boolean isCommonPassword(String password) {
        if (password == null || password.isEmpty()) {
            return true;
        }
        return COMMON_PASSWORDS.contains(password.toLowerCase());
    }

    /**
     * Calculates the strength score of a password (0-100).
     *
     * @param password the password to evaluate
     * @return the strength score (0-100) or -1 if invalid
     */
    public static int calculateStrengthScore(String password) {
        if (password == null || password.isEmpty()) {
            return -1;
        }

        int score = 0;

        // Length score (up to 30 points)
        int lengthScore = Math.min(password.length() * 3, 30);
        score += lengthScore;

        // Character type score (up to 40 points)
        boolean hasUppercase = UPPERCASE_PATTERN.matcher(password).find();
        boolean hasLowercase = LOWERCASE_PATTERN.matcher(password).find();
        boolean hasDigit = DIGIT_PATTERN.matcher(password).find();
        boolean hasSpecialChar = SPECIAL_CHAR_PATTERN.matcher(password).find();

        if (hasUppercase) score += 10;
        if (hasLowercase) score += 10;
        if (hasDigit) score += 10;
        if (hasSpecialChar) score += 10;

        // Bonus points for mixed character types (up to 10 points)
        int charTypesCount = 0;
        if (hasUppercase) charTypesCount++;
        if (hasLowercase) charTypesCount++;
        if (hasDigit) charTypesCount++;
        if (hasSpecialChar) charTypesCount++;

        score += (charTypesCount - 1) * 3;

        // Bonus points for longer passwords (up to 10 points)
        if (password.length() > 12) {
            score += Math.min((password.length() - 12) * 2, 10);
        }

        // Deduct points for common passwords (up to 20 points)
        if (isCommonPassword(password)) {
            score -= 20;
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Gets the strength category of a password.
     *
     * @param password the password to evaluate
     * @return the strength category or null if invalid
     */
    public static PasswordStrength getStrengthCategory(String password) {
        int score = calculateStrengthScore(password);

        if (score < 0) {
            return null;
        } else if (score < 20) {
            return PasswordStrength.VERY_WEAK;
        } else if (score < 40) {
            return PasswordStrength.WEAK;
        } else if (score < 60) {
            return PasswordStrength.MEDIUM;
        } else if (score < 80) {
            return PasswordStrength.STRONG;
        } else {
            return PasswordStrength.VERY_STRONG;
        }
    }

    /**
     * Generates a secure random password with the specified requirements.
     *
     * @param length the desired password length
     * @param minLength the minimum allowed length
     * @param maxLength the maximum allowed length
     * @param requireUppercase whether to include uppercase characters
     * @param requireLowercase whether to include lowercase characters
     * @param requireDigit whether to include digits
     * @param requireSpecialChar whether to include special characters
     * @return a secure random password, or null if the length is outside bounds
     */
    public static String generateSecurePassword(int length, int minLength, int maxLength,
                                                 boolean requireUppercase, boolean requireLowercase,
                                                 boolean requireDigit, boolean requireSpecialChar) {
        if (length < minLength || length > maxLength) {
            return null;
        }

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        String uppercaseChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercaseChars = "abcdefghijklmnopqrstuvwxyz";
        String digitChars = "0123456789";
        String specialChars = "!@#$%^&*()-_=+[]{}|;:,.<>?";

        // Ensure at least one character from each required type
        if (requireUppercase) {
            password.append(uppercaseChars.charAt(random.nextInt(uppercaseChars.length())));
        }
        if (requireLowercase) {
            password.append(lowercaseChars.charAt(random.nextInt(lowercaseChars.length())));
        }
        if (requireDigit) {
            password.append(digitChars.charAt(random.nextInt(digitChars.length())));
        }
        if (requireSpecialChar) {
            password.append(specialChars.charAt(random.nextInt(specialChars.length())));
        }

        // Build the full character set based on requirements
        StringBuilder charSet = new StringBuilder();
        if (requireUppercase) charSet.append(uppercaseChars);
        if (requireLowercase) charSet.append(lowercaseChars);
        if (requireDigit) charSet.append(digitChars);
        if (requireSpecialChar) charSet.append(specialChars);

        // Fill the rest of the password with random characters
        for (int i = password.length(); i < length; i++) {
            password.append(charSet.charAt(random.nextInt(charSet.length())));
        }

        // Shuffle to avoid predictable patterns
        char[] passwordArray = password.toString().toCharArray();
        for (int i = 0; i < passwordArray.length; i++) {
            int j = random.nextInt(passwordArray.length);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    /**
     * Password strength categories.
     */
    public enum PasswordStrength {
        VERY_WEAK,
        WEAK,
        MEDIUM,
        STRONG,
        VERY_STRONG
    }
}
