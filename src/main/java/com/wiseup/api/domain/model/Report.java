package com.wiseup.api.domain.model;

import java.time.OffsetDateTime;

/**
 * A content moderation report submitted by a user.
 */
public record Report(
        String id,
        String videoId,
        String userId,
        String reason,
        String note,
        OffsetDateTime createdAt,
        String status,            // "aberta" | "procede" | "improcede"
        OffsetDateTime reviewedAt,
        String reviewedBy
) {

    public boolean isOpen() {
        return "aberta".equals(status);
    }
}
