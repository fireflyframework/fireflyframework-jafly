# lib-utils Tutorials

Welcome to the lib-utils tutorials! This directory contains a series of tutorials to help you learn how to use the utilities provided by the lib-utils library, with a focus on the powerful `TemplateRenderUtil` class.

## Prerequisites

Before starting these tutorials, make sure you have:

1. Added the lib-utils dependency to your project:
   ```xml
   <dependency>
       <groupId>org.fireflyframework</groupId>
       <artifactId>lib-utils</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```

2. Basic knowledge of Java programming
3. Familiarity with Maven or Gradle for dependency management

## Tutorial Series

The tutorials are designed to be followed in sequence, starting with the basics and progressing to more advanced topics:

### 1. [Introduction to TemplateRenderUtil](01-introduction-to-templaterenderutil.md)

This tutorial introduces the basic concepts and usage of the `TemplateRenderUtil` class. You'll learn:
- How to render FreeMarker templates to HTML
- How to convert HTML to PDF
- How to configure template locations
- Basic error handling

### 2. [Template Syntax and Structure](02-template-syntax-and-structure.md)

This tutorial covers the FreeMarker template syntax and how to structure your templates effectively:
- FreeMarker syntax basics
- Variables, expressions, and directives
- Template inheritance and inclusion
- Best practices for template organization

### 3. [PDF Customization Options](03-pdf-customization-options.md)

This tutorial explores the various options available for customizing PDF output:
- Page size and margins
- Fonts and styling
- Headers and footers
- Bookmarks and metadata
- Security features (passwords and permissions)

### 4. [Advanced Features](04-advanced-features.md)

This tutorial covers advanced features of the `TemplateRenderUtil` class:
- Template caching for improved performance
- Shared variables across templates
- Pre-processing and post-processing hooks
- Asynchronous rendering
- Template validation
- HTML to image conversion
- Custom template loaders

## How to Use These Tutorials

1. **Start with the Introduction**: Begin with the first tutorial to understand the basics.
2. **Follow the Sequence**: The tutorials build on each other, so it's best to follow them in order.
3. **Try the Examples**: Each tutorial includes code examples that you can copy and adapt for your own projects.
4. **Experiment**: Don't hesitate to experiment with the examples and try different options.
5. **Refer to the API Documentation**: For more detailed information, refer to the Javadoc documentation.

## Additional Resources

- [Main README](../README.md): Overview of the entire lib-utils library
- [FreeMarker Documentation](https://freemarker.apache.org/docs/): Official documentation for the FreeMarker template engine
- [Flying Saucer Documentation](https://github.com/flyingsaucerproject/flyingsaucer): Documentation for the Flying Saucer HTML renderer used for PDF generation

## Getting Help

If you encounter any issues or have questions about the tutorials or the lib-utils library:

1. Check the [GitHub Issues](https://github.org/fireflyframework-oss/lib-utils/issues) for existing problems and solutions
2. Create a new issue if your problem hasn't been addressed
3. Contribute improvements to the tutorials by submitting a pull request

Happy learning!
