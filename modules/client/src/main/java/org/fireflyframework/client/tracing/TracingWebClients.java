package org.fireflyframework.client.tracing;

import org.fireflyframework.observability.tracing.ObservedClients;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds WebClients that join the current trace. Outbound calls create CLIENT spans
 * and send W3C {@code traceparent} when the observability registry is installed.
 */
public final class TracingWebClients {

    private TracingWebClients() {
    }

    public static WebClient.Builder builder() {
        return ObservedClients.instrument(WebClient.builder());
    }
}
