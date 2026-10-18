# Introduction to TemplateRenderUtil

## Overview

TemplateRenderUtil is a powerful utility class that simplifies the process of rendering [FreeMarker](https://freemarker.apache.org/) templates to various output formats including HTML, PDF, and images. It provides a comprehensive set of features for template processing, with extensive customization options.

This tutorial will introduce you to the basic concepts and usage of TemplateRenderUtil.

## Features

TemplateRenderUtil offers the following key features:

- Rendering FreeMarker templates to HTML
- Converting HTML to PDF with customizable options
- Direct HTML to PDF conversion (without requiring templates)
- Converting HTML to images
- Template caching for improved performance
- Template validation
- Shared variables across templates
- Pre-processing and post-processing hooks
- Asynchronous rendering
- PDF customization (page size, margins, bookmarks, etc.)
- Security features for PDF documents

## Prerequisites

To use TemplateRenderUtil, you need to import the lib-utils library in your project:

```xml
<!-- lib-utils library containing TemplateRenderUtil -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>lib-utils</artifactId>
    <version>1.0.0</version>
</dependency>
```

The lib-utils library already includes all necessary dependencies:

- FreeMarker Template Engine (for template processing)
- Flying Saucer with OpenPDF (for PDF generation)
- SLF4J API (for logging)

No additional dependencies are required to use the TemplateRenderUtil functionality.

## Basic Usage

### Rendering a Template to HTML

The most basic operation is rendering a FreeMarker template to HTML:

```java
// Create a data model
Map<String, Object> dataModel = new HashMap<>();
dataModel.put("name", "World");

// Render template to HTML
try {
    String html = TemplateRenderUtil.renderTemplateToHtml("hello.ftl", dataModel);
    System.out.println(html);
} catch (Exception e) {
    e.printStackTrace();
}
```

In this example:
- `hello.ftl` is the name of the template file
- `dataModel` is a map containing the data to be used in the template
- The result is the rendered HTML as a string

### Rendering a Template String to HTML

You can also render a template string (not from a file):

```java
// Create a template string
String templateContent = "<p>Hello ${name}!</p>";

// Create a data model
Map<String, Object> dataModel = new HashMap<>();
dataModel.put("name", "Template String");

// Render the template string
try {
    String html = TemplateRenderUtil.renderTemplateStringToHtml(templateContent, "inline-template", dataModel);
    System.out.println(html);
} catch (Exception e) {
    e.printStackTrace();
}
```

### Converting HTML to PDF

You can convert HTML (either from a template or a string) to PDF:

```java
// Create a data model
Map<String, Object> dataModel = new HashMap<>();
dataModel.put("name", "PDF Example");

try {
    // Render template to PDF file
    TemplateRenderUtil.renderTemplateToPdfFile("hello.ftl", dataModel, "output.pdf");

    // Or get the PDF as bytes
    byte[] pdfBytes = TemplateRenderUtil.renderTemplateToPdfBytes("hello.ftl", dataModel);
} catch (Exception e) {
    e.printStackTrace();
}
```

## Template Location

By default, TemplateRenderUtil looks for templates in two locations:

1. Classpath: `/templates` directory in the classpath
2. File system: `templates` directory in the current working directory

You can customize these locations:

```java
// Set a specific directory for templates
TemplateRenderUtil.setTemplateDirectory("/path/to/templates");

// Or use both classpath and file system
TemplateRenderUtil.setClasspathAndFileTemplateLoaders("templates", "/path/to/external/templates");
```

## Error Handling

When using TemplateRenderUtil, you should handle the following exceptions:

```java
try {
    String html = TemplateRenderUtil.renderTemplateToHtml("template.ftl", dataModel);
} catch (IOException e) {
    // Handle file access errors
    System.err.println("Could not access template file: " + e.getMessage());
} catch (TemplateException e) {
    // Handle template processing errors
    System.err.println("Error in template syntax or processing: " + e.getMessage());
}
```

For PDF operations, you might need to handle additional exceptions:

```java
try {
    TemplateRenderUtil.renderTemplateToPdfFile("template.ftl", dataModel, "output.pdf");
} catch (Exception e) {
    // Handle all possible exceptions
    System.err.println("Error generating PDF: " + e.getMessage());
}
```

## Next Steps

Now that you understand the basics of TemplateRenderUtil, you can explore more advanced features:

- [Template Syntax and Structure](02-template-syntax-and-structure.md)
- [PDF Customization Options](03-pdf-customization-options.md)
- [Advanced Features](04-advanced-features.md)
- [Complete Examples](05-complete-examples.md)