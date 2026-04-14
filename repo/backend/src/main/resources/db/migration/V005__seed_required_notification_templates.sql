INSERT INTO notification_templates (
    id,
    notification_type,
    subject_template,
    body_template,
    active
) VALUES
(
    UUID(),
    'REGISTRATION_CONFIRMATION',
    'Registration confirmed for {{sessionTitle}}',
    'Your registration for {{sessionTitle}} is confirmed. Current status: {{status}}.',
    TRUE
),
(
    UUID(),
    'WAITLIST_PROMOTION',
    'Seat available for {{sessionTitle}}',
    'You have been promoted from the waitlist for {{sessionTitle}}. Current status: {{status}}.',
    TRUE
),
(
    UUID(),
    'WAITLIST_EXPIRED',
    'Waitlist closed for {{sessionTitle}}',
    'Your waitlist position for {{sessionTitle}} expired because the promotion cutoff passed.',
    TRUE
),
(
    UUID(),
    'CHECKIN_EXCEPTION',
    'Check-in exception for {{sessionTitle}}',
    'A check-in exception occurred for {{sessionTitle}}. Reason: {{reason}}. Status: {{status}}.',
    TRUE
),
(
    UUID(),
    'CHECKIN_DEVICE_WARNING',
    'Device warning for {{sessionTitle}}',
    'A device-related check-in warning was detected for {{sessionTitle}}. Reason: {{reason}}. Status: {{status}}.',
    TRUE
),
(
    UUID(),
    'FINANCE_POSTING_RESULT',
    'Finance posting result for {{sessionTitle}}',
    'Finance posting completed for {{sessionTitle}} with status {{status}}.',
    TRUE
)
ON DUPLICATE KEY UPDATE
    subject_template = VALUES(subject_template),
    body_template = VALUES(body_template),
    active = VALUES(active);
