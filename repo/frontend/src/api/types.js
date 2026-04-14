/**
 * Shared type constants and response-mapping utilities
 * aligned with backend DTO contracts.
 */

// --- Enums matching backend domain enums ---

export const RoleType = {
  ATTENDEE: 'ATTENDEE',
  EVENT_STAFF: 'EVENT_STAFF',
  FINANCE_MANAGER: 'FINANCE_MANAGER',
  SYSTEM_ADMIN: 'SYSTEM_ADMIN'
}

export const AccountStatus = {
  ACTIVE: 'ACTIVE',
  LOCKED: 'LOCKED',
  DISABLED: 'DISABLED'
}

export const SessionStatus = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  OPEN_FOR_REGISTRATION: 'OPEN_FOR_REGISTRATION',
  FULL: 'FULL',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
}

export const RegistrationStatus = {
  CONFIRMED: 'CONFIRMED',
  WAITLISTED: 'WAITLISTED',
  PROMOTED: 'PROMOTED',
  CANCELLED: 'CANCELLED',
  EXPIRED: 'EXPIRED'
}

export const CheckInStatus = {
  CHECKED_IN: 'CHECKED_IN',
  DENIED_WINDOW_CLOSED: 'DENIED_WINDOW_CLOSED',
  DENIED_INVALID_PASSCODE: 'DENIED_INVALID_PASSCODE',
  DENIED_DEVICE_CONFLICT: 'DENIED_DEVICE_CONFLICT',
  DENIED_DUPLICATE: 'DENIED_DUPLICATE',
  DENIED_NOT_REGISTERED: 'DENIED_NOT_REGISTERED',
  CONFLICT_MULTI_DEVICE: 'CONFLICT_MULTI_DEVICE'
}

export const NotificationType = {
  REGISTRATION_CONFIRMATION: 'REGISTRATION_CONFIRMATION',
  WAITLIST_PROMOTION: 'WAITLIST_PROMOTION',
  WAITLIST_EXPIRED: 'WAITLIST_EXPIRED',
  CHECKIN_EXCEPTION: 'CHECKIN_EXCEPTION',
  CHECKIN_DEVICE_WARNING: 'CHECKIN_DEVICE_WARNING',
  FINANCE_POSTING_RESULT: 'FINANCE_POSTING_RESULT',
  IMPORT_COMPLETED: 'IMPORT_COMPLETED',
  IMPORT_FAILED: 'IMPORT_FAILED',
  SYSTEM_ALERT: 'SYSTEM_ALERT'
}

export const SendStatus = {
  PENDING: 'PENDING',
  DELIVERED: 'DELIVERED',
  FAILED_RETRYING: 'FAILED_RETRYING',
  PERMANENTLY_FAILED: 'PERMANENTLY_FAILED'
}

export const PostingStatus = {
  DRAFT: 'DRAFT',
  POSTED: 'POSTED',
  REVERSED: 'REVERSED',
  FAILED: 'FAILED'
}

export const AllocationMethod = {
  PROPORTIONAL: 'PROPORTIONAL',
  FIXED: 'FIXED',
  TIERED: 'TIERED'
}

export const RevenueRecognitionMethod = {
  IMMEDIATE: 'IMMEDIATE',
  OVER_SESSION_DATES: 'OVER_SESSION_DATES'
}

export const PeriodStatus = {
  OPEN: 'OPEN',
  CLOSED: 'CLOSED',
  LOCKED: 'LOCKED'
}

export const ImportJobStatus = {
  QUEUED: 'QUEUED',
  RUNNING: 'RUNNING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CIRCUIT_BROKEN: 'CIRCUIT_BROKEN'
}

export const BackupStatus = {
  SCHEDULED: 'SCHEDULED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED'
}

// --- Conflict types matching backend ConflictType enum ---

export const ConflictType = {
  DUPLICATE_REGISTRATION: 'DUPLICATE_REGISTRATION',
  QUOTA_EXCEEDED: 'QUOTA_EXCEEDED',
  WINDOW_CLOSED: 'WINDOW_CLOSED',
  DEVICE_CONFLICT: 'DEVICE_CONFLICT',
  CONCURRENT_DEVICE: 'CONCURRENT_DEVICE',
  DUPLICATE_CHECKIN: 'DUPLICATE_CHECKIN',
  IDEMPOTENCY_DUPLICATE: 'IDEMPOTENCY_DUPLICATE',
  PERIOD_CLOSED: 'PERIOD_CLOSED',
  RULE_VERSION_CONFLICT: 'RULE_VERSION_CONFLICT'
}

// --- Response envelope helpers ---

/**
 * Unwrap the standard ApiResponse envelope, returning data on success
 * or throwing a structured error on failure.
 */
export function unwrapResponse(axiosResponse) {
  const envelope = axiosResponse.data
  if (envelope.success) {
    return envelope.data
  }
  const error = new Error(envelope.message || 'Request failed')
  error.errors = envelope.errors || []
  error.conflictType = envelope.errors?.[0]?.code || null
  throw error
}

/**
 * Extract paged content from a paged ApiResponse.
 */
export function unwrapPagedResponse(axiosResponse) {
  const envelope = axiosResponse.data
  if (envelope.success) {
    return envelope.data // { content, page, size, totalElements, totalPages }
  }
  const error = new Error(envelope.message || 'Request failed')
  error.errors = envelope.errors || []
  throw error
}
