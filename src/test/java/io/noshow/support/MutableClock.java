package io.noshow.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} whose instant can be set and advanced, for driving
 * time-dependent logic (the evaluator) deterministically in tests.
 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public static MutableClock at(Instant instant) {
        return new MutableClock(instant, ZoneOffset.UTC);
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void advanceSeconds(long seconds) {
        this.instant = this.instant.plusSeconds(seconds);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public long millis() {
        return instant.toEpochMilli();
    }
}
