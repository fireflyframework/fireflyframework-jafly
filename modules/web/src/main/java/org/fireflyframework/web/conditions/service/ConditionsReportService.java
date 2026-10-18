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

package org.fireflyframework.web.conditions.service;

import org.fireflyframework.web.conditions.config.ConditionsReportProperties;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service that generates a professional conditions evaluation report.
 * 
 * <p>This service analyzes Spring Boot's auto-configuration conditions and generates
 * a formatted report showing which configurations were activated and why, organized
 * by technology categories.</p>
 */
@Service
public class ConditionsReportService {

    private final ConditionsReportProperties properties;

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    public ConditionsReportService(ConditionsReportProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates and prints the conditions evaluation report.
     *
     * @param context the application context
     */
    public void generateReport(ConfigurableApplicationContext context) {
        ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
        
        Map<String, List<ConditionEntry>> categorizedConditions = categorizeConditions(report);
        
        if (properties.isSummaryOnly()) {
            printSummary(categorizedConditions);
        } else {
            printDetailedReport(categorizedConditions);
        }
    }

    /**
     * Categorizes conditions by technology type.
     */
    private Map<String, List<ConditionEntry>> categorizeConditions(ConditionEvaluationReport report) {
        Map<String, List<ConditionEntry>> categorized = new LinkedHashMap<>();

        report.getConditionAndOutcomesBySource().forEach((source, conditionAndOutcomes) -> {
            boolean matched = conditionAndOutcomes.isFullMatch();

            if (!matched && !properties.isShowNegative()) {
                return; // Skip non-matched if not showing negative
            }

            String category = determineCategory(source);
            String description = getDescription(source);

            // Collect all conditions and outcomes for detailed analysis
            List<ConditionInfo> conditions = new ArrayList<>();
            conditionAndOutcomes.forEach(conditionAndOutcome -> {
                String conditionClass = conditionAndOutcome.getCondition().getClass().getSimpleName();
                ConditionOutcome outcome = conditionAndOutcome.getOutcome();
                conditions.add(new ConditionInfo(conditionClass, outcome));
            });

            ConditionEntry entry = new ConditionEntry(
                source,
                matched,
                description,
                conditions
            );

            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
        });

        // Sort entries within each category
        categorized.values().forEach(list ->
            list.sort(Comparator.comparing(ConditionEntry::getClassName))
        );

        return categorized;
    }

    /**
     * Determines the category for a given auto-configuration class.
     */
    private String determineCategory(String className) {
        String lowerClassName = className.toLowerCase();

        // Firefly custom configurations (check first for priority)
        if (className.contains("org.fireflyframework")) {
            return "Firefly Custom";
        }

        // Web & HTTP
        if (lowerClassName.contains("web") || lowerClassName.contains("servlet") || lowerClassName.contains("reactive")
            || lowerClassName.contains("netty") || lowerClassName.contains("tomcat") || lowerClassName.contains("jetty")
            || lowerClassName.contains("undertow") || lowerClassName.contains("http")) {
            return "Web & HTTP";
        }

        // Databases & Data Access
        if (lowerClassName.contains("data") || lowerClassName.contains("jdbc") || lowerClassName.contains("r2dbc")
            || lowerClassName.contains("jpa") || lowerClassName.contains("mongo") || lowerClassName.contains("redis")
            || lowerClassName.contains("cassandra") || lowerClassName.contains("elasticsearch")
            || lowerClassName.contains("hibernate") || lowerClassName.contains("datasource")
            || lowerClassName.contains("flyway") || lowerClassName.contains("liquibase")
            || lowerClassName.contains("neo4j") || lowerClassName.contains("couchbase")) {
            return "Databases & Data Access";
        }

        // Security & Authentication
        if (lowerClassName.contains("security") || lowerClassName.contains("oauth") || lowerClassName.contains("jwt")
            || lowerClassName.contains("saml") || lowerClassName.contains("ldap")) {
            return "Security & Authentication";
        }

        // Caching
        if (lowerClassName.contains("cache") || lowerClassName.contains("caffeine")
            || lowerClassName.contains("hazelcast") || lowerClassName.contains("ehcache")) {
            return "Caching";
        }

        // Messaging & Events
        if (lowerClassName.contains("kafka") || lowerClassName.contains("rabbit") || lowerClassName.contains("jms")
            || lowerClassName.contains("messaging") || lowerClassName.contains("amqp")
            || lowerClassName.contains("activemq") || lowerClassName.contains("artemis")
            || lowerClassName.contains("pulsar")) {
            return "Messaging & Events";
        }

        // Observability & Monitoring
        if (lowerClassName.contains("actuator") || lowerClassName.contains("metrics") || lowerClassName.contains("tracing")
            || lowerClassName.contains("micrometer") || lowerClassName.contains("prometheus")
            || lowerClassName.contains("zipkin") || lowerClassName.contains("jaeger")
            || lowerClassName.contains("observation") || lowerClassName.contains("health")) {
            return "Observability & Monitoring";
        }

        // Serialization & JSON
        if (lowerClassName.contains("jackson") || lowerClassName.contains("json") || lowerClassName.contains("gson")
            || lowerClassName.contains("xml") || lowerClassName.contains("protobuf")) {
            return "Serialization & JSON";
        }

        // Validation
        if (lowerClassName.contains("validation") || lowerClassName.contains("validator")) {
            return "Validation";
        }

        // API Documentation
        if (lowerClassName.contains("openapi") || lowerClassName.contains("swagger") || lowerClassName.contains("springdoc")) {
            return "API Documentation";
        }

        // Logging
        if (lowerClassName.contains("logging") || lowerClassName.contains("log4j") || lowerClassName.contains("logback")
            || lowerClassName.contains("slf4j")) {
            return "Logging";
        }

        // Task Scheduling
        if (lowerClassName.contains("scheduling") || lowerClassName.contains("quartz") || lowerClassName.contains("task")) {
            return "Task Scheduling";
        }

        // Cloud & Distributed
        if (lowerClassName.contains("cloud") || lowerClassName.contains("eureka") || lowerClassName.contains("consul")
            || lowerClassName.contains("zookeeper") || lowerClassName.contains("config")) {
            return "Cloud & Distributed";
        }

        return "Other";
    }

    /**
     * Gets a human-readable description for an auto-configuration class.
     */
    private String getDescription(String className) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String lowerSimpleName = simpleName.toLowerCase();

        // Firefly custom configurations
        if (className.contains("org.fireflyframework.web.openapi.OpenAPIAutoConfiguration")) {
            return "OpenAPI/Swagger documentation configuration";
        } else if (className.contains("org.fireflyframework.web.configuration.ExceptionHandlerAutoConfiguration")) {
            return "Global exception handler for standardized error responses";
        } else if (className.contains("org.fireflyframework.web.idempotency.config.IdempotencyAutoConfiguration")) {
            return "HTTP idempotency support using X-Idempotency-Key header";
        } else if (className.contains("org.fireflyframework.web.idempotency.config.IdempotencyOpenAPIAutoConfiguration")) {
            return "OpenAPI documentation for idempotency features";
        } else if (className.contains("org.fireflyframework.web.logging.config.PiiMaskingAutoConfiguration")) {
            return "PII (Personally Identifiable Information) masking in logs";
        } else if (className.contains("org.fireflyframework.web.logging.filter.HttpRequestLoggingWebFilter")) {
            return "HTTP request/response logging filter";
        } else if (className.contains("org.fireflyframework.web.conditions.config.ConditionsReportAutoConfiguration")) {
            return "Conditions evaluation report (this feature)";
        }

        // Web & HTTP
        if (lowerSimpleName.contains("webflux")) {
            return "Spring WebFlux reactive web framework";
        } else if (lowerSimpleName.contains("netty")) {
            return "Netty reactive HTTP server";
        } else if (lowerSimpleName.contains("tomcat")) {
            return "Apache Tomcat servlet container";
        } else if (lowerSimpleName.contains("jetty")) {
            return "Eclipse Jetty web server";
        } else if (lowerSimpleName.contains("undertow")) {
            return "Undertow web server";
        } else if (lowerSimpleName.contains("servlet")) {
            return "Servlet-based web application support";
        } else if (lowerSimpleName.contains("reactive")) {
            return "Reactive programming support";
        }

        // Databases & Data Access
        else if (lowerSimpleName.contains("r2dbc")) {
            return "Reactive Relational Database Connectivity (R2DBC)";
        } else if (lowerSimpleName.contains("jdbc")) {
            return "JDBC database connectivity";
        } else if (lowerSimpleName.contains("jpa")) {
            return "Java Persistence API (JPA) / Hibernate";
        } else if (lowerSimpleName.contains("hibernate")) {
            return "Hibernate ORM framework";
        } else if (lowerSimpleName.contains("datasource")) {
            return "Database connection pool configuration";
        } else if (lowerSimpleName.contains("flyway")) {
            return "Flyway database migration tool";
        } else if (lowerSimpleName.contains("liquibase")) {
            return "Liquibase database migration tool";
        } else if (lowerSimpleName.contains("redis")) {
            return "Redis in-memory data store";
        } else if (lowerSimpleName.contains("mongo")) {
            return "MongoDB NoSQL database";
        } else if (lowerSimpleName.contains("cassandra")) {
            return "Apache Cassandra distributed database";
        } else if (lowerSimpleName.contains("elasticsearch")) {
            return "Elasticsearch search and analytics engine";
        } else if (lowerSimpleName.contains("neo4j")) {
            return "Neo4j graph database";
        }

        // Security & Authentication
        else if (lowerSimpleName.contains("oauth2")) {
            return "OAuth 2.0 authentication and authorization";
        } else if (lowerSimpleName.contains("security")) {
            return "Spring Security authentication and authorization";
        } else if (lowerSimpleName.contains("jwt")) {
            return "JSON Web Token (JWT) support";
        } else if (lowerSimpleName.contains("saml")) {
            return "SAML 2.0 authentication";
        } else if (lowerSimpleName.contains("ldap")) {
            return "LDAP authentication and directory services";
        }

        // Caching
        else if (lowerSimpleName.contains("caffeine")) {
            return "Caffeine high-performance in-memory cache";
        } else if (lowerSimpleName.contains("hazelcast")) {
            return "Hazelcast distributed cache";
        } else if (lowerSimpleName.contains("ehcache")) {
            return "Ehcache caching provider";
        } else if (lowerSimpleName.contains("cache")) {
            return "Spring caching abstraction";
        }

        // Messaging & Events
        else if (lowerSimpleName.contains("kafka")) {
            return "Apache Kafka distributed streaming platform";
        } else if (lowerSimpleName.contains("rabbit")) {
            return "RabbitMQ message broker (AMQP)";
        } else if (lowerSimpleName.contains("activemq")) {
            return "Apache ActiveMQ message broker";
        } else if (lowerSimpleName.contains("artemis")) {
            return "Apache ActiveMQ Artemis message broker";
        } else if (lowerSimpleName.contains("jms")) {
            return "Java Message Service (JMS)";
        } else if (lowerSimpleName.contains("amqp")) {
            return "Advanced Message Queuing Protocol (AMQP)";
        }

        // Observability & Monitoring
        else if (lowerSimpleName.contains("actuator")) {
            return "Spring Boot Actuator monitoring endpoints";
        } else if (lowerSimpleName.contains("metrics")) {
            return "Micrometer metrics collection and export";
        } else if (lowerSimpleName.contains("prometheus")) {
            return "Prometheus metrics exporter";
        } else if (lowerSimpleName.contains("tracing")) {
            return "Distributed tracing (OpenTelemetry/Zipkin/Jaeger)";
        } else if (lowerSimpleName.contains("zipkin")) {
            return "Zipkin distributed tracing";
        } else if (lowerSimpleName.contains("jaeger")) {
            return "Jaeger distributed tracing";
        } else if (lowerSimpleName.contains("observation")) {
            return "Spring Observability API";
        } else if (lowerSimpleName.contains("health")) {
            return "Health check endpoints";
        }

        // Serialization & JSON
        else if (lowerSimpleName.contains("jackson")) {
            return "Jackson JSON/XML serialization";
        } else if (lowerSimpleName.contains("gson")) {
            return "Google Gson JSON serialization";
        } else if (lowerSimpleName.contains("protobuf")) {
            return "Protocol Buffers serialization";
        }

        // Validation
        else if (lowerSimpleName.contains("validation")) {
            return "Bean Validation (JSR-303/JSR-380)";
        }

        // API Documentation
        else if (lowerSimpleName.contains("openapi") || lowerSimpleName.contains("springdoc")) {
            return "OpenAPI 3.0 / Swagger documentation";
        }

        // Logging
        else if (lowerSimpleName.contains("logback")) {
            return "Logback logging framework";
        } else if (lowerSimpleName.contains("log4j")) {
            return "Log4j logging framework";
        }

        // Task Scheduling
        else if (lowerSimpleName.contains("quartz")) {
            return "Quartz job scheduling";
        } else if (lowerSimpleName.contains("scheduling")) {
            return "Spring task scheduling";
        }

        // Cloud & Distributed
        else if (lowerSimpleName.contains("eureka")) {
            return "Netflix Eureka service discovery";
        } else if (lowerSimpleName.contains("consul")) {
            return "HashiCorp Consul service mesh";
        } else if (lowerSimpleName.contains("config")) {
            return "Spring Cloud Config server/client";
        }

        // Default: clean up the class name
        return simpleName.replace("AutoConfiguration", "").replace("Configuration", "");
    }

