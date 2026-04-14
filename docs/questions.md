# questions.md

## 1. Local HTTPS certificate provisioning and trust model

**The Gap**  
The prompt requires HTTPS on the local facility network with request signature verification, but it does not specify how TLS certificates are issued, trusted, rotated, or distributed inside the offline environment.

**The Interpretation**  
We should assume the platform is deployed inside a controlled offline network where administrators can provision locally trusted certificates through an internal CA or a pre-generated certificate bundle distributed with deployment instructions. The implementation should not depend on any public CA, ACME flow, or internet reachability.

**Proposed Implementation**  
Implement backend HTTPS configuration using externally supplied keystore/truststore files and documented environment/config properties. Provide a development-safe local certificate path for containerized verification and a production path that accepts administrator-supplied certificate material. Keep request-signature verification independent of TLS issuance so transport trust and message authenticity remain separate controls.

## 2. Rotating 6-digit passcode authority and verification scope

**The Gap**  
The prompt requires a rotating 6-digit passcode updated every 60 seconds on the Check-In screen, but it does not state whether this passcode is merely displayed for staff awareness, used as an operator confirmation factor, or validated against attendee actions.

**The Interpretation**  
Treat the rotating passcode as a staff-facing operational control generated server-side per active event/session or check-in context, primarily used to support controlled check-in verification and reduce proxy/manual misuse. It should not replace authentication or device-binding logic.

**Proposed Implementation**  
Generate passcodes on the backend using a time-sliced deterministic or cryptographically secure rolling scheme bound to the relevant session/check-in context, expose the current code and countdown through a protected API, and record validation/audit events when the passcode is used in a guarded check-in flow. Keep the design modular so a later policy decision can switch between display-only and actively enforced verification without rewriting the whole module.

## 3. One-time device binding identity source

**The Gap**  
The prompt requires optional one-time device binding on first check-in per day and warnings on concurrent multi-device attempts, but it does not specify the exact device identifier source in an offline web environment.

**The Interpretation**  
Assume device identity must be derived from a controlled app-generated device token plus stable local browser/client fingerprints that are acceptable for an internal offline facility environment, while acknowledging that browser-only device identity is not perfect.

**Proposed Implementation**  
Issue a signed client device token at first authenticated staff use, persist a hashed device-binding record server-side, and pair it with conservative fingerprint metadata such as user agent, local device label if available, and issued token lineage. Use this to detect same-user cross-device conflicts and warn or block based on policy. Keep device-binding policy configurable at session/event level.

## 4. Weak-connectivity local cache and pending-action storage limits

**The Gap**  
The prompt requires roster caching, pending local actions, resumable uploads, and retry prompts under weak connectivity, but it does not define retention limits, storage quotas, or which entities are safe to cache locally.

**The Interpretation**  
Assume only the minimum operational data needed for active event workflows should be cached locally on the client, with short-lived retention and explicit reconciliation behavior to reduce stale or sensitive offline copies.

**Proposed Implementation**  
Cache only active roster slices, pending check-in actions, upload manifests, and UI-needed session metadata in a structured client-side store with timestamps, version markers, and explicit expiration. Encrypt or obfuscate client-stored sensitive fields only if the chosen client architecture supports it cleanly, and always revalidate against the backend during replay/reconnect before final state commitment.

## 5. Attachment upload types, storage location, and retention

**The Gap**  
The prompt mentions resumable uploads of attachments but does not specify what attachments are allowed, where they are stored in the offline facility network, how large they may be, or how long they are retained.

**The Interpretation**  
Treat attachments as operational event-support artifacts stored on local managed storage accessible to the backend, with type and size restrictions enforced by policy. The exact storage root should be configurable per deployment.

**Proposed Implementation**  
Implement resumable upload support through backend-managed file storage rooted in a configurable local filesystem path or offline network share. Enforce allowlisted MIME/extensions and configurable size caps, store attachment metadata in MySQL, and link retention/deletion policy to event or audit retention settings. Document the assumption in config and README rather than hardcoding deployment-specific paths.

## 6. Waitlist promotion execution trigger and cutoff enforcement

**The Gap**  
The prompt states that the UI clearly shows the cutoff time for promotion, but it does not define whether promotions happen immediately on seat release, on a scheduler, or through staff-triggered processing when the cutoff approaches.

**The Interpretation**  
Assume waitlist promotions should be processed automatically by the backend whenever seat availability changes and also by scheduled reconciliation jobs, with promotion eligibility blocked once the cutoff is reached.

**Proposed Implementation**  
Model waitlist promotions as a backend service triggered by registration cancellation/capacity change events plus a periodic reconciliation scheduler. Persist a computed promotion cutoff per session, evaluate promotions transactionally, and emit notification-center events for successful promotions or expired eligibility. Keep audit entries for promotion attempts and cutoff rejections.

