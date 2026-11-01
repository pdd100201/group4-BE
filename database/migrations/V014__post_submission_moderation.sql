-- Add lifecycle states for user-submitted blog articles.
-- Review the target database and back it up before applying this migration.
-- Preserve moderation states and synchronize historical states with the public visibility flag.
DELIMITER //
CREATE PROCEDURE ols_migrate_v014()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='posts' AND COLUMN_NAME='status') THEN
        ALTER TABLE posts ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
    ELSE
        ALTER TABLE posts MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='posts' AND COLUMN_NAME='published_at') THEN
        ALTER TABLE posts ADD COLUMN published_at DATETIME(6) NULL;
    END IF;

    UPDATE posts SET status=CASE
        WHEN published=b'1' THEN 'PUBLISHED'
        WHEN status IN ('PENDING_REVIEW','REJECTED') THEN status
        ELSE 'DRAFT'
    END
    WHERE status IS NULL OR status NOT IN ('DRAFT','PENDING_REVIEW','PUBLISHED','REJECTED')
       OR (published=b'1' AND status<>'PUBLISHED')
       OR (published=b'0' AND status='PUBLISHED');
END//
CALL ols_migrate_v014()//
DROP PROCEDURE ols_migrate_v014//
DELIMITER ;
