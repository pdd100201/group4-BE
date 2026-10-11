-- UC44-48: nullable metadata; unknown historical values stay NULL.
-- Safe to rerun after Hibernate update has added some columns. Back up first.
DELIMITER //
DROP PROCEDURE IF EXISTS ols_migrate_v006//
CREATE PROCEDURE ols_migrate_v006()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND COLUMN_NAME = 'created_at') THEN
        ALTER TABLE course_registration ADD COLUMN created_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND COLUMN_NAME = 'package_name_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN package_name_snapshot VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND COLUMN_NAME = 'price_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN price_snapshot DECIMAL(38,2) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND COLUMN_NAME = 'discount_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN discount_snapshot DECIMAL(38,2) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND COLUMN_NAME = 'validity_days_snapshot') THEN
        ALTER TABLE course_registration ADD COLUMN validity_days_snapshot INT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_transactions' AND COLUMN_NAME = 'created_at') THEN
        ALTER TABLE payment_transactions ADD COLUMN created_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND INDEX_NAME = 'idx_registration_created_at') THEN
        CREATE INDEX idx_registration_created_at ON course_registration (created_at, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_registration' AND INDEX_NAME = 'idx_registration_status') THEN
        CREATE INDEX idx_registration_status ON course_registration (status, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_transactions' AND INDEX_NAME = 'idx_transaction_created_at') THEN
        CREATE INDEX idx_transaction_created_at ON payment_transactions (created_at, id);
    END IF;
END//
CALL ols_migrate_v006()//
DROP PROCEDURE ols_migrate_v006//
DELIMITER ;
