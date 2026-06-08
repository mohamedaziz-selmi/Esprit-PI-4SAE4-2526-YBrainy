-- =============================================================
-- YBrainy Microservices — Database Initialization Script
-- DB names match the JDBC URLs in config-server configs.
-- These are also auto-created via createDatabaseIfNotExist=true.
-- =============================================================

CREATE DATABASE IF NOT EXISTS forum_users      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_categories CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_threads    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_posts      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_comments   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_messages   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tables are created automatically by Spring Boot JPA (ddl-auto=update).

-- Seed some categories after category-service starts:
-- USE forum_categories;
-- INSERT IGNORE INTO categories (name, description, icon_url) VALUES
--   ('General', 'General discussion', NULL),
--   ('Tech', 'Technology topics', NULL),
--   ('Science', 'Science and research', NULL),
--   ('Off-Topic', 'Anything goes', NULL);
