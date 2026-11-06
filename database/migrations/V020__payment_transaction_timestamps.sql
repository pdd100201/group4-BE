-- MySQL 8. Back up before running against the configured application database.
-- Existing valid timestamps are preserved. Unknown historical times use UTC now.
-- Add nullable columns and backfill before enforcing NOT NULL on populated tables.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_v020_transaction_timestamps$$
CREATE PROCEDURE migrate_v020_transaction_timestamps()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='payment_transactions' AND COLUMN_NAME='created_at') THEN
        ALTER TABLE payment_transactions ADD COLUMN created_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='payment_transactions' AND COLUMN_NAME='updated_at') THEN
        ALTER TABLE payment_transactions ADD COLUMN updated_at DATETIME(6) NULL;
    END IF;
    UPDATE payment_transactions SET created_at=UTC_TIMESTAMP(6)
        WHERE created_at IS NULL OR CAST(created_at AS CHAR) LIKE '0000-%';
    UPDATE payment_transactions SET updated_at=created_at
        WHERE updated_at IS NULL OR CAST(updated_at AS CHAR) LIKE '0000-%';
    ALTER TABLE payment_transactions
        MODIFY COLUMN created_at DATETIME(6) NOT NULL,
        MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
END$$
DELIMITER ;
CALL migrate_v020_transaction_timestamps();
DROP PROCEDURE migrate_v020_transaction_timestamps;
