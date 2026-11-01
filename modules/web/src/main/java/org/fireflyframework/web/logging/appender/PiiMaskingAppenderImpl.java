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


package org.fireflyframework.web.logging.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Masks configured PII patterns in a detached event snapshot before delegating to other appenders.
 * Missing or incomplete masking, unsupported values and unsafe source access omit the event.
 * An explicitly disabled masking service preserves the original unprotected event behavior.
 * 
 * Configuration example in logback-spring.xml:
 * <pre>
 * &lt;appender name="PII_MASKING_CONSOLE" class="org.fireflyframework.web.logging.appender.PiiMaskingAppenderImpl"&gt;
 *   &lt;appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"&gt;
 *     &lt;encoder&gt;
 *       &lt;pattern&gt;%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
 *     &lt;/encoder&gt;
 *   &lt;/appender&gt;
 * &lt;/appender&gt;
 * </pre>
 */
public class PiiMaskingAppenderImpl extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    private final AppenderAttachableImpl<ILoggingEvent> appenderAttachableImpl = new AppenderAttachableImpl<>();
    private volatile PiiMaskingService piiMaskingService;

    /**
     * Sets the PII masking service to use for masking log messages.
     * This is typically injected by Spring during auto-configuration.
     * 
     * @param piiMaskingService the PII masking service
     */
    public void setPiiMaskingService(PiiMaskingService piiMaskingService) {
        this.piiMaskingService = piiMaskingService;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        try {
            ILoggingEvent maskedEvent = createMaskedEvent(event);
            appenderAttachableImpl.appendLoopOnAppenders(maskedEvent);
        } catch (Exception e) {
            addWarn("PII masking could not safely snapshot a log event; event omitted");
        }
    }

    /**
     * Detaches every encoder-visible event channel before handing it to another appender.
     * 
     * @param originalEvent the original logging event
     * @return a masked snapshot, or the original event only for an explicit disabled policy
     */
    private ILoggingEvent createMaskedEvent(ILoggingEvent originalEvent) {
        PiiMaskingService service = Objects.requireNonNull(piiMaskingService);
        Map<String, Object> stats = Objects.requireNonNull(service.getMaskingStats());
        Object enabled = stats.get("enabled");
        if (Boolean.FALSE.equals(enabled)) {
            return originalEvent;
        }
        if (!Boolean.TRUE.equals(enabled) || !service.isReady()) {
            throw new IllegalStateException("PII masking unavailable");
        }
        return new MaskedLoggingEvent(Objects.requireNonNull(originalEvent), new EventMasker(service));
    }

    /**
     * No original event, argument, marker or throwable references escape this snapshot.
     */
    private static final class MaskedLoggingEvent implements ILoggingEvent {
        private final String message, threadName, loggerName, contextName;
        private final Level level;
        private final Map<String, String> mdc, contextProperties;
        private final long timestamp, sequence, contextBirth;
        private final int nanoseconds;
        private final List<KeyValuePair> pairs;
        private final List<Marker> markers;
        private final IThrowableProxy throwable;
        private final StackTraceElement[] caller;

        private MaskedLoggingEvent(ILoggingEvent event, EventMasker masker) {
            String template = masker.text(event.getMessage());
            Object[] originalArguments = event.getArgumentArray();
            Object[] arguments = originalArguments == null ? null : new Object[originalArguments.length];
            if (arguments != null) {
                for (int i = 0; i < arguments.length; i++) arguments[i] = masker.scalar(originalArguments[i]);
            }
            message = masker.text(MessageFormatter.arrayFormat(template, arguments, null).getMessage());
            threadName = masker.text(event.getThreadName());
            loggerName = masker.text(event.getLoggerName());
            level = Objects.requireNonNull(event.getLevel());
            timestamp = event.getTimeStamp();
            nanoseconds = event.getNanoseconds();
            sequence = event.getSequenceNumber();
            mdc = masker.map(event.getMDCPropertyMap());
            LoggerContextVO context = Objects.requireNonNull(event.getLoggerContextVO());
            contextName = masker.text(context.getName());
            contextProperties = masker.map(context.getPropertyMap());
            contextBirth = context.getBirthTime();
            var values = new ArrayList<KeyValuePair>();
            List<KeyValuePair> originalPairs = event.getKeyValuePairs();
            if (originalPairs != null) {
                for (KeyValuePair pair : originalPairs) {
                    values.add(new KeyValuePair(Objects.requireNonNull(masker.text(pair.key)), masker.scalar(pair.value)));
                }
            }
            pairs = List.copyOf(values);
            var labels = new ArrayList<Marker>();
            List<Marker> originalMarkers = event.getMarkerList();
            if (originalMarkers != null) {
                for (Marker marker : originalMarkers) labels.add(masker.marker(marker, 0));
            }
            markers = List.copyOf(labels);
            throwable = masker.throwable(event.getThrowableProxy(), 0);
            caller = event.hasCallerData() ? masker.frames(event.getCallerData()) : new StackTraceElement[0];
        }

        @Override public String getFormattedMessage() { return message; }
        @Override public String getMessage() { return message; }
        @Override public Object[] getArgumentArray() { return null; }
        @Override public String getThreadName() { return threadName; }
        @Override public Level getLevel() { return level; }
        @Override public String getLoggerName() { return loggerName; }
        @Override public LoggerContextVO getLoggerContextVO() {
            return new LoggerContextVO(contextName, contextProperties, contextBirth);
        }
        @Override public IThrowableProxy getThrowableProxy() { return throwable; }
        @Override public StackTraceElement[] getCallerData() { return caller.clone(); }
        @Override public boolean hasCallerData() { return caller.length > 0; }
        @Override public Marker getMarker() { return markers.isEmpty() ? null : markers.getFirst(); }
        @Override public List<Marker> getMarkerList() { return markers; }
        @Override public Map<String, String> getMDCPropertyMap() { return mdc; }
        @Override public Map<String, String> getMdc() { return mdc; }
        @Override public long getTimeStamp() { return timestamp; }
        @Override public int getNanoseconds() { return nanoseconds; }
        @Override public long getSequenceNumber() { return sequence; }
        @Override public List<KeyValuePair> getKeyValuePairs() { return pairs; }
        @Override public void prepareForDeferredProcessing() { }
    }

    private static final class EventMasker {
        private final PiiMaskingService service;
        private final Set<Object> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

        private EventMasker(PiiMaskingService service) { this.service = service; }

        private String text(String value) {
            return value == null ? null : Objects.requireNonNull(service.maskLogData(value));
        }

        private Object scalar(Object value) {
            if (value == null) return null;
            if (value instanceof String string) return text(string);
            if (value instanceof Boolean) return value;
            if (value instanceof Character character) return text(character.toString());
            if (value instanceof Enum<?> enumeration) return text(enumeration.name());
            Class<?> type = value.getClass();
            if (type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                    || type == Float.class || type == Double.class || type == BigInteger.class || type == BigDecimal.class) {
                if (value instanceof Float floatValue && !Float.isFinite(floatValue)
                        || value instanceof Double doubleValue && !Double.isFinite(doubleValue)) throw new IllegalStateException();
                String original = value.toString();
                String masked = text(original);
                return original.equals(masked) ? value : masked;
            }
            throw new IllegalStateException("Unsupported log value");
        }

        private Map<String, String> map(Map<String, String> values) {
            if (values == null) return Map.of();
            var copy = new LinkedHashMap<String, String>();
            values.forEach((key, value) -> {
                String maskedKey = Objects.requireNonNull(text(key));
                if (copy.containsKey(maskedKey)) throw new IllegalStateException();
                copy.put(maskedKey, text(value));
            });
            return Collections.unmodifiableMap(copy);
        }

        private void enter(Object value, int depth) {
            if (depth >= 32 || !visiting.add(value)) throw new IllegalStateException("Unsafe log graph");
        }

        private Marker marker(Marker value, int depth) {
            enter(Objects.requireNonNull(value), depth);
            try {
                var children = new ArrayList<Marker>();
                Iterator<Marker> iterator = value.iterator();
                while (iterator.hasNext()) children.add(marker(iterator.next(), depth + 1));
                return new MaskedMarker(Objects.requireNonNull(text(value.getName())), List.copyOf(children));
            } finally {
                visiting.remove(value);
            }
        }

        private StackTraceElement[] frames(StackTraceElement[] source) {
            if (source == null) return new StackTraceElement[0];
            var copy = new StackTraceElement[source.length];
            for (int i = 0; i < copy.length; i++) {
                StackTraceElement frame = Objects.requireNonNull(source[i]);
                copy[i] = new StackTraceElement(text(frame.getClassLoaderName()), text(frame.getModuleName()),
                        text(frame.getModuleVersion()), Objects.requireNonNull(text(frame.getClassName())),
                        Objects.requireNonNull(text(frame.getMethodName())), text(frame.getFileName()), frame.getLineNumber());
            }
            return copy;
        }

        private IThrowableProxy throwable(IThrowableProxy value, int depth) {
            if (value == null) return null;
            enter(value, depth);
            try {
                StackTraceElementProxy[] sourceFrames = value.getStackTraceElementProxyArray();
                var elements = new StackTraceElement[sourceFrames == null ? 0 : sourceFrames.length];
                for (int i = 0; i < elements.length; i++) elements[i] = sourceFrames[i].getStackTraceElement();
                var suppressed = new ArrayList<IThrowableProxy>();
                IThrowableProxy[] sourceSuppressed = value.getSuppressed();
                if (sourceSuppressed != null) {
                    for (IThrowableProxy item : sourceSuppressed) suppressed.add(throwable(Objects.requireNonNull(item), depth + 1));
                }
                return new MaskedThrowable(text(value.getClassName()), text(value.getMessage()),
                        text(value.getOverridingMessage()), frames(elements), value.getCommonFrames(),
                        throwable(value.getCause(), depth + 1), suppressed.toArray(IThrowableProxy[]::new), value.isCyclic());
            } finally {
                visiting.remove(value);
            }
        }
    }

    private record MaskedMarker(String name, List<Marker> children) implements Marker {
        private static final long serialVersionUID = 1L;
        @Override public String getName() { return name; }
        @Override public void add(Marker reference) { throw new UnsupportedOperationException("Immutable masked marker"); }
        @Override public boolean remove(Marker reference) { throw new UnsupportedOperationException("Immutable masked marker"); }
        @Override public boolean hasChildren() { return !children.isEmpty(); }
        @Override public boolean hasReferences() { return !children.isEmpty(); }
        @Override public Iterator<Marker> iterator() { return children.iterator(); }
        @Override public boolean contains(Marker other) { return other != null && contains(other.getName()); }
        @Override public boolean contains(String other) {
            return name.equals(other) || children.stream().anyMatch(child -> child.contains(other));
        }
    }

    private record MaskedThrowable(String className, String message, String overridingMessage,
                                   StackTraceElement[] frames, int commonFrames, IThrowableProxy cause,
                                   IThrowableProxy[] suppressed, boolean cyclic) implements IThrowableProxy {
        @Override public String getClassName() { return className; }
        @Override public String getMessage() { return message; }
        @Override public String getOverridingMessage() { return overridingMessage; }
        @Override public StackTraceElementProxy[] getStackTraceElementProxyArray() {
            var copy = new StackTraceElementProxy[frames.length];
            for (int i = 0; i < copy.length; i++) copy[i] = new StackTraceElementProxy(frames[i]);
            return copy;
        }
        @Override public int getCommonFrames() { return commonFrames; }
        @Override public IThrowableProxy getCause() { return cause; }
        @Override public IThrowableProxy[] getSuppressed() { return suppressed.clone(); }
        @Override public boolean isCyclic() { return cyclic; }
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        appenderAttachableImpl.detachAndStopAllAppenders();
    }

    // AppenderAttachable implementation methods

    @Override
    public void addAppender(ch.qos.logback.core.Appender<ILoggingEvent> newAppender) {
        appenderAttachableImpl.addAppender(newAppender);
    }

    @Override
    public Iterator<ch.qos.logback.core.Appender<ILoggingEvent>> iteratorForAppenders() {
        return appenderAttachableImpl.iteratorForAppenders();
    }

    @Override
    public ch.qos.logback.core.Appender<ILoggingEvent> getAppender(String name) {
        return appenderAttachableImpl.getAppender(name);
    }

    @Override
    public boolean isAttached(ch.qos.logback.core.Appender<ILoggingEvent> appender) {
        return appenderAttachableImpl.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        appenderAttachableImpl.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(ch.qos.logback.core.Appender<ILoggingEvent> appender) {
        return appenderAttachableImpl.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
        return appenderAttachableImpl.detachAppender(name);
    }
}
