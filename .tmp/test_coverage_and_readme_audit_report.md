# 1) Test Coverage Audit

## Scope, Method, and Project Type

- Audit mode: static inspection only.
- No execution performed (no tests, no scripts, no containers, no builds).
- Project type from README: fullstack.
- Evidence: repo/README.md (line 3).

## Backend Endpoint Inventory

Resolved endpoint = METHOD + fully resolved PATH.

1. POST /api/auth/login
2. POST /api/auth/register
3. GET /api/auth/me
4. POST /api/auth/refresh
5. POST /api/auth/logout
6. GET /api/events
7. GET /api/events/{id}
8. GET /api/events/{id}/availability
9. POST /api/registrations
10. GET /api/registrations
11. GET /api/registrations/{id}
12. DELETE /api/registrations/{id}
13. GET /api/registrations/waitlist
14. GET /api/registrations/session/{sessionId}
15. POST /api/checkin/sessions/{sessionId}
16. GET /api/checkin/sessions/{sessionId}/passcode
17. GET /api/checkin/sessions/{sessionId}/roster
18. GET /api/checkin/sessions/{sessionId}/conflicts
19. GET /api/notifications
20. GET /api/notifications/unread-count
21. PATCH /api/notifications/{id}/read
22. GET /api/notifications/subscriptions
23. PUT /api/notifications/subscriptions
24. GET /api/notifications/dnd
25. PUT /api/notifications/dnd
26. GET /api/audit/logs
27. GET /api/audit/logs/{id}
28. POST /api/audit/logs/export
29. GET /api/audit/exports/{id}/download
30. POST /api/attachments/sessions
31. PUT /api/attachments/sessions/{uploadId}/chunks/{chunkIndex}
32. GET /api/attachments/sessions/{uploadId}
33. POST /api/attachments/sessions/{uploadId}/complete
34. GET /api/imports/sources
35. POST /api/imports/sources
36. PUT /api/imports/sources/{id}
37. GET /api/imports/jobs
38. GET /api/imports/jobs/{id}
39. POST /api/imports/jobs/trigger
40. GET /api/imports/circuit-breaker
41. GET /api/finance/periods
42. POST /api/finance/periods
43. PUT /api/finance/periods/{id}/close
44. GET /api/finance/accounts
45. POST /api/finance/accounts
46. GET /api/finance/cost-centers
47. POST /api/finance/cost-centers
48. GET /api/finance/rules
49. POST /api/finance/rules
50. PUT /api/finance/rules/{id}
51. POST /api/finance/postings
52. GET /api/finance/postings
53. GET /api/finance/postings/{id}/line-items
54. POST /api/finance/postings/{id}/reverse
55. GET /api/admin/backups
56. GET /api/admin/backups/{id}
57. POST /api/admin/backups/trigger
58. GET /api/admin/backups/retention
59. POST /api/exports/rosters
60. POST /api/exports/finance-reports
61. GET /api/exports/{id}/download
62. GET /api/exports/policies
63. PUT /api/exports/policies
64. GET /api/admin/users
65. POST /api/admin/users
66. PUT /api/admin/users/{id}
67. GET /api/admin/security/settings
68. PUT /api/admin/security/settings

Total endpoints: 68.

## API Test Mapping Table

