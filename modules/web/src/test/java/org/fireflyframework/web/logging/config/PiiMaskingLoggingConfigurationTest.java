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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.web.logging.appender.PiiMaskingAppender;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.fireflyframework.web.logging.service.StdoutMaskingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test class for PiiMaskingLoggingConfiguration to verify automatic PII masking
 * is properly configured for all application logs.
 */
@ExtendWith(MockitoExtension.class)
public class PiiMaskingLoggingConfigurationTest {

    @Mock
    private ApplicationReadyEvent applicationReadyEvent;

    private PiiMaskingProperties properties;
    private PiiMaskingService piiMaskingService;
    private PiiMaskingLoggingConfiguration loggingConfiguration;
    private LoggerContext loggerContext;
    private Logger rootLogger;
    private ListAppender<ILoggingEvent> testAppender;
    private StdoutMaskingService stdoutMaskingService;

    @BeforeEach
    void setUp() {
        // Setup properties
        properties = new PiiMaskingProperties();
        properties.setEnabled(true);
        properties.setAutoMaskLogs(true);

        // Setup PII masking service
        piiMaskingService = new PiiMaskingService(properties);

        // Setup logging configuration
        loggingConfiguration = new PiiMaskingLoggingConfiguration(piiMaskingService, properties);

        // Setup Logback context
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        // Create a test appender to capture log messages
        testAppender = new ListAppender<>();
        testAppender.setContext(loggerContext);
        testAppender.start();

        // Add test appender to root logger
        rootLogger.addAppender(testAppender);

        // Setup stdout masking to prevent PII disclosure in System.out.println calls
        stdoutMaskingService = new StdoutMaskingService(piiMaskingService);
        stdoutMaskingService.enableStdoutMasking();
    }

    @AfterEach
    void tearDown() {
        // Clean up appenders
        rootLogger.detachAndStopAllAppenders();
        testAppender.stop();
        
        // Disable stdout masking and restore original System.out
        if (stdoutMaskingService != null) {
            stdoutMaskingService.disableStdoutMasking();
        }
    }

