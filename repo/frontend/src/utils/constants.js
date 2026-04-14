/**
 * Application-wide constants.
 * Values are traceable to prompt requirements.
 */

export const ROLES = {
  ATTENDEE: 'ATTENDEE',
  EVENT_STAFF: 'EVENT_STAFF',
  FINANCE_MANAGER: 'FINANCE_MANAGER',
  SYSTEM_ADMIN: 'SYSTEM_ADMIN'
}

export const CACHE_TTL = {
  ROSTER: 5 * 60 * 1000,
  SESSION_LIST: 2 * 60 * 1000,
  NOTIFICATIONS: 1 * 60 * 1000
}

export const CHECKIN = {
  PASSCODE_INTERVAL_MS: 60000,
  PASSCODE_DIGITS: 6,
  WINDOW_BEFORE_MINUTES: 30,
  WINDOW_AFTER_MINUTES: 15
}

export const DND_DEFAULT = {
  start: '21:00',
  end: '07:00'
}

export const NOTIFICATION_RETRY = {
  MAX_ATTEMPTS: 5,
  BACKOFF_MAX_MINUTES: 10
}

export const IMPORT = {
  CONCURRENCY_CAP: 3,
  TIMEOUT_SECONDS: 30,
  CIRCUIT_BREAKER_THRESHOLD: 10
}

export const RATE_LIMIT = {
  REQUESTS_PER_MINUTE: 60
}

export const BACKUP = {
  RETENTION_DAYS: 30
}
