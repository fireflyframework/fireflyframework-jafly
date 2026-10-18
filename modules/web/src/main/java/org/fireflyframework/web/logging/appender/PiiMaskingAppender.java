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
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.slf4j.Marker;

import java.util.Iterator;

/**
 * A Logback appender that automatically masks PII (Personally Identifiable Information)
 * in log messages before delegating to other appenders.
 * 
 * This appender acts as a wrapper/filter that:
 * 1. Intercepts all log messages
 * 2. Applies PII masking to the message content
 * 3. Forwards the masked message to configured child appenders
 * 
 * Features:
 * - Automatic PII detection and masking using PiiMaskingService
 * - Support for multiple child appenders
 * - Thread-safe operation
 * - Graceful error handling to prevent log disruption
 * - Performance optimized with lazy masking
 * 
 * Configuration example in logback-spring.xml:
 * <pre>
 * &lt;appender name="PII_MASKING_CONSOLE" class="org.fireflyframework.web.logging.appender.PiiMaskingAppender"&gt;
 *   &lt;appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"&gt;
 *     &lt;encoder&gt;
 *       &lt;pattern&gt;%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
 *     &lt;/encoder&gt;
 *   &lt;/appender&gt;
 * &lt;/appender&gt;
 * </pre>
 */
public class PiiMaskingAppender extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    private final AppenderAttachableImpl<ILoggingEvent> appenderAttachableImpl = new AppenderAttachableImpl<>();
    private PiiMaskingService piiMaskingService;

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
            // Create a new logging event with masked message
            ILoggingEvent maskedEvent = createMaskedEvent(event);
            
            // Forward the masked event to all attached appenders
            appenderAttachableImpl.appendLoopOnAppenders(maskedEvent);
            
        } catch (Exception e) {
            // In case of any error, log the original event to prevent log loss
            addError("Error during PII masking, forwarding original message", e);
            appenderAttachableImpl.appendLoopOnAppenders(event);
        }
    }

    /**
     * Creates a wrapper around the logging event that masks PII in the formatted message.
     * 
     * @param originalEvent the original logging event
     * @return a wrapped logging event with masked message
     */
    private ILoggingEvent createMaskedEvent(ILoggingEvent originalEvent) {
        if (piiMaskingService == null || !piiMaskingService.isReady()) {
            // If PII masking is not available, return original event
            return originalEvent;
        }

        String originalMessage = originalEvent.getFormattedMessage();
        if (originalMessage == null || originalMessage.isEmpty()) {
            return originalEvent;
        }

        // Apply PII masking to the message
        String maskedMessage = piiMaskingService.maskPiiData(originalMessage);
        
        // If no masking occurred, return original event to avoid unnecessary object creation
        if (originalMessage.equals(maskedMessage)) {
            return originalEvent;
        }

        // Return a wrapper that provides the masked message
        return new MaskedLoggingEventWrapper(originalEvent, maskedMessage);
    }

    /**
     * A wrapper for ILoggingEvent that provides a masked formatted message
     * while delegating all other calls to the original event.
     */
    private static class MaskedLoggingEventWrapper implements ILoggingEvent {
        private final ILoggingEvent originalEvent;
        private final String maskedMessage;

        public MaskedLoggingEventWrapper(ILoggingEvent originalEvent, String maskedMessage) {
            this.originalEvent = originalEvent;
            this.maskedMessage = maskedMessage;
        }

        @Override
        public String getFormattedMessage() {
            return maskedMessage;
        }

        // Delegate all other methods to the original event
        @Override
        public String getThreadName() { return originalEvent.getThreadName(); }
        
        @Override
        public ch.qos.logback.classic.Level getLevel() { return originalEvent.getLevel(); }
        
        @Override
        public String getMessage() { return originalEvent.getMessage(); }
        
        @Override
        public Object[] getArgumentArray() { return originalEvent.getArgumentArray(); }
        
        @Override
        public String getLoggerName() { return originalEvent.getLoggerName(); }
        
        @Override
        public ch.qos.logback.classic.spi.LoggerContextVO getLoggerContextVO() { 
            return originalEvent.getLoggerContextVO(); 
        }
        
        @Override
        public ch.qos.logback.classic.spi.IThrowableProxy getThrowableProxy() { 
            return originalEvent.getThrowableProxy(); 
        }
        
        @Override
        public StackTraceElement[] getCallerData() { return originalEvent.getCallerData(); }
        
        @Override
        public boolean hasCallerData() { return originalEvent.hasCallerData(); }
        
        @SuppressWarnings("deprecation")
        @Override
        public Marker getMarker() { return originalEvent.getMarker(); }
        
        @Override
        public java.util.List<Marker> getMarkerList() { return originalEvent.getMarkerList(); }
        
        @Override
        public java.util.Map<String, String> getMDCPropertyMap() { 
            return originalEvent.getMDCPropertyMap(); 
        }
        
        @SuppressWarnings("deprecation")
        @Override
        public java.util.Map<String, String> getMdc() { return originalEvent.getMdc(); }
        
        @Override
        public long getTimeStamp() { return originalEvent.getTimeStamp(); }
        
        @Override
        public int getNanoseconds() { return originalEvent.getNanoseconds(); }
        
        @Override
        public long getSequenceNumber() { return originalEvent.getSequenceNumber(); }
        
        @Override
        public java.util.List<org.slf4j.event.KeyValuePair> getKeyValuePairs() { 
            return originalEvent.getKeyValuePairs(); 
        }
        
        @Override
        public void prepareForDeferredProcessing() { originalEvent.prepareForDeferredProcessing(); }
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