| Endpoint                                                     | Covered | Test type         | Test files                                                                         | Evidence                                                                             |
| ------------------------------------------------------------ | ------- | ----------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| POST /api/auth/login                                         | yes     | HTTP with mocking | AuthControllerIT, AuthFlowIT, UnauthorizedAccessIT, RealHttpIT                     | AuthControllerIT.login_success_returnsUserAndSignatureHeader                         |
| POST /api/auth/register                                      | yes     | HTTP with mocking | AuthControllerIT, AuthFlowIT, RealHttpIT                                           | AuthControllerIT.register_success_returnsCreatedUser                                 |
| GET /api/auth/me                                             | yes     | HTTP with mocking | AuthControllerIT, AuthFlowIT, SignatureFlowIT, RealHttpIT                          | AuthControllerIT.me_withAuthenticatedUser_returnsCurrentUser                         |
| POST /api/auth/refresh                                       | yes     | HTTP with mocking | AuthControllerIT, AuthFlowIT, RealHttpIT                                           | AuthControllerIT.refresh_withAuthenticatedUser_returnsRefreshedSession               |
| POST /api/auth/logout                                        | yes     | HTTP with mocking | AuthControllerIT, AuthFlowIT, RealHttpIT                                           | AuthControllerIT.logout_returnsSuccess                                               |
| GET /api/events                                              | yes     | HTTP with mocking | EventControllerIT, RegistrationFlowIT, RealHttpIT                                  | EventControllerIT.listSessions_asAuthenticatedUser_returnsSessionPage                |
| GET /api/events/{id}                                         | yes     | HTTP with mocking | EventControllerIT, RealHttpIT                                                      | EventControllerIT.getSession_asAuthenticatedUser_returnsSession                      |
| GET /api/events/{id}/availability                            | yes     | HTTP with mocking | EventControllerIT, RealHttpIT                                                      | EventControllerIT.getAvailability_asAuthenticatedUser_returnsRemainingSeats          |
| POST /api/registrations                                      | yes     | HTTP with mocking | RegistrationNotificationControllerIT, RegistrationFlowIT, RealHttpIT               | RegistrationNotificationControllerIT.register_confirmed_returnsConfirmationMessage   |
| GET /api/registrations                                       | yes     | HTTP with mocking | RegistrationNotificationControllerIT, RealHttpIT                                   | RegistrationNotificationControllerIT.listRegistrations_returnsUserRegistrations      |
| GET /api/registrations/{id}                                  | yes     | HTTP with mocking | RegistrationNotificationControllerIT                                               | RegistrationNotificationControllerIT.getRegistration_returnsSingleRegistration       |
| DELETE /api/registrations/{id}                               | yes     | HTTP with mocking | RegistrationNotificationControllerIT                                               | RegistrationNotificationControllerIT.cancelRegistration_returnsCancelledRegistration |
| GET /api/registrations/waitlist                              | yes     | HTTP with mocking | RegistrationNotificationControllerIT, RealHttpIT                                   | RegistrationNotificationControllerIT.waitlist_returnsWaitlistEntries                 |
| GET /api/registrations/session/{sessionId}                   | yes     | HTTP with mocking | RegistrationNotificationControllerIT                                               | RegistrationNotificationControllerIT.sessionRoster_returnsRosterEntries              |
| POST /api/checkin/sessions/{sessionId}                       | yes     | HTTP with mocking | CheckInFlowIT, BackupCheckInControllerIT                                           | CheckInFlowIT.checkIn_ineligibleStatus_returns422                                    |
| GET /api/checkin/sessions/{sessionId}/passcode               | yes     | HTTP with mocking | CheckInFlowIT, BackupCheckInControllerIT, UnauthorizedAccessIT, RealHttpIT         | CheckInFlowIT.staff_canReachPasscodeEndpoint                                         |
| GET /api/checkin/sessions/{sessionId}/roster                 | yes     | HTTP with mocking | BackupCheckInControllerIT                                                          | BackupCheckInControllerIT.getRoster_returnsRosterEntries                             |
| GET /api/checkin/sessions/{sessionId}/conflicts              | yes     | HTTP with mocking | BackupCheckInControllerIT                                                          | BackupCheckInControllerIT.getConflicts_returnsConflictEntries                        |
| GET /api/notifications                                       | yes     | HTTP with mocking | RegistrationNotificationControllerIT, NotificationRetryIT, RealHttpIT              | NotificationRetryIT.notifications_list_returnsDelivered                              |
| GET /api/notifications/unread-count                          | yes     | HTTP with mocking | RegistrationNotificationControllerIT, NotificationRetryIT, RealHttpIT              | NotificationRetryIT.unreadCount_returnsZeroInitially                                 |
| PATCH /api/notifications/{id}/read                           | yes     | HTTP with mocking | RegistrationNotificationControllerIT, NotificationRetryIT, RealHttpIT              | RegistrationNotificationControllerIT.markAsRead_returnsSuccessMessage                |
| GET /api/notifications/subscriptions                         | yes     | HTTP with mocking | RegistrationNotificationControllerIT, RealHttpIT                                   | RegistrationNotificationControllerIT.getSubscriptions_returnsSubscriptions           |
| PUT /api/notifications/subscriptions                         | yes     | HTTP with mocking | RegistrationNotificationControllerIT                                               | RegistrationNotificationControllerIT.updateSubscriptions_returnsSuccessMessage       |
| GET /api/notifications/dnd                                   | yes     | HTTP with mocking | RegistrationNotificationControllerIT, NotificationRetryIT, RealHttpIT              | RegistrationNotificationControllerIT.getDndSettings_returnsCurrentWindow             |
| PUT /api/notifications/dnd                                   | yes     | HTTP with mocking | RegistrationNotificationControllerIT, NotificationRetryIT                          | RegistrationNotificationControllerIT.updateDndSettings_returnsUpdatedWindow          |
| GET /api/audit/logs                                          | yes     | HTTP with mocking | AuditControllerIT, ExportRestrictionIT, UnauthorizedAccessIT, RealHttpIT           | AuditControllerIT.searchLogs_asSystemAdmin_returnsPagedAuditEvents                   |
| GET /api/audit/logs/{id}                                     | yes     | HTTP with mocking | AuditControllerIT                                                                  | AuditControllerIT.getLog_asSystemAdmin_returnsAuditLogDetails                        |
| POST /api/audit/logs/export                                  | yes     | HTTP with mocking | AuditControllerIT                                                                  | AuditControllerIT.exportLogs_asSystemAdmin_returnsCreatedExportJob                   |
| GET /api/audit/exports/{id}/download                         | yes     | HTTP with mocking | AuditControllerIT                                                                  | AuditControllerIT.downloadExport_asSystemAdmin_returnsFileBytes                      |
| POST /api/attachments/sessions                               | yes     | HTTP with mocking | AttachmentControllerIT, RealHttpIT                                                 | AttachmentControllerIT.createSession_asAuthenticatedUser_returnsCreatedSession       |
| PUT /api/attachments/sessions/{uploadId}/chunks/{chunkIndex} | yes     | HTTP with mocking | AttachmentControllerIT                                                             | AttachmentControllerIT.uploadChunk_andGetStatus_worksEndToEnd                        |
| GET /api/attachments/sessions/{uploadId}                     | yes     | HTTP with mocking | AttachmentControllerIT                                                             | AttachmentControllerIT.getStatus_asSystemAdmin_canViewAnySession                     |
| POST /api/attachments/sessions/{uploadId}/complete           | yes     | HTTP with mocking | AttachmentControllerIT                                                             | AttachmentControllerIT.completeUpload_allChunksUploaded_returnsCompletedStatus       |
| GET /api/imports/sources                                     | yes     | HTTP with mocking | ImportExportControllerIT, ImportCircuitBreakerIT, UnauthorizedAccessIT, RealHttpIT | ImportExportControllerIT.getSources_returnsImportSources                             |
| POST /api/imports/sources                                    | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.createSource_returnsCreatedSource                           |
| PUT /api/imports/sources/{id}                                | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.updateSource_returnsUpdatedSource                           |
| GET /api/imports/jobs                                        | yes     | HTTP with mocking | ImportExportControllerIT, RealHttpIT                                               | ImportExportControllerIT.getJobs_returnsPagedJobs                                    |
| GET /api/imports/jobs/{id}                                   | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.getJob_returnsSingleJob                                     |
| POST /api/imports/jobs/trigger                               | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.triggerImport_returnsCreatedJob                             |
| GET /api/imports/circuit-breaker                             | yes     | HTTP with mocking | ImportExportControllerIT, ImportCircuitBreakerIT, RealHttpIT                       | ImportExportControllerIT.getCircuitBreakerStatus_returnsStatusMap                    |
| GET /api/finance/periods                                     | yes     | HTTP with mocking | FinanceControllerIT, RealHttpIT                                                    | FinanceControllerIT.listPeriods_returnsPeriods                                       |
| POST /api/finance/periods                                    | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.createPeriod_returnsCreatedPeriod                                |
| PUT /api/finance/periods/{id}/close                          | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.closePeriod_returnsClosedPeriod                                  |
| GET /api/finance/accounts                                    | yes     | HTTP with mocking | FinanceControllerIT, FinanceFlowIT, UnauthorizedAccessIT, RealHttpIT               | FinanceControllerIT.listAccounts_returnsAccounts                                     |
| POST /api/finance/accounts                                   | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.createAccount_returnsCreatedAccount                              |
| GET /api/finance/cost-centers                                | yes     | HTTP with mocking | FinanceControllerIT, RealHttpIT                                                    | FinanceControllerIT.listCostCenters_returnsCenters                                   |
| POST /api/finance/cost-centers                               | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.createCostCenter_returnsCreatedCenter                            |
| GET /api/finance/rules                                       | yes     | HTTP with mocking | FinanceControllerIT, RealHttpIT                                                    | FinanceControllerIT.listRules_returnsRules                                           |
| POST /api/finance/rules                                      | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.createRule_returnsCreatedRule                                    |
| PUT /api/finance/rules/{id}                                  | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.updateRule_returnsUpdatedRule                                    |
| POST /api/finance/postings                                   | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.executePosting_returnsCreatedPosting                             |
| GET /api/finance/postings                                    | yes     | HTTP with mocking | FinanceControllerIT, RealHttpIT                                                    | FinanceControllerIT.listPostings_returnsPostings                                     |
| GET /api/finance/postings/{id}/line-items                    | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.getPostingLineItems_returnsLineItems                             |
| POST /api/finance/postings/{id}/reverse                      | yes     | HTTP with mocking | FinanceControllerIT                                                                | FinanceControllerIT.reversePosting_returnsReversedPosting                            |
| GET /api/admin/backups                                       | yes     | HTTP with mocking | BackupCheckInControllerIT, BackupRetentionIT, RealHttpIT                           | BackupCheckInControllerIT.listBackups_returnsJobs                                    |
| GET /api/admin/backups/{id}                                  | yes     | HTTP with mocking | BackupCheckInControllerIT                                                          | BackupCheckInControllerIT.getBackup_returnsJob                                       |
| POST /api/admin/backups/trigger                              | yes     | HTTP with mocking | BackupCheckInControllerIT                                                          | BackupCheckInControllerIT.triggerBackup_returnsCreatedBackup                         |
| GET /api/admin/backups/retention                             | yes     | HTTP with mocking | BackupCheckInControllerIT, BackupRetentionIT, RealHttpIT                           | BackupCheckInControllerIT.getRetentionStatus_returnsAggregatedCounts                 |
| POST /api/exports/rosters                                    | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.generateRosterExport_returnsCreatedJob                      |
| POST /api/exports/finance-reports                            | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.generateFinanceReport_returnsCreatedJob                     |
| GET /api/exports/{id}/download                               | yes     | HTTP with mocking | ExportRestrictionIT                                                                | ExportRestrictionIT.downloadExport_requiresAuthenticatedRole                         |
| GET /api/exports/policies                                    | yes     | HTTP with mocking | ImportExportControllerIT, ExportRestrictionIT, UnauthorizedAccessIT, RealHttpIT    | ImportExportControllerIT.getExportPolicies_returnsPolicies                           |
| PUT /api/exports/policies                                    | yes     | HTTP with mocking | ImportExportControllerIT                                                           | ImportExportControllerIT.updatePolicy_returnsUpdatedPolicy                           |
| GET /api/admin/users                                         | yes     | HTTP with mocking | AdminControllerIT, UnauthorizedAccessIT, RealHttpIT                                | AdminControllerIT.listUsers_asSystemAdmin_returnsPagedUsers                          |
| POST /api/admin/users                                        | yes     | HTTP with mocking | AdminControllerIT                                                                  | AdminControllerIT.createUser_asSystemAdmin_returnsCreatedUser                        |
| PUT /api/admin/users/{id}                                    | yes     | HTTP with mocking | AdminControllerIT                                                                  | AdminControllerIT.updateUser_asSystemAdmin_returnsUpdatedUser                        |
| GET /api/admin/security/settings                             | yes     | HTTP with mocking | AdminControllerIT, RealHttpIT                                                      | AdminControllerIT.getSecuritySettings_asSystemAdmin_returnsSettings                  |
| PUT /api/admin/security/settings                             | yes     | HTTP with mocking | AdminControllerIT                                                                  | AdminControllerIT.updateSecuritySettings_asSystemAdmin_returnsUpdatedSettings        |

