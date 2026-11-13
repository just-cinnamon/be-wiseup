package com.wiseup.api.domain.model;

import java.time.OffsetDateTime;

/**
 * An active session identified by the SHA-256 hash of the opaque bearer token.
 * The raw token lives only in the HTTP response; the server never stores it.
 */
public record Session(
        String tokenHash,
        String userId,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime ageVerifiedAt,
        int acceptedTermsCount,   // how many current-version documents are accepted
        boolean isAdmin,
        boolean isGuest
) {

    public Session(String tokenHash, String userId, String email, OffsetDateTime createdAt,
                   OffsetDateTime expiresAt, OffsetDateTime ageVerifiedAt,
                   int acceptedTermsCount, boolean isAdmin) {
        this(tokenHash, userId, email, createdAt, expiresAt, ageVerifiedAt,
                acceptedTermsCount, isAdmin, false);
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isAgeVerified() {
        return ageVerifiedAt != null;
    }

    /** True when both 'termos' and 'privacidade' on the current version are accepted. */
    public boolean hasAcceptedAllTerms(int requiredCount) {
        return acceptedTermsCount >= requiredCount;
    }
}
