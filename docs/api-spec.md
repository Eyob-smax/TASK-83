# API Specification

## 1. Overview

All APIs are REST-style endpoints served by the Spring Boot backend over HTTPS on the local facility network. The Vue.js frontend is the primary consumer. No external clients or third-party integrations are supported.

### 1.1 Base URL

```
https://<facility-host>:8443/api      # default (SSL_ENABLED=true)
http://<facility-host>:8443/api       # explicit local override (SSL_ENABLED=false)
```

In the default Docker Compose deployment, the backend serves HTTPS on port 8443. HTTPS requires operator provisioning of a PKCS12 keystore — see
`backend/src/main/resources/keystore/README.md`. Browsers interact with the
nginx frontend on port 443 (plain HTTP by default); the backend port is only
directly exposed for API testing.

### 1.2 Protocol Requirements

- **Transport**: HTTPS (default) with locally provisioned TLS certificates (see `SSL_ENABLED`)
- **Content-Type**: `application/json` for all request/response bodies
- **Authentication**: Session-based (cookie or token in `Authorization` header)
- **Request Signatures**: HMAC-SHA256 in `X-Request-Signature` header (disabled in dev profile; enabled in production)
- **Rate Limiting**: 60 requests/minute per authenticated user

### 1.3 Request Signature Format

Every mutating request (POST, PUT, PATCH, DELETE) must include:

```
X-Request-Signature: <Base64(HMAC-SHA256(secret, timestamp + nonce + requestBody))>
X-Request-Timestamp: <iso8601-timestamp or epoch-seconds>
X-Request-Nonce: <uuid>
```

The signature is computed as: `Base64(HMAC-SHA256(session_signing_token, timestamp + nonce + request_body))`.
For browser clients, `session_signing_token` is issued by the backend per authenticated session via response header and must not be embedded in build-time frontend configuration. The server accepts ISO-8601 or epoch-second timestamps and rejects requests older than 5 minutes or duplicate nonces.

---

## 2. Common Response Envelope

All API responses use a consistent envelope:

```json
{
  "success": true,
  "message": "Operation completed",
  "data": { ... },
  "timestamp": "2026-04-13T10:30:00Z",
  "errors": null
}
```

