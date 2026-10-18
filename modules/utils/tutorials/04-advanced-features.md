# Advanced Features of TemplateRenderUtil

## Introduction

TemplateRenderUtil provides several advanced features that can enhance your template rendering capabilities. This tutorial covers template caching, shared variables, processing hooks, asynchronous rendering, and template validation.

## Template Caching

Template caching improves performance by storing compiled templates in memory, avoiding the need to parse and compile templates repeatedly.

### Enabling Template Caching

```java
// Enable template caching
TemplateRenderUtil.setTemplateCachingEnabled(true);

// Set maximum cache size (number of templates)
TemplateRenderUtil.setTemplateCacheMaxSize(100);
```

### Clearing the Cache

```java
// Clear the template cache when needed
TemplateRenderUtil.clearTemplateCache();
```

### When to Use Caching

Template caching is beneficial when:
- You render the same templates repeatedly
- Template parsing is a performance bottleneck
- Your templates are relatively stable (not changing frequently)

If your templates change frequently during runtime, you might need to clear the cache or disable caching to see the changes.

## Shared Variables

Shared variables are available to all templates without needing to include them in each data model.

### Adding Shared Variables

```java
// Add shared variables
try {
    TemplateRenderUtil.addSharedVariable("companyName", "Acme Corporation");
    TemplateRenderUtil.addSharedVariable("companyLogo", "/images/logo.png");
    TemplateRenderUtil.addSharedVariable("currentYear", java.time.Year.now().getValue());
    
    // You can also add complex objects
    TemplateRenderUtil.addSharedVariable("utils", new UtilityFunctions());
} catch (TemplateException e) {
    System.err.println("Error adding shared variable: " + e.getMessage());
}
```

### Removing Shared Variables

```java
// Remove a specific shared variable
TemplateRenderUtil.removeSharedVariable("companyLogo");

// Clear all shared variables
TemplateRenderUtil.clearSharedVariables();
```

### Using Shared Variables in Templates

In your FreeMarker templates, you can use shared variables just like regular model variables:

```html
<footer>
    <p>&copy; ${currentYear} ${companyName}. All rights reserved.</p>
    <img src="${companyLogo}" alt="${companyName} Logo">
</footer>
```

## Template Processing Hooks

Processing hooks allow you to modify templates before rendering or modify the rendered output.

### Pre-Processing Hooks

Pre-processors modify the template content before it's processed:

```java
// Add a pre-processor to modify template content before rendering
TemplateRenderUtil.setTemplatePreProcessor((content, model) -> {
    // Add a header to all templates
    return "<div class='header'>Company Header</div>\n" + content;
});
```

### Post-Processing Hooks

Post-processors modify the rendered HTML after processing:

```java
// Add a post-processor to modify rendered HTML
TemplateRenderUtil.setTemplatePostProcessor((html, model) -> {
    // Add analytics code to all rendered pages
    return html + "\n<script>trackPageView();</script>";
});
```

### Removing Processing Hooks

```java
// Clear processors when no longer needed
TemplateRenderUtil.setTemplatePreProcessor(null);
TemplateRenderUtil.setTemplatePostProcessor(null);
```

### Use Cases for Processing Hooks

- Adding common elements to all templates
- Injecting analytics or tracking code
- Sanitizing or transforming content
- Adding dynamic content based on the environment
- Implementing custom template inheritance

## Asynchronous Rendering

For improved performance, especially with large templates or many concurrent renderings, you can use asynchronous rendering.

### Rendering Templates Asynchronously

```java
// Render template to HTML asynchronously
CompletableFuture<String> htmlFuture = TemplateRenderUtil.renderTemplateToHtmlAsync(
    "report.ftl", 
    dataModel
);

// Process the result when it's ready
htmlFuture.thenAccept(html -> {
    System.out.println("HTML rendering completed, length: " + html.length());
    // Do something with the HTML
});
```

### Rendering to PDF Asynchronously

```java
// Render template to PDF asynchronously
CompletableFuture<byte[]> pdfFuture = TemplateRenderUtil.renderTemplateToPdfBytesAsync(
    "invoice.ftl",
    invoiceData,
    new TemplateRenderUtil.PdfOptions()
);

// Process the PDF when it's ready
pdfFuture.thenAccept(pdfBytes -> {
    System.out.println("PDF rendering completed, size: " + pdfBytes.length + " bytes");
    // Save the PDF or send it to the client
});
```

### Configuring the Thread Pool

```java
// Configure the thread pool size for async operations
TemplateRenderUtil.setAsyncThreadPoolSize(10);

// Shutdown the thread pool when no longer needed (e.g., on application shutdown)
TemplateRenderUtil.shutdownAsyncThreadPool();
```

## Template Validation

Template validation helps you catch template syntax errors before rendering.

### Validating Template Strings

```java
// Validate a template string
String templateContent = "<p>Hello ${name}!</p>";
List<String> errors = TemplateRenderUtil.validateTemplate(templateContent);

if (errors.isEmpty()) {
    System.out.println("Template is valid!");
} else {
    System.out.println("Template has errors: " + errors);
}
```

### Validating Template Files

```java
// Validate a template file
List<String> fileErrors = TemplateRenderUtil.validateTemplateFile("invoice.ftl");

if (fileErrors.isEmpty()) {
    System.out.println("Template file is valid!");
} else {
    System.out.println("Template file has errors: " + fileErrors);
}
```