## API Test Classification

### 1. True no-mock HTTP

- None (strict criteria).
- Reason: `RealHttpIT` uses real TCP (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) but introduces a test-only `SecurityFilterChain` override via `@TestConfiguration`, so execution path is altered.
- Evidence: repo/backend/api_tests/java/com/eventops/RealHttpIT.java.

### 2. HTTP with mocking

- All MockMvc API tests.
- Reasons:
  - simulated transport (`MockMvc` + `@AutoConfigureMockMvc`)
  - principal injection helper (`TestSecurity.user(...)`) bypasses real auth flow in many tests.
- Evidence examples:
  - repo/backend/api_tests/java/com/eventops/AuthControllerIT.java
  - repo/backend/api_tests/java/com/eventops/AdminControllerIT.java
  - repo/backend/api_tests/java/com/eventops/ImportExportControllerIT.java
  - repo/backend/api_tests/java/com/eventops/TestSecurity.java

### 3. Non-HTTP (unit/integration without HTTP)

- Backend unit tests: repo/backend/unit_tests/java/\*\*
- Frontend unit tests: repo/frontend/unit_tests/\*\*

## Mock Detection (Strict)

- Mock transport layer: `MockMvc` in API tests.
  - Where: many files under repo/backend/api_tests/java/com/eventops/\*IT.java.
