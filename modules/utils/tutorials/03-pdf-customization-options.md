# PDF Customization Options

## Introduction

One of the most powerful features of TemplateRenderUtil is its ability to convert HTML templates to PDF documents with extensive customization options. This tutorial covers the various ways you can customize PDF output using the `PdfOptions` class.

## The PdfOptions Class

The `TemplateRenderUtil.PdfOptions` class provides a fluent interface for configuring PDF generation. It allows you to customize:

- Page size and margins
- Fonts
- Bookmarks (document outline)
- Watermarks
- Document security (encryption, passwords, permissions)
- Metadata

## Basic Usage

Here's how to create a basic PdfOptions object:

```java
// Create PDF options with default settings
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions();

// Use the options when converting to PDF
TemplateRenderUtil.renderTemplateToPdfFile("template.ftl", dataModel, "output.pdf", options);
```

## Page Size and Margins

### Setting Page Size

You can choose from standard page sizes:

```java
// Set page size to Letter
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withPageSize(TemplateRenderUtil.PdfOptions.PageSize.LETTER);

// Available page sizes:
// - PageSize.A4 (default)
// - PageSize.LETTER
// - PageSize.LEGAL
// - PageSize.A3
```

### Setting Margins

Margins are specified in points (1/72 inch):

```java
// Set custom margins (top, right, bottom, left)
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withMargins(72, 72, 72, 72);  // 1 inch margins on all sides
```

## Font Configuration

### Setting Font Directory

You can specify a directory containing custom fonts:

```java
// Set font directory
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withFontDirectory("/path/to/fonts");
```

The utility will automatically load all `.ttf` and `.otf` font files from this directory.

### Setting Default Font

You can specify a default font for the entire document:

```java
// Set default font
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withDefaultFont("Arial");
```

## Document Bookmarks

Bookmarks (also known as the document outline) help users navigate through the PDF:

```java
// Add bookmarks to the PDF
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    // Add top-level bookmarks
    .withBookmark("Chapter 1", "1")  // Title and page number
    // Add child bookmarks
    .withChildBookmark("Section 1.1", "1")
    .withChildBookmark("Section 1.2", "1")
    // Add another top-level bookmark
    .withBookmark("Chapter 2", "2");
```

You can also clear all bookmarks:

```java
// Clear all bookmarks
options.clearBookmarks();
```

## Watermarks

You can add text watermarks to your PDF documents:

```java
// Add a watermark
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withWatermark("CONFIDENTIAL");  // Simple watermark with default settings

// Or with custom settings
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withWatermark(
        "DRAFT",       // Text
        0.3f,          // Opacity (0.0-1.0)
        60,            // Font size
        45,            // Rotation angle in degrees
        "#FF0000"      // Color (HTML format)
    );
```

## Document Security

You can secure your PDF documents with passwords and permissions:

```java
// Add encryption with owner password only
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withEncryption("owner123");  // Only owner password

// Or with full security options
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withEncryption(
        "user123",     // User password (required to open the document)
        "owner456",    // Owner password (required to change permissions)
        true,          // Allow printing
        false,         // Disallow copying content
        false          // Disallow modifying the document
    );
```

## Document Metadata

You can add metadata to your PDF documents:

```java
// Add metadata
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withMetadata(
        "Invoice #12345",             // Title
        "Your Company Name",          // Author
        "Monthly Invoice",            // Subject
        "invoice, billing, monthly"   // Keywords
    );
```

## Base URI for Resources

If your HTML references external resources (like images), you can set a base URI:

```java
// Set base URI for resolving relative links
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    .withBaseUri("file:///path/to/resources/");
```

## Combining Multiple Options

You can chain multiple options together:

```java
// Create PDF options with multiple settings
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    // Page settings
    .withPageSize(TemplateRenderUtil.PdfOptions.PageSize.LETTER)
    .withMargins(36, 36, 36, 36)  // 0.5 inch margins
    
    // Font settings
    .withFontDirectory("/path/to/fonts")
    .withDefaultFont("Helvetica")
    
    // Document navigation
    .withBookmark("Summary", "1")
    .withBookmark("Details", "2")
    .withChildBookmark("Section 2.1", "2")
    
    // Security
    .withEncryption("owner123")
    
    // Metadata
    .withMetadata("Report", "System", "Monthly Report", "report,monthly,data");
```

## Complete Example

Here's a complete example showing how to generate a PDF with custom options:

```java
// Create data model
Map<String, Object> dataModel = new HashMap<>();
dataModel.put("title", "Quarterly Report");
dataModel.put("quarter", "Q2 2023");
dataModel.put("content", "This is the quarterly report content...");

// Create PDF options
TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
    // Page settings
    .withPageSize(TemplateRenderUtil.PdfOptions.PageSize.A4)
    .withMargins(72, 54, 72, 54)  // 1 inch top/bottom, 0.75 inch left/right
    
    // Font settings
    .withDefaultFont("Times New Roman")
    
    // Document navigation
    .withBookmark("Executive Summary", "1")
    .withBookmark("Financial Results", "2")
    .withChildBookmark("Revenue", "2")
    .withChildBookmark("Expenses", "3")
    .withBookmark("Projections", "4")
    
    // Add a watermark for draft documents
    .withWatermark("DRAFT", 0.2f, 72, 45, "#888888")
    
    // Add metadata
    .withMetadata(
        "Q2 2023 Financial Report",
        "Finance Department",
        "Quarterly Financial Report",
        "financial,quarterly,report,Q2,2023"
    );

try {
    // Generate the PDF
    TemplateRenderUtil.renderTemplateToPdfFile(
        "reports/quarterly.ftl", 
        dataModel, 
        "Q2-2023-Report.pdf", 
        options
    );
    System.out.println("PDF report generated successfully!");
} catch (Exception e) {
    System.err.println("Failed to generate PDF report: " + e.getMessage());
    e.printStackTrace();
}
```

## Best Practices for PDF Customization

1. **Choose Appropriate Page Size**: Select the page size based on your target audience (A4 is common internationally, Letter in the US).

2. **Set Reasonable Margins**: Ensure your margins are large enough for printing (at least 0.5 inch or 36 points).

3. **Use Bookmarks for Long Documents**: Add bookmarks to make navigation easier in longer documents.

4. **Embed Fonts**: For consistent rendering across all PDF viewers, embed custom fonts by setting the font directory.

5. **Add Metadata**: Include proper metadata to make your PDFs more searchable and professional.

6. **Test PDF Rendering**: Always test your PDFs on different viewers to ensure consistent rendering.

7. **Consider File Size**: Be mindful of image quality and embedded resources to keep PDF file sizes reasonable.

## Next Steps

Now that you understand how to customize PDF output, you can explore:

- [Advanced Features](04-advanced-features.md)
- [Complete Examples](05-complete-examples.md)