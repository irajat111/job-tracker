package com.example.jobtracker.security;

import java.util.HashMap;
import java.util.Map;

// Fixed window rate limit: ek key ko window mein max requests.
// Login, OTP aur AI jaise sensitive endpoints par lagta hai.
public final class RateLimiter {

    private static final class Counter {
        final long start;
        int count;

        Counter(long start) {
            this.start = start;
        }
    }

    private final int max;
    private final long windowMs;
    private final Map<String, Counter> counters = new HashMap<>();

    public RateLimiter(int max, long windowMs) {
        this.max = max;
        this.windowMs = windowMs;
    }

    public synchronized boolean allow(String key) {
        long now = System.currentTimeMillis();
        Counter c = counters.get(key);
        if (c == null || now - c.start >= windowMs) {
            c = new Counter(now);
            counters.put(key, c);
        }
        c.count++;
        return c.count <= max;
    }
}