    /**
     * Prints a summary report.
     */
    private void printSummary(Map<String, List<ConditionEntry>> categorized) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n");
        sb.append(line("="));
        sb.append(centered("SPRING BOOT CONDITIONS EVALUATION REPORT - SUMMARY"));
        sb.append(line("="));
        sb.append("\n");
        
        int totalMatched = 0;
        int totalNotMatched = 0;
        
        for (Map.Entry<String, List<ConditionEntry>> entry : categorized.entrySet()) {
            long matched = entry.getValue().stream().filter(ConditionEntry::isMatched).count();
            long notMatched = entry.getValue().size() - matched;
            
            totalMatched += matched;
            totalNotMatched += notMatched;
            
            sb.append(color(BOLD + CYAN, String.format("%-35s", entry.getKey())));
            sb.append(color(GREEN, String.format(" %3d active", matched)));
            if (notMatched > 0) {
                sb.append(color(GRAY, String.format(" | %3d inactive", notMatched)));
            }
            sb.append("\n");
        }
        
        sb.append("\n");
        sb.append(line("-"));
        sb.append(color(BOLD, String.format("TOTAL: %d active, %d inactive\n", totalMatched, totalNotMatched)));
        sb.append(line("="));
        
        System.out.println(sb.toString());
    }

    /**
     * Prints a detailed report.
     */
    private void printDetailedReport(Map<String, List<ConditionEntry>> categorized) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n");
        sb.append(line("="));
        sb.append(centered("SPRING BOOT CONDITIONS EVALUATION REPORT"));
        sb.append(line("="));
        sb.append("\n");
        
        int totalMatched = 0;
        int totalNotMatched = 0;
        
        for (Map.Entry<String, List<ConditionEntry>> categoryEntry : categorized.entrySet()) {
            String category = categoryEntry.getKey();
            List<ConditionEntry> entries = categoryEntry.getValue();
            
            long matched = entries.stream().filter(ConditionEntry::isMatched).count();
            long notMatched = entries.size() - matched;
            
            totalMatched += matched;
            totalNotMatched += notMatched;
            
            // Category header
            sb.append(color(BOLD + CYAN, "\n" + category.toUpperCase()));
            sb.append(color(GRAY, String.format(" (%d active, %d inactive)", matched, notMatched)));
            sb.append("\n");
            sb.append(line("-"));
            
            for (ConditionEntry entry : entries) {
                String status = entry.isMatched()
                    ? color(GREEN, "[ACTIVE]  ")
                    : color(RED, "[INACTIVE]");

                String className = entry.getSimpleName();
                sb.append(String.format("%s %s\n", status, color(BOLD, className)));

                if (properties.isShowDetails()) {
                    sb.append(color(GRAY, "           " + entry.getDescription() + "\n"));

                    // Show condition types used
                    if (!entry.getConditions().isEmpty()) {
                        Set<String> conditionTypes = entry.getConditions().stream()
                            .map(ConditionInfo::getConditionType)
                            .collect(Collectors.toSet());
                        sb.append(color(CYAN, "           Conditions: " + String.join(", ", conditionTypes) + "\n"));
                    }

                    if (!entry.isMatched()) {
                        // Show reasons for each failed condition
                        for (ConditionInfo condition : entry.getConditions()) {
                            if (condition.getOutcome() != null && !condition.getOutcome().isMatch()) {
                                String reason = condition.getOutcome().getMessage();
                                if (reason != null && !reason.isEmpty()) {
                                    String cleanReason = cleanReasonMessage(reason);
                                    sb.append(color(YELLOW, "           • " + condition.getConditionType() + ": " + cleanReason + "\n"));
                                }
                            }
                        }

                        // Provide actionable advice
                        String advice = getAdvice(entry);
                        if (advice != null && !advice.isEmpty()) {
                            sb.append(color(BLUE, "           💡 Tip: " + advice + "\n"));
                        }
                    }
                }
            }
        }
        
        sb.append("\n");
        sb.append(line("="));
        sb.append(color(BOLD, String.format("SUMMARY: %d configurations active, %d inactive\n", totalMatched, totalNotMatched)));
        sb.append(line("="));

        // Add condition types statistics
        printConditionStatistics(sb, categorized);

        // Add helpful tips section
        printHelpfulTips(sb);

        sb.append("\n");

        System.out.println(sb.toString());
    }

    /**
     * Prints statistics about condition types used.
     */
    private void printConditionStatistics(StringBuilder sb, Map<String, List<ConditionEntry>> categorized) {
        sb.append("\n");
        sb.append(color(BOLD + CYAN, "CONDITION TYPES ANALYSIS\n"));
        sb.append(line("-"));

        Map<String, Integer> conditionTypeCounts = new LinkedHashMap<>();
        Map<String, Integer> conditionTypeMatched = new LinkedHashMap<>();

        for (List<ConditionEntry> entries : categorized.values()) {
            for (ConditionEntry entry : entries) {
                for (ConditionInfo condition : entry.getConditions()) {
                    String type = condition.getConditionType();
                    conditionTypeCounts.merge(type, 1, Integer::sum);
                    if (condition.getOutcome() != null && condition.getOutcome().isMatch()) {
                        conditionTypeMatched.merge(type, 1, Integer::sum);
                    }
                }
            }
        }

        // Sort by count descending
        conditionTypeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String type = entry.getKey();
                int total = entry.getValue();
                int matched = conditionTypeMatched.getOrDefault(type, 0);
                int failed = total - matched;

                String typeDescription = getConditionTypeDescription(type);
                sb.append(String.format("  %s%-25s%s: %s%3d matched%s, %s%3d failed%s - %s\n",
                    color(BOLD, ""),
                    type,
                    RESET,
                    color(GREEN, ""),
                    matched,
                    RESET,
                    color(RED, ""),
                    failed,
                    RESET,
                    color(GRAY, typeDescription)
                ));
            });
    }

    /**
     * Prints helpful tips about Spring Boot conditions.
     */
    private void printHelpfulTips(StringBuilder sb) {
        sb.append("\n");
        sb.append(color(BOLD + CYAN, "UNDERSTANDING SPRING BOOT CONDITIONS\n"));
        sb.append(line("-"));

        sb.append(color(BOLD, "Common Condition Types:\n"));
        sb.append(color(GRAY, "  • OnClassCondition") + " - Checks if specific classes are on the classpath\n");
        sb.append(color(GRAY, "    Use: @ConditionalOnClass") + " when you want to activate only if a library is present\n");
        sb.append(color(GRAY, "    Fix: Add the required dependency to your build file\n\n"));

        sb.append(color(GRAY, "  • OnPropertyCondition") + " - Checks if properties are set with specific values\n");
        sb.append(color(GRAY, "    Use: @ConditionalOnProperty") + " for feature flags and configuration-based activation\n");
        sb.append(color(GRAY, "    Fix: Set the property in application.yml or application.properties\n\n"));

        sb.append(color(GRAY, "  • OnBeanCondition") + " - Checks if specific beans exist in the context\n");
        sb.append(color(GRAY, "    Use: @ConditionalOnBean/@ConditionalOnMissingBean") + " for conditional bean creation\n");
        sb.append(color(GRAY, "    Fix: Ensure the required bean is defined or remove the dependency\n\n"));

        sb.append(color(GRAY, "  • OnWebApplicationCondition") + " - Checks the type of web application\n");
        sb.append(color(GRAY, "    Use: @ConditionalOnWebApplication") + " for web-specific configurations\n");
        sb.append(color(GRAY, "    Fix: Ensure you have a web starter (servlet or reactive)\n\n"));

        sb.append(color(BOLD, "Best Practices:\n"));
        sb.append(color(YELLOW, "  ✓ Use @ConditionalOnClass") + " for optional dependencies\n");
        sb.append(color(YELLOW, "  ✓ Use @ConditionalOnProperty") + " for feature toggles\n");
        sb.append(color(YELLOW, "  ✓ Use @ConditionalOnMissingBean") + " to allow user overrides\n");
        sb.append(color(YELLOW, "  ✓ Combine conditions") + " with @ConditionalOnExpression for complex logic\n");
        sb.append(color(YELLOW, "  ✓ Order matters") + " - use @AutoConfigureAfter/@AutoConfigureBefore\n");
    }

    private String color(String colorCode, String text) {
        if (!properties.isUseColors()) {
            return text;
        }
        return colorCode + text + RESET;
    }

    private String line(String character) {
        return character.repeat(80) + "\n";
    }

    private String centered(String text) {
        int padding = (80 - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + color(BOLD + BLUE, text) + "\n";
    }

    /**
     * Gets a description for a condition type.
     */
    private String getConditionTypeDescription(String conditionType) {
        return switch (conditionType) {
            case "OnClassCondition" -> "Checks if classes are present on classpath";
            case "OnPropertyCondition" -> "Checks if properties match expected values";
            case "OnBeanCondition" -> "Checks if beans exist or are missing";
            case "OnWebApplicationCondition" -> "Checks web application type (servlet/reactive)";
            case "OnResourceCondition" -> "Checks if resources exist";
            case "OnExpressionCondition" -> "Evaluates SpEL expression";
            case "OnCloudPlatformCondition" -> "Checks if running on cloud platform";
            case "OnJavaCondition" -> "Checks Java version";
            case "OnJndiCondition" -> "Checks JNDI availability";
            case "OnWarDeploymentCondition" -> "Checks if deployed as WAR";
            case "OnEnabledResourceChainCondition" -> "Checks resource chain configuration";
            default -> "Custom condition";
        };
    }

    /**
     * Cleans up condition reason messages for better readability.
     */
    private String cleanReasonMessage(String reason) {
        if (reason == null || reason.isEmpty()) {
            return reason;
        }

        // Remove excessive whitespace
        String cleaned = reason.trim().replaceAll("\\s+", " ");

        // Truncate very long messages
        if (cleaned.length() > 150) {
            cleaned = cleaned.substring(0, 147) + "...";
        }

        return cleaned;
    }

    /**
     * Provides actionable advice based on the failed conditions.
     */
    private String getAdvice(ConditionEntry entry) {
        List<ConditionInfo> conditions = entry.getConditions();
        if (conditions.isEmpty()) {
            return null;
        }

        StringBuilder advice = new StringBuilder();
        Set<String> conditionTypes = conditions.stream()
            .map(ConditionInfo::getConditionType)
            .collect(Collectors.toSet());

        String className = entry.getClassName().toLowerCase();

        // Analyze condition types and provide specific advice
        if (conditionTypes.contains("OnClassCondition")) {
            // Extract missing classes from outcome messages
            Set<String> missingClasses = new HashSet<>();
            for (ConditionInfo condition : conditions) {
                if ("OnClassCondition".equals(condition.getConditionType()) &&
                    condition.getOutcome() != null && !condition.getOutcome().isMatch()) {
                    String message = condition.getOutcome().getMessage();
                    if (message != null && message.contains("did not find")) {
                        // Try to extract class names
                        Pattern pattern = Pattern.compile("@ConditionalOnClass did not find: '([^']+)'");
                        Matcher matcher = pattern.matcher(message);
                        while (matcher.find()) {
                            missingClasses.add(matcher.group(1));
                        }
                    }
                }
            }

            if (!missingClasses.isEmpty()) {
                advice.append("Missing classes: ").append(String.join(", ", missingClasses)).append(". ");
                advice.append("Add dependency to pom.xml/build.gradle. ");
            } else {
                advice.append("Required classes not found on classpath. Add the dependency. ");
            }
        }

        if (conditionTypes.contains("OnPropertyCondition")) {
            // Extract property names from the outcome messages
            Set<String> properties = new HashSet<>();
            for (ConditionInfo condition : conditions) {
                if ("OnPropertyCondition".equals(condition.getConditionType()) &&
                    condition.getOutcome() != null && !condition.getOutcome().isMatch()) {
                    String message = condition.getOutcome().getMessage();
                    if (message != null) {
                        // Extract property names using various patterns
                        Pattern pattern1 = Pattern.compile("@ConditionalOnProperty \\(([^)]+)\\)");
                        Matcher matcher1 = pattern1.matcher(message);
                        if (matcher1.find()) {
                            properties.add(matcher1.group(1));
                        }

                        // Also try to extract from "did not find property" messages
                        Pattern pattern2 = Pattern.compile("did not find property '([^']+)'");
                        Matcher matcher2 = pattern2.matcher(message);
                        while (matcher2.find()) {
                            properties.add(matcher2.group(1));
                        }
                    }
                }
            }
            if (!properties.isEmpty()) {
                advice.append("Set in application.yml: ").append(String.join(", ", properties)).append(". ");
            } else {
                advice.append("Enable via application.yml property. ");
            }
        }

        if (conditionTypes.contains("OnBeanCondition")) {
            advice.append("Required beans missing or already exist. Check bean definitions. ");
        }

        if (conditionTypes.contains("OnWebApplicationCondition")) {
            advice.append("Requires web context (add spring-boot-starter-web or -webflux). ");
        }

        if (conditionTypes.contains("OnCloudPlatformCondition")) {
            advice.append("Only activates on cloud platforms (Cloud Foundry, Kubernetes, etc.). ");
        }

        if (conditionTypes.contains("OnExpressionCondition")) {
            advice.append("SpEL expression evaluated to false. Check condition logic. ");
        }

        // Add technology-specific advice based on configuration class name
        if (className.contains("datasource") || className.contains("jdbc")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.datasource.url, username, password. ");
            }
        } else if (className.contains("r2dbc")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.r2dbc.url, username, password. ");
            }
        } else if (className.contains("redis")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.data.redis.host, spring.data.redis.port. ");
            }
        } else if (className.contains("kafka")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.kafka.bootstrap-servers. ");
            }
        } else if (className.contains("rabbitmq")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.rabbitmq.host, spring.rabbitmq.port. ");
            }
        } else if (className.contains("mongodb")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.data.mongodb.uri or host/port. ");
            }
        } else if (className.contains("elasticsearch")) {
            if (advice.length() == 0) {
                advice.append("Configure: spring.elasticsearch.uris. ");
            }
        } else if (className.contains("security")) {
            if (advice.length() == 0) {
                advice.append("Add: spring-boot-starter-security dependency. ");
            }
        } else if (className.contains("oauth2")) {
            if (advice.length() == 0) {
                advice.append("Configure OAuth2 client/resource server properties. ");
            }
        } else if (className.contains("actuator")) {
            if (advice.length() == 0) {
                advice.append("Add: spring-boot-starter-actuator dependency. ");
            }
        } else if (className.contains("micrometer") || className.contains("metrics")) {
            if (advice.length() == 0) {
                advice.append("Add: micrometer-registry-* dependency for your monitoring system. ");
            }
        } else if (className.contains("flyway")) {
            if (advice.length() == 0) {
                advice.append("Add: flyway-core dependency and migration scripts. ");
            }
        } else if (className.contains("liquibase")) {
            if (advice.length() == 0) {
                advice.append("Add: liquibase-core dependency and changelog files. ");
            }
        }

        return advice.length() > 0 ? advice.toString().trim() : null;
    }

    /**
     * Internal class to hold condition entry information.
     */
    private static class ConditionEntry {
        private final String className;
        private final boolean matched;
        private final String description;
        private final List<ConditionInfo> conditions;

        public ConditionEntry(String className, boolean matched, String description, List<ConditionInfo> conditions) {
            this.className = className;
            this.matched = matched;
            this.description = description;
            this.conditions = conditions;
        }

        public String getClassName() {
            return className;
        }

        public String getSimpleName() {
            return className.substring(className.lastIndexOf('.') + 1);
        }

        public boolean isMatched() {
            return matched;
        }

        public String getDescription() {
            return description;
        }

        public List<ConditionInfo> getConditions() {
            return conditions;
        }
    }

    /**
     * Internal class to hold individual condition information.
     */
    private static class ConditionInfo {
        private final String conditionType;
        private final ConditionOutcome outcome;

        public ConditionInfo(String conditionType, ConditionOutcome outcome) {
            this.conditionType = conditionType;
            this.outcome = outcome;
        }

        public String getConditionType() {
            return conditionType;
        }

        public ConditionOutcome getOutcome() {
            return outcome;
        }
    }
}

