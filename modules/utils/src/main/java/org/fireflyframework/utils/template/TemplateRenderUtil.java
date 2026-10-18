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

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.swing.Java2DRenderer;
import org.xhtmlrenderer.util.FSImageWriter;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfOutline;
import com.lowagie.text.pdf.PdfAction;
import com.lowagie.text.pdf.PdfDestination;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/**
 * Utility for rendering FreeMarker templates to XHTML, PDF, and images using Flying Saucer.
 * Ensures well-formed XHTML, supports classpath and filesystem loaders,
 * custom PDF options, and font embedding.
 *
 * This utility supports rendering templates from both paths and strings, and provides
 * features such as:
 * - Template caching for improved performance
 * - Custom template loaders
 * - Template validation
 * - HTML to image conversion
 * - Enhanced PDF options (watermarks, encryption, metadata)
 */
public class TemplateRenderUtil {
    private static final Logger log = LoggerFactory.getLogger(TemplateRenderUtil.class);
    private static final List<TemplateLoader> additionalLoaders = new ArrayList<>();
    private static Configuration freemarkerConfig = createFreemarkerConfig();
    private static final Map<String, Template> templateCache = new ConcurrentHashMap<>();
    private static boolean templateCachingEnabled = true;
    private static int templateCacheMaxSize = 100;
    private static final Map<String, Object> sharedVariables = new HashMap<>();
    private static ExecutorService executorService = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private static BiFunction<String, Map<String, Object>, String> templatePreProcessor = null;
    private static BiFunction<String, Map<String, Object>, String> templatePostProcessor = null;

    /**
     * Creates the default FreeMarker configuration.
     *
     * @return A configured FreeMarker Configuration instance
     */
    private static Configuration createFreemarkerConfig() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
        cfg.setLocale(Locale.US);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // Multi-loader: first classpath:/templates, then ./templates
        ClassTemplateLoader ctl = new ClassTemplateLoader(
                Thread.currentThread().getContextClassLoader(), "/templates");
        try {
            FileTemplateLoader ftl = new FileTemplateLoader(new File("templates"));
            List<TemplateLoader> loaderList = new ArrayList<>();
            loaderList.add(ctl);
            loaderList.add(ftl);
            loaderList.addAll(additionalLoaders);

            TemplateLoader[] loaders = loaderList.toArray(new TemplateLoader[0]);
            cfg.setTemplateLoader(new MultiTemplateLoader(loaders));
        } catch (IOException e) {
            log.warn("Filesystem loader not available, falling back to classpath only", e);

            if (additionalLoaders.isEmpty()) {
                cfg.setTemplateLoader(ctl);
            } else {
                List<TemplateLoader> loaderList = new ArrayList<>();
                loaderList.add(ctl);
                loaderList.addAll(additionalLoaders);
                TemplateLoader[] loaders = loaderList.toArray(new TemplateLoader[0]);
                cfg.setTemplateLoader(new MultiTemplateLoader(loaders));
            }
        }

