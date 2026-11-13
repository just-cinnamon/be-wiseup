-- ---------------------------------------------------------------------------
-- V2 – Guest sessions: entry without an account
-- ---------------------------------------------------------------------------
-- The front-end dropped login entirely (ADR-0023 of be-wiseup-one): the +18
-- gate is the only entrance, so declaring adulthood is what must open a
-- session. POST /api/auth/guest does that.
--
-- A guest is a row in `users` with no usable credentials:
--
--   * email          'guest:<uuid>' – synthetic and unique. It can never be
--                    typed into /auth/login: the e-mail format check there
--                    rejects the ':' and the missing '@' before the database is
--                    ever touched.
--   * password_hash  '!guest'       – not a PBKDF2 string, and Crypto.verify()
--                    returns false for anything that does not start with
--                    'PBKDF2$'. No password matches, ever.
--
-- Why synthetic values instead of NULL? Because `email` and `password_hash` are
-- NOT NULL since V1, and dropping a NOT NULL is spelled differently in
-- PostgreSQL and in H2 — and the test profile runs these same migrations on H2
-- (see %test in application.properties). A migration that runs on both engines
-- is worth more here than two prettier columns. The synthetic e-mail never
-- leaves the API: AuthService hides it from SessionUser.
--
-- Keeping the guest inside `users` is what makes every existing query work
-- untouched: sessions, terms_acceptances, access_logs and reports all point at
-- users(id), and a guest has a real one.
-- ---------------------------------------------------------------------------

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_guest BOOLEAN DEFAULT FALSE NOT NULL;

-- Used by the hourly purge of guests whose session is already gone.
CREATE INDEX IF NOT EXISTS idx_users_guest ON users(is_guest);