**Error response**:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "timestamp": "2026-04-13T10:30:00Z",
  "errors": [
    {
      "field": "email",
      "code": "INVALID_FORMAT",
      "message": "Email format is invalid"
    }
  ]
}
```

### 2.1 Standard HTTP Status Codes

| Code | Meaning                                                       |
| ---- | ------------------------------------------------------------- |
| 200  | Success                                                       |
| 201  | Created                                                       |
| 400  | Bad Request / Validation failure                              |
| 401  | Unauthenticated                                               |
| 403  | Forbidden / Insufficient role                                 |
| 404  | Resource not found                                            |
| 409  | Conflict (duplicate registration, concurrent device, etc.)    |
| 422  | Business rule violation (quota exceeded, window closed, etc.) |
| 429  | Rate limit exceeded                                           |
| 500  | Internal server error                                         |

### 2.2 Pagination

Paginated endpoints accept:

```
GET /api/events?page=0&size=20&sort=startTime,asc
```

Paginated response wrapper:

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

### 2.3 Conflict Semantics

Conflict responses (409) include a `conflictType` field to enable precise client-side handling:

```json
{
  "success": false,
  "message": "Already registered for this session",
  "errors": [
    {
      "code": "DUPLICATE_REGISTRATION",
      "message": "You are already registered for session 'Advanced Safety Training'",
      "conflictType": "DUPLICATE_REGISTRATION",
      "existingResourceId": "reg-uuid-123"
    }
  ]
}
```

---

## 3. Endpoint Groups

### 3.1 Authentication (`/api/auth`)

| Method | Path                 | Description                               | Auth Required | Roles |
| ------ | -------------------- | ----------------------------------------- | ------------- | ----- |
| POST   | `/api/auth/login`    | Authenticate with username/password       | No            | Any   |
| POST   | `/api/auth/logout`   | End session                               | Yes           | Any   |
| POST   | `/api/auth/register` | Create attendee account                   | No            | —     |
| POST   | `/api/auth/refresh`  | Refresh the current authenticated session | Yes           | Any   |
| GET    | `/api/auth/me`       | Get current user profile                  | Yes           | Any   |

**Anti-bot protection**: Login endpoint enforces throttling — 5 failed attempts triggers 15-minute lockout per username. Returns 429 with `retryAfterSeconds`.

### 3.2 Event Sessions (`/api/events`)

| Method | Path                            | Description                             | Auth Required | Roles |
| ------ | ------------------------------- | --------------------------------------- | ------------- | ----- |
| GET    | `/api/events`                   | List/search event sessions (paginated)  | Yes           | Any   |
| GET    | `/api/events/{id}`              | Get session detail with remaining seats | Yes           | Any   |
| GET    | `/api/events/{id}/availability` | Real-time seat quota check              | Yes           | Any   |

### 3.3 Registrations (`/api/registrations`)

| Method | Path                          | Description                   | Auth Required | Roles    |
| ------ | ----------------------------- | ----------------------------- | ------------- | -------- |
| POST   | `/api/registrations`          | Register for a session        | Yes           | ATTENDEE |
| GET    | `/api/registrations`          | List user's registrations     | Yes           | ATTENDEE |
| GET    | `/api/registrations/{id}`     | Get registration detail       | Yes           | ATTENDEE |
| DELETE | `/api/registrations/{id}`     | Cancel registration           | Yes           | ATTENDEE |
| GET    | `/api/registrations/waitlist` | Get user's waitlist positions | Yes           | ATTENDEE |

**Key business rules enforced server-side**:

- One registration per attendee per session (409 DUPLICATE_REGISTRATION)
- Quota check before confirming (422 QUOTA_EXCEEDED → waitlist offer)
- Waitlist promotion cutoff enforcement

### 3.4 Check-In (`/api/checkin`)

| Method | Path                                          | Description                             | Auth Required | Roles        |
| ------ | --------------------------------------------- | --------------------------------------- | ------------- | ------------ |
| GET    | `/api/checkin/sessions/{sessionId}/passcode`  | Get current rotating passcode           | Yes           | STAFF, ADMIN |
| POST   | `/api/checkin/sessions/{sessionId}`           | Check in an attendee                    | Yes           | STAFF, ADMIN |
| GET    | `/api/checkin/sessions/{sessionId}/roster`    | Get session roster with check-in status | Yes           | STAFF, ADMIN |
| GET    | `/api/checkin/sessions/{sessionId}/conflicts` | Get device/duplicate conflicts          | Yes           | STAFF, ADMIN |

**Key business rules**:

- Check-in window: 30 min before to 15 min after session start (422 WINDOW_CLOSED)
- Rotating 6-digit passcode validated per check-in
- Device binding: optional one-time per day (409 DEVICE_CONFLICT)
- Concurrent multi-device warning (409 CONCURRENT_DEVICE)
- Duplicate check-in detection (409 DUPLICATE_CHECKIN)

### 3.5 Notifications (`/api/notifications`)

| Method | Path                               | Description                           | Auth Required | Roles |
| ------ | ---------------------------------- | ------------------------------------- | ------------- | ----- |
| GET    | `/api/notifications`               | List user's notifications (paginated) | Yes           | Any   |
| GET    | `/api/notifications/unread-count`  | Get unread notification count         | Yes           | Any   |
| PATCH  | `/api/notifications/{id}/read`     | Mark notification as read             | Yes           | Any   |
| GET    | `/api/notifications/subscriptions` | Get subscription preferences          | Yes           | Any   |
| PUT    | `/api/notifications/subscriptions` | Update subscription preferences       | Yes           | Any   |
| GET    | `/api/notifications/dnd`           | Get DND settings                      | Yes           | Any   |
| PUT    | `/api/notifications/dnd`           | Update DND hours                      | Yes           | Any   |

### 3.6 Finance (`/api/finance`)

| Method | Path                                    | Description                       | Auth Required | Roles                  |
| ------ | --------------------------------------- | --------------------------------- | ------------- | ---------------------- |
| GET    | `/api/finance/periods`                  | List accounting periods           | Yes           | FINANCE_MANAGER, ADMIN |
| POST   | `/api/finance/periods`                  | Create accounting period          | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/finance/accounts`                 | List chart of accounts            | Yes           | FINANCE_MANAGER, ADMIN |
| POST   | `/api/finance/accounts`                 | Create account                    | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/finance/cost-centers`             | List cost centers                 | Yes           | FINANCE_MANAGER, ADMIN |
| POST   | `/api/finance/cost-centers`             | Create cost center                | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/finance/rules`                    | List allocation/recognition rules | Yes           | FINANCE_MANAGER, ADMIN |
| POST   | `/api/finance/rules`                    | Create rule (creates new version) | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/finance/rules/{id}/versions`      | List rule version history         | Yes           | FINANCE_MANAGER, ADMIN |
| POST   | `/api/finance/postings`                 | Execute posting                   | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/finance/postings`                 | List posting results              | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/finance/postings/{id}/line-items` | Get allocation line items         | Yes           | FINANCE_MANAGER, ADMIN |

### 3.7 Audit (`/api/audit`)

| Method | Path                               | Description                               | Auth Required | Roles |
| ------ | ---------------------------------- | ----------------------------------------- | ------------- | ----- |
| GET    | `/api/audit/logs`                  | Search audit logs (paginated, filterable) | Yes           | ADMIN |
| GET    | `/api/audit/logs/{id}`             | Get audit event detail with field diffs   | Yes           | ADMIN |
| POST   | `/api/audit/logs/export`           | Trigger CSV export of filtered logs       | Yes           | ADMIN |
| GET    | `/api/audit/exports/{id}/download` | Download exported CSV                     | Yes           | ADMIN |

**Filters**: `dateFrom`, `dateTo`, `actionType`, `operatorId`, `entityType`, `entityId`

### 3.8 Administration (`/api/admin`)

| Method | Path                           | Description                   | Auth Required | Roles |
| ------ | ------------------------------ | ----------------------------- | ------------- | ----- |
| GET    | `/api/admin/users`             | List users (paginated)        | Yes           | ADMIN |
| POST   | `/api/admin/users`             | Create user with role         | Yes           | ADMIN |
| PUT    | `/api/admin/users/{id}`        | Update user role/status       | Yes           | ADMIN |
| GET    | `/api/admin/security/settings` | Get security configuration    | Yes           | ADMIN |
| PUT    | `/api/admin/security/settings` | Update security configuration | Yes           | ADMIN |

### 3.9 Backups (`/api/admin/backups`)

| Method | Path                           | Description                 | Auth Required | Roles |
| ------ | ------------------------------ | --------------------------- | ------------- | ----- |
| GET    | `/api/admin/backups`           | List backup history         | Yes           | ADMIN |
| GET    | `/api/admin/backups/{id}`      | Get backup job detail       | Yes           | ADMIN |
| POST   | `/api/admin/backups/trigger`   | Trigger manual backup       | Yes           | ADMIN |
| GET    | `/api/admin/backups/retention` | Get retention policy status | Yes           | ADMIN |

### 3.10 Imports (`/api/imports`)

| Method | Path                           | Description                     | Auth Required | Roles        |
| ------ | ------------------------------ | ------------------------------- | ------------- | ------------ |
| GET    | `/api/imports/sources`         | List configured import sources  | Yes           | STAFF, ADMIN |
| POST   | `/api/imports/sources`         | Create import source definition | Yes           | ADMIN        |
| PUT    | `/api/imports/sources/{id}`    | Update import source            | Yes           | ADMIN        |
| GET    | `/api/imports/jobs`            | List crawl jobs (paginated)     | Yes           | STAFF, ADMIN |
| GET    | `/api/imports/jobs/{id}`       | Get job detail with trace ID    | Yes           | STAFF, ADMIN |
| POST   | `/api/imports/jobs/trigger`    | Trigger manual crawl            | Yes           | ADMIN        |
| GET    | `/api/imports/circuit-breaker` | Get circuit breaker status      | Yes           | ADMIN        |

Trigger payload (`POST /api/imports/jobs/trigger`):

```json
{
  "sourceId": "source-uuid-001",
  "mode": "INCREMENTAL",
  "priority": 100
}
```

- `priority` is optional.
- Lower numeric values represent higher queue priority.
- Accepted range is `1..1000`; omitted values default to `100`.

### 3.11 Exports (`/api/exports`)

| Method | Path                           | Description                   | Auth Required | Roles                  |
| ------ | ------------------------------ | ----------------------------- | ------------- | ---------------------- |
| POST   | `/api/exports/rosters`         | Generate roster export        | Yes           | STAFF, ADMIN           |
| POST   | `/api/exports/finance-reports` | Generate financial report     | Yes           | FINANCE_MANAGER, ADMIN |
| GET    | `/api/exports/{id}/download`   | Download export file          | Yes           | Role-dependent         |
| GET    | `/api/exports/policies`        | Get export/watermark policies | Yes           | ADMIN                  |
| PUT    | `/api/exports/policies`        | Update export policies        | Yes           | ADMIN                  |

**Export restrictions**: Downloads enforce watermark injection and role-based download restriction policies. All download attempts (allowed/denied) are audit-logged.

---

## 4. Offline/Local Integration Constraints

- All APIs operate without external service dependencies
- No outbound HTTP calls to external systems
- Notification dispatch is in-app only (no email, SMS, push)
- File imports read from locally configured shared-folder paths
- Exports write to locally configured storage paths
- Backup artifacts stored on local/network filesystem
- HTTPS certificates are locally provisioned (no ACME/Let's Encrypt)

---

## 5. Concrete Request/Response Examples (Added Prompt 2)

### 5.1 Register for Session

**Request**: `POST /api/registrations`

```json
{
  "sessionId": "abc-123-def"
}
```

**Success Response** (201):

```json
{
  "success": true,
  "message": "Registration confirmed",
  "data": {
    "id": "reg-uuid-001",
    "userId": "user-uuid-001",
    "sessionId": "abc-123-def",
    "sessionTitle": "Advanced Safety Training",
    "status": "CONFIRMED",
    "waitlistPosition": null,
    "createdAt": "2026-04-13T10:00:00Z"
  }
}
```

**Duplicate Conflict** (409):

```json
{
  "success": false,
  "message": "Already registered for this session",
  "errors": [
    {
      "code": "DUPLICATE_REGISTRATION",
      "message": "You are already registered for session 'Advanced Safety Training'",
      "field": "sessionId"
    }
  ]
}
```

**Waitlisted** (201):

```json
{
  "success": true,
  "message": "Session is full — added to waitlist",
  "data": {
    "id": "reg-uuid-002",
    "userId": "user-uuid-001",
    "sessionId": "abc-123-def",
    "status": "WAITLISTED",
    "waitlistPosition": 3,
    "createdAt": "2026-04-13T10:01:00Z"
  }
}
```

### 5.2 Check-In Attendee

**Request**: `POST /api/checkin/sessions/{sessionId}`

```json
{
  "userId": "user-uuid-001",
  "passcode": "483291",
  "deviceToken": "device-token-hash"
}
```

**Success** (200):

```json
{
  "success": true,
  "data": {
    "id": "ci-uuid-001",
    "sessionId": "abc-123-def",
    "userId": "user-uuid-001",
    "status": "CHECKED_IN",
    "checkedInAt": "2026-04-13T09:55:00Z",
    "message": "Check-in successful"
  }
}
```

**Window Closed** (422):

```json
{
  "success": false,
  "message": "Check-in window is not open",
  "errors": [
    {
      "code": "WINDOW_CLOSED",
      "message": "Check-in opens at 09:30 and closes at 10:15"
    }
  ]
}
```

### 5.3 Login

**Request**: `POST /api/auth/login`

```json
{
  "username": "jdoe",
  "password": "securepass123"
}
```

**Success** (200):

```json
{
  "success": true,
  "data": {
    "id": "user-uuid-001",
    "username": "jdoe",
    "displayName": "Jane Doe",
    "contactInfoMasked": "****@facility.local",
    "roleType": "ATTENDEE",
    "status": "ACTIVE",
    "lastLoginAt": "2026-04-12T16:00:00Z"
  }
}
```

**Locked Out** (429):

```json
{
  "success": false,
  "message": "Account locked due to too many failed attempts",
  "errors": [
    {
      "code": "ACCOUNT_LOCKED",
      "message": "Try again after 15 minutes",
      "field": "username"
    }
  ]
}
```

### 5.4 Finance Posting

**Request**: `POST /api/finance/postings`

```json
{
  "periodId": "period-uuid-001",
  "sessionId": "abc-123-def",
  "ruleId": "rule-uuid-001",
  "totalAmount": 5000.0,
  "description": "Q1 training allocation"
}
```

**Period Closed** (422):

```json
{
  "success": false,
  "message": "Cannot post to a closed period",
  "errors": [
    {
      "code": "PERIOD_CLOSED",
      "message": "Accounting period 'Q1 2026' is closed"
    }
  ]
}
```

### 5.5 Validation Error Example

**Malformed Input** (400):

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "username",
      "code": "INVALID_FORMAT",
      "message": "Username must be 3-100 characters"
    },
    {
      "field": "password",
      "code": "INVALID_FORMAT",
      "message": "Password must be 8-128 characters"
    }
  ]
}
```

