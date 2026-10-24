package org.fireflyframework.core.auditing;

import org.springframework.data.auditing.DateTimeProvider;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;
import java.util.Optional;

/**
 * Opt-in UTC time source for Spring Data auditing of LocalDateTime,
 * OffsetDateTime, and Instant fields. Date and Long fields are unsupported.
 */
public final class UtcOffsetDateTimeProvider implements DateTimeProvider {
    private final Clock clock;

    public UtcOffsetDateTimeProvider(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock").withZone(ZoneOffset.UTC);
    }

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(OffsetDateTime.now(clock));
    }
}
