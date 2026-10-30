package org.fireflyframework.validators.schema;

import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.resource.SchemaLoader;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline Draft 2020-12 validation with a bounded, acyclic, document-local reference profile.
 * Public contracts deliberately contain neither vendor types nor Jackson nodes.
 */
public final class OfflineJsonSchemaValidator {
    public static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final Set<String> META_RESOURCES = Set.of(DIALECT,
            "https://json-schema.org/draft/2020-12/meta/core", "https://json-schema.org/draft/2020-12/meta/applicator",
            "https://json-schema.org/draft/2020-12/meta/unevaluated", "https://json-schema.org/draft/2020-12/meta/validation",
            "https://json-schema.org/draft/2020-12/meta/meta-data", "https://json-schema.org/draft/2020-12/meta/format-annotation",
            "https://json-schema.org/draft/2020-12/meta/content", "https://json-schema.org/draft/2020-12/meta/format-assertion");

    /** Limits bound input parsing and reference structure, not total validation CPU time. */
    public record Limits(int schemaBytes, int instanceBytes, int depth, int stringLength, int numberLength, int nodes, int references) {
        public Limits {
            if (schemaBytes < 1 || schemaBytes > 1_048_576 || instanceBytes < 1 || instanceBytes > 16_777_216
                    || depth < 1 || depth > 128 || stringLength < 1 || stringLength > 1_048_576
                    || numberLength < 1 || numberLength > 1_024 || nodes < 1 || nodes > 100_000
                    || references < 1 || references > 1_024) {
                throw new IllegalArgumentException("JSON Schema limits are outside the supported range");
            }
        }
        public static Limits defaults() { return new Limits(65_536, 1_048_576, 32, 65_536, 128, 5_000, 128); }
    }

    private final Limits limits;
    private final JsonMapper mapper;
    private final SchemaRegistry registry;
    private final Schema metaSchema;

    public OfflineJsonSchemaValidator() { this(Limits.defaults()); }

    public OfflineJsonSchemaValidator(Limits limits) {
        this.limits = java.util.Objects.requireNonNull(limits);
        var factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(limits.depth())
                        .maxStringLength(limits.stringLength()).maxNumberLength(limits.numberLength()).build()).build();
        mapper = JsonMapper.builder(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
        registry = SchemaRegistry.withDialect(Dialects.getDraft202012(), builder -> builder
                .schemaLoader(SchemaLoader.builder().fetchRemoteResources(false)
                        .allow(iri -> META_RESOURCES.contains(iri.toString())).build()));
        metaSchema = registry.getSchema(SchemaLocation.of(DIALECT));
    }

    public SchemaValidationResult validateSchema(String schemaJson) {
        try {
            compile(schemaJson);
            return valid();
        } catch (ProfileException error) {
            return invalid(error.code, "", error.getMessage());
        } catch (RuntimeException error) {
            return invalid("INVALID_SCHEMA", "", "Schema syntax, constraints or references are invalid");
        }
    }

