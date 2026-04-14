# Requirement-to-Test Traceability Matrix

This document maps each major prompt requirement to the code modules that implement it and the test files that verify it.

## Legend

- **BE-UT**: Backend unit test (`repo/backend/unit_tests/`)
- **BE-IT**: Backend API/integration test (`repo/backend/api_tests/`)
- **FE-UT**: Frontend unit test (`repo/frontend/unit_tests/`)

---

## 1. Authentication & Anti-Bot

| Requirement                                     | Implementation                                 | Tests                                                   |
| ----------------------------------------------- | ---------------------------------------------- | ------------------------------------------------------- |
| Local username/password login                   | `security.auth.AuthService`, `AuthController`  | BE-UT: `AuthServiceTest` (login_success, login_failure) |
| BCrypt password hashing (strength 12)           | `security.auth.PasswordService`                | BE-UT: `PasswordServiceTest` (encode, matches, prefix)  |
| Anti-bot throttle (5 attempts → 15-min lockout) | `security.auth.LoginThrottleService`           | BE-UT: `LoginThrottleTest`, `AuthServiceTest` (lockout) |
| Session lifecycle (offline local-network)       | `security.config.SecurityConfig` (IF_REQUIRED) | BE-IT: `AuthFlowIT` (register→login→access)             |
| Frontend auth state & session persistence       | `stores/auth.js`, `api/client.js` interceptors | FE-UT: `auth.test.js`, `guards.test.js`                 |

## 2. RBAC & Authorization

| Requirement                                | Implementation                                        | Tests                                             |
| ------------------------------------------ | ----------------------------------------------------- | ------------------------------------------------- |
| 4 roles: ATTENDEE, STAFF, FINANCE, ADMIN   | `domain.user.RoleType`, `security.rbac.RoleConstants` | BE-UT: `EnumValidationTest`, `RbacTest`           |
| URL-pattern authorization                  | `security.config.SecurityConfig` FilterChain          | BE-IT: `UnauthorizedAccessIT` (7 endpoint groups) |
| Data-scope restriction (own registrations) | `security.rbac.PermissionEvaluator`                   | BE-UT: `RbacTest` (9 tests)                       |
| Frontend route guards with role checks     | `router/index.js` beforeEach                          | FE-UT: `guards.test.js` (6 tests)                 |
| Sidebar role filtering                     | `components/AppSidebar.vue`                           | FE-UT: `AppSidebar.test.js` (5 tests)             |

## 3. Request Signatures & Rate Limiting

| Requirement                              | Implementation                                    | Tests                                                                                                                |
| ---------------------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| HMAC-SHA256 signature verification       | `security.signature.SignatureVerificationFilter`  | BE-UT: `SignatureVerificationFilterTest` (header checks + skip rules); IT tests run with sig disabled (see footnote) |
| Replay protection (300s, nonce dedup)    | `SignatureVerificationFilter` nonce store         | BE-UT: `SignatureVerificationFilterTest`; no end-to-end IT coverage (see footnote)                                   |
| CORS preflight compatibility (`OPTIONS`) | `SignatureVerificationFilter.doFilterInternal()`  | BE-UT: `SignatureVerificationFilterTest` (allows unsigned OPTIONS through filter chain)                              |
| 60 req/min rate limiting                 | `security.ratelimit.RateLimitFilter`              | BE-UT: `RateLimitTest`, `SlidingWindowCounterTest`                                                                   |
| Frontend signature computation           | `utils/signature.js`, `api/client.js` interceptor | FE-UT: `signature.test.js` (5 tests)                                                                                 |

## 4. AES-256 Encryption at Rest

| Requirement                     | Implementation                                | Tests                                                        |
| ------------------------------- | --------------------------------------------- | ------------------------------------------------------------ |
| AES-256-GCM field encryption    | `security.encryption.EncryptionService`       | BE-UT: `EncryptionServiceTest` (round-trip, unique IV)       |
| JPA transparent encrypt/decrypt | `security.encryption.EncryptedFieldConverter` | BE-UT: `EncryptionMaskingTest` (11 tests)                    |
| Masked-by-default display       | `EncryptionService.mask()`, `MaskedField.vue` | BE-UT: `EncryptionMaskingTest`; FE-UT: `MaskedField.test.js` |
| Startup key validation          | `EncryptionService.@PostConstruct`            | BE-UT: `EncryptionMaskingTest`                               |

