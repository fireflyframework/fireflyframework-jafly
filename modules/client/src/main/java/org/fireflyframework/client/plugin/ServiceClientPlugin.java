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

package org.fireflyframework.client.plugin;

import org.fireflyframework.client.ServiceClient;
import reactor.core.publisher.Mono;

/**
 * Service Provider Interface (SPI) for ServiceClient plugins.
 * 
 * <p>Plugins can extend the ServiceClient framework with custom functionality
 * without modifying the core code. Plugins are discovered and loaded automatically
 * using Java's ServiceLoader mechanism.
 *
 * <p>Plugin lifecycle:
 * <ol>
 *   <li>Discovery - Plugins are discovered via ServiceLoader</li>
 *   <li>Initialization - {@link #initialize(ServiceClient)} is called</li>
 *   <li>Request Processing - {@link #beforeRequest(RequestContext)} is called before each request</li>
 *   <li>Response Processing - {@link #afterResponse(ResponseContext)} is called after each response</li>
 *   <li>Shutdown - {@link #shutdown()} is called when client is closed</li>
 * </ol>
 *
 * <p>Example plugin implementation:
 * <pre>{@code
 * public class CustomMetricsPlugin implements ServiceClientPlugin {
 *     
 *     @Override
 *     public String getName() {
 *         return "custom-metrics";
 *     }
 *     
 *     @Override
 *     public void initialize(ServiceClient client) {
 *         System.out.println("Initializing custom metrics for: " + client.getServiceName());
 *     }
 *     
 *     @Override
 *     public Mono<Void> beforeRequest(RequestContext context) {
 *         // Record request start time
 *         context.setAttribute("startTime", System.currentTimeMillis());
 *         return Mono.empty();
 *     }
 *     
 *     @Override
 *     public Mono<Void> afterResponse(ResponseContext context) {
 *         // Calculate and record request duration
 *         long startTime = (long) context.getAttribute("startTime");
 *         long duration = System.currentTimeMillis() - startTime;
 *         System.out.println("Request took: " + duration + "ms");
 *         return Mono.empty();
 *     }
 * }
 * }</pre>
 *
 * <p>To register a plugin, create a file:
 * {@code META-INF/services/org.fireflyframework.client.plugin.ServiceClientPlugin}
 * containing the fully qualified class name of your plugin implementation.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
public interface ServiceClientPlugin {

    /**
     * Gets the plugin name.
     *
     * @return the plugin name
     */
    String getName();

    /**
     * Gets the plugin version.
     *
     * @return the plugin version
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Gets the plugin description.
     *
     * @return the plugin description
     */
    default String getDescription() {
        return "";
    }

    /**
     * Initializes the plugin with the ServiceClient instance.
     *
     * @param client the service client
     */
    void initialize(ServiceClient client);

    /**
     * Called before each request.
     *
     * @param context the request context
     * @return a Mono that completes when pre-processing is done
     */
    default Mono<Void> beforeRequest(RequestContext context) {
        return Mono.empty();
    }

    /**
     * Called after each response.
     *
     * @param context the response context
     * @return a Mono that completes when post-processing is done
     */
    default Mono<Void> afterResponse(ResponseContext context) {
        return Mono.empty();
    }

    /**
     * Called when an error occurs.
     *
     * @param context the error context
     * @return a Mono that completes when error handling is done
     */
    default Mono<Void> onError(ErrorContext context) {
        return Mono.empty();
    }

    /**
     * Called when the client is shutting down.
     */
    default void shutdown() {
        // Default implementation does nothing
    }

    /**
     * Gets the plugin priority (lower values execute first).
     *
     * @return the priority
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Checks if the plugin is enabled.
     *
     * @return true if enabled
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Request context for plugin processing.
     */
    interface RequestContext {
        String getServiceName();
        String getMethod();
        String getEndpoint();
        java.util.Map<String, String> getHeaders();
        Object getBody();
        void setAttribute(String key, Object value);
        Object getAttribute(String key);
    }

    /**
     * Response context for plugin processing.
     */
    interface ResponseContext {
        String getServiceName();
        String getMethod();
        String getEndpoint();
        int getStatusCode();
        java.util.Map<String, String> getHeaders();
        Object getBody();
        long getDuration();
        void setAttribute(String key, Object value);
        Object getAttribute(String key);
    }

    /**
     * Error context for plugin processing.
     */
    interface ErrorContext {
        String getServiceName();
        String getMethod();
        String getEndpoint();
        Throwable getError();
        long getDuration();
        void setAttribute(String key, Object value);
        Object getAttribute(String key);
    }
}

