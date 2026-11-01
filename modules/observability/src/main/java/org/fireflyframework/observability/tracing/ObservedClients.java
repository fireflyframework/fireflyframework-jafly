package org.fireflyframework.observability.tracing;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Applies the process-wide tracing registry to WebClient builders.
 * <p>
 * Spring Boot instruments only the auto-configured {@code WebClient.Builder}. Framework
 * modules call {@code WebClient.builder()} directly, so those calls never became CLIENT
 * spans and never sent {@code traceparent}. Routing them through {@link #instrument}
 * adds a lazy client-tracing filter and, once the registry exists, Spring's own
 * observation hook.
 */
public final class ObservedClients {

    private ObservedClients() {
    }

    /** @see TracingRegistry#install(ObservationRegistry) */
    public static void install(ObservationRegistry registry) {
        TracingRegistry.install(registry);
    }

    public static ObservationRegistry registry() {
        return TracingRegistry.registry();
    }

    static void reset() {
        TracingRegistry.reset();
    }

    public static WebClient.Builder instrument(WebClient.Builder builder) {
        if (builder == null) {
            return null;
        }
        ObservationRegistry registry = TracingRegistry.registry();
        if (registry != null) {
            // Spring's observation hook creates the CLIENT span and injects traceparent.
            // Adding the exchange filter as well opens a second root span.
            builder.observationRegistry(registry);
        } else {
            // Built before tracing started. The filter reads the registry on each call.
            builder.filter(ClientTracingExchangeFilterImpl.INSTANCE);
        }
        return builder;
    }

    public static WebClient instrument(WebClient client) {
        if (client == null) {
            return null;
        }
        return instrument(client.mutate()).build();
    }
}
