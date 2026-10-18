# Callback System - Practical Examples

> **Practical guide with real integration examples**

## Table of Contents

- [Example 1: Basic Integration](#example-1-basic-integration)
- [Example 2: Salesforce Integration](#example-2-salesforce-integration)
- [Example 3: Zapier Integration](#example-3-zapier-integration)
- [Example 4: Webhook with OAuth Authentication](#example-4-webhook-with-oauth-authentication)
- [Example 5: Callback with Extra Parameters](#example-5-callback-with-extra-parameters)
- [Example 6: Error Handling](#example-6-error-handling)
- [Example 7: Testing Callbacks](#example-7-testing-callbacks)

## Example 1: Basic Integration

### Step 1: Authorize the Domain

```bash
curl -X POST http://localhost:8080/api/v1/authorized-domains \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "webhook.site",
    "organization": "Testing",
    "contactEmail": "test@example.com",
    "verified": true,
    "active": true,
    "requireHttps": true
  }'
```

### Step 2: Create Callback Configuration

```bash
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Basic Webhook",
    "description": "Simple webhook for testing",
    "url": "https://webhook.site/your-unique-url",
    "httpMethod": "POST",
    "subscribedEventTypes": ["customer.created"],
    "signatureEnabled": false,
    "maxRetries": 3,
    "active": true
  }'
```

### Step 3: Create Event Subscription

```bash
curl -X POST http://localhost:8080/api/v1/event-subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Customer Events",
    "messagingSystemType": "KAFKA",
    "topicOrQueue": "customer-events",
    "consumerGroupId": "callback-consumer",
    "active": true,
    "eventTypePatterns": ["customer.*"]
  }'
```

### Result

When a `customer.created` event is published to Kafka, you will receive:

```http
POST https://webhook.site/your-unique-url
Content-Type: application/json
X-Event-Type: customer.created
X-Event-Id: 550e8400-e29b-41d4-a716-446655440000
X-Timestamp: 2025-11-01T10:00:00Z

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

## Example 2: Salesforce Integration

### Complete Configuration

```json
{
  "name": "Salesforce Customer Sync",
  "description": "Syncs Firefly customers with Salesforce",
  "url": "https://your-instance.salesforce.com/services/apexrest/firefly/webhook",
  "httpMethod": "POST",
  "subscribedEventTypes": [
    "customer.created",
    "customer.updated",
    "customer.deleted"
  ],
  
  "customHeaders": {
    "Authorization": "Bearer 00D5g000000xxxx!ARxxxxxxxxxxxxxxx",
    "Content-Type": "application/json",
    "X-Salesforce-Client": "Firefly-Integration"
  },
  
  "metadata": {
    "salesforce_instance": "your-instance.salesforce.com",
    "integration_version": "2.0",
    "object_type": "Contact",
    "field_mapping": {
      "customerId": "External_ID__c",
      "name": "Name",
      "email": "Email"
    }
  },
  
  "signatureEnabled": true,
  "secret": "your-salesforce-webhook-secret",
  "signatureHeader": "X-Firefly-Signature",
  
  "maxRetries": 5,
  "retryDelayMs": 2000,
  "retryBackoffMultiplier": 2.0,
  "timeoutMs": 45000,
  
  "active": true,
  "tenantId": "salesforce-integration"
}
```

### Apex Handler in Salesforce

```apex
@RestResource(urlMapping='/firefly/webhook')
global class FireflyWebhookHandler {
    
    @HttpPost
    global static void handleWebhook() {
        RestRequest req = RestContext.request;
        RestResponse res = RestContext.response;
        
        try {
            // Verify HMAC signature
            String signature = req.headers.get('X-Firefly-Signature');
            String payload = req.requestBody.toString();
            
            if (!verifySignature(payload, signature)) {
                res.statusCode = 401;
                res.responseBody = Blob.valueOf('{"error": "Invalid signature"}');
                return;
            }
            
            // Parse payload
            Map<String, Object> data = (Map<String, Object>) JSON.deserializeUntyped(payload);
            String eventType = (String) data.get('eventType');
            Map<String, Object> eventData = (Map<String, Object>) data.get('data');
            
            // Process by event type
            if (eventType == 'customer.created') {
                createContact(eventData);
            } else if (eventType == 'customer.updated') {
                updateContact(eventData);
            } else if (eventType == 'customer.deleted') {
                deleteContact(eventData);
            }
            
            res.statusCode = 200;
            res.responseBody = Blob.valueOf('{"status": "success"}');
            
        } catch (Exception e) {
            res.statusCode = 500;
            res.responseBody = Blob.valueOf('{"error": "' + e.getMessage() + '"}');
        }
    }
    
    private static Boolean verifySignature(String payload, String signature) {
        String secret = 'your-salesforce-webhook-secret';
        Blob hmac = Crypto.generateMac('HmacSHA256', 
            Blob.valueOf(payload), 
            Blob.valueOf(secret));
        String expected = EncodingUtil.base64Encode(hmac);
        return signature == expected;
    }
    
    private static void createContact(Map<String, Object> data) {
        Contact c = new Contact(
            External_ID__c = (String) data.get('customerId'),
            Name = (String) data.get('name'),
            Email = (String) data.get('email')
        );
        insert c;
    }
    
    private static void updateContact(Map<String, Object> data) {
        Contact c = [SELECT Id FROM Contact 
                     WHERE External_ID__c = :(String) data.get('customerId') 
                     LIMIT 1];
        c.Name = (String) data.get('name');
        c.Email = (String) data.get('email');
        update c;
    }
    
    private static void deleteContact(Map<String, Object> data) {
        Contact c = [SELECT Id FROM Contact 
                     WHERE External_ID__c = :(String) data.get('customerId') 
                     LIMIT 1];
        delete c;
    }
}
```

## Example 3: Zapier Integration

### Configuration

```json
{
  "name": "Zapier Webhook",
  "description": "Sends events to Zapier for automation",
  "url": "https://hooks.zapier.com/hooks/catch/123456/abcdef/",
  "httpMethod": "POST",
  "subscribedEventTypes": ["*"],
  
  "customHeaders": {
    "X-Zapier-Client": "Firefly",
    "X-Hook-Secret": "your-zapier-secret"
  },
  
  "metadata": {
    "zap_name": "Firefly to Slack Notifications",
    "zap_id": "123456",
    "created_by": "admin@firefly.com"
  },
  
  "signatureEnabled": false,
  "maxRetries": 3,
  "active": true
}
```

### Zap Configuration

1. **Trigger**: Webhooks by Zapier → Catch Hook
2. **URL**: Copy from callback configuration
3. **Action**: Slack → Send Channel Message

```javascript
// Zapier Code (optional for data transformation)
const eventType = inputData.eventType;
const data = inputData.data;

let message = '';

if (eventType === 'customer.created') {
  message = `🎉 New customer: ${data.name} (${data.email})`;
} else if (eventType === 'order.completed') {
  message = `💰 Order completed: ${data.orderId} - $${data.amount}`;
}

output = {
  channel: '#notifications',
  message: message
};
```

## Example 4: Webhook with OAuth Authentication

### Configuration

```json
{
  "name": "OAuth Protected Webhook",
  "description": "Webhook requiring OAuth token",
  "url": "https://api.partner.com/webhooks/firefly",
  "httpMethod": "POST",
  "subscribedEventTypes": ["payment.received"],
  
  "customHeaders": {
    "Authorization": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "X-Client-ID": "firefly-client-id",
    "X-Correlation-ID": "{{eventId}}"
  },
  
  "metadata": {
    "oauth_provider": "partner-oauth",
    "token_expires_at": "2025-12-31T23:59:59Z",
    "refresh_token": "encrypted-refresh-token",
    "scopes": ["webhooks:write", "events:read"]
  },
  
  "signatureEnabled": true,
  "secret": "shared-webhook-secret",
  
  "maxRetries": 3,
  "timeoutMs": 30000,
  "active": true
}
```

### Receiver with OAuth Validation

```javascript
// Node.js/Express example
const express = require('express');
const crypto = require('crypto');
const app = express();

app.post('/webhooks/firefly', express.json(), async (req, res) => {
  try {
    // 1. Validate OAuth token
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid authorization' });
    }
    
    const token = authHeader.substring(7);
    const isValid = await validateOAuthToken(token);
    if (!isValid) {
      return res.status(401).json({ error: 'Invalid token' });
    }
    
    // 2. Verify HMAC signature
    const signature = req.headers['x-firefly-signature'];
    const payload = JSON.stringify(req.body);
    const secret = 'shared-webhook-secret';
    
    const expectedSignature = crypto
      .createHmac('sha256', secret)
      .update(payload)
      .digest('base64');
    
    if (signature !== expectedSignature) {
      return res.status(401).json({ error: 'Invalid signature' });
    }
    
    // 3. Process event
    const { eventType, data } = req.body;
    
    if (eventType === 'payment.received') {
      await processPayment(data);
    }
    
    res.status(200).json({ status: 'success' });
    
  } catch (error) {
    console.error('Webhook error:', error);
    res.status(500).json({ error: error.message });
  }
});

async function validateOAuthToken(token) {
  // Validate with OAuth provider
  // Provider-specific implementation
  return true;
}

async function processPayment(data) {
  console.log('Processing payment:', data);
  // Business logic
}

app.listen(3000);
```

## Example 5: Callback with Extra Parameters

### Configuration with Rich Metadata

```json
{
  "name": "Analytics Webhook",
  "description": "Sends events to analytics platform",
  "url": "https://analytics.example.com/api/events",
  "httpMethod": "POST",
  "subscribedEventTypes": ["*"],
  
  "customHeaders": {
    "X-API-Key": "analytics-api-key",
    "X-Source": "firefly-callbacks",
    "X-Environment": "production"
  },
  
  "metadata": {
    "analytics_config": {
      "project_id": "firefly-prod",
      "dataset": "events",
      "table": "callback_events"
    },
    "data_retention_days": 90,
    "pii_handling": "anonymize",
    "event_enrichment": {
      "add_geo_data": true,
      "add_user_agent": false,
      "add_timestamp_utc": true
    },
    "alerting": {
      "enabled": true,
      "slack_channel": "#analytics-alerts",
      "error_threshold": 10
    },
    "cost_tracking": {
      "department": "Engineering",
      "cost_center": "CC-1234",
      "budget_code": "ANALYTICS-2025"
    }
  },
  
  "signatureEnabled": true,
  "secret": "analytics-webhook-secret",
  "maxRetries": 5,
  "active": true
}
```

### Query Metadata

```bash
# Get configuration with metadata
curl http://localhost:8080/api/v1/callback-configurations/{id} | jq '.metadata'

# Result
{
  "analytics_config": {
    "project_id": "firefly-prod",
    "dataset": "events",
    "table": "callback_events"
  },
  "data_retention_days": 90,
  ...
}
```

## Example 6: Error Handling

### Configuration with Robust Error Handling

```json
{
  "name": "Resilient Webhook",
  "description": "Webhook with advanced error handling",
  "url": "https://api.example.com/webhooks",
  "httpMethod": "POST",
  "subscribedEventTypes": ["order.*"],
  
  "maxRetries": 5,
  "retryDelayMs": 1000,
  "retryBackoffMultiplier": 2.0,
  "timeoutMs": 30000,
  
  "failureThreshold": 20,
  
  "metadata": {
    "error_handling": {
      "retry_on_timeout": true,
      "retry_on_5xx": true,
      "retry_on_429": true,
      "max_retry_delay_ms": 60000
    },
    "monitoring": {
      "alert_on_failure_threshold": true,
      "alert_email": "ops@example.com"
    }
  },
  
  "active": true
}
```

### Monitor Failures

```bash
# View failed executions
curl "http://localhost:8080/api/v1/callback-executions/by-status?status=FAILED_PERMANENT" | jq

# Filter by configuration and status
curl -X POST http://localhost:8080/api/v1/callback-executions/filter \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "configurationId": "your-config-id",
      "status": "FAILED_RETRYING"
    },
    "page": 0,
    "size": 50
  }' | jq
```

## Example 7: Testing Callbacks

### Test Setup with WireMock

```java
@SpringBootTest
class CallbackIntegrationTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    private WireMockServer wireMockServer;
    
    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }
    
    @Test
    void testCallbackWithCustomHeaders() {
        // 1. Setup WireMock stub
        stubFor(post(urlEqualTo("/webhook"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\":\"success\"}")));
        
        // 2. Create authorized domain
        AuthorizedDomainDTO domain = AuthorizedDomainDTO.builder()
            .domain("localhost:8089")
            .verified(true)
            .active(true)
            .build();
        
        webTestClient.post()
            .uri("/api/v1/authorized-domains")
            .bodyValue(domain)
            .exchange()
            .expectStatus().isCreated();
        
        // 3. Create configuration with custom headers
        Map<String, String> customHeaders = Map.of(
            "X-API-Key", "test-key",
            "X-Custom", "value"
        );
        
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
            .name("Test Webhook")
            .url("http://localhost:8089/webhook")
            .httpMethod(HttpMethod.POST)
            .subscribedEventTypes(new String[]{"test.event"})
            .customHeaders(customHeaders)
            .active(true)
            .build();
        
        webTestClient.post()
            .uri("/api/v1/callback-configurations")
            .bodyValue(config)
            .exchange()
            .expectStatus().isCreated();
        
        // 4. Simulate event (this would trigger callback in real test)
        // In a complete test, you would publish to Kafka and verify
        
        // 5. Verify WireMock received request with headers
        verify(postRequestedFor(urlEqualTo("/webhook"))
            .withHeader("X-API-Key", equalTo("test-key"))
            .withHeader("X-Custom", equalTo("value"))
            .withHeader("X-Event-Type", matching(".*"))
            .withHeader("Content-Type", equalTo("application/json")));
    }
    
    @AfterEach
    void teardown() {
        wireMockServer.stop();
    }
}
```

### HMAC Verification Test

```python
# Python test example
import hmac
import hashlib
import base64
import requests

def test_hmac_signature():
    # Configuration
    url = "http://localhost:8080/api/v1/callback-configurations"
    secret = "test-secret-key"
    
    # Create configuration with HMAC
    config = {
        "name": "HMAC Test",
        "url": "https://webhook.site/test",
        "httpMethod": "POST",
        "subscribedEventTypes": ["test.event"],
        "signatureEnabled": True,
        "secret": secret,
        "signatureHeader": "X-Test-Signature",
        "active": True
    }
    
    response = requests.post(url, json=config)
    assert response.status_code == 201
    
    # Simulate payload that webhook would receive
    payload = '{"eventType":"test.event","data":{"test":"value"}}'
    
    # Calculate expected signature
    expected_signature = base64.b64encode(
        hmac.new(
            secret.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).digest()
    ).decode('utf-8')
    
    print(f"Expected signature: {expected_signature}")
    
    # In a real test, you would verify the webhook received
    # the X-Test-Signature header with this value
```

---

**© 2025 Firefly Software Foundation. All rights reserved.**

