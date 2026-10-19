![Jafly. Utils](../../docs/site/assets/modules/utils.svg)

# Utils

Render FreeMarker templates and convert rendered content to document formats; mark filter DTO fields.

Use TemplateRenderUtil for template rendering and document output, and UserFilterDTO for the filter metadata consumed by the R2DBC utilities. Keep template resources and model data under application control.

[Module guide](../../docs/site/modules/utils.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

After accepting an application, the service can render a confirmation template or generate a PDF receipt. Template data and document storage remain application choices.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-utils</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

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

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`TemplateRenderUtil`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/utils/src/main/java/org/fireflyframework/utils/template/TemplateRenderUtil.java) | Utility for rendering FreeMarker templates to XHTML, PDF, and images using Flying Saucer. |
| [`FilterableId`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/utils/src/main/java/org/fireflyframework/utils/annotations/FilterableId.java) | The FilterableId annotation is used to mark ID fields that should be included in filtering operations. |

## Continue the application

Use the [Notifications Core guide](../../docs/site/modules/notifications-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-utils -am verify
./mvnw -pl :fireflyframework-utils -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/utils.md).
