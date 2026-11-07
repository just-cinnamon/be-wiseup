-- =============================================================================
-- V1__initial_schema.sql
-- Initial database schema for WiseUp.
-- Written using ANSI SQL standard types (compatible with PostgreSQL and H2).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(36)  PRIMARY KEY,          -- UUID
    email           VARCHAR(160) NOT NULL UNIQUE,      -- always lowercase
    password_hash   TEXT         NOT NULL,             -- PBKDF2 hash
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_count    INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP WITH TIME ZONE,
    age_verified_at TIMESTAMP WITH TIME ZONE,
    age_method      VARCHAR(50),                       -- 'autodeclaracao'
    age_declared_ip VARCHAR(45),                       -- IPv4 or IPv6
    is_admin        BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ---------------------------------------------------------------------------
-- Sessions (opaque bearer token stored as SHA-256 hash)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
    token_hash  VARCHAR(64)  PRIMARY KEY,              -- SHA-256 hex of raw token
    user_id     VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_user    ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

-- ---------------------------------------------------------------------------
-- Terms acceptances (one row per document version accepted)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS terms_acceptances (
    user_id     VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document    VARCHAR(50)  NOT NULL,   -- 'termos' | 'privacidade'
    version     VARCHAR(20)  NOT NULL,   -- e.g. '2026-11-05'
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, document, version)
);

-- ---------------------------------------------------------------------------
-- Reports (user_id SET NULL intentionally – deletion must not erase report)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id          VARCHAR(36)  PRIMARY KEY,              -- UUID
    video_id    VARCHAR(40)  NOT NULL,
    user_id     VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    reason      VARCHAR(30)  NOT NULL,
    note        VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status      VARCHAR(20)  NOT NULL DEFAULT 'aberta', -- aberta|procede|improcede
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by VARCHAR(36)
);

CREATE INDEX IF NOT EXISTS idx_reports_video  ON reports(video_id);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status, created_at);
CREATE INDEX IF NOT EXISTS idx_reports_user   ON reports(user_id, created_at);

-- ---------------------------------------------------------------------------
-- Blocked videos (small set; loaded into memory on boot)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS blocked_videos (
    video_id   VARCHAR(40)  PRIMARY KEY,
    reason     VARCHAR(30)  NOT NULL,  -- denuncia|admin|origem-removeu
    blocked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    report_id  VARCHAR(36)                            -- nullable
);

-- ---------------------------------------------------------------------------
-- Access logs (Marco Civil da Internet art.15 – 6-month retention)
-- NO foreign key to users intentionally: logs must survive account deletion.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS access_logs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    ip          VARCHAR(45),
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_access_logs_date ON access_logs(accessed_at);
