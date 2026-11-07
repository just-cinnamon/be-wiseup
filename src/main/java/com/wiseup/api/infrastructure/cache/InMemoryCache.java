package com.wiseup.api.infrastructure.cache;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe, in-process TTL cache for upstream (Webmasters API) responses.
 *
 * <p>Each entry stores:
 * <ul>
 *   <li>{@code value} – the cached JSON payload (as a parsed Java object)</li>
 *   <li>{@code storedAt} – epoch-millis when inserted</li>
 *   <li>{@code expiresAt} – epoch-millis of normal expiry</li>
 * </ul>
 *
 * <p>The cache is configured with a {@code maxStaleSeconds} ceiling: even when
 * the upstream is down, an entry older than that ceiling is evicted rather than
 * served, to avoid perpetuating removed content (ADR-0018 equivalent).
 */
@ApplicationScoped
public class InMemoryCache {

    private static final Logger LOG = Logger.getLogger(InMemoryCache.class);

    private int ttlSeconds    = 600;
    private int maxEntries    = 500;
    private int maxStaleSeconds = 3600;

    /** Insertion-ordered map; eldest entries are evicted when capacity is reached. */
    private final LinkedHashMap<String, Entry<?>> store =
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry<?>> eldest) {
                    return size() > maxEntries;
                }
            };

    // Called once from the application startup bean before any request arrives.
    public void configure(int ttlSeconds, int maxEntries, int maxStaleSeconds) {
        this.ttlSeconds      = ttlSeconds;
        this.maxEntries      = maxEntries;
        this.maxStaleSeconds = maxStaleSeconds;
    }

    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public synchronized <T> Optional<CacheEntry<T>> get(String key) {
        Entry<?> raw = store.get(key);
        if (raw == null) return Optional.empty();
        long now = epochMs();
        boolean fresh      = raw.expiresAt > now;
        int ageSeconds = (int) Math.max(0, (now - raw.storedAt) / 1_000);
        return Optional.of(new CacheEntry<>((T) raw.value, fresh, ageSeconds));
    }

    public synchronized <T> void put(String key, T value) {
        long now = epochMs();
        store.put(key, new Entry<>(value, now, now + (long) ttlSeconds * 1_000));
    }

    public synchronized void evict(String key) {
        store.remove(key);
    }

    public synchronized int size() {
        return store.size();
    }

    // -------------------------------------------------------------------------

    private long epochMs() {
        return Instant.now().toEpochMilli();
    }

    // -------------------------------------------------------------------------

    record Entry<T>(T value, long storedAt, long expiresAt) {}

    public record CacheEntry<T>(T value, boolean fresh, int ageSeconds) {}
}
