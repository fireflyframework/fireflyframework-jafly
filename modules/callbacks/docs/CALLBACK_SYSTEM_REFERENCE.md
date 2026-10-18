# Callback System - Complete Reference

> **Comprehensive documentation of the Firefly Callback Management System**

## Table of Contents

- [Overview](#overview)
- [System Components](#system-components)
- [Complete Callback Flow](#complete-callback-flow)
- [Domain Authorization](#domain-authorization)
- [Callback Configuration](#callback-configuration)
- [Custom Headers and Extra Parameters](#custom-headers-and-extra-parameters)
- [HMAC Signature](#hmac-signature)
- [Retries and Circuit Breaker](#retries-and-circuit-breaker)
- [Execution Tracking](#execution-tracking)
- [Practical Examples](#practical-examples)
- [Best Practices](#best-practices)

## Overview

The Firefly Callback Management System is a complete and robust platform for sending HTTP webhooks to external systems when events occur in the Firefly platform. The system is designed with the following principles:

### Key Features

✅ **Domain Authorization**: Granular control over which domains can receive callbacks
✅ **Custom Headers**: Full support for custom HTTP headers
✅ **Extra Parameters (Metadata)**: Flexible storage for additional data
✅ **HMAC Signature**: Cryptographic verification of callback authenticity
✅ **Smart Retries**: Exponential backoff with per-callback configuration
✅ **Circuit Breaker**: Protection against repeatedly failing endpoints
✅ **Complete Tracking**: Audit trail of all callback executions
✅ **Multi-tenant**: Support for multiple organizations/tenants
✅ **Reactive**: Non-blocking architecture with Spring WebFlux

## System Components

### 1. Authorized Domains

Authorized domains are the first line of defense in the system. **No callback can be sent to a domain that is not authorized and verified**.

#### Main Fields

```java
AuthorizedDomain {
    UUID id;                          // Unique domain ID
    String domain;                    // Domain (e.g., "api.example.com" or "localhost:8080")
    String organization;              // Owner organization
    String contactEmail;              // Contact email
    Boolean verified;                 // Domain verified?
    Boolean active;                   // Domain active?
    String[] allowedPaths;            // Allowed paths (e.g., ["/webhooks/*"])
    Integer maxCallbacksPerMinute;    // Rate limiting threshold
    String[] ipWhitelist;             // Allowed IPs (optional)
    Boolean requireHttps;             // Require HTTPS?
    Instant expiresAt;                // Expiration date (optional)
    Long totalCallbacks;              // Total callbacks sent
    Long totalFailed;                 // Total failed callbacks
}
```

#### Security Validations

The system automatically validates:

1. **Active domain**: `active = true`
2. **Verified domain**: `verified = true`
3. **Not expired**: `expiresAt` is null or future
4. **Allowed path**: If `allowedPaths` is configured, path must match
5. **HTTPS required**: If `requireHttps = true`, URL must use HTTPS

#### Creation Example

```json
POST /api/v1/authorized-domains
{
  "domain": "api.example.com",
  "organization": "Example Corp",
  "contactEmail": "webhooks@example.com",
  "verified": true,
  "active": true,
  "requireHttps": true,
  "allowedPaths": ["/webhooks/*", "/callbacks/*"],
  "maxCallbacksPerMinute": 100
}
```

### 2. Callback Configurations

Callback configurations define **how** and **when** webhooks are sent.

#### Main Fields

```java
CallbackConfiguration {
    UUID id;                          // Unique configuration ID
    String name;                      // Descriptive name
    String description;               // Description
    String url;                       // Webhook URL
    HttpMethod httpMethod;            // POST, PUT, PATCH
    CallbackStatus status;            // ACTIVE, PAUSED, DISABLED
    String[] subscribedEventTypes;    // Event types (e.g., ["customer.created", "order.*"])
    
    // Headers and Custom Parameters
    Map<String, String> customHeaders;    // Custom HTTP headers
    Map<String, Object> metadata;         // Metadata/extra parameters
    
    // Security
    String secret;                    // HMAC secret
    Boolean signatureEnabled;         // Enable HMAC signature?
    String signatureHeader;           // Signature header name (default: "X-Signature")
    
    // Retries
    Integer maxRetries;               // Maximum retry attempts (default: 3)
    Integer retryDelayMs;             // Initial delay in ms (default: 1000)
    Double retryBackoffMultiplier;    // Backoff multiplier (default: 2.0)
    Integer timeoutMs;                // Timeout in ms (default: 30000)
    
    // Control
    Boolean active;                   // Configuration active?
    String tenantId;                  // Tenant/organization ID
    String filterExpression;          // Filter expression (JSONPath)
    Integer failureThreshold;         // Failure threshold before auto-disable
    Integer failureCount;             // Consecutive failure counter
}
```

#### Complete Configuration Example

```json
POST /api/v1/callback-configurations
{
  "name": "CRM Customer Sync",
  "description": "Syncs customer events with our CRM",
  "url": "https://api.example.com/webhooks/firefly",
  "httpMethod": "POST",
  "status": "ACTIVE",
  "subscribedEventTypes": ["customer.created", "customer.updated", "customer.deleted"],
  
  "customHeaders": {
    "X-API-Key": "your-api-key-here",
    "X-Client-ID": "firefly-integration",
    "X-Custom-Header": "custom-value"
  },
  
  "metadata": {
    "environment": "production",
    "region": "us-east-1",
    "version": "2.0",
    "customField": "any-value"
  },
  
  "signatureEnabled": true,
  "secret": "your-secret-key-here",
  "signatureHeader": "X-Firefly-Signature",
  
  "maxRetries": 3,
  "retryDelayMs": 1000,
  "retryBackoffMultiplier": 2.0,
  "timeoutMs": 30000,
  
  "active": true,
  "tenantId": "tenant-123",
  "failureThreshold": 10
}
```

### 3. Callback Executions

Each callback send attempt is recorded in the database for complete audit trail.

#### Execution Fields

```java
CallbackExecution {
    UUID id;                          // Unique execution ID
    UUID configurationId;             // Configuration ID
    String eventType;                 // Event type
    UUID sourceEventId;               // Original event ID
    CallbackExecutionStatus status;   // SUCCESS, FAILED_RETRYING, FAILED_PERMANENT
    String requestPayload;            // JSON payload sent
    String requestHeaders;            // Headers sent (JSON)
    Integer responseStatusCode;       // HTTP response code
    String responseBody;              // Response body
    Integer attemptNumber;            // Attempt number (1, 2, 3...)
    Integer maxAttempts;              // Maximum configured attempts
    String errorMessage;              // Error message (if failed)
    Long requestDurationMs;           // Request duration in ms
    Instant executedAt;               // Execution timestamp
    Instant completedAt;              // Completion timestamp
}
```

## Complete Callback Flow

### Flow Diagram

```
1. Kafka Event → 2. Callback Router → 3. Domain Validation → 4. Dispatcher → 5. HTTP Request → 6. Recording
```

### Detailed Step-by-Step

#### 1. Event Reception

```java
// An event arrives from Kafka
{
  "eventType": "customer.created",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-11-01T10:00:00Z",
  "data": {
    "customerId": "CUST-123",
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

#### 2. Configuration Lookup

The `CallbackRouter` finds all active configurations matching the event type:

```java
// Searches for configurations where:
// - active = true
// - status = ACTIVE
// - subscribedEventTypes contains "customer.created" or "customer.*"
```

#### 3. Domain Validation

For each found configuration, the domain is validated:

```java
// Extract domain from URL
String domain = extractDomain("https://api.example.com:8080/webhooks/firefly");
// Result: "api.example.com:8080"

// Validate:
// ✓ Domain exists in authorized_domains
// ✓ active = true
// ✓ verified = true
// ✓ Not expired
// ✓ Path allowed
// ✓ HTTPS if required
```

#### 4. HTTP Request Preparation

The `CallbackDispatcher` prepares the request:

```java
// Standard headers
X-Event-Type: customer.created
X-Event-Id: 550e8400-e29b-41d4-a716-446655440000
X-Timestamp: 2025-11-01T10:00:00Z
Content-Type: application/json

// Custom headers (from customHeaders)
X-API-Key: your-api-key-here
X-Client-ID: firefly-integration
X-Custom-Header: custom-value

// HMAC signature (if enabled)
X-Firefly-Signature: base64(HMAC-SHA256(payload, secret))
```

#### 5. Send with Retries and Circuit Breaker

```java
// Attempt 1: Fails with 500
// → Wait 1000ms
// Attempt 2: Fails with 503
// → Wait 2000ms (1000 * 2.0)
// Attempt 3: Success with 200
// → Record SUCCESS
```

#### 6. Execution Recording

```java
// Saved in callback_executions
{
  "configurationId": "...",
  "eventType": "customer.created",
  "status": "SUCCESS",
  "responseStatusCode": 200,
  "attemptNumber": 3,
  "requestDurationMs": 245
}
```

## Custom Headers and Extra Parameters

### Custom Headers (customHeaders)

Custom headers are sent in **every HTTP request**. They are ideal for:

- **Authentication**: API keys, tokens
- **Identification**: Client IDs, correlation IDs
- **Routing**: Headers for load balancers or proxies
- **Metadata**: Additional request information

#### Usage Example

```json
{
  "customHeaders": {
    "Authorization": "Bearer eyJhbGciOiJIUzI1NiIs...",
    "X-API-Key": "sk_live_123456789",
    "X-Tenant-ID": "tenant-abc",
    "X-Request-ID": "req-xyz",
    "X-Environment": "production"
  }
}
```

### Metadata (Extra Parameters)

Metadata is stored in the configuration but **NOT sent in the HTTP request**. It's useful for:

- **Internal configuration**: Integration parameters
- **Documentation**: Information about the callback
- **Filtering**: Data for searches and reports
- **Extensibility**: Custom fields without schema changes

#### Usage Example

```json
{
  "metadata": {
    "integration_version": "2.1.0",
    "created_by_user": "admin@firefly.com",
    "business_unit": "sales",
    "cost_center": "CC-1234",
    "notes": "Callback for Salesforce synchronization",
    "custom_config": {
      "batch_size": 100,
      "priority": "high"
    }
  }
}
```

### Key Difference

| Aspect | customHeaders | metadata |
|---------|---------------|----------|
| **Sent in HTTP** | ✅ Yes | ❌ No |
| **Data type** | Map<String, String> | Map<String, Object> |
| **Primary use** | Authentication, routing | Configuration, documentation |
| **Visible to receiver** | ✅ Yes | ❌ No |

## HMAC Signature

### What is HMAC?

HMAC (Hash-based Message Authentication Code) is a cryptographic mechanism that allows the receiver to verify that:

1. The callback actually comes from Firefly
2. The payload has not been modified in transit

### Configuration

```json
{
  "signatureEnabled": true,
  "secret": "your-secret-key-here",
  "signatureHeader": "X-Firefly-Signature"
}
```

### Signature Generation

```java
// Firefly generates the signature like this:
String payload = "{\"eventType\":\"customer.created\",\"data\":{...}}";
String secret = "your-secret-key-here";

Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
mac.init(secretKey);
byte[] signatureBytes = mac.doFinal(payload.getBytes(UTF_8));
String signature = Base64.getEncoder().encodeToString(signatureBytes);

// Header sent:
// X-Firefly-Signature: 8F7A3B2C1D9E8F7A6B5C4D3E2F1A0B9C8D7E6F5A4B3C2D1E0F9A8B7C6D5E4F3A
```

### Verification in Receiver

```python
# Python example
import hmac
import hashlib
import base64

def verify_signature(payload, signature, secret):
    expected = base64.b64encode(
        hmac.new(
            secret.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).digest()
    ).decode('utf-8')
    
    return hmac.compare_digest(signature, expected)

# Usage
payload = request.body
signature = request.headers['X-Firefly-Signature']
secret = 'your-secret-key-here'

if verify_signature(payload, signature, secret):
    # Valid signature, process callback
    process_callback(payload)
else:
    # Invalid signature, reject
    return 401
```

## Retries and Circuit Breaker

### Retry Strategy

The system implements **exponential backoff** with flexible configuration:

```java
// Configuration
maxRetries = 3
retryDelayMs = 1000
retryBackoffMultiplier = 2.0

// Wait times
Attempt 1: Immediate
Attempt 2: Wait 1000ms (1s)
Attempt 3: Wait 2000ms (2s)
Attempt 4: Wait 4000ms (4s)
```

### When to Retry?

Automatic retry in these cases:

- **5xx errors**: 500, 502, 503, 504 (server errors)
- **Timeout**: 408 Request Timeout
- **Rate Limit**: 429 Too Many Requests
- **Network errors**: Connection refused, timeout, etc.

**Does NOT retry** in these cases:

- **4xx errors** (except 408 and 429): 400, 401, 403, 404, etc.
- **Domain authorization errors**
- **Configuration errors**

### Circuit Breaker

The circuit breaker protects against repeatedly failing endpoints:

```
States:
CLOSED → OPEN → HALF_OPEN → CLOSED

CLOSED: Normal operation
OPEN: Too many failures, requests not sent
HALF_OPEN: Testing if endpoint recovered
```

#### Configuration

```yaml
circuit-breaker:
  failure-rate-threshold: 50          # Opens if >50% of requests fail
  slow-call-rate-threshold: 50        # Opens if >50% of requests are slow
  slow-call-duration-threshold-ms: 10000  # Request is "slow" if >10s
  sliding-window-size: 100            # Window of 100 requests
  minimum-number-of-calls: 10         # Minimum 10 calls before calculating
  wait-duration-in-open-state-ms: 60000  # Wait 60s before HALF_OPEN
```

## Execution Tracking

### Query Executions

```bash
# All executions for a configuration
GET /api/v1/callback-executions/by-configuration/{configId}

# Executions by status
GET /api/v1/callback-executions/by-status?status=FAILED_PERMANENT

# Recent executions
GET /api/v1/callback-executions/recent?configurationId={id}&duration=PT1H

# Advanced filtering
POST /api/v1/callback-executions/filter
{
  "filters": {
    "status": "SUCCESS",
    "eventType": "customer.created"
  },
  "page": 0,
  "size": 20,
  "sort": [{"field": "executedAt", "direction": "DESC"}]
}
```

### Execution States

- **SUCCESS**: Successful callback (2xx response)
- **FAILED_RETRYING**: Failed but will retry
- **FAILED_PERMANENT**: Permanently failed (exhausted retries)

## Practical Examples

See the `docs/CALLBACK_EXAMPLES.md` file for complete examples of:

- Salesforce integration
- Zapier integration
- Custom webhooks
- Error handling
- Callback testing

## Best Practices

### 1. Security

✅ **Always enable HMAC** for production callbacks
✅ **Use HTTPS** for all endpoints
✅ **Configure allowedPaths** to limit routes
✅ **Rotate secrets** periodically
✅ **Monitor authorization failures**

### 2. Reliability

✅ **Configure appropriate retries** (3-5 attempts)
✅ **Use reasonable timeouts** (30s default)
✅ **Implement idempotency** in receiver
✅ **Monitor circuit breakers**
✅ **Review failed executions** regularly

### 3. Performance

✅ **Use custom headers** instead of metadata for data the receiver needs
✅ **Configure appropriate rate limits**
✅ **Optimize receiver endpoint** to respond quickly
✅ **Use event filters** to reduce unnecessary callbacks

### 4. Maintenance

✅ **Document the purpose** of each configuration
✅ **Use descriptive names**
✅ **Keep metadata up to date**
✅ **Clean up inactive configurations**
✅ **Monitor callback metrics**

---

**© 2025 Firefly Software Foundation. All rights reserved.**

