# Security Features

This document describes the comprehensive security features available in the Firefly Common Client Library.

## Table of Contents

- [Certificate Pinning](#certificate-pinning)
- [mTLS Support](#mtls-support)
- [API Key Management](#api-key-management)
- [JWT Validation](#jwt-validation)
- [Secrets Encryption](#secrets-encryption)
- [Client-Side Rate Limiting](#client-side-rate-limiting)
- [Best Practices](#best-practices)

---

## Certificate Pinning

Certificate pinning prevents man-in-the-middle attacks by validating that the server's certificate matches a known set of certificates or public keys.

### Basic Usage

```java
// Create certificate pinning manager
CertificatePinningManager pinning = CertificatePinningManager.builder()
    .addPin("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .addPin("api.example.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup pin
    .strictMode(true)
    .build();

// Create SSL context with pinning
SSLContext sslContext = pinning.createSslContext();

// Use with WebClient (requires custom configuration)
```

### Getting Certificate Hashes

```bash
# Get SHA-256 hash of certificate's public key
openssl s_client -connect api.example.com:443 < /dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  base64
```

### Configuration Options

```java
CertificatePinningManager pinning = CertificatePinningManager.builder()
    .addPin("api.example.com", "sha256/...")
    .hashAlgorithm("SHA-256")           // Hash algorithm (default: SHA-256)
    .strictMode(true)                   // Fail on mismatch (default: true)
    .enabled(true)                      // Enable pinning (default: true)
    .build();
```

### Best Practices

- **Always have backup pins**: Include at least 2 pins (current + backup)
- **Pin to intermediate CA**: More flexible than pinning to leaf certificate
- **Monitor expiration**: Set up alerts for certificate expiration
- **Test in staging first**: Validate pins in non-production environments

---

## mTLS Support

Mutual TLS (mTLS) provides two-way authentication between client and server.

### SOAP Client with mTLS

```java
SoapClient client = ServiceClient.soap("secure-service")
    .wsdlUrl("https://secure.example.com/service?WSDL")
    .trustStore("/path/to/truststore.jks", "truststore-password")
    .keyStore("/path/to/keystore.jks", "keystore-password")
    .build();
```

### REST Client with mTLS

```yaml
# application.yml
firefly:
  service-client:
    security:
      tls-enabled: true
      trust-store-path: /path/to/truststore.jks
      trust-store-password: ${TRUSTSTORE_PASSWORD}
      key-store-path: /path/to/keystore.jks
      key-store-password: ${KEYSTORE_PASSWORD}
```

### Creating Keystores

```bash
# Create keystore with client certificate
keytool -genkeypair -alias client -keyalg RSA -keysize 2048 \
  -keystore client-keystore.jks -storepass changeit

# Import CA certificate into truststore
keytool -import -alias ca -file ca-cert.pem \
  -keystore client-truststore.jks -storepass changeit
```

---

## API Key Management

Secure API key management with rotation, expiration, and multiple strategies.

### Static API Key

```java
ApiKeyManager keyManager = ApiKeyManager.simple("payment-service", "api-key-12345");

// Use in REST client
RestClient client = ServiceClient.rest("payment-service")
    .baseUrl("https://api.example.com")
    .defaultHeader(keyManager.getHeaderName(), keyManager.getHeaderValue())
    .build();
```

### Dynamic API Key with Rotation

```java
// Fetch API key from vault/secrets manager
ApiKeyManager keyManager = ApiKeyManager.builder()
    .serviceName("user-service")
    .apiKeySupplier(() -> vaultClient.getSecret("user-service-api-key"))
    .rotationInterval(Duration.ofHours(1))
    .autoRotate(true)
    .headerName("X-API-Key")
    .build();

// Key is automatically rotated every hour
String currentKey = keyManager.getCurrentApiKey();
```

### Bearer Token Authentication

```java
ApiKeyManager keyManager = ApiKeyManager.bearer("auth-service", "eyJhbGciOiJIUzI1NiIs...");

// Header will be: Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
String headerValue = keyManager.getHeaderValue();
```

### Configuration Options

```java
ApiKeyManager keyManager = ApiKeyManager.builder()
    .serviceName("service-name")
    .apiKey("static-key")                           // Static key
    .apiKeySupplier(() -> fetchKey())               // Dynamic key
    .headerName("X-API-Key")                        // Header name
    .headerPrefix("ApiKey ")                        // Header prefix
    .rotationInterval(Duration.ofHours(24))         // Rotation interval
    .autoRotate(true)                               // Auto-rotate
    .cacheEnabled(true)                             // Cache key
    .cacheExpiration(Duration.ofMinutes(5))         // Cache duration
    .build();
```

---

## JWT Validation

Validate JSON Web Tokens for authentication and authorization.

### Basic Validation

```java
JwtValidator validator = JwtValidator.builder()
    .secret("your-secret-key")
    .issuer("https://auth.example.com")
    .audience("api.example.com")
    .build();

try {
    JwtClaims claims = validator.validate(jwtToken);
    String userId = claims.getSubject();
    String email = claims.getClaim("email", String.class);
} catch (JwtValidationException e) {
    // Invalid JWT
    log.error("JWT validation failed", e);
}
```

### Advanced Configuration

```java
JwtValidator validator = JwtValidator.builder()
    .secret("your-secret-key")
    .issuer("https://auth.example.com")
    .audience("api.example.com")
    .clockSkewSeconds(60)                   // Clock skew tolerance
    .validateExpiration(true)               // Validate exp claim
    .validateNotBefore(true)                // Validate nbf claim
    .validateIssuer(true)                   // Validate iss claim
    .validateAudience(true)                 // Validate aud claim
    .validateSignature(true)                // Validate signature
    .requiredClaims(Set.of("sub", "email")) // Required claims
    .build();
```

### Accessing Claims

```java
JwtClaims claims = validator.validate(token);

// Standard claims
String subject = claims.getSubject();
String issuer = claims.getIssuer();
String audience = claims.getAudience();
Long expiration = claims.getExpiration();
Long notBefore = claims.getNotBefore();
Long issuedAt = claims.getIssuedAt();
String jwtId = claims.getJwtId();

// Custom claims
String email = claims.getClaim("email", String.class);
List<String> roles = claims.getClaim("roles", List.class);
```

---

## Secrets Encryption

Encrypt sensitive data like API keys, passwords, and tokens using AES-256-GCM.

### Basic Usage

```java
// Create encryption manager
SecretsEncryptionManager encryption = SecretsEncryptionManager.builder()
    .masterKey("your-32-byte-master-key-here!!")
    .build();

// Encrypt a secret
String encrypted = encryption.encrypt("my-api-key-12345");

// Decrypt a secret
String decrypted = encryption.decrypt(encrypted);
```

### Storing Secrets

```java
// Store encrypted secrets
encryption.storeSecret("payment-api-key", "sk_live_12345");
encryption.storeSecret("database-password", "super-secret-password");

// Retrieve secrets
String apiKey = encryption.getSecret("payment-api-key");
String dbPassword = encryption.getSecret("database-password");

// Check if secret exists
boolean exists = encryption.hasSecret("payment-api-key");

// Remove secret
encryption.removeSecret("old-api-key");
```

### Key Rotation

```java
// Generate new master key
String newMasterKey = SecretsEncryptionManager.generateMasterKey();

// Rotate all secrets to new key
SecretsEncryptionManager newManager = encryption.rotateKey(newMasterKey);

// Old manager is still valid, but new manager has re-encrypted secrets
```

### Configuration Options

```java
SecretsEncryptionManager encryption = SecretsEncryptionManager.builder()
    .masterKey("your-32-byte-master-key-here!!")
    .algorithm("AES/GCM/NoPadding")         // Encryption algorithm
    .keySize(256)                           // Key size in bits
    .gcmTagLength(128)                      // GCM tag length
    .ivLength(12)                           // IV length in bytes
    .build();
```

---

## Client-Side Rate Limiting

Prevent overwhelming downstream services with client-side rate limiting.

### Basic Usage

```java
// Create rate limiter (100 requests per minute)
ClientSideRateLimiter rateLimiter = ClientSideRateLimiter.builder()
    .serviceName("payment-service")
    .maxRequestsPerSecond(100.0 / 60.0)  // ~1.67 RPS
    .maxConcurrentRequests(10)
    .build();

// Acquire permit before making request
if (rateLimiter.tryAcquire()) {
    try {
        // Make API call
        Response response = client.get("/api/endpoint").block();
    } finally {
        rateLimiter.release();
    }
} else {
    // Rate limit exceeded
    throw new RateLimitExceededException("Rate limit exceeded");
}
```

### Rate Limiting Strategies

```java
// Token Bucket (smooth rate limiting)
ClientSideRateLimiter tokenBucket = ClientSideRateLimiter.builder()
    .serviceName("service")
    .strategy(RateLimitStrategy.TOKEN_BUCKET)
    .maxRequestsPerSecond(10.0)
    .build();

// Fixed Window (simple, but can have bursts)
ClientSideRateLimiter fixedWindow = ClientSideRateLimiter.builder()
    .serviceName("service")
    .strategy(RateLimitStrategy.FIXED_WINDOW)
    .maxRequestsPerSecond(10.0)
    .build();

// Sliding Window (accurate, but more memory)
ClientSideRateLimiter slidingWindow = ClientSideRateLimiter.builder()
    .serviceName("service")
    .strategy(RateLimitStrategy.SLIDING_WINDOW)
    .maxRequestsPerSecond(10.0)
    .build();
```

### With Timeout

```java
// Try to acquire with timeout
if (rateLimiter.tryAcquire(Duration.ofSeconds(5))) {
    try {
        makeApiCall();
    } finally {
        rateLimiter.release();
    }
} else {
    // Timeout or rate limit exceeded
}
```

### Monitoring

```java
// Get statistics
RateLimiterStats stats = rateLimiter.getStats();
log.info("Total requests: {}", stats.totalRequests());
log.info("Rejected requests: {}", stats.rejectedRequests());
log.info("Available slots: {}", stats.availableConcurrentSlots());
log.info("Utilization: {}%", stats.utilization() * 100);

// Reset rate limiter
rateLimiter.reset();
```

---

## Best Practices

### 1. Use Environment Variables for Secrets

```yaml
# application.yml
firefly:
  service-client:
    security:
      trust-store-password: ${TRUSTSTORE_PASSWORD}
      key-store-password: ${KEYSTORE_PASSWORD}
```

### 2. Rotate Keys Regularly

```java
// Schedule key rotation
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void rotateApiKeys() {
    apiKeyManager.rotateApiKey();
}
```

### 3. Use Secrets Management Services

```java
// Integrate with AWS Secrets Manager, HashiCorp Vault, etc.
ApiKeyManager keyManager = ApiKeyManager.dynamic(
    "service",
    () -> awsSecretsManager.getSecretValue("api-key").getSecretString()
);
```

### 4. Enable mTLS for Production

```yaml
# Production configuration
firefly:
  service-client:
    security:
      tls-enabled: true
      trust-store-path: ${TRUSTSTORE_PATH}
      key-store-path: ${KEYSTORE_PATH}
```

### 5. Monitor Rate Limiting

```java
// Log rate limiter statistics periodically
@Scheduled(fixedRate = 60000)  // Every minute
public void logRateLimiterStats() {
    RateLimiterStats stats = rateLimiter.getStats();
    if (stats.rejectedRequests() > 0) {
        log.warn("Rate limiter rejected {} requests", stats.rejectedRequests());
    }
}
```

### 6. Use Certificate Pinning for Critical Services

```java
// Pin certificates for payment, authentication services
CertificatePinningManager pinning = CertificatePinningManager.builder()
    .addPins("payment.example.com", 
        "sha256/primary-pin-hash",
        "sha256/backup-pin-hash")
    .strictMode(true)
    .build();
```

---

## Security Checklist

- [ ] Enable TLS/mTLS for all production services
- [ ] Use certificate pinning for critical services
- [ ] Rotate API keys regularly
- [ ] Validate JWTs with proper claims
- [ ] Encrypt sensitive data at rest
- [ ] Implement client-side rate limiting
- [ ] Use secrets management services
- [ ] Monitor security metrics
- [ ] Keep dependencies up to date
- [ ] Conduct regular security audits