- DI/security override in Real HTTP tests.
  - What: custom `SecurityFilterChain` bean in test config.
  - Where: repo/backend/api_tests/java/com/eventops/RealHttpIT.java.
- Security context injection bypass.
  - What: `TestSecurity.user(...)` request post-processor.
  - Where: repo/backend/api_tests/java/com/eventops/TestSecurity.java; used across most MockMvc tests.

## Coverage Summary

- Total endpoints: 68
- Endpoints with HTTP tests: 68
- Endpoints with TRUE no-mock tests: 0
- HTTP coverage: 100.0%
- True API coverage: 0.0%

## Unit Test Summary

### Backend unit tests

- Present: yes.
- Service coverage: broad (e.g., `RegistrationServiceTest`, `FinanceServiceTest`, `CheckInServiceTest`, `ImportServiceTest`, `NotificationServiceTest`, `BackupServiceTest`, `AdminServiceTest`).
- Repository coverage: limited (3 direct repository tests found).
  - `UserRepositoryTest`, `RegistrationRepositoryTest`, `EventSessionRepositoryTest`.
- Auth/guards/middleware coverage: present.
  - e.g., `AuthServiceTest`, `SignatureVerificationFilterTest`, `RbacTest`, `RateLimitTest`, `PermissionEvaluatorTest`.
