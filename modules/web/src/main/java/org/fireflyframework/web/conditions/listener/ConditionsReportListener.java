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

package org.fireflyframework.web.conditions.listener;

import org.fireflyframework.web.conditions.service.ConditionsReportService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

/**
 * Application listener that generates the conditions evaluation report
 * when the application is ready.
 *
 * <p>This listener is automatically registered when the conditions report
 * feature is enabled via configuration.</p>
 *
 * <p>It runs with the LOWEST_PRECEDENCE to ensure it executes AFTER all
 * other application startup tasks have completed, providing a complete
 * view of all auto-configurations.</p>
 */
public class ConditionsReportListener implements ApplicationListener<ApplicationReadyEvent>, Ordered {

    private final ConditionsReportService reportService;

    public ConditionsReportListener(ConditionsReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();

        // Execute the report generation in a separate thread to avoid blocking
        // the application startup completion
        new Thread(() -> {
            try {
                // Small delay to ensure all startup logs are flushed
                Thread.sleep(100);
                reportService.generateReport(context);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "conditions-report-generator").start();
    }

    @Override
    public int getOrder() {
        // Run last to ensure all auto-configurations are complete
        return Ordered.LOWEST_PRECEDENCE;
    }
}

