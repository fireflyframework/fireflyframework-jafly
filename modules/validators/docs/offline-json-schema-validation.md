# Offline JSON Schema validation

`OfflineJsonSchemaValidator` validates JSON Schema documents and JSON instances through a vendor-neutral string API. It belongs to `fireflyframework-validators`; applications retain their business schema, authorization and lifecycle rules. The implementation uses NetworkNT 3.0.8 and Jackson 3 internally. Public results expose only Java records and strings, so a Jackson 2 consumer can supply exact JSON without translating vendor nodes.

```java
var validator = new OfflineJsonSchemaValidator();
var schemaResult = validator.validateSchema(schemaJson);
if (schemaResult.valid()) {
    var instanceResult = validator.validateInstance(schemaJson, instanceJson);
}
```

`SchemaValidationResult` contains `valid` and an immutable list of `SchemaValidationIssue(code, instanceLocation, message)`. Codes distinguish `UNSUPPORTED_SCHEMA` (outside the closed profile), `INVALID_SCHEMA` (malformed JSON, invalid schema constraints or unresolved references), `INVALID_INSTANCE` (malformed or excessive input), and `INSTANCE_CONSTRAINT` (well-formed input that violates the schema). Application-facing messages omit schema fragments and instance values. Instance locations can contain caller-controlled object property names: do not log them without an application's data-handling policy.

## Supported profile

The schema must be an object with explicit `$schema: "https://json-schema.org/draft/2020-12/schema"`. Boolean schemas are unsupported at the document root. Nested boolean schemas remain valid according to Draft 2020-12. The official bundled metaschema validates keyword structure; the engine also compiles validators to resolve local references. Unknown application keywords remain annotations, as permitted by JSON Schema.

Only document-local JSON Pointer `$ref` fragments are supported, including correctly escaped and URI-encoded pointers. The reference/containment graph must be acyclic. Unresolved pointers fail validation. References must target recognized Draft 2020-12 schema positions: the root, `$defs`/`properties`/`patternProperties`/`dependentSchemas` entries, schema-valued keywords or schema-array entries. Executing literal `const`/`enum` or unknown annotation objects as referenced schemas is unsupported. URI decoding normalizes only executable schema references on a private compilation copy; literal constraint data is preserved.

External URI references, `$id`, `$anchor`, `$dynamicAnchor`, `$dynamicRef`, custom `$vocabulary`, and another declared dialect are unsupported. The profile checks every JSON object conservatively, including annotation and `const` data objects: a `$ref` or `$id` there is subject to the same restriction.

The loader permits only a fixed list of bundled Draft 2020-12 metaschema resources. Remote retrieval is disabled. Caller-supplied file, classpath, HTTP and HTTPS schema references are rejected before loading, even when the URI points to an official metaschema. No external schema registry is consulted.

Format assertions are enabled, including actual calendar-date validation. Numbers are parsed as arbitrary-precision integers/decimals; the qualification cases include integers above 2^53 and exact 18-place `multipleOf` constraints. Regex evaluation uses the upstream default Java regex implementation; this is not a claim of complete ECMA-262 regex compatibility.

## Limits

`new OfflineJsonSchemaValidator(Limits.defaults())` applies:

| Limit | Default |
| --- | ---: |
| Schema UTF-8 bytes | 65,536 |
| Instance UTF-8 bytes | 1,048,576 |
| JSON nesting depth | 32 |
| String length | 65,536 |
| Number token length | 128 |
| Nodes in each document | 5,000 |
| Schema references | 128 |
| Returned constraint issues | 20 |

A custom immutable `Limits(schemaBytes, instanceBytes, depth, stringLength, numberLength, nodes, references)` must stay within constructor bounds. Duplicate fields, trailing JSON documents, excess UTF-8 bytes, depth, strings, number tokens, nodes and references are rejected. These limits and cycle rejection bound parsing and reference structure. They do not impose a hard CPU deadline or prove cheap evaluation for every schema: combinations and regex patterns can be expensive. Applications must govern who may author schemas and control execution resources for their workload.

## Qualification

Run the affected module with Java 25: `mvn -pl modules/validators -am verify`. The schema tests exercise bundled metaschema errors, instance constraints, offline rejection with a local listening socket, recursion, escaped/encoded pointers, precision, dates, duplicate/trailing inputs and configured limits. These checks qualify this mechanism; they do not establish any application's publishing authority, domain-specific schema fitness or document-generation acceptance.

The upstream API and bundled metaschema behavior are described in the [NetworkNT 3.0.8 documentation](https://github.com/networknt/json-schema-validator/tree/3.0.8). The wrapper deliberately narrows that library's supported profile.
