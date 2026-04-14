-- ============================================================================
-- V001: Initial schema for EventOps platform
-- Covers: users, events, registrations, check-in, notifications,
--         imports, finance, audit, backup
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. USERS & SECURITY
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    username        VARCHAR(100)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(200)    NOT NULL,
    contact_info    VARBINARY(512)  NULL,          -- AES-256 encrypted
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    role_type       VARCHAR(30)     NOT NULL DEFAULT 'ATTENDEE',
    failed_login_attempts INT       NOT NULL DEFAULT 0,
    lockout_until   TIMESTAMP       NULL,
    last_login_at   TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_username UNIQUE (username),
    INDEX idx_users_role (role_type),
    INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 2. EVENT SESSIONS
-- ---------------------------------------------------------------------------

CREATE TABLE event_sessions (
    id                              VARCHAR(36)     NOT NULL PRIMARY KEY,
    title                           VARCHAR(300)    NOT NULL,
    description                     TEXT            NULL,
    location                        VARCHAR(100)    NOT NULL,
    start_time                      DATETIME        NOT NULL,
    end_time                        DATETIME        NOT NULL,
    max_capacity                    INT             NOT NULL,
    current_registrations           INT             NOT NULL DEFAULT 0,
    status                          VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    checkin_window_before_minutes   INT             NOT NULL DEFAULT 30,
    checkin_window_after_minutes    INT             NOT NULL DEFAULT 15,
    device_binding_required         BOOLEAN         NOT NULL DEFAULT FALSE,
    waitlist_promotion_cutoff       DATETIME        NULL,
    created_by                      VARCHAR(36)     NULL,
    created_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_sessions_status (status),
    INDEX idx_sessions_start (start_time),
    CONSTRAINT fk_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. REGISTRATIONS & WAITLISTS
-- ---------------------------------------------------------------------------

CREATE TABLE registrations (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_id             VARCHAR(36)     NOT NULL,
    session_id          VARCHAR(36)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'CONFIRMED',
    waitlist_position   INT             NULL,
    promoted_at         TIMESTAMP       NULL,
    cancelled_at        TIMESTAMP       NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Critical invariant: one registration per attendee per session
    CONSTRAINT uk_registration_user_session UNIQUE (user_id, session_id),
    INDEX idx_reg_session (session_id),
    INDEX idx_reg_status (status),
    INDEX idx_reg_waitlist (session_id, status, waitlist_position),
    CONSTRAINT fk_reg_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reg_session FOREIGN KEY (session_id) REFERENCES event_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. CHECK-IN
-- ---------------------------------------------------------------------------

CREATE TABLE passcode_states (
    session_id          VARCHAR(36)     NOT NULL PRIMARY KEY,
    current_passcode    VARCHAR(6)      NOT NULL,
    generated_at        TIMESTAMP       NOT NULL,
    expires_at          TIMESTAMP       NOT NULL,

    CONSTRAINT fk_passcode_session FOREIGN KEY (session_id) REFERENCES event_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE checkin_records (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    session_id          VARCHAR(36)     NOT NULL,
    user_id             VARCHAR(36)     NOT NULL,
    staff_id            VARCHAR(36)     NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    passcode_used       VARCHAR(6)      NULL,
    device_token_hash   VARCHAR(128)    NULL,
    device_fingerprint  VARBINARY(512)  NULL,      -- AES-256 encrypted
    request_source      VARCHAR(50)     NULL,
    checked_in_at       TIMESTAMP       NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_checkin_session (session_id),
    INDEX idx_checkin_user_session (user_id, session_id),
    INDEX idx_checkin_status (status),
    CONSTRAINT fk_checkin_session FOREIGN KEY (session_id) REFERENCES event_sessions(id),
    CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_checkin_staff FOREIGN KEY (staff_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE device_bindings (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_id             VARCHAR(36)     NOT NULL,
    device_token_hash   VARCHAR(128)    NOT NULL,
    device_fingerprint  VARBINARY(512)  NULL,      -- AES-256 encrypted
    binding_date        DATE            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One device binding per user per day
    CONSTRAINT uk_device_binding_user_date UNIQUE (user_id, binding_date),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. NOTIFICATIONS
-- ---------------------------------------------------------------------------

CREATE TABLE notification_templates (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    notification_type   VARCHAR(40)     NOT NULL,
    subject_template    VARCHAR(500)    NOT NULL,
    body_template       TEXT            NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_template_type UNIQUE (notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_subscriptions (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_id             VARCHAR(36)     NOT NULL,
    notification_type   VARCHAR(40)     NOT NULL,
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_subscription_user_type UNIQUE (user_id, notification_type),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dnd_rules (
    user_id             VARCHAR(36)     NOT NULL PRIMARY KEY,
    start_time          TIME            NOT NULL DEFAULT '21:00:00',
    end_time            TIME            NOT NULL DEFAULT '07:00:00',
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_dnd_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_send_logs (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_id             VARCHAR(36)     NOT NULL,
    notification_type   VARCHAR(40)     NOT NULL,
    subject             VARCHAR(500)    NOT NULL,
    body                TEXT            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempt_count       INT             NOT NULL DEFAULT 0,
    max_attempts        INT             NOT NULL DEFAULT 5,
    next_attempt_at     TIMESTAMP       NULL,
    last_attempt_at     TIMESTAMP       NULL,
    last_error          VARCHAR(1000)   NULL,
    idempotency_key     VARCHAR(100)    NOT NULL,
    reference_id        VARCHAR(36)     NULL,
    delivered_at        TIMESTAMP       NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_send_log_idempotency UNIQUE (idempotency_key),
    INDEX idx_send_log_status (status),
    INDEX idx_send_log_next_attempt (next_attempt_at),
    INDEX idx_send_log_user (user_id),
    CONSTRAINT fk_send_log_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. IMPORTS
-- ---------------------------------------------------------------------------

CREATE TABLE import_sources (
    id                          VARCHAR(36)     NOT NULL PRIMARY KEY,
    name                        VARCHAR(200)    NOT NULL,
    folder_path                 VARCHAR(500)    NOT NULL,
    file_pattern                VARCHAR(200)    NOT NULL,
    import_mode                 VARCHAR(20)     NOT NULL DEFAULT 'INCREMENTAL',
    column_mappings             TEXT            NULL,
    concurrency_cap             INT             NOT NULL DEFAULT 3,
    timeout_seconds             INT             NOT NULL DEFAULT 30,
    circuit_breaker_threshold   INT             NOT NULL DEFAULT 10,
    active                      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crawl_jobs (
    id                      VARCHAR(36)     NOT NULL PRIMARY KEY,
    source_id               VARCHAR(36)     NOT NULL,
    import_mode             VARCHAR(20)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'QUEUED',
    trace_id                VARCHAR(36)     NOT NULL,
    files_processed         INT             NOT NULL DEFAULT 0,
    records_imported        INT             NOT NULL DEFAULT 0,
    records_failed          INT             NOT NULL DEFAULT 0,
    consecutive_failures    INT             NOT NULL DEFAULT 0,
    checkpoint_marker       VARCHAR(500)    NULL,
    error_message           VARCHAR(2000)   NULL,
    started_at              TIMESTAMP       NULL,
    completed_at            TIMESTAMP       NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_crawl_job_source (source_id),
    INDEX idx_crawl_job_status (status),
    INDEX idx_crawl_job_trace (trace_id),
    CONSTRAINT fk_crawl_source FOREIGN KEY (source_id) REFERENCES import_sources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. FINANCE & ACCOUNTING
-- ---------------------------------------------------------------------------

CREATE TABLE accounting_periods (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_period_dates (start_date, end_date),
    INDEX idx_period_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chart_of_accounts (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    account_code    VARCHAR(20)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(500)    NULL,
    parent_id       VARCHAR(36)     NULL,
    account_type    VARCHAR(30)     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_account_code UNIQUE (account_code),
    INDEX idx_account_type (account_type),
    CONSTRAINT fk_account_parent FOREIGN KEY (parent_id) REFERENCES chart_of_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cost_centers (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    code            VARCHAR(20)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(500)    NULL,
    center_type     VARCHAR(50)     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_cost_center_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE allocation_rules (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    name                VARCHAR(200)    NOT NULL,
    allocation_method   VARCHAR(20)     NOT NULL,
    recognition_method  VARCHAR(30)     NOT NULL,
    account_id          VARCHAR(36)     NULL,
    cost_center_id      VARCHAR(36)     NULL,
    rule_config         TEXT            NULL,
    version             INT             NOT NULL DEFAULT 1,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(36)     NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_rule_method (allocation_method),
    INDEX idx_rule_version (id, version),
    CONSTRAINT fk_rule_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id),
    CONSTRAINT fk_rule_cost_center FOREIGN KEY (cost_center_id) REFERENCES cost_centers(id),
    CONSTRAINT fk_rule_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE posting_journals (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    period_id       VARCHAR(36)     NOT NULL,
    session_id      VARCHAR(36)     NULL,
    rule_id         VARCHAR(36)     NOT NULL,
    rule_version    INT             NOT NULL,
    total_amount    DECIMAL(15,2)   NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'DRAFT',
    description     VARCHAR(500)    NULL,
    posted_by       VARCHAR(36)     NOT NULL,
    posted_at       TIMESTAMP       NULL,
    reversed_at     TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_posting_period (period_id),
    INDEX idx_posting_status (status),
    CONSTRAINT fk_posting_period FOREIGN KEY (period_id) REFERENCES accounting_periods(id),
    CONSTRAINT fk_posting_session FOREIGN KEY (session_id) REFERENCES event_sessions(id),
    CONSTRAINT fk_posting_rule FOREIGN KEY (rule_id) REFERENCES allocation_rules(id),
    CONSTRAINT fk_posting_by FOREIGN KEY (posted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE allocation_line_items (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    posting_id          VARCHAR(36)     NOT NULL,
    account_id          VARCHAR(36)     NOT NULL,
    cost_center_id      VARCHAR(36)     NULL,
    amount              DECIMAL(15,2)   NOT NULL,
    description         VARCHAR(500)    NULL,
    recognition_start   DATE            NULL,
    recognition_end     DATE            NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_line_posting (posting_id),
    CONSTRAINT fk_line_posting FOREIGN KEY (posting_id) REFERENCES posting_journals(id),
    CONSTRAINT fk_line_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts(id),
    CONSTRAINT fk_line_cost_center FOREIGN KEY (cost_center_id) REFERENCES cost_centers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. AUDIT
-- ---------------------------------------------------------------------------

CREATE TABLE audit_events (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    action_type     VARCHAR(40)     NOT NULL,
    operator_id     VARCHAR(36)     NOT NULL,
    operator_name   VARCHAR(200)    NOT NULL,
    request_source  VARCHAR(50)     NULL,
    entity_type     VARCHAR(50)     NULL,
    entity_id       VARCHAR(36)     NULL,
    description     TEXT            NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_timestamp (created_at),
    INDEX idx_audit_action (action_type),
    INDEX idx_audit_operator (operator_id),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE field_diffs (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    audit_event_id  VARCHAR(36)     NOT NULL,
    field_name      VARCHAR(100)    NOT NULL,
    old_value       TEXT            NULL,
    new_value       TEXT            NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_diff_event (audit_event_id),
    CONSTRAINT fk_diff_audit FOREIGN KEY (audit_event_id) REFERENCES audit_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE export_jobs (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    export_type     VARCHAR(50)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(36)     NOT NULL,
    file_path       VARCHAR(500)    NULL,
    file_size_bytes BIGINT          NULL,
    watermark_text  VARCHAR(500)    NULL,
    filter_criteria TEXT            NULL,
    record_count    INT             NULL,
    error_message   VARCHAR(1000)   NULL,
    completed_at    TIMESTAMP       NULL,
    expires_at      TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_export_status (status),
    CONSTRAINT fk_export_by FOREIGN KEY (requested_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE watermark_policies (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    report_type         VARCHAR(50)     NOT NULL,
    role_type           VARCHAR(30)     NOT NULL,
    download_allowed    BOOLEAN         NOT NULL DEFAULT TRUE,
    watermark_template  VARCHAR(500)    NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_wm_policy_type_role (report_type, role_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 9. BACKUPS
-- ---------------------------------------------------------------------------

CREATE TABLE backup_jobs (
    id                      VARCHAR(36)     NOT NULL PRIMARY KEY,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
    file_path               VARCHAR(500)    NULL,
    file_size_bytes         BIGINT          NULL,
    triggered_by            VARCHAR(50)     NOT NULL,
    error_message           VARCHAR(1000)   NULL,
    retention_expires_at    TIMESTAMP       NULL,
    started_at              TIMESTAMP       NULL,
    completed_at            TIMESTAMP       NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_backup_status (status),
    INDEX idx_backup_retention (retention_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
