# Static Delivery Acceptance and Architecture Audit

Date: 2026-04-14
Scope root: `./` (workspace)
Mode: Static-only (no runtime execution)

## 1. Verdict

- Overall conclusion: **Partial Pass**

## 2. Scope and Static Verification Boundary

- Reviewed:
  - Prompt and acceptance docs: `prompt.md:1`, `docs/api-spec.md:1`, `docs/test-traceability.md:1`
  - Delivery docs and run/test instructions: `repo/README.md:1`, `repo/run_tests.sh:1`, `repo/.env.example:1`
  - Backend architecture/security/business modules: `repo/backend/src/main/java/**`
  - Backend config and schema migrations: `repo/backend/src/main/resources/application.yml:1`, `repo/backend/src/main/resources/db/migration/V001__initial_schema.sql:1`
  - Static test assets: `repo/backend/unit_tests/java/**`, `repo/backend/api_tests/java/**`, `repo/frontend/unit_tests/**`
- Not reviewed in depth:
  - Frontend visual/aesthetic quality and runtime behavior (non-front-end focus)
  - Runtime resilience under real load/network conditions
- Intentionally not executed:
  - No project startup, no Docker, no tests, no external service calls
- Manual verification required for:
  - Actual runtime security behavior in browser (cookie flags, CSRF exploitability)
  - Real scheduler timing/throughput under concurrent operations
  - End-to-end request signature behavior in production profile with real clients

## 3. Repository / Requirement Mapping Summary

- Prompt core goal: offline, compliance-grade event ops + accounting platform with strict security, auditable operations, and no external dependencies (`prompt.md:1`).
- Core flows mapped: local auth, registration/waitlist, check-in/passcode/device binding, notifications with retry+DND, imports with circuit breaker, finance allocation/revenue recognition, audit/export, backup retention.
- Main implementation areas:
  - Security/auth/signature/rate-limit: `repo/backend/src/main/java/com/eventops/security/**`
  - Domain and persistence: `repo/backend/src/main/java/com/eventops/domain/**`, `repo/backend/src/main/resources/db/migration/V001__initial_schema.sql:1`
  - Services/controllers/schedulers: `repo/backend/src/main/java/com/eventops/service/**`, `repo/backend/src/main/java/com/eventops/controller/**`, `repo/backend/src/main/java/com/eventops/scheduler/**`
  - Tests and traceability docs: `repo/backend/unit_tests/java/**`, `repo/backend/api_tests/java/**`, `docs/test-traceability.md:1`

## 4. Section-by-section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability

- Conclusion: **Partial Pass**
- Rationale:
  - Strong setup/test/config docs exist (`repo/README.md:1`, `repo/.env.example:1`, `repo/run_tests.sh:1`).
  - Static inconsistency exists between documented request-signature contract and filter behavior.
- Evidence:
  - Docs say signatures required for mutating requests: `docs/api-spec.md:29`
  - Filter is applied to authenticated GET as well (test proves): `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:61`, `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:77`
  - Filter skip logic is session-based rather than method-based: `repo/backend/src/main/java/com/eventops/security/signature/SignatureVerificationFilter.java:91`, `repo/backend/src/main/java/com/eventops/security/signature/SignatureVerificationFilter.java:94`
- Manual verification note:
  - Confirm production client contract for signatures on GET endpoints.

#### 4.1.2 Material deviation from Prompt

- Conclusion: **Partial Pass**
- Rationale:
  - Most core domains are implemented.
  - Material rule mismatch in check-in eligibility: status-blind registration check can admit non-active registrations.
- Evidence:
  - Check-in uses `existsByUserIdAndSessionId` only: `repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java:107`
  - Repository method is status-agnostic: `repo/backend/src/main/java/com/eventops/repository/registration/RegistrationRepository.java:12`
  - Registration lifecycle includes non-eligible statuses: `repo/backend/src/main/java/com/eventops/domain/registration/RegistrationStatus.java:8`, `repo/backend/src/main/java/com/eventops/domain/registration/RegistrationStatus.java:10`, `repo/backend/src/main/java/com/eventops/domain/registration/RegistrationStatus.java:11`

