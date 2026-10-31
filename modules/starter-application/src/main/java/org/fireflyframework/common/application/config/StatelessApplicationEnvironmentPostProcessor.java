package org.fireflyframework.common.application.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Keeps business persistence out of this stateless application role. */
public final class StatelessApplicationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var excludes = new LinkedHashSet<>(Binder.get(environment)
                .bind("spring.autoconfigure.exclude", Bindable.listOf(String.class)).orElse(List.of()));
        excludes.add("org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration");
        excludes.add("org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration");
        excludes.add("org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration");
        excludes.add("org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "fireflyStatelessApplicationEnvironmentPostProcessor", Map.of("spring.autoconfigure.exclude", String.join(",", excludes))));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
