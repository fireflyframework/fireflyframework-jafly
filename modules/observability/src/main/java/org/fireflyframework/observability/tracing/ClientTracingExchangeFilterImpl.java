package org.fireflyframework.observability.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.DefaultClientRequestObservationConvention;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates a CLIENT span and injects W3C {@code traceparent} for a {@code WebClient}
 * that was built without Spring's observation customizer.
 * <p>
 * The filter reads the registry lazily, so a client constructed during early startup
 * still propagates once tracing is installed. When the client already has an active
 * client observation (Spring's {@code observationRegistry} on the builder), the filter
 * does nothing and does not open a second span.
 */
public final class ClientTracingExchangeFilterImpl implements ExchangeFilterFunction {

    public static final ClientTracingExchangeFilterImpl INSTANCE = new ClientTracingExchangeFilterImpl();

    private static final DefaultClientRequestObservationConvention CONVENTION =
            new DefaultClientRequestObservationConvention();

    private ClientTracingExchangeFilterImpl() {
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(reactorContext -> {
            ObservationRegistry registry = TracingRegistry.registry();
            if (registry == null || registry.isNoop() || alreadyObserved(registry, reactorContext)) {
                return next.exchange(request);
            }

            ClientRequest.Builder mutable = ClientRequest.from(request);
            ClientRequestObservationContext context = new ClientRequestObservationContext(mutable);
            Observation observation = Observation.createNotStarted(CONVENTION, () -> context, registry).start();
            AtomicBoolean stopped = new AtomicBoolean();
            try {
                ClientRequest traced = mutable.build();
                context.setRequest(traced);
                return next.exchange(traced)
                        .map(response -> {
                            context.setResponse(response);
                            stop(observation, stopped);
                            return response;
                        })
                        .doOnError(error -> {
                            observation.error(error);
                            stop(observation, stopped);
                        })
                        .doOnCancel(() -> stop(observation, stopped))
                        .contextWrite(current -> current.put(
                                ObservationThreadLocalAccessor.KEY, observation));
            } catch (RuntimeException ex) {
                observation.error(ex);
                stop(observation, stopped);
                throw ex;
            }
        });
    }

    private static void stop(Observation observation, AtomicBoolean stopped) {
        if (stopped.compareAndSet(false, true)) {
            observation.stop();
        }
    }

    private static boolean alreadyObserved(ObservationRegistry registry, ContextView reactorContext) {
        Observation current = registry.getCurrentObservation();
        if (current == null) {
            current = reactorContext.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
        }
        return current != null && current.getContext() instanceof ClientRequestObservationContext;
    }

}
