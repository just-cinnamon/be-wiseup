-- =============================================================================
-- V2__add_guest_user_support.sql
-- Add guest visitor account flag to users table
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_guest BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_users_guest ON users(is_guest, created_at);
