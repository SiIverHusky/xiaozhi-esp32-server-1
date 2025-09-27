-- Premium subscription table for tracking user premium status
CREATE TABLE user_premium_subscription (
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

-- Add premium status fields to sys_user table
ALTER TABLE sys_user 
ADD COLUMN is_premium tinyint(1) DEFAULT 0 COMMENT 'Premium status: 0=regular, 1=premium',
ADD COLUMN premium_expires_at datetime NULL COMMENT 'Premium expiry date, NULL if not premium',
ADD COLUMN premium_last_check datetime NULL COMMENT 'Last premium status check timestamp';

-- Create index for premium status queries
CREATE INDEX idx_premium_status ON sys_user(is_premium, premium_expires_at);

-- Insert sample premium subscription data (optional - for testing)
-- INSERT INTO user_premium_subscription 
-- (user_id, subscription_type, payment_amount, payment_currency, payment_method, payment_transaction_id, 
--  subscription_start_date, subscription_end_date, status, creator) 
-- VALUES 
-- (1, 'MONTHLY', 9.99, 'USD', 'STRIPE', 'pi_test_123456789', 
--  DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY), 'ACTIVE', 1);