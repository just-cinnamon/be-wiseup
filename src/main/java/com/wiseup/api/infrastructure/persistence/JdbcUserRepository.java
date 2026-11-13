package com.wiseup.api.infrastructure.persistence;

import com.wiseup.api.domain.model.Session;
import com.wiseup.api.domain.model.TermsAcceptance;
import com.wiseup.api.domain.model.User;
import com.wiseup.api.domain.repository.UserRepository;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * JDBC implementation of {@link UserRepository}.
 * No ORM: plain SQL for maximum transparency and control over every query.
 */
@ApplicationScoped
public class JdbcUserRepository implements UserRepository {

    private static final Logger LOG = Logger.getLogger(JdbcUserRepository.class);

    @Inject
    AgroalDataSource ds;

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------

    @Override
    public Optional<User> findByEmail(String email) {
        final String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("findByEmail", e);
        }
    }

    @Override
    public Optional<User> findById(String userId) {
        final String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("findById", e);
        }
    }

    @Override
    public User create(String email, String passwordHash) {
        final String id = UUID.randomUUID().toString();
        final String sql = """
                INSERT INTO users (id, email, password_hash, created_at, failed_count)
                VALUES (?, ?, ?, NOW(), 0)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("create user", e);
        }
        return findByEmail(email).orElseThrow();
    }

    @Override
    public User createGuest(String ip, OffsetDateTime at, Map<String, String> versions) {
        final String id = UUID.randomUUID().toString();
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO users (id, email, password_hash, created_at, failed_count,
                                           age_verified_at, age_method, age_declared_ip, is_guest)
                        VALUES (?, ?, ?, ?, 0, ?, 'autodeclaracao', ?, TRUE)
                        """)) {
                    ps.setString(1, id);
                    ps.setString(2, User.GUEST_EMAIL_PREFIX + id);
                    ps.setString(3, User.GUEST_PASSWORD_HASH);
                    ps.setObject(4, at);
                    ps.setObject(5, at);
                    ps.setString(6, ip);
                    ps.executeUpdate();
                }

                // The gate is one click: age and both documents are accepted together.
                for (Map.Entry<String, String> entry : versions.entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO terms_acceptances (user_id, document, version, accepted_at)
                            VALUES (?, ?, ?, ?)
                            """)) {
                        ps.setString(1, id);
                        ps.setString(2, entry.getKey());
                        ps.setString(3, entry.getValue());
                        ps.setObject(4, at);
                        ps.executeUpdate();
                    }
                }

                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("createGuest", e);
        }
        return findById(id).orElseThrow();
    }

    @Override
    public int purgeOrphanGuests() {
        final String sql = """
                DELETE FROM users
                 WHERE is_guest = TRUE
                   AND NOT EXISTS (SELECT 1 FROM sessions s WHERE s.user_id = users.id)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            // Housekeeping: a failure here must never break a request.
            LOG.error("purgeOrphanGuests failed: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean remove(String userId) {
        final String sql = "DELETE FROM users WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("remove user", e);
        }
    }

    @Override
    public void setLockState(String userId, int failedCount, OffsetDateTime lockedUntil) {
        final String sql = "UPDATE users SET failed_count = ?, locked_until = ? WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, failedCount);
            if (lockedUntil == null) ps.setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
            else ps.setObject(2, lockedUntil);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("setLockState", e);
        }
    }

    // -------------------------------------------------------------------------
    // Sessions
    // -------------------------------------------------------------------------

    @Override
    public void createSession(String tokenHash, String userId, OffsetDateTime expiresAt) {
        final String sql = """
                INSERT INTO sessions (token_hash, user_id, created_at, expires_at)
                VALUES (?, ?, NOW(), ?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.setString(2, userId);
            ps.setObject(3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("createSession", e);
        }
    }

    @Override
    public Optional<Session> findSession(String tokenHash,
                                         String termosVersion,
                                         String privacidadeVersion) {
        final String sql = """
                SELECT u.id, u.email, u.age_verified_at, u.is_admin, u.is_guest,
                       s.expires_at,
                       (SELECT COUNT(*) FROM terms_acceptances ta
                         WHERE ta.user_id = u.id
                           AND ((ta.document = 'termos'      AND ta.version = ?)
                             OR (ta.document = 'privacidade' AND ta.version = ?))
                       ) AS accepted_count
                  FROM sessions s
                  JOIN users u ON u.id = s.user_id
                 WHERE s.token_hash = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, termosVersion);
            ps.setString(2, privacidadeVersion);
            ps.setString(3, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                var session = new Session(
                        tokenHash,
                        rs.getString("id"),
                        rs.getString("email"),
                        null,
                        toOdt(rs, "expires_at"),
                        toOdt(rs, "age_verified_at"),
                        rs.getInt("accepted_count"),
                        rs.getBoolean("is_admin"),
                        rs.getBoolean("is_guest")
                );
                return Optional.of(session);
            }
        } catch (SQLException e) {
            throw new PersistenceException("findSession", e);
        }
    }

    @Override
    public boolean removeSession(String tokenHash) {
        final String sql = "DELETE FROM sessions WHERE token_hash = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("removeSession", e);
        }
    }

    @Override
    public void removeExpiredSessions(OffsetDateTime before) {
        final String sql = "DELETE FROM sessions WHERE expires_at <= ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, before);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("removeExpiredSessions failed: " + e.getMessage());
        }
    }

    @Override
    public Session createGuestSession(String tokenHash, String clientIp, OffsetDateTime now,
                                      OffsetDateTime expiresAt, Map<String, String> versions) {
        final String userId = UUID.randomUUID().toString();
        final String guestEmail = "guest_" + userId + "@visitor.wiseup.test";
        final String dummyHash = "DISABLED_GUEST_ACCOUNT";

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Insert guest user
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO users (id, email, password_hash, created_at, failed_count,
                                           age_verified_at, age_method, age_declared_ip, is_admin, is_guest)
                        VALUES (?, ?, ?, ?, 0, ?, 'autodeclaracao', ?, FALSE, TRUE)
                        """)) {
                    ps.setString(1, userId);
                    ps.setString(2, guestEmail);
                    ps.setString(3, dummyHash);
                    ps.setObject(4, now);
                    ps.setObject(5, now);
                    ps.setString(6, clientIp);
                    ps.executeUpdate();
                }

                // 2. Insert terms acceptances for all current versions
                for (Map.Entry<String, String> entry : versions.entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO terms_acceptances (user_id, document, version, accepted_at)
                            VALUES (?, ?, ?, ?)
                            """)) {
                        ps.setString(1, userId);
                        ps.setString(2, entry.getKey());
                        ps.setString(3, entry.getValue());
                        ps.setObject(4, now);
                        ps.executeUpdate();
                    }
                }

                // 3. Insert active session
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO sessions (token_hash, user_id, created_at, expires_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    ps.setString(1, tokenHash);
                    ps.setString(2, userId);
                    ps.setObject(3, now);
                    ps.setObject(4, expiresAt);
                    ps.executeUpdate();
                }

                c.commit();

                return new Session(
                        tokenHash,
                        userId,
                        guestEmail,
                        now,
                        expiresAt,
                        now,
                        versions.size(),
                        false,
                        true
                );
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("createGuestSession", e);
        }
    }

    // -------------------------------------------------------------------------
    // Age gate + Terms
    // -------------------------------------------------------------------------

    @Override
    public void declareAdultAndAcceptTerms(String userId, String ip,
                                           OffsetDateTime at,
                                           Map<String, String> versions) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Age declaration – idempotent
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE users
                           SET age_verified_at = ?, age_method = 'autodeclaracao',
                               age_declared_ip = ?
                         WHERE id = ? AND age_verified_at IS NULL
                        """)) {
                    ps.setObject(1, at);
                    ps.setString(2, ip);
                    ps.setString(3, userId);
                    ps.executeUpdate();
                }

                // Terms acceptances – idempotent
                for (Map.Entry<String, String> entry : versions.entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO terms_acceptances (user_id, document, version, accepted_at)
                            SELECT ?, ?, ?, ?
                            WHERE NOT EXISTS (
                                SELECT 1 FROM terms_acceptances
                                 WHERE user_id = ? AND document = ? AND version = ?
                            )
                            """)) {
                        ps.setString(1, userId);
                        ps.setString(2, entry.getKey());
                        ps.setString(3, entry.getValue());
                        ps.setObject(4, at);
                        ps.setString(5, userId);
                        ps.setString(6, entry.getKey());
                        ps.setString(7, entry.getValue());
                        ps.executeUpdate();
                    }
                }

                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("declareAdultAndAcceptTerms", e);
        }
    }

    @Override
    public List<TermsAcceptance> listAcceptances(String userId) {
        final String sql = """
                SELECT document, version, accepted_at
                  FROM terms_acceptances
                 WHERE user_id = ?
                 ORDER BY accepted_at DESC
                """;
        List<TermsAcceptance> result = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TermsAcceptance(
                            rs.getString("document"),
                            rs.getString("version"),
                            toOdt(rs, "accepted_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("listAcceptances", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Access log
    // -------------------------------------------------------------------------

    @Override
    public void recordAccessLog(String userId, String ip) {
        final String sql = "INSERT INTO access_logs (user_id, ip, accessed_at) VALUES (?, ?, NOW())";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Never propagate – access-log failure must not block login
            LOG.error("Failed to record access log: " + e.getMessage());
        }
    }

    @Override
    public int purgeAccessLogs(OffsetDateTime before) {
        final String sql = "DELETE FROM access_logs WHERE accessed_at < ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, before);
            return ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to purge access logs: " + e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                toOdt(rs, "created_at"),
                rs.getInt("failed_count"),
                toOdt(rs, "locked_until"),
                toOdt(rs, "age_verified_at"),
                rs.getString("age_method"),
                rs.getString("age_declared_ip"),
                rs.getBoolean("is_admin"),
                rs.getBoolean("is_guest")
        );
    }

    private OffsetDateTime toOdt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        if (ts == null) return null;
        return ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    // -------------------------------------------------------------------------

    /** Wraps checked SQLExceptions into an unchecked runtime exception. */
    public static class PersistenceException extends RuntimeException {
        public PersistenceException(String operation, Throwable cause) {
            super("DB error during: " + operation, cause);
        }
    }
}