## HTML to Image Conversion

TemplateRenderUtil can convert HTML or templates to images.

### Converting HTML to Images

```java
// Convert HTML string to image
String html = "<html><body><h1 style='color:blue'>Hello World</h1></body></html>";
byte[] pngBytes = TemplateRenderUtil.renderHtmlToImage(html, 800, 600, "png");

// Save the image to a file
TemplateRenderUtil.saveImageToFile(pngBytes, "output.png");
```

### Converting Templates to Images

```java
// Render a template directly to an image
byte[] templateImageBytes = TemplateRenderUtil.renderTemplateToImage(
    "certificate.ftl",
    dataModel,
    1024, 768,
    "jpg"
);

// Save the image
TemplateRenderUtil.saveImageToFile(templateImageBytes, "certificate.jpg");
```

### Converting Template Strings to Images

```java
// Render a template string to an image
String templateContent = "<html><body><h1>${title}</h1><p>${content}</p></body></html>";
byte[] templateStringImageBytes = TemplateRenderUtil.renderTemplateStringToImage(
    templateContent,
    "inline-template",
    dataModel,
    800, 600,
    "png"
);

// Save the image
TemplateRenderUtil.saveImageToFile(templateStringImageBytes, "dynamic-image.png");
```

## Custom Template Loaders

You can add custom template loaders to load templates from different sources.

```java
// Create a custom template loader
StringTemplateLoader stringLoader = new StringTemplateLoader();
stringLoader.putTemplate("dynamic-template", "<p>This is a dynamic template with ${variable}</p>");

// Add the loader to TemplateRenderUtil
TemplateRenderUtil.addTemplateLoader(stringLoader);

// Now you can use the template
Map<String, Object> model = new HashMap<>();
model.put("variable", "custom value");
String html = TemplateRenderUtil.renderTemplateToHtml("dynamic-template", model);
```

## Complete Example: Combining Advanced Features

Here's an example that combines several advanced features:

```java
// 1. Configure template caching
TemplateRenderUtil.setTemplateCachingEnabled(true);
TemplateRenderUtil.setTemplateCacheMaxSize(50);

// 2. Add shared variables
try {
    TemplateRenderUtil.addSharedVariable("companyName", "Acme Corporation");
    TemplateRenderUtil.addSharedVariable("currentYear", java.time.Year.now().getValue());
} catch (TemplateException e) {
    System.err.println("Error adding shared variable: " + e.getMessage());
}

// 3. Set up processing hooks
TemplateRenderUtil.setTemplatePreProcessor((content, model) -> {
    // Add responsive meta tag to all templates
    if (content.contains("<head>")) {
        return content.replace("<head>", 
            "<head>\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
    }
    return content;
});

TemplateRenderUtil.setTemplatePostProcessor((html, model) -> {
    // Add footer to all pages
    return html + "\n<footer>&copy; " + java.time.Year.now().getValue() + 
           " Acme Corporation. All rights reserved.</footer>";
});

// 4. Create data model
Map<String, Object> dataModel = new HashMap<>();
dataModel.put("title", "Advanced Features Demo");
dataModel.put("content", "This demonstrates advanced features of TemplateRenderUtil.");

// 5. Validate the template
List<String> errors = TemplateRenderUtil.validateTemplateFile("advanced-demo.ftl");
if (!errors.isEmpty()) {
    System.err.println("Template has errors: " + errors);
    return;
}

// 6. Render asynchronously
CompletableFuture<byte[]> pdfFuture = TemplateRenderUtil.renderTemplateToPdfBytesAsync(
    "advanced-demo.ftl",
    dataModel,
    new TemplateRenderUtil.PdfOptions()
        .withPageSize(TemplateRenderUtil.PdfOptions.PageSize.A4)
        .withMargins(36, 36, 36, 36)
        .withBookmark("Advanced Features", "1")
);

// 7. Process the result
pdfFuture.thenAccept(pdfBytes -> {
    try {
        // Save the PDF
        try (FileOutputStream fos = new FileOutputStream("advanced-demo.pdf")) {
            fos.write(pdfBytes);
        }
        System.out.println("PDF generated successfully!");
    } catch (IOException e) {
        System.err.println("Error saving PDF: " + e.getMessage());
    }
}).exceptionally(ex -> {
    System.err.println("Error generating PDF: " + ex.getMessage());
    return null;
});

// 8. Clean up when done
// TemplateRenderUtil.clearSharedVariables();
// TemplateRenderUtil.setTemplatePreProcessor(null);
// TemplateRenderUtil.setTemplatePostProcessor(null);
// TemplateRenderUtil.shutdownAsyncThreadPool();
```

## Best Practices for Advanced Features

1. **Use Template Caching Wisely**: Enable caching for production environments but consider disabling it during development for easier debugging.

2. **Limit Shared Variables**: Only use shared variables for truly global values to avoid confusion.

3. **Keep Processing Hooks Simple**: Complex processing in hooks can impact performance.

4. **Handle Async Exceptions**: Always include exception handling with asynchronous operations.

5. **Validate Templates Early**: Validate templates during application startup to catch errors early.

6. **Clean Up Resources**: Shut down the async thread pool when your application is shutting down.

7. **Test Thoroughly**: Advanced features can have subtle interactions, so test combinations thoroughly.

## Next Steps

Now that you understand the advanced features of TemplateRenderUtil, you can explore:

- [Complete Examples](05-complete-examples.md)