## 6. Idempotency and Conflict Conventions (Added Prompt 2)

### Idempotency Keys

- Notification send logs use `idempotency_key` (UNIQUE constraint) to prevent duplicate sends
- Clients should include `X-Idempotency-Key` header on POST requests where replay protection is needed
- Server returns 409 with `IDEMPOTENCY_DUPLICATE` conflict type for duplicate keys

### Conflict Types (ConflictType enum)

| Code                     | HTTP Status | Domain        |
| ------------------------ | ----------- | ------------- |
| `DUPLICATE_REGISTRATION` | 409         | Registration  |
| `QUOTA_EXCEEDED`         | 422         | Registration  |
| `WINDOW_CLOSED`          | 422         | Check-in      |
| `DEVICE_CONFLICT`        | 409         | Check-in      |
| `CONCURRENT_DEVICE`      | 409         | Check-in      |
| `DUPLICATE_CHECKIN`      | 409         | Check-in      |
| `IDEMPOTENCY_DUPLICATE`  | 409         | Cross-cutting |
| `PERIOD_CLOSED`          | 422         | Finance       |
| `RULE_VERSION_CONFLICT`  | 409         | Finance       |

## 7. Authentication Flow Detail (Added Prompt 3)

### 7.1 Login Sequence

```
Client                              Server
  |                                    |
  |-- POST /api/auth/login ----------->|
  |   { username, password }           |
  |                                    |-- Load user by username
  |                                    |-- Check lockout (isLockedOut?)
  |                                    |   If locked: return 429 ACCOUNT_LOCKED
  |                                    |-- Verify BCrypt password hash
  |                                    |   If wrong: recordFailedAttempt, return 401
  |                                    |-- recordSuccessfulLogin (reset counters)
  |                                    |-- Create HTTP session
  |<-- 200 { user data, session } -----|
  |                                    |
  |-- Store in sessionStorage          |
```

