package org.fireflyframework.observability.autoconfigure;

import org.fireflyframework.observability.FireflyObservabilityEnvironmentPostProcessorImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class IndexedAutoConfigurationExclusionsTest {
    private static final String KEY = "spring.autoconfigure.exclude";
    private static final List<String> DATABASE = List.of(
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration",
            "org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration");
    private static final String BRAVE =
            "org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration";

    private MockEnvironment indexedEnvironment(boolean lowerScalar) {
        var environment = new MockEnvironment();
        if (lowerScalar) {
            environment.getPropertySources().addLast(new MapPropertySource("frameworkWebDefaults",
                    Map.of(KEY, "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")));
        }
        var yaml = new YamlPropertiesFactoryBean();
        String document = "spring:\n  autoconfigure:\n    exclude:\n"
                + DATABASE.stream().map(value -> "      - " + value + "\n")
                .collect(java.util.stream.Collectors.joining());
        yaml.setResources(new ByteArrayResource(document.getBytes(StandardCharsets.UTF_8)));
        environment.getPropertySources().addFirst(new PropertiesPropertySource("indexedProfile",
                Objects.requireNonNull(yaml.getObject())));
        return environment;
    }

    private List<String> bound(MockEnvironment environment) {
        return Binder.get(environment).bind(KEY, Bindable.listOf(String.class)).orElseThrow(IllegalStateException::new);
    }

    @Test
    void yamlIndexedExclusionsSurviveTracingBridgeConfiguration() {
        var environment = indexedEnvironment(false);
        assertThat(bound(environment)).containsExactlyElementsOf(DATABASE);
        new FireflyObservabilityEnvironmentPostProcessorImpl()
                .postProcessEnvironment(environment, new SpringApplication());
        assertThat(bound(environment)).containsAll(DATABASE).contains(BRAVE);
    }

    @Test
    void higherPriorityIndexedListWinsOverLowerScalarDefault() {
        var environment = indexedEnvironment(true);
        assertThat(bound(environment)).containsExactlyElementsOf(DATABASE);
        new FireflyObservabilityEnvironmentPostProcessorImpl()
                .postProcessEnvironment(environment, new SpringApplication());
        assertThat(bound(environment)).containsAll(DATABASE).contains(BRAVE)
                .doesNotContain("org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration");
    }

    @Test
    void existingCommaSeparatedExclusionsRemainInOrder() {
        var environment = new MockEnvironment().withProperty(KEY, String.join(",", DATABASE));
        new FireflyObservabilityEnvironmentPostProcessorImpl()
                .postProcessEnvironment(environment, new SpringApplication());
        assertThat(bound(environment)).containsExactly(DATABASE.get(0), DATABASE.get(1),
                DATABASE.get(2), DATABASE.get(3), BRAVE);
    }

    @Test
    void disabledTracingDoesNotRewriteTheIndexedList() {
        var environment = indexedEnvironment(true)
                .withProperty("firefly.observability.tracing.enabled", "false");
        new FireflyObservabilityEnvironmentPostProcessorImpl()
                .postProcessEnvironment(environment, new SpringApplication());
        assertThat(bound(environment)).containsExactlyElementsOf(DATABASE);
    }
}
