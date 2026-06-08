SET @table_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'ban_appeals'
);

SET @has_viewed := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ban_appeals'
      AND column_name = 'viewed'
);
SET @sql := IF(
    @table_exists = 1 AND @has_viewed = 0,
    'ALTER TABLE ban_appeals ADD COLUMN viewed BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_viewed_at := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ban_appeals'
      AND column_name = 'viewed_at'
);
SET @sql := IF(
    @table_exists = 1 AND @has_viewed_at = 0,
    'ALTER TABLE ban_appeals ADD COLUMN viewed_at DATETIME NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_viewed_by := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ban_appeals'
      AND column_name = 'viewed_by'
);
SET @sql := IF(
    @table_exists = 1 AND @has_viewed_by = 0,
    'ALTER TABLE ban_appeals ADD COLUMN viewed_by VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    @table_exists = 1,
    'UPDATE ban_appeals SET viewed = FALSE WHERE viewed IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
