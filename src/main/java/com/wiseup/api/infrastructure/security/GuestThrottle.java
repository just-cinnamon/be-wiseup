package com.wiseup.api.infrastructure.security;

import com.wiseup.api.config.WiseUpConfig;
import com.wiseup.api.presentation.exception.TooManyGuestsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limit for {@code POST /api/auth/guest}.
 *
 * <p>That endpoint is open and writes a row per call, so without a limit a
 * single client could fill the {@code users} table. A fixed one-hour window per
 * IP is enough to stop that while staying invisible to real visitors – nobody
 * crosses the +18 gate thirty times in an hour.
 *
 * <p>In memory on purpose: a counter in the database would cost a write per
 * entry, and the only thing it buys is sharing the count between replicas. With
 * one replica (see railway.json) there is nothing to share, and the limit is a
 * speed bump, not a security boundary – the real protection is the session TTL
 * and the hourly purge of guests without a session.
 *
 * <p>Memory is bounded: when more than {@value #MAX_TRACKED_IPS} addresses are
 * being tracked, expired windows are dropped, and if that is not enough the map
 * is cleared. It fails open – losing counts is better than refusing entry to
 * everyone.
 */
@ApplicationScoped
public class GuestThrottle {

    private static final long WINDOW_MS = Duration.ofHours(1).toMillis();
    private static final int MAX_TRACKED_IPS = 20_000;

    @Inject
    WiseUpConfig config;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Counts one attempt from this IP.
     *
     * @throws TooManyGuestsException when the hourly limit is already spent
     */
    public void check(String ip) {
        int max = config.guest().maxPerHour();
        if (max <= 0) return; // 0 disables the throttle

        long now = System.currentTimeMillis();

        if (windows.size() > MAX_TRACKED_IPS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MS);
            if (windows.size() > MAX_TRACKED_IPS) windows.clear();
        }

        Window window = windows.compute(ip, (key, current) ->
                current == null || now - current.startedAt() >= WINDOW_MS
                        ? new Window(now, 1)
                        : new Window(current.startedAt(), current.count() + 1));

        if (window != null && window.count() > max) throw new TooManyGuestsException();
    }

    private record Window(long startedAt, int count) {}
}
