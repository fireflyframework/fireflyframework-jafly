# Restricted HTML fragments

`RestrictedHtmlTemplateRenderer` is an independent, immutable FreeMarker configuration for published-document HTML fragments. It does not use or change `TemplateRenderUtil`'s global loaders, hooks, shared variables, asynchronous executor or PDF/image behavior. Notification templates retain their own engine.

```java
var renderer = new RestrictedHtmlTemplateRenderer();
var result = renderer.render("<p>${name}</p>", Map.of("name", "Synthetic & Bank"));
if (!result.success()) {
    // Handle the sanitized result.code(); html is always null on failure.
}
```

The API accepts only a deeply copied JSON-compatible model: string-keyed maps, lists, strings, booleans, null, exact BigInteger/BigDecimal and integral Java values. Arbitrary objects, direct FreeMarker TemplateModel instances, dates/DOM objects and Float/Double values are rejected. Numeric plain expansion is limited to128 characters; Unicode must contain valid surrogate pairs. Model strings are data, never additional template source.

The configuration uses SimpleObjectWrapper, blocks API/class construction, disables DOM/Jython wrapping, installs a loader that never resolves anything, and forces HTML auto-escaping. Classpath/file/URL imports/includes are unavailable. Each request gets its own processing environment and copied model. FreeMarker still evaluates ordinary template expressions, conditions, loops and local macros inside these capability restrictions.

After rendering, a secure JDK XML parser checks the well-formed fragment with DTD/entities/XInclude/external DTD/schema access disabled. The closed, lowercase print-document tag profile includes text sections/headings, emphasis, tables and lists. Only class/id token attributes, bounded numeric spans and table scope values are permitted. Scripts/events, author CSS/style tags, links, images/embedded content, XML instructions/comments and unknown elements/attributes reject the entire result. No partial or automatically stripped document is returned. Fragments must contain visible text. A product adapter may add a trusted outer shell, typed branding and restrictive CSP after this validation; arbitrary CSS/assets/PDF require another explicitly qualified profile.

`RestrictedTemplateLimits.defaults()` bounds source256KiB UTF-8, output1MiB UTF-8, model5000nodes/depth32/string65536UTF-16 units,10000 template elements and5000milliseconds of cooperative deadline checks. Numeric values and model nodes have separate guards. Output accounting counts actual UTF-8 bytes, including supplementary characters split across writer calls. XML output is additionally limited to20000nodes and the configured depth. A failure leaves an instance reusable for the next request.

Result codes are `UNSUPPORTED_TEMPLATE`, `UNSUPPORTED_MODEL`, `RENDER_LIMIT`, `EMPTY_OUTPUT`, `INVALID_HTML` and `RENDER_FAILED`. They do not contain model values, source fragments or exception messages. `RestrictedTemplateResult` cannot hold successful content together with an error code, or failed partial content.

The deadline and TemplateProcessingTracer check element-level work; they do not interrupt a single long-running expression, schema validation, native code or a JVM allocation. This utility is not a hard CPU/heap/RSS sandbox. A service accepting published template content must apply the separately qualified bounded worker-process lifecycle when it needs a wall-clock/heap isolation boundary. The governed document producer supplies that adapter; do not treat Reactor timeout alone as cancellation of template execution.

Tests cover actual escaped financial rows and exact decimal/large-integer output, blocked Java/API/loaders, escaping suppression, active HTML/XML entities/styles, output/element budgets, cyclic models/number expansion/Unicode, recovery after failure and isolation from legacy global hooks. Module verification also runs the unchanged legacy utility suite. Process kill/reap, schema validation and document persistence are separate producer gates.
