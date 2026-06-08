-- Migration: Create XP events table for tracking user experience points
-- This replaces the forum_xp_events table from the old forum-user-service

-- Flyway runs before Hibernate/JPA schema generation. On a fresh database, the `users` table
-- might not exist yet (this project uses `spring.jpa.hibernate.ddl-auto=update`), so ensure a
-- minimal `users` table exists to satisfy the FK.
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    xp BIGINT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

-- If the table already exists (e.g. created by Hibernate), ensure the `xp` column exists
-- before creating an index on it.
SET @users_table_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
);
SET @users_has_xp := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'xp'
);
SET @sql := IF(
    @users_table_exists = 1 AND @users_has_xp = 0,
    'ALTER TABLE users ADD COLUMN xp BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS xp_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    new_total BIGINT NOT NULL,
    new_level INT NOT NULL,
    level_up BOOLEAN DEFAULT FALSE,
    description VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_xp_events_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- Index for efficient queries
SET @has_idx_xp_events_user_id := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'xp_events'
      AND index_name = 'idx_xp_events_user_id'
);
SET @sql := IF(
    @has_idx_xp_events_user_id = 0,
    'CREATE INDEX idx_xp_events_user_id ON xp_events(user_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_xp_events_created_at := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'xp_events'
      AND index_name = 'idx_xp_events_created_at'
);
SET @sql := IF(
    @has_idx_xp_events_created_at = 0,
    'CREATE INDEX idx_xp_events_created_at ON xp_events(created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_xp_events_source_type := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'xp_events'
      AND index_name = 'idx_xp_events_source_type'
);
SET @sql := IF(
    @has_idx_xp_events_source_type = 0,
    'CREATE INDEX idx_xp_events_source_type ON xp_events(source_type)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Index for leaderboard queries (user XP is already in users table)
-- But we might want to query users by XP quickly
SET @has_idx_users_xp_desc := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'idx_users_xp_desc'
);
SET @users_has_xp := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'xp'
);
SET @sql := IF(
    @users_has_xp = 1 AND @has_idx_users_xp_desc = 0,
    'CREATE INDEX idx_users_xp_desc ON users(xp)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Comment explaining the table purpose
-- xp_events tracks every XP transaction for users
-- source_type can be: FORUM_POST, FORUM_COMMENT, COURSE_COMPLETED, QUIZ_PASSED, DAILY_LOGIN, etc.
