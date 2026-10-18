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

package org.fireflyframework.web.conditions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Conditions Evaluation Report.
 * 
 * <p>This report shows which Spring Boot auto-configurations were activated
 * and which were not, organized by technology categories (Web, Databases, 
 * Messaging, Security, etc.) with detailed explanations.</p>
 * 
 * <p>Example configuration:</p>
 * <pre>
 * firefly:
 *   web:
 *     conditions-report:
 *     enabled: true              # Enable the report
 *     show-negative: true        # Show non-activated conditions
 *     show-details: true         # Show detailed explanations
 *     summary-only: false        # false = detailed, true = summary only
 *     use-colors: true           # Use ANSI colors in console output
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "firefly.web.conditions-report")
public class ConditionsReportProperties {

    /**
     * Whether the conditions report is enabled.
     * Default: false (disabled by default)
     */
    private boolean enabled = false;

    /**
     * Whether to show conditions that did not match (negative matches).
     * Default: true
     */
    private boolean showNegative = true;

    /**
     * Whether to show detailed explanations for each condition.
     * Default: true
     */
    private boolean showDetails = true;

    /**
     * Whether to show only a summary instead of detailed report.
     * Default: false (show detailed report)
     */
    private boolean summaryOnly = false;

    /**
     * Whether to use ANSI colors in console output.
     * Default: true
     */
    private boolean useColors = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShowNegative() {
        return showNegative;
    }

    public void setShowNegative(boolean showNegative) {
        this.showNegative = showNegative;
    }

    public boolean isShowDetails() {
        return showDetails;
    }

    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
    }

    public boolean isSummaryOnly() {
        return summaryOnly;
    }

    public void setSummaryOnly(boolean summaryOnly) {
        this.summaryOnly = summaryOnly;
    }

    public boolean isUseColors() {
        return useColors;
    }

    public void setUseColors(boolean useColors) {
        this.useColors = useColors;
    }
}

