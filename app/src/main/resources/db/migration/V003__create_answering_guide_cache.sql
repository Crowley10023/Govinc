-- Create answering guide cache table for storing AI-generated questions
CREATE TABLE IF NOT EXISTS answering_guide_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    control_id BIGINT NOT NULL UNIQUE,
    control_name VARCHAR(255) NOT NULL,
    control_detail LONGTEXT,
    questions LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    usage_count INT NOT NULL DEFAULT 0,
    INDEX idx_control_id (control_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
