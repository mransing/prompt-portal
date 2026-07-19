# Prompt Version Control Portal — Technical Design (MVP)

**Status:** Implementation-ready  
**Based on:** `REQUIREMENTS.md` (decisions locked)  
**Date:** 2026-07-19  

---

## 1. Summary

Self-hosted web app for a **single user** to store and version **prompt text**, attach **result images** (not versioned), sign in with **Google or Facebook**, enforce **2 MB / file** and **50 MB total** image quota, and purge **unmodified data after 2 years**.

**Domain invariant:** every image belongs to exactly one prompt (the prompt that created it). One prompt may own many images. No orphan media; `promptId` is required on every media document; uploads only via prompt-scoped REST APIs.

**Deploy (locked):** native OS processes on a PC/server. **Docker is not part of the stack** (no Dockerfile, Compose, or containers in MVP).

---

## 2. Locked stack

| Layer | Choice | Why |
|-------|--------|-----|
| Frontend | **React / Next.js** (UI only) + TypeScript | Mockups and UX; talks to backend via **REST only** |
| Backend | **Java** (Spring Boot 3) + REST API | User preference; clear API boundary |
| Auth | **Spring Security OAuth2** — Google + Facebook + email allowlist | OAuth-only, single user |
| DB | **MongoDB** | Document store for users, prompts, versions, media metadata |
| File storage | **Local disk** (`UPLOAD_DIR/{promptId}/…`); path string in MongoDB | 50 MB quota; no S3; no Docker volumes |
| Diff | Client-side or API helper (`diff` / similar) | Text-only version compare |
| Image validation | FE allowlist + BE magic bytes + decode | PNG/JPEG/WebP/GIF only |
| Logging | AUDIT → FATAL → ERROR → WARNING → INFO → DEBUG; PERF at INFO | Ops and safe errors |
| Runtime (API) | **JDK 21** (or 17+) | Spring Boot |
| Runtime (DB) | **MongoDB** as OS service | Native install |
| Runtime (UI, optional same machine) | **Node.js 20+** to build/serve frontend | Not required if static export served elsewhere |
| Deploy | **Native processes only** — Windows service or manual start | Self-hosted PC/server |

### Explicitly **not** in the stack

| Excluded | Notes |
|----------|--------|
| **Docker / Docker Compose / Kubernetes** | Out of scope for MVP; do not document as install path |
| SQLite / Prisma / Auth.js as backend | Superseded by Java + MongoDB + Spring Security |
| S3 / cloud object storage | Local disk only |
| Video file handling | Tag only |

**Alternatives (not default):** Postgres instead of Mongo later if needed; still native processes, still no Docker requirement.

---

## 3. Architecture

```
Browser (React / Next.js UI)
  │  REST only (JSON + multipart + binary)
  ▼
Java Spring Boot API  (:8080)
  ├── Spring Security OAuth2 (Google, Facebook) + allowlist
  ├── REST controllers (prompts, versions, media, storage)
  ├── MongoDB  (:27017) — metadata + relative storagePath
  └── Local disk — UPLOAD_DIR/{promptId}/...

Retention: in-app @Scheduled and/or Windows Task Scheduler CLI
(No Docker, no container network, no compose volumes)
```

**Process boundaries**

- Two long-running processes: **MongoDB** + **Java API** (frontend optional third).
- Uploads stream to disk; MongoDB stores relative paths + sizes only.
- Quota = sum of `byteSize` on media documents for the user.

---

## 4. Data model (MongoDB sketch; was Prisma-oriented)