        return cfg;
    }

    /**
     * Resets the FreeMarker configuration with the current settings.
     * This is useful after adding custom template loaders or changing configuration.
     */
    private static void resetConfiguration() {
        // Clear the template cache when resetting configuration
        templateCache.clear();

        // Recreate the configuration with current settings
        freemarkerConfig = createFreemarkerConfig();

        // Re-apply shared variables
        for (Map.Entry<String, Object> entry : sharedVariables.entrySet()) {
            try {
                freemarkerConfig.setSharedVariable(entry.getKey(), entry.getValue());
            } catch (TemplateException e) {
                log.error("Failed to set shared variable: {}", entry.getKey(), e);
            }
        }

        log.info("FreeMarker configuration has been reset");
    }

    /**
     * Adds a shared variable that will be available to all templates.
     *
     * @param name the name of the variable
     * @param value the value of the variable
     * @throws TemplateException if the variable cannot be set
     */
    public static void addSharedVariable(String name, Object value) throws TemplateException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Variable name cannot be null or empty");
        }

        sharedVariables.put(name, value);
        freemarkerConfig.setSharedVariable(name, value);
        log.info("Added shared variable: {}", name);
    }

    /**
     * Removes a shared variable.
     *
     * @param name the name of the variable to remove
     */
    public static void removeSharedVariable(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        sharedVariables.remove(name);
        // Just remove from the map, no need to set to null in the config
        log.info("Removed shared variable: {}", name);
    }

    /**
     * Clears all shared variables.
     */
    public static void clearSharedVariables() {
        sharedVariables.clear();
        resetConfiguration();
        log.info("Cleared all shared variables");
    }

    /**
     * Sets configuration properties for FreeMarker.
     *
     * @param properties the properties to set
     * @throws TemplateException if there is an error setting the properties
     */
    public static void setConfigurationProperties(Properties properties) throws TemplateException {
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }

        try {
            freemarkerConfig.setSettings(properties);
            log.info("Applied configuration properties");
        } catch (TemplateException e) {
            log.error("Failed to apply configuration properties", e);
            throw e;
        }
    }

    /**
     * Sets a template preprocessor function that will be applied to template content before rendering.
     * The function takes the template content and data model as input and returns the modified template content.
     *
     * @param preprocessor the preprocessor function, or null to remove the current preprocessor
     */
    public static void setTemplatePreProcessor(BiFunction<String, Map<String, Object>, String> preprocessor) {
        templatePreProcessor = preprocessor;
        log.info("Template preprocessor {}", preprocessor != null ? "set" : "removed");
    }

    /**
     * Sets a template postprocessor function that will be applied to rendered output.
     * The function takes the rendered output and the original data model as input and returns the modified output.
     *
     * @param postprocessor the postprocessor function, or null to remove the current postprocessor
     */
    public static void setTemplatePostProcessor(BiFunction<String, Map<String, Object>, String> postprocessor) {
        templatePostProcessor = postprocessor;
        log.info("Template postprocessor {}", postprocessor != null ? "set" : "removed");
    }

    /**
     * Configures the thread pool for asynchronous rendering.
     *
     * @param threadCount the number of threads in the pool
     */
    public static void setAsyncThreadPoolSize(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }

        // Shutdown the existing executor service
        if (executorService != null) {
            executorService.shutdown();
        }

        // Create a new executor service with the specified thread count
        executorService = Executors.newFixedThreadPool(threadCount);
        log.info("Async thread pool size set to {}", threadCount);
    }

    /**
     * Shuts down the thread pool for asynchronous rendering.
     * This should be called when the application is shutting down.
     */
    public static void shutdownAsyncThreadPool() {
        if (executorService != null) {
            executorService.shutdown();
            log.info("Async thread pool shut down");
        }
    }

    /**
     * Sets the directory where template files are located.
     *
     * @param templateDir the directory containing template files
     * @throws IOException if the directory cannot be accessed
     */
    public static void setTemplateDirectory(String templateDir) throws IOException {
        File dir = new File(templateDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Template directory does not exist: " + templateDir);
        }
        freemarkerConfig.setDirectoryForTemplateLoading(dir);
        // Clear the template cache when changing the template directory
        templateCache.clear();
        log.info("FreeMarker configured to load templates from directory: {}", templateDir);
    }

    /**
     * Adds a custom template loader to the configuration.
     * The loader will be added to the existing loaders.
     *
     * @param loader the template loader to add
     */
    public static void addTemplateLoader(TemplateLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("Template loader cannot be null");
        }

        additionalLoaders.add(loader);
        resetConfiguration();
        log.info("Added custom template loader: {}", loader.getClass().getSimpleName());
    }

    /**
     * Enables or disables template caching.
     * When enabled, templates are cached in memory for faster access.
     *
     * @param enabled true to enable caching, false to disable
     */
    public static void setTemplateCachingEnabled(boolean enabled) {
        templateCachingEnabled = enabled;
        if (!enabled) {
            templateCache.clear();
            log.info("Template caching disabled and cache cleared");
        } else {
            log.info("Template caching enabled");
        }
    }

    /**
     * Sets the maximum number of templates to keep in the cache.
     * If the cache exceeds this size, the least recently used templates will be removed.
     *
     * @param maxSize the maximum number of templates in the cache
     */
    public static void setTemplateCacheMaxSize(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("Template cache max size must be at least 1");
        }

        templateCacheMaxSize = maxSize;

        // If the current cache is larger than the new max size, trim it
        if (templateCache.size() > maxSize) {
            // Simple approach: clear the cache entirely
            // A more sophisticated approach would use a LinkedHashMap with access order
            templateCache.clear();
        }

        log.info("Template cache max size set to: {}", maxSize);
    }

    /**
     * Clears the template cache.
     */
    public static void clearTemplateCache() {
        templateCache.clear();
        log.info("Template cache cleared");
    }

    /**
     * Configures FreeMarker to load templates from both classpath and file system.
     * Templates will be searched first in the classpath, then in the file system.
     *
     * @param classpathPrefix prefix for classpath resources (e.g., "templates")
     * @param fileSystemDir directory for file system resources
     * @throws IOException if the file system directory cannot be accessed
     */
    public static void setClasspathAndFileTemplateLoaders(String classpathPrefix, String fileSystemDir) throws IOException {
        if (classpathPrefix == null || classpathPrefix.trim().isEmpty()) {
            classpathPrefix = "templates";
        }

        if (fileSystemDir == null || fileSystemDir.trim().isEmpty()) {
            fileSystemDir = "templates";
        }

        ClassTemplateLoader classLoader = new ClassTemplateLoader(
                Thread.currentThread().getContextClassLoader(), classpathPrefix);

        File dir = new File(fileSystemDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                log.warn("Could not create template directory: {}", fileSystemDir);
                // Continue with just the classpath loader
                freemarkerConfig.setTemplateLoader(classLoader);
                log.info("FreeMarker configured to load templates from classpath:{} only", classpathPrefix);
                return;
            }
        }

        if (!dir.isDirectory()) {
            throw new IOException("Template path is not a directory: " + fileSystemDir);
        }

        try {
            FileTemplateLoader fileLoader = new FileTemplateLoader(dir);
            TemplateLoader[] loaders = new TemplateLoader[]{classLoader, fileLoader};
            freemarkerConfig.setTemplateLoader(new MultiTemplateLoader(loaders));
            log.info("FreeMarker configured to load templates from classpath:{} and directory:{}",
                      classpathPrefix, fileSystemDir);
        } catch (IOException e) {
            // If file loader fails, continue with just the classpath loader
            freemarkerConfig.setTemplateLoader(classLoader);
            log.info("FreeMarker configured to load templates from classpath:{} only", classpathPrefix);
            log.warn("Could not configure file system template loader", e);
        }
    }

    /**
     * Renders a FreeMarker template to an XHTML string.
     * @param templateName path within loaders, e.g., "invoice.ftl"
     * @param dataModel the data model to use for rendering
     * @return the rendered HTML as a string
     * @throws IOException if the template cannot be read
     * @throws TemplateException if the template cannot be processed
     */
    public static String renderTemplateToHtml(String templateName, Map<String, Object> dataModel)
            throws IOException, TemplateException {
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be null or empty");
        }

        if (dataModel == null) {
            dataModel = new HashMap<>();
        }

        try {
            // Get template from cache or load it
            Template tpl = getTemplateFromCacheOrLoad(templateName);

            try (StringWriter out = new StringWriter()) {
                tpl.process(dataModel, out);
                String result = out.toString();

                // Apply post-processing if configured
                if (templatePostProcessor != null) {
                    result = templatePostProcessor.apply(result, dataModel);
                }

                return result;
            }
        } catch (IOException e) {
            log.error("Failed to load template: {}", templateName, e);
            throw new IOException("Failed to load template: " + templateName, e);
        } catch (TemplateException e) {
            log.error("Failed to process template: {}", templateName, e);
            throw e;
        }
    }

    /**
     * Renders a FreeMarker template to an XHTML string asynchronously.
     * @param templateName path within loaders, e.g., "invoice.ftl"
     * @param dataModel the data model to use for rendering
     * @return a CompletableFuture that will complete with the rendered HTML
     */
    public static CompletableFuture<String> renderTemplateToHtmlAsync(String templateName, Map<String, Object> dataModel) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return renderTemplateToHtml(templateName, dataModel);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Gets a template from the cache or loads it if not cached.
     *
     * @param templateName the name of the template to load
     * @return the loaded template
     * @throws IOException if the template cannot be loaded
     */
    private static Template getTemplateFromCacheOrLoad(String templateName) throws IOException {
        if (!templateCachingEnabled) {
            return freemarkerConfig.getTemplate(templateName);
        }

        // Check if the template is in the cache
        Template template = templateCache.get(templateName);
        if (template == null) {
            // Template not in cache, load it
            template = freemarkerConfig.getTemplate(templateName);

            // Add to cache if not at max size
            if (templateCache.size() < templateCacheMaxSize) {
                templateCache.put(templateName, template);
                log.debug("Added template to cache: {}", templateName);
            } else {
                // Cache is full, log a warning
                log.warn("Template cache is full (size: {}). Consider increasing the cache size.",
                        templateCacheMaxSize);
            }
        } else {
            log.debug("Template loaded from cache: {}", templateName);
        }

        return template;
    }

    /**
     * Renders a FreeMarker template string (not a file) to an HTML string.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @return the rendered HTML as a string
     * @throws IOException if an I/O error occurs
     * @throws TemplateException if the template cannot be processed
     * @throws IllegalArgumentException if templateContent is null or empty
     */
    public static String renderTemplateStringToHtml(String templateContent, String templateName, Map<String, Object> dataModel)
            throws IOException, TemplateException {
        if (templateContent == null || templateContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Template content cannot be null or empty");
        }

        if (templateName == null || templateName.trim().isEmpty()) {
            templateName = "inline-template-" + System.currentTimeMillis();
        }

        if (dataModel == null) {
            dataModel = new HashMap<>();
        }

        try {
            // Apply preprocessing if configured
            if (templatePreProcessor != null) {
                templateContent = templatePreProcessor.apply(templateContent, dataModel);
            }

            Template template = new Template(templateName, new StringReader(templateContent), freemarkerConfig);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String result = writer.toString();

            // Apply postprocessing if configured
            if (templatePostProcessor != null) {
                result = templatePostProcessor.apply(result, dataModel);
            }

            return result;
        } catch (TemplateException e) {
            log.error("Failed to process template string: {}", templateName, e);
            throw e;
        } catch (IOException e) {
            log.error("I/O error processing template string: {}", templateName, e);
            throw e;
        }
    }

    /**
     * Renders a FreeMarker template string (not a file) to an HTML string asynchronously.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @return a CompletableFuture that will complete with the rendered HTML
     */
    public static CompletableFuture<String> renderTemplateStringToHtmlAsync(String templateContent, String templateName, Map<String, Object> dataModel) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return renderTemplateStringToHtml(templateContent, templateName, dataModel);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Saves a template string to a file in the templates directory.
     *
     * @param templateContent the content of the template
     * @param templateName the name to save the template as
     * @throws IOException if the file cannot be written
     */
    public static void saveTemplate(String templateContent, String templateName) throws IOException {
        // Create a default templates directory if it doesn't exist
        File templateDir = new File("templates");
        if (!templateDir.exists()) {
            if (!templateDir.mkdirs()) {
                throw new IOException("Could not create templates directory: " + templateDir);
            }
        }

        Path templatePath = Paths.get(templateDir.getPath(), templateName);
        Files.write(templatePath, templateContent.getBytes(StandardCharsets.UTF_8));
        log.info("Template saved to: {}", templatePath);
    }

    /**
     * Renders HTML/XHTML content to PDF using custom options.
     */
    public static void renderHtmlToPdf(String htmlContent, OutputStream os, PdfOptions options) throws Exception {
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new IllegalArgumentException("HTML content is empty");
        }

        // Ensure well-formed XHTML and inject page CSS
        String xhtml = ensureXhtmlDocument(htmlContent);
        xhtml = injectPageCss(xhtml, options);

        ITextRenderer renderer = new ITextRenderer();
        configureFonts(renderer, options);

        if (options.getBaseUri() != null) {
            ITextUserAgent ua = new ITextUserAgent(renderer.getOutputDevice());
            ua.setSharedContext(renderer.getSharedContext());
            ua.setBaseURL(options.getBaseUri());
            renderer.getSharedContext().setUserAgentCallback(ua);
            renderer.setDocumentFromString(xhtml, options.getBaseUri());
        } else {
            renderer.setDocumentFromString(xhtml);
        }

        renderer.layout();

        // Create PDF with bookmarks if specified
        if (options.hasBookmarks()) {
            ByteArrayOutputStream tempOs = new ByteArrayOutputStream();
            renderer.createPDF(tempOs);
            tempOs.flush();

            // Create a reader for the generated PDF
            PdfReader reader = new PdfReader(tempOs.toByteArray());

            // Create a stamper to modify the PDF
            PdfStamper stamper = new PdfStamper(reader, os);

            // Add bookmarks
            addBookmarksToPdf(stamper, options.getBookmarks());

            // Close the stamper to finalize the PDF
            stamper.close();
            reader.close();
        } else {
            // No bookmarks, create PDF directly
            renderer.createPDF(os);
            os.flush();
        }
    }

    /**
     * Adds bookmarks to a PDF document.
     *
     * @param stamper the PdfStamper to modify
     * @param bookmarks the list of bookmarks to add
     */
    private static void addBookmarksToPdf(PdfStamper stamper, List<PdfOptions.Bookmark> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            return;
        }

        // Create the root outline
        PdfContentByte cb = stamper.getOverContent(1);
        PdfOutline root = new PdfOutline(cb.getRootOutline(), new PdfDestination(PdfDestination.FIT), "Root", false);

        // Add each bookmark
        for (PdfOptions.Bookmark bookmark : bookmarks) {
            addBookmarkToPdf(root, bookmark, stamper);
        }
    }

    /**
     * Recursively adds a bookmark and its children to a PDF document.
     *
     * @param parent the parent PdfOutline
     * @param bookmark the bookmark to add
     * @param stamper the PdfStamper to modify
     */
    private static void addBookmarkToPdf(PdfOutline parent, PdfOptions.Bookmark bookmark, PdfStamper stamper) {
        // Create destination (default to first page if not specified)
        String dest = bookmark.getDestination();
        int pageNum = 1;

        // Try to parse destination as page number
        if (dest != null && !dest.isEmpty()) {
            try {
                pageNum = Integer.parseInt(dest);
                if (pageNum < 1) pageNum = 1;
                if (pageNum > stamper.getReader().getNumberOfPages()) {
                    pageNum = stamper.getReader().getNumberOfPages();
                }
            } catch (NumberFormatException e) {
                // Not a page number, use as named destination
                // For simplicity, we'll default to first page in this example
                pageNum = 1;
            }
        }

        // Create the destination and action
        PdfDestination destination = new PdfDestination(PdfDestination.FIT);
        PdfAction action = PdfAction.gotoLocalPage(pageNum, destination, stamper.getWriter());

        // Create the outline entry with the action
        PdfOutline outline = new PdfOutline(parent, action, bookmark.getTitle());

        // Add children recursively
        for (PdfOptions.Bookmark child : bookmark.getChildren()) {
            addBookmarkToPdf(outline, child, stamper);
        }
    }

    /**
     * Renders a FreeMarker template directly to PDF.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @param os the output stream to write the PDF to
     * @param options custom PDF rendering options
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateToPdf(String templateName, Map<String, Object> dataModel,
                                           OutputStream os, PdfOptions options) throws Exception {
        String html = renderTemplateToHtml(templateName, dataModel);
        renderHtmlToPdf(html, os, options);
    }

    /**
     * Renders a FreeMarker template string directly to PDF.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param os the output stream to write the PDF to
     * @param options custom PDF rendering options
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateStringToPdf(String templateContent, String templateName,
                                                Map<String, Object> dataModel,
                                                OutputStream os, PdfOptions options) throws Exception {
        String html = renderTemplateStringToHtml(templateContent, templateName, dataModel);
        renderHtmlToPdf(html, os, options);
    }

    /**
     * Renders a FreeMarker template string directly to PDF with default options.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param os the output stream to write the PDF to
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateStringToPdf(String templateContent, String templateName,
                                                Map<String, Object> dataModel,
                                                OutputStream os) throws Exception {
        renderTemplateStringToPdf(templateContent, templateName, dataModel, os, new PdfOptions());
    }

    /**
     * Renders a FreeMarker template to a PDF file.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @param outputPath the path where the PDF file will be saved
     * @param options custom PDF rendering options
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateToPdfFile(String templateName, Map<String, Object> dataModel,
                                              String outputPath, PdfOptions options) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            renderTemplateToPdf(templateName, dataModel, fos, options);
            log.info("PDF created successfully at: {}", outputPath);
        }
    }

    /**
     * Renders a FreeMarker template to a PDF file with default options.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @param outputPath the path where the PDF file will be saved
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateToPdfFile(String templateName, Map<String, Object> dataModel,
                                              String outputPath) throws Exception {
        renderTemplateToPdfFile(templateName, dataModel, outputPath, new PdfOptions());
    }

    /**
     * Renders a FreeMarker template string to a PDF file.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param outputPath the path where the PDF file will be saved
     * @param options custom PDF rendering options
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateStringToPdfFile(String templateContent, String templateName,
                                                    Map<String, Object> dataModel,
                                                    String outputPath, PdfOptions options) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            renderTemplateStringToPdf(templateContent, templateName, dataModel, fos, options);
            log.info("PDF created successfully at: {}", outputPath);
        }
    }

    /**
     * Renders a FreeMarker template string to a PDF file with default options.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param outputPath the path where the PDF file will be saved
     * @throws Exception if an error occurs during rendering
     */
    public static void renderTemplateStringToPdfFile(String templateContent, String templateName,
                                                    Map<String, Object> dataModel,
                                                    String outputPath) throws Exception {
        renderTemplateStringToPdfFile(templateContent, templateName, dataModel, outputPath, new PdfOptions());
    }

    /**
     * Renders a FreeMarker template to a PDF and returns it as a byte array.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @param options custom PDF rendering options
     * @return byte array containing the PDF data
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderTemplateToPdfBytes(String templateName, Map<String, Object> dataModel,
                                                 PdfOptions options) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            renderTemplateToPdf(templateName, dataModel, baos, options);
            return baos.toByteArray();
        }
    }

    /**
     * Renders a FreeMarker template to a PDF and returns it as a byte array with default options.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @return byte array containing the PDF data
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderTemplateToPdfBytes(String templateName, Map<String, Object> dataModel) throws Exception {
        return renderTemplateToPdfBytes(templateName, dataModel, new PdfOptions());
    }

    /**
     * Renders a FreeMarker template to a PDF and returns it as a byte array asynchronously.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @param options custom PDF rendering options
     * @return a CompletableFuture that will complete with the PDF data as a byte array
     */
    public static CompletableFuture<byte[]> renderTemplateToPdfBytesAsync(String templateName, Map<String, Object> dataModel,
                                                                         PdfOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return renderTemplateToPdfBytes(templateName, dataModel, options);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Renders a FreeMarker template to a PDF and returns it as a byte array asynchronously with default options.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @return a CompletableFuture that will complete with the PDF data as a byte array
     */
    public static CompletableFuture<byte[]> renderTemplateToPdfBytesAsync(String templateName, Map<String, Object> dataModel) {
        return renderTemplateToPdfBytesAsync(templateName, dataModel, new PdfOptions());
    }

    /**
     * Renders a FreeMarker template string to a PDF and returns it as a byte array.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param options custom PDF rendering options
     * @return byte array containing the PDF data
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderTemplateStringToPdfBytes(String templateContent, String templateName,
                                                       Map<String, Object> dataModel,
                                                       PdfOptions options) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            renderTemplateStringToPdf(templateContent, templateName, dataModel, baos, options);
            return baos.toByteArray();
        }
    }

    /**
     * Renders a FreeMarker template string to a PDF and returns it as a byte array with default options.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @return byte array containing the PDF data
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderTemplateStringToPdfBytes(String templateContent, String templateName,
                                                       Map<String, Object> dataModel) throws Exception {
        return renderTemplateStringToPdfBytes(templateContent, templateName, dataModel, new PdfOptions());
    }

    /**
     * Renders a FreeMarker template string to a PDF and returns it as a byte array asynchronously.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param options custom PDF rendering options
     * @return a CompletableFuture that will complete with the PDF data as a byte array
     */
    public static CompletableFuture<byte[]> renderTemplateStringToPdfBytesAsync(String templateContent, String templateName,
                                                                               Map<String, Object> dataModel,
                                                                               PdfOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return renderTemplateStringToPdfBytes(templateContent, templateName, dataModel, options);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Renders a FreeMarker template string to a PDF and returns it as a byte array asynchronously with default options.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @return a CompletableFuture that will complete with the PDF data as a byte array
     */
    public static CompletableFuture<byte[]> renderTemplateStringToPdfBytesAsync(String templateContent, String templateName,
                                                                               Map<String, Object> dataModel) {
        return renderTemplateStringToPdfBytesAsync(templateContent, templateName, dataModel, new PdfOptions());
    }

    private static String ensureXhtmlDocument(String html) {
        String trimmed = html.trim().toLowerCase();
        if (!trimmed.startsWith("<!doctype")) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \n");
            sb.append(" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
            sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
            sb.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
            sb.append("</head><body>");
            sb.append(html);
            sb.append("</body></html>");
            return sb.toString();
        }
        return html;
    }

    private static String injectPageCss(String html, PdfOptions opts) {
        StringBuilder css = new StringBuilder();
        css.append("<style> @page { size: ")
                .append(opts.pageSize.name().toLowerCase())
                .append("; margin: ")
                .append(opts.marginTop).append("pt ")
                .append(opts.marginRight).append("pt ")
                .append(opts.marginBottom).append("pt ")
                .append(opts.marginLeft).append("pt; }");
        if (opts.getDefaultFont() != null) {
            css.append(" body { font-family: '")
                    .append(opts.getDefaultFont())
                    .append("'; }");
        }
        css.append(" </style>");

        int idx = html.indexOf("</head>");
        if (idx > -1) {
            return html.substring(0, idx) + css + html.substring(idx);
        }
        return css + html;
    }

    private static void configureFonts(ITextRenderer renderer, PdfOptions opts) {
        if (opts.getFontDir() != null) {
            try {
                org.xhtmlrenderer.pdf.ITextFontResolver fr = renderer.getFontResolver();
                File dir = new File(opts.getFontDir());
                if (dir.isDirectory()) {
                    for (File f : dir.listFiles()) {
                        String name = f.getName().toLowerCase();
                        if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                            fr.addFont(f.getAbsolutePath(), true);
                            log.debug("Loaded font: {}", f.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error loading fonts from {}", opts.getFontDir(), e);
            }
        }
    }

    /**
     * Options for PDF rendering.
     */
    public static class PdfOptions {
        private String baseUri;
        private String fontDir;
        private String defaultFont;
        private PageSize pageSize = PageSize.A4;
        private float marginTop = 36, marginRight = 36, marginBottom = 36, marginLeft = 36;

        // Watermark options
        private String watermarkText;
        private float watermarkOpacity = 0.3f;
        private int watermarkFontSize = 60;
        private int watermarkRotation = 45;
        private String watermarkColor = "#888888";

        // Encryption options
        private boolean encrypted = false;
        private String userPassword;
        private String ownerPassword;
        private boolean allowPrinting = true;
        private boolean allowCopy = true;
        private boolean allowModify = false;

        // Metadata options
        private String title;
        private String author;
        private String subject;
        private String keywords;
        private String creator = "TemplateRenderUtil";

        // Bookmark options
        private List<Bookmark> bookmarks = new ArrayList<>();

        /**
         * Represents a bookmark (outline entry) in the PDF.
         */
        public static class Bookmark {
            private String title;
            private int level;
            private String destination;
            private List<Bookmark> children = new ArrayList<>();

            public Bookmark(String title, int level, String destination) {
                this.title = title;
                this.level = level;
                this.destination = destination;
            }

            public String getTitle() { return title; }
            public int getLevel() { return level; }
            public String getDestination() { return destination; }
            public List<Bookmark> getChildren() { return children; }

            public Bookmark addChild(String title, String destination) {
                Bookmark child = new Bookmark(title, this.level + 1, destination);
                children.add(child);
                return child;
            }
        }

        public PdfOptions withBaseUri(String uri) { this.baseUri = uri; return this; }
        public PdfOptions withFontDirectory(String dir) { this.fontDir = dir; return this; }
        public PdfOptions withDefaultFont(String fontName) { this.defaultFont = fontName; return this; }
        public PdfOptions withPageSize(PageSize sz) { this.pageSize = sz; return this; }
        public PdfOptions withMargins(float top, float right, float bottom, float left) {
            this.marginTop = top; this.marginRight = right;
            this.marginBottom = bottom; this.marginLeft = left;
            return this;
        }

        /**
         * Adds a text watermark to the PDF.
         *
         * @param text the watermark text
         * @param opacity the opacity of the watermark (0.0-1.0)
         * @param fontSize the font size of the watermark
         * @param rotation the rotation angle in degrees
         * @param color the color of the watermark in HTML format (#RRGGBB)
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withWatermark(String text, float opacity, int fontSize, int rotation, String color) {
            this.watermarkText = text;
            this.watermarkOpacity = Math.max(0.0f, Math.min(1.0f, opacity)); // Clamp between 0 and 1
            this.watermarkFontSize = fontSize;
            this.watermarkRotation = rotation;
            this.watermarkColor = color;
            return this;
        }

        /**
         * Adds a simple text watermark to the PDF with default settings.
         *
         * @param text the watermark text
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withWatermark(String text) {
            return withWatermark(text, 0.3f, 60, 45, "#888888");
        }

        /**
         * Enables PDF encryption with the specified passwords and permissions.
         *
         * @param userPassword the user password (can be null for no user password)
         * @param ownerPassword the owner password
         * @param allowPrinting whether to allow printing
         * @param allowCopy whether to allow copying text and images
         * @param allowModify whether to allow document modification
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withEncryption(String userPassword, String ownerPassword,
                                        boolean allowPrinting, boolean allowCopy, boolean allowModify) {
            this.encrypted = true;
            this.userPassword = userPassword;
            this.ownerPassword = ownerPassword;
            this.allowPrinting = allowPrinting;
            this.allowCopy = allowCopy;
            this.allowModify = allowModify;
            return this;
        }

        /**
         * Enables PDF encryption with owner password only and default permissions.
         *
         * @param ownerPassword the owner password
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withEncryption(String ownerPassword) {
            return withEncryption(null, ownerPassword, true, true, false);
        }

        /**
         * Sets PDF document metadata.
         *
         * @param title the document title
         * @param author the document author
         * @param subject the document subject
         * @param keywords the document keywords
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withMetadata(String title, String author, String subject, String keywords) {
            this.title = title;
            this.author = author;
            this.subject = subject;
            this.keywords = keywords;
            return this;
        }

        /**
         * Adds a bookmark (outline entry) to the PDF.
         *
         * @param title the title of the bookmark
         * @param destination the destination anchor or page number
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withBookmark(String title, String destination) {
            bookmarks.add(new Bookmark(title, 1, destination));
            return this;
        }

        /**
         * Adds a bookmark (outline entry) to the PDF with a specific level.
         *
         * @param title the title of the bookmark
         * @param level the level of the bookmark (1 for top level)
         * @param destination the destination anchor or page number
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions withBookmark(String title, int level, String destination) {
            bookmarks.add(new Bookmark(title, level, destination));
            return this;
        }

        /**
         * Adds a child bookmark to the last added bookmark.
         *
         * @param title the title of the bookmark
         * @param destination the destination anchor or page number
         * @return this PdfOptions instance for method chaining
         * @throws IllegalStateException if no parent bookmark exists
         */
        public PdfOptions withChildBookmark(String title, String destination) {
            if (bookmarks.isEmpty()) {
                throw new IllegalStateException("Cannot add child bookmark without a parent bookmark");
            }

            Bookmark parent = bookmarks.get(bookmarks.size() - 1);
            parent.addChild(title, destination);
            return this;
        }

        /**
         * Clears all bookmarks.
         *
         * @return this PdfOptions instance for method chaining
         */
        public PdfOptions clearBookmarks() {
            bookmarks.clear();
            return this;
        }

        public enum PageSize { A4, LETTER, LEGAL, A3 }

        // getters for internal use
        private String getBaseUri() { return baseUri; }
        private String getFontDir() { return fontDir; }
        private String getDefaultFont() { return defaultFont; }
        private String getWatermarkText() { return watermarkText; }
        private boolean isEncrypted() { return encrypted; }
        private boolean hasMetadata() { return title != null || author != null || subject != null || keywords != null; }

        // Bookmark getters (public for testing)
        public boolean hasBookmarks() { return !bookmarks.isEmpty(); }
        public List<Bookmark> getBookmarks() { return bookmarks; }
    }

    /**
     * Renders HTML content to an image.
     *
     * @param htmlContent the HTML content to render
     * @param width the width of the image in pixels
     * @param height the height of the image in pixels
     * @param imageType the type of image to create (e.g., "png", "jpg")
     * @return the rendered image as a byte array
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderHtmlToImage(String htmlContent, int width, int height, String imageType) throws Exception {
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new IllegalArgumentException("HTML content is empty");
        }

        // Ensure well-formed XHTML
        String xhtml = ensureXhtmlDocument(htmlContent);

        // Create a temporary file for the HTML
        Path tempHtmlFile = Files.createTempFile("render_", ".html");
        Files.write(tempHtmlFile, xhtml.getBytes(StandardCharsets.UTF_8));

        try {
            // Create a URL for the temporary file
            String url = tempHtmlFile.toUri().toURL().toString();

            // Use Flying Saucer to render the HTML to an image
            Java2DRenderer renderer = new Java2DRenderer(url, width, height);
            BufferedImage image = renderer.getImage();

            // Convert the image to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, imageType, baos);
            return baos.toByteArray();
        } finally {
            // Clean up the temporary file
            Files.deleteIfExists(tempHtmlFile);
        }
    }

    /**
     * Renders a FreeMarker template to an image.
     *
     * @param templateName the name of the template file
     * @param dataModel the data model to use for rendering
     * @param width the width of the image in pixels
     * @param height the height of the image in pixels
     * @param imageType the type of image to create (e.g., "png", "jpg")
     * @return the rendered image as a byte array
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderTemplateToImage(String templateName, Map<String, Object> dataModel,
                                              int width, int height, String imageType) throws Exception {
        String html = renderTemplateToHtml(templateName, dataModel);
        return renderHtmlToImage(html, width, height, imageType);
    }

    /**
     * Renders a FreeMarker template string to an image.
     *
     * @param templateContent the template content as a string
     * @param templateName a name for the template (used for error reporting)
     * @param dataModel the data model to use for rendering
     * @param width the width of the image in pixels
     * @param height the height of the image in pixels
     * @param imageType the type of image to create (e.g., "png", "jpg")
     * @return the rendered image as a byte array
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderTemplateStringToImage(String templateContent, String templateName,
                                                   Map<String, Object> dataModel,
                                                   int width, int height, String imageType) throws Exception {
        String html = renderTemplateStringToHtml(templateContent, templateName, dataModel);
        return renderHtmlToImage(html, width, height, imageType);
    }

    /**
     * Renders HTML content to a PDF file.
     *
     * @param htmlContent the HTML content to render
     * @param outputPath the path where the PDF file will be saved
     * @param options custom PDF rendering options
     * @throws Exception if an error occurs during rendering
     */
    public static void renderHtmlToPdfFile(String htmlContent, String outputPath, PdfOptions options) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            renderHtmlToPdf(htmlContent, fos, options);
            log.info("PDF created successfully at: {}", outputPath);
        }
    }

    /**
     * Renders HTML content to a PDF file with default options.
     *
     * @param htmlContent the HTML content to render
     * @param outputPath the path where the PDF file will be saved
     * @throws Exception if an error occurs during rendering
     */
    public static void renderHtmlToPdfFile(String htmlContent, String outputPath) throws Exception {
        renderHtmlToPdfFile(htmlContent, outputPath, new PdfOptions());
    }

    /**
     * Renders HTML content to a PDF and returns it as a byte array.
     *
     * @param htmlContent the HTML content to render
     * @param options custom PDF rendering options
     * @return byte array containing the PDF data
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderHtmlToPdfBytes(String htmlContent, PdfOptions options) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            renderHtmlToPdf(htmlContent, baos, options);
            return baos.toByteArray();
        }
    }

    /**
     * Renders HTML content to a PDF and returns it as a byte array with default options.
     *
     * @param htmlContent the HTML content to render
     * @return byte array containing the PDF data
     * @throws Exception if an error occurs during rendering
     */
    public static byte[] renderHtmlToPdfBytes(String htmlContent) throws Exception {
        return renderHtmlToPdfBytes(htmlContent, new PdfOptions());
    }

    /**
     * Renders HTML content to a PDF and returns it as a byte array asynchronously.
     *
     * @param htmlContent the HTML content to render
     * @param options custom PDF rendering options
     * @return a CompletableFuture that will complete with the PDF data as a byte array
     */
    public static CompletableFuture<byte[]> renderHtmlToPdfBytesAsync(String htmlContent, PdfOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return renderHtmlToPdfBytes(htmlContent, options);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Renders HTML content to a PDF and returns it as a byte array asynchronously with default options.
     *
     * @param htmlContent the HTML content to render
     * @return a CompletableFuture that will complete with the PDF data as a byte array
     */
    public static CompletableFuture<byte[]> renderHtmlToPdfBytesAsync(String htmlContent) {
        return renderHtmlToPdfBytesAsync(htmlContent, new PdfOptions());
    }

    /**
     * Saves an image to a file.
     *
     * @param imageBytes the image bytes
     * @param outputPath the path where the image file will be saved
     * @throws IOException if the file cannot be written
     */
    public static void saveImageToFile(byte[] imageBytes, String outputPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(imageBytes);
            log.info("Image saved to: {}", outputPath);
        }
    }

    /**
     * Validates a FreeMarker template for syntax errors.
     *
     * @param templateContent the template content to validate
     * @return a list of validation errors, or an empty list if the template is valid
     */
    public static List<String> validateTemplate(String templateContent) {
        List<String> errors = new ArrayList<>();

        if (templateContent == null || templateContent.trim().isEmpty()) {
            errors.add("Template content is empty");
            return errors;
        }

        try {
            // Try to create a template from the content
            new Template("validation-template", new StringReader(templateContent), freemarkerConfig);
        } catch (Exception e) {
            // Add the error message to the list
            errors.add(e.getMessage());
        }

        return errors;
    }

    /**
     * Validates a FreeMarker template file for syntax errors.
     *
     * @param templateName the name of the template file to validate
     * @return a list of validation errors, or an empty list if the template is valid
     */
    public static List<String> validateTemplateFile(String templateName) {
        List<String> errors = new ArrayList<>();

        if (templateName == null || templateName.trim().isEmpty()) {
            errors.add("Template name is empty");
            return errors;
        }

        try {
            // Try to load the template
            freemarkerConfig.getTemplate(templateName);
        } catch (Exception e) {
            // Add the error message to the list
            errors.add(e.getMessage());
        }

        return errors;
    }
}
