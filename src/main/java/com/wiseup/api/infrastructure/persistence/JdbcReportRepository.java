package com.wiseup.api.infrastructure.persistence;

import com.wiseup.api.domain.model.BlockedVideo;
import com.wiseup.api.domain.model.Report;
import com.wiseup.api.domain.repository.ReportRepository;
import com.wiseup.api.infrastructure.persistence.JdbcUserRepository.PersistenceException;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link ReportRepository}.
 */
@ApplicationScoped
public class JdbcReportRepository implements ReportRepository {

    @Inject
    AgroalDataSource ds;

    // -------------------------------------------------------------------------
    // Reports
    // -------------------------------------------------------------------------

    @Override
    public boolean createMaybeBlocking(String id, String videoId, String userId,
                                       String reason, String note,
                                       OffsetDateTime createdAt, boolean blockNow) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Insert report
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO reports (id, video_id, user_id, reason, note, created_at, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'aberta')
                        """)) {
                    ps.setString(1, id);
                    ps.setString(2, videoId);
                    ps.setString(3, userId);
                    ps.setString(4, reason);
                    ps.setString(5, note);
                    ps.setObject(6, createdAt);
                    ps.executeUpdate();
                }

                boolean blocked = false;
                if (blockNow) {
                    // ON CONFLICT DO NOTHING: first report wins
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO blocked_videos (video_id, reason, blocked_at, report_id)
                            VALUES (?, 'denuncia', ?, ?)
                            ON CONFLICT (video_id) DO NOTHING
                            """)) {
                        ps.setString(1, videoId);
                        ps.setObject(2, createdAt);
                        ps.setString(3, id);
                        ps.executeUpdate();
                    }
                    blocked = true;
                }

                c.commit();
                return blocked;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("createMaybeBlocking", e);
        }
    }

    @Override
    public int countByUserSince(String userId, OffsetDateTime since) {
        final String sql = """
                SELECT COUNT(*)::int AS total FROM reports
                 WHERE user_id = ? AND created_at >= ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setObject(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("countByUserSince", e);
        }
    }

    @Override
    public Optional<Report> findById(String id) {
        final String sql = """
                SELECT id, video_id, user_id, reason, note, created_at, status,
                       reviewed_at, reviewed_by
                  FROM reports WHERE id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapReport(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("findById report", e);
        }
    }

    @Override
    public List<Report> listOpen(int limit) {
        final String sql = """
                SELECT id, video_id, user_id, reason, note, created_at, status,
                       reviewed_at, reviewed_by
                  FROM reports
                 WHERE status = 'aberta'
                 ORDER BY created_at ASC
                 LIMIT ?
                """;
        List<Report> result = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapReport(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException("listOpen reports", e);
        }
        return result;
    }

    @Override
    public boolean review(String id, String status, OffsetDateTime reviewedAt, String reviewedBy) {
        final String sql = """
                UPDATE reports
                   SET status = ?, reviewed_at = ?, reviewed_by = ?
                 WHERE id = ? AND status = 'aberta'
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, reviewedAt);
            ps.setString(3, reviewedBy);
            ps.setString(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("review report", e);
        }
    }

    // -------------------------------------------------------------------------
    // Blocked videos
    // -------------------------------------------------------------------------

    @Override
    public void block(String videoId, String reason, OffsetDateTime blockedAt, String reportId) {
        final String sql = """
                INSERT INTO blocked_videos (video_id, reason, blocked_at, report_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (video_id) DO NOTHING
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, videoId);
            ps.setString(2, reason);
            ps.setObject(3, blockedAt);
            ps.setString(4, reportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("block video", e);
        }
    }

    @Override
    public boolean unblockFromReport(String videoId, String reportId) {
        final String sql = """
                DELETE FROM blocked_videos
                 WHERE video_id = ? AND reason = 'denuncia' AND report_id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, videoId);
            ps.setString(2, reportId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("unblockFromReport", e);
        }
    }

    @Override
    public List<String> listBlockedIds() {
        List<String> ids = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT video_id FROM blocked_videos")) {
            while (rs.next()) ids.add(rs.getString("video_id"));
        } catch (SQLException e) {
            throw new PersistenceException("listBlockedIds", e);
        }
        return ids;
    }

    @Override
    public List<BlockedVideo> listBlocked(int limit) {
        final String sql = """
                SELECT video_id, reason, blocked_at, report_id
                  FROM blocked_videos
                 ORDER BY blocked_at DESC
                 LIMIT ?
                """;
        List<BlockedVideo> result = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BlockedVideo(
                            rs.getString("video_id"),
                            rs.getString("reason"),
                            toOdt(rs, "blocked_at"),
                            rs.getString("report_id")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("listBlocked", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Report mapReport(ResultSet rs) throws SQLException {
        return new Report(
                rs.getString("id"),
                rs.getString("video_id"),
                rs.getString("user_id"),
                rs.getString("reason"),
                rs.getString("note"),
                toOdt(rs, "created_at"),
                rs.getString("status"),
                toOdt(rs, "reviewed_at"),
                rs.getString("reviewed_by")
        );
    }

    private OffsetDateTime toOdt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        if (ts == null) return null;
        return ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
