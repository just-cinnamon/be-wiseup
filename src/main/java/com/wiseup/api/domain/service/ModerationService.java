package com.wiseup.api.domain.service;

import com.wiseup.api.application.dto.*;
import com.wiseup.api.domain.model.BlockedVideo;
import com.wiseup.api.domain.model.Report;
import com.wiseup.api.domain.repository.ReportRepository;
import com.wiseup.api.infrastructure.external.MediaProviderClient;
import com.wiseup.api.presentation.exception.InvalidParamsException;
import com.wiseup.api.presentation.exception.NotFoundException;
import com.wiseup.api.presentation.exception.TooManyReportsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * Content moderation and compliance domain service.
 *
 * <p>Keeps an in-memory {@link Set} of blocked IDs for O(1) per-request lookup.
 */
@ApplicationScoped
public class ModerationService {

    private static final Logger LOG = Logger.getLogger(ModerationService.class);

    public static final List<String> REASONS = List.of(
            "menor", "sem_consentimento", "violencia", "direitos_autorais", "outro");

    /** Reasons that cause an immediate block without admin review. */
    public static final Set<String> IMMEDIATE_BLOCK = Set.of("menor", "sem_consentimento");

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,40}$");
    private static final int NOTE_MAX      = 500;
    private static final int MAX_PER_HOUR  = 10;

    @Inject
    ReportRepository reports;

    @Inject
    MediaProviderClient client;

    /** Thread-safe in-memory set populated on startup and updated on block events. */
    private final Set<String> blockedIds = new CopyOnWriteArraySet<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isBlocked(String videoId) {
        return blockedIds.contains(videoId);
    }

    /** Re-reads the blocked list from DB. Called at startup and after sync. */
    public int refresh() {
        List<String> ids = reports.listBlockedIds();
        blockedIds.clear();
        blockedIds.addAll(ids);
        return blockedIds.size();
    }

    public ReportResponse report(String userId, ReportRequest req) {
        validateReport(req);

        OffsetDateTime now    = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime since  = now.minusHours(1);

        if (reports.countByUserSince(userId, since) >= MAX_PER_HOUR) {
            throw new TooManyReportsException();
        }

        String id      = UUID.randomUUID().toString();
        boolean blockNow = IMMEDIATE_BLOCK.contains(req.reason());

        boolean blocked = reports.createMaybeBlocking(
                id, req.videoId(), userId, req.reason(),
                req.note() == null ? null : req.note().trim(),
                now, blockNow);

        if (blocked) blockedIds.add(req.videoId());

        return new ReportResponse(id, blocked);
    }

    public List<Report> listOpen() {
        return reports.listOpen(100);
    }

    public List<BlockedVideo> listBlocked() {
        return reports.listBlocked(200);
    }

    public ReviewResponse review(String id, boolean procede, String adminId) {
        Report report = reports.findById(id).orElseThrow(NotFoundException::new);
        if (!report.isOpen()) {
            throw new InvalidParamsException("Esta denúncia já foi analisada.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String status = procede ? "procede" : "improcede";
        reports.review(id, status, now, adminId);

        if (procede) {
            reports.block(report.videoId(), "denuncia", now, id);
            blockedIds.add(report.videoId());
            return new ReviewResponse("procede", true);
        }

        boolean unblocked = reports.unblockFromReport(report.videoId(), id);
        if (unblocked) blockedIds.remove(report.videoId());
        return new ReviewResponse("improcede", blockedIds.contains(report.videoId()));
    }

    public BlockResponse block(String videoId, String adminId) {
        if (!VIDEO_ID_PATTERN.matcher(videoId).matches()) {
            throw new InvalidParamsException("Vídeo inválido.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String reportRef = adminId != null ? "admin:" + adminId : null;
        reports.block(videoId, "admin", now, reportRef);
        blockedIds.add(videoId);
        return new BlockResponse(videoId, true);
    }

    public SyncResult syncWithOrigin(int pages) {
        int blocked = 0;
        try {
            for (int page = 1; page <= pages; page++) {
                List<String> ids = client.deletedVideos(page);
                for (String videoId : ids) {
                    if (!blockedIds.contains(videoId)) {
                        reports.block(videoId, "origem-removeu",
                                OffsetDateTime.now(ZoneOffset.UTC), null);
                        blockedIds.add(videoId);
                        blocked++;
                    }
                }
            }
            return new SyncResult(blocked, false);
        } catch (Exception e) {
            LOG.error("Origin sync failed: " + e.getMessage());
            return new SyncResult(blocked, true);
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void validateReport(ReportRequest req) {
        String videoId = req.videoId() == null ? "" : req.videoId().trim();
        String reason  = req.reason()  == null ? "" : req.reason().trim();
        String note    = req.note()    == null ? "" : req.note();

        if (!VIDEO_ID_PATTERN.matcher(videoId).matches()) {
            throw new InvalidParamsException("Vídeo inválido.");
        }
        if (!REASONS.contains(reason)) {
            throw new InvalidParamsException("reason deve ser: " + String.join(", ", REASONS) + ".");
        }
        if (note.length() > NOTE_MAX) {
            throw new InvalidParamsException("A observação pode ter no máximo " + NOTE_MAX + " caracteres.");
        }
    }

    public record SyncResult(int blocked, boolean failed) {}
}
