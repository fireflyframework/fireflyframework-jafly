# Template Syntax and Structure

## Introduction to FreeMarker Templates

TemplateRenderUtil uses [FreeMarker](https://freemarker.apache.org/) as its template engine. FreeMarker is a powerful template engine that allows you to create dynamic content by combining static template content with dynamic data.

This tutorial will cover the basic syntax and structure of FreeMarker templates that you can use with TemplateRenderUtil.

## Basic Template Structure

A FreeMarker template is essentially an HTML file with special FreeMarker tags and expressions that are processed and replaced with actual values when the template is rendered.

Here's a simple example of a FreeMarker template:

```html
<!DOCTYPE html>
<html>
<head>
    <title>${title}</title>
</head>
<body>
    <h1>${title}</h1>
    <p>Hello, ${name}!</p>
    <p>Today is: ${.now?string("yyyy-MM-dd")}</p>
</body>
</html>
```

In this template:
- `${title}` and `${name}` are variables that will be replaced with values from the data model
- `${.now?string("yyyy-MM-dd")}` is a built-in expression that outputs the current date in the specified format

## FreeMarker Syntax

### Variables and Expressions

To output a variable value, use the `${...}` syntax:

```
Hello, ${name}!
```

You can also use expressions within the `${...}` syntax:

```
Total: ${price * quantity}
```

### Conditionals

Use `<#if>`, `<#elseif>`, and `<#else>` for conditional content:

```
<#if user.age lt 18>
    <p>Sorry, you are too young.</p>
<#elseif user.age gt 65>
    <p>Senior discount applies.</p>
<#else>
    <p>Welcome, ${user.name}!</p>
</#if>
```

### Loops

Use `<#list>` to iterate over collections:

```
<h2>Items:</h2>
<ul>
<#list items as item>
    <li>${item.name}: $${item.price}</li>
</#list>
</ul>
```

You can also check if a list is empty:

```
<#if items?has_content>
    <h2>Items:</h2>
    <ul>
    <#list items as item>
        <li>${item.name}: $${item.price}</li>
    </#list>
    </ul>
<#else>
    <p>No items found.</p>
</#if>
```

### Including Other Templates

You can include other templates using the `<#include>` directive:

```
<#include "header.ftl">
<div class="content">
    <h1>${title}</h1>
    <p>${content}</p>
</div>
<#include "footer.ftl">
```

### Macros

Macros are reusable template fragments:

```
<#macro greet person>
    <div class="greeting">
        <p>Hello, ${person.name}!</p>
        <#if person.birthday??>
            <p>Happy Birthday!</p>
        </#if>
    </div>
</#macro>

<@greet person=user />
```

## Data Types in FreeMarker

FreeMarker supports various data types:

### Strings

```
${name}
${name?upper_case}
${name?length}
${name?substring(0, 5)}
```

### Numbers

```
${price}
${price?string.currency}
${quantity?string("0000")}
```

### Dates

```
${date}
${date?string("yyyy-MM-dd")}
${date?string("EEE, MMM d, yyyy")}
```

### Booleans

```
${isActive?string("Yes", "No")}
```

### Collections (Lists and Maps)

```
<#list users as user>
    ${user.name}
</#list>

${users[0].name}
${users?size}

${person.address.city}
```

## Using TemplateRenderUtil with FreeMarker Templates

### Creating a Template File

Create a file with the `.ftl` extension in your templates directory:

**invoice.ftl**:
```html
<!DOCTYPE html>
<html>
<head>
    <title>Invoice #${invoice.number}</title>
    <style>
        body { font-family: Arial, sans-serif; }
        .invoice-header { background-color: #f0f0f0; padding: 10px; }
        .invoice-items { width: 100%; border-collapse: collapse; }
        .invoice-items th, .invoice-items td { border: 1px solid #ddd; padding: 8px; }
        .invoice-total { text-align: right; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="invoice-header">
        <h1>Invoice #${invoice.number}</h1>
        <p>Date: ${invoice.date?string("yyyy-MM-dd")}</p>
        <p>Due Date: ${invoice.dueDate?string("yyyy-MM-dd")}</p>
    </div>
    
    <div class="customer-info">
        <h2>Customer</h2>
        <p>${invoice.customer.name}<br>
        ${invoice.customer.email}<br>
        ${invoice.customer.address}</p>
    </div>
    
    <h2>Items</h2>
    <table class="invoice-items">
        <tr>
            <th>Description</th>
            <th>Quantity</th>
            <th>Price</th>
            <th>Amount</th>
        </tr>
        <#list invoice.items as item>
        <tr>
            <td>${item.description}</td>
            <td>${item.quantity}</td>
            <td>$${item.price?string("0.00")}</td>
            <td>$${item.amount?string("0.00")}</td>
        </tr>
        </#list>
    </table>
    
    <div class="invoice-total">
        <p>Subtotal: $${invoice.subtotal?string("0.00")}</p>
        <p>Tax: $${invoice.tax?string("0.00")}</p>
        <p><strong>Total: $${invoice.total?string("0.00")}</strong></p>
    </div>
</body>
</html>
```

### Rendering the Template

Use TemplateRenderUtil to render the template:

```java
// Create invoice data
Map<String, Object> invoice = new HashMap<>();
invoice.put("number", "INV-2023-001");
invoice.put("date", new Date());
invoice.put("dueDate", new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));

Map<String, Object> customer = new HashMap<>();
customer.put("name", "John Doe");
customer.put("email", "john.doe@example.com");
customer.put("address", "123 Main St, Anytown, USA");
invoice.put("customer", customer);

List<Map<String, Object>> items = new ArrayList<>();
Map<String, Object> item1 = new HashMap<>();
item1.put("description", "Website Design");
item1.put("quantity", 1);
item1.put("price", 1200.00);
item1.put("amount", 1200.00);
items.add(item1);

Map<String, Object> item2 = new HashMap<>();
item2.put("description", "Hosting (1 year)");
item2.put("quantity", 1);
item2.put("price", 300.00);
item2.put("amount", 300.00);
items.add(item2);

invoice.put("items", items);
invoice.put("subtotal", 1500.00);
invoice.put("tax", 150.00);
invoice.put("total", 1650.00);

// Render the template to PDF
try {
    TemplateRenderUtil.renderTemplateToPdfFile("invoice.ftl", invoice, "invoice.pdf");
    System.out.println("Invoice PDF generated successfully!");
} catch (Exception e) {
    System.err.println("Failed to generate invoice: " + e.getMessage());
    e.printStackTrace();
}
```

## Best Practices for Templates

1. **Separate Content from Presentation**: Use CSS for styling and keep the template focused on structure.

2. **Use Includes for Common Elements**: Extract headers, footers, and other repeated elements into separate template files.

3. **Handle Missing Values**: Always check if a value exists before using it to avoid errors:
   ```
   <#if user.email??>${user.email}<#else>No email provided</#if>
   ```

4. **Format Numbers and Dates Properly**: Use FreeMarker's built-in formatting functions:
   ```
   ${price?string.currency}
   ${date?string("yyyy-MM-dd")}
   ```

5. **Comment Your Templates**: Use FreeMarker comments for complex sections:
   ```
   <#-- This section displays the user profile -->
   ```

6. **Validate Your Templates**: Use TemplateRenderUtil's validation methods to check for syntax errors:
   ```java
   List<String> errors = TemplateRenderUtil.validateTemplateFile("invoice.ftl");
   if (!errors.isEmpty()) {
       System.err.println("Template has errors: " + errors);
   }
   ```

## Next Steps

Now that you understand the syntax and structure of FreeMarker templates, you can explore:

- [PDF Customization Options](03-pdf-customization-options.md)
- [Advanced Features](04-advanced-features.md)
- [Complete Examples](05-complete-examples.md)