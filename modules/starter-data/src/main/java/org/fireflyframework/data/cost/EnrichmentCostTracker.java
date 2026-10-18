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

package org.fireflyframework.data.cost;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe tracker for enrichment call costs.
 *
 * <p>Tracks the number of calls made to each provider and calculates costs
 * based on registered per-call pricing. This tracker is designed for
 * high-concurrency environments using lock-free data structures.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Register providers with their cost information
 * costTracker.registerProvider("Financial Data Provider", 0.05, "USD");
 * costTracker.registerProvider("Credit Bureau Provider", 1.50, "USD");
 *
 * // Record calls as they happen
 * costTracker.recordCall("Financial Data Provider");
 * costTracker.recordCall("Credit Bureau Provider");
 *
 * // Generate a report
 * CostReport report = costTracker.getReport();
 * }</pre>
 *
 * @see CostReport
 */
@Slf4j
public class EnrichmentCostTracker {

    private final Map<String, Double> providerCostPerCall = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> providerCallCounts = new ConcurrentHashMap<>();
    private final Map<String, String> providerCurrencies = new ConcurrentHashMap<>();

    /**
     * Registers a provider with its cost-per-call information.
     *
     * <p>If the provider is already registered, the cost information is updated.</p>
     *
     * @param providerName the provider name
     * @param costPerCall the cost per call
     * @param currency the currency code (ISO 4217)
     */
    public void registerProvider(String providerName, double costPerCall, String currency) {
        providerCostPerCall.put(providerName, costPerCall);
        providerCallCounts.putIfAbsent(providerName, new AtomicLong(0));
        providerCurrencies.put(providerName, currency);
        log.debug("Registered provider cost: {} = {} {}/call", providerName, costPerCall, currency);
    }

    /**
     * Records a call to a provider, incrementing its call count.
     *
     * <p>If the provider has not been registered, the call is still counted
     * with a cost of 0.0.</p>
     *
     * @param providerName the provider name
     */
    public void recordCall(String providerName) {
        providerCallCounts.computeIfAbsent(providerName, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Generates a cost report with per-provider breakdown.
     *
     * @return the cost report
     */
    public CostReport getReport() {
        Map<String, CostReport.ProviderCost> providerCosts = providerCallCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String name = entry.getKey();
                            long callCount = entry.getValue().get();
                            double costPerCall = providerCostPerCall.getOrDefault(name, 0.0);
                            return CostReport.ProviderCost.builder()
                                    .providerName(name)
                                    .callCount(callCount)
                                    .costPerCall(costPerCall)
                                    .totalCost(callCount * costPerCall)
                                    .build();
                        }
                ));

        double totalCost = providerCosts.values().stream()
                .mapToDouble(CostReport.ProviderCost::getTotalCost)
                .sum();

        String currency = providerCurrencies.values().stream()
                .findFirst()
                .orElse("USD");

        return CostReport.builder()
                .providerCosts(providerCosts)
                .totalCost(totalCost)
                .currency(currency)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Returns the total cost across all providers.
     *
     * @return the total cost
     */
    public double getTotalCost() {
        return providerCallCounts.entrySet().stream()
                .mapToDouble(entry -> {
                    long callCount = entry.getValue().get();
                    double costPerCall = providerCostPerCall.getOrDefault(entry.getKey(), 0.0);
                    return callCount * costPerCall;
                })
                .sum();
    }
}
