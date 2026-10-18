package org.fireflyframework.client.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.kernel.exception.FireflySecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * JWT (JSON Web Token) validator for authentication and authorization.
 * 
 * <p>This validator supports JWT validation including signature verification,
 * expiration checking, issuer validation, and audience validation.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Create validator
 * JwtValidator validator = JwtValidator.builder()
 *     .secret("your-secret-key")
 *     .issuer("https://auth.example.com")
 *     .audience("api.example.com")
 *     .clockSkew(Duration.ofMinutes(1))
 *     .build();
 * 
 * // Validate JWT
 * JwtClaims claims = validator.validate(jwtToken);
 * String userId = claims.getSubject();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
@Builder
public class JwtValidator {

    /**
     * Secret key for HMAC signature verification.
     */
    private final String secret;

    /**
     * Expected issuer (iss claim).
     */
    private final String issuer;

    /**
     * Expected audience (aud claim).
     */
    private final String audience;

    /**
     * Clock skew tolerance for time-based validations.
     */
    @Builder.Default
    private final long clockSkewSeconds = 60;

    /**
     * Whether to validate expiration.
     */
    @Builder.Default
    private final boolean validateExpiration = true;

    /**
     * Whether to validate not-before time.
     */
    @Builder.Default
    private final boolean validateNotBefore = true;

    /**
     * Whether to validate issuer.
     */
    @Builder.Default
    private final boolean validateIssuer = true;

    /**
     * Whether to validate audience.
     */
    @Builder.Default
    private final boolean validateAudience = true;

    /**
     * Whether to validate signature.
     */
    @Builder.Default
    private final boolean validateSignature = true;

    /**
     * Required claims that must be present.
     */
    @Builder.Default
    private final Set<String> requiredClaims = new HashSet<>();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Validates a JWT token and returns the claims.
     *
     * @param token the JWT token
     * @return the validated claims
     * @throws JwtValidationException if validation fails
     */
    public JwtClaims validate(String token) throws JwtValidationException {
        if (token == null || token.trim().isEmpty()) {
            throw new JwtValidationException("JWT token is null or empty");
        }

        // Remove "Bearer " prefix if present
        token = token.replace("Bearer ", "").trim();

        // Split token into parts
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException("Invalid JWT format: expected 3 parts, got " + parts.length);
        }

        String headerBase64 = parts[0];
        String payloadBase64 = parts[1];
        String signatureBase64 = parts[2];

        // Decode and parse payload
        JwtClaims claims = parsePayload(payloadBase64);

        // Validate signature
        if (validateSignature) {
            validateSignature(headerBase64, payloadBase64, signatureBase64);
        }

        // Validate claims
        validateClaims(claims);

        log.debug("JWT validated successfully for subject: {}", claims.getSubject());
        return claims;
    }

    /**
     * Parses the JWT payload.
     *
     * @param payloadBase64 the base64-encoded payload
     * @return the parsed claims
     * @throws JwtValidationException if parsing fails
     */
    private JwtClaims parsePayload(String payloadBase64) throws JwtValidationException {
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claimsMap = objectMapper.readValue(payloadJson, Map.class);
            return new JwtClaims(claimsMap);
        } catch (Exception e) {
            throw new JwtValidationException("Failed to parse JWT payload", e);
        }
    }

    /**
     * Validates the JWT signature.
     *
     * @param headerBase64 the base64-encoded header
     * @param payloadBase64 the base64-encoded payload
     * @param signatureBase64 the base64-encoded signature
     * @throws JwtValidationException if signature is invalid
     */
    private void validateSignature(String headerBase64, String payloadBase64, String signatureBase64) 
            throws JwtValidationException {
        try {
            String data = headerBase64 + "." + payloadBase64;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] expectedSignature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expectedSignatureBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(expectedSignature);

            if (!expectedSignatureBase64.equals(signatureBase64)) {
                throw new JwtValidationException("Invalid JWT signature");
            }
        } catch (JwtValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtValidationException("Failed to validate JWT signature", e);
        }
    }

    /**
     * Validates JWT claims.
     *
     * @param claims the claims to validate
     * @throws JwtValidationException if validation fails
     */
    private void validateClaims(JwtClaims claims) throws JwtValidationException {
        long now = Instant.now().getEpochSecond();

        // Validate expiration
        if (validateExpiration && claims.getExpiration() != null) {
            if (now > claims.getExpiration() + clockSkewSeconds) {
                throw new JwtValidationException("JWT has expired");
            }
        }

        // Validate not-before
        if (validateNotBefore && claims.getNotBefore() != null) {
            if (now < claims.getNotBefore() - clockSkewSeconds) {
                throw new JwtValidationException("JWT not yet valid");
            }
        }

        // Validate issuer
        if (validateIssuer && issuer != null) {
            if (!issuer.equals(claims.getIssuer())) {
                throw new JwtValidationException("Invalid issuer: expected " + issuer + ", got " + claims.getIssuer());
            }
        }

        // Validate audience
        if (validateAudience && audience != null) {
            if (!audience.equals(claims.getAudience())) {
                throw new JwtValidationException("Invalid audience: expected " + audience + ", got " + claims.getAudience());
            }
        }

        // Validate required claims
        for (String requiredClaim : requiredClaims) {
            if (!claims.hasClaim(requiredClaim)) {
                throw new JwtValidationException("Missing required claim: " + requiredClaim);
            }
        }
    }

    /**
     * JWT claims container.
     */
    @Getter
    public static class JwtClaims {
        private final Map<String, Object> claims;

        public JwtClaims(Map<String, Object> claims) {
            this.claims = claims;
        }

        public String getSubject() {
            return (String) claims.get("sub");
        }

        public String getIssuer() {
            return (String) claims.get("iss");
        }

        public String getAudience() {
            return (String) claims.get("aud");
        }

        public Long getExpiration() {
            Object exp = claims.get("exp");
            return exp instanceof Number ? ((Number) exp).longValue() : null;
        }

        public Long getNotBefore() {
            Object nbf = claims.get("nbf");
            return nbf instanceof Number ? ((Number) nbf).longValue() : null;
        }

        public Long getIssuedAt() {
            Object iat = claims.get("iat");
            return iat instanceof Number ? ((Number) iat).longValue() : null;
        }

        public String getJwtId() {
            return (String) claims.get("jti");
        }

        public Object getClaim(String name) {
            return claims.get(name);
        }

        public boolean hasClaim(String name) {
            return claims.containsKey(name);
        }

        @SuppressWarnings("unchecked")
        public <T> T getClaim(String name, Class<T> type) {
            Object value = claims.get(name);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return (T) value;
            }
            throw new ClassCastException("Claim " + name + " is not of type " + type.getName());
        }
    }

    /**
     * JWT validation exception.
     */
    public static class JwtValidationException extends FireflySecurityException {
        public JwtValidationException(String message) {
            super(message);
        }

        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

