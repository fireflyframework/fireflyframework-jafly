# Quickstart Guide

> **Get up and running with the Firefly Framework Callbacks Library in 10 minutes**

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Your First Callback](#your-first-callback)
- [Testing the Setup](#testing-the-setup)
- [Common Use Cases](#common-use-cases)
- [Troubleshooting](#troubleshooting)
- [Next Steps](#next-steps)

## Prerequisites

Before you begin, ensure you have the following installed:

### Required

- **Java 21** or higher
  ```bash
  java -version
  # Should show: openjdk version "21" or higher
  ```

- **Maven 3.8+**
  ```bash
  mvn -version
  # Should show: Apache Maven 3.8.x or higher
  ```

- **PostgreSQL 14+**
  ```bash
  psql --version
  # Should show: psql (PostgreSQL) 14.x or higher
  ```

- **Apache Kafka 3.0+** (or Confluent Platform 7.5+)
  ```bash
  kafka-topics --version
  # Should show: 3.0.x or higher
  ```

### Optional (Recommended)

- **Docker & Docker Compose** - For running dependencies easily
  ```bash
  docker --version
  docker-compose --version
  ```

- **curl** or **Postman** - For testing API endpoints

- **jq** - For pretty-printing JSON responses
  ```bash
  brew install jq  # macOS
  sudo apt-get install jq  # Ubuntu/Debian
  ```

## Installation

### Option 1: Using Docker Compose (Recommended)

This is the fastest way to get started with all dependencies.

#### 1. Clone the Repository

```bash
git clone https://github.com/firefly-oss/fireflyframework-callbacks.git
cd fireflyframework-callbacks
```

#### 2. Start Dependencies

```bash
# Start PostgreSQL and Kafka
docker-compose up -d postgres kafka zookeeper

# Verify containers are running
docker-compose ps
```

Expected output:
```
NAME                STATUS              PORTS
postgres            Up 10 seconds       0.0.0.0:5432->5432/tcp
kafka               Up 10 seconds       0.0.0.0:9092->9092/tcp
zookeeper           Up 10 seconds       0.0.0.0:2181->2181/tcp
```

#### 3. Build the Application

```bash
mvn clean install
```

#### 4. Run the Application

```bash
cd fireflyframework-callbacks-web
mvn spring-boot:run
```

### Option 2: Manual Installation

If you prefer to install dependencies manually:

#### 1. Install PostgreSQL

**macOS (Homebrew)**:
```bash
brew install postgresql@14
brew services start postgresql@14
```

**Ubuntu/Debian**:
```bash
sudo apt-get update
sudo apt-get install postgresql-14
sudo systemctl start postgresql
```

**Create Database**:
```bash
psql -U postgres
CREATE DATABASE callbacks_db;
CREATE USER firefly WITH PASSWORD 'firefly';
GRANT ALL PRIVILEGES ON DATABASE callbacks_db TO firefly;
\q
```

#### 2. Install Apache Kafka

**macOS (Homebrew)**:
```bash
brew install kafka
brew services start zookeeper
brew services start kafka
```

**Manual Installation**:
```bash
# Download Kafka
wget https://downloads.apache.org/kafka/3.6.0/kafka_2.13-3.6.0.tgz
tar -xzf kafka_2.13-3.6.0.tgz
cd kafka_2.13-3.6.0

# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties &

# Start Kafka
bin/kafka-server-start.sh config/server.properties &
```

#### 3. Clone and Build

```bash
git clone https://github.com/firefly-oss/fireflyframework-callbacks.git
cd fireflyframework-callbacks
mvn clean install
```

#### 4. Run the Application

```bash
cd fireflyframework-callbacks-web
mvn spring-boot:run
```

## Configuration

### Environment Variables

Create a `.env` file in the project root (or export these variables):

```bash
# Database Configuration
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=callbacks_db
export DB_USERNAME=firefly
export DB_PASSWORD=firefly

# Kafka Configuration
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_CONSUMER_GROUP=callbacks-mgmt-consumer

# Server Configuration
export SERVER_PORT=8080

# Logging
export LOG_LEVEL=INFO
```

### Verify Configuration

The application will automatically:
1. Connect to PostgreSQL using R2DBC
2. Run Flyway migrations to create tables
3. Connect to Kafka for event consumption
4. Start the REST API on port 8080

## Running the Application

### Start the Application

```bash
cd fireflyframework-callbacks-web
mvn spring-boot:run
```

### Verify Application is Running

#### 1. Check Health Endpoint

```bash
curl http://localhost:8080/actuator/health | jq
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

#### 2. Access Swagger UI

Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```

You should see the interactive API documentation.

#### 3. Check Application Info

```bash
curl http://localhost:8080/actuator/info | jq
```

## Your First Callback

Let's create a complete end-to-end callback flow in 5 steps.

### Step 1: Authorize a Domain

Before creating callbacks, you must authorize the target domain.

```bash
curl -X POST http://localhost:8080/api/v1/authorized-domains \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "webhook.site",
    "organization": "Test Organization",
    "verified": true,
    "active": true,
    "requireHttps": true
  }' | jq
```

**Response**:
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "domain": "webhook.site",
  "organization": "Test Organization",
  "verified": true,
  "active": true,
  "requireHttps": true,
  "createdAt": "2025-01-15T10:00:00Z"
}
```

**💡 Tip**: Use [webhook.site](https://webhook.site) to get a free test webhook URL.

### Step 2: Create an Event Subscription

Subscribe to a Kafka topic to receive events.

```bash
curl -X POST http://localhost:8080/api/v1/event-subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Customer Events Subscription",
    "description": "Subscribe to all customer events",
    "messagingSystemType": "KAFKA",
    "connectionConfig": {
      "bootstrap.servers": "localhost:9092",
      "group.id": "callbacks-consumer"
    },
    "topicOrQueue": "customer.events",
    "consumerGroupId": "callbacks-consumer",
    "eventTypePatterns": ["customer.*"],
    "active": true
  }' | jq
```

**Response**:
```json
{
  "id": "234e5678-e89b-12d3-a456-426614174001",
  "name": "Customer Events Subscription",
  "description": "Subscribe to all customer events",
  "messagingSystemType": "KAFKA",
  "connectionConfig": {
    "bootstrap.servers": "localhost:9092",
    "group.id": "callbacks-consumer"
  },
  "topicOrQueue": "customer.events",
  "consumerGroupId": "callbacks-consumer",
  "eventTypePatterns": ["customer.*"],
  "active": true,
  "maxConcurrentConsumers": 1,
  "pollingIntervalMs": 1000,
  "totalMessagesReceived": 0,
  "totalMessagesFailed": 0,
  "createdAt": "2025-01-15T10:01:00Z"
}
```

### Step 3: Create a Callback Configuration

Create a webhook that will be called when events are received.

```bash
# First, get a test webhook URL from https://webhook.site
# Copy the unique URL (e.g., https://webhook.site/abc123)

curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Customer CRM Webhook",
    "description": "Send customer events to CRM",
    "url": "https://webhook.site/YOUR-UNIQUE-ID",
    "httpMethod": "POST",
    "subscribedEventTypes": ["customer.created", "customer.updated"],
    "status": "ACTIVE",
    "signatureEnabled": true,
    "secret": "my-secret-key",
    "maxRetries": 3,
    "retryDelayMs": 1000,
    "retryBackoffMultiplier": 2.0,
    "timeoutMs": 30000,
    "active": true
  }' | jq
```

**Response**:
```json
{
  "id": "345e6789-e89b-12d3-a456-426614174002",
  "name": "Customer CRM Webhook",
  "description": "Send customer events to CRM",
  "url": "https://webhook.site/YOUR-UNIQUE-ID",
  "httpMethod": "POST",
  "status": "ACTIVE",
  "subscribedEventTypes": ["customer.created", "customer.updated"],
  "signatureEnabled": true,
  "maxRetries": 3,
  "retryDelayMs": 1000,
  "retryBackoffMultiplier": 2.0,
  "timeoutMs": 30000,
  "active": true,
  "failureThreshold": 10,
  "failureCount": 0,
  "createdAt": "2025-01-15T10:02:00Z"
}
```

### Step 4: Publish a Test Event to Kafka

Now let's publish a test event to Kafka to trigger the callback.

**Using Kafka Console Producer**:

```bash
# Create the topic if it doesn't exist
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic customer.events \
  --partitions 1 \
  --replication-factor 1

# Publish a test event
echo '{
  "eventType": "customer.created",
  "eventId": "456e7890-e89b-12d3-a456-426614174003",
  "timestamp": "2025-01-15T10:03:00Z",
  "payload": {
    "customerId": "CUST-001",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "status": "ACTIVE"
  }
}' | kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic customer.events
```

**Using kafkacat (kcat)**:

```bash
# Install kafkacat
brew install kcat  # macOS

# Publish event
echo '{
  "eventType": "customer.created",
  "eventId": "456e7890-e89b-12d3-a456-426614174003",
  "timestamp": "2025-01-15T10:03:00Z",
  "payload": {
    "customerId": "CUST-001",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com"
  }
}' | kcat -P -b localhost:9092 -t customer.events
```

### Step 5: Verify the Callback

#### Check webhook.site

Go to your webhook.site URL in the browser. You should see the incoming request with:

**Headers**:
```
X-Event-Type: customer.created
X-Event-Id: 456e7890-e89b-12d3-a456-426614174003
X-Timestamp: 2025-01-15T10:03:00Z
X-Firefly-Signature: <base64-encoded-hmac>
Content-Type: application/json
```

**Body**:
```json
{
  "customerId": "CUST-001",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "status": "ACTIVE"
}
```

#### Query Callback Executions

```bash
curl -X POST http://localhost:8080/api/v1/callback-executions/filter \
  -H "Content-Type: application/json" \
  -d '{
    "page": 0,
    "size": 10,
    "sort": ["executedAt,DESC"]
  }' | jq
