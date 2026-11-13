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

    /**
     * Creates a guest: a user row with no usable credentials, with the adult
     * self-declaration and the term acceptances already recorded – all in one
     * transaction, so a guest never exists in a half-declared state.
     *
     * @param ip       client IP, stored with the declaration (Marco Civil)
     * @param at       declaration timestamp
     * @param versions current legal versions, one acceptance row each
     */
    User createGuest(String ip, OffsetDateTime at, java.util.Map<String, String> versions);

    /**
     * Deletes guest rows that have no session left – their session expired or
     * they logged out. Accounts are never touched.
     *
     * @return number of rows removed
     */
    int purgeOrphanGuests();

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
