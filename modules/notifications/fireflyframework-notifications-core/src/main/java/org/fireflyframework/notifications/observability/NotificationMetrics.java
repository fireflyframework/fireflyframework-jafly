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

package org.fireflyframework.notifications.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import reactor.core.publisher.Mono;

/**
 * Shared observability instrumentation for the Notifications module.
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.notifications.sent} — total notifications dispatched, tagged by {@code channel}
 *         (email/sms/push), {@code provider} (sendgrid/twilio/firebase/resend/...) and {@code status}</li>
 *     <li>{@code firefly.notifications.delivery.duration} — end-to-end dispatch latency timer</li>
 *     <li>{@code firefly.notifications.errors} — failed deliveries, tagged by {@code error.type}</li>
 *     <li>{@code firefly.notifications.templates.rendered} — template renders, tagged by template name</li>
 * </ul>
 */
public class NotificationMetrics extends FireflyMetricsSupport {

    private static final String TAG_CHANNEL = "channel";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_TEMPLATE = "template";

    public NotificationMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "notifications");
    }

    /**
     * Wraps a notification dispatch operation with a timer and success/failure counters.
     */
    public <T> Mono<T> timedDispatch(String channel, String provider, Mono<T> dispatch) {
        return timed("delivery.duration", dispatch, TAG_CHANNEL, channel, TAG_PROVIDER, provider)
                .doOnSuccess(v -> recordSuccess("sent", TAG_CHANNEL, channel, TAG_PROVIDER, provider))
                .doOnError(e -> {
                    recordFailure("sent", e, TAG_CHANNEL, channel, TAG_PROVIDER, provider);
                    recordFailure("errors", e, TAG_CHANNEL, channel, TAG_PROVIDER, provider);
                });
    }

    public void recordTemplateRendered(String template) {
        counter("templates.rendered", TAG_TEMPLATE, template).increment();
    }
}
