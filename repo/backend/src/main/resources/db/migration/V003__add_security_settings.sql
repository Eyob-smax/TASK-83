CREATE TABLE security_settings (
    id                          VARCHAR(36)     NOT NULL PRIMARY KEY,
    rate_limit_per_minute       INT             NOT NULL,
    login_max_attempts          INT             NOT NULL,
    login_lockout_minutes       INT             NOT NULL,
    signature_enabled           BOOLEAN         NOT NULL,
    signature_algorithm         VARCHAR(50)     NOT NULL,
    signature_max_age_seconds   INT             NOT NULL,
    pii_display_mode            VARCHAR(30)     NOT NULL,
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