## 5. Registration & Duplicate Blocking

| Requirement                           | Implementation                                         | Tests                                                      |
| ------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------- |
| One registration per user per session | `service.registration.RegistrationService` + DB UNIQUE | BE-UT: `RegistrationServiceTest` (duplicate → 409)         |
| Server-side quota enforcement         | `RegistrationService.register()`                       | BE-UT: `RegistrationServiceTest` (confirmed vs waitlisted) |
| Immediate conflict feedback           | `RegistrationController` → `BusinessException`         | BE-IT: `RegistrationFlowIT` (duplicate conflict)           |
| Frontend duplicate detection          | `stores/registration.js` conflictMessage               | FE-UT: `registration.test.js` (DUPLICATE_REGISTRATION)     |

## 6. Waitlist & Promotion

| Requirement                  | Implementation                                | Tests                                                                  |
| ---------------------------- | --------------------------------------------- | ---------------------------------------------------------------------- |
| Waitlist entry at capacity   | `RegistrationService.register()`              | BE-UT: `RegistrationServiceTest` (waitlisted when full)                |
| Promotion-cutoff logic       | `RegistrationService.promoteNextWaitlisted()` | BE-UT: `RegistrationServiceTest` (promote before cutoff, expire after) |
| Scheduled reconciliation     | `scheduler.WaitlistPromotionScheduler`        | BE-UT: `RegistrationServiceTest` (noop when full/empty)                |
| Frontend waitlist visibility | `views/registration/WaitlistView.vue`         | FE-UT: `views/registration.test.js`                                    |

## 7. Check-In, Passcode, Device Binding

| Requirement                        | Implementation                               | Tests                                                   |
| ---------------------------------- | -------------------------------------------- | ------------------------------------------------------- |
| Rotating 6-digit passcode (60s)    | `service.checkin.PasscodeService`            | BE-UT: `PasscodeServiceTest` (6 tests)                  |
| Check-in window enforcement        | `service.checkin.CheckInService`             | BE-UT: `CheckInServiceTest` (before/after/boundary)     |
| Passcode validation                | `PasscodeService.validatePasscode()`         | BE-UT: `PasscodeServiceTest` (match, expired, mismatch) |
| Device binding (1/day)             | `CheckInService` + `DeviceBindingRepository` | BE-UT: `CheckInServiceTest` (new binding, conflict)     |
| Concurrent multi-device warning    | `CheckInService`                             | BE-UT: `CheckInServiceTest`                             |
| Duplicate check-in blocking        | `CheckInService`                             | BE-UT: `CheckInServiceTest` (duplicate → 409)           |
| Frontend conflict states (6 types) | `stores/checkin.js`, `CheckInView.vue`       | FE-UT: `checkin.test.js`, `views/checkin.test.js`       |

## 8. Notifications, Subscriptions, DND

| Requirement                                   | Implementation                                          | Tests                                                 |
| --------------------------------------------- | ------------------------------------------------------- | ----------------------------------------------------- |
| Templated in-app notifications                | `service.notification.NotificationService`              | BE-UT: `NotificationServiceTest` (send, template)     |
| Subscription controls                         | `NotificationService` + `SubscriptionRepository`        | BE-UT: `NotificationServiceTest` (disabled skip)      |
| DND default 9PM-7AM                           | `domain.notification.DndRule`                           | BE-UT: `DndRuleTest`, `NotificationServiceTest` (DND) |
| Retry queue (5 attempts, exponential backoff) | `NotificationService.attemptDelivery()`                 | BE-UT: `NotificationServiceTest` (5 backoff tests)    |
| Idempotency keys                              | `NotificationService` + DB UNIQUE                       | BE-UT: `NotificationServiceTest` (deduplicate)        |
| Frontend notification center                  | `stores/notifications.js`, `NotificationCenterView.vue` | FE-UT: `notifications.test.js` (5 tests)              |

## 9. Import Orchestration

| Requirement                   | Implementation                               | Tests                                               |
| ----------------------------- | -------------------------------------------- | --------------------------------------------------- |
| Concurrency cap (3 workers)   | `service.importing.ImportService`            | BE-UT: `ImportServiceTest` (under/at cap)           |
| 30-second timeout             | `ImportService.executeCrawl()`               | BE-UT: `ImportServiceTest` (timeout)                |
| Circuit breaker (10 failures) | `ImportService`                              | BE-UT: `ImportServiceTest` (circuit breaker)        |
| Checkpoint resume             | `ImportService`, `CrawlJob.checkpointMarker` | BE-UT: `ImportServiceTest` (checkpoint)             |
| Trace IDs                     | `CrawlJob.traceId` (auto-generated)          | BE-UT: `ImportServiceTest` (queued job has traceId) |
| Frontend import dashboard     | `ImportDashboardView.vue`                    | FE-UT: `views/admin.test.js`                        |