### 4.2 Delivery Completeness

#### 4.2.1 Core explicit requirements coverage

- Conclusion: **Partial Pass**
- Rationale:
  - Implementations exist for all major prompt areas (auth, RBAC, signatures, encryption, registration/waitlist, check-in/passcode, notifications, imports, finance, audit, backups).
  - Some compliance details are weakened by security and business-rule defects.
- Evidence:
  - Security roles and route guards: `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:83`-`87`
  - Rate limiting defaults: `repo/backend/src/main/resources/application.yml:62`
  - Login throttle defaults: `repo/backend/src/main/resources/application.yml:64`-`65`
  - Encryption at rest converter usage: `repo/backend/src/main/java/com/eventops/domain/user/User.java:36`, `repo/backend/src/main/java/com/eventops/domain/checkin/CheckInRecord.java:37`, `repo/backend/src/main/java/com/eventops/domain/checkin/DeviceBinding.java:27`
  - Passcode rotation and window defaults: `repo/backend/src/main/java/com/eventops/scheduler/PasscodeRotationScheduler.java:23`, `repo/backend/src/main/resources/application.yml:98`, `repo/backend/src/main/resources/application.yml:101`-`102`

#### 4.2.2 End-to-end deliverable (0 to 1)

- Conclusion: **Pass**
- Rationale:
  - Complete backend/frontend project structure, docs, docker compose, schema migrations, and tests are present.
- Evidence:
  - Monorepo structure and startup docs: `repo/README.md:1`
  - Compose manifests: `repo/docker-compose.yml:1`, `repo/docker-compose.dev.yml:1`
  - Backend migrations and controllers/services exist: `repo/backend/src/main/resources/db/migration/V001__initial_schema.sql:1`, `repo/backend/src/main/java/com/eventops/controller/**`
  - Test orchestration and suite commands: `repo/run_tests.sh:1`, `repo/README.md:191`

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and module decomposition

- Conclusion: **Pass**
- Rationale:
  - Clear modular separation by domain (`controller`, `service`, `repository`, `domain`, `security`, `scheduler`, `finance`, `audit`).
- Evidence:
  - Package layout in README: `repo/README.md:50`
  - Controller/service/repository split in backend tree: `repo/backend/src/main/java/com/eventops/controller/**`, `repo/backend/src/main/java/com/eventops/service/**`, `repo/backend/src/main/java/com/eventops/repository/**`

#### 4.3.2 Maintainability and extensibility

- Conclusion: **Partial Pass**
- Rationale:
  - Good extensible patterns exist (allocation engine, scheduler orchestration, DTOs).
  - Security config has contradictory CORS setup (wildcard origin with credentials) and comments indicating drift, increasing long-term risk.
- Evidence:
  - CORS wildcard + credentials: `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:153`, `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:156`
  - Separate WebConfig with restricted origins, indicating dual-policy ambiguity: `repo/backend/src/main/java/com/eventops/config/WebConfig.java:24`, `repo/backend/src/main/java/com/eventops/config/WebConfig.java:29`

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling, logging, validation, API design

- Conclusion: **Partial Pass**
- Rationale:
  - Positive: global exception handling, audit redaction/sanitization, structured responses.
  - Gaps: several endpoints accept raw maps without bean validation, reducing API contract rigor.
- Evidence:
  - Global exception handler: `repo/backend/src/main/java/com/eventops/common/exception/GlobalExceptionHandler.java:34`
  - Audit redaction/sanitization: `repo/backend/src/main/java/com/eventops/audit/logging/AuditService.java:237`, `repo/backend/src/main/java/com/eventops/audit/logging/LogSanitizer.java:21`
  - Raw map request bodies in finance/export/import endpoints: `repo/backend/src/main/java/com/eventops/controller/finance/FinanceController.java:118`, `repo/backend/src/main/java/com/eventops/controller/export/ExportController.java:49`, `repo/backend/src/main/java/com/eventops/controller/importing/ImportController.java:117`