### 7.2 Request Signature Headers (Mutating Requests)

```
X-Request-Signature: <Base64(HMAC-SHA256(secret, timestamp + nonce + body))>
X-Request-Timestamp: 2026-04-13T10:30:00.000Z
X-Request-Nonce: 550e8400-e29b-41d4-a716-446655440000
```

Server rejects if:

- Any header is missing (401)
- Timestamp is older than 300 seconds (401 REPLAY_DETECTED)
- Nonce was already seen within the TTL window (401 REPLAY_DETECTED)
- Signature does not match computed value (401 INVALID_SIGNATURE)

GET requests and `/api/auth/login`, `/api/auth/register` skip signature verification.

### 7.3 Rate Limit Headers

All authenticated responses include:

```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 42
```

On limit exceeded (429):

```
Retry-After: 15
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
```

### 7.4 Security Error Codes

| HTTP | Code                    | Trigger                            |
| ---- | ----------------------- | ---------------------------------- |
| 400  | VALIDATION_ERROR        | Invalid request body fields        |
| 401  | AUTHENTICATION_REQUIRED | No session / expired session       |
| 401  | INVALID_SIGNATURE       | Signature verification failed      |
| 401  | REPLAY_DETECTED         | Stale timestamp or duplicate nonce |
| 403  | ACCESS_DENIED           | Insufficient role                  |
| 409  | DUPLICATE_USERNAME      | Username already taken on register |
| 429  | ACCOUNT_LOCKED          | Too many failed login attempts     |
| 429  | RATE_LIMIT_EXCEEDED     | Over 60 req/min                    |

