-- Repeatable MySQL 8 migration. Preserve comments, replies and likes.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_v022_lesson_comment_edit_delete$$
CREATE PROCEDURE migrate_v022_lesson_comment_edit_delete()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='lesson_comments' AND COLUMN_NAME='updated_at') THEN
        ALTER TABLE lesson_comments ADD COLUMN updated_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='lesson_comments' AND COLUMN_NAME='deleted') THEN
        ALTER TABLE lesson_comments ADD COLUMN deleted BIT NOT NULL DEFAULT 0;
    END IF;
END$$
DELIMITER ;
CALL migrate_v022_lesson_comment_edit_delete();
DROP PROCEDURE migrate_v022_lesson_comment_edit_delete;
