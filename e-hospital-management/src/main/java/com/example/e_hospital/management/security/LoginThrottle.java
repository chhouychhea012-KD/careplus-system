package com.example.e_hospital.management.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded, single-process fixed-window limits. Do not trust client-supplied forwarding headers. */
@Component
public class LoginThrottle {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private record Bucket(int failures, Instant until) {}
    public LoginThrottle(Clock clock) { this.clock = clock; }
    private String account(String username) { return "account:" + TokenSecrets.hash(username.toLowerCase(Locale.ROOT)); }
    public synchronized boolean blocked(String username, String address) {
        return blockedKey(account(username), 5) || blockedKey("ip:" + address, 20);
    }
    public synchronized void failed(String username, String address) {
        increment(account(username)); increment("ip:" + address);
    }
    public synchronized void succeeded(String username) { buckets.remove(account(username)); }
    public synchronized boolean allowRecovery(String address) {
        String key = "recovery:" + address;
        if (blockedKey(key, 5)) return false;
        increment(key); return true;
    }
    private boolean blockedKey(String key, int limit) {
        prune();
        var bucket = buckets.get(key);
        return buckets.size() >= 10000 || bucket != null && bucket.failures() >= limit;
    }
    private void increment(String key) {
        prune();
        if (buckets.size() >= 10000 && !buckets.containsKey(key)) return;
        var old = buckets.get(key);
        buckets.put(key, old == null ? new Bucket(1, clock.instant().plus(WINDOW))
            : new Bucket(old.failures() + 1, old.until()));
    }
    private void prune() { buckets.values().removeIf(bucket -> !clock.instant().isBefore(bucket.until())); }
}
