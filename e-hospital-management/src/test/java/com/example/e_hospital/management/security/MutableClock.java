package com.example.e_hospital.management.security;

import java.time.*;

public final class MutableClock extends Clock {
    private volatile Instant current = Instant.parse("2026-01-01T00:00:00Z");
    public void advance(Duration duration) { current = current.plus(duration); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return current; }
}
