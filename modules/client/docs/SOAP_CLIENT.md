# SOAP Client Guide

Complete guide for using the SOAP client to communicate with SOAP/WSDL services.

## Table of Contents

- [Quick Start](#quick-start)
- [Creating a SOAP Client](#creating-a-soap-client)
- [Configuration Options](#configuration-options)
- [Invoking Operations](#invoking-operations)
- [Authentication](#authentication)
- [Advanced Features](#advanced-features)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

```java
import org.fireflyframework.client.SoapClient;
import org.fireflyframework.client.ServiceClient;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {
    
    private final SoapClient weatherClient;
    
    public WeatherService() {
        this.weatherClient = ServiceClient.soap("weather-service")
            .wsdlUrl("http://www.webservicex.net/globalweather.asmx?WSDL")
            .build();
    }
    
    public Mono<WeatherResponse> getWeather(String city, String country) {
        WeatherRequest request = new WeatherRequest();
        request.setCityName(city);
        request.setCountryName(country);
        
        return weatherClient.invokeAsync("GetWeather", request, WeatherResponse.class);
    }
}
```

---

## Creating a SOAP Client

### Basic Configuration

```java
SoapClient client = ServiceClient.soap("my-service")
    .wsdlUrl("http://example.com/service?WSDL")      // Required
    .build();
```

### With Authentication

```java
SoapClient client = ServiceClient.soap("secure-service")
    .wsdlUrl("https://secure.example.com/service?WSDL")
    .credentials("username", "password")              // WS-Security
    .build();
```

### WSDL URL with Embedded Credentials

Many SOAP services (like Equifax, PayNet) include credentials in the WSDL URL:

```java
SoapClient client = ServiceClient.soap("equifax-spain")
    .wsdlUrl("https://uat2.equifax.es/icflex/api?WSDL&user=myuser&password=mypass")
    .build();
// Credentials are automatically extracted and used for WS-Security
```

### Full Configuration

```java
SoapClient client = ServiceClient.soap("payment-service")
    .wsdlUrl("https://payment.example.com/service?WSDL")
    .credentials("api-user", "secret-password")
    .timeout(Duration.ofSeconds(60))
    .enableMtom()                                     // For large binary transfers
    .enableSchemaValidation()                         // Validate XML against schema
    .header("X-API-Key", "your-api-key")             // Custom HTTP header
    .header("X-Client-Version", "1.0.0")
    .build();
```

---

## Configuration Options

### Required Options

| Method | Description | Example |
|--------|-------------|---------|
| `wsdlUrl(String)` | WSDL URL (can include credentials) | `.wsdlUrl("http://example.com/service?WSDL")` |

### Optional Options

| Method | Description | Default | Example |
|--------|-------------|---------|---------|
| `credentials(String, String)` | WS-Security username/password | None | `.credentials("user", "pass")` |
| `username(String)` | WS-Security username | None | `.username("api-user")` |
| `password(String)` | WS-Security password | None | `.password("secret")` |
| `timeout(Duration)` | Request timeout | 30s | `.timeout(Duration.ofSeconds(60))` |
| `enableMtom()` | Enable MTOM for attachments | false | `.enableMtom()` |
| `enableSchemaValidation()` | Validate XML against schema | true | `.enableSchemaValidation()` |
| `disableSchemaValidation()` | Disable schema validation | - | `.disableSchemaValidation()` |
| `header(String, String)` | Add custom HTTP header | None | `.header("X-API-Key", "key")` |
| `property(String, Object)` | Set JAX-WS property | None | `.property("key", "value")` |
| `serviceName(QName)` | Override service QName | Auto-detected | `.serviceName(qname)` |
| `portName(QName)` | Override port QName | Auto-detected | `.portName(qname)` |
| `endpointAddress(String)` | Override endpoint URL | From WSDL | `.endpointAddress("https://prod.example.com")` |

### SSL/TLS Options

| Method | Description | Example |
|--------|-------------|---------|
| `trustStore(String, String)` | SSL trust store path and password | `.trustStore("/path/to/truststore.jks", "password")` |
| `keyStore(String, String)` | SSL key store path and password | `.keyStore("/path/to/keystore.jks", "password")` |
| `disableSslVerification()` | Disable SSL verification (dev only!) | `.disableSslVerification()` |

---

## Invoking Operations

### Method 1: invokeAsync() with Request Object

```java
public Mono<PaymentResponse> processPayment(PaymentRequest request) {
    return soapClient.invokeAsync("ProcessPayment", request, PaymentResponse.class)
        .doOnSuccess(response -> log.info("Payment processed: {}", response.getTransactionId()))
        .doOnError(error -> log.error("Payment failed", error));
}
```

### Method 2: Fluent invoke() Builder

```java
public Mono<WeatherResponse> getWeather(String city, String country) {
    return soapClient.invoke("GetWeather")
        .withParameter("cityName", city)
        .withParameter("countryName", country)
        .withTimeout(Duration.ofSeconds(30))
        .execute(WeatherResponse.class);
}
```

### Method 3: Direct Port Access (Advanced)

```java
// Get the JAX-WS port for advanced operations
WeatherServicePort port = soapClient.getPort(WeatherServicePort.class);
WeatherResponse response = port.getWeather(city, country);
```

### Discovering Available Operations

```java
// Get list of all operations from WSDL
List<String> operations = soapClient.getOperations();
operations.forEach(op -> log.info("Available operation: {}", op));
// Output: GetWeather, GetCitiesByCountry, etc.
```

---

## Authentication

### WS-Security Username/Password

```java
SoapClient client = ServiceClient.soap("secure-service")
    .wsdlUrl("https://secure.example.com/service?WSDL")
    .credentials("api-user", "secret-password")
    .build();
```

### Credentials in WSDL URL

```java
// Common pattern for services like Equifax, PayNet
SoapClient client = ServiceClient.soap("equifax")
    .wsdlUrl("https://api.equifax.com/service?WSDL&user=myuser&password=mypass")
    .build();
// Credentials are automatically extracted and used
```

### Custom HTTP Headers (API Keys)

```java
SoapClient client = ServiceClient.soap("api-service")
    .wsdlUrl("https://api.example.com/service?WSDL")
    .header("X-API-Key", "your-api-key-here")
    .header("Authorization", "Bearer your-token")
    .build();
```

### Basic Authentication

```java
String credentials = "username:password";
String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

SoapClient client = ServiceClient.soap("basic-auth-service")
    .wsdlUrl("https://api.example.com/service?WSDL")
    .header("Authorization", "Basic " + encodedCredentials)
    .build();
```

---

## Advanced Features

### MTOM/XOP for Large Binary Data

```java
// Enable MTOM for efficient binary transfer
SoapClient client = ServiceClient.soap("document-service")
    .wsdlUrl("https://docs.example.com/service?WSDL")
    .enableMtom()
    .build();

// Send document with attachment
DocumentRequest request = new DocumentRequest();
request.setFileName("report.pdf");
request.setContent(pdfBytes);  // Large binary data

Mono<DocumentResponse> response = client.invokeAsync("UploadDocument", request, DocumentResponse.class);
```

### Custom Namespaces and Service Names

```java
import javax.xml.namespace.QName;

QName serviceName = new QName("http://example.com/services", "PaymentService");
QName portName = new QName("http://example.com/services", "PaymentServicePort");

SoapClient client = ServiceClient.soap("payment-service")
    .wsdlUrl("https://payment.example.com/service?WSDL")
    .serviceName(serviceName)
    .portName(portName)
    .build();
```

### Override Endpoint Address

```java
// Use different endpoint than what's in WSDL (e.g., for testing)
SoapClient client = ServiceClient.soap("payment-service")
    .wsdlUrl("https://payment.example.com/service?WSDL")  // WSDL from prod
    .endpointAddress("https://uat.payment.example.com/service")  // But call UAT
    .build();
```

### SSL/TLS with Custom Trust Store

```java
SoapClient client = ServiceClient.soap("banking-service")
    .wsdlUrl("https://secure-bank.example.com/service?WSDL")
    .credentials("api-user", "password")
    .trustStore("/path/to/truststore.jks", "truststore-password")
    .keyStore("/path/to/keystore.jks", "keystore-password")
    .build();
```

### Development: Disable SSL Verification

```java
// ⚠️ ONLY FOR DEVELOPMENT/TESTING - NEVER IN PRODUCTION!
SoapClient client = ServiceClient.soap("dev-service")
    .wsdlUrl("https://dev.example.com/service?WSDL")
    .disableSslVerification()
    .build();
```

### Custom JAX-WS Properties

```java
SoapClient client = ServiceClient.soap("custom-service")
    .wsdlUrl("https://api.example.com/service?WSDL")
    .property("javax.xml.ws.client.connectionTimeout", "30000")
    .property("javax.xml.ws.client.receiveTimeout", "60000")
    .property("com.sun.xml.ws.request.timeout", "30000")
    .build();
```

### Health Checks

```java
// Check if WSDL is accessible and service is ready
boolean ready = soapClient.isReady();

// Perform comprehensive health check
Mono<Void> healthCheck = soapClient.healthCheck()
    .doOnSuccess(v -> log.info("SOAP service is healthy"))
    .doOnError(e -> log.error("SOAP service health check failed", e));

// Schedule periodic health checks
@Scheduled(fixedRate = 60000)  // Every minute
public void checkHealth() {
    soapClient.healthCheck()
        .subscribe(
            v -> log.debug("Health check passed"),
            e -> log.error("Health check failed", e)
        );
}
```

### Error Handling

```java
import org.fireflyframework.client.exception.*;

public Mono<PaymentResponse> processPayment(PaymentRequest request) {
    return soapClient.invokeAsync("ProcessPayment", request, PaymentResponse.class)
        .onErrorMap(ServiceNotFoundException.class,
            ex -> new PaymentServiceException("Payment service not found"))
        .onErrorMap(ServiceUnavailableException.class,
            ex -> new PaymentServiceException("Payment service unavailable"))
        .onErrorMap(ServiceAuthenticationException.class,
            ex -> new PaymentAuthException("Authentication failed"))
        .retry(3)
        .timeout(Duration.ofSeconds(60));
}
```

### Lifecycle Management

```java
// Get service information
String serviceName = soapClient.getServiceName();
String wsdlUrl = soapClient.getWsdlUrl();
QName serviceQName = soapClient.getServiceQName();
QName portQName = soapClient.getPortQName();
ClientType type = soapClient.getClientType();  // Returns ClientType.SOAP

// Shutdown client (releases resources)
soapClient.shutdown();
```

---

## Best Practices

### 1. Use Specific Types

```java
// ✅ GOOD - Type-safe
private final SoapClient equifaxClient;

// ❌ BAD - Requires casting
private final ServiceClient equifaxClient;
```

### 2. Configure Once, Reuse

```java
@Configuration
public class SoapClientConfig {
    
    @Bean
    public SoapClient equifaxClient(
            @Value("${equifax.wsdl.url}") String wsdlUrl,
            @Value("${equifax.username}") String username,
            @Value("${equifax.password}") String password) {
        
        return ServiceClient.soap("equifax-spain")
            .wsdlUrl(wsdlUrl)
            .credentials(username, password)
            .timeout(Duration.ofSeconds(60))
            .defaultHeader("dptOrchestrationCode", "equifax-comm360")
            .build();
    }
}
```

### 3. Externalize Configuration

```yaml
# application.yml
equifax:
  wsdl:
    url: https://uat2.equifax.es/icflex/api?WSDL
  username: ${EQUIFAX_USERNAME}
  password: ${EQUIFAX_PASSWORD}
  timeout: 60s
```

### 4. Handle SOAP Faults

```java
public Mono<Response> callService(Request request) {
    return soapClient.invokeAsync("Operation", request, Response.class)
        .onErrorMap(SOAPFaultException.class, ex -> {
            String faultCode = ex.getFault().getFaultCode();
            String faultString = ex.getFault().getFaultString();
            log.error("SOAP Fault: {} - {}", faultCode, faultString);
            return new ServiceException("SOAP Fault: " + faultString);
        });
}
```

### 5. Use MTOM for Large Files

```java
// ✅ GOOD - MTOM for large binary data
SoapClient client = ServiceClient.soap("document-service")
    .wsdlUrl("https://docs.example.com/service?WSDL")
    .enableMtom()  // Efficient binary transfer
    .build();

// ❌ BAD - Base64 encoding without MTOM (inefficient)
SoapClient client = ServiceClient.soap("document-service")
    .wsdlUrl("https://docs.example.com/service?WSDL")
    .build();
```

---

## Troubleshooting

### WSDL Not Found

**Problem**: Cannot access WSDL URL

**Solution**:
- Verify WSDL URL is correct and accessible
- Check network connectivity
- Verify authentication if required
- Try accessing WSDL in browser

### Authentication Failures

**Problem**: WS-Security authentication fails

**Solution**:
```java
// Ensure credentials are correct
SoapClient client = ServiceClient.soap("service")
    .wsdlUrl("https://api.example.com/service?WSDL")
    .credentials("correct-username", "correct-password")
    .build();

// Or use credentials in URL
SoapClient client = ServiceClient.soap("service")
    .wsdlUrl("https://api.example.com/service?WSDL&user=username&password=pass")
    .build();
```

### SSL/TLS Errors

**Problem**: SSL handshake failures, certificate errors

**Solution**:
```java
// For production: Use proper trust store
SoapClient client = ServiceClient.soap("service")
    .wsdlUrl("https://secure.example.com/service?WSDL")
    .trustStore("/path/to/truststore.jks", "password")
    .build();

// For development only: Disable verification
SoapClient client = ServiceClient.soap("service")
    .wsdlUrl("https://dev.example.com/service?WSDL")
    .disableSslVerification()  // ⚠️ DEV ONLY!
    .build();
```

### Timeout Errors

**Problem**: Requests timing out

**Solution**:
```java
// Increase timeout
SoapClient client = ServiceClient.soap("slow-service")
    .wsdlUrl("https://slow.example.com/service?WSDL")
    .timeout(Duration.ofMinutes(2))  // Increase from default 30s
    .build();
```

### Operation Not Found

**Problem**: Operation name not found in WSDL

**Solution**:
```java
// List available operations
List<String> operations = soapClient.getOperations();
operations.forEach(System.out::println);

// Use exact operation name from WSDL
soapClient.invokeAsync("GetWeather", request, Response.class);  // Case-sensitive!
```

### XML Parsing Errors

**Problem**: Cannot parse SOAP response

**Solution**:
```java
// Disable schema validation if schema is problematic
SoapClient client = ServiceClient.soap("service")
    .wsdlUrl("https://api.example.com/service?WSDL")
    .disableSchemaValidation()
    .build();

// Enable message logging to see raw XML
```

```yaml
# In application.yml
firefly:
  service-client:
    soap:
      message-logging-enabled: true  # See raw SOAP messages
```

---

## What's Included

✅ **WSDL Parsing**: Automatic service discovery  
✅ **WS-Security**: Username/password authentication  
✅ **MTOM/XOP**: Efficient binary attachments  
✅ **SSL/TLS**: Custom trust stores and key stores  
✅ **Custom Headers**: HTTP and SOAP headers  
✅ **Schema Validation**: XML validation against WSDL  
✅ **Circuit Breaker**: Automatic failure detection  
✅ **Health Checks**: WSDL availability monitoring  
✅ **Timeouts**: Configurable request timeouts  
✅ **Reactive**: Non-blocking Mono responses  
✅ **Fluent API**: Easy operation invocation  

## What's NOT Included

❌ **SOAP Server**: This is client-only  
❌ **WSDL Generation**: Use JAX-WS tools  
❌ **WS-Policy**: Not currently supported  
❌ **WS-ReliableMessaging**: Not currently supported  

---

**Next Steps**:

**Core Clients**:
- [REST Client Guide](REST_CLIENT.md)
- [gRPC Client Guide](GRPC_CLIENT.md)

**Helper Utilities**:
- [GraphQL Client Guide](GRAPHQL_CLIENT.md)
- [WebSocket Helper Guide](WEBSOCKET_HELPER.md)
- [OAuth2 Helper Guide](OAUTH2_HELPER.md)
- [File Upload Helper Guide](MULTIPART_HELPER.md)

**Configuration**:
- [Configuration Reference](CONFIGURATION.md)