    @Test
    void testPiiMaskingConfigurationEnabled() {
        // Given: PII masking is enabled
        assertTrue(properties.isEnabled());
        assertTrue(properties.isAutoMaskLogs());

        // When: Application ready event is fired
        loggingConfiguration.onApplicationEvent(applicationReadyEvent);

        // Then: Root logger should have PiiMaskingAppender
        boolean hasPiiMaskingAppender = false;
        var appenderIterator = rootLogger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            if (appenderIterator.next() instanceof PiiMaskingAppender) {
                hasPiiMaskingAppender = true;
                break;
            }
        }
        assertTrue(hasPiiMaskingAppender, "Root logger should have PiiMaskingAppender");
    }

    @Test
    void testPiiMaskingConfigurationDisabled() {
        // Given: PII masking is disabled
        properties.setAutoMaskLogs(false);

        // When: Application ready event is fired
        loggingConfiguration.onApplicationEvent(applicationReadyEvent);

        // Then: Root logger should not have PiiMaskingAppender
        boolean hasPiiMaskingAppender = false;
        var appenderIterator = rootLogger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            if (appenderIterator.next() instanceof PiiMaskingAppender) {
                hasPiiMaskingAppender = true;
                break;
            }
        }
        assertFalse(hasPiiMaskingAppender, "Root logger should not have PiiMaskingAppender when disabled");
    }

    @Test
    void testAutomaticLogMasking() {
        // Given: PII masking is configured (this wraps existing appenders including testAppender)
        loggingConfiguration.onApplicationEvent(applicationReadyEvent);

        // Clear any existing events from setup
        testAppender.list.clear();

        // When: Log a message with PII data
        Logger testLogger = loggerContext.getLogger("test.logger");
        String messageWithPii = "User email is john.doe@example.com and phone is 555-123-4567";
        testLogger.info(messageWithPii);

        // Then: The wrapped testAppender should receive the masked events
        List<ILoggingEvent> logEvents = testAppender.list;
        assertFalse(logEvents.isEmpty(), "Should have captured log events");

        ILoggingEvent logEvent = logEvents.get(0);
        String formattedMessage = logEvent.getFormattedMessage();
        
        // Temporarily disable stdout masking to show comparison for verification
        // This demonstrates how tests can selectively disable masking when needed
        stdoutMaskingService.temporarilyDisableMasking();
        System.out.println("[DEBUG_LOG] COMPARISON - Original message: " + messageWithPii);
        System.out.println("[DEBUG_LOG] COMPARISON - Formatted message: " + formattedMessage);
        System.out.println("[DEBUG_LOG] PII masking service ready: " + piiMaskingService.isReady());
        
        // Debug: Test the service directly
        String directMasked = piiMaskingService.maskPiiData(messageWithPii);
        System.out.println("[DEBUG_LOG] COMPARISON - Direct service masking: " + directMasked);
        
        // Re-enable stdout masking
        stdoutMaskingService.reEnableMasking();
        
        // Now any PII in stdout will be masked
        System.out.println("[DEBUG_LOG] This message with PII: " + messageWithPii + " should be masked in stdout");

        // The message should be different from original (masked)
        assertNotEquals(messageWithPii, formattedMessage, "Message should be masked");
        
        // Should not contain the original PII data
        assertFalse(formattedMessage.contains("john.doe@example.com"), "Should not contain original email");
        assertFalse(formattedMessage.contains("555-123-4567"), "Should not contain original phone");
        
        // Should still contain the non-PII parts
        assertTrue(formattedMessage.contains("User email is"), "Should contain non-PII text");
        assertTrue(formattedMessage.contains("and phone is"), "Should contain non-PII text");
    }

    @Test
    void testMaskingServiceDisabled() {
        // Given: PII masking service is disabled
        properties.setEnabled(false);

        // When: Application ready event is fired
        loggingConfiguration.onApplicationEvent(applicationReadyEvent);

        // Then: Should not setup PII masking
        boolean hasPiiMaskingAppender = false;
        var appenderIterator = rootLogger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            if (appenderIterator.next() instanceof PiiMaskingAppender) {
                hasPiiMaskingAppender = true;
                break;
            }
        }
        assertFalse(hasPiiMaskingAppender, "Should not have PiiMaskingAppender when service is disabled");
    }

    @Test
    void testStdoutMaskingWithSystemOutPrintln() {
        // Given: Stdout masking is enabled (done in setUp)
        assertTrue(stdoutMaskingService.isMaskingActive(), "Stdout masking should be active");

        // When: Using System.out.println with PII data
        String messageWithPii = "Customer SSN: 123-45-6789 and email: customer@example.com";
        
        // Test stdout masking by capturing output to a test stream first
        java.io.ByteArrayOutputStream testOutput = new java.io.ByteArrayOutputStream();
        java.io.PrintStream testStream = new java.io.PrintStream(testOutput);
        java.io.PrintStream originalOut = System.out;
        
        try {
            // First, redirect System.out to our capture stream
            System.setOut(testStream);
            
            // Create a masking service that will capture the current System.out (our test stream)
            StdoutMaskingService testMaskingService = new StdoutMaskingService(piiMaskingService);
            testMaskingService.enableStdoutMasking();
            
            // Print message with PII - this should be masked
            System.out.println("[DEBUG_LOG] Message with PII: " + messageWithPii);
            System.out.flush();
            
            String capturedString = testOutput.toString();
            
            // Restore original stdout and disable test masking
            System.setOut(originalOut);
            testMaskingService.disableStdoutMasking();
            
            // Then: The output should be masked
            System.out.println("[DEBUG_LOG] VERIFICATION - Captured output: '" + capturedString + "'");
            System.out.println("[DEBUG_LOG] VERIFICATION - Original message: " + messageWithPii);
            
            // The captured output should not be empty
            assertFalse(capturedString.trim().isEmpty(), "Captured output should not be empty");
            
            // Should not contain original PII
            assertFalse(capturedString.contains("123-45-6789"), "Should not contain original SSN");
            assertFalse(capturedString.contains("customer@example.com"), "Should not contain original email");
            
            // Should contain non-PII parts and the DEBUG_LOG prefix
            assertTrue(capturedString.contains("[DEBUG_LOG]"), "Should contain DEBUG_LOG prefix");
            assertTrue(capturedString.contains("Message with PII:"), "Should contain non-PII text");
            
        } finally {
            // Ensure stdout is always restored
            System.setOut(originalOut);
            // Re-initialize stdout masking for other tests
            stdoutMaskingService = new StdoutMaskingService(piiMaskingService);
            stdoutMaskingService.enableStdoutMasking();
        }
    }

    @Test
    void testStdoutMaskingCanBeDisabledTemporarily() {
        // Given: Stdout masking is enabled
        assertTrue(stdoutMaskingService.isMaskingActive(), "Stdout masking should be active");

        // When: Temporarily disabling stdout masking
        stdoutMaskingService.temporarilyDisableMasking();
        
        // Then: Masking should be inactive
        assertFalse(stdoutMaskingService.isMaskingActive(), "Stdout masking should be inactive");
        
        // When: Re-enabling stdout masking
        stdoutMaskingService.reEnableMasking();
        
        // Then: Masking should be active again
        assertTrue(stdoutMaskingService.isMaskingActive(), "Stdout masking should be active again");
    }

    @Test
    void testErrorHandlingDoesNotBreakLogging() {
        // Given: A configuration that might throw an exception (simulated by null service)
        PiiMaskingLoggingConfiguration faultyConfiguration = 
            new PiiMaskingLoggingConfiguration(null, properties);

        // When: Application ready event is fired
        // Then: Should not throw exception
        assertDoesNotThrow(() -> {
            faultyConfiguration.onApplicationEvent(applicationReadyEvent);
        }, "Error in PII masking setup should not break application startup");

        // And logging should still work
        Logger testLogger = loggerContext.getLogger("test.logger");
        assertDoesNotThrow(() -> {
            testLogger.info("This should still work");
        }, "Logging should still work even if PII masking setup fails");
    }

    @Test
    void testStdoutMaskingEnabledByDefaultInProperties() {
        // Given: Default properties configuration
        PiiMaskingProperties defaultProperties = new PiiMaskingProperties();

        // Then: Stdout masking should be enabled by default
        assertTrue(defaultProperties.isEnableStdoutMasking(), 
            "Stdout masking should be enabled by default for all Spring applications");
        
        // And: PII masking should also be enabled by default
        assertTrue(defaultProperties.isEnabled(), 
            "PII masking should be enabled by default");
        
        // And: Auto mask logs should be enabled by default
        assertTrue(defaultProperties.isAutoMaskLogs(), 
            "Auto mask logs should be enabled by default");
    }

    @Test
    void testStdoutMaskingCanBeDisabledViaProperties() {
        // Given: Properties with stdout masking disabled
        properties.setEnableStdoutMasking(false);

        // When: Creating a new stdout masking service (simulating auto-configuration)
        StdoutMaskingService disabledStdoutService = new StdoutMaskingService(piiMaskingService);

        // Then: Service should be created but not activated by default
        assertNotNull(disabledStdoutService, "StdoutMaskingService should be created");
        
        // Verify property is set correctly
        assertFalse(properties.isEnableStdoutMasking(), 
            "enableStdoutMasking property should be false when disabled");
    }
}