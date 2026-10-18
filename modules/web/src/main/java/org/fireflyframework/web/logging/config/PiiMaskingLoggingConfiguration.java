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


package org.fireflyframework.web.logging.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.fireflyframework.web.logging.appender.PiiMaskingAppender;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Configuration component that automatically sets up PII masking for all application logs.
 * This component intercepts the logging system after Spring Boot initialization and wraps
 * existing appenders with PiiMaskingAppender to provide automatic PII data masking.
 * 
 * Features:
 * - Automatic detection and wrapping of existing Logback appenders
 * - Integration with PiiMaskingService for consistent masking behavior
 * - Configurable enable/disable through properties
 * - Non-intrusive setup that preserves existing logging configuration
 * - Graceful error handling to prevent logging system disruption
 */
@Component
@ConditionalOnProperty(name = "pii-masking.auto-mask-logs", havingValue = "true", matchIfMissing = true)
public class PiiMaskingLoggingConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    private final PiiMaskingService piiMaskingService;
    private final PiiMaskingProperties properties;

    public PiiMaskingLoggingConfiguration(PiiMaskingService piiMaskingService, PiiMaskingProperties properties) {
        this.piiMaskingService = piiMaskingService;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled() || !properties.isAutoMaskLogs()) {
            return;
        }

        try {
            setupPiiMaskingForAllLoggers();
        } catch (Exception e) {
            // Log the error but don't fail the application startup
            org.slf4j.Logger logger = LoggerFactory.getLogger(PiiMaskingLoggingConfiguration.class);
            logger.error("Failed to setup automatic PII masking for logs. Logging will continue without PII masking.", e);
        }
    }

    /**
     * Sets up PII masking by wrapping existing appenders with PiiMaskingAppender.
     * This method modifies the Logback configuration at runtime to intercept log messages.
     */
    private void setupPiiMaskingForAllLoggers() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        
        // Get all existing appenders from the root logger
        List<Appender<ILoggingEvent>> existingAppenders = new ArrayList<>();
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
        
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            // Skip if it's already a PiiMaskingAppender to avoid double-wrapping
            if (!(appender instanceof PiiMaskingAppender)) {
                existingAppenders.add(appender);
            }
        }

        // If we have existing appenders, wrap them with PII masking
        if (!existingAppenders.isEmpty()) {
            // Create a new PiiMaskingAppender that will wrap all existing appenders
            PiiMaskingAppender piiMaskingAppender = new PiiMaskingAppender();
            piiMaskingAppender.setName("PII_MASKING_WRAPPER");
            piiMaskingAppender.setContext(loggerContext);
            piiMaskingAppender.setPiiMaskingService(piiMaskingService);

            // Add all existing appenders as children of the PiiMaskingAppender
            for (Appender<ILoggingEvent> existingAppender : existingAppenders) {
                // Detach from root logger
                rootLogger.detachAppender(existingAppender);
                // Attach to PiiMaskingAppender
                piiMaskingAppender.addAppender(existingAppender);
            }

            // Start the PiiMaskingAppender
            piiMaskingAppender.start();

            // Attach the PiiMaskingAppender to the root logger
            rootLogger.addAppender(piiMaskingAppender);

            org.slf4j.Logger logger = LoggerFactory.getLogger(PiiMaskingLoggingConfiguration.class);
            logger.info("PII masking enabled for all application logs. Wrapped {} existing appenders.", 
                       existingAppenders.size());
        }
    }
}