## 10. Finance Engine

| Requirement                                  | Implementation                                      | Tests                                                    |
| -------------------------------------------- | --------------------------------------------------- | -------------------------------------------------------- |
| Accounting periods (configurable)            | `service.finance.FinanceService`                    | BE-UT: `FinanceServiceTest` (create, validate, close)    |
| Chart of accounts                            | `FinanceService.createAccount()`                    | BE-UT: `FinanceServiceTest`                              |
| Cost centers                                 | `FinanceService.createCostCenter()`                 | BE-UT: `FinanceServiceTest`                              |
| Revenue recognition (immediate / over dates) | `finance.recognition.RevenueRecognizer`             | BE-UT: `RevenueRecognizerTest` (3 tests)                 |
| Allocation: proportional                     | `finance.allocation.ProportionalAllocator`          | BE-UT: `AllocationCalculatorTest` (3 tests)              |
| Allocation: fixed                            | `finance.allocation.FixedAllocator`                 | BE-UT: `AllocationCalculatorTest` (2 tests)              |
| Allocation: tiered                           | `finance.allocation.TieredAllocator`                | BE-UT: `AllocationCalculatorTest` (1 test)               |
| Rule versioning                              | `finance.rules.RuleVersionManager`                  | BE-UT: `RuleVersionTest` (3 tests), `FinanceServiceTest` |
| Auditable line items                         | `FinanceService.executePosting()`                   | BE-UT: `FinanceServiceTest` (posting creates items)      |
| Frontend finance views                       | `FinanceDashboard/AccountingPeriods/AllocationView` | FE-UT: `finance.test.js`, `stores/finance.test.js`       |

## 11. Audit & Export

| Requirement                        | Implementation                                     | Tests                                         |
| ---------------------------------- | -------------------------------------------------- | --------------------------------------------- |
| Immutable audit logs (INSERT-only) | `audit.logging.AuditService`                       | BE-UT: `AuditImmutabilityTest` (7 tests)      |
| Field-level diffs                  | `audit.diff.FieldDiffCalculator`                   | BE-UT: `AuditFieldDiffTest` (7 tests)         |
| Sensitive-field redaction          | `FieldDiffCalculator.SENSITIVE_FIELDS`             | BE-UT: `AuditFieldDiffTest` (redaction tests) |
| Log sanitization                   | `audit.logging.LogSanitizer`                       | BE-UT: `LogSanitizerTest` (5 tests)           |
| Searchable/exportable CSV          | `service.audit.AuditQueryService`, `ExportService` | BE-IT: `ExportRestrictionIT`                  |
| Watermark policies                 | `domain.audit.WatermarkPolicy`, `ExportService`    | BE-UT: `ExportPolicyTest` (4 tests)           |
| Download restrictions              | `ExportService.isDownloadAllowed()`                | BE-IT: `ExportRestrictionIT` (5 tests)        |

## 12. Backups & Retention

| Requirement                | Implementation                       | Tests                                                               |
| -------------------------- | ------------------------------------ | ------------------------------------------------------------------- |
| Nightly backup (2 AM cron) | `scheduler.BackupScheduler`          | BE-UT: `BackupServiceTest`                                          |
| 30-day retention           | `service.backup.BackupService`       | BE-UT: `BackupServiceTest` (expiry, retention days, EXPIRED status) |
| Admin-visible history      | `controller.backup.BackupController` | BE-IT: `BackupRetentionIT` (3 tests)                                |

## 13. Offline/Weak Connectivity

