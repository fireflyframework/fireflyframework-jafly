package org.fireflyframework.web.openapi;

import static org.assertj.core.api.Assertions.*;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

class InstantScalarSchemaGraphTest {
    static final String REF = InstantScalarOpenApiCustomizer.SCHEMA_REF;

    record Site(String name, Function<Schema<?>, OpenAPI> document) {}

    static OpenAPI schema(Schema<?> schema) {
        return new OpenAPI()
                .openapi("3.1.0")
                .components(new Components().addSchemas("OwnerDto", schema));
    }

    static ApiResponse response(Schema<?> schema) {
        return new ApiResponse()
                .description("metadata")
                .content(
                        new Content()
                                .addMediaType("application/json", new MediaType().schema(schema)));
    }

    static PathItem pathResponse(Schema<?> schema) {
        return new PathItem()
                .get(
                        new Operation()
                                .responses(
                                        new ApiResponses()
                                                .addApiResponse("200", response(schema))));
    }

    static PathItem pathParameter(Schema<?> schema) {
        return new PathItem()
                .addParametersItem(new Parameter().name("at").in("query").schema(schema));
    }

    static Map<String, Schema> schemas(Schema<?> child) {
        return Map.of("child", child);
    }

    static Stream<Arguments> sites() {
        List<Site> sites =
                List.of(
                        new Site(
                                "path parameter schema",
                                m ->
                                        new OpenAPI()
                                                .paths(
                                                        new Paths()
                                                                .addPathItem(
                                                                        "/metadata",
                                                                        pathParameter(m)))),
                        new Site(
                                "path parameter content",
                                m ->
                                        new OpenAPI()
                                                .paths(
                                                        new Paths()
                                                                .addPathItem(
                                                                        "/metadata",
                                                                        new PathItem()
                                                                                .addParametersItem(
                                                                                        new Parameter()
                                                                                                .name(
                                                                                                        "at")
                                                                                                .in(
                                                                                                        "query")
                                                                                                .content(
                                                                                                        new Content()
                                                                                                                .addMediaType(
                                                                                                                        "application/json",
                                                                                                                        new MediaType()
                                                                                                                                .schema(
                                                                                                                                        m))))))),
                        new Site(
                                "webhook response",
                                m -> new OpenAPI().webhooks(Map.of("metadata", pathResponse(m)))),
                        new Site(
                                "component path item",
                                m ->
                                        new OpenAPI()
                                                .components(
                                                        new Components()
                                                                .pathItems(
                                                                        Map.of(
                                                                                "metadata",
                                                                                pathParameter(
                                                                                        m))))),
                        new Site(
                                "encoding header schema",
                                m ->
                                        new OpenAPI()
                                                .paths(
                                                        new Paths()
                                                                .addPathItem(
                                                                        "/metadata",
                                                                        pathResponseWithEncoding(
                                                                                new Header()
                                                                                        .schema(
                                                                                                m))))),
                        new Site(
                                "encoding header content",
                                m ->
                                        new OpenAPI()
                                                .paths(
                                                        new Paths()
                                                                .addPathItem(
                                                                        "/metadata",
                                                                        pathResponseWithEncoding(
                                                                                new Header()
                                                                                        .content(
                                                                                                new Content()
                                                                                                        .addMediaType(
                                                                                                                "application/json",
                                                                                                                new MediaType()
                                                                                                                        .schema(
                                                                                                                                m))))))),
                        new Site(
                                "schema properties",
                                m -> schema(new ObjectSchema().addProperty("at", m))),
                        new Site("schema items", m -> schema(new ArraySchema().items(m))),
                        new Site(
                                "schema additionalProperties",
                                m -> schema(new ObjectSchema().additionalProperties(m))),
                        new Site("schema allOf", m -> schema(new ComposedSchema().addAllOfItem(m))),
                        new Site("schema anyOf", m -> schema(new ComposedSchema().addAnyOfItem(m))),
                        new Site("schema oneOf", m -> schema(new ComposedSchema().addOneOfItem(m))),
                        new Site(
                                "schema dynamicRef",
                                m -> schema(new Schema<>().$dynamicRef(m.get$ref()))),
                        new Site("schema not", m -> schema(new Schema<>().not(m))),
                        new Site("schema contains", m -> schema(new Schema<>().contains(m))),
                        new Site(
                                "schema patternProperties",
                                m -> schema(new Schema<>().patternProperties(schemas(m)))),
                        new Site(
                                "schema prefixItems",
                                m -> schema(new Schema<>().prefixItems(List.of(m)))),
                        new Site(
                                "schema contentSchema",
                                m -> schema(new Schema<>().contentSchema(m))),
                        new Site(
                                "schema propertyNames",
                                m -> schema(new Schema<>().propertyNames(m))),
                        new Site(
                                "schema unevaluatedProperties",
                                m -> schema(new Schema<>().unevaluatedProperties(m))),
                        new Site(
                                "schema additionalItems",
                                m -> schema(new Schema<>().additionalItems(m))),
                        new Site(
                                "schema unevaluatedItems",
                                m -> schema(new Schema<>().unevaluatedItems(m))),
                        new Site("schema if", m -> schema(new Schema<>()._if(m))),
                        new Site(
                                "schema then",
                                m -> {
                                    var s = new Schema<>();
                                    s.setThen(m);
                                    return schema(s);
                                }),
                        new Site("schema else", m -> schema(new Schema<>()._else(m))),
                        new Site(
                                "schema dependentSchemas",
                                m -> schema(new Schema<>().dependentSchemas(schemas(m)))));
        return sites.stream().map(site -> Arguments.of(site.name(), site.document()));
    }