#### 4.4.2 Product-level organization vs demo quality

- Conclusion: **Pass**
- Rationale:
  - Codebase and docs resemble a real service with operational concerns (retention, scheduler jobs, role matrix, migrations).
- Evidence:
  - Backup retention configuration and cleanup path: `repo/backend/src/main/resources/application.yml:82`, `repo/backend/src/main/java/com/eventops/service/backup/BackupService.java:170`
  - Import queue orchestration with priority: `repo/backend/src/main/java/com/eventops/scheduler/ImportOrchestrationScheduler.java:43`

### 4.5 Prompt Understanding and Requirement Fit

#### 4.5.1 Business goal/constraints fit

- Conclusion: **Partial Pass**
- Rationale:
  - Offline-first and in-app-only notification approach is represented.
  - Security posture is weakened by CORS/session/CSRF combination and by check-in eligibility rule gap.
- Evidence:
  - Offline/no external dependency intent documented: `repo/README.md:235`, `repo/README.md:238`
  - Session-based auth in controller and security config: `repo/backend/src/main/java/com/eventops/security/auth/AuthController.java:64`, `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:78`
  - CORS permissive config with credentials: `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:153`, `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:156`
  - Check-in registration gate is status-blind: `repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java:107`

### 4.6 Aesthetics (frontend-only / full-stack)

- Conclusion: **Not Applicable**
- Rationale:
  - This audit was explicitly scoped as non-front-end static architecture/testing review.

## 5. Issues / Suggestions (Severity-Rated)

### 5.1 High

1. **High** - Check-in authorization bypass for non-active registrations

- Conclusion: **Fail**
- Evidence:
  - Status-blind registration check in check-in flow: `repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java:107`
  - Status-agnostic repository method: `repo/backend/src/main/java/com/eventops/repository/registration/RegistrationRepository.java:12`
  - Non-eligible statuses exist (`WAITLISTED`, `CANCELLED`, `EXPIRED`): `repo/backend/src/main/java/com/eventops/domain/registration/RegistrationStatus.java:8`, `repo/backend/src/main/java/com/eventops/domain/registration/RegistrationStatus.java:10`, `repo/backend/src/main/java/com/eventops/domain/registration/RegistrationStatus.java:11`
- Impact:
  - Users not actively registered could potentially pass check-in eligibility if any historical registration row exists.
- Minimum actionable fix:
  - Replace boolean existence check with status-constrained validation (`CONFIRMED`/`PROMOTED` only) and add denial tests for waitlisted/cancelled/expired users.
- Minimal verification path:
  - Add/inspect tests asserting 422/409 for inactive statuses in `CheckInServiceTest` and `CheckInFlowIT`.

2. **High** - Broad credentialed CORS with session auth and CSRF disabled

- Conclusion: **Fail (Security Hardening Gap)**
- Evidence:
  - Stateful sessions enabled: `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:78`
  - CORS allows any origin pattern and credentials: `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:153`, `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:156`
  - CSRF disabled in same chain (static code path): `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:71`
- Impact:
  - Elevated cross-origin request risk in browser contexts; attack surface is inconsistent with compliance-grade least privilege.
- Minimum actionable fix:
  - Remove wildcard origin patterns, enforce explicit allowlist from config, and re-evaluate CSRF strategy for session-backed auth.
- Minimal verification path:
  - Manual browser security verification required (origin/cookie behavior and CSRF attempts).

### 5.2 Medium

3. **Medium** - Request-signature contract drift between docs and implementation

