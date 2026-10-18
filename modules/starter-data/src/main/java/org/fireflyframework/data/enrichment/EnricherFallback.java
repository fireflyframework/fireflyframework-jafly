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

package org.fireflyframework.data.enrichment;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a fallback provider for a data enricher.
 *
 * <p>When the primary enricher fails or returns empty results (depending on the
 * {@link #strategy()}), the framework automatically tries the fallback enricher
 * identified by {@link #fallbackTo()}. Fallback chains are supported, where each
 * fallback enricher can itself declare a further fallback, up to {@link #maxFallbacks()}
 * depth to prevent infinite loops.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @EnricherMetadata(
 *     providerName = "primary-provider",
 *     type = "credit-report"
 * )
 * @EnricherFallback(
 *     fallbackTo = "backup-provider",
 *     strategy = FallbackStrategy.ON_ERROR
 * )
 * public class PrimaryCreditReportEnricher extends DataEnricher<...> { }
 *
 * @EnricherMetadata(
 *     providerName = "backup-provider",
 *     type = "credit-report"
 * )
 * public class BackupCreditReportEnricher extends DataEnricher<...> { }
 * }</pre>
 *
 * @see FallbackStrategy
 * @see FallbackEnrichmentExecutor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnricherFallback {

    /**
     * The provider name of the fallback enricher.
     *
     * <p>This must match the {@code providerName} declared in the fallback enricher's
     * {@link EnricherMetadata} annotation. The fallback enricher is resolved at runtime
     * from the {@link org.fireflyframework.data.service.DataEnricherRegistry}.</p>
     *
     * @return the fallback provider name
     */
    String fallbackTo();

    /**
     * The strategy that determines when the fallback should be triggered.
     *
     * @return the fallback strategy (default: {@link FallbackStrategy#ON_ERROR})
     */
    FallbackStrategy strategy() default FallbackStrategy.ON_ERROR;

    /**
     * Maximum number of fallback steps in a chain to prevent infinite loops.
     *
     * <p>For example, if enricher A falls back to B and B falls back to C,
     * that is a chain depth of 2. If {@code maxFallbacks} is set to 3, the
     * chain can go up to 3 levels deep before stopping.</p>
     *
     * @return the maximum chain depth (default: 3)
     */
    int maxFallbacks() default 3;
}