- Important backend modules not unit-tested directly:
  - Controllers (no controller unit tests found).
  - Most repository interfaces (24 repositories in src; 3 direct repository tests).

### Frontend unit tests (strict requirement)

- Frontend unit tests: PRESENT.
- Why present (all strict checks satisfied):
  - identifiable `*.test.js` files exist in repo/frontend/unit_tests/\*\*
  - Vitest tooling evident (`describe/it` imports from `vitest`; package scripts)
  - component/view tests use `@vue/test-utils` (`mount`)
  - tests import actual frontend modules/components (e.g., `AccountView.vue`, `SubscriptionSettingsView.vue`, `MainLayout.vue`)
- Covered frontend areas:
  - Views: extensive (19 view test files; full view set observed)
  - Components: common/layout/offline components
  - Stores: all 9 stores have matching tests
  - API wrapper modules and utilities
- Important frontend modules not tested directly:
  - No obvious high-value gaps found in `src/views`, `src/components`, or `src/stores` by filename mapping.

### Cross-layer observation

- Backend and frontend unit/API test presence is broad.
- Missing real FE->BE end-to-end tests (browser/UI driving real backend over real network) remains a cross-layer gap for a fullstack project.

## API Observability Check

- Strength: generally good.
- Most API tests show method/path, request payload, and response assertions (`status`, `jsonPath`, headers).
- Evidence examples:
  - repo/backend/api_tests/java/com/eventops/AuthControllerIT.java
  - repo/backend/api_tests/java/com/eventops/ImportExportControllerIT.java
  - repo/backend/api_tests/java/com/eventops/RegistrationNotificationControllerIT.java