```

**Response**:
```json
{
  "content": [
    {
      "id": "567e8901-e89b-12d3-a456-426614174004",
      "configurationId": "345e6789-e89b-12d3-a456-426614174002",
      "eventType": "customer.created",
      "sourceEventId": "456e7890-e89b-12d3-a456-426614174003",
      "status": "SUCCESS",
      "responseStatusCode": 200,
      "attemptNumber": 0,
      "requestDurationMs": 245,
      "executedAt": "2025-01-15T10:03:01Z",
      "completedAt": "2025-01-15T10:03:01Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "numberOfElements": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

🎉 **Congratulations!** You've successfully set up your first end-to-end callback flow!

## Testing the Setup

### Test Retry Logic

Create a callback that will fail and retry:

```bash
# Use a non-existent URL to trigger retries
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Failing Webhook",
    "url": "https://webhook.site/non-existent-endpoint",
    "httpMethod": "POST",
    "subscribedEventTypes": ["customer.created"],
    "status": "ACTIVE",
    "maxRetries": 3,
    "retryDelayMs": 1000,
    "active": true
  }' | jq
```

Publish an event and watch the retries in the logs:

```bash
# Watch application logs
tail -f fireflyframework-callbacks-web/target/spring-boot.log
```

### Test Circuit Breaker

Publish multiple failing events to trigger the circuit breaker:

```bash
# Publish 20 events quickly
for i in {1..20}; do
  echo "{\"eventType\":\"customer.created\",\"eventId\":\"$i\",\"payload\":{}}" | \
  kafka-console-producer --bootstrap-server localhost:9092 --topic customer.events
done
```

Check circuit breaker metrics:

```bash
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | jq
```

### Test HMAC Signature Verification

Verify the HMAC signature on the receiving end:

**Node.js Example**:
```javascript
const crypto = require('crypto');

function verifySignature(payload, signature, secret) {
    const hmac = crypto.createHmac('sha256', secret);
    hmac.update(JSON.stringify(payload));
    const expectedSignature = hmac.digest('base64');
    return signature === expectedSignature;
}

// In your webhook handler
app.post('/webhook', (req, res) => {
    const signature = req.headers['x-firefly-signature'];
    const payload = req.body;
    const secret = 'my-secret-key';
    
    if (verifySignature(payload, signature, secret)) {
        console.log('✅ Signature verified');
        res.status(200).send('OK');
    } else {
        console.log('❌ Invalid signature');
        res.status(401).send('Unauthorized');
    }
});
```

## Common Use Cases

### Use Case 1: CRM Integration

Send customer lifecycle events to Salesforce or HubSpot.

```bash
# 1. Authorize CRM domain
curl -X POST http://localhost:8080/api/v1/authorized-domains \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "api.salesforce.com",
    "organization": "Salesforce",
    "verified": true,
    "active": true
  }' | jq

# 2. Create callback for customer events
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Salesforce Customer Sync",
    "url": "https://api.salesforce.com/webhooks/firefly",
    "httpMethod": "POST",
    "subscribedEventTypes": ["customer.created", "customer.updated", "customer.deleted"],
    "status": "ACTIVE",
    "signatureEnabled": true,
    "secret": "salesforce-webhook-secret",
    "active": true
  }' | jq
```

### Use Case 2: Analytics Pipeline

Stream events to Segment or custom analytics platform.

```bash
# 1. Authorize analytics domain
curl -X POST http://localhost:8080/api/v1/authorized-domains \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "api.segment.io",
    "organization": "Segment",
    "verified": true,
    "active": true
  }' | jq

# 2. Create callback for all events
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Segment Analytics",
    "url": "https://api.segment.io/v1/track",
    "httpMethod": "POST",
    "subscribedEventTypes": ["*"],
    "status": "ACTIVE",
    "customHeaders": {
      "Authorization": "Bearer YOUR_SEGMENT_KEY"
    },
    "active": true
  }' | jq
```

### Use Case 3: Multi-Tenant Callbacks

Configure different callbacks per tenant.

```bash
# Tenant A - CRM Integration
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tenant A - CRM",
    "url": "https://tenant-a.crm.com/webhooks",
    "subscribedEventTypes": ["customer.*"],
    "tenantId": "tenant-a",
    "active": true
  }' | jq

# Tenant B - Analytics
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tenant B - Analytics",
    "url": "https://tenant-b.analytics.com/events",
    "subscribedEventTypes": ["*"],
    "tenantId": "tenant-b",
    "active": true
  }' | jq
```

## Troubleshooting

### Issue: Application won't start

**Error**: `Connection refused: localhost:5432`

**Solution**: Ensure PostgreSQL is running
```bash
# Check if PostgreSQL is running
docker-compose ps postgres
# or
brew services list | grep postgresql

# Start PostgreSQL
docker-compose up -d postgres
# or
brew services start postgresql@14
```

---

**Error**: `Connection refused: localhost:9092`

**Solution**: Ensure Kafka is running
```bash
# Check if Kafka is running
docker-compose ps kafka
# or
brew services list | grep kafka

# Start Kafka
docker-compose up -d kafka
# or
brew services start kafka
```

### Issue: Callbacks not being sent

**Possible Causes**:

1. **Domain not authorized**
   ```bash
   # Check authorized domains
   curl -X POST http://localhost:8080/api/v1/authorized-domains/filter \
     -H "Content-Type: application/json" \
     -d '{"page": 0, "size": 100}' | jq
   ```

2. **Event subscription not active**
   ```bash
   # Check subscriptions
   curl -X POST http://localhost:8080/api/v1/event-subscriptions/filter \
     -H "Content-Type: application/json" \
     -d '{"page": 0, "size": 100}' | jq
   ```

3. **Event type mismatch**
   - Ensure `subscribedEventTypes` in callback config matches the `eventType` in the Kafka message

4. **Circuit breaker is OPEN**
   ```bash
   # Check circuit breaker state
   curl http://localhost:8080/actuator/health | jq '.components.circuitBreakers'
   ```

### Issue: Database migration failed

**Error**: `Flyway migration failed`

**Solution**: Reset the database
```bash
# Drop and recreate database
psql -U postgres
DROP DATABASE callbacks_db;
CREATE DATABASE callbacks_db;
GRANT ALL PRIVILEGES ON DATABASE callbacks_db TO firefly;
\q

# Restart application (migrations will run automatically)
mvn spring-boot:run
```

### Issue: High memory usage

**Solution**: Adjust JVM heap size
```bash
export MAVEN_OPTS="-Xmx512m -Xms256m"
mvn spring-boot:run
```

### Enable Debug Logging

```bash
export LOG_LEVEL=DEBUG
export LOG_LEVEL_R2DBC=DEBUG
mvn spring-boot:run
```

## Next Steps

Now that you have the basics working, explore these advanced topics:

### 1. Learn the Architecture
Read the [Architecture Deep Dive](ARCHITECTURE.md) to understand:
- Event flow and processing
- Circuit breaker patterns
- Security architecture
- Performance optimizations

### 2. Write Tests
See the [Testing Guide](TESTING_GUIDE.md) for:
- Unit testing strategies
- Integration testing with Testcontainers
- End-to-end testing examples

### 3. Production Deployment
- Configure production database with connection pooling
- Set up Kafka cluster with replication
- Enable Prometheus metrics
- Configure log aggregation (ELK, Splunk)
- Set up alerting for circuit breaker states

### 4. Advanced Features
- Implement custom filter expressions (JSONPath)
- Configure IP whitelisting for domains
- Set up rate limiting per domain
- Implement custom retry strategies

### 5. Monitoring & Observability
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# HTTP client metrics
curl http://localhost:8080/actuator/metrics/http.client.requests
```

---

**Need Help?**
- 📖 [Architecture Documentation](ARCHITECTURE.md)
- 🧪 [Testing Guide](TESTING_GUIDE.md)
- 🐛 [Report Issues](https://github.com/firefly-oss/fireflyframework-callbacks/issues)
- 💬 [Discussions](https://github.com/firefly-oss/fireflyframework-callbacks/discussions)

---

**© 2025 Firefly Software Foundation. All rights reserved.**

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

**Happy Coding! 🚀**