## 7. Badge CSV import file format ownership and shared-folder conventions

**The Gap**  
The prompt mentions incremental and full crawls of locally defined data sources such as badge CSV drops from a shared folder, but it does not define the exact CSV schema, naming conventions, or folder layout.

**The Interpretation**  
Assume source definitions are administrator-configured and each source can declare its own file pattern, schema mapping, and import mode. The system should not hardcode one badge CSV layout globally.

**Proposed Implementation**  
Create a configurable source-definition model that stores folder path, filename pattern, import mode, column mappings, checkpoint markers, and validation rules. Implement parser adapters so new CSV layouts can be supported by configuration plus bounded code extensions instead of rewriting the scheduler/import engine.

## 8. Finance posting destination and accounting finalization semantics

**The Gap**  
The prompt requires finance posting results, revenue recognition, allocation methods, and auditable allocation line items, but it does not specify whether "posting" means writing to an internal subledger only or finalizing records for export to another offline accounting system.

**The Interpretation**  
Assume the platform owns an internal accounting/event-finance subledger and may later export summarized results to other offline systems, but prompt completion requires truthful internal posting, rule evaluation, and auditability first.

**Proposed Implementation**  
Implement internal posting journals, allocation line items, rule-version snapshots, and posting-result statuses inside the platform database. Provide export-ready endpoints/files later as a separate adapter surface without making the core finance engine dependent on an external accounting package.

## 9. Export watermark content and download restriction policy granularity

**The Gap**  
The prompt requires configurable watermarking and download restrictions for exported rosters and financial reports, but it does not specify whether restrictions are role-based, per-report-type, per-user, or environment-wide, nor what watermark fields must appear.

**The Interpretation**  
Assume export restrictions should be policy-driven by report type and role, with optional finer-grained overrides. Watermarks should at minimum include operator identity, export timestamp, and report context.

**Proposed Implementation**  
Create an export-policy module where administrators configure allowed export types per role and report category, plus watermark templates containing operator name/ID, timestamp, request source, and report label. Enforce policy at service/export-generation level and persist audit records for all export attempts, whether allowed or denied.

## 11. HTTPS / TLS for the Docker Compose development deployment

**The Gap**  
The platform requires HTTPS on the local facility network, but the Docker Compose setup must also be easy to bring up for the first time without pre-provisioning certificates.

**The Interpretation**  
The default `docker-compose.yml` ships with `SSL_ENABLED=false` so that first-run startup works without any TLS credential provisioning. The backend serves plain HTTP on port 8443 and nginx proxies to it over plain HTTP. This is acceptable for a controlled offline facility network where the administrator controls the physical infrastructure.

**Proposed Implementation**  
Document the distinction clearly in README and in `backend/src/main/resources/keystore/README.md`. Production deployments where end-to-end TLS is required follow a documented three-step process: generate/supply a PKCS12 keystore, set `SSL_ENABLED=true`, and update nginx.conf to use `https://backend:8443`. Request-signature verification (HMAC-SHA256) remains a separate transport-independent control and should be enabled (`SIGNATURE_ENABLED=true`) independently of TLS status.

## 12. CORS policy for a local-network same-origin-proxy deployment

**The Gap**  
The platform runs a Vue.js SPA behind an nginx reverse proxy that forwards all `/api/*` requests to the Spring Boot backend. CORS headers on the backend are therefore not exercised by normal browser traffic (same-origin from the browser's perspective), but direct API testing tools and Vite dev server access do trigger CORS checks.

**The Interpretation**  
Configure CORS permissively for the internal local network: allow `localhost:443` (nginx frontend), `localhost:5173` (Vite dev server), and bare `localhost`, with all methods and credentials. Make allowed origins configurable via the `CORS_ALLOWED_ORIGINS` environment variable so operators can restrict to their specific network topology without a code change.

**Proposed Implementation**  
Implemented in `WebConfig.addCorsMappings()` reading from `${eventops.cors.allowed-origins}` (env var `CORS_ALLOWED_ORIGINS`). Default is documented in `.env.example` and `application.yml`. CORS is mapped to `/api/**` only.

## 10. Backup storage target and restore-visibility expectations

**The Gap**  
The prompt requires nightly local backups with 30-day retention, but it does not define the backup storage location, encryption expectation for backups, or whether restore workflows must be user-facing in this version.

**The Interpretation**  
Assume backups are written to an administrator-configured local path or controlled network share within the offline environment, with retention and execution history managed by the platform. Full restore tooling can remain operational/admin-oriented rather than a broad end-user feature unless the implementation naturally supports more.

**Proposed Implementation**  
Implement scheduled backup jobs that write encrypted or otherwise protected backup artifacts to a configurable local target, persist execution history and retention metadata in MySQL, and expose admin-visible status/history screens. Keep full restore mechanics documented as an administrator-controlled operational procedure unless explicit restore UI requirements emerge later.
