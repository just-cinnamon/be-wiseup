package com.wiseup.api.domain.repository;

import com.wiseup.api.domain.model.User;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Port (interface) for user persistence.
 * Implementations live in the infrastructure layer.
 */
public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(String userId);

    /** Creates and returns the new user (ID assigned inside). */
    User create(String email, String passwordHash);

    /** Delete the user; sessions cascade by DB constraint. */
    boolean remove(String userId);

    void setLockState(String userId, int failedCount, OffsetDateTime lockedUntil);

    void createSession(String tokenHash, String userId, OffsetDateTime expiresAt);

    /**
     * Returns the session joined with user data and the count of accepted
     * current-version terms documents.
     *
     * @param termosVersion     current 'termos' version string
     * @param privacidadeVersion current 'privacidade' version string
     */
    Optional<com.wiseup.api.domain.model.Session> findSession(
            String tokenHash,
            String termosVersion,
            String privacidadeVersion
    );

    boolean removeSession(String tokenHash);

    void removeExpiredSessions(OffsetDateTime before);

    /**
     * Atomically creates an anonymous visitor account, records age verification and terms acceptances,
     * and establishes an active session.
     */
    com.wiseup.api.domain.model.Session createGuestSession(
            String tokenHash,
            String clientIp,
            OffsetDateTime now,
            OffsetDateTime expiresAt,
            java.util.Map<String, String> versions
    );

    // ---- Age gate ----

    /**
     * Atomically declares adult-age AND inserts term acceptances.
     * Idempotent: second call does nothing (WHERE age_verified_at IS NULL).
     */
    void declareAdultAndAcceptTerms(String userId, String ip, OffsetDateTime at,
                                    java.util.Map<String, String> versions);

    java.util.List<com.wiseup.api.domain.model.TermsAcceptance> listAcceptances(String userId);

    // ---- Access log ----

    void recordAccessLog(String userId, String ip);

    /** Deletes rows older than the given cutoff. Returns count removed. */
    int purgeAccessLogs(OffsetDateTime before);
}