- Conclusion: **Partial Fail (Docs/Contract Mismatch)**
- Evidence:
  - API doc states mutating requests require signatures: `docs/api-spec.md:29`
  - Signature tests show authenticated GET is also enforced: `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:61`, `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:77`
  - Filter logic keys off session/path, not mutating methods: `repo/backend/src/main/java/com/eventops/security/signature/SignatureVerificationFilter.java:91`, `repo/backend/src/main/java/com/eventops/security/signature/SignatureVerificationFilter.java:94`
- Impact:
  - Client implementers can fail on signed GET expectations not documented.
- Minimum actionable fix:
  - Align either docs or filter policy and explicitly version the signature contract.

4. **Medium** - API tests do not provide meaningful end-to-end signature verification coverage

- Conclusion: **Partial Fail (Coverage Gap)**
- Evidence:
  - Dev profile disables signatures: `repo/backend/src/main/resources/application-dev.yml:25`
  - Dev compose activates dev profile: `repo/docker-compose.dev.yml:4`
  - Some API tests use test profile; others do not, creating mixed assumptions: `repo/backend/api_tests/java/com/eventops/RegistrationFlowIT.java:29`, `repo/backend/api_tests/java/com/eventops/NotificationRetryIT.java:21`
  - Existing signature tests are unit-level filter tests: `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:77`
- Impact:
  - Severe signature integration regressions could pass current API suite.
- Minimum actionable fix:
  - Add integration tests with signature enabled and real header generation/nonce replay scenarios.

5. **Medium** - Weakly typed/validated request payloads in key endpoints

- Conclusion: **Partial Fail**
- Evidence:
  - Raw `Map` request bodies used in finance/export/import endpoints: `repo/backend/src/main/java/com/eventops/controller/finance/FinanceController.java:118`, `repo/backend/src/main/java/com/eventops/controller/export/ExportController.java:49`, `repo/backend/src/main/java/com/eventops/controller/importing/ImportController.java:117`
- Impact:
  - Reduced schema validation quality and weaker contract stability.
- Minimum actionable fix:
  - Introduce DTOs with bean validation for those endpoints.

### 5.3 Low

6. **Low** - Security hardening defaults in env examples are weak for a compliance-oriented product

- Conclusion: **Partial Fail (Operational Hygiene)**
- Evidence:
  - Example defaults include weak DB/root passwords: `repo/.env.example:18`, `repo/.env.example:23`
- Impact:
  - Increases risk of insecure deployment if examples are copied without hardening.
- Minimum actionable fix:
  - Use non-trivial placeholders and explicit fail-fast guidance for production bootstrap.

## 6. Security Review Summary

- Authentication entry points: **Pass**
  - Evidence: `repo/backend/src/main/java/com/eventops/security/auth/AuthController.java:53`, `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:83`
  - Reasoning: dedicated auth controller, explicit permit-all only for login/register.

- Route-level authorization: **Partial Pass**
  - Evidence: role route matrix in `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:84`-`87`
  - Reasoning: broad role guards are present; overall posture weakened by CORS/session/CSRF combo.

- Object-level authorization: **Partial Pass**
  - Evidence:
    - Registration ownership checks: `repo/backend/src/main/java/com/eventops/service/registration/RegistrationService.java:115`, `repo/backend/src/main/java/com/eventops/service/registration/RegistrationService.java:168`
    - Export ownership check in download path: `repo/backend/src/main/java/com/eventops/service/audit/ExportService.java:456`
    - Check-in eligibility gap (status-blind): `repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java:107`

- Function-level authorization: **Partial Pass**
  - Evidence: method-level preauthorize exists in roster endpoint: `repo/backend/src/main/java/com/eventops/controller/registration/RegistrationController.java:141`
  - Reasoning: mostly route-level controls; method-level is not systematically applied.