```prisma
model User {
  id            String   @id @default(cuid())
  email         String   @unique
  name          String?
  image         String?
  provider      String   // "google" | "facebook"
  providerId    String
  createdAt     DateTime @default(now())
  prompts       Prompt[]
  // OAuth/session fields as required by Spring Security (collections may differ)
}

model Prompt {
  id              String   @id @default(cuid())
  ownerId         String
  owner           User     @relation(fields: [ownerId], references: [id])
  title           String
  description     String?
  status          String   // draft | active | archived
  mediaTypeFocus  String   // image | video | mixed | text-only
  tags            String   // JSON array or separate Tag table
  currentVersion  Int      @default(1)
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt
  lastModifiedAt  DateTime @default(now()) // retention clock
  deletedAt       DateTime? // soft delete
  versions        PromptVersion[]
  media           MediaAsset[]
}

model PromptVersion {
  id              String   @id @default(cuid())
  promptId        String
  prompt          Prompt   @relation(fields: [promptId], references: [id], onDelete: Cascade)
  versionNumber   Int
  body            String
  negativePrompt  String?
  parametersJson  String?  // JSON
  changeSummary   String?
  createdAt       DateTime @default(now())
  createdById     String

  @@unique([promptId, versionNumber])
  @@index([promptId])
}

model MediaAsset {
  id          String   @id @default(cuid())
  // REQUIRED: every image is created by exactly one prompt (1:N from Prompt).
  // Never null — no standalone media library.
  promptId    String
  prompt      Prompt   @relation(fields: [promptId], references: [id], onDelete: Cascade)
  role        String   @default("output") // result of this prompt
  fileName    String
  mimeType    String
  byteSize    Int
  storagePath String
  width       Int?
  height      Int?
  checksum    String?
  caption     String?
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  @@index([promptId])
}
```

**Notes**

- `lastModifiedAt` updates on any prompt metadata, version save, or media change (drives 2-year purge).
- Tags: start as JSON string array for speed; normalize later if needed.
- Spring Security / OAuth may use its own session or token collections; keep media `promptId` required in application code (Mongo has no FK cascade — implement in services).

---

## 5. Auth design

1. Configure Google and Facebook OAuth apps; set redirect URIs to the **Java API** base URL (e.g. `http://localhost:8080/...`).
2. Env: `AUTH_SECRET`, `AUTH_GOOGLE_ID/SECRET`, `AUTH_FACEBOOK_ID/SECRET`.
3. Env allowlist: `ALLOWED_EMAILS=you@gmail.com` (comma-separated).
4. On sign-in callback: reject if email not in allowlist (AUDIT log).
5. Single-user UX: if another account tries login → clear error JSON, no stack traces.

---

## 6. Versioning algorithm

On **Save** (create or update):

```
INPUT: title, description, status, mediaTypeFocus, tags,
       body, negativePrompt, parameters, changeSummary?

IF new prompt:
  create Prompt (status may be draft)
  create PromptVersion versionNumber=1 with body/params
  lastModifiedAt = now
  return

IF existing:
  update metadata fields always
  load latest PromptVersion
  IF body | negativePrompt | parametersJson changed:
    versionNumber = currentVersion + 1
    insert PromptVersion
    prompt.currentVersion = versionNumber
  update lastModifiedAt
  return
```

**No Commit button.** Media endpoints do not call version creation.

**Restore version K:**

```
load version K text fields
create new version with next number and those fields
(do not change media)
```

---

## 7. Media & quota

| Rule | Implementation |
|------|----------------|
| Types | `image/png`, `image/jpeg`, `image/webp`, `image/gif` only |
| Max file | 2 * 1024 * 1024 bytes; reject before write if `Content-Length` or measured size exceeds |
| Total quota | 50 * 1024 * 1024 bytes; `used = SUM(byteSize)`; reject if `used + new > quota` |
| Warning | API returns `{ usedBytes, quotaBytes, warning: used/quota >= 0.9 }` |
| Replace | Upload new file → write temp → validate fully → update row path/size → delete old file |
| Path | `data/uploads/{promptId}/{assetId}.{ext}` — **ext from server-detected type only** |
| Video / other | Reject with **415** (or 400) + clear message; do not persist bytes |

Serve images via authenticated REST route only so files are not world-readable on disk URLs.

---

## 7.1 Image type restriction & upload hardening (FE + BE)

