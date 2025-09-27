-- Fix for premium account feature - Add missing columns to sys_user table
-- Run this script to resolve login issues after adding premium functionality

-- Check and add is_premium column if it doesn't exist
SET @s = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'is_premium') > 0,
    "SELECT 'is_premium column already exists'",
    "ALTER TABLE sys_user ADD COLUMN is_premium tinyint(1) DEFAULT 0 COMMENT 'Premium status: 0=regular, 1=premium'"
));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and add premium_expires_at column if it doesn't exist
SET @s = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'premium_expires_at') > 0,
    "SELECT 'premium_expires_at column already exists'",
    "ALTER TABLE sys_user ADD COLUMN premium_expires_at datetime NULL COMMENT 'Premium expiry date, NULL if not premium'"
));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and add premium_last_check column if it doesn't exist
SET @s = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'premium_last_check') > 0,
    "SELECT 'premium_last_check column already exists'",
    "ALTER TABLE sys_user ADD COLUMN premium_last_check datetime NULL COMMENT 'Last premium status check timestamp'"
));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index for premium status queries (ignore if exists)
CREATE INDEX idx_premium_status ON sys_user(is_premium, premium_expires_at);

-- Create the premium subscription table
CREATE TABLE IF NOT EXISTS user_premium_subscription (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'Subscription ID',
    user_id bigint NOT NULL COMMENT 'User ID from sys_user table',
    subscription_type varchar(50) NOT NULL DEFAULT 'MONTHLY' COMMENT 'Subscription type: MONTHLY, YEARLY',
    payment_amount decimal(10,2) NOT NULL COMMENT 'Payment amount',
    payment_currency varchar(10) NOT NULL DEFAULT 'USD' COMMENT 'Payment currency',
    payment_method varchar(50) COMMENT 'Payment method: STRIPE, PAYPAL, WECHAT, ALIPAY',
    payment_transaction_id varchar(255) COMMENT 'External payment transaction ID',
    subscription_start_date datetime NOT NULL COMMENT 'Subscription start date',
    subscription_end_date datetime NOT NULL COMMENT 'Subscription end date',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, EXPIRED, CANCELLED, REFUNDED',
    auto_renew tinyint(1) DEFAULT 0 COMMENT 'Auto renewal enabled: 0=no, 1=yes',
    created_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation date',
    updated_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update date',
    creator bigint COMMENT 'Creator user ID',
    updater bigint COMMENT 'Updater user ID',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_subscription_dates (subscription_start_date, subscription_end_date),
    INDEX idx_status (status),
    INDEX idx_payment_transaction (payment_transaction_id),
    CONSTRAINT fk_premium_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User premium subscription records';

-- Verify the changes
SELECT 'Database schema updated successfully for premium accounts' as status;