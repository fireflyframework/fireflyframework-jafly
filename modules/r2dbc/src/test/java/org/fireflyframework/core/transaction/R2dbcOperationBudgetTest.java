package org.fireflyframework.core.transaction;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class R2dbcOperationBudgetTest {
    static final class Time extends Clock {
        final Instant base=Instant.parse("2026-10-26T00:00:00Z");final AtomicLong wall=new AtomicLong(),mono=new AtomicLong();final LongSupplier nanos=mono::get;
        public Instant instant(){return base.plusMillis(wall.get());}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
        R2dbcOperationBudget budget(long millis){return R2dbcOperationBudget.observed(instant(),instant().plusMillis(millis),mono.get(),Duration.ofMillis(millis),this,nanos);}
    }
    @Test void elapsedTimeCannotBeResetByRemainingCalls(){var t=new Time();var b=t.budget(1000);t.mono.set(400_000_000);assertEquals(Duration.ofMillis(600),b.remaining());t.mono.set(900_000_000);assertEquals(Duration.ofMillis(100),b.remaining());}
    @Test void earlierWallClockExpiryWinsOverMonotonicLimit(){var t=new Time();var b=t.budget(1000);t.wall.set(999);assertEquals(Duration.ofMillis(1),b.remaining());t.wall.set(1000);assertThrows(IllegalStateException.class,b::remaining);}
    @Test void backwardWallClockIsRefusedInsteadOfExtendingTime(){var t=new Time();var b=t.budget(1000);t.wall.set(-1);assertThrows(IllegalStateException.class,b::remaining);}
    @Test void negativeMonotonicElapsedIsRefused(){var t=new Time();var b=t.budget(1000);t.mono.set(-1);assertThrows(IllegalStateException.class,b::remaining);}
    @Test void zeroNegativeAndOverMaximumDurationsAreRejected(){var t=new Time();for(long millis:new long[]{0,-1,1001})assertThrows(IllegalArgumentException.class,()->R2dbcOperationBudget.observed(t.base,t.base.plusMillis(millis),0,Duration.ofSeconds(1),t,t.nanos));}
    @Test void unrepresentableNanosAreRejectedBeforeAnyTimerOrResource(){var t=new Time();assertThrows(ArithmeticException.class,()->R2dbcOperationBudget.observed(t.base,t.base.plus(Duration.ofDays(200000)),0,Duration.ofDays(200000),t,t.nanos));}
    @Test void incompatibleClockOrMonotonicSourcesCannotReplaceAnOriginalCap(){var a=new Time();var b=new Time();assertThrows(IllegalArgumentException.class,()->a.budget(1000).intersect(b.budget(1000)));}
    @Test void intersectionRetainsEarlierOriginalAndNativeCaps(){var t=new Time();var original=t.budget(1000);var shorter=t.budget(200);var cap=original.intersect(shorter);t.mono.set(100_000_000);assertEquals(Duration.ofMillis(100),cap.remaining());assertEquals(Duration.ofMillis(900),original.remaining());}
    @Test void cleanupLimitsMustBeFiniteAndStrictlyPositive(){assertThrows(IllegalArgumentException.class,()->new CleanupPolicy(Duration.ZERO,Duration.ofSeconds(1)));assertThrows(ArithmeticException.class,()->new CleanupPolicy(Duration.ofDays(200000),Duration.ofSeconds(1)));}
}
