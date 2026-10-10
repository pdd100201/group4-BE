-- The current application stores user roles in user_roles (many-to-many).
-- Keep the legacy users.role_id column for compatibility, but do not require it
-- for users created by the current JPA model.
ALTER TABLE users
    MODIFY COLUMN role_id TINYINT UNSIGNED NULL;

-- These legacy audit columns are not part of the current entity because
-- occurred_at is the authoritative event timestamp.
ALTER TABLE audit_log
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);