    public SchemaValidationResult validateInstance(String schemaJson, String instanceJson) {
        final Schema schema;
        try {
            schema = compile(schemaJson);
        } catch (ProfileException error) {
            return invalid(error.code, "", error.getMessage());
        } catch (RuntimeException error) {
            return invalid("INVALID_SCHEMA", "", "Schema syntax, constraints or references are invalid");
        }
        try {
            var instance = parse(instanceJson, limits.instanceBytes());
            countNodes(instance);
            var errors = schema.validate(instance, context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
            if (errors.isEmpty()) return valid();
            // Vendor messages can contain instance values, so only stable application messages leave this boundary.
            return new SchemaValidationResult(false, errors.stream().limit(20).map(error ->
                    new SchemaValidationIssue("INSTANCE_CONSTRAINT", error.getInstanceLocation().toString(),
                            "Instance does not satisfy the schema")).toList());
        } catch (RuntimeException error) {
            return invalid("INVALID_INSTANCE", "", "Instance syntax or input limits are invalid");
        }
    }

    private Schema compile(String json) {
        JsonNode root = parse(json, limits.schemaBytes());
        if (!root.isObject() || !DIALECT.equals(root.path("$schema").asString())) {
            throw unsupported("Only explicit Draft 2020-12 object schemas are supported");
        }
        Map<String, List<String>> graph = new LinkedHashMap<>();
        var schemaLocations = new java.util.HashSet<String>();
        collectSchemaLocations(root, "", schemaLocations);
        collect(root, "", graph, schemaLocations, new int[2]);
        rejectCycles(graph);
        if (!metaSchema.validate(root, context -> context.executionConfig(config -> config.formatAssertionsEnabled(true))).isEmpty()) {
            throw new ProfileException("INVALID_SCHEMA", "Schema does not satisfy the Draft 2020-12 metaschema");
        }
        Schema result = registry.getSchema(normalizeLocalReferences(root.deepCopy(), schemaLocations));
        result.initializeValidators();
        return result;
    }

    private JsonNode normalizeLocalReferences(JsonNode root, Set<String> schemaLocations) {
        for (String location : schemaLocations) {
            JsonNode node = root.at(location);
            if (node.isObject() && node.has("$ref")) {
                var object = (tools.jackson.databind.node.ObjectNode) node;
                object.put("$ref", "#" + URI.create(node.get("$ref").asString()).getFragment());
            }
        }
        return root;
    }

    private void collectSchemaLocations(JsonNode node, String path, Set<String> locations) {
        locations.add(path);
        if (!node.isObject()) return;
        for (var property : node.properties()) {
            String keyword = property.getKey();
            JsonNode value = property.getValue();
            String child = path + "/" + escape(keyword);
            if (Set.of("additionalProperties", "unevaluatedProperties", "propertyNames", "contains", "items",
                    "unevaluatedItems", "not", "if", "then", "else", "contentSchema").contains(keyword)) {
                collectSchemaLocations(value, child, locations);
            } else if (Set.of("$defs", "properties", "patternProperties", "dependentSchemas").contains(keyword) && value.isObject()) {
                for (var entry : value.properties()) collectSchemaLocations(entry.getValue(), child + "/" + escape(entry.getKey()), locations);
            } else if (Set.of("allOf", "anyOf", "oneOf", "prefixItems").contains(keyword) && value.isArray()) {
                for (int index = 0; index < value.size(); index++) collectSchemaLocations(value.get(index), child + "/" + index, locations);
            }
        }
    }

    private static String escape(String key) { return key.replace("~", "~0").replace("/", "~1"); }

    private JsonNode parse(String json, int maxBytes) {
        if (json == null || json.length() > maxBytes || json.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("Input exceeds the byte limit");
        }
        JsonNode result = mapper.readTree(json);
        if (result == null || result.isMissingNode()) throw new IllegalArgumentException("A JSON document is required");
        return result;
    }

    private void countNodes(JsonNode root) {
        var pending = new ArrayDeque<JsonNode>();
        pending.add(root);
        int nodes = 0;
        while (!pending.isEmpty()) {
            JsonNode node = pending.removeFirst();
            if (++nodes > limits.nodes()) throw new IllegalArgumentException("Input exceeds the node limit");
            node.forEach(pending::addLast);
        }
    }

    private void collect(JsonNode root, String path, Map<String, List<String>> graph, Set<String> schemaLocations, int[] counts) {
        JsonNode node = root.at(path);
        if (++counts[0] > limits.nodes()) throw unsupported("Schema exceeds the node limit");
        List<String> edges = new ArrayList<>();
        graph.put(path, edges);
        if (node.isObject()) {
            for (var property : node.properties()) {
                String key = property.getKey();
                JsonNode value = property.getValue();
                if (Set.of("$id", "$dynamicRef", "$dynamicAnchor", "$anchor", "$vocabulary").contains(key)) {
                    throw unsupported("Schema resource identifiers, anchors and custom vocabularies are unsupported");
                }
                if (key.equals("$schema") && !DIALECT.equals(value.asString())) throw unsupported("Schema dialect override is unsupported");
                if (key.equals("$ref")) {
                    if (++counts[1] > limits.references()) throw unsupported("Schema exceeds the reference limit");
                    if (!value.isString() || !value.asString().startsWith("#")) throw unsupported("External schema references are unsupported");
                    String pointer = URI.create(value.asString()).getFragment();
                    if (pointer == null || (!pointer.isEmpty() && !pointer.startsWith("/"))) throw unsupported("Only local JSON pointer references are supported");
                    if (root.at(pointer).isMissingNode()) throw new ProfileException("INVALID_SCHEMA", "Local schema reference is unresolved");
                    if (!schemaLocations.contains(pointer)) throw unsupported("References to literal or annotation data are unsupported");
                    edges.add(pointer);
                }
                String child = path + "/" + escape(key);
                edges.add(child);
                collect(root, child, graph, schemaLocations, counts);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                String child = path + "/" + index;
                edges.add(child);
                collect(root, child, graph, schemaLocations, counts);
            }
        }
    }

    private void rejectCycles(Map<String, List<String>> graph) {
        record Frame(String path, Iterator<String> children) { }
        Map<String, Integer> colors = new HashMap<>();
        var stack = new ArrayDeque<Frame>();
        colors.put("", 1);
        stack.push(new Frame("", graph.get("").iterator()));
        while (!stack.isEmpty()) {
            Frame current = stack.peek();
            if (!current.children().hasNext()) {
                colors.put(current.path(), 2);
                stack.pop();
                continue;
            }
            String next = current.children().next();
            if (colors.getOrDefault(next, 0) == 1) throw unsupported("Recursive schema references are unsupported");
            if (colors.getOrDefault(next, 0) == 0) {
                colors.put(next, 1);
                stack.push(new Frame(next, graph.get(next).iterator()));
            }
        }
    }

    private static SchemaValidationResult valid() { return new SchemaValidationResult(true, List.of()); }
    private static SchemaValidationResult invalid(String code, String path, String message) {
        return new SchemaValidationResult(false, List.of(new SchemaValidationIssue(code, path, message)));
    }
    private static ProfileException unsupported(String message) { return new ProfileException("UNSUPPORTED_SCHEMA", message); }
    private static final class ProfileException extends RuntimeException {
        private final String code;
        private ProfileException(String code, String message) { super(message); this.code = code; }
    }
}