- Tenant/user data isolation: **Cannot Confirm Statistically**
  - Evidence: single-tenant local-network design focus (`prompt.md:1`) and no explicit tenant model in domain schema (`repo/backend/src/main/resources/db/migration/V001__initial_schema.sql:1`).
  - Reasoning: likely single-tenant by design; explicit multi-tenant isolation is not modeled.

- Admin/internal/debug endpoint protection: **Pass**
  - Evidence: admin/audit route restrictions in security config (`repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:84`, `repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java:85`), backup admin namespace (`repo/backend/src/main/java/com/eventops/controller/backup/BackupController.java:27`).

## 7. Tests and Logging Review

- Unit tests: **Pass (breadth), Partial Pass (risk depth)**
  - Evidence: extensive backend unit suite under `repo/backend/unit_tests/java/**`; key security/business tests include `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:1`, `repo/backend/unit_tests/java/com/eventops/service/CheckInServiceTest.java:1`.
  - Note: missing negative tests for check-in with non-active registration statuses.

- API/integration tests: **Partial Pass**
  - Evidence: multiple IT classes under `repo/backend/api_tests/java/**` and test profile usage in some suites (`repo/backend/api_tests/java/com/eventops/RegistrationFlowIT.java:29`).
  - Gap: no strong E2E signature-enabled coverage.

- Logging categories / observability: **Pass**
  - Evidence: structured logging levels in config (`repo/backend/src/main/resources/application.yml:43`) and audited operation logging in `repo/backend/src/main/java/com/eventops/audit/logging/AuditService.java:188`.

- Sensitive-data leakage risk in logs / responses: **Partial Pass**
  - Evidence: sanitizer/redaction exists (`repo/backend/src/main/java/com/eventops/audit/logging/LogSanitizer.java:21`, `repo/backend/src/main/java/com/eventops/audit/logging/AuditService.java:237`).
  - Residual risk: manual verification needed for non-audit log paths and operational logs under failure conditions.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

- Unit tests exist:
  - Backend unit tests: `repo/backend/unit_tests/java/**`
  - Frontend unit tests: `repo/frontend/unit_tests/**`
- API/integration tests exist:
  - `repo/backend/api_tests/java/**`
- Frameworks and entry points:
  - Backend: JUnit/Surefire/Failsafe configured in `repo/backend/pom.xml:120`, `repo/backend/pom.xml:132`
  - Frontend: Vitest in `repo/frontend/package.json:10`
- Test commands documented:
  - `repo/README.md:191`-`196`

### 8.2 Coverage Mapping Table

