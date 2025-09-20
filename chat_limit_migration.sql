-- Monthly Chat Limit Feature Migration
-- This script adds the necessary field and configuration for automatic account disabling based on monthly chat limits

-- Check if auto_disabled_reason column exists, if not add it
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'auto_disabled_reason');

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE sys_user ADD COLUMN auto_disabled_reason VARCHAR(100) NULL COMMENT ''自动禁用原因，用于区分手动禁用和自动禁用的账户''',
    'SELECT ''Column auto_disabled_reason already exists'' AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Insert the max_chat_count system parameter with default value (if not exists)
INSERT INTO sys_params (id, param_code, param_value, param_type, remark, creator, create_date, updater, update_date)
SELECT 
    (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_params), 
    'server.max_chat_count', 
    '1000', 
    0, 
    '每月最大聊天次数限制，超过此数量的非超级管理员账户将被自动禁用', 
    1, 
    NOW(), 
    1, 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_params WHERE param_code = 'server.max_chat_count'
);

-- Verify the migration
SELECT 'Migration completed successfully.' AS status;
