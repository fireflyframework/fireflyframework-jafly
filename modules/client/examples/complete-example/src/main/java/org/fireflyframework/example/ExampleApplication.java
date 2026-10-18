package org.fireflyframework.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Complete example application demonstrating all features of Firefly Common Client Library.
 *
 * This application demonstrates:
 * - REST client usage
 * - gRPC client usage (if available)
 * - SOAP client usage
 * - GraphQL client helper
 * - OAuth2 client helper
 * - Multipart upload helper
 * - WebSocket client helper
 * - Security features (certificate pinning, API key management, JWT validation)
 * - Observability features (metrics, health checks, logging)
 * - Circuit breaker and retry patterns
 *
 * <p>The service client infrastructure is wired automatically by
 * {@code org.fireflyframework.client.config.ServiceClientAutoConfiguration}, so no
 * additional enabling annotation is required.
 */
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
