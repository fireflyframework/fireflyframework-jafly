/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.fireflyframework.utils.template;

import freemarker.template.TemplateException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateRenderUtil.
 */
public class TemplateRenderUtilTest {
    private static final String TEMPLATE_DIR = "templates";

    @BeforeAll
    static void setupTemplateDirectory() throws IOException {
        Path dir = Paths.get(TEMPLATE_DIR);
        if (Files.notExists(dir)) {
            Files.createDirectory(dir);
        }
        // Write a simple FreeMarker template for testing
        String templateContent = "<p>Hello ${name}!</p>";
        Files.write(dir.resolve("test.ftl"), templateContent.getBytes(StandardCharsets.UTF_8));

        // Write a template that uses shared variables
        String sharedVarTemplate = "<p>Hello ${name}!</p><p>Company: ${companyName}</p>";
        Files.write(dir.resolve("shared-var-test.ftl"), sharedVarTemplate.getBytes(StandardCharsets.UTF_8));
    }

    @AfterAll
    static void cleanup() {
        // Clean up any shared variables or processors that might affect other tests
        TemplateRenderUtil.clearSharedVariables();
        TemplateRenderUtil.setTemplatePreProcessor(null);
        TemplateRenderUtil.setTemplatePostProcessor(null);
        TemplateRenderUtil.shutdownAsyncThreadPool();
    }

    @Test
    void testRenderTemplateToHtml() throws IOException, TemplateException {
        String html = TemplateRenderUtil.renderTemplateToHtml("test.ftl", Map.of("name", "World"));
        assertNotNull(html, "HTML output should not be null");
        assertTrue(html.contains("Hello World!"), "Rendered HTML should contain the correct greeting");
    }

    @Test
    void testRenderTemplateStringToHtml() throws IOException, TemplateException {
        // Create a template string
        String templateContent = "<p>Hello ${name}!</p>";

        // Create a data model
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("name", "Template String");

        // Render the template string
        String html = TemplateRenderUtil.renderTemplateStringToHtml(templateContent, "string-template", dataModel);

        // Verify the HTML contains the expected content
        assertNotNull(html, "HTML output should not be null");
        assertTrue(html.contains("Hello Template String!"), "Rendered HTML should contain the correct greeting");
    }

    @Test
    void testRenderTemplateStringWithNullName() throws IOException, TemplateException {
        // Create a template string
        String templateContent = "<p>Hello ${name}!</p>";

        // Create a data model
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("name", "Auto-named Template");

        // Render the template string with null name (should auto-generate a name)
        String html = TemplateRenderUtil.renderTemplateStringToHtml(templateContent, null, dataModel);

        // Verify the HTML contains the expected content
        assertNotNull(html, "HTML output should not be null");
        assertTrue(html.contains("Hello Auto-named Template!"), "Rendered HTML should contain the correct greeting");
    }

