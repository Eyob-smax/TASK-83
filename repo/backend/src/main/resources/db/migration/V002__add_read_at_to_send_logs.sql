-- V002: Add read_at column to notification_send_logs for read tracking
ALTER TABLE notification_send_logs
    ADD COLUMN read_at TIMESTAMP NULL AFTER delivered_at;

CREATE INDEX idx_send_log_read ON notification_send_logs (read_at);
