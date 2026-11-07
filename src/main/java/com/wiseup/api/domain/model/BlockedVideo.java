package com.wiseup.api.domain.model;

import java.time.OffsetDateTime;

/**
 * A video blocked from the catalogue. Kept in-memory as a Set of IDs for
 * O(1) lookup on every catalogue request.
 */
public record BlockedVideo(
        String videoId,
        String reason,           // "denuncia" | "admin" | "origem-removeu"
        OffsetDateTime blockedAt,
        String reportId          // nullable
) {}