| Requirement                  | Implementation                                                                                                   | Tests                                                                                           |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Offline detection            | `stores/offline.js`, `utils/offline.js`                                                                          | FE-UT: `offline.test.js`, `stores/offline.test.js`                                              |
| Pending-action queue         | `offlineStore.enqueuePendingAction()`                                                                            | FE-UT: `stores/offline.test.js`                                                                 |
| Replay on reconnect          | `offlineStore.replayPendingActions()`                                                                            | FE-UT: `stores/offline.test.js`                                                                 |
| Resumable attachment uploads | `controller.attachment.AttachmentController`, `service.attachment.AttachmentService`, `utils/resumableUpload.js` | BE: implemented; FE: store wiring in `offlineStore.uploadAttachment()`; dedicated tests pending |
| Roster cache (5-min TTL)     | `offlineStore.cacheRoster()`                                                                                     | FE-UT: `stores/offline.test.js`                                                                 |
| Offline UI indicators        | `OfflineIndicator.vue`, `PendingActionsBar.vue`                                                                  | FE-UT: `OfflineIndicator.test.js`                                                               |

## 14. Docker / Config Hardening (Prompt 9)

| Requirement                        | Implementation                                                                       | Tests                                                           |
| ---------------------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------- |
| Config defaults match requirements | `AppConstants`, `RateLimitProperties`, `SignatureProperties`, `EncryptionProperties` | BE-UT: `ConfigPropertiesTest` (16 tests)                        |
| CORS for local-network API access  | `config.WebConfig.addCorsMappings()`                                                 | BE-UT: `ConfigPropertiesTest` (cors defaults test)              |
| Backup EXPIRED status semantics    | `domain.backup.BackupStatus`, `BackupService`                                        | BE-UT: `BackupServiceTest` (expiredStatus_isDistinctFromFailed) |
| Import timeout enforcement         | `service.importing.ImportService.executeCrawl()`                                     | BE-UT: `ImportServiceTest` (timeoutSeconds_defaultIs30)         |

---

## Test File Inventory

### Backend Unit Tests (35 files)

| File                                 | Tests | Domain                                    |
| ------------------------------------ | ----- | ----------------------------------------- |
| EventOpsApplicationTests.java        | 2     | Bootstrap                                 |
| EnumValidationTest.java              | 13    | Domain enums                              |
| EventSessionTest.java                | 8     | Event entity                              |
| RegistrationTest.java                | 2     | Registration entity                       |
| DndRuleTest.java                     | 3     | Notification entity                       |
| SendLogTest.java                     | 3     | Notification entity                       |
| ImportSourceTest.java                | 4     | Import entity                             |
| AllocationRuleTest.java              | 2     | Finance entity                            |
| ApiResponseTest.java                 | 3     | Common DTO                                |
| PagedResponseTest.java               | 3     | Common DTO                                |
| PasswordServiceTest.java             | 5     | Auth security                             |
| LoginThrottleTest.java               | 4     | Auth security                             |
| RbacTest.java                        | 9     | Authorization                             |
| EncryptionServiceTest.java           | 5     | Encryption                                |
| EncryptionMaskingTest.java           | 11    | Encryption masking                        |
| RateLimitTest.java                   | 3     | Rate limiting                             |
| SlidingWindowCounterTest.java        | 9     | Rate limiting                             |
| SignatureVerificationFilterTest.java | 3     | Signature verification filter             |
| AuditFieldDiffTest.java              | 7     | Audit diffs                               |
| LogSanitizerTest.java                | 5     | Log sanitization                          |
| AuditImmutabilityTest.java           | 7     | Audit immutability                        |
| AuthServiceTest.java                 | 8     | Auth service                              |
| EventServiceTest.java                | 9     | Event service                             |
| RegistrationServiceTest.java         | 10    | Registration service                      |
| CheckInServiceTest.java              | 10    | Check-in service                          |
| NotificationServiceTest.java         | 14    | Notification service                      |
| ImportServiceTest.java               | 8     | Import service (+ timeout, traceId tests) |
| FinanceServiceTest.java              | 10    | Finance service                           |
| PasscodeServiceTest.java             | 10    | Passcode service                          |
| BackupServiceTest.java               | 5     | Backup service (+ EXPIRED status test)    |
| ExportPolicyTest.java                | 4     | Export policies                           |
| AllocationCalculatorTest.java        | 8     | Allocation math                           |
| RevenueRecognizerTest.java           | 3     | Revenue recognition                       |
| RuleVersionTest.java                 | 3     | Rule versioning                           |
| ConfigPropertiesTest.java            | 16    | Config defaults (added Prompt 9)          |

**Total: ~198 backend unit test methods**

### Backend API/Integration Tests (10 files)

