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

package org.fireflyframework.client;

import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Mono;

import javax.xml.namespace.QName;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SOAP-specific service client interface with WSDL-based operation invocation.
 * 
 * <p>Provides natural SOAP operation semantics with automatic WSDL discovery,
 * WS-Security support, and modern reactive API over traditional SOAP services.
 *
 * <p>Example usage:
 * <pre>{@code
 * SoapClient client = ServiceClient.soap("weather-service")
 *     .wsdlUrl("http://www.webservicex.net/globalweather.asmx?WSDL")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * // Fluent operation invocation
 * Mono<WeatherResponse> weather = client.invoke("GetWeatherByCity")
 *     .withParameter("city", "New York")
 *     .withParameter("country", "US")
 *     .execute(WeatherResponse.class);
 *
 * // Direct invocation with request object
 * Mono<WeatherResponse> weather = client.invokeAsync(
 *     "GetWeatherByCity",
 *     request,
 *     WeatherResponse.class
 * );
 *
 * // Discover available operations
 * List<String> operations = client.getOperations();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface SoapClient extends ServiceClient {

    // ========================================
    // Operation Invocation
    // ========================================

    /**
     * Invokes a SOAP operation by name with fluent parameter building.
     * 
     * <p>This method provides a fluent API for building SOAP requests with
     * named parameters, making it easy to invoke operations without creating
     * request objects.
     *
     * <p>Example:
     * <pre>{@code
     * Mono<WeatherResponse> weather = client.invoke("GetWeatherByCity")
     *     .withParameter("city", "New York")
     *     .withParameter("country", "US")
     *     .withTimeout(Duration.ofSeconds(10))
     *     .execute(WeatherResponse.class);
     * }</pre>
     *
     * @param operationName the SOAP operation name from WSDL
     * @return a fluent builder for the operation
     */
    OperationBuilder invoke(String operationName);

    /**
     * Invokes a SOAP operation asynchronously with a request object.
     * 
     * <p>Use this when you have a pre-built request object (e.g., from JAXB
     * generated classes).
     *
     * <p>Example:
     * <pre>{@code
     * GetWeatherRequest request = new GetWeatherRequest();
     * request.setCity("New York");
     * request.setCountry("US");
     * 
     * Mono<WeatherResponse> weather = client.invokeAsync(
     *     "GetWeatherByCity",
     *     request,
     *     WeatherResponse.class
     * );
     * }</pre>
     *
     * @param operationName the SOAP operation name from WSDL
     * @param request the request object
     * @param responseType the expected response type
     * @param <Req> the request type
     * @param <Res> the response type
     * @return a Mono containing the response
     */
    <Req, Res> Mono<Res> invokeAsync(String operationName, Req request, Class<Res> responseType);

    /**
     * Invokes a SOAP operation asynchronously with TypeReference support.
     * 
     * <p>Use this for generic response types that require TypeReference.
     *
     * @param operationName the SOAP operation name from WSDL
     * @param request the request object
     * @param typeReference the type reference for generic response types
     * @param <Req> the request type
     * @param <Res> the response type
     * @return a Mono containing the response
     */
    <Req, Res> Mono<Res> invokeAsync(String operationName, Req request, TypeReference<Res> typeReference);

    // ========================================
    // WSDL Introspection
    // ========================================

    /**
     * Gets the list of available SOAP operations from WSDL.
     * 
     * <p>This method parses the WSDL and returns all operation names that can
     * be invoked on this service.
     *
     * <p>Example:
     * <pre>{@code
     * List<String> operations = client.getOperations();
     * // Returns: ["GetWeatherByCity", "GetCitiesByCountry", ...]
     * }</pre>
     *
     * @return list of operation names
     */
    List<String> getOperations();

    /**
     * Gets the raw JAX-WS port for advanced SOAP operations.
     * 
     * <p>Use this when you need direct access to the JAX-WS port for advanced
     * features not exposed by this interface.
     *
     * @param portType the port interface class
     * @param <P> the port type
     * @return the JAX-WS port
     */
    <P> P getPort(Class<P> portType);

    // ========================================
    // SOAP-Specific Metadata
    // ========================================

    /**
     * Returns the WSDL URL for this SOAP service.
     *
     * @return the WSDL URL
     */
    String getWsdlUrl();

    /**
     * Returns the service QName from WSDL.
     *
     * @return the service QName, or null if not specified
     */
    QName getServiceQName();

    /**
     * Returns the port QName from WSDL.
     *
     * @return the port QName, or null if not specified
     */
    QName getPortQName();

    /**
     * Fluent builder for SOAP operation invocation.
     * 
     * <p>Provides a fluent API for building SOAP requests with named parameters,
     * headers, and timeouts.
     */
    interface OperationBuilder {
        /**
         * Adds a parameter to the SOAP request.
         *
         * @param name the parameter name
         * @param value the parameter value
         * @return this builder
         */
        OperationBuilder withParameter(String name, Object value);

        /**
         * Adds multiple parameters to the SOAP request.
         *
         * @param parameters the parameters map
         * @return this builder
         */
        OperationBuilder withParameters(Map<String, Object> parameters);

        /**
         * Adds a SOAP header.
         *
         * @param name the header name
         * @param value the header value
         * @return this builder
         */
        OperationBuilder withHeader(String name, String value);

        /**
         * Adds multiple SOAP headers.
         *
         * @param headers the headers map
         * @return this builder
         */
        OperationBuilder withHeaders(Map<String, String> headers);

        /**
         * Sets the request timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        OperationBuilder withTimeout(Duration timeout);

        /**
         * Executes the SOAP operation.
         *
         * @param responseType the expected response type
         * @param <R> the response type
         * @return a Mono containing the response
         */
        <R> Mono<R> execute(Class<R> responseType);

        /**
         * Executes the SOAP operation with TypeReference support.
         *
         * @param typeReference the type reference for generic response types
         * @param <R> the response type
         * @return a Mono containing the response
         */
        <R> Mono<R> execute(TypeReference<R> typeReference);
    }
}

