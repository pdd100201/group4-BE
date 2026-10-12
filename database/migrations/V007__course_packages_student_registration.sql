-- UC12 / BR48-50: additive migration only. Back up and run V004-V006 preflights first.
-- Never assign fabricated package/contact/validity snapshots to old registrations.
-- Apply manually on MySQL 8; this repository does not have a Flyway runner.
DELIMITER //
DROP PROCEDURE IF EXISTS ols_migrate_v007//
CREATE PROCEDURE ols_migrate_v007()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course' AND COLUMN_NAME='id' AND DATA_TYPE='bigint' AND COLUMN_TYPE NOT LIKE '%unsigned%') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Preflight failed: course.id must be signed BIGINT before V007';
    END IF;
    CREATE TABLE IF NOT EXISTS course_package (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        course_id BIGINT NOT NULL,
        duration VARCHAR(20) NOT NULL,
        price DECIMAL(12,0) NOT NULL,
        discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        CONSTRAINT uk_course_package_duration UNIQUE (course_id,duration),
        CONSTRAINT fk_package_course FOREIGN KEY (course_id) REFERENCES course(id)
    );
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='course_package_id') THEN
        ALTER TABLE course_registration ADD COLUMN course_package_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='package_duration_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN package_duration_snapshot VARCHAR(20) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='validity_months_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN validity_months_snapshot INT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='lifetime_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN lifetime_snapshot BOOLEAN NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='discount_percent_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN discount_percent_snapshot DECIMAL(5,2) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='student_name_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN student_name_snapshot VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='student_email_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN student_email_snapshot VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='student_phone_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN student_phone_snapshot VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND COLUMN_NAME='course_package_id' AND REFERENCED_TABLE_NAME='course_package') THEN
        ALTER TABLE course_registration ADD CONSTRAINT fk_registration_package FOREIGN KEY(course_package_id) REFERENCES course_package(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course_registration' AND INDEX_NAME='idx_registration_student_course_status') THEN
        CREATE INDEX idx_registration_student_course_status ON course_registration(student_id,course_id,status,id);
    END IF;
END//
CALL ols_migrate_v007()//
DROP PROCEDURE ols_migrate_v007//
DELIMITER ;
