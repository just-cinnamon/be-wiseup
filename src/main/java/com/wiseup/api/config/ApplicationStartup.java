package com.wiseup.api.config;

import com.wiseup.api.domain.service.AuthService;
import com.wiseup.api.domain.service.ModerationService;
import com.wiseup.api.infrastructure.cache.InMemoryCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Application startup hook.
 *
 * <p>Runs once after CDI is fully wired:
 * <ol>
 *   <li>Configures the in-memory cache with settings from {@link WiseUpConfig}.</li>
 *   <li>Loads the blocked-video set from the database into memory.</li>
 *   <li>Creates seed users from the {@code SEED_USERS} environment variable.</li>
 *   <li>Runs the first origin-sync cycle (deleted videos).</li>
 * </ol>
 */
@ApplicationScoped
public class ApplicationStartup {

    private static final Logger LOG = Logger.getLogger(ApplicationStartup.class);

    @Inject WiseUpConfig config;
    @Inject InMemoryCache cache;
    @Inject ModerationService moderation;
    @Inject AuthService authService;

    void onStart(@Observes StartupEvent ev) {
        // Configure cache
        cache.configure(
                config.cache().ttlSeconds(),
                config.cache().maxEntries(),
                config.cache().maxStaleSeconds()
        );

        // Load blocked-video set
        int blocked = moderation.refresh();
        LOG.info("Moderation: " + blocked + " video(s) currently blocked.");

        // Create seed users (boot-time accounts for first deploy)
        List<AuthService.SeedEntry> seeds = parseSeedUsers();
        if (!seeds.isEmpty()) {
            int created = authService.seedUsers(seeds);
            LOG.info("Seed users: " + seeds.size() + " configured, " + created + " created now.");
        }

        // Initial origin sync
        ModerationService.SyncResult result = moderation.syncWithOrigin(config.sync().pages());
        if (!result.failed()) {
            LOG.info("Origin sync: " + result.blocked() + " new video(s) blocked.");
        } else {
            LOG.warn("Origin sync failed; " + result.blocked() + " blocked before failure.");
        }
    }

    private List<AuthService.SeedEntry> parseSeedUsers() {
        return config.seedUsers()
                .filter(s -> !s.isBlank())
                .map(raw -> {
                    List<AuthService.SeedEntry> entries = new ArrayList<>();
                    for (String pair : raw.split(",")) {
                        int colon = pair.indexOf(':');
                        if (colon > 0) {
                            entries.add(new AuthService.SeedEntry(
                                    pair.substring(0, colon).trim(),
                                    pair.substring(colon + 1)));
                        }
                    }
                    return entries;
                })
                .orElse(List.of());
    }
}
