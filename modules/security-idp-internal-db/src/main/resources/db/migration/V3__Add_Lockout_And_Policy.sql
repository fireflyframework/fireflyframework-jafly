-- V3: Add account lockout tracking columns to idp_users
-- Supports automatic lockout after configurable failed login attempts

ALTER TABLE idp_users ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0;
ALTER TABLE idp_users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