**Goal:** Do not store arbitrary binary disguised as an image (malware, HTML/JS polyglots, SVG XSS, renamed `.exe`, etc.).  
**Principle:** Frontend validation is for **UX**; backend validation is for **security**. Both are required.

### Allowed set (closed allowlist)

| Format | MIME (stored) | Extensions accepted (client + server check) | Signature (magic) notes |
|--------|---------------|---------------------------------------------|-------------------------|
| JPEG | `image/jpeg` | `.jpg`, `.jpeg` | Starts with `FF D8 FF` |
| PNG | `image/png` | `.png` | `89 50 4E 47 0D 0A 1A 0A` |
| GIF | `image/gif` | `.gif` | `GIF87a` or `GIF89a` |
| WebP | `image/webp` | `.webp` | `RIFF....WEBP` (bytes 0–3 `RIFF`, 8–11 `WEBP`) |

**Explicitly forbidden (non-exhaustive):** SVG/SVGZ, BMP, TIFF, ICO, HEIC/HEIF, AVIF (unless later approved), PDF, HTML/HTM/XML, JS, WASM, ZIP/JAR, EXE/DLL/MSI, scripts, Office docs, video/audio, unknown/`application/octet-stream` without matching image magic.

### Frontend (must implement)

| Step | Behavior |
|------|----------|
| File picker | `accept="image/png,image/jpeg,image/jpg,image/webp,image/gif,.png,.jpg,.jpeg,.webp,.gif"` |
| Drag-and-drop | Same rules as picker; do not rely on `accept` alone |
| Extension | Lowercase extension must be in allowlist; reject otherwise **before** upload |
| MIME | If `file.type` is non-empty, it must be one of the four MIME types; empty type → still allow only if extension OK, then server decides |
| Size | Reject if `file.size > 2_097_152` or `size === 0` |
| Optional preview | `URL.createObjectURL` + `<img onload/onerror>` — if image fails to load in browser, reject (helps catch obvious non-images) |
| UX | Block submit; show message e.g. “Only PNG, JPEG, WebP, or GIF up to 2 MB.” |
| Multi-file | Validate **each** file independently |

**Frontend must not:**

- Trust that the browser MIME is authoritative  
- Skip checks because the backend will catch them (backend still must; FE still must for UX and reduced abuse traffic)  
- Accept `image/*` broadly  

Example validation sketch (TypeScript):

```ts
const ALLOWED_EXT = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif']);
const ALLOWED_MIME = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif']);
const MAX = 2 * 1024 * 1024;

function validateImageFile(file: File): string | null {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!ALLOWED_EXT.has(ext)) return 'File type not allowed';
  if (file.type && !ALLOWED_MIME.has(file.type)) return 'File type not allowed';
  if (file.size <= 0 || file.size > MAX) return 'File must be between 1 byte and 2 MB';
  return null;
}
```

### Backend (must implement — authority)

Pipeline for `POST/PUT .../media` (order matters). Fail fast; **delete any temp file** on failure.

