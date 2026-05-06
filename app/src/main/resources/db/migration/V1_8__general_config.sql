-- General application configuration table (session timeout, etc.)
CREATE TABLE IF NOT EXISTS general_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    session_timeout_minutes INT NOT NULL DEFAULT 30
);

-- Insert the default configuration row
INSERT INTO general_config (config_key, session_timeout_minutes)
VALUES ('default', 30)
ON DUPLICATE KEY UPDATE config_key = config_key;
