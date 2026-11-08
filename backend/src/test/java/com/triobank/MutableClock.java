package com.triobank;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock: real time plus an offset that a test can move (lockout, token expiry). */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Duration offset = Duration.ZERO;

    public MutableClock() {
        this(ZoneOffset.UTC);
    }

    private MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public synchronized void advance(Duration duration) {
        offset = offset.plus(duration);
    }

    public synchronized void setOffset(Duration newOffset) {
        offset = newOffset;
    }

    public synchronized void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        MutableClock self = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return newZone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return self.withZone(other);
            }

            @Override
            public Instant instant() {
                return self.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }

}
