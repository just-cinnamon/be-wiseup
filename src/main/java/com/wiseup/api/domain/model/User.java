package com.wiseup.api.domain.model;

import java.time.OffsetDateTime;

/**
 * Aggregate root representing a registered user.
 * This is a plain Java record – no JPA annotations, keeping the domain layer
 * free of infrastructure concerns.
 */
public record User(
        String id,
        String email,
        String passwordHash,
        OffsetDateTime createdAt,
        int failedCount,
        OffsetDateTime lockedUntil,
        OffsetDateTime ageVerifiedAt,
        String ageMethod,
        String ageDeclaredIp,
        boolean isAdmin
) {

    /** Whether the account is currently locked for brute-force protection. */
    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Whether the user has completed the adult self-declaration gate. */
    public boolean isAgeVerified() {
        return ageVerifiedAt != null;
    }
}