## Tests Check

- `run_tests.sh` supports Docker-contained path: PASS.
  - Evidence: docker compose test stack usage in repo/run_tests.sh (line 148).
- `run_tests.sh` also supports host mode with local toolchain and package installation: FLAG.
  - Evidence: repo/run_tests.sh lines 12-16, 20, 78, 106, 109.

## Test Coverage Score (0-100)

- Score: 72/100

## Score Rationale

- - endpoint-level HTTP coverage is complete (68/68).
- - backend and frontend unit tests are extensive.
- - assertion depth is mostly meaningful (status/body/header checks).
- - strict true no-mock API coverage is 0%.
- - heavy use of MockMvc + injected principal helper reduces real-path confidence.
- - no true FE<->BE end-to-end coverage for a fullstack system.

## Key Gaps

1. Critical: true no-mock API coverage is absent under strict criteria.
2. High: test auth flow often bypassed with `TestSecurity.user(...)`.
3. High: fullstack real browser-to-backend E2E suite is missing.
4. Medium: repository-level tests cover only a small subset of repository interfaces.

## Confidence and Assumptions

- Confidence: high for endpoint/test mapping and README gate checks.
- Assumptions:
  - Endpoint inventory limited to controllers discovered in backend src main Java.
  - Coverage judged from static source evidence only (no runtime verification).

### Test Coverage Final Verdict

- PARTIAL PASS (high breadth, but fails strict true no-mock expectation).

---

# 2) README Audit

## README Location Check

- Required file exists: repo/README.md.

## Hard Gate Evaluation

### Formatting

- PASS.
- Markdown structure is clear and segmented (Overview, Quick Start, Verification, Tests, etc.).

### Startup Instructions (Backend/Fullstack)

- PASS.
- `docker compose up --build` present.
- `docker-compose up --build` variant also present.
- Evidence: repo/README.md lines 105-106.

### Access Method

- PASS.
- Frontend URL+port and backend API URL+port documented.
- Evidence: repo/README.md lines 112-113.

### Verification Method

- PASS.
- API verification via curl and UI verification flow provided.
- Evidence: repo/README.md line 144 and section `Web UI Verification`.

### Environment Rules (No runtime installs/manual DB setup)

- PASS (README content).
- README does not instruct `npm install`, `pip install`, `apt-get`, or manual DB provisioning.

### Demo Credentials (auth exists)

- PASS.
- Credentials and all roles provided.
- Evidence: repo/README.md section `Demo Credentials`.

## Engineering Quality

- Tech stack clarity: strong.
- Architecture/repo structure explanation: strong.
- Testing instructions: present and Docker-first.
- Security/roles and workflows: documented.
- Presentation quality: strong.

## High Priority Issues

- None.

## Medium Priority Issues

- README claims strong API-test realism, but strict no-mock criteria are not met in test suite design.
  - This is a test-suite quality issue reflected by README positioning, not a hard-gate format failure.

## Low Priority Issues

- Optional dev/profile variants are documented; could be streamlined for stricter acceptance-path brevity.

## Hard Gate Failures

- None.

## README Verdict

- PASS.

---

# Final Combined Verdicts

- Test Coverage Audit: PARTIAL PASS
- README Audit: PASS