### 7.5 Authorization Matrix

| Endpoint Group          | ATTENDEE | EVENT_STAFF | FINANCE_MANAGER | SYSTEM_ADMIN |
| ----------------------- | -------- | ----------- | --------------- | ------------ |
| `/api/auth/**`          | Yes      | Yes         | Yes             | Yes          |
| `/api/events/**`        | Yes      | Yes         | Yes             | Yes          |
| `/api/registrations/**` | Own only | All         | —               | All          |
| `/api/notifications/**` | Own only | Own only    | Own only        | All          |
| `/api/checkin/**`       | —        | Yes         | —               | Yes          |
| `/api/imports/**`       | —        | Yes         | —               | Yes          |
| `/api/finance/**`       | —        | —           | Yes             | Yes          |
| `/api/exports/**`       | —        | Yes         | Yes             | Yes          |
| `/api/audit/**`         | —        | —           | —               | Yes          |
| `/api/admin/**`         | —        | —           | —               | Yes          |

## 8. Implemented Endpoints (After Prompt 4)

All operational API endpoints are now implemented with real controllers, services, and persistence:

| Group         | Endpoints Implemented                                                             | Controller             |
| ------------- | --------------------------------------------------------------------------------- | ---------------------- |
| Auth          | POST login/register/logout, GET me                                                | AuthController         |
| Events        | GET /api/events (paginated+search), GET /{id}, GET /{id}/availability             | EventController        |
| Registrations | POST /api/registrations, GET (list), GET /{id}, DELETE /{id}, GET /waitlist       | RegistrationController |
| Check-in      | POST /sessions/{id}, GET passcode, GET roster, GET conflicts                      | CheckInController      |
| Notifications | GET (paginated), GET unread-count, PATCH read, GET/PUT subscriptions, GET/PUT dnd | NotificationController |
| Imports       | GET sources, GET jobs, GET jobs/{id}, POST trigger, GET circuit-breaker           | ImportController       |