| # | Check | Fail response |
|---|--------|----------------|
| 1 | Authenticated + allowlisted user | 401 / 403 |
| 2 | Prompt exists; media scoped to that prompt | 404 |
| 3 | Multipart present; measured size ≤ 2 MB (and Spring multipart limits) | 413 |
| 4 | Original filename: no path segments (`\`, `/`); reject `..` | 400 |
| 5 | Extension allowlist (from original name); map `.jpg`/`.jpeg` → jpeg | 415 |
| 6 | Declared `Content-Type` if present must be allowlisted MIME (ignore parameters) | 415 |
| 7 | Read **magic bytes** (first ~16 bytes); must match one allowed image signature | 415 |
| 8 | Extension / declared MIME / magic **must agree** (same family) | 415 |
| 9 | **Decode** image with a real decoder (e.g. ImageIO + TwelveMonkeys for WebP if needed, or `javax.imageio` / dedicated lib). Must produce width/height ≥ 1 | 415 |
| 10 | Optional: re-encode or strip metadata later; MVP: store original bytes only after 1–9 pass | — |
| 11 | Quota: `used + size ≤ 50 MB` | 413 / 400 with `QUOTA_EXCEEDED` |
| 12 | Persist to `{promptId}/{assetId}.{ext}` where **ext/MIME come from detection**, not client | 201/200 |

**Do not:**

- Trust `Content-Type` header alone  
- Trust extension alone  
- Store under user-provided filename  
- Serve upload directory as static files  
- Allow SVG “images”  

**Java sketch (signature check):**

```java
// After streaming to temp file or byte array (≤ 2MB)
String detected = ImageSignatureDetector.detect(bytes); // "image/png" | ... | null
if (detected == null) throw new UnsupportedMediaType(...);
if (!detected.equals(normalizedClientMime) && clientMimePresent) { /* reject mismatch */ }
BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
if (img == null || img.getWidth() < 1 || img.getHeight() < 1)
    throw new UnsupportedMediaType(...);
// store mime=detected, path=promptId + "/" + assetId + extensionFor(detected)
```

### Serving (reduce later exploit)

When `GET /api/prompts/{id}/media/{mediaId}`:

| Header / behavior | Value |
|-------------------|--------|
| `Content-Type` | Stored server MIME only (`image/png` etc.) |
| `X-Content-Type-Options` | `nosniff` |
| `Content-Disposition` | `inline; filename="safe-or-original.png"` (sanitized) or `attachment` if preferred |
| Body | Raw file bytes from disk path resolved under upload root |
| Auth | Required; verify `media.promptId` matches path id |

Never set `Content-Type: text/html` for media. Never execute or template-render file contents.

### API error contract (for FE)

Uses the global error shape (§8.6). Example:

```json
{
  "code": "UNSUPPORTED_MEDIA_TYPE",
  "message": "Only PNG, JPEG, WebP, and GIF images up to 2 MB are allowed.",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

Other codes: `FILE_TOO_LARGE`, `QUOTA_EXCEEDED`, `EMPTY_FILE`, `INVALID_IMAGE_CONTENT`, `INTERNAL_ERROR`.  
**Never** include stack traces or Java exception class names in this body.

### Test cases (security)

- [ ] `.png` that is actually HTML/JS → rejected (magic + decode)  
- [ ] `.exe` renamed to `.jpg` → rejected  
- [ ] Correct JPEG with wrong `Content-Type: image/png` → rejected (mismatch)  
- [ ] SVG renamed `.png` → rejected  
- [ ] Polyglot ZIP/JPEG edge: decoder + signature policy rejects if not clean image  
- [ ] Oversize 2 MB + 1 byte → rejected FE and BE  
- [ ] Video MIME / `.mp4` → rejected  
- [ ] Valid PNG/JPEG/WebP/GIF → accepted; disk name `{id}.ext`; DB MIME server-side  
- [ ] Upload without auth → 401  
- [ ] FE blocks disallowed type without calling API  

### Summary

| Layer | Responsibility |
|-------|----------------|
| **Frontend** | Restrict picker, extension/MIME/size, optional decode preview, clear errors |
| **Backend** | Size limits, allowlists, magic bytes, MIME/extension/magic consistency, image decode, safe storage name, safe serve headers |
| **Trust model** | Client is untrusted; only backend-validated files touch disk and MongoDB |

---

## 8. Logging, performance metrics, and error handling

### 8.1 Log levels (priority order)

Higher priority = more severe. **AUDIT is always highest priority** (always written when the configured threshold allows it — see filter below).

| Priority (high → low) | Level | Use for |
|-----------------------|--------|---------|
| 1 (highest) | **AUDIT** | Security and compliance events: login success/failure, logout, allowlist deny, create/update/delete prompt, version restore, media upload/replace/delete, soft-delete, purge job actions. Immutable intent: who/what/when. |
| 2 | **FATAL** | Process cannot continue: failed to bind port, cannot connect to Mongo at startup after retries, upload root not writable, missing required config. |
| 3 | **ERROR** | Request failed unexpectedly or business operation failed after validation: unhandled exceptions, disk I/O failure mid-write, DB write failure. Include error code + correlation id; stack trace **only in logs**, never in HTTP body. |
| 4 | **WARNING** | Recoverable issues: near-quota (≥90%), rejected upload (bad type/size), slow request over threshold, deprecated client, retry succeeded. |
| 5 | **INFO** | Normal operations and **API performance metrics** (see §8.3). Startup banner (effective log level, upload dir, app version). |
| 6 (lowest) | **DEBUG** | Diagnostics: validation step detail, resolved paths (not secrets), query shapes. Off in production unless troubleshooting. |

**Standard libraries (SLF4J/Logback)** map custom names as needed. Prefer a first-class **AUDIT** logger (e.g. logger name `AUDIT` or marker `AUDIT`) so audit lines are easy to filter and ship separately. Map:

| App level | Typical Logback/SLF4J |
|-----------|------------------------|
| AUDIT | Separate logger `AUDIT` at INFO (or custom), always enabled when threshold ≤ AUDIT |
| FATAL | `ERROR` with marker FATAL, or level ERROR + `fatal=true` field (JVM has no FATAL in SLF4J; treat as highest ERROR class) |
| ERROR | `ERROR` |
| WARNING | `WARN` |
| INFO | `INFO` |
| DEBUG | `DEBUG` |

Document the mapping in README so operators are not confused.

### 8.2 Threshold at service start (filter)

- Config key: `LOG_LEVEL` or `app.logging.level` (env / `application.yml`).
- **Read once at application startup** (and logged at INFO: “Effective log level = ERROR”).
- **MVP: no runtime level change** without restart (keeps behaviour predictable). Optional later: actuator endpoint.
- **Rule:** only events with priority **≥ configured level** are written to the log store (file and/or console).

**Examples:**

| Configured level | Stored levels |
|------------------|---------------|
| `AUDIT` | AUDIT only (if you allow “audit-only”; see note) |
| `FATAL` | AUDIT, FATAL |
| `ERROR` | AUDIT, FATAL, ERROR |
| `WARNING` | AUDIT, FATAL, ERROR, WARNING |
| `INFO` | AUDIT … INFO (includes API timings) |
| `DEBUG` | All levels |

**Note on AUDIT:** Audit events are **priority 1**. They are stored whenever the threshold is `AUDIT` or any lower priority threshold (`FATAL` … `DEBUG`).  
If configured level is `ERROR`, stored set = **AUDIT + FATAL + ERROR** (not WARNING/INFO/DEBUG).  
**API duration lines are INFO** — they appear only when level is `INFO` or `DEBUG`.

Default recommended: **`INFO`** for normal self-host; **`WARNING`** or **`ERROR`** if logs must stay small.

### 8.3 Performance metrics (high level, INFO)

Every REST API/endpoint invocation records **one summary line at INFO** when INFO is enabled:

| Field | Example |
|-------|---------|
| `type` | `PERF` or `http_request` |
| `method` | `GET`, `POST`, … |
| `path` | `/api/prompts/{id}` (normalized template, not raw ids if preferred) or full path |
| `status` | `200` |
| `durationMs` | `47` |
| `correlationId` | UUID per request |
| `userId` / subject | If authenticated (no secrets) |

**Implementation:** servlet filter / Spring `OncePerRequestFilter` or `HandlerInterceptor`:

1. `start = nanoTime()` at request entry  
2. On completion (success or error): `durationMs = (nanoTime()-start)/1e6`  
3. Log at **INFO**: e.g. `PERF method=POST path=/api/prompts/x/media status=201 durationMs=128 correlationId=…`  

**Scope:** high-level **per HTTP call only** (not per Mongo query in MVP). Optional WARNING if `durationMs > PERF_SLOW_MS` (e.g. 2000).

Do **not** log request bodies, Authorization headers, or file bytes.

### 8.4 Log destination & format

| Item | MVP choice |
|------|------------|
| Output | Console + rolling file under `./logs` or `LOG_DIR` (Windows-friendly path) |
| Format | Structured **JSON lines** preferred (level, timestamp, message, correlationId, logger); plain text acceptable for MVP |
| Retention | Rolling by size/day (e.g. 10 MB × 7 files) — ops detail in README |
| Secrets | Never log tokens, OAuth secrets, full auth cookies, or raw multipart payloads |

### 8.5 Correlation ID

- Accept incoming `X-Correlation-Id` or generate UUID.  
- Echo on response header `X-Correlation-Id`.  
- Include in all log lines for that request and in error JSON `correlationId` so UI/support can match logs without stack traces.

### 8.6 Error handling (API → UI)

**Goal:** UI never shows raw Java exceptions, class names, or stack traces.

| Layer | Responsibility |
|-------|----------------|
| Domain / validation | Throw typed exceptions: `NotFoundException`, `ForbiddenException`, `ValidationException`, `UnsupportedMediaException`, `QuotaExceededException`, … |
| `@ControllerAdvice` / global handler | Map to HTTP status + **stable JSON body**; log server-side |
| Unexpected `Exception` | Log at **ERROR** with full stack + correlationId; respond **500** with generic message |
| Frontend | Display `message` (and optional `code`); show correlationId on 500 for support; never render `stackTrace` |

**Standard error response body (only these fields to the client):**

```json
{
  "code": "QUOTA_EXCEEDED",
  "message": "Storage quota of 50 MB would be exceeded.",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "details": null
}
```

| Field | Client visibility | Notes |
|-------|-------------------|--------|
| `code` | Yes | Machine-readable; FE can branch |
| `message` | Yes | Safe, human-readable; **no** internal paths, SQL, or exception type names |
| `correlationId` | Yes | Support / log lookup |
| `details` | Optional | Field-level validation only (e.g. `{ "field": "title", "issue": "required" }`) — never stack traces |
| Stack trace / `exception` / `cause` | **Never** | Logs only |

**HTTP mapping (examples):**

| Situation | Status | code (example) | Log level |
|-----------|--------|----------------|-----------|
| Bad input / validation | 400 | `VALIDATION_ERROR` | WARNING or INFO |
| Unauthorized | 401 | `UNAUTHORIZED` | AUDIT (failed auth) |
| Forbidden / not allowlisted | 403 | `FORBIDDEN` | AUDIT |
| Not found | 404 | `NOT_FOUND` | INFO or WARNING |
| Unsupported media | 415 | `UNSUPPORTED_MEDIA_TYPE` | WARNING |
| Too large / quota | 413 | `FILE_TOO_LARGE` / `QUOTA_EXCEEDED` | WARNING |
| Unexpected server fault | 500 | `INTERNAL_ERROR` | ERROR (+ stack in log) |
| Fatal startup | process exit | — | FATAL |

**Generic 500 message (fixed copy):**  
`"An unexpected error occurred. If this continues, contact support with reference id: {correlationId}."`

**Spring:** disable default Whitelabel stack pages for APIs; `server.error.include-stacktrace=never`, `include-message=never` (we use our own body).

### 8.7 AUDIT events (minimum set)

Log at **AUDIT** (structured fields: action, actor, resourceType, resourceId, outcome, correlationId):

- `AUTH_LOGIN_SUCCESS` / `AUTH_LOGIN_FAILURE` / `AUTH_LOGOUT` / `AUTH_ALLOWLIST_DENY`  
- `PROMPT_CREATE` / `PROMPT_UPDATE` / `PROMPT_ARCHIVE` / `PROMPT_DELETE`  
- `VERSION_RESTORE`  
- `MEDIA_UPLOAD` / `MEDIA_REPLACE` / `MEDIA_DELETE`  
- `STORAGE_QUOTA_WARNING` (optional; can be WARNING instead)  
- `RETENTION_PURGE` (counts deleted)

### 8.8 Config

```yaml
app:
  logging:
    level: ${LOG_LEVEL:INFO}   # AUDIT | FATAL | ERROR | WARNING | INFO | DEBUG
    dir: ${LOG_DIR:./logs}
  perf:
    slow-ms: ${PERF_SLOW_MS:2000}  # optional WARNING if slower
```

Startup sequence:

1. Load `LOG_LEVEL`  
2. Configure logging backend filter  
3. Log FATAL and exit if Mongo unreachable / upload dir not writable  
4. Log INFO: effective level, bind address, upload root (absolute path)

---

## 9. Retention (2 years)

- Field: `Prompt.lastModifiedAt` (and cascade consideration for orphaned media via prompt).
- Job: Spring `@Scheduled` inside the API process, **or** CLI entrypoint e.g. `java -jar app.jar --purge-stale`, triggered by **Windows Task Scheduler** (daily). No container/cron sidecar.
- Logic: hard-delete (or permanently purge soft-deleted) prompts where `lastModifiedAt < now() - 2 years` and optionally already soft-deleted; delete media files from disk.
- Log count deleted at AUDIT/INFO.
- Document in Settings: “Unmodified prompts and their images may be deleted after 2 years. You are responsible for your content.”

---

## 10. API surface (MVP)

| Method | Path | Purpose |
|--------|------|---------|
| GET/POST | `/api/auth/*` or Spring OAuth2 login/callback paths | Auth |
| GET | `/api/prompts` | List/search/filter/sort |
| POST | `/api/prompts` | Create + version 1 |
| GET | `/api/prompts/:id` | Detail + media + latest text |
| PATCH | `/api/prompts/:id` | Save metadata + optional text (auto version) |
| POST | `/api/prompts/:id/archive` | Archive |
| DELETE | `/api/prompts/:id` | Soft delete |
| GET | `/api/prompts/:id/versions` | History |
| GET | `/api/prompts/:id/versions/:n` | One snapshot |
| POST | `/api/prompts/:id/versions/:n/restore` | Restore as new version |
| POST | `/api/prompts/:id/media` | Upload one or more result images **for this prompt only** (prompt must exist) |
| PUT | `/api/prompts/:id/media/:mediaId` | Replace image on this prompt |
| DELETE | `/api/prompts/:id/media/:mediaId` | Remove image from this prompt |
| GET | `/api/prompts/:id/media/:mediaId` | Authenticated file bytes (verify media.promptId === id) |

**Forbidden:** any endpoint that creates media without `promptId`.
| GET | `/api/storage` | Quota usage |

Search: MongoDB text/regex on title/description/latest body; optional text index later.

---

## 11. UI pages

1. **Login** — Google / Facebook buttons  
2. **Library** — grid/list, filters, search, quota bar  
3. **New / Edit prompt** — title, status, tags, media type focus, body, negative, params, image dropzone, **Save**  
4. **Detail** — copy buttons, gallery, version list, restore, archive  
5. **Compare** — pick two version numbers, text diff  
6. **Settings** — account, quota, retention disclaimer  

---

## 12. Environment variables

```bash
AUTH_SECRET=
AUTH_GOOGLE_ID=
AUTH_GOOGLE_SECRET=
AUTH_FACEBOOK_ID=
AUTH_FACEBOOK_SECRET=
ALLOWED_EMAILS=you@example.com
MONGODB_URI=mongodb://localhost:27017/promptportal
UPLOAD_DIR=./data/uploads
MAX_UPLOAD_BYTES=2097152
QUOTA_BYTES=52428800
APP_URL=http://localhost:3000
LOG_LEVEL=INFO
LOG_DIR=./logs
PERF_SLOW_MS=2000
```

---

## 13. Native self-host (no Docker)

Docker is **out of scope**. The backend machine runs ordinary Windows (or Linux) processes.

### 13.1 Process layout

```text
MongoDB Windows Service     →  localhost:27017
Java Spring Boot (jar)      →  localhost:8080   (REST API)
Optional: frontend static
  or Node/Next dev server   →  localhost:3000   (calls API via REST)
Disk:
  C:\prompt-portal-data\uploads\
  C:\prompt-portal-data\logs\
  (Mongo data: MongoDB default data directory)
```

### 13.2 Run (Windows sketch)

```powershell
# MongoDB already installed as a service (auto-start)
# Set env vars or use application.yml / .env loaded by the app

java -jar prompt-portal-api.jar
# or: mvn spring-boot:run
```

Optional: register the jar as a **Windows Service** (e.g. WinSW, NSSM) so it starts on boot.

### 13.3 Persist & backup (no volumes)

| Data | Location | Backup |
|------|----------|--------|
| Images | `UPLOAD_DIR` | Copy folder |
| Logs | `LOG_DIR` | Optional; rolling files |
| MongoDB | Mongo data path | `mongodump` / Compass export |

### 13.4 Retention schedule

Windows Task Scheduler → daily →  
`java -jar prompt-portal-api.jar --purge-stale`  
(or rely on in-process `@Scheduled` while the API is running).

### 13.5 README must document

- Install JDK, Maven, MongoDB (no Docker steps)
- Create upload/log folders
- Env vars / OAuth redirect URIs for `http://localhost:8080`
- Start order: MongoDB → API → frontend
- Backup: `mongodump` + copy `uploads`

---

## 14. Build plan (implementation order)

| Step | Deliverable |
|------|-------------|
| 1 | Scaffold Java (Spring Boot) + MongoDB + frontend REST client layout |
| 2 | Logging framework: levels AUDIT→DEBUG, startup threshold, rolling files |
| 3 | Global exception handler + safe error JSON (no stack traces to UI) |
| 4 | HTTP PERF filter (durationMs at INFO) + correlation id |
| 5 | OAuth Google + Facebook + allowlist + AUDIT auth events |
| 6 | Prompt CRUD + auto version on save + draft status |
| 7 | Version list, detail, diff, restore |
| 8 | Image upload/replace/delete + validation + quota + 90% warning |
| 9 | Library search/filter + copy text |
| 10 | Retention purge (in-app schedule and/or CLI) + Settings copy |
| 11 | **Windows native runbook** + README (install, env, OAuth, backup data + logs) — **no Docker** |

---

## 15. Testing checklist

- [ ] Non-allowlisted OAuth user denied  
- [ ] Create text-only prompt → version 1  
- [ ] Save text change → version 2; metadata-only → still version 2  
- [ ] Draft status persists  
- [ ] Upload 2 MB+1 byte → rejected (FE + BE)  
- [ ] Fill near 50 MB → warning ≥ 90%; over quota rejected  
- [ ] Upload without prompt id → rejected  
- [ ] One prompt can hold multiple images  
- [ ] Delete prompt removes all its images from DB and disk  
- [ ] Replace image → no new version  
- [ ] Restore v1 → creates v3 (example) with v1 text  
- [ ] Diff shows text differences  
- [ ] Video MIME / non-image types rejected (FE + BE)  
- [ ] Fake image (wrong magic / HTML as .png / exe as .jpg) rejected on backend  
- [ ] Extension vs magic mismatch rejected  
- [ ] Soft-deleted / archived hidden from default list  
- [ ] Purge dry-run identifies 2-year-old unmodified prompts  
- [ ] Served media has nosniff + correct Content-Type; no static upload root  
- [ ] `LOG_LEVEL=ERROR` → INFO/DEBUG/WARNING lines not written; AUDIT + FATAL + ERROR are  
- [ ] Each API call logs PERF duration at INFO when level is INFO  
- [ ] Forced 500 returns JSON with code/message/correlationId only — no stack trace in body  
- [ ] UI shows friendly message, not Java exception text  
- [ ] Audit line written on login deny and media upload  

---

## 16. Out of scope (do not build in MVP)

- Video files, collections, share links, multi-user, S3, generator APIs, branching, Facebook/Google beyond login.
- Runtime log-level change without restart (MVP: startup only).
- Per-query DB timing metrics (MVP: per-HTTP-endpoint only).
- **Docker / Docker Compose / Kubernetes** — deploy as native OS processes only.

---

*Ready to scaffold when you say go.*