| Requirement / Risk Point                 | Mapped Test Case(s)                                                                                                                                        | Key Assertion / Fixture / Mock               | Coverage Assessment | Gap                                                               | Minimum Test Addition                                          |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------- | ------------------- | ----------------------------------------------------------------- | -------------------------------------------------------------- |
| Local auth + lockout                     | `repo/backend/unit_tests/java/com/eventops/security/AuthServiceTest.java:1`, `repo/backend/unit_tests/java/com/eventops/security/LoginThrottleTest.java:1` | lockout/attempt behavior                     | basically covered   | E2E profile variance                                              | add IT for lockout response fields                             |
| Route-level RBAC                         | `repo/backend/api_tests/java/com/eventops/UnauthorizedAccessIT.java:1`, `repo/backend/api_tests/java/com/eventops/ImportCircuitBreakerIT.java:1`           | 401/403 on protected routes                  | sufficient          | none major                                                        | extend matrix for export/download object scope                 |
| Request signature verification           | `repo/backend/unit_tests/java/com/eventops/security/signature/SignatureVerificationFilterTest.java:77`                                                     | header-required and valid-signature path     | insufficient        | no E2E signed requests in API tests                               | add production-profile IT for signed POST/GET and replay nonce |
| Registration duplicate blocking          | `repo/backend/api_tests/java/com/eventops/RegistrationFlowIT.java:66`                                                                                      | expects `DUPLICATE_REGISTRATION` conflict    | basically covered   | edge cases around cancellation+re-register                        | add IT for cancel/re-register transitions                      |
| Waitlist-on-capacity                     | `repo/backend/api_tests/java/com/eventops/RegistrationFlowIT.java:84`                                                                                      | expects WAITLISTED and position              | basically covered   | promotion cutoff IT depth                                         | add scheduler+cutoff IT                                        |
| Check-in window + passcode               | `repo/backend/unit_tests/java/com/eventops/service/CheckInServiceTest.java:97`, `repo/backend/api_tests/java/com/eventops/CheckInFlowIT.java:92`           | window-closed and passcode endpoint behavior | basically covered   | status-based registration eligibility missing                     | add tests for WAITLISTED/CANCELLED/EXPIRED denied check-in     |
| Notification retry/backoff/idempotency   | `repo/backend/unit_tests/java/com/eventops/service/NotificationServiceTest.java:1`                                                                         | `computeBackoff`, idempotency key checks     | basically covered   | API-level retry orchestration depth                               | add scheduler-driven IT with timing assertions                 |
| Import cap/priority/circuit/timeout      | `repo/backend/unit_tests/java/com/eventops/service/ImportServiceTest.java:1`, `repo/backend/api_tests/java/com/eventops/ImportCircuitBreakerIT.java:1`     | concurrency, circuit status, role gates      | basically covered   | large dataset/checkpoint resume IT                                | add IT with seeded files/checkpoint resume                     |
| Finance allocation/rule versions/posting | `repo/backend/unit_tests/java/com/eventops/service/FinanceServiceTest.java:1`, `repo/backend/api_tests/java/com/eventops/FinanceFlowIT.java:1`             | posting and role access assertions           | basically covered   | object-level authorization on posting assets                      | add per-user scope assertions for finance exports/download     |
| Export restrictions + download auth      | `repo/backend/api_tests/java/com/eventops/ExportRestrictionIT.java:1`                                                                                      | policy endpoint role checks                  | insufficient        | weak assertions on owner/non-owner download with real export jobs | add IT for cross-user download denial and role permutations    |
| Backup retention lifecycle               | `repo/backend/unit_tests/java/com/eventops/service/BackupServiceTest.java:1`, `repo/backend/api_tests/java/com/eventops/BackupRetentionIT.java:1`          | expired status and retention endpoints       | basically covered   | filesystem failure paths in IT                                    | add IT for failed dump/cleanup path                            |

### 8.3 Security Coverage Audit

- Authentication: **Basically covered**
  - Unit + API tests exist for login/auth-required paths (`repo/backend/api_tests/java/com/eventops/UnauthorizedAccessIT.java:1`).
- Route authorization: **Covered**
  - Multiple role/forbidden tests across domains (`repo/backend/api_tests/java/com/eventops/ImportCircuitBreakerIT.java:1`, `repo/backend/api_tests/java/com/eventops/FinanceFlowIT.java:1`).
- Object-level authorization: **Insufficient**
  - Some ownership checks are tested indirectly, but critical check-in status-scope scenario is not covered.
- Tenant/data isolation: **Cannot confirm**
  - No tenant model/tests; appears single-tenant by design.
- Admin/internal protection: **Basically covered**
  - Route-level checks present; deeper abuse-case tests remain limited.

### 8.4 Final Coverage Judgment

- **Partial Pass**
- Covered major risks:
  - Core RBAC route protection, auth basics, key service logic units.
- Uncovered/insufficient risks that could hide severe defects while tests still pass:
  - Signature verification end-to-end behavior under production settings.
  - Check-in eligibility for non-active registration statuses.
  - Object-level export/download abuse permutations.

## 9. Final Notes

- This report is static-only and evidence-based; no runtime success is claimed.
- Primary root-cause risks are security hardening gaps and one high-impact business-rule authorization defect in check-in eligibility.
- Manual verification is required for browser-origin security behavior, production signature enforcement, and scheduler behavior under real operational timing/load.