    static PathItem pathResponseWithEncoding(Header header) {
        var media =
                new MediaType()
                        .encoding(Map.of("metadata", new Encoding().headers(Map.of("At", header))));
        return new PathItem()
                .get(
                        new Operation()
                                .responses(
                                        new ApiResponses()
                                                .addApiResponse(
                                                        "200",
                                                        new ApiResponse()
                                                                .description("metadata")
                                                                .content(
                                                                        new Content()
                                                                                .addMediaType(
                                                                                        "multipart/mixed",
                                                                                        media)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    void everySupportedSchemaLocationRegistersThePrimitive(
            String name, Function<Schema<?>, OpenAPI> site) {
        var api = site.apply(new Schema<>().$ref(REF));
        new InstantScalarOpenApiCustomizer().customise(api);
        assertThat(api.getComponents()).as(name).isNotNull();
        assertThat(api.getComponents().getSchemas()).as(name).containsKey("FireflyInstant");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    void everySupportedSchemaLocationEnforcesTheEnabledReservedContract(
            String name, Function<Schema<?>, OpenAPI> site) {
        var api = site.apply(new Schema<>().$ref(REF));
        if (api.getComponents() == null) api.setComponents(new Components());
        api.getComponents().addSchemas("FireflyInstant", new ObjectSchema());
        assertThatThrownBy(() -> new InstantScalarOpenApiCustomizer().customise(api))
                .as(name)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OPENAPI_INSTANT_SCHEMA_CONFLICT");
    }

    @Test
    void schema31CyclesTerminateWithoutTreatingBackingMetadataAsSchemaLocations() {
        var parent = new Schema<>();
        parent.setContains(parent);
        parent.setPrefixItems(List.of(parent));
        parent.setJsonSchema(Map.of("$ref", REF));
        parent.setJsonSchemaImpl(Map.of("$ref", REF));
        var api = schema(parent);
        new InstantScalarOpenApiCustomizer().customise(api);
        assertThat(api.getComponents().getSchemas()).doesNotContainKey("FireflyInstant");
        parent.setPropertyNames(new Schema<>().$ref(REF));
        new InstantScalarOpenApiCustomizer().customise(api);
        assertThat(api.getComponents().getSchemas()).containsKey("FireflyInstant");
    }

    @Test
    void theScalarEmitsStringDateTimeUnderBothSwaggerSerializers() {
        var api = schema(new Schema<>().$ref(REF));
        new InstantScalarOpenApiCustomizer().customise(api);
        var scalar = api.getComponents().getSchemas().get("FireflyInstant");
        for (var mapper : List.of(Json.mapper(), Json31.mapper())) {
            com.fasterxml.jackson.databind.JsonNode tree = mapper.valueToTree(scalar);
            assertThat(tree.path("type").asText()).isEqualTo("string");
            assertThat(tree.path("format").asText()).isEqualTo("date-time");
        }
    }
}
