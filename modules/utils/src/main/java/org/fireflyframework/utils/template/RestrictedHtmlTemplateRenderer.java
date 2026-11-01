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

import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.core.TemplateProcessingTracer;
import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.SimpleObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Isolated JSON-only FreeMarker configuration and closed XHTML fragment profile. No global legacy
 * utility configuration is consulted. The caller must isolate expression-heavy work when a hard
 * worker-lifetime limit is needed: element tracing cannot interrupt an expression's Java code.
 */
public final class RestrictedHtmlTemplateRenderer {
    public static final String PROFILE = "RESTRICTED_XHTML_V1";
    private static final Set<String> TAGS = Set.of("section", "article", "main", "header", "footer", "div", "span",
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "strong", "em", "b", "i", "u", "small", "sub", "sup",
            "table", "caption", "thead", "tbody", "tfoot", "tr", "th", "td", "colgroup", "col", "ul", "ol", "li",
            "dl", "dt", "dd", "br", "hr", "blockquote", "pre", "code");
    private final RestrictedTemplateLimits limits;
    private final Configuration configuration;

    public RestrictedHtmlTemplateRenderer() {
        this(RestrictedTemplateLimits.defaults());
    }

    public RestrictedHtmlTemplateRenderer(RestrictedTemplateLimits limits) {
        this.limits = java.util.Objects.requireNonNull(limits);
        var cfg = new Configuration(Configuration.VERSION_2_3_35);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.US);
        cfg.setNumberFormat("computer");
        cfg.setBooleanFormat("c");
        cfg.setOutputFormat(HTMLOutputFormat.INSTANCE);
        cfg.setAutoEscapingPolicy(Configuration.FORCE_AUTO_ESCAPING_POLICY);
        cfg.setAPIBuiltinEnabled(false);
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        var wrapper = new SimpleObjectWrapper(Configuration.VERSION_2_3_35);
        wrapper.setDOMNodeSupport(false);
        wrapper.setJythonSupport(false);
        wrapper.writeProtect();
        cfg.setObjectWrapper(wrapper);
        cfg.setTemplateLoader(new TemplateLoader() {
            public Object findTemplateSource(String name) { return null; }
            public long getLastModified(Object source) { return -1; }
            public Reader getReader(Object source, String encoding) { throw new IllegalStateException("Template loading is disabled"); }
            public void closeTemplateSource(Object source) { }
        });
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setLocalizedLookup(false);
        this.configuration = cfg;
    }

    public RestrictedTemplateResult render(String source, Map<String, ?> model) {
        try {
            if (source == null || source.isBlank() || !unicode(source)
                    || source.getBytes(StandardCharsets.UTF_8).length > limits.templateBytes()) {
                return failure("UNSUPPORTED_TEMPLATE");
            }
            var budget = new BudgetImpl();
            Object copied = copy(model, 0, budget);
            if (!(copied instanceof Map<?, ?>)) return failure("UNSUPPORTED_MODEL");
            var template = new Template("restricted-published-template", new StringReader(source), configuration);
            var writer = new BoundedWriterImpl(limits.outputBytes());
            Environment environment = template.createProcessingEnvironment(copied, writer);
            environment.setTemplateProcessingTracer(budget);
            environment.process();
            String html = writer.result();
            if (html.isBlank()) return failure("EMPTY_OUTPUT");
            validateHtml(html);
            return new RestrictedTemplateResult(true, html, null);
        } catch (Exception error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof RenderLimit) return failure("RENDER_LIMIT");
                if (cause instanceof UnsupportedModel) return failure("UNSUPPORTED_MODEL");
                if (cause instanceof InvalidHtml) return failure("INVALID_HTML");
            }
            return failure("RENDER_FAILED");
        }
    }

    private Object copy(Object value, int depth, BudgetImpl budget) {
        if (++budget.nodes > limits.modelNodes() || depth > limits.depth()) throw new UnsupportedModel();
        budget.deadline();
        if (value == null || value instanceof Boolean) return value;
        if (value instanceof String text) {
            if (text.length() > limits.stringChars() || !unicode(text)) throw new UnsupportedModel();
            return text;
        }
        if (value.getClass() == BigDecimal.class) {
            var number = ((BigDecimal) value).stripTrailingZeros();
            long integer = Math.max(1L, (long) number.precision() - number.scale());
            long fraction = Math.max(0L, number.scale());
            if (integer + (fraction == 0 ? 0 : fraction + 1) + (number.signum() < 0 ? 1 : 0) > 128) throw new UnsupportedModel();
            return number;
        }
        if (value.getClass() == BigInteger.class) {
            if (((BigInteger) value).abs().bitLength() > 426 || value.toString().length() > 128) throw new UnsupportedModel();
            return value;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > limits.stringChars() || !unicode(key)) throw new UnsupportedModel();
                result.put(key, copy(entry.getValue(), depth + 1, budget));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<>();
            for (var item : list) result.add(copy(item, depth + 1, budget));
            return result;
        }
        throw new UnsupportedModel();
    }

    private void validateHtml(String html) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", limits.depth() + 1);
        var parser = factory.newDocumentBuilder();
        parser.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException error) throws SAXParseException { throw error; }
            @Override public void fatalError(SAXParseException error) throws SAXParseException { throw error; }
        });
        var root = parser.parse(new InputSource(new StringReader("<render-root>" + html + "</render-root>"))).getDocumentElement();
        record Visit(Node node, int depth) { }
        var pending = new ArrayDeque<Visit>();
        pending.add(new Visit(root, 0));
        int nodes = 0;
        while (!pending.isEmpty()) {
            var visit = pending.removeFirst();
            if (++nodes > 20_000 || visit.depth() > limits.depth()) throw new RenderLimit();
            Node node = visit.node();
            if (node instanceof Element element && node != root) {
                if (!TAGS.contains(element.getTagName())) throw new InvalidHtml();
                for (int i = 0; i < element.getAttributes().getLength(); i++) {
                    Node attribute = element.getAttributes().item(i);
                    String name = attribute.getNodeName(), value = attribute.getNodeValue();
                    if ((name.equals("class") || name.equals("id")) && value.matches("[A-Za-z0-9_ -]{1,200}")) continue;
                    if ((name.equals("colspan") || name.equals("rowspan") || name.equals("span")) && value.matches("[1-9][0-9]?")) continue;
                    if (name.equals("scope") && Set.of("row", "col", "rowgroup", "colgroup").contains(value)) continue;
                    throw new InvalidHtml();
                }
            } else if (node != root && node.getNodeType() != Node.TEXT_NODE) throw new InvalidHtml();
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) pending.addLast(new Visit(child, visit.depth() + 1));
        }
        if (root.getTextContent().codePoints().noneMatch(c -> !Character.isWhitespace(c)
                && !Character.isSpaceChar(c) && Character.getType(c) != Character.FORMAT
                && Character.getType(c) != Character.NON_SPACING_MARK
                && Character.getType(c) != Character.COMBINING_SPACING_MARK
                && Character.getType(c) != Character.ENCLOSING_MARK)) throw new InvalidHtml();
    }

    private static boolean unicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(value)) return false;
        }
        return true;
    }
    private static RestrictedTemplateResult failure(String code) { return new RestrictedTemplateResult(false, null, code); }
    private static final class RenderLimit extends RuntimeException { }
    private static final class UnsupportedModel extends RuntimeException { }
    private static final class InvalidHtml extends RuntimeException { }

    private final class BudgetImpl implements TemplateProcessingTracer {
        private final long end = System.nanoTime() + limits.renderMillis() * 1_000_000;
        private int nodes, steps, depth;
        void deadline() { if (System.nanoTime() > end) throw new RenderLimit(); }
        public void enterElement(Environment environment, TracedElement element) {
            if (++steps > limits.elementSteps() || ++depth > limits.depth()) throw new RenderLimit();
            deadline();
        }
        public void exitElement(Environment environment, TracedElement element) { depth--; }
    }

    private static final class BoundedWriterImpl extends Writer {
        private final int limit;
        private final StringBuilder text = new StringBuilder();
        private int bytes;
        private boolean high;
        BoundedWriterImpl(int limit) { this.limit = limit; }
        @Override public void write(char[] value, int offset, int length) {
            for (int i = offset; i < offset + length; i++) {
                char character = value[i];
                if (high) {
                    if (!Character.isLowSurrogate(character)) throw new RenderLimit();
                    bytes += 4; high = false;
                } else if (Character.isHighSurrogate(character)) high = true;
                else if (Character.isLowSurrogate(character)) throw new RenderLimit();
                else bytes += character <= 0x7f ? 1 : character <= 0x7ff ? 2 : 3;
                if (bytes > limit) throw new RenderLimit();
                text.append(character);
            }
        }
        String result() { if (high) throw new RenderLimit(); return text.toString(); }
        @Override public void flush() { }
        @Override public void close() { }
    }
}
