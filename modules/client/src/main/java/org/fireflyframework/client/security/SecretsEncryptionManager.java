package org.fireflyframework.client.security;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secrets encryption manager for secure storage of sensitive data.
 * 
 * <p>This manager provides AES-256-GCM encryption for sensitive data like
 * API keys, passwords, and tokens. It supports key rotation and secure
 * key storage.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Create encryption manager
 * SecretsEncryptionManager encryption = SecretsEncryptionManager.builder()
 *     .masterKey("your-32-byte-master-key-here!!")
 *     .build();
 * 
 * // Encrypt a secret
 * String encrypted = encryption.encrypt("my-api-key-12345");
 * 
 * // Decrypt a secret
 * String decrypted = encryption.decrypt(encrypted);
 * 
 * // Store encrypted secret
 * encryption.storeSecret("payment-api-key", "sk_live_12345");
 * String apiKey = encryption.getSecret("payment-api-key");
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
@Builder
public class SecretsEncryptionManager {

    /**
     * Master encryption key (32 bytes for AES-256).
     */
    private final String masterKey;

    /**
     * Algorithm to use for encryption.
     */
    @Builder.Default
    private final String algorithm = "AES/GCM/NoPadding";

    /**
     * Key size in bits.
     */
    @Builder.Default
    private final int keySize = 256;

    /**
     * GCM tag length in bits.
     */
    @Builder.Default
    private final int gcmTagLength = 128;

    /**
     * IV (Initialization Vector) length in bytes.
     */
    @Builder.Default
    private final int ivLength = 12;

    /**
     * In-memory encrypted secrets storage.
     */
    @Builder.Default
    private final Map<String, String> encryptedSecrets = new ConcurrentHashMap<>();

    /**
     * Secure random generator.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypts a plaintext string.
     *
     * @param plaintext the plaintext to encrypt
     * @return the encrypted string (Base64-encoded)
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Plaintext cannot be null or empty");
        }

        try {
            // Generate random IV
            byte[] iv = new byte[ivLength];
            secureRandom.nextBytes(iv);

            // Create cipher
            Cipher cipher = Cipher.getInstance(algorithm);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(gcmTagLength, iv);
            SecretKey secretKey = getSecretKey();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode to Base64
            String encrypted = Base64.getEncoder().encodeToString(byteBuffer.array());
            log.debug("Successfully encrypted data (length: {})", plaintext.length());
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts an encrypted string.
     *
     * @param encrypted the encrypted string (Base64-encoded)
     * @return the decrypted plaintext
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            throw new IllegalArgumentException("Encrypted data cannot be null or empty");
        }

        try {
            // Decode from Base64
            byte[] decodedData = Base64.getDecoder().decode(encrypted);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedData);
            byte[] iv = new byte[ivLength];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Create cipher
            Cipher cipher = Cipher.getInstance(algorithm);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(gcmTagLength, iv);
            SecretKey secretKey = getSecretKey();
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            String decrypted = new String(plaintext, StandardCharsets.UTF_8);
            log.debug("Successfully decrypted data");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Stores a secret in encrypted form.
     *
     * @param key the secret key/name
     * @param value the secret value
     */
    public void storeSecret(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Secret key cannot be null or empty");
        }
        String encrypted = encrypt(value);
        encryptedSecrets.put(key, encrypted);
        log.debug("Stored encrypted secret: {}", key);
    }

    /**
     * Retrieves and decrypts a secret.
     *
     * @param key the secret key/name
     * @return the decrypted secret value, or null if not found
     */
    public String getSecret(String key) {
        String encrypted = encryptedSecrets.get(key);
        if (encrypted == null) {
            log.warn("Secret not found: {}", key);
            return null;
        }
        return decrypt(encrypted);
    }

    /**
     * Removes a secret from storage.
     *
     * @param key the secret key/name
     * @return true if the secret was removed, false if it didn't exist
     */
    public boolean removeSecret(String key) {
        boolean removed = encryptedSecrets.remove(key) != null;
        if (removed) {
            log.debug("Removed secret: {}", key);
        }
        return removed;
    }

    /**
     * Checks if a secret exists.
     *
     * @param key the secret key/name
     * @return true if the secret exists, false otherwise
     */
    public boolean hasSecret(String key) {
        return encryptedSecrets.containsKey(key);
    }

    /**
     * Clears all stored secrets.
     */
    public void clearAllSecrets() {
        int count = encryptedSecrets.size();
        encryptedSecrets.clear();
        log.info("Cleared {} encrypted secrets", count);
    }

    /**
     * Gets the number of stored secrets.
     *
     * @return the number of secrets
     */
    public int getSecretCount() {
        return encryptedSecrets.size();
    }

    /**
     * Rotates encryption for all stored secrets with a new master key.
     *
     * @param newMasterKey the new master key
     * @return a new SecretsEncryptionManager with re-encrypted secrets
     */
    public SecretsEncryptionManager rotateKey(String newMasterKey) {
        log.info("Rotating encryption key for {} secrets", encryptedSecrets.size());
        
        SecretsEncryptionManager newManager = SecretsEncryptionManager.builder()
            .masterKey(newMasterKey)
            .algorithm(algorithm)
            .keySize(keySize)
            .gcmTagLength(gcmTagLength)
            .ivLength(ivLength)
            .build();

        // Decrypt with old key and re-encrypt with new key
        for (Map.Entry<String, String> entry : encryptedSecrets.entrySet()) {
            String decrypted = decrypt(entry.getValue());
            newManager.storeSecret(entry.getKey(), decrypted);
        }

        log.info("Key rotation completed successfully");
        return newManager;
    }

    /**
     * Gets the secret key from the master key.
     *
     * @return the secret key
     */
    private SecretKey getSecretKey() {
        if (masterKey == null || masterKey.isEmpty()) {
            throw new IllegalStateException("Master key is not configured");
        }

        // Ensure master key is exactly 32 bytes for AES-256
        byte[] keyBytes = masterKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("Master key must be exactly 32 bytes for AES-256");
        }

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Generates a random master key.
     *
     * @return a random 32-byte master key (Base64-encoded)
     */
    public static String generateMasterKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate master key", e);
        }
    }

    /**
     * Creates a simple encryption manager with a generated key.
     *
     * @return configured SecretsEncryptionManager
     */
    public static SecretsEncryptionManager create() {
        String masterKey = generateMasterKey();
        // Decode to get exactly 32 bytes
        byte[] keyBytes = Base64.getDecoder().decode(masterKey);
        String key32Bytes = new String(keyBytes, StandardCharsets.ISO_8859_1);
        
        return SecretsEncryptionManager.builder()
            .masterKey(key32Bytes)
            .build();
    }
}

