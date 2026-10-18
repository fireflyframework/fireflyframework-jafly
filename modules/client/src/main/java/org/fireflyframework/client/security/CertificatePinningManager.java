package org.fireflyframework.client.security;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Certificate pinning manager for enhanced HTTPS security.
 * 
 * <p>Certificate pinning prevents man-in-the-middle attacks by validating that
 * the server's certificate matches a known set of certificates or public keys.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Pin by SHA-256 hash of public key
 * CertificatePinningManager pinning = CertificatePinningManager.builder()
 *     .addPin("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
 *     .addPin("api.example.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup pin
 *     .build();
 * 
 * // Use with WebClient
 * SslContext sslContext = pinning.createSslContext();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
@Builder
public class CertificatePinningManager {

    /**
     * Map of hostname to list of pinned certificate hashes.
     */
    private final Map<String, Set<String>> pinnedCertificates;

    /**
     * Hash algorithm to use (default: SHA-256).
     */
    @Builder.Default
    private final String hashAlgorithm = "SHA-256";

    /**
     * Whether to fail on pin mismatch (true) or just log warning (false).
     */
    @Builder.Default
    private final boolean strictMode = true;

    /**
     * Whether to enable certificate pinning.
     */
    @Builder.Default
    private final boolean enabled = true;

    /**
     * Custom trust manager for certificate validation.
     */
    private X509TrustManager trustManager;

    /**
     * Builder class for CertificatePinningManager.
     */
    public static class CertificatePinningManagerBuilder {
        private Map<String, Set<String>> pinnedCertificates = new ConcurrentHashMap<>();

        /**
         * Adds a certificate pin for a hostname.
         *
         * @param hostname the hostname to pin
         * @param pin the certificate hash (format: "sha256/BASE64_HASH")
         * @return this builder
         */
        public CertificatePinningManagerBuilder addPin(String hostname, String pin) {
            pinnedCertificates.computeIfAbsent(hostname, k -> ConcurrentHashMap.newKeySet()).add(pin);
            return this;
        }

        /**
         * Adds multiple certificate pins for a hostname.
         *
         * @param hostname the hostname to pin
         * @param pins the certificate hashes
         * @return this builder
         */
        public CertificatePinningManagerBuilder addPins(String hostname, String... pins) {
            pinnedCertificates.computeIfAbsent(hostname, k -> ConcurrentHashMap.newKeySet())
                .addAll(Arrays.asList(pins));
            return this;
        }
    }

    /**
     * Creates an SSLContext with certificate pinning enabled.
     *
     * @return configured SSLContext
     * @throws RuntimeException if SSL context creation fails
     */
    public SSLContext createSslContext() {
        if (!enabled) {
            log.debug("Certificate pinning is disabled");
            return getDefaultSslContext();
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{createPinningTrustManager()}, null);
            log.info("Created SSL context with certificate pinning for {} hosts", pinnedCertificates.size());
            return sslContext;
        } catch (Exception e) {
            log.error("Failed to create SSL context with certificate pinning", e);
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    /**
     * Creates a trust manager that validates certificate pins.
     *
     * @return X509TrustManager with pinning validation
     */
    private X509TrustManager createPinningTrustManager() {
        return new X509TrustManager() {
            private final X509TrustManager defaultTrustManager = getDefaultTrustManager();

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                defaultTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // First, perform standard certificate validation
                defaultTrustManager.checkServerTrusted(chain, authType);

                // Then, validate certificate pinning
                if (chain == null || chain.length == 0) {
                    throw new CertificateException("Certificate chain is empty");
                }

                // Extract hostname from certificate
                X509Certificate cert = chain[0];
                String hostname = extractHostname(cert);

                if (hostname != null && pinnedCertificates.containsKey(hostname)) {
                    validatePins(hostname, chain);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return defaultTrustManager.getAcceptedIssuers();
            }
        };
    }

    /**
     * Validates that at least one certificate in the chain matches a pinned hash.
     *
     * @param hostname the hostname being validated
     * @param chain the certificate chain
     * @throws CertificateException if no pins match and strict mode is enabled
     */
    private void validatePins(String hostname, X509Certificate[] chain) throws CertificateException {
        Set<String> pins = pinnedCertificates.get(hostname);
        if (pins == null || pins.isEmpty()) {
            return;
        }

        for (X509Certificate cert : chain) {
            String certHash = calculateCertificateHash(cert);
            if (pins.contains(certHash)) {
                log.debug("Certificate pin validated for hostname: {}", hostname);
                return;
            }
        }

        String errorMsg = String.format("Certificate pin validation failed for hostname: %s", hostname);
        log.error(errorMsg);

        if (strictMode) {
            throw new CertificateException(errorMsg);
        } else {
            log.warn("Certificate pin mismatch (non-strict mode): {}", hostname);
        }
    }

    /**
     * Calculates the hash of a certificate's public key.
     *
     * @param cert the certificate
     * @return the hash in format "sha256/BASE64_HASH"
     */
    private String calculateCertificateHash(X509Certificate cert) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] publicKey = cert.getPublicKey().getEncoded();
            byte[] hash = digest.digest(publicKey);
            String base64Hash = Base64.getEncoder().encodeToString(hash);
            return hashAlgorithm.toLowerCase().replace("-", "") + "/" + base64Hash;
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm not available: {}", hashAlgorithm, e);
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }

    /**
     * Extracts hostname from certificate's Subject Alternative Names or Common Name.
     *
     * @param cert the certificate
     * @return the hostname, or null if not found
     */
    private String extractHostname(X509Certificate cert) {
        try {
            // Try Subject Alternative Names first
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> san : sans) {
                    if (san.size() >= 2 && san.get(0).equals(2)) { // DNS name
                        return (String) san.get(1);
                    }
                }
            }

            // Fall back to Common Name
            String dn = cert.getSubjectX500Principal().getName();
            String[] parts = dn.split(",");
            for (String part : parts) {
                if (part.trim().startsWith("CN=")) {
                    return part.trim().substring(3);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract hostname from certificate", e);
        }
        return null;
    }

    /**
     * Gets the default trust manager from the JVM.
     *
     * @return default X509TrustManager
     */
    private X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((java.security.KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
            throw new RuntimeException("No X509TrustManager found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default trust manager", e);
        }
    }

    /**
     * Gets the default SSL context.
     *
     * @return default SSLContext
     */
    private SSLContext getDefaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default SSL context", e);
        }
    }

    /**
     * Validates a certificate against pinned hashes.
     *
     * @param hostname the hostname
     * @param certificate the certificate to validate
     * @return true if the certificate matches a pin, false otherwise
     */
    public boolean validateCertificate(String hostname, X509Certificate certificate) {
        if (!enabled || !pinnedCertificates.containsKey(hostname)) {
            return true;
        }

        Set<String> pins = pinnedCertificates.get(hostname);
        String certHash = calculateCertificateHash(certificate);
        return pins.contains(certHash);
    }
}

