-- MySQL 8. Back up first and apply before starting the updated backend.
-- No rows/IDs/payment records are deleted. Repeat execution is safe.
-- Historical course timestamps are unknown: missing dates use migration time,
-- not a fabricated historical creation date. Existing valid dates are retained.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_v011_course_progress$$
CREATE PROCEDURE migrate_v011_course_progress()
BEGIN
    DECLARE legacy_fk_count INT DEFAULT 0;
    DECLARE progress_count BIGINT DEFAULT 0;
    DECLARE invalid_count BIGINT DEFAULT 0;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration'
        AND COLUMN_NAME='id' AND COLUMN_TYPE='bigint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V011 requires course_registration.id BIGINT signed; inspect schema first.';
    END IF;

    SELECT COUNT(*) INTO progress_count FROM student_lesson_progress;
    SELECT COUNT(*) INTO legacy_fk_count FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='student_lesson_progress'
        AND COLUMN_NAME='registration_id' AND REFERENCED_TABLE_NAME IS NOT NULL
        AND (REFERENCED_TABLE_NAME<>'course_registration' OR REFERENCED_COLUMN_NAME<>'id');
    IF legacy_fk_count>0 AND progress_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V011 stopped: legacy progress rows require explicit registration ID mapping; no rows changed.';
    END IF;
    SELECT COUNT(*) INTO invalid_count FROM student_lesson_progress p
        LEFT JOIN course_registration r ON r.id=p.registration_id
        WHERE p.registration_id IS NULL OR p.registration_id>9223372036854775807 OR r.id IS NULL;
    IF invalid_count>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V011 stopped: null, out-of-range or orphan progress registration IDs; no rows changed.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course' AND COLUMN_NAME='created_at') THEN
        ALTER TABLE course ADD COLUMN created_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course' AND COLUMN_NAME='updated_at') THEN
        ALTER TABLE course ADD COLUMN updated_at DATETIME(6) NULL;
    END IF;
    UPDATE course SET created_at=UTC_TIMESTAMP(6)
        WHERE created_at IS NULL OR CAST(created_at AS CHAR) LIKE '0000-%';
    UPDATE course SET updated_at=created_at
        WHERE updated_at IS NULL OR CAST(updated_at AS CHAR) LIKE '0000-%';
    ALTER TABLE course MODIFY COLUMN created_at DATETIME(6) NOT NULL;
    ALTER TABLE course MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

    -- Empty legacy progress has no IDs to remap. Remove only its registration FK;
    -- leave lesson/student relationships and all other tables unchanged.
    IF legacy_fk_count>0 THEN
        SELECT CONCAT('ALTER TABLE student_lesson_progress ',
            GROUP_CONCAT(DISTINCT CONCAT('DROP FOREIGN KEY `',REPLACE(CONSTRAINT_NAME,'`','``'),'`') SEPARATOR ', '))
            INTO @v011_drop_fk
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='student_lesson_progress'
            AND COLUMN_NAME='registration_id' AND REFERENCED_TABLE_NAME IS NOT NULL
            AND (REFERENCED_TABLE_NAME<>'course_registration' OR REFERENCED_COLUMN_NAME<>'id');
        PREPARE v011_statement FROM @v011_drop_fk;
        EXECUTE v011_statement;
        DEALLOCATE PREPARE v011_statement;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='student_lesson_progress'
        AND COLUMN_NAME='registration_id' AND COLUMN_TYPE='bigint' AND IS_NULLABLE='NO') THEN
        ALTER TABLE student_lesson_progress MODIFY COLUMN registration_id BIGINT NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='student_lesson_progress'
        AND COLUMN_NAME='registration_id' AND REFERENCED_TABLE_NAME='course_registration'
        AND REFERENCED_COLUMN_NAME='id') THEN
        ALTER TABLE student_lesson_progress ADD CONSTRAINT fk_progress_current_registration
            FOREIGN KEY (registration_id) REFERENCES course_registration(id);
    END IF;
END$$
DELIMITER ;
CALL migrate_v011_course_progress();
DROP PROCEDURE migrate_v011_course_progress;
