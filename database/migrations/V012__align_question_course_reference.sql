-- MySQL 8. Back up first. Run against the configured application database.
-- Replaces the obsolete questions -> courses FK with questions -> course.
-- No question, answer, quiz, course or payment data is deleted or renumbered.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_v012_question_course$$
CREATE PROCEDURE migrate_v012_question_course()
BEGIN
    DECLARE invalid_count BIGINT DEFAULT 0;
    DECLARE legacy_count INT DEFAULT 0;
    SELECT COUNT(*) INTO invalid_count FROM questions q LEFT JOIN course c ON c.id=q.course_id
        WHERE c.id IS NULL;
    IF invalid_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V012 stopped: questions reference missing current courses. Review ID mapping; no rows changed.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions' AND COLUMN_NAME='course_id' AND COLUMN_TYPE='bigint')
        OR NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course' AND COLUMN_NAME='id' AND COLUMN_TYPE='bigint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V012 requires signed BIGINT current course IDs. Run schema preflight first.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions' AND COLUMN_NAME='course_id'
        AND REFERENCED_TABLE_NAME='course' AND REFERENCED_COLUMN_NAME='id') THEN
        ALTER TABLE questions ADD CONSTRAINT fk_question_current_course
            FOREIGN KEY(course_id) REFERENCES course(id);
    END IF;
    SELECT COUNT(*) INTO legacy_count FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions' AND COLUMN_NAME='course_id'
        AND REFERENCED_TABLE_NAME='courses';
    IF legacy_count>0 THEN
        SELECT CONCAT('ALTER TABLE questions ',
            GROUP_CONCAT(DISTINCT CONCAT('DROP FOREIGN KEY `',REPLACE(CONSTRAINT_NAME,'`','``'),'`') SEPARATOR ', '))
            INTO @v012_drop_fk FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='questions' AND COLUMN_NAME='course_id'
            AND REFERENCED_TABLE_NAME='courses';
        PREPARE v012_statement FROM @v012_drop_fk;
        EXECUTE v012_statement;
        DEALLOCATE PREPARE v012_statement;
    END IF;
END$$
DELIMITER ;
CALL migrate_v012_question_course();
DROP PROCEDURE migrate_v012_question_course;
