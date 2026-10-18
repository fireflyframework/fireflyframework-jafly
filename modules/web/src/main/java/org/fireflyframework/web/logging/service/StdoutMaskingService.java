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


package org.fireflyframework.web.logging.service;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that provides stdout masking capability by intercepting System.out.println calls
 * and applying PII masking to the output.
 * 
 * This service can be used in testing environments to prevent PII disclosure through
 * direct stdout calls while still allowing tests to show masked vs original content
 * for verification purposes.
 */
public class StdoutMaskingService {

    private final PiiMaskingService piiMaskingService;
    private final PrintStream originalOut;
    private final AtomicBoolean maskingEnabled = new AtomicBoolean(true);
    private MaskingPrintStream maskingPrintStream;

    public StdoutMaskingService(PiiMaskingService piiMaskingService) {
        this.piiMaskingService = piiMaskingService;
        this.originalOut = System.out;
    }

    /**
     * Enables stdout masking by replacing System.out with a masking wrapper.
     */
    public void enableStdoutMasking() {
        if (piiMaskingService != null && piiMaskingService.isReady()) {
            maskingPrintStream = new MaskingPrintStream(originalOut, piiMaskingService, maskingEnabled);
            System.setOut(maskingPrintStream);
        }
    }

    /**
     * Disables stdout masking and restores the original System.out.
     */
    public void disableStdoutMasking() {
        if (maskingPrintStream != null) {
            System.setOut(originalOut);
            maskingPrintStream = null;
        }
    }

    /**
     * Temporarily disables masking for the current thread.
     * Useful when tests need to show original vs masked content for verification.
     */
    public void temporarilyDisableMasking() {
        maskingEnabled.set(false);
    }

    /**
     * Re-enables masking for the current thread.
     */
    public void reEnableMasking() {
        maskingEnabled.set(true);
    }

    /**
     * Checks if stdout masking is currently active.
     */
    public boolean isMaskingActive() {
        return maskingPrintStream != null && maskingEnabled.get();
    }

    /**
     * A PrintStream wrapper that applies PII masking to output.
     */
    private static class MaskingPrintStream extends PrintStream {
        private final PrintStream originalStream;
        private final PiiMaskingService piiMaskingService;
        private final AtomicBoolean maskingEnabled;

        public MaskingPrintStream(PrintStream originalStream, PiiMaskingService piiMaskingService, 
                                 AtomicBoolean maskingEnabled) {
            super(originalStream, true);
            this.originalStream = originalStream;
            this.piiMaskingService = piiMaskingService;
            this.maskingEnabled = maskingEnabled;
        }

        @Override
        public void println(String text) {
            if (text != null && maskingEnabled.get() && piiMaskingService.isReady()) {
                // Apply masking to the text
                String maskedText = piiMaskingService.maskPiiData(text);
                originalStream.println(maskedText);
            } else {
                originalStream.println(text);
            }
        }

        @Override
        public void print(String text) {
            if (text != null && maskingEnabled.get() && piiMaskingService.isReady()) {
                // Apply masking to the text
                String maskedText = piiMaskingService.maskPiiData(text);
                originalStream.print(maskedText);
            } else {
                originalStream.print(text);
            }
        }

        @Override
        public void println(Object obj) {
            if (obj != null) {
                println(obj.toString());
            } else {
                originalStream.println(obj);
            }
        }

        @Override
        public void print(Object obj) {
            if (obj != null) {
                print(obj.toString());
            } else {
                originalStream.print(obj);
            }
        }

        // Delegate other methods to the original stream
        @Override
        public void flush() {
            originalStream.flush();
        }

        @Override
        public void close() {
            originalStream.close();
        }
    }
}