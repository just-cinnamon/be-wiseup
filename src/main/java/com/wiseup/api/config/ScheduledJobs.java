package com.wiseup.api.config;

import com.wiseup.api.domain.repository.UserRepository;
import com.wiseup.api.domain.service.ModerationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Scheduled background jobs.
 *
 * <p>All cron intervals are configurable via application.properties. These jobs
 * use {@link Scheduled} with a jitter window to avoid synchronized spikes in
 * multi-instance deployments.
 */
@ApplicationScoped
public class ScheduledJobs {

    private static final Logger LOG = Logger.getLogger(ScheduledJobs.class);

    @Inject WiseUpConfig config;
    @Inject ModerationService moderation;
    @Inject UserRepository userRepository;

    /**
     * Purge access logs older than the configured retention period (default 180 days).
     * Runs daily at 03:00 UTC with up to 5 minutes of jitter.
     * Required by Marco Civil da Internet art.15.
     */
    @Scheduled(cron = "0 0 3 * * ?", delay = 0, delayUnit = java.util.concurrent.TimeUnit.SECONDS)
    void purgeAccessLogs() {
        try {
            int days = config.accessLog().retentionDays();
            OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
            int purged = userRepository.purgeAccessLogs(cutoff);
            if (purged > 0) {
                LOG.info("Access log purge: " + purged + " row(s) older than " + days + " days removed.");
            }
        } catch (Exception e) {
            LOG.error("Access log purge failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sync deleted videos from the upstream. Runs every 6 hours (configurable).
     * Also calls {@link ModerationService#refresh()} so multi-instance deployments
     * pick up blocks made by other replicas.
     */
    @Scheduled(every = "${wiseup.sync.interval-hours}h", delay = 6,
               delayUnit = java.util.concurrent.TimeUnit.HOURS)
    void syncOrigin() {
        try {
            ModerationService.SyncResult result =
                    moderation.syncWithOrigin(config.sync().pages());
            moderation.refresh();
            if (result.failed()) {
                LOG.warn("Scheduled origin sync failed; " + result.blocked() + " blocked before failure.");
            } else {
                LOG.info("Scheduled origin sync: " + result.blocked() + " new video(s) blocked.");
            }
        } catch (Exception e) {
            LOG.error("Scheduled origin sync threw: " + e.getMessage(), e);
        }
    }
}
