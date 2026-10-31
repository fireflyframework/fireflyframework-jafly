package org.fireflyframework.observability.tracing;

import io.micrometer.context.ContextSnapshot;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Hooks;

/**
 * Compatibility configuration retained for existing imports.
 * Native Spring Boot Reactor auto-configuration owns automatic propagation.
 * The environment post-processor translates the legacy hooks flag into a
 * lowest-precedence {@code spring.reactor.context-propagation} default;
 * an application's explicit native {@code auto} or {@code limited} setting wins.
 * Propagation requires the relevant actual Micrometer accessor, rather than
 * implying that arbitrary MDC fields are automatically registered.
 *
 * @deprecated Configure the native Boot Reactor propagation mode instead.
 */
@Deprecated
@AutoConfiguration
@ConditionalOnClass({Hooks.class, ContextSnapshot.class})
@ConditionalOnProperty(prefix = "firefly.observability.context-propagation",
        name = "reactor-hooks-enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveContextPropagationAutoConfiguration {

    /** @deprecated Native Boot owns the hook; retained as a no-op compatibility method. */
    @Deprecated
    void enableAutomaticContextPropagation() {
    }
}
