package com.wiseup.api.domain.repository;

import com.wiseup.api.domain.model.BlockedVideo;
import com.wiseup.api.domain.model.Report;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Port for report and blocked-video persistence.
 */
public interface ReportRepository {

    /**
     * Inserts the report and, if {@code blockNow}, also inserts a blocked_videos
     * row in the same transaction. Returns whether the video was blocked.
     */
    boolean createMaybeBlocking(String id, String videoId, String userId,
                                String reason, String note,
                                OffsetDateTime createdAt, boolean blockNow);

    int countByUserSince(String userId, OffsetDateTime since);

    Optional<Report> findById(String id);

    List<Report> listOpen(int limit);

    /** Marks the report's status; returns false if already reviewed. */
    boolean review(String id, String status, OffsetDateTime reviewedAt, String reviewedBy);

    // ---- Blocked videos ----

    void block(String videoId, String reason, OffsetDateTime blockedAt, String reportId);

    /**
     * Removes the blocked_videos row only if it was inserted by this specific
     * report. Returns true if a row was actually removed.
     */
    boolean unblockFromReport(String videoId, String reportId);

    List<String> listBlockedIds();

    List<BlockedVideo> listBlocked(int limit);
}
