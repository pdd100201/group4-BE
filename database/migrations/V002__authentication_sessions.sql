-- Review and back up the target database before applying this MySQL 8 migration.
-- The script preserves existing users and adds the state required by UC01/UC02/UC03/UC72.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS google_subject VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until DATETIME(6) NULL;

CREATE UNIQUE INDEX uk_users_google_subject ON users (google_subject);

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS occurred_at DATETIME(6) NULL;
UPDATE audit_log SET occurred_at = CURRENT_TIMESTAMP(6) WHERE occurred_at IS NULL;
ALTER TABLE audit_log MODIFY occurred_at DATETIME(6) NOT NULL;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token_hash (token_hash),
    KEY idx_email_verification_user (user_id),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_session_id (session_id),
    KEY idx_auth_session_user (user_id),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES users (id)
);

ALTER TABLE password_reset_token MODIFY token VARCHAR(64) NOT NULL;
