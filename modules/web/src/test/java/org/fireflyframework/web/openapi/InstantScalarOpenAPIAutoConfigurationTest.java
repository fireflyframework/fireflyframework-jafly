package org.fireflyframework.web.openapi;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InstantScalarOpenAPIAutoConfigurationTest {
    @Test
    void normalConfigurationRegistersOnlyOneStatelessCustomizerWithoutBusinessBeans() {
        new ApplicationContextRunner()
                .withPropertyValues("firefly.openapi.instant-scalar.enabled=true")
                .withConfiguration(
                        AutoConfigurations.of(InstantScalarOpenAPIAutoConfiguration.class))
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .hasSingleBean(InstantScalarOpenApiCustomizerImpl.class)
                                        .doesNotHaveBean(io.swagger.v3.oas.models.OpenAPI.class));
    }

    @Test
    void originalProducerMetaAnnotationImportsTheSameConfiguration() {
        assertThat(EnableOpenApiGen.class.getAnnotation(ImportAutoConfiguration.class).value())
                .contains(InstantScalarOpenAPIAutoConfiguration.class);
    }

    @Test
    void normalAutoConfigurationMetadataIncludesTheSameConfiguration() throws Exception {
        var resources =
                getClass()
                        .getClassLoader()
                        .getResources(
                                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        var webMetadata = new java.util.ArrayList<String>();
        while (resources.hasMoreElements()) {
            try (var stream = resources.nextElement().openStream()) {
                String content =
                        new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (content.contains("org.fireflyframework.web.openapi.OpenAPIAutoConfiguration"))
                    webMetadata.add(content);
            }
        }
        assertThat(webMetadata).isNotEmpty();
        assertThat(webMetadata)
                .anyMatch(
                        content ->
                                content.contains(
                                        "org.fireflyframework.web.openapi.InstantScalarOpenAPIAutoConfiguration"));
    }

    @Test
    void defaultDoesNotTreatAnExistingDomainSchemaAsAnInstantOptIn() throws Exception {
        var api =
                new io.swagger.v3.oas.models.OpenAPI()
                        .components(
                                new io.swagger.v3.oas.models.Components()
                                        .addSchemas(
                                                "FireflyInstant",
                                                new io.swagger.v3.oas.models.media.ObjectSchema()
                                                        .addProperty(
                                                                "domainId",
                                                                new io.swagger.v3.oas.models.media
                                                                        .StringSchema()))
                                        .addSchemas(
                                                "OwnerDto",
                                                new io.swagger.v3.oas.models.media.Schema<>()
                                                        .$ref(
                                                                "#/components/schemas/FireflyInstant")));
        String before = io.swagger.v3.core.util.Json.pretty(api);
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(InstantScalarOpenAPIAutoConfiguration.class))
                .run(
                        context -> {
                            assertThatCode(
                                            () ->
                                                    context.getBeansOfType(
                                                                    org.springdoc.core.customizers
                                                                            .OpenApiCustomizer
                                                                            .class)
                                                            .values()
                                                            .forEach(
                                                                    customizer ->
                                                                            customizer.customise(
                                                                                    api)))
                                    .doesNotThrowAnyException();
                            assertThat(context)
                                    .hasNotFailed()
                                    .doesNotHaveBean(InstantScalarOpenApiCustomizerImpl.class);
                        });
        assertThat(io.swagger.v3.core.util.Json.pretty(api)).isEqualTo(before);
    }

    @Test
    void explicitlyDisabledConfigurationDoesNotCreateARegistrar() {
        new ApplicationContextRunner()
                .withPropertyValues("firefly.openapi.instant-scalar.enabled=false")
                .withConfiguration(
                        AutoConfigurations.of(InstantScalarOpenAPIAutoConfiguration.class))
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(InstantScalarOpenApiCustomizerImpl.class));
    }
}
