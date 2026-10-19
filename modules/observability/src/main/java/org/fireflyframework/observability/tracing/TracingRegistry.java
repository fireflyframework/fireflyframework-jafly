package org.fireflyframework.observability.tracing;

import io.micrometer.observation.ObservationRegistry;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide {@link ObservationRegistry} installed when tracing starts.
 * Kept free of WebClient types so imperative services can load observability
 * without reactor-netty or WebFlux.
 */
public final class TracingRegistry {

    private static final AtomicReference<ObservationRegistry> REGISTRY = new AtomicReference<>();

    private TracingRegistry() {
    }

    public static void install(ObservationRegistry registry) {
        if (registry != null && !registry.isNoop()) {
            REGISTRY.set(registry);
        }
    }

    public static ObservationRegistry registry() {
        return REGISTRY.get();
    }

    static void reset() {
        REGISTRY.set(null);
    }
}
