package com.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fenêtre glissante par clé (IP) pour limiter les POST /auth/login (PRD §12).
 */
@Component
public class LoginRateLimiter {

    private final int maxRequests;
    private final long windowMillis;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.security.auth-rate-limit-max:30}") int maxRequests,
            @Value("${app.security.auth-rate-limit-window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        List<Long> list = buckets.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            list.removeIf(t -> now - t > windowMillis);
            if (list.size() >= maxRequests) {
                return false;
            }
            list.add(now);
            return true;
        }
    }
}
