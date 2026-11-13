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

    /**
     * Prefix of the synthetic e-mail every guest row carries (see the V2
     * migration). It exists so a guest can live in a table whose `email` is
     * NOT NULL and UNIQUE, and it never leaves the API – {@code AuthService}
     * strips it before building a {@link com.wiseup.api.application.dto.SessionUser}.
     */
    public static final String GUEST_EMAIL_PREFIX = "guest:";

    /** Placeholder stored in `password_hash`: no password can ever match it. */
    public static final String GUEST_PASSWORD_HASH = "!guest";

    /** Whether the account is currently locked for brute-force protection. */
    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Whether the user has completed the adult self-declaration gate. */
    public boolean isAgeVerified() {
        return ageVerifiedAt != null;
    }
}