    @Test
    void testRenderHtmlToPdfProducesPdfHeader() throws Exception {
        String html = "<html><body><h1>Test PDF</h1></body></html>";
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TemplateRenderUtil.renderHtmlToPdf(html, os, new TemplateRenderUtil.PdfOptions());
        byte[] pdfBytes = os.toByteArray();

        assertTrue(pdfBytes.length > 0, "PDF output should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderTemplateToPdfProducesValidPdf() throws Exception {
        // Render using the test.ftl template and ensure PDF header
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TemplateRenderUtil.renderTemplateToPdf(
                "test.ftl",
                Map.of("name", "JUnit"),
                os,
                new TemplateRenderUtil.PdfOptions()
        );
        byte[] pdfBytes = os.toByteArray();

        assertTrue(pdfBytes.length > 0, "PDF output from template should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderTemplateStringToPdfProducesValidPdf() throws Exception {
        // Create a template string
        String templateContent = "<html><body><h1>Hello ${name}!</h1></body></html>";

        // Create a data model
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("name", "Template String PDF");

        // Render the template string to PDF
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TemplateRenderUtil.renderTemplateStringToPdf(
                templateContent,
                "string-template-pdf",
                dataModel,
                os,
                new TemplateRenderUtil.PdfOptions()
        );
        byte[] pdfBytes = os.toByteArray();

        // Verify the PDF was created correctly
        assertTrue(pdfBytes.length > 0, "PDF output from template string should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderTemplateStringToPdfBytes() throws Exception {
        // Create a template string
        String templateContent = "<html><body><h1>Hello ${name}!</h1></body></html>";

        // Create a data model
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("name", "Template String PDF Bytes");

        // Render the template string to PDF bytes
        byte[] pdfBytes = TemplateRenderUtil.renderTemplateStringToPdfBytes(
                templateContent,
                "string-template-pdf-bytes",
                dataModel
        );

        // Verify the PDF bytes were created correctly
        assertTrue(pdfBytes.length > 0, "PDF bytes from template string should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderTemplateToPdfFile() throws Exception {
        // Create a temporary file for the PDF output
        File tempFile = File.createTempFile("test-pdf-file-", ".pdf");
        tempFile.deleteOnExit();

        // Render the template to a PDF file
        TemplateRenderUtil.renderTemplateToPdfFile(
                "test.ftl",
                Map.of("name", "PDF File"),
                tempFile.getAbsolutePath()
        );

        // Verify the PDF file was created and is not empty
        assertTrue(tempFile.exists(), "PDF file should exist");
        assertTrue(tempFile.length() > 0, "PDF file should not be empty");

        // Read the first few bytes to verify it's a PDF
        byte[] header = Files.readAllBytes(tempFile.toPath());
        String headerStr = new String(header, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", headerStr, "PDF file should start with '%PDF'");
    }

    @Test
    void testInvalidHtmlThrows() {
        // Provide blank HTML
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Exception ex = assertThrows(
                IllegalArgumentException.class,
                () -> TemplateRenderUtil.renderHtmlToPdf("   ", os, new TemplateRenderUtil.PdfOptions())
        );
        assertTrue(ex.getMessage().contains("HTML content is empty"));
    }

    @Test
    void testTemplateCaching() throws IOException, TemplateException {
        // Enable template caching
        TemplateRenderUtil.setTemplateCachingEnabled(true);
        TemplateRenderUtil.setTemplateCacheMaxSize(10);

        // Render the template twice
        String html1 = TemplateRenderUtil.renderTemplateToHtml("test.ftl", Map.of("name", "Cache Test 1"));
        String html2 = TemplateRenderUtil.renderTemplateToHtml("test.ftl", Map.of("name", "Cache Test 2"));

        // Verify both renders worked
        assertNotNull(html1);
        assertNotNull(html2);
        assertTrue(html1.contains("Cache Test 1"));
        assertTrue(html2.contains("Cache Test 2"));

        // Clean up
        TemplateRenderUtil.clearTemplateCache();
    }

    @Test
    void testTemplateValidation() {
        // Valid template
        String validTemplate = "<p>Hello ${name}!</p>";
        List<String> validErrors = TemplateRenderUtil.validateTemplate(validTemplate);
        assertTrue(validErrors.isEmpty(), "Valid template should have no errors");

        // Invalid template (missing closing brace)
        String invalidTemplate = "<p>Hello ${name!</p>";
        List<String> invalidErrors = TemplateRenderUtil.validateTemplate(invalidTemplate);
        assertFalse(invalidErrors.isEmpty(), "Invalid template should have errors");
    }

    @Test
    void testTemplateFileValidation() {
        // Valid template file
        List<String> validErrors = TemplateRenderUtil.validateTemplateFile("test.ftl");
        assertTrue(validErrors.isEmpty(), "Valid template file should have no errors");

        // Invalid template file (non-existent)
        List<String> invalidErrors = TemplateRenderUtil.validateTemplateFile("non-existent.ftl");
        assertFalse(invalidErrors.isEmpty(), "Non-existent template file should have errors");
    }

    @Test
    void testRenderHtmlToImage() throws Exception {
        // Create a simple HTML content
        String html = "<html><body><h1 style='color:blue'>Image Test</h1></body></html>";

        // Render to image
        byte[] imageBytes = TemplateRenderUtil.renderHtmlToImage(html, 400, 200, "png");

        // Verify image was created
        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0, "Image should not be empty");

        // Verify it's a PNG image by checking the header
        byte[] pngHeader = {(byte)0x89, 'P', 'N', 'G'};
        byte[] actualHeader = new byte[4];
        System.arraycopy(imageBytes, 0, actualHeader, 0, 4);
        assertArrayEquals(pngHeader, actualHeader, "Image should be a PNG");
    }

    @Test
    void testRenderTemplateToImage() throws Exception {
        // Render template to image
        byte[] imageBytes = TemplateRenderUtil.renderTemplateToImage(
                "test.ftl",
                Map.of("name", "Image Test"),
                400, 200, "png");

        // Verify image was created
        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0, "Image should not be empty");
    }

    @Test
    void testSharedVariables() throws Exception {
        // Add a shared variable
        TemplateRenderUtil.addSharedVariable("companyName", "Acme Corporation");

        // Render a template that uses the shared variable
        String html = TemplateRenderUtil.renderTemplateToHtml("shared-var-test.ftl", Map.of("name", "User"));

        // Verify the shared variable was used
        assertNotNull(html);
        assertTrue(html.contains("Hello User!"), "Rendered HTML should contain the name");
        assertTrue(html.contains("Company: Acme Corporation"), "Rendered HTML should contain the company name");

        // Clean up
        TemplateRenderUtil.clearSharedVariables();
    }

    @Test
    void testTemplateProcessingHooks() throws Exception {
        // Set up a preprocessor that adds a header
        BiFunction<String, Map<String, Object>, String> preprocessor = (content, model) ->
            "<h1>Preprocessed</h1>" + content;

        // Set up a postprocessor that adds a footer
        BiFunction<String, Map<String, Object>, String> postprocessor = (content, model) ->
            content + "<footer>Postprocessed</footer>";

        // Set the processors
        TemplateRenderUtil.setTemplatePreProcessor(preprocessor);
        TemplateRenderUtil.setTemplatePostProcessor(postprocessor);

        // Render a template string
        String templateContent = "<p>Hello ${name}!</p>";
        String html = TemplateRenderUtil.renderTemplateStringToHtml(templateContent, "hook-test", Map.of("name", "Hooks"));

        // Verify the hooks were applied
        assertNotNull(html);
        assertTrue(html.contains("<h1>Preprocessed</h1>"), "Rendered HTML should contain preprocessor output");
        assertTrue(html.contains("Hello Hooks!"), "Rendered HTML should contain the template content");
        assertTrue(html.contains("<footer>Postprocessed</footer>"), "Rendered HTML should contain postprocessor output");

        // Clean up
        TemplateRenderUtil.setTemplatePreProcessor(null);
        TemplateRenderUtil.setTemplatePostProcessor(null);
    }

    @Test
    void testAsyncRendering() throws Exception {
        // Render a template asynchronously
        CompletableFuture<String> future = TemplateRenderUtil.renderTemplateToHtmlAsync("test.ftl", Map.of("name", "Async"));

        // Wait for the result
        String html = future.get();

        // Verify the result
        assertNotNull(html);
        assertTrue(html.contains("Hello Async!"), "Rendered HTML should contain the correct greeting");
    }

    @Test
    void testAsyncPdfRendering() throws Exception {
        // Render a template to PDF asynchronously
        CompletableFuture<byte[]> future = TemplateRenderUtil.renderTemplateToPdfBytesAsync(
                "test.ftl",
                Map.of("name", "Async PDF"),
                new TemplateRenderUtil.PdfOptions());

        // Wait for the result
        byte[] pdfBytes = future.get();

        // Verify the PDF was created
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0, "PDF output should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderHtmlToPdfBytes() throws Exception {
        // Create a simple HTML content
        String html = "<html><body><h1>Direct HTML to PDF Test</h1></body></html>";

        // Render HTML to PDF bytes
        byte[] pdfBytes = TemplateRenderUtil.renderHtmlToPdfBytes(html);

        // Verify the PDF bytes were created correctly
        assertNotNull(pdfBytes, "PDF bytes should not be null");
        assertTrue(pdfBytes.length > 0, "PDF bytes should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderHtmlToPdfBytesWithOptions() throws Exception {
        // Create a simple HTML content
        String html = "<html><body><h1>Direct HTML to PDF Test with Options</h1></body></html>";

        // Create custom PDF options
        TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
                .withPageSize(TemplateRenderUtil.PdfOptions.PageSize.LETTER)
                .withMargins(72, 72, 72, 72);

        // Render HTML to PDF bytes with options
        byte[] pdfBytes = TemplateRenderUtil.renderHtmlToPdfBytes(html, options);

        // Verify the PDF bytes were created correctly
        assertNotNull(pdfBytes, "PDF bytes should not be null");
        assertTrue(pdfBytes.length > 0, "PDF bytes should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testRenderHtmlToPdfFile() throws Exception {
        // Create a simple HTML content
        String html = "<html><body><h1>Direct HTML to PDF File Test</h1></body></html>";

        // Create a temporary file for the PDF output
        File tempFile = File.createTempFile("html-pdf-file-", ".pdf");
        tempFile.deleteOnExit();

        // Render HTML to PDF file
        TemplateRenderUtil.renderHtmlToPdfFile(html, tempFile.getAbsolutePath());

        // Verify the PDF file was created and is not empty
        assertTrue(tempFile.exists(), "PDF file should exist");
        assertTrue(tempFile.length() > 0, "PDF file should not be empty");

        // Read the first few bytes to verify it's a PDF
        byte[] header = Files.readAllBytes(tempFile.toPath());
        String headerStr = new String(header, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", headerStr, "PDF file should start with '%PDF'");
    }

    @Test
    void testRenderHtmlToPdfBytesAsync() throws Exception {
        // Create a simple HTML content
        String html = "<html><body><h1>Async HTML to PDF Test</h1></body></html>";

        // Render HTML to PDF bytes asynchronously
        CompletableFuture<byte[]> future = TemplateRenderUtil.renderHtmlToPdfBytesAsync(html);

        // Wait for the result
        byte[] pdfBytes = future.get();

        // Verify the PDF bytes were created correctly
        assertNotNull(pdfBytes, "PDF bytes should not be null");
        assertTrue(pdfBytes.length > 0, "PDF bytes should not be empty");
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF header should start with '%PDF'");
    }

    @Test
    void testPdfBookmarksOptions() {
        // Create PDF options with bookmarks
        TemplateRenderUtil.PdfOptions options = new TemplateRenderUtil.PdfOptions()
                .withBookmark("Chapter 1", "1")
                .withChildBookmark("Section 1.1", "1")
                .withChildBookmark("Section 1.2", "1")
                .withBookmark("Chapter 2", "1");

        // Verify the bookmarks were added to the options
        assertTrue(options.hasBookmarks(), "Options should have bookmarks");

        List<TemplateRenderUtil.PdfOptions.Bookmark> bookmarks = options.getBookmarks();
        assertEquals(2, bookmarks.size(), "Should have 2 top-level bookmarks");

        // Check first bookmark and its children
        TemplateRenderUtil.PdfOptions.Bookmark chapter1 = bookmarks.get(0);
        assertEquals("Chapter 1", chapter1.getTitle(), "First bookmark should be Chapter 1");
        assertEquals(1, chapter1.getLevel(), "First bookmark should be level 1");
        assertEquals("1", chapter1.getDestination(), "First bookmark should point to page 1");

        List<TemplateRenderUtil.PdfOptions.Bookmark> children = chapter1.getChildren();
        assertEquals(2, children.size(), "Chapter 1 should have 2 children");
        assertEquals("Section 1.1", children.get(0).getTitle(), "First child should be Section 1.1");
        assertEquals("Section 1.2", children.get(1).getTitle(), "Second child should be Section 1.2");

        // Check second bookmark
        TemplateRenderUtil.PdfOptions.Bookmark chapter2 = bookmarks.get(1);
        assertEquals("Chapter 2", chapter2.getTitle(), "Second bookmark should be Chapter 2");
        assertEquals(0, chapter2.getChildren().size(), "Chapter 2 should have no children");

        // Test clearing bookmarks
        options.clearBookmarks();
        assertFalse(options.hasBookmarks(), "Options should have no bookmarks after clearing");
    }
}
