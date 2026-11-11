-- Adds one nullable JSON column. Database remains at 30 tables.
USE online_learning_new;
SET @review_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='course' AND COLUMN_NAME='review_baseline'),
  'SELECT ''review_baseline already exists'' AS result',
  'ALTER TABLE course ADD COLUMN review_baseline JSON NULL'
);
PREPARE review_stmt FROM @review_sql;
EXECUTE review_stmt;
DEALLOCATE PREPARE review_stmt;
