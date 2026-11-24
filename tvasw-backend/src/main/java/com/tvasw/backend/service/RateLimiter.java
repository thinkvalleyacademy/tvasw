package com.tvasw.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Fixed-window counters kept in memory: single server only, reset on restart
@Service
@EnableScheduling
public class RateLimiter {

    private record Window(long expiresAt, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String key, int limit, Duration period, String message) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) ->
            old == null || old.expiresAt <= now
                ? new Window(now + period.toMillis(), 1)
                : new Window(old.expiresAt, Math.min(old.count + 1, limit + 1)));
        if (w.count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    @Scheduled(fixedDelay = 600000)
    void removeExpired() {
        long now = System.currentTimeMillis();
        windows.values().removeIf(w -> w.expiresAt <= now);
    }
}
