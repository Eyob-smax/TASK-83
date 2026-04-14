# System Design Document

## 1. Overview

The **Compliance-Grade Event Operations and Accounting Platform** is a full-stack offline facility-network application designed to manage internal trainings, certification sessions, and community events. It enforces compliance-grade accounting, audit trails, and security controls entirely within a local network — with zero dependency on external SaaS providers, identity services, email, SMS, maps, or third-party APIs.

### 1.1 Deployment Model

- All services run on a local/offline facility network
- HTTPS with request-signature verification secures all API communication
- TLS certificates are provisioned by facility administrators via internal CA or pre-generated bundles (see `questions.md` #1)
- No internet reachability is required or assumed at any point

### 1.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | Vue.js (Composition API) | 3.5.x |
| Frontend State | Pinia | 3.0.x |
| Frontend Routing | Vue Router | 4.5.x |
| Frontend Build | Vite | 6.3.x |
| Frontend Tests | Vitest | 4.1.x |
| HTTP Client | axios | 1.15.x |
| Offline Storage | localforage (IndexedDB) | 1.10.x |
| Backend | Spring Boot | 3.2.x |
| Backend Language | Java | 17 LTS |
| Security | Spring Security | 6.x |
| ORM | Spring Data JPA / Hibernate | 6.x |
| Migrations | Flyway | 10.x |
| Database | MySQL | 8.x |
| Containerization | Docker Compose | latest |

### 1.3 User Roles

| Role | Responsibilities |
|------|-----------------|
| Attendee | Browse events, register, check-in, manage notifications |
| Event Staff | Manage rosters, run check-in screens, import badge data |
| Finance Manager | Configure accounting periods, allocations, revenue recognition, review postings |
| System Administrator | Security controls, audit logs, backups, user management, export restrictions |

---

## 2. Architecture Overview

### 2.1 High-Level Component Diagram

```
+-----------------------+        HTTPS + Signatures        +------------------------+
|   Vue.js Frontend     | -------------------------------->|   Spring Boot Backend  |
|                       |                                   |                        |
|  - App Shell/Router   |        REST API (JSON)           |  - Controllers         |
|  - Pinia Stores       | <--------------------------------|  - Services            |
|  - API Service Layer  |                                   |  - Domain/Repositories |
|  - Offline Cache      |                                   |  - Schedulers          |
|  - Components/Views   |                                   |  - Security Filters    |
+-----------------------+                                   +------------------------+
                                                                      |
                                                                      v
                                                            +------------------+
                                                            |   MySQL 8.x      |
                                                            |                  |
                                                            | - Event data     |
                                                            | - Registrations  |
                                                            | - Check-ins      |
                                                            | - Notifications  |
                                                            | - Finance/Audit  |
                                                            | - Security/RBAC  |
                                                            | - AES-256 fields |
                                                            +------------------+
```

### 2.2 Vue Client Composition and Local Caching

**App Shell**: Single-page application with `vue-router` for client-side navigation. `MainLayout` (authenticated pages with sidebar/header) and `AuthLayout` (login pages) provide structural separation.

**State Management**: Pinia stores organized by domain — auth, events, registration, checkin, notifications, finance, admin, offline. Each store manages reactive UI state and delegates HTTP operations to the API service layer.

**API Service Layer**: Centralized `axios` instance (`src/api/client.js`) with interceptors for:
- Authorization header injection
- Request signature computation (HMAC-SHA256)
- Error normalization into structured envelopes
- Offline detection and pending-action queueing

**Offline Cache (localforage)**:
- `offline_queue`: Pending actions awaiting reconnection (check-ins, registrations)
- `roster_cache`: Active attendee roster slices with TTL-based expiration
- `data_cache`: Session metadata, event listings with version markers
- All cached entries carry timestamps and explicit expiration for reconciliation

**Weak-Connectivity Behavior**:
- Detect online/offline via `navigator.onLine` + event listeners
- Queue failed submissions to IndexedDB via localforage
- Replay pending actions on reconnection with server-side conflict resolution
- Resumable uploads track chunk state in local storage
- Duplicate check-in from multiple devices triggers conflict prompts

### 2.3 Spring Boot Service Layering

```
Controllers (thin REST handlers)
    |
    v
Services (business logic, transactions, orchestration)
    |
    v
Domain / Model (entities, enums, value objects, invariants)
    |
    v
Repositories (Spring Data JPA interfaces)
    |
    v
MySQL (persistence, constraints, indexes)
```

**Additional cross-cutting layers**:
- **Security**: Authentication, RBAC, rate limiting, signature verification, encryption
- **Schedulers**: Passcode rotation, notification retry, waitlist promotion, import orchestration, nightly backup
- **Integration Adapters**: Shared-folder file access, notification dispatch, CSV export generation
- **Finance Engine**: Allocation strategies, revenue recognition, rule versioning, posting journals
- **Audit Infrastructure**: Immutable log writing, field-level diff computation, CSV export with watermarks

### 2.4 MySQL Persistence and Encryption Strategy

**Schema Design** (to be defined in Prompt 2):
- Flyway-managed versioned migrations under `repo/backend/src/main/resources/db/migration/`
- Foreign keys, unique constraints, and indexes enforcing business invariants
- Sensitive fields (attendee contacts, device identifiers) encrypted at rest using AES-256 via JPA attribute converters
- Encrypted fields stored as `VARBINARY` or `BLOB` columns
- Decryption happens at the JPA layer; plaintext never persisted

**Encryption Infrastructure**:
- AES-256 key managed via environment variable / secure config
- JPA `@Convert` attribute converter for transparent encrypt/decrypt
- Masked-by-default display helpers — sensitive fields show `****` unless explicitly revealed
- Log sanitization prevents encrypted or plaintext sensitive data from appearing in logs

### 2.5 Scheduler, Queue, Backup, and Checkpoint/Resume Architecture

**Scheduled Tasks** (Spring `@Scheduled`):

| Scheduler | Cadence | Purpose |
|-----------|---------|---------|
| PasscodeRotationScheduler | Every 60 seconds | Rotate 6-digit check-in passcodes per active session |
| NotificationRetryScheduler | Every 30 seconds | Poll retry queue, apply exponential backoff (5 attempts / 10 min) |
| WaitlistPromotionScheduler | Every 5 minutes | Reconcile waitlist positions against capacity changes |
| ImportOrchestrationScheduler | Configurable | Trigger incremental/full crawls of shared-folder sources |
| BackupScheduler | Nightly (2:00 AM) | Execute database backup, enforce 30-day retention |

**Notification Retry Queue**:
- Database-backed queue with `next_attempt_at`, `attempt_count`, `idempotency_key`
- Exponential backoff: attempts at ~0, 1, 2, 4, 8 minutes (configurable)
- Max 5 attempts; failed notifications marked as permanently failed
- Idempotency keys prevent duplicate sends

**Import Orchestration**:
- Per-source concurrency cap: 3 workers (configurable)
- Per-task timeout: 30 seconds
- Circuit breaker: trips after 10 consecutive failures, resets on manual intervention or cooldown
- Checkpoint resume: last-processed file/row persisted for incremental restart
- End-to-end trace IDs for cross-cutting observability

**Backup Infrastructure**:
- Nightly MySQL dump to configurable local path
- Encrypted backup artifacts
- 30-day retention with automated cleanup
- Execution history and status persisted in MySQL
- Admin-visible backup status and history screens

### 2.6 Finance and Audit Architecture

**Finance Engine**:
- Configurable accounting periods (monthly, quarterly, custom)
- Chart of accounts with hierarchical structure
- Cost centers (e.g., vehicle type, instructor team)
- Revenue recognition: immediate vs. spread over session dates
- Allocation methods: proportional, fixed, tiered (strategy pattern)
- Rule versioning: every rule change creates a new version; postings reference the exact rule version
- Posting journals with auditable allocation line items
- Posting results feed into the notification center

**Audit Infrastructure**:
- Immutable audit events (INSERT-only, no UPDATE/DELETE on audit tables)
- Each event captures: operator ID, timestamp, request source IP, action type
- Field-level diffs for key entity changes (before/after snapshots)
- Searchable by date range, action type, operator, entity
- Exportable as locally generated CSV
- Export includes watermark (operator identity, timestamp, report context)

### 2.7 Security Boundaries and Export Restriction Strategy

**Authentication**: Local username/password with BCrypt hashing. Anti-bot login throttling (5 failed attempts triggers 15-minute lockout).

**Authorization (RBAC)**:
- Four roles: ATTENDEE, EVENT_STAFF, FINANCE_MANAGER, SYSTEM_ADMIN
- Menu/view visibility controlled by role in Vue router guards
- API endpoint authorization via Spring Security method-level annotations
- Data-scope restrictions: staff see their event rosters, finance managers see their cost centers

**Request Security**:
- HTTPS on all API communication
- HMAC-SHA256 request signatures in custom headers
- Signature verification filter in Spring Security chain
- Rate limiting: 60 requests/minute per user (configurable)

**Export Controls**:
- Export policy per report type and role
- Watermark templates: operator name, timestamp, request source, report label
- Download restriction checks at service layer
- All export attempts (allowed/denied) logged to audit

---

## 3. Requirement-to-Module Traceability

| Requirement Domain | Backend Module(s) | Frontend Module(s) | Prompt |
|--------------------|-------------------|--------------------|----|
| Attendee accounts & authentication | `security.auth`, `domain.user`, `controller.auth` | `stores/auth`, `api/auth`, `views/auth/` | P2-P3 |
| RBAC (menu/view/API/action) | `security.rbac`, `security.config` | `router/` guards, `stores/auth.hasRole` | P3 |
| Event session browsing & seat quotas | `service.event`, `controller.event`, `domain.event` | `stores/events`, `api/events`, `views/events/` | P2,P4,P6 |
| Registration & duplicate blocking | `service.registration`, `controller.registration`, `domain.registration` | `stores/registration`, `api/registration`, `views/registration/` | P2,P4,P6 |
| Waitlist & promotion cutoff | `service.registration`, `scheduler.WaitlistPromotion`, `domain.registration` | `views/registration/WaitlistView` | P4,P6 |
| Check-in windows & rotating passcode | `service.checkin`, `scheduler.PasscodeRotation`, `controller.checkin`, `domain.checkin` | `stores/checkin`, `views/checkin/` | P4,P6 |
| Device binding & multi-device conflict | `service.checkin`, `domain.checkin` | `views/checkin/DeviceBindingView` | P4,P6 |
| Notification center & subscriptions | `service.notification`, `controller.notification`, `domain.notification` | `stores/notifications`, `views/notifications/` | P4,P6 |
| Notification retry queue & idempotency | `scheduler.NotificationRetry`, `service.notification` | — | P4 |
| DND hours (default 9PM-7AM) | `service.notification`, `domain.notification` | `views/notifications/SubscriptionSettings` | P4,P6 |
| Weak-connectivity / offline cache | — | `stores/offline`, `utils/offline`, `utils/cache`, `components/offline/` | P6 |
| Resumable uploads | `controller.export` (upload endpoint) | `utils/offline` (chunk tracking) | P4,P6 |
| Request signature verification | `security.signature` | `utils/signature`, `api/client` interceptor | P3 |
| AES-256 encryption at rest | `security.encryption` | `components/common/MaskedField` | P3 |
| Masked-by-default display | `security.encryption` | `components/common/MaskedField` | P3,P6 |
| Rate limiting (60 req/min) | `security.ratelimit` | — (error handling in interceptor) | P3 |
| Anti-bot login throttling | `security.auth` | `views/auth/LoginView` (lockout display) | P3 |
| Shared-folder import orchestration | `scheduler.ImportOrchestration`, `integration.sharedfolder`, `service.importing` | `views/imports/ImportDashboard` | P4,P7 |
| Concurrency caps / timeouts / circuit breaker | `service.importing`, `domain.importing` | `views/imports/ImportDashboard` (status display) | P4,P7 |
| Checkpoint resume & trace IDs | `service.importing`, `domain.importing` | `views/imports/ImportDashboard` | P4,P7 |
| Accounting periods & chart of accounts | `service.finance`, `finance.rules`, `domain.finance` | `views/finance/AccountingPeriods` | P5,P7 |
| Cost centers | `service.finance`, `domain.finance` | `views/finance/FinanceDashboard` | P5,P7 |
| Revenue recognition rules | `finance.recognition`, `finance.rules` | `views/finance/AllocationView` | P5,P7 |
| Allocation methods (proportional/fixed/tiered) | `finance.allocation`, `finance.rules` | `views/finance/AllocationView` | P5,P7 |
| Rule versioning & auditable line items | `finance.rules`, `finance.posting` | `views/finance/AllocationView` | P5,P7 |
| Audit logs with field-level diffs | `audit.logging`, `audit.diff`, `service.audit`, `controller.audit` | `views/admin/AuditLog` | P3,P5,P7 |
| Audit search & CSV export | `audit.export`, `controller.audit` | `views/admin/AuditLog` | P5,P7 |
| Export watermarking | `integration.export`, `service.audit` | `views/exports/ExportDashboard` | P5,P7 |
| Download restrictions | `integration.export` | `views/exports/ExportDashboard` | P5,P7 |
| Nightly backups & 30-day retention | `scheduler.BackupScheduler`, `backup.execution`, `backup.retention` | `views/admin/BackupStatus` | P5,P7 |
| Immutable operation logs | `audit.logging` | `views/admin/AuditLog` | P3 |
| Least-privilege roles | `security.rbac` | Router guards, conditional UI | P3 |
| Structured error responses | `common.exception`, `common.dto` | Error handling in stores/interceptor | P3 |
| Log sanitization | `audit.logging`, logging config | — | P3 |

---

## 4. Workflow Flow Descriptions (Added Prompt 2)

### 4.1 Registration Flow

```
Attendee → POST /api/registrations { sessionId }
  → Controller validates input (sessionId exists, not blank)
  → Service checks: user already registered for this session?
    → YES: return 409 DUPLICATE_REGISTRATION
    → NO: check session.currentRegistrations < session.maxCapacity
      → HAS CAPACITY: create Registration(CONFIRMED), increment currentRegistrations, return 201
      → FULL: create Registration(WAITLISTED) with next position, return 201 with waitlist info
  → Emit REGISTRATION_CONFIRMATION or WAITLIST notification
  → Write audit event: REGISTRATION_CREATED
```

### 4.2 Waitlist Promotion Flow

```
Trigger: Registration cancelled OR WaitlistPromotionScheduler runs
  → Service finds session with freed capacity
  → Query waitlisted registrations ORDER BY waitlistPosition ASC
  → For each eligible entry:
    → Check: current time < session.waitlistPromotionCutoff?
      → YES: promote → set status=PROMOTED, promotedAt=now, increment currentRegistrations
      → NO: expire → set status=EXPIRED
    → Emit WAITLIST_PROMOTION or WAITLIST_EXPIRED notification
    → Write audit event: WAITLIST_PROMOTED or WAITLIST_EXPIRED
```

### 4.3 Check-In Flow

```
Staff → POST /api/checkin/sessions/{sessionId} { userId, passcode, deviceToken }
  → Controller validates: 6-digit passcode pattern, userId not blank
  → Service checks check-in window:
    → session.startTime - checkinWindowBeforeMinutes <= now <= session.startTime + checkinWindowAfterMinutes
    → OUTSIDE: return 422 WINDOW_CLOSED, record DENIED_WINDOW_CLOSED
  → Validate passcode against PasscodeState.currentPasscode
    → INVALID: return 422, record DENIED_INVALID_PASSCODE
  → Check: user already successfully checked in for this session?
    → YES: return 409 DUPLICATE_CHECKIN
  → If session.deviceBindingRequired:
    → Lookup DeviceBinding for (userId, today)
      → EXISTS with different token hash: return 409 DEVICE_CONFLICT
      → NOT EXISTS: create DeviceBinding record
    → Check concurrent device: any other active session for same user with different device?
      → YES: return 409 CONCURRENT_DEVICE (warning)
  → Create CheckInRecord(CHECKED_IN)
  → Write audit event: CHECKIN_SUCCESS
```

### 4.4 Notification Retry Dispatch Flow

```
NotificationRetryScheduler runs every 30 seconds:
  → Query SendLog WHERE status=PENDING OR (status=FAILED_RETRYING AND nextAttemptAt <= now)
  → For each entry:
    → Check idempotency: already delivered? Skip.
    → Check DND: is user in do-not-disturb window? Defer nextAttemptAt.
    → Attempt delivery (in-app notification)
      → SUCCESS: set status=DELIVERED, deliveredAt=now
      → FAILURE:
        → attemptCount < maxAttempts: set status=FAILED_RETRYING, compute next backoff
          → Backoff: initialBackoff * 2^(attemptCount-1), capped at backoffMax
        → attemptCount >= maxAttempts: set status=PERMANENTLY_FAILED
    → Write audit event for delivery outcome
```

### 4.5 Shared-Folder Import Flow

```
ImportOrchestrationScheduler triggers crawl for active ImportSource:
  → Create CrawlJob(QUEUED) with new traceId
  → Check source.concurrencyCap: running jobs < cap?
    → NO: remain QUEUED, skip
    → YES: set status=RUNNING, startedAt=now
  → Scan source.folderPath for files matching source.filePattern
  → For each file (from checkpoint if incremental):
    → Parse according to columnMappings
    → Import records (within timeoutSeconds)
      → SUCCESS: increment recordsImported, update checkpointMarker
      → FAILURE: increment recordsFailed, consecutiveFailures++
        → consecutiveFailures >= circuitBreakerThreshold:
          → Trip circuit breaker: set status=CIRCUIT_BROKEN
          → Write audit event: CIRCUIT_BREAKER_TRIPPED
          → Stop processing
    → On timeout: record timeout error, continue to next file
  → On completion: set status=COMPLETED/FAILED, completedAt=now
  → Write audit event: IMPORT_COMPLETED or IMPORT_FAILED
```

### 4.6 Finance Posting Flow

```
Finance Manager → POST /api/finance/postings { periodId, ruleId, totalAmount }
  → Validate: period exists and status=OPEN
    → CLOSED/LOCKED: return 422 PERIOD_CLOSED
  → Load AllocationRule by ruleId, capture current version
  → Create PostingJournal(DRAFT) with ruleVersion snapshot
  → Execute allocation engine based on rule.allocationMethod:
    → PROPORTIONAL: distribute totalAmount across cost centers by weight
    → FIXED: apply fixed amounts per cost center
    → TIERED: apply tiered thresholds
  → Apply revenue recognition:
    → IMMEDIATE: recognitionStart = recognitionEnd = postingDate
    → OVER_SESSION_DATES: recognitionStart = session.startTime, recognitionEnd = session.endTime
  → Create AllocationLineItem records for each distribution
  → Set PostingJournal status=POSTED, postedAt=now
  → Emit FINANCE_POSTING_RESULT notification
  → Write audit event: POSTING_CREATED with field diffs
```

### 4.7 Audit Export Flow

```
Admin → POST /api/audit/logs/export { dateFrom, dateTo, actionType, ... }
  → Check role: SYSTEM_ADMIN required
  → Check WatermarkPolicy for (AUDIT_LOG, userRole):
    → downloadAllowed = false: return 403, write audit EXPORT_DENIED
  → Create ExportJob(PENDING)
  → Generate CSV with filtered audit events
  → Inject watermark: operator name, timestamp, report context
  → Set ExportJob status=COMPLETED, filePath, fileSize, recordCount
  → Write audit event: EXPORT_GENERATED
  → Return exportId for download
```

## 5. Data Model Summary (Added Prompt 2)

### Entity Count by Domain

| Domain | Entities | Key Constraints |
|--------|----------|----------------|
| User | User | UNIQUE(username) |
| Event | EventSession | FK(created_by → users) |
| Registration | Registration | UNIQUE(user_id, session_id), FK(user_id, session_id) |
| Check-in | CheckInRecord, DeviceBinding, PasscodeState | UNIQUE(user_id, binding_date), FK chains |
| Notification | NotificationTemplate, Subscription, DndRule, SendLog | UNIQUE(idempotency_key), UNIQUE(user_id, notification_type) |
| Import | ImportSource, CrawlJob | FK(source_id), INDEX(trace_id) |
| Finance | AccountingPeriod, Account, CostCenter, AllocationRule, PostingJournal, AllocationLineItem | UNIQUE(account_code), UNIQUE(cost_center_code), FK chains |
| Audit | AuditEvent, FieldDiff, ExportJob, WatermarkPolicy | Immutable (INSERT-only), INDEX(timestamp, action, operator, entity) |
| Backup | BackupJob | INDEX(retention_expires_at) |

### Encryption-at-Rest Fields

| Table | Column | Type |
|-------|--------|------|
| users | contact_info | VARBINARY(512) |
| checkin_records | device_fingerprint | VARBINARY(512) |
| device_bindings | device_fingerprint | VARBINARY(512) |

## 6. Security Model (Added Prompt 3)

### 6.1 Authentication

- **Method**: Local username/password via Spring Security's `DaoAuthenticationProvider`
- **Password Storage**: BCrypt with strength 12 (no plaintext ever stored or logged)
- **Session Management**: Stateful sessions (`IF_REQUIRED` policy) — appropriate for offline local-network operation where token refresh infrastructure is unnecessary
- **Anti-Bot Throttling**: `LoginThrottleService` tracks failed attempts per user. After 5 consecutive failures, account is locked for 15 minutes (`AccountStatus.LOCKED` + `lockoutUntil`). Auto-unlocks after the lockout window expires.
- **Frontend State**: Pinia `auth` store persists user/token in `sessionStorage`. `initFromSession()` restores state on page reload.

### 6.2 Authorization (RBAC)

**Role Hierarchy (least-privilege)**:

| Role | Scope |
|------|-------|
| ATTENDEE | Own registrations, notifications, profile |
| EVENT_STAFF | Check-in, rosters, imports + ATTENDEE scope |
| FINANCE_MANAGER | Finance config, postings, reports + ATTENDEE scope |
| SYSTEM_ADMIN | All operations, audit, backups, user management |

**Enforcement Points**:
1. **Spring Security FilterChain**: URL-pattern rules (`/api/admin/**` → SYSTEM_ADMIN, `/api/finance/**` → FINANCE_MANAGER or SYSTEM_ADMIN, etc.)
2. **Method-level**: `@PreAuthorize` annotations using SpEL constants from `RoleConstants`
3. **Data-scope**: `PermissionEvaluator` checks ownership (attendees see only their own registrations)
4. **Vue Router guards**: `meta.roles` checked in `beforeEach` hook; unauthorized users redirected
5. **JSON error responses**: `JsonAuthenticationEntryPoint` (401) and `JsonAccessDeniedHandler` (403) return structured `ApiResponse` envelopes

### 6.3 Request Signature Verification

- **Algorithm**: HMAC-SHA256
- **Headers**: `X-Request-Signature`, `X-Request-Timestamp`, `X-Request-Nonce`
- **Replay Protection**: Timestamps rejected if older than 300 seconds. Nonces tracked in in-memory store with TTL-based cleanup.
- **Scope**: Mutating requests (POST/PUT/PATCH/DELETE) only; GET and public endpoints skipped
- **Configurable**: Disabled via `eventops.security.signature.enabled=false` in dev profile
- **Frontend**: `utils/signature.js` computes HMAC-SHA256 via Web Crypto API; `api/client.js` interceptor attaches headers

### 6.4 Rate Limiting

- **Baseline**: 60 requests/minute per authenticated user (configurable via `eventops.security.rate-limit.requests-per-minute`)
- **Algorithm**: Sliding window counter (lock-free `ConcurrentLinkedQueue` of timestamps)
- **Response**: HTTP 429 with `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers
- **Frontend**: axios response interceptor extracts `Retry-After` and attaches to error for UI display

### 6.5 AES-256 Encryption at Rest

- **Algorithm**: AES/GCM/NoPadding (authenticated encryption)
- **Key Derivation**: SHA-256 hash of configured secret → 256-bit `SecretKeySpec`
- **IV**: Random 12-byte IV prepended to ciphertext for each encryption
- **Encrypted Fields**: `users.contact_info`, `checkin_records.device_fingerprint`, `device_bindings.device_fingerprint` (stored as `VARBINARY(512)`)
- **JPA Integration**: `EncryptedFieldConverter` (`@Converter`) transparently encrypts/decrypts via static `ApplicationContextHolder`
- **Masked Display**: `EncryptionService.mask()` shows only last 4 characters by default. Frontend `MaskedField.vue` component provides toggle reveal.
- **Startup Validation**: `@PostConstruct` rejects default/placeholder encryption keys

### 6.6 Immutable Audit Logging

- **Architecture**: `AuditService` writes to `audit_events` and `field_diffs` tables — INSERT only, no UPDATE/DELETE
- **Field Diffs**: `FieldDiffCalculator` compares before/after snapshots. Sensitive fields (`passwordHash`, `contactInfo`, `deviceFingerprint`) are automatically redacted to `[REDACTED]`
- **Log Sanitization**: `LogSanitizer` regex-scrubs any sensitive key-value patterns before they reach application logs
- **Context**: `logForCurrentUser()` extracts operator identity from `SecurityContextHolder` and request source from `RequestContextHolder`

### 6.7 Structured Error Handling

- **Envelope**: All errors wrapped in `ApiResponse` with `success=false`, message, and `errors[]` array
- **Typed Exceptions**: `BusinessException` carries httpStatus, errorCode, and optional `ConflictType`
- **Validation**: `@Valid` request bodies produce field-level errors via `GlobalExceptionHandler`
- **Security Errors**: Authentication (401), authorization (403), rate-limit (429) all return JSON envelopes
- **No Leak**: Stack traces never exposed to clients. `GlobalExceptionHandler` logs WARN for 4xx, ERROR for 5xx.

## 7. Implemented Backend Services (Added Prompt 4)

### 7.1 Service Module Summary

| Service | Package | Key Responsibilities |
|---------|---------|---------------------|
| EventService | service.event | Session browsing/search with JPA Specification, availability computation |
| RegistrationService | service.registration | Register (confirm/waitlist), cancel, waitlist promotion with cutoff |
| PasscodeService | service.checkin | 6-digit SecureRandom passcode generation, 60s rotation, validation |
| CheckInService | service.checkin | Window enforcement, passcode check, device binding, conflict detection |
| NotificationService | service.notification | Template rendering, DND check, idempotency, retry backoff, delivery |
| ImportService | service.importing | Trigger crawl, concurrency cap, circuit breaker, checkpoint resume |

### 7.2 Scheduler Summary

| Scheduler | Cadence | Service Dependency |
|-----------|---------|-------------------|
| PasscodeRotationScheduler | Every 60s | PasscodeService.rotateAllActiveSessionPasscodes() |
| NotificationRetryScheduler | Every 30s | NotificationService.findRetryableNotifications() + attemptDelivery() |
| WaitlistPromotionScheduler | Every 5 min | RegistrationService.promoteNextWaitlisted() |
| ImportOrchestrationScheduler | Every 5 min (configurable) | ImportService.startIfUnderCap() |

### 7.3 Backoff Formula (Notification Retry)

```
backoff(attempt) = min(30 * 2^(attempt-1), 240) seconds
Attempt 1: 30s, Attempt 2: 60s, Attempt 3: 120s, Attempt 4: 240s, Attempt 5: 240s
Total worst-case: ~11.5 minutes (5 attempts)
```

## 8. Finance and Compliance Architecture (Added Prompt 5)

### 8.1 Finance Engine

The finance computation engine lives in `com.eventops.finance` as pure Java classes (no Spring dependency) for easy unit testing:

- **AllocationEngine**: Strategy resolver returning the correct calculator per `AllocationMethod`
- **ProportionalAllocator**: Distributes by weight ratios. Rounding remainder adjusted to last entry. Sum always equals totalAmount.
- **FixedAllocator**: Assigns configured fixed amounts per cost center. Remainder tracked as UNALLOCATED.
- **TieredAllocator**: Walks through threshold tiers applying rates. Parses tier config from JSON.
- **RevenueRecognizer**: IMMEDIATE returns posting date. OVER_SESSION_DATES uses session start/end dates.
- **RuleVersionManager**: Creates new rule versions by copying + incrementing version number. Old versions are deactivated but preserved.

### 8.2 Posting Lifecycle

```
FinanceService.executePosting(periodId, ruleId, totalAmount):
  1. Validate period is OPEN (else 422 PERIOD_CLOSED)
  2. Load AllocationRule, capture current version
  3. Create PostingJournal(DRAFT) with ruleVersion snapshot
  4. Resolve AllocationCalculator via AllocationEngine
  5. Execute allocation → List<AllocationResult>
  6. Compute recognition period via RevenueRecognizer
  7. Create AllocationLineItem per result with recognition dates
  8. Set PostingJournal status=POSTED, postedAt=now
  9. Audit: POSTING_CREATED with field diffs
  10. Notify: FINANCE_POSTING_RESULT
```

### 8.3 Export Restriction Architecture

- **WatermarkPolicy** entity stores per-report-type/role download permissions and watermark templates
- **ExportService** enforces policies at both generation and download time
- **CsvGenerator** utility produces RFC 4180-compliant CSV with watermark header
- Watermark content: operator name, timestamp, report type, confidentiality notice
- All export attempts (allowed/denied) are audit-logged as EXPORT_GENERATED or EXPORT_DENIED
- Three export types: AUDIT_LOG, ROSTER, FINANCE_REPORT

### 8.4 Backup Retention Design

- **BackupScheduler**: Nightly at 2:00 AM (configurable cron), cleanup at 3:00 AM
- **BackupService.executeBackup()**: Creates backup artifact at configurable path, records metadata
- **BackupService.cleanupExpiredBackups()**: Queries expired jobs (retentionExpiresAt <= now), deletes files, audits cleanup
- Retention: 30 days (configurable via `eventops.backup.retention-days`)
- Admin-visible: BackupController exposes history, status, retention metrics

## 9. Frontend Shell Architecture (Added Prompt 6)

### 9.1 Layout System

- **App.vue**: Dynamic layout switching via `route.meta.layout` computed property. Initializes auth and offline stores on mount.
- **MainLayout**: Authenticated shell — AppHeader + AppSidebar + main content + AppFooter + OfflineIndicator + PendingActionsBar
- **AuthLayout**: Centered card for login/registration pages

### 9.2 Route Map

| Route | View | Auth | Roles |
|-------|------|------|-------|
| /login | LoginView | No | — |
| /account | AccountView | No | — |
| / | EventListView | Yes | All |
| /events/:id | EventDetailView | Yes | All |
| /registration/:sessionId | RegistrationView | Yes | All |
| /waitlist | WaitlistView | Yes | All |
| /checkin | CheckInView | Yes | STAFF, ADMIN |
| /notifications | NotificationCenterView | Yes | All |
| /notifications/settings | SubscriptionSettingsView | Yes | All |
| /finance/* | Finance views | Yes | FINANCE_MANAGER, ADMIN |
| /admin/* | Admin views | Yes | ADMIN |

### 9.3 Offline-Capable Client Behavior

- **Offline detection**: `navigator.onLine` + `online`/`offline` event listeners in offlineStore
- **Pending action queue**: localforage-backed (`offline_queue` IndexedDB store). Actions queued when offline with method/url/data/timestamp.
- **Replay**: Sequential replay on reconnection. 409 conflicts auto-discarded. Failed replays pause the queue.
- **Roster cache**: localforage-backed (`roster_cache`) with 5-minute TTL. Used for check-in roster display when offline.
- **Data cache**: Generic TTL-based cache (`data_cache`) for session metadata and event listings.
- **UI indicators**: Orange offline banner, blue pending-actions bar with "Sync Now" button.

### 9.4 Store Architecture

All stores use Pinia Composition API (setup style) with:
- Reactive refs for data, loading, error states
- Async actions calling real API modules
- Conflict-specific state (conflictMessage, conflictDetail) for 409 handling
- Optimistic UI updates where appropriate (mark-as-read, cancel registration)

## 10. Complete Screen Map and Role-to-Module Matrix (Added Prompt 7)

### 10.1 Complete View Inventory

| View | Route | Roles | Store | Status |
|------|-------|-------|-------|--------|
| LoginView | /login | Public | auth | Implemented P6 |
| AccountView | /account | Public | auth | Implemented P6 |
| EventListView | / | All | events | Implemented P6 |
| EventDetailView | /events/:id | All | events, registration | Implemented P6 |
| RegistrationView | /registration/:id | All | registration | Implemented P6 |
| WaitlistView | /waitlist | All | registration | Implemented P6 |
| CheckInView | /checkin | STAFF, ADMIN | checkin, events, offline | Implemented P6 |
| DeviceBindingView | /checkin/device-binding | STAFF, ADMIN | checkin, events | Implemented P6 |
| NotificationCenterView | /notifications | All | notifications | Implemented P6 |
| SubscriptionSettingsView | /notifications/settings | All | notifications | Implemented P6 |
| FinanceDashboardView | /finance | FINANCE, ADMIN | finance | Implemented P7 |
| AccountingPeriodsView | /finance/periods | FINANCE, ADMIN | finance | Implemented P7 |
| AllocationView | /finance/allocations | FINANCE, ADMIN | finance | Implemented P7 |
| AuditLogView | /admin/audit | ADMIN | admin | Implemented P7 |
| SecuritySettingsView | /admin/security | ADMIN | admin | Implemented P7 |
| BackupStatusView | /admin/backups | ADMIN | admin | Implemented P7 |
| UserManagementView | /admin/users | ADMIN | admin | Implemented P7 |
| ImportDashboardView | /imports | STAFF, ADMIN | admin | Implemented P7 |
| ExportDashboardView | /exports | STAFF, FINANCE, ADMIN | admin | Implemented P7 |

### 10.2 Role-to-Module Access Matrix

| Module | ATTENDEE | EVENT_STAFF | FINANCE_MANAGER | SYSTEM_ADMIN |
|--------|----------|-------------|-----------------|--------------|
| Event Browsing | Yes | Yes | Yes | Yes |
| Registration | Yes (own) | — | — | Yes (all) |
| Waitlist | Yes (own) | — | — | Yes |
| Check-In | — | Yes | — | Yes |
| Notifications | Yes (own) | Yes (own) | Yes (own) | Yes (all) |
| Finance Config | — | — | Yes | Yes |
| Postings/Allocations | — | — | Yes | Yes |
| Audit Logs | — | — | — | Yes |
| User Management | — | — | — | Yes |
| Security Settings | — | — | — | Yes |
| Backups | — | — | — | Yes |
| Imports | — | Yes | — | Yes |
| Exports | — | Yes | Yes | Yes |

## 11. Test Suite Architecture (Added Prompt 8)

### 11.1 Test Infrastructure

- **Backend unit tests**: JUnit 5 + Mockito, run via Maven Surefire, sources in `backend/unit_tests/`
- **Backend API tests**: JUnit 5 + Spring Boot Test + MockMvc, run via Maven Failsafe, sources in `backend/api_tests/`
- **Frontend unit tests**: Vitest + @vue/test-utils, sources in `frontend/unit_tests/`
- **Orchestrator**: `repo/run_tests.sh` with suite selection and `--coverage` flag
- **Coverage**: JaCoCo (backend), V8/Vitest (frontend)
- **Traceability**: `docs/test-traceability.md` maps 13 requirement domains to 65 test files

### 11.2 Test Counts

| Category | Files | ~Methods |
|----------|-------|----------|
| Backend unit | 33 | 187 |
| Backend API/IT | 9 | 43 |
| Frontend unit | 23 | 129 |
| **Total** | **65** | **~359** |

## 12. Docker and Runtime Configuration (Added Prompt 9)

### 12.1 Container Architecture

Three-service Docker Compose stack (`docker-compose.yml`):

| Service | Container | Internal Port | Default External Port | Image |
|---------|-----------|--------------|----------------------|-------|
| MySQL | eventops-mysql | 3306 | 3306 (`MYSQL_PORT`) | mysql:8.0 |
| Backend | eventops-backend | 8443 | 8443 (`BACKEND_PORT`) | Built from `backend/Dockerfile` |
| Frontend | eventops-frontend | 80 | 443 (`FRONTEND_PORT`) | Built from `frontend/Dockerfile` |

Startup order: MySQL (health-checked) → Backend → Frontend.

Named volumes: `mysql-data`, `backup-data`, `export-data`, `shared-data`, `attachment-data`.

### 12.2 Build Strategy

**Backend** (multi-stage Maven build):
1. Stage 1: `maven:3.9-eclipse-temurin-17` — resolves dependencies offline, packages JAR
2. Stage 2: `eclipse-temurin:17-jre-alpine` — minimal JRE runtime, runs as non-root `eventops` user

**Frontend** (multi-stage Node build):
1. Stage 1: `node:20-alpine` — `npm ci`, `npm run build` (picks up `.env.production`)
2. Stage 2: `nginx:stable-alpine` — serves `/dist` on port 80, proxies `/api/*` to backend

### 12.3 Network and Proxy Design

The nginx container proxies `/api/*` to the backend using the Docker Compose internal hostname:

```
Browser → nginx:80 (frontend container)
             └─→ http://backend:8443/api/   (plain HTTP; SSL_ENABLED=false default)
                     └─→ MySQL:3306
```

- All browser requests arrive at the same origin (nginx port 443), so CORS is not exercised during normal use
- `WebConfig.addCorsMappings()` handles direct API access (dev tooling, automated tests)
- `CORS_ALLOWED_ORIGINS` env var controls allowed origins (default: localhost:443, localhost:5173, localhost)

### 12.4 TLS / HTTPS Configuration

| Mode | Setting | Effect |
|------|---------|--------|
| Default (secure) | `SPRING_PROFILES_ACTIVE=` and `SSL_ENABLED=false` | Backend serves plain HTTP on port 8443 with signature verification enabled |
| Production | `SSL_ENABLED=true` | Backend serves HTTPS; keystore required |

**Keystore provisioning** (when SSL_ENABLED=true):
- See `backend/src/main/resources/keystore/README.md` for `keytool` generation steps
- Set `SSL_KEYSTORE`, `SSL_KEYSTORE_PASSWORD` env vars
- Update `frontend/nginx.conf` to use `https://backend:8443/api/`
- Keystore files (`.p12`, `.jks`) are gitignored and must be provisioned per deployment

**Request signature verification** is a separate transport-independent control:
- Disabled only in the explicit dev profile (`SIGNATURE_ENABLED=false` in `application-dev.yml`)
- Enabled in production: HMAC-SHA256, 300-second replay window, nonce deduplication

### 12.5 Environment Variable Reference

All config is externalized. See `.env.example` for full documentation. Critical variables:

| Variable | Default | Notes |
|----------|---------|-------|
| `ENCRYPTION_SECRET_KEY` | none | **Must be set before first startup** |
| `SSL_ENABLED` | `false` | Set true for HTTPS with keystore |
| `SPRING_PROFILES_ACTIVE` | empty | Empty for secure defaults; set `dev` only in the explicit dev override |
| `SERVER_PORT` | `8443` | Must match Docker port mapping and nginx proxy target |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:443,...` | Comma-separated |
| `BACKUP_PATH` | `/var/eventops/backups` | Mapped to `backup-data` volume |
| `SHARED_FOLDER_PATH` | `/var/eventops/shared` | Import source root |

> **Port Invariant**: `SERVER_PORT=8443` in docker-compose env, `EXPOSE 8443` in backend Dockerfile, and `proxy_pass http://backend:8443/api/` in nginx.conf must all agree. The dev Spring profile does **not** override `server.port` — port is exclusively controlled by the `SERVER_PORT` env var.

### 12.6 CORS Policy

`WebConfig.java` implements `WebMvcConfigurer.addCorsMappings()` for `/api/**`:
- Allowed origins: configurable via `CORS_ALLOWED_ORIGINS` env var
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Credentials: allowed
- Max age: 3600s

See `questions.md` #12 for rationale.

---

## 13. Final Static Audit (Added Prompt 10)

### 13.1 Requirement Completeness

All 13 prompt requirement domains are implemented and verified:

| Domain | Status | Evidence |
|--------|--------|----------|
| Auth / Anti-bot | Complete | `AuthService`, `LoginThrottleService`, `PasswordService`, `AuthController` |
| RBAC | Complete | `SecurityConfig`, `RoleConstants`, `PermissionEvaluator`, `router/index.js` |
| Request Signatures | Complete | `SignatureVerificationFilter`, `utils/signature.js`, `api/client.js` |
| AES-256 Encryption | Complete | `EncryptionService`, `EncryptedFieldConverter`, `MaskedField.vue` |
| Registration/Waitlist | Complete | `RegistrationService`, `WaitlistPromotionScheduler` |
| Check-in | Complete | `CheckInService`, `PasscodeService`, `DeviceBindingRepository` |
| Notifications | Complete | `NotificationService`, `DndRule`, retry queue |
| Import Orchestration | Complete | `ImportService`, circuit breaker, checkpoint, `ImportOrchestrationScheduler` |
| Finance Engine | Complete | Allocators, `RevenueRecognizer`, `RuleVersionManager`, `FinanceService` |
| Audit/Export | Complete | `AuditService` (INSERT-only), `FieldDiffCalculator`, `ExportService` |
| Backup | Complete | `BackupService`, `BackupScheduler` (2 AM cron), 30-day retention |
| Offline/Connectivity | Complete | `offlineStore`, `OfflineIndicator`, queue, replay, roster cache |
| Docker/Config | Complete | `docker-compose.yml`, Dockerfiles, config hardening (Prompt 9) |

### 13.2 Repo Structure Compliance

```
repo/
├── run_tests.sh              ✓ exists, targets correct folders
├── .gitignore                ✓ excludes keystores, .env, build artifacts
├── .env.example              ✓ all env vars documented
├── frontend/
│   └── unit_tests/           ✓ 23 test files (Vitest)
└── backend/
    ├── unit_tests/           ✓ 34 test files (JUnit 5)
    └── api_tests/            ✓ 9 test files (JUnit 5 / Failsafe)
```

No root-level `unit_tests/` or `API_tests/` directories exist.

### 13.3 Security Invariants Verified

| Invariant | Implementation | Test |
|-----------|---------------|------|
| BCrypt strength 12 | `PasswordService` | `PasswordServiceTest` |
| 5-attempt lockout → 15 min | `LoginThrottleService` | `LoginThrottleTest` |
| AES-256-GCM, @PostConstruct key validation | `EncryptionService` | `EncryptionServiceTest` |
| Audit INSERT-only | `AuditService` | `AuditImmutabilityTest` |
| Log sanitization | `LogSanitizer` | `LogSanitizerTest` |
| 60 req/min sliding window | `RateLimitFilter` | `RateLimitTest`, `SlidingWindowCounterTest` |
| HMAC-SHA256, 300s replay window | `SignatureVerificationFilter` | `AuthFlowIT` |
| 30-day backup retention | `BackupService` | `BackupServiceTest`, `BackupRetentionIT` |

### 13.4 Test Suite Summary

| Suite | Files | Methods | Runner |
|-------|-------|---------|--------|
| Backend unit | 34 | ~191 | JUnit 5 / Surefire |
| Backend API | 9 | ~43 | JUnit 5 / Failsafe |
| Frontend unit | 23 | ~129 | Vitest |
| **Total** | **66** | **~363** | |

`ConfigPropertiesTest.java` added in Prompt 9 (config defaults verification).

## 14. Current State (After Prompts 9–10)

- **Static audit complete**: all requirements verified against implementation, docs, tests
- **Config hardened**: port conflict fixed, nginx proxy scheme corrected, CORS configured, keystore documented
- **Docs synchronized**: README, design.md, api-spec.md, test-traceability.md, questions.md all consistent
- **Docker/config consistent**: service names, ports, env vars, and startup commands agree across all files
- **Test suite**: 66 files, ~363 methods; `ConfigPropertiesTest` adds config-default coverage
- **Ready for execution**: after provisioning `ENCRYPTION_SECRET_KEY`, run `docker compose up --build`
- **Not yet executed**: Docker builds and test runs remain deferred until execution environment is available