> **Note on signature coverage:** End-to-end signed-request integration coverage is provided by
> `SignatureFlowIT`, which runs against the production Spring profile with a provisioned
> `SIGNATURE_SECRET_KEY`. It exercises valid signature acceptance, missing-header rejection, stale
> timestamp rejection, and replay-nonce rejection (REQ-SEC-04, REQ-SEC-05).

| File                        | Tests | Domain                 |
| --------------------------- | ----- | ---------------------- |
| AuthFlowIT.java             | 5     | Auth flow              |
| UnauthorizedAccessIT.java   | 8     | Authorization          |
| RegistrationFlowIT.java     | 4     | Registration flow      |
| CheckInFlowIT.java          | 5     | Check-in flow          |
| NotificationRetryIT.java    | 4     | Notification retry     |
| ImportCircuitBreakerIT.java | 4     | Import circuit breaker |
| FinanceFlowIT.java          | 5     | Finance flow           |
| BackupRetentionIT.java      | 3     | Backup retention       |
| ExportRestrictionIT.java    | 5     | Export restrictions    |
| SignatureFlowIT.java        | 5     | Signature validation (end-to-end) |

**Total: ~48 backend API test methods**

### Frontend Unit Tests (23 files)

| File                                | Tests | Domain              |
| ----------------------------------- | ----- | ------------------- |
| stores/auth.test.js                 | 6     | Auth store          |
| stores/events.test.js               | 4     | Events store        |
| stores/registration.test.js         | 4     | Registration store  |
| stores/checkin.test.js              | 5     | Check-in store      |
| stores/notifications.test.js        | 5     | Notifications store |
| stores/finance.test.js              | 5     | Finance store       |
| stores/admin.test.js                | 6     | Admin store         |
| stores/offline.test.js              | 6     | Offline store       |
| api/types.test.js                   | 11    | Type contracts      |
| router/guards.test.js               | 6     | Route guards        |
| components/AppHeader.test.js        | 2     | Header component    |
| components/MaskedField.test.js      | 8     | Masked display      |
| components/AppSidebar.test.js       | 5     | Role filtering      |
| components/ConfirmDialog.test.js    | 4     | Dialog component    |
| components/OfflineIndicator.test.js | 3     | Offline UI          |
| utils/offline.test.js               | 6     | Offline utils       |
| utils/cache.test.js                 | 3     | Cache utils         |
| utils/signature.test.js             | 5     | Signature utils     |
| utils/formatters.test.js            | 8     | Formatters          |
| views/registration.test.js          | 5     | Registration UI     |
| views/checkin.test.js               | 5     | Check-in UI         |
| views/finance.test.js               | 6     | Finance UI          |
| views/admin.test.js                 | 7     | Admin UI            |

**Total: ~129 frontend test methods**

---

## Coverage Positioning

Coverage percentages in this document are directional and should not be interpreted as measured line/branch coverage unless generated reports are attached.

| Module                | Current Confidence | Key Risk / Note                                                                                                                                |
| --------------------- | ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Auth/Security         | Medium             | Core auth and lockout behavior tested; signature/rate-limit chain ordering needs ongoing IT coverage                                           |
| Registration/Waitlist | Medium             | Duplicate and waitlist flows covered by seeded API tests; more edge-case fixtures still recommended                                            |
| Check-In              | Medium             | Passcode/authz and window-closed failure paths covered; additional conflict-path IT depth still valuable                                       |
| Notifications         | Medium             | Retry/backoff and idempotency unit tests exist; template provisioning and dispatch-path IT coverage should continue                            |
| Finance Engine        | Medium             | Allocation logic unit-tested; posting/export authorization scope requires continued IT assertions                                              |
| Import Orchestration  | Medium             | Concurrency/circuit behavior covered in unit tests; queue-priority ordering now implemented and should be regression-tested in scheduler flows |
| Audit/Export          | Medium             | Audit write immutability tested; object-scope export authorization now enforced and should be validated with role/user matrix tests            |
| Backup                | Medium             | Retention and cleanup logic tested; environment-specific backup path behavior remains an integration concern                                   |
| Config/Docker         | Medium             | Config defaults tracked in docs/tests; deployment-time verification still required                                                             |

## Grand Total (After Prompts 9–10)

| Suite         | Files  | Methods  |
| ------------- | ------ | -------- |
| Backend unit  | 34     | ~195     |
| Backend API   | 9      | ~43      |
| Frontend unit | 23     | ~129     |
| **Total**     | **67** | **~370** |
