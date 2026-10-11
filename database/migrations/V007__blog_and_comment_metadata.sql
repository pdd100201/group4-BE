-- UC07/08/21-23/60-65. Review against SHOW CREATE TABLE posts/post_comment first.
-- Existing dates remain NULL: do not fabricate historical creation/update times.
-- Back up and inspect database/preflight/V007__check_blog_schema.sql before deployment.
-- Conditional additions tolerate columns already added by development Hibernate update.
-- No records are deleted. Do not run while application writes are in progress.
DELIMITER //
CREATE PROCEDURE ols_migrate_v007()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='posts' AND COLUMN_NAME='deleted') THEN
        ALTER TABLE posts ADD COLUMN deleted BIT NOT NULL DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='posts' AND COLUMN_NAME='created_at') THEN
        ALTER TABLE posts ADD COLUMN created_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='posts' AND COLUMN_NAME='updated_at') THEN
        ALTER TABLE posts ADD COLUMN updated_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='posts' AND INDEX_NAME='ix_posts_public_updated') THEN
        CREATE INDEX ix_posts_public_updated ON posts (published, deleted, updated_at, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='post_comment' AND COLUMN_NAME='created_at') THEN
        ALTER TABLE post_comment ADD COLUMN created_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='post_comment' AND COLUMN_NAME='updated_at') THEN
        ALTER TABLE post_comment ADD COLUMN updated_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='post_comment' AND INDEX_NAME='ix_comments_post_status_id') THEN
        CREATE INDEX ix_comments_post_status_id ON post_comment (post_id, status, id);
    END IF;
END//
CALL ols_migrate_v007()//
DROP PROCEDURE ols_migrate_v007//
DELIMITER ;
