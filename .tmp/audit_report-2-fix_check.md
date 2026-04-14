# Fix Check Report (Static Recheck - Rerun)

## 1. Verdict

- Overall conclusion: Fixed
- Summary: 7 issues fixed, 0 issues not fixed.

## 2. Static Verification Boundary

- This was a static-only recheck.
- I did not run the project, Docker, services, or tests.
- Conclusions are based on current source, docs, and configuration files only.

## 3. Fix Check Results

### 3.1 Issue: ImportController compile-break risk (missing Map import)

- Previous status: Blocker / Fail
- Current status: Fixed
- Evidence of fix:
  - Map import is present: [repo/backend/src/main/java/com/eventops/controller/importing/ImportController.java](repo/backend/src/main/java/com/eventops/controller/importing/ImportController.java#L20)
  - Controller still uses Map in response type: [repo/backend/src/main/java/com/eventops/controller/importing/ImportController.java](repo/backend/src/main/java/com/eventops/controller/importing/ImportController.java#L130)

### 3.2 Issue: Edge transport not satisfying HTTPS requirement

- Previous status: High / Fail
- Current status: Fixed
- Evidence of fix:
  - HTTP requests are redirected to HTTPS: [repo/frontend/nginx.conf](repo/frontend/nginx.conf#L4)
  - Frontend now serves TLS on 443 with cert/key: [repo/frontend/nginx.conf](repo/frontend/nginx.conf#L8)
  - Frontend container now exposes and maps 443: [repo/frontend/Dockerfile](repo/frontend/Dockerfile#L20), [repo/docker-compose.yml](repo/docker-compose.yml#L83)
  - Quick-start docs now point to HTTPS frontend URL: [repo/README.md](repo/README.md#L110)

### 3.3 Issue: Offline replay did not show clear conflict prompts

- Previous status: High / Fail
- Current status: Fixed
- Evidence of fix:
  - Replay now captures 409 conflict code/message and stores it: [repo/frontend/src/stores/offline.js](repo/frontend/src/stores/offline.js#L60), [repo/frontend/src/stores/offline.js](repo/frontend/src/stores/offline.js#L65)
  - Conflicts tracked in dedicated state: [repo/frontend/src/stores/offline.js](repo/frontend/src/stores/offline.js#L12)
  - UI now renders sync conflict prompt and dismiss action: [repo/frontend/src/components/offline/PendingActionsBar.vue](repo/frontend/src/components/offline/PendingActionsBar.vue#L11), [repo/frontend/src/components/offline/PendingActionsBar.vue](repo/frontend/src/components/offline/PendingActionsBar.vue#L19)

### 3.4 Issue: CSRF disabled with credentialed session-cookie model

- Previous status: High / Partial Fail
- Current status: Fixed
- Evidence of fix:
  - CSRF is enabled with cookie token repository and request handler: [repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java](repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java#L87), [repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java](repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java#L88), [repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java](repo/backend/src/main/java/com/eventops/security/config/SecurityConfig.java#L89)
  - Session cookie hardening is configured (`same-site: strict`, `http-only: true`): [repo/backend/src/main/resources/application.yml](repo/backend/src/main/resources/application.yml#L47), [repo/backend/src/main/resources/application.yml](repo/backend/src/main/resources/application.yml#L48)
  - Auth integration tests now send CSRF tokens on state-changing auth calls: [repo/backend/api_tests/java/com/eventops/AuthFlowIT.java](repo/backend/api_tests/java/com/eventops/AuthFlowIT.java#L27), [repo/backend/api_tests/java/com/eventops/AuthFlowIT.java](repo/backend/api_tests/java/com/eventops/AuthFlowIT.java#L33), [repo/backend/api_tests/java/com/eventops/AuthFlowIT.java](repo/backend/api_tests/java/com/eventops/AuthFlowIT.java#L47)
  - Client continues credentialed requests (consistent with session model): [repo/frontend/src/api/client.js](repo/frontend/src/api/client.js#L21)

### 3.5 Issue: Check-in anti-proxy UI/API contract mismatch

- Previous status: Medium / Partial Fail
- Current status: Fixed
- Evidence of fix:
  - Check-in response now includes conflictType and detectedAt: [repo/backend/src/main/java/com/eventops/common/dto/checkin/CheckInResponse.java](repo/backend/src/main/java/com/eventops/common/dto/checkin/CheckInResponse.java#L12)
  - Check-in response now includes deviceToken field: [repo/backend/src/main/java/com/eventops/common/dto/checkin/CheckInResponse.java](repo/backend/src/main/java/com/eventops/common/dto/checkin/CheckInResponse.java#L14)
  - Service maps conflictType, detectedAt, and deviceToken into response: [repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java](repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java#L281), [repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java](repo/backend/src/main/java/com/eventops/service/checkin/CheckInService.java#L283)
  - Frontend check-in views consume those fields: [repo/frontend/src/views/checkin/CheckInView.vue](repo/frontend/src/views/checkin/CheckInView.vue#L181), [repo/frontend/src/views/checkin/DeviceBindingView.vue](repo/frontend/src/views/checkin/DeviceBindingView.vue#L105)
- Note:
  - Current mapped deviceToken value is derived from stored token hash, not raw token.

### 3.6 Issue: Test traceability document stale vs actual API tests

- Previous status: Medium / Fail
- Current status: Fixed
- Evidence of fix:
  - Traceability now states end-to-end signature integration coverage and references SignatureFlowIT: [docs/test-traceability.md](docs/test-traceability.md#L207)
  - Matrix now includes SignatureFlowIT and updated file count: [docs/test-traceability.md](docs/test-traceability.md#L205), [docs/test-traceability.md](docs/test-traceability.md#L223)
  - SignatureFlowIT exists in API tests: [repo/backend/api_tests/java/com/eventops/SignatureFlowIT.java](repo/backend/api_tests/java/com/eventops/SignatureFlowIT.java#L1)

### 3.7 Issue: Design doc migration-path mismatch

- Previous status: Medium / Fail
- Current status: Fixed
- Evidence of fix:
  - Design doc now points to src/main/resources/db/migration: [docs/design.md](docs/design.md#L126)
  - Runtime Flyway location remains classpath db/migration: [repo/backend/src/main/resources/application.yml](repo/backend/src/main/resources/application.yml#L29)

## 4. Notes

- This fix-check re-evaluates the issues listed in the previous fix-check report.
- No runtime guarantees are claimed from this static recheck.
- No remaining open items from this rerun set.
