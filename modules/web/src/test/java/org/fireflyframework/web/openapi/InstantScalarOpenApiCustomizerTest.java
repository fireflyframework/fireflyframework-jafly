package org.fireflyframework.web.openapi;

import static org.assertj.core.api.Assertions.*;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;

import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.*;

class InstantScalarOpenApiCustomizerTest {
    final String ref = "#/components/schemas/FireflyInstant";

    OpenApiCustomizer customizer() {
        try {
            return (OpenApiCustomizer)
                    Class.forName("org.fireflyframework.web.openapi.InstantScalarOpenApiCustomizer")
                            .getConstructor()
                            .newInstance();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing opt-in instant schema registrar", error);
        }
    }

    Schema<?> marker() {
        return new Schema<>().$ref(ref);
    }

    OpenAPI components(Schema<?> schema) {
        return new OpenAPI().components(new Components().addSchemas("OwnerDto", schema));
    }

    void registered(OpenAPI api) {
        customizer().customise(api);
        com.fasterxml.jackson.databind.JsonNode actual =
                Json.mapper().valueToTree(api.getComponents().getSchemas().get("FireflyInstant"));
        assertThat(actual)
                .isEqualTo(Json.mapper().valueToTree(new StringSchema().format("date-time")));
    }

    @Test
    void defaultConsumerIsByteUnchangedWithoutAnExplicitReference() throws Exception {
        var api =
                components(
                        new ObjectSchema()
                                .addProperty("legacyTime", new StringSchema().format("date-time")));
        String before = Json.pretty(api);
        customizer().customise(api);
        assertThat(Json.pretty(api)).isEqualTo(before);
    }

    @Test
    void anExampleOrDescriptionIsNotAnOptIn() throws Exception {
        var api = components(new StringSchema().description(ref).example(Map.of("$ref", ref)));
        String before = Json.pretty(api);
        customizer().customise(api);
        assertThat(Json.pretty(api)).isEqualTo(before);
    }

    @Test
    void explicitDtoPropertyRegistersTheExactRfc3339Scalar() {
        registered(components(new ObjectSchema().addProperty("recordedAt", marker())));
    }

    @Test
    void arrayAndCompositionReferencesAreVisited() {
        var api = components(new ComposedSchema().addAllOfItem(new ArraySchema().items(marker())));
        registered(api);
    }

    @Test
    void pathResponseSchemaRegistersWithoutExistingComponents() {
        registered(
                new OpenAPI()
                        .paths(
                                new Paths()
                                        .addPathItem(
                                                "/metadata",
                                                new PathItem()
                                                        .get(
                                                                new Operation()
                                                                        .responses(
                                                                                new ApiResponses()
                                                                                        .addApiResponse(
                                                                                                "200",
                                                                                                new ApiResponse()
                                                                                                        .description(
                                                                                                                "metadata")
                                                                                                        .content(
                                                                                                                new Content()
                                                                                                                        .addMediaType(
                                                                                                                                "application/json",
                                                                                                                                new MediaType()
                                                                                                                                        .schema(
                                                                                                                                                marker())))))))));
    }

    @Test
    void requestAndParameterAndReusableComponentSchemasAreVisited() {
        for (var api :
                List.of(
                        new OpenAPI()
                                .paths(
                                        new Paths()
                                                .addPathItem(
                                                        "/metadata",
                                                        new PathItem()
                                                                .post(
                                                                        new Operation()
                                                                                .requestBody(
                                                                                        new RequestBody()
                                                                                                .content(
                                                                                                        new Content()
                                                                                                                .addMediaType(
                                                                                                                        "application/json",
                                                                                                                        new MediaType()
                                                                                                                                .schema(
                                                                                                                                        marker()))))))),
                        new OpenAPI()
                                .components(
                                        new Components()
                                                .addParameters(
                                                        "at",
                                                        new Parameter()
                                                                .name("at")
                                                                .in("query")
                                                                .schema(marker()))))) {
            registered(api);
        }
    }

    @Test
    void anEquivalentExistingScalarIsPreservedWithoutReplacement() {
        var scalar = new StringSchema().format("date-time");
        var api = components(marker());
        api.getComponents().addSchemas("FireflyInstant", scalar);
        registered(api);
        assertThat(api.getComponents().getSchemas().get("FireflyInstant")).isSameAs(scalar);
    }

    @Test
    void aConflictingReservedScalarFailsInsteadOfChangingItsMeaning() {
        var api = components(marker());
        api.getComponents().addSchemas("FireflyInstant", new StringSchema().format("date"));
        assertThatThrownBy(() -> customizer().customise(api))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OPENAPI_INSTANT_SCHEMA_CONFLICT");
    }

    @Test
    void schemaCyclesDoNotRequireGlobalModelRegistryState() {
        var schema = new ObjectSchema();
        schema.addProperty("recursive", schema);
        var api = components(schema);
        customizer().customise(api);
        assertThat(api.getComponents().getSchemas()).doesNotContainKey("FireflyInstant");
        schema.addProperty("at", marker());
        registered(api);
    }

    @Test
    void repeatedCustomizationIsIdempotent() throws Exception {
        var api = components(marker());
        registered(api);
        String once = Json.pretty(api);
        customizer().customise(api);
        assertThat(Json.pretty(api)).isEqualTo(once);
    }
}
