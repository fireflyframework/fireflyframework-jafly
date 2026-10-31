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


package org.fireflyframework.validators;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.fireflyframework.annotations.ValidDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeValidatorClockProviderTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ParameterizedTest
    @ValueSource(strings = {"2001-01-01T12:00:00Z", "2099-01-01T12:00:00Z"})
    void usesTheProviderClockForThePresent(String instant) {
        Clock clock = clock(instant);
        try (ValidatorFactory factory = factory(clock)) {
            var validator = factory.getValidator();
            String now = format(clock.instant());
            assertThat(validator.validate(new NoPast(now))).isEmpty();
            assertThat(validator.validate(new NoFuture(now))).isEmpty();
            assertThat(validator.validate(new PresentOnly(now))).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001-01-01T12:00:00Z", "2099-01-01T12:00:00Z"})
    void comparesPastAndFutureAgainstTheProviderClock(String instant) {
        Clock clock = clock(instant);
        try (ValidatorFactory factory = factory(clock)) {
            var validator = factory.getValidator();
            String before = format(clock.instant().minusSeconds(2));
            String after = format(clock.instant().plusSeconds(2));
            assertThat(validator.validate(new NoPast(before))).hasSize(1);
            assertThat(validator.validate(new NoPast(after))).isEmpty();
            assertThat(validator.validate(new NoFuture(before))).isEmpty();
            assertThat(validator.validate(new NoFuture(after))).hasSize(1);
        }
    }

    @Test
    void consultsTheClockProviderForEachValidation() {
        var current = new AtomicReference<>(clock("2001-01-01T12:00:00Z"));
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .clockProvider(current::get).buildValidatorFactory()) {
            var validator = factory.getValidator();
            var value = new NoFuture(format(Instant.parse("2050-01-01T12:00:00Z")));
            assertThat(validator.validate(value)).hasSize(1);
            current.set(clock("2099-01-01T12:00:00Z"));
            assertThat(validator.validate(value)).isEmpty();
        }
    }

    @Test
    void preservesTheExistingStrictOneSecondTolerance() {
        Clock clock = clock("2001-01-01T12:00:00Z");
        try (ValidatorFactory factory = factory(clock)) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new PresentOnly(format(clock.instant())))).isEmpty();
            assertThat(validator.validate(new PresentOnly(format(clock.instant().minusSeconds(1))))).hasSize(1);
            assertThat(validator.validate(new PresentOnly(format(clock.instant().plusSeconds(1))))).hasSize(1);
        }
    }

    @Test
    void retainsTheSystemClockFallbackForANullContext() throws Exception {
        var noPast = new DateTimeValidator();
        noPast.initialize(NoPast.class.getDeclaredField("value").getAnnotation(ValidDateTime.class));
        var noFuture = new DateTimeValidator();
        noFuture.initialize(NoFuture.class.getDeclaredField("value").getAnnotation(ValidDateTime.class));
        Instant now = Instant.now();
        String before = format(now.minusSeconds(86400));
        String after = format(now.plusSeconds(86400));
        assertThat(noPast.isValid(before, null)).isFalse();
        assertThat(noPast.isValid(after, null)).isTrue();
        assertThat(noFuture.isValid(before, null)).isTrue();
        assertThat(noFuture.isValid(after, null)).isFalse();
    }

    private static Clock clock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.systemDefault());
    }

    private static ValidatorFactory factory(Clock clock) {
        return Validation.byDefaultProvider().configure().clockProvider(() -> clock).buildValidatorFactory();
    }

    private static String format(Instant instant) {
        return FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    private record NoPast(@ValidDateTime(allowPast = false) String value) { }
    private record NoFuture(@ValidDateTime(allowFuture = false) String value) { }
    private record PresentOnly(@ValidDateTime(allowPast = false, allowFuture = false) String value) { }
}