**Business conflict codes added in Prompt 4:**

| HTTP | Code                   | Trigger                                          |
| ---- | ---------------------- | ------------------------------------------------ |
| 409  | DUPLICATE_REGISTRATION | Same user re-registers for same session          |
| 422  | SESSION_NOT_OPEN       | Session not in OPEN_FOR_REGISTRATION/FULL status |
| 422  | ALREADY_CANCELLED      | Cancelling an already-cancelled registration     |
| 422  | WINDOW_CLOSED          | Check-in outside the configured time window      |
| 422  | INVALID_PASSCODE       | Incorrect or expired 6-digit passcode            |
| 422  | NOT_REGISTERED         | Attendee not registered for the session          |
| 409  | DUPLICATE_CHECKIN      | Same user checked in twice for same session      |
| 409  | DEVICE_CONFLICT        | Device binding mismatch (different device today) |

**Finance/Compliance endpoints added in Prompt 5:**

| Group   | Endpoints                                                                                                                                              | Controller        |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------- |
| Finance | GET/POST periods, PUT close, GET/POST accounts, GET/POST cost-centers, GET/POST rules, PUT rules/{id}, POST/GET postings, GET line-items, POST reverse | FinanceController |
| Audit   | GET logs (paginated+filtered), GET logs/{id} (with diffs), POST logs/export, GET exports/{id}/download                                                 | AuditController   |
| Exports | POST rosters, POST finance-reports, GET /{id}/download, GET/PUT policies                                                                               | ExportController  |
| Backups | GET (list), GET /{id}, POST trigger, GET retention                                                                                                     | BackupController  |

**Finance conflict codes:**

| HTTP | Code          | Trigger                                   |
| ---- | ------------- | ----------------------------------------- |
| 422  | PERIOD_CLOSED | Posting to a closed accounting period     |
| 403  | EXPORT_DENIED | Watermark policy denies download for role |

All operational and compliance endpoints are now implemented. Frontend UI screens for finance/admin deferred to Prompts 6-7.
