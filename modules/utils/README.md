# Firefly Framework - Utils

[![CI](https://github.com/fireflyframework/fireflyframework-utils/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-utils/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Shared utility library for the Firefly Framework — FreeMarker-powered template rendering to XHTML, PDF, and images, plus filtering annotations used across framework modules.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-utils` is a lightweight, dependency-light utility library used across the Firefly Framework ecosystem. It bundles two independent capabilities that show up repeatedly in microservices but do not warrant a module of their own:

1. **Document rendering** — `TemplateRenderUtil` turns [FreeMarker](https://freemarker.apache.org/) templates into well-formed XHTML and, via the [Flying Saucer](https://github.com/flyingsaucerproject/flyingsaucer) renderer backed by OpenPDF, into **PDF documents** and **raster images** (PNG/JPEG). It is the engine behind document-heavy use cases such as invoices, statements, contracts, and reports — with watermarks, encryption, metadata, bookmarks/outlines, custom fonts, template caching, shared variables, pre/post-processing hooks, and asynchronous rendering.
2. **Filtering metadata** — the `@FilterableId` annotation marks ID fields on filter DTOs so they participate in dynamic query filtering and OpenAPI parameter generation with exact-match semantics.

The module sits low in the framework's dependency graph. It depends only on `fireflyframework-kernel` (shared abstractions and exceptions) and brings FreeMarker and Flying Saucer/OpenPDF as its functional dependencies, so it can be added to any service — reactive or servlet, core/domain/experience tier — without pulling in the full framework stack. The `@FilterableId` annotation it defines is consumed at runtime by the filtering utilities in `fireflyframework-web` (`FilterUtils` for query building and `FilterParameterCustomizer` for OpenAPI/Swagger documentation).

`TemplateRenderUtil` is a static-method utility — there is no Spring auto-configuration and no bean to wire. Call it directly from any service, mapper, or scheduled job.

## Features

**Template rendering (`TemplateRenderUtil`)**

- Render FreeMarker templates from the **classpath** (`classpath:/templates`), the **filesystem** (`./templates`), or arbitrary custom `TemplateLoader`s — resolved via a `MultiTemplateLoader` in that order.
- Render either named template **files** (`*.ftl`) or in-memory **template strings**.
- Output to **XHTML string**, **PDF** (`OutputStream`, `File`, or `byte[]`), or **image** (`byte[]`, e.g. PNG/JPEG at a chosen width/height).
- Automatic XHTML normalization — wraps fragments in a valid XHTML document and injects `@page` CSS for size/margins so Flying Saucer always receives well-formed input.
- Rich `PdfOptions` builder: page size (`A4`, `LETTER`, `LEGAL`, `A3`), margins, custom font directories (TTF/OTF embedding), default font, base URI for relative resources.
- **Watermarks** (text, opacity, font size, rotation, color), **encryption** (user/owner passwords + printing/copy/modify permissions), **document metadata** (title, author, subject, keywords, creator), and **bookmarks/outlines** (nested, page-targeted).
- **Asynchronous** variants (`*Async`) returning `CompletableFuture`, backed by a configurable fixed thread pool.
- **Template caching** (toggleable, with configurable max size), **shared variables** available to every template, and **pre/post-processing hooks** (`BiFunction<String, Map, String>`).
- **Template validation** for both inline content and files, returning a list of syntax errors instead of throwing.

**Filtering metadata**

- `@FilterableId` — a runtime-retained field annotation that opts an `Id` field into filtering with exact-match-only semantics (no `LIKE`, no range parameters), and surfaces it as a query parameter in generated OpenAPI documentation.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- No external runtime services. For TTF/OTF font embedding you provide font files; for classpath template loading place templates under `src/main/resources/templates`.

## Installation

Add the dependency. The version is managed by the Firefly BOM / parent POM, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-utils</artifactId>
    <!-- version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

If your project does not inherit the Firefly parent or import the BOM, pin the version explicitly to match the rest of your framework dependencies (for example `26.05.08`).

## Quick Start

### Render a template to HTML

Place `invoice.ftl` under `src/main/resources/templates/`:

```html
<!-- src/main/resources/templates/invoice.ftl -->
<html><body>
  <h1>Invoice for ${customerName}</h1>
  <p>Total: ${total}</p>
</body></html>
```

```java
import org.fireflyframework.utils.template.TemplateRenderUtil;
import java.util.Map;

Map<String, Object> model = Map.of("customerName", "Acme Corp", "total", "1,250.00 EUR");

String html = TemplateRenderUtil.renderTemplateToHtml("invoice.ftl", model);
```

### Render a template straight to PDF bytes

```java
byte[] pdf = TemplateRenderUtil.renderTemplateToPdfBytes("invoice.ftl", model);
// e.g. return as a ResponseEntity<byte[]> with Content-Type: application/pdf
```

### Customize the PDF (watermark, encryption, metadata, bookmarks)

```java
import org.fireflyframework.utils.template.TemplateRenderUtil.PdfOptions;
import org.fireflyframework.utils.template.TemplateRenderUtil.PdfOptions.PageSize;

PdfOptions options = new PdfOptions()
        .withPageSize(PageSize.A4)
        .withMargins(36, 36, 36, 36)
        .withDefaultFont("DejaVu Sans")
        .withFontDirectory("/opt/fonts")              // embeds .ttf/.otf found here
        .withWatermark("CONFIDENTIAL", 0.3f, 60, 45, "#888888")
        .withMetadata("Q1 Invoice", "Billing", "Invoice", "invoice,billing")
        .withEncryption("ownerSecret")                // owner password, printing/copy allowed
        .withBookmark("Summary", "1")
        .withChildBookmark("Line Items", "1");

byte[] pdf = TemplateRenderUtil.renderTemplateToPdfBytes("invoice.ftl", model, options);
```

### Render asynchronously

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<byte[]> future =
        TemplateRenderUtil.renderTemplateToPdfBytesAsync("invoice.ftl", model);
```

### Render an HTML fragment to an image

```java
byte[] png = TemplateRenderUtil.renderHtmlToImage(
        "<h1 style='color:#0a6'>Hello</h1>", 800, 400, "png");
```

### Mark a filterable ID on a filter DTO

```java
import org.fireflyframework.utils.annotations.FilterableId;
import java.util.UUID;

public class UserFilterDTO {
    @FilterableId
    private UUID customerId;   // included in filters with exact matching, exposed as a query param

    private UUID accountId;    // excluded from filters (default behaviour for *Id fields)

    private String name;       // regular field with full filtering capabilities
}
```

## Configuration

This module has **no Spring `@ConfigurationProperties` and no auto-configuration** — there are no `firefly.*` properties to set in `application.yml`. `TemplateRenderUtil` is configured programmatically via its static methods, which apply globally to the utility:

| Method | Purpose | Default |
| --- | --- | --- |
| `setTemplateCachingEnabled(boolean)` | Enable/disable in-memory template caching | enabled |
| `setTemplateCacheMaxSize(int)` | Max number of cached compiled templates | `100` |
| `setAsyncThreadPoolSize(int)` | Size of the async rendering thread pool | `max(2, availableProcessors())` |
| `addSharedVariable(name, value)` | Expose a variable to every template | none |
| `setTemplatePreProcessor(BiFunction)` | Transform template source before rendering | none |
| `setTemplatePostProcessor(BiFunction)` | Transform rendered output after rendering | none |
| `addTemplateLoader(TemplateLoader)` | Register an additional FreeMarker loader | classpath + `./templates` |
| `setTemplateDirectory(String)` / `setClasspathAndFileTemplateLoaders(...)` | Override where templates are loaded from | `classpath:/templates`, then `./templates` |

Template lookup order out of the box: `classpath:/templates`, then the working-directory `./templates` folder, then any custom loaders. FreeMarker defaults: UTF-8 encoding, `Locale.US`, and a rethrow exception handler.

Call `TemplateRenderUtil.shutdownAsyncThreadPool()` on application shutdown if you used the async APIs.

## Documentation

- In-repo tutorials under [`tutorials/`](tutorials/) — a four-part series covering `TemplateRenderUtil` basics, FreeMarker syntax, PDF customization, and advanced features (caching, shared variables, hooks, async, validation, image rendering).
- Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- [FreeMarker manual](https://freemarker.apache.org/docs/) and [Flying Saucer](https://github.com/flyingsaucerproject/flyingsaucer) for template syntax and rendering specifics.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
