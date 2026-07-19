# Prompt Version Control Portal — Requirements Document

**Document type:** Product & System Requirements  
**Status:** Decisions locked — ready for design/build  
**Date:** 2026-07-18  
**Primary use case:** Version-controlled storage and management of generative AI prompts for **images** and **video-oriented text prompts**, with optional result **images**. Every image is owned by the prompt that created it; one prompt may own many images.

---

## 1. Purpose

Build a **self-hosted** web portal where a single user can:

- Create, edit, and **version-control** prompt text (and related text fields).
- Attach **images produced by a prompt** to that prompt (media is **not** versioned).
- Tag prompts as image- or video-oriented (text/metadata only — **no video file upload**).
- Browse history of how prompt text evolved.
- Find and reuse past prompts and the media they created.

The system treats prompts as first-class artifacts (like source code), not disposable chat text.

### 1.1 Core domain invariant (locked)

| Rule | Meaning |
|------|---------|
| **Prompt → Media is 1:N** | One prompt can be linked to **multiple** images. |
| **Every media has exactly one prompt** | An image **cannot** exist without a parent prompt. There is no standalone media library. |
| **Prompt created the media** | The image is a **result** of that prompt (the text used to generate it). Upload always happens **in the context of a prompt**. |
| **Prompt without media is allowed** | A prompt may have zero images (text-only / not yet generated). |
| **Delete prompt → delete its media** | Media is never left orphaned. |

---

## 2. Goals and non-goals

### 2.1 Goals

| ID | Goal |
|----|------|
| G1 | Never lose a useful prompt iteration; every text change that is saved is recoverable via versions. |
| G2 | Every image is stored under the prompt that **created** it; one prompt may have many result images. |
| G3 | Support prompts **with** and **without** images. |
| G4 | Make discovery easy (search, tags, filters; collections optional later). |
| G5 | Provide a simple, browser-based UI suitable for creative workflows. |
| G6 | Run self-hosted with modest storage (50 MB image quota). |

### 2.2 Non-goals (v1)

| ID | Non-goal |
|----|----------|
| NG1 | Generating images/videos inside the portal. |
| NG2 | Full Git hosting (branches/PRs); v1 is linear version history of **prompt text**. |
| NG3 | Real-time multi-user collaborative editing. |
| NG4 | Mobile-native apps (responsive web is enough). |
| NG5 | Video file upload, transcoding, or in-browser video playback of user videos. |
| NG6 | Public share links or multi-tenant SaaS. |
| NG7 | Integrations with Discord, Drive, generators, etc. |
| NG8 | Versioning of media assets (replace in place only). |

---

## 3. Users and roles

| Role | Description | Typical actions |
|------|-------------|-----------------|
| **Author** | The single authenticated user | Create prompt, upload/replace images, save drafts/versions, search, organize |

**v1 assumption:** **Single user** only. Authentication via **Google or Facebook** OAuth. Other auth methods later. No Viewer/Admin roles in v1.

**Access control:** Only the authenticated owner can access the library. Unauthenticated requests are denied.

---

## 4. Core concepts

### 4.1 Prompt (document)

A **Prompt** is a named library document. It has **mutable metadata**, **current media** (not versioned), and a chain of **text versions**.

| Field | Required | Description |
|-------|----------|-------------|
| Title | Yes | Human-readable name |
| Description / notes | No | Free-text context (intent, style notes) |
| Current content | Yes | Latest prompt text (from latest version, or draft buffer — see §6) |
| Media type focus | Yes | `image` \| `video` \| `mixed` \| `text-only` — **tag only**; does not enable video upload |
| Tags | No | Labels for discovery |
| Status | Yes | `draft` \| `active` \| `archived` |
| Owner | Yes | Authenticated user |
| Media attachments | No | Zero or more **result images** created by this prompt (1:N; current set only, not versioned) |
| Created / updated timestamps | Yes | System-managed |
| Last modified at | Yes | Used for 2-year retention of unmodified data |

### 4.2 Prompt version (text snapshot only)

Each **Save** that changes versioned text fields creates an immutable snapshot (no separate “Commit” action).

| Field | Required | Description |
|-------|----------|-------------|
| Version number | Yes | Simple integers: **1, 2, 3, …** |
| Prompt text | Yes | Full text at this version |
| Change summary | No | Optional short message (if UI collects it) |
| Negative prompt | No | Optional |
| Model / tool metadata | No | e.g. model name, seed, steps, CFG, aspect ratio |
| Parameters (JSON) | No | Extensible tool-specific settings |
| Author | Yes | Who saved this version |
| Created at | Yes | Timestamp |
| Parent version | Yes (except first) | Previous version id |

**Not stored on a version:** media files or media links (media is prompt-level only).

**Rule:** Versions are **immutable**. Corrections create a new version number.

### 4.3 Media asset (always owned by a prompt; not versioned)

An uploaded **image** that was **created by** a prompt. Media is a child of the prompt document, not of a historical text version.

| Field | Required | Description |
|-------|----------|-------------|
| File | Yes | Image binary on local disk (self-hosted) |
| MIME type / kind | Yes | `image` only in v1 |
| Parent prompt | **Yes (mandatory)** | The prompt that created this image; never null |
| Role | No (default `output`) | Default: result of the prompt |
| Caption / alt text | No | Optional note |
| Dimensions | No | Extracted when possible |
| Checksum | Yes | Integrity / optional dedup |
| Uploaded by / at | Yes | Audit |

**Media mutation rules (locked):**

1. **Every image must belong to exactly one prompt** — enforced in API and DB (`promptId` NOT NULL, FK).
2. Images are uploaded **only** against an existing prompt (or in create-prompt flow: create prompt first, then attach media).
3. **One prompt → many images** (multiple results / variations from the same prompt).
4. Media is **not versioned** (text versions do not snapshot images).
5. Updating an image **replaces** that attachment’s file (or user deletes + re-adds).
6. Historical text versions show the **current** media gallery for that prompt, not a past media set.
7. **No orphan media:** deleting a prompt deletes (or soft-deletes then purges) all of its images.

### 4.4 Association rules

1. **Cardinality:** Prompt **1 — N** Media. Media **N — 1** Prompt (**required**).
2. A prompt may have **zero** images (text-only / not yet generated).
3. A prompt may have **many** images (multiple generations from the same prompt).
4. An image **must** have a prompt; upload without a prompt target is **rejected**.
5. Semantic: the linked prompt is the instruction that **created** the image.
6. Tagging media type focus as `video` means the **prompt is for video generation**; it does **not** allow video file upload.
7. Soft-delete / hard-delete of a prompt **cascades** to its media (no orphans).

### 4.5 Collection / project

**Deferred to v1.1** (not required for MVP). Tags + search cover organization initially.

---

## 5. Functional requirements

### 5.1 Authentication and access

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-AUTH-01 | Users must authenticate via **Google OAuth** or **Facebook OAuth** only (v1). | Must |
| FR-AUTH-02 | Unauthenticated access is denied (private library). | Must |
| FR-AUTH-03 | Single-user deployment: only the configured allowed account(s) may sign in (allowlist by email/provider id). | Must |
| FR-AUTH-04 | Session handling with secure cookies; logout supported. | Must |
| FR-AUTH-05 | Public share links are **out of scope**. | — |

### 5.2 Prompt CRUD

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-P-01 | User can create a new prompt with title and body text. | Must |
| FR-P-02 | User can create a prompt **without** any media. | Must |
| FR-P-03 | User can create a prompt **with** one or more images (≤ 2 MB each) at creation time. | Must |
| FR-P-04 | User can edit title, description, tags, status, and media-type focus without creating a new text version. | Must |
| FR-P-05 | User can edit prompt text / negative prompt / parameters and **Save**; if versioned fields changed, system creates version N+1 automatically (**no separate Commit button**). | Must |
| FR-P-06 | User can save work with status **draft** (draft save allowed). | Must |
| FR-P-07 | User can archive a prompt (hidden from default lists). | Must |
| FR-P-08 | User can soft-delete a prompt. | Should |
| FR-P-09 | User can duplicate a prompt as a new prompt starting at version 1. | Should |
| FR-P-10 | User can set optional negative prompt and parameters (JSON or simple form). | Should |

### 5.3 Version control (text only)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-V-01 | Saving changes to versioned text fields creates a new immutable version with next integer number. | Must |
| FR-V-02 | System stores full snapshot of text (and parameters) per version. | Must |
| FR-V-03 | User can view full version history (newest first). | Must |
| FR-V-04 | User can open any historical version and view its text and parameters (**not** historical media). | Must |
| FR-V-05 | User can **compare** two versions (side-by-side or inline text diff). | Must |
| FR-V-06 | User can **restore** a historical version: creates a **new** version with that text content. | Must |
| FR-V-07 | System records author and timestamp for each version. | Must |
| FR-V-08 | No branching in v1. | — |
| FR-V-09 | Export version history as Markdown/plain text (and optionally JSON). | Should |

### 5.4 Media upload and management (images only)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-M-01 | User can upload **one or more images** to a **prompt** (1:N). Each image is a result created by that prompt. | Must |
| FR-M-01a | **Every image must have a parent prompt.** System rejects any upload that is not scoped to a prompt. No orphan / standalone media. | Must |
| FR-M-01b | UI never offers a global “upload image” outside a prompt context (create/edit/detail of a prompt only). | Must |
| FR-M-02 | Video file upload is **not supported**. | — |
| FR-M-03 | Supported formats: **PNG, JPEG, WebP, GIF only**. **Max 2 MB per file.** No other types (SVG, BMP, TIFF, HEIC, PDF, HTML, executables, archives, etc.). | Must |
| FR-M-03a | **Frontend must restrict and pre-validate** uploads: file picker `accept` limited to allowed types; reject by extension, MIME, and size **before** calling the API; clear user error. Frontend checks are UX only — never trusted alone. | Must |
| FR-M-03b | **Backend must independently validate** every upload (create and replace): extension allowlist, declared Content-Type allowlist, **magic-byte / file-signature** check, successful **image decode**, size ≤ 2 MB, and reject polyglot/disallowed content. Invalid files are **not stored**. | Must |
| FR-M-03c | **Do not trust** client-supplied MIME type, extension, or filename alone. Server is source of truth; store only server-detected MIME and a safe generated filename on disk. | Must |
| FR-M-03d | **SVG is forbidden** (can embed script). **GIF** allowed for still/animated images only after signature + decode validation; no HTML/JS sniffing of stored files. | Must |
| FR-M-04 | Images default to role `output` (created by this prompt). Optional role field is not required for MVP. | Should |
| FR-M-05 | UI shows thumbnails; fullscreen image preview on the **prompt** detail page. | Must |
| FR-M-06 | User can replace an existing image attachment (overwrite / replace file) on that prompt. | Must |
| FR-M-07 | User can remove an image from a prompt. | Must |
| FR-M-08 | **Total image storage quota: 50 MB** (text excluded). | Must |
| FR-M-09 | System rejects uploads that would exceed quota or per-file limit. | Must |
| FR-M-10 | System shows storage usage; **warning at ≥ 90%** of 50 MB. | Must |
| FR-M-11 | Drag-and-drop upload (same type/size validation as file picker). | Should |
| FR-M-12 | Thumbnails and basic metadata (width, height) derived only from successfully validated images. | Should |

### 5.5 Browse, search, and organization

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-S-01 | List prompts with title, status, tags, thumbnail (if any), updated time, version count. | Must |
| FR-S-02 | Full-text search over title, description, prompt body. | Must |
| FR-S-03 | Filter by tags, status, media type focus, date range, has-media / no-media. | Must |
| FR-S-04 | Sort by updated, created, title. | Must |
| FR-S-05 | Tag create/autocomplete. | Must |
| FR-S-06 | Collections: deferred to v1.1. | Could |

### 5.6 Detail views and UX

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-UI-01 | Prompt detail: current text, metadata, image gallery, version timeline. | Must |
| FR-UI-02 | One-click **copy prompt text** to clipboard. | Must |
| FR-UI-03 | One-click copy of negative prompt and/or parameters. | Should |
| FR-UI-04 | Desktop-first responsive layout; large editor for prompts. | Must |
| FR-UI-05 | Confirm destructive actions (archive, delete, replace/remove media). | Must |
| FR-UI-06 | Empty states for first-time use. | Should |
| FR-UI-07 | Clear **Save** action (no separate Commit control). | Must |
| FR-UI-08 | Quota meter (used / 50 MB) with 90% warning. | Must |

### 5.7 Import / export

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-IO-01 | Export prompt text as Markdown/plain text. | Must |
| FR-IO-02 | Export prompt + versions as JSON (no media binary required). | Should |
| FR-IO-03 | Zip export with images. | Could |

### 5.8 Retention, audit, and safety

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-A-01 | **Retention:** data not modified for **two years** is eligible for deletion (scheduled purge). | Must |
| FR-A-02 | User is solely responsible for content they upload/store (shown in UI/terms note). | Must |
| FR-A-03 | Soft-delete with optional recovery window before hard purge. | Should |
| FR-A-04 | Activity log for create/update/version/upload/delete. | Should |

---

## 6. Version control behavior (detailed)

### 6.1 Draft save and automatic versioning

**Product decision (locked):** No explicit **Commit** button. **Draft save is allowed.**

| Action | Result |
|--------|--------|
| Save when versioned fields **unchanged** | Update metadata / timestamps only; no new version |
| Save when versioned fields **changed** | Create version **N+1** with full text snapshot; update “current” content |
| Status = `draft` | Same versioning rules; draft is a visibility/status flag, not a separate history system |
| Metadata-only edit (title, tags, status, media type focus) | No new version |
| Add / replace / remove **images** | No new version (media not versioned) |

**Versioned fields:** prompt body text, negative prompt, parameters/model metadata.

### 6.2 Restore semantics

```
Current: version 5
User restores version 2 content
→ System creates version 6 with text identical to version 2
→ History remains: 1…5, 6 (restored from 2)
→ Media gallery unchanged (still current prompt media)
```

### 6.3 Media and versions

- Versions store **text only**.
- Gallery always reflects **current** prompt media.
- Viewing an old version does not restore old images (they were never snapshotted).

---

## 7. Non-functional requirements

| ID | Category | Requirement | Priority |
|----|----------|-------------|----------|
| NFR-01 | Deployment | **Self-hosted** on the user’s machine or server as **native processes** (Java API + MongoDB + local disk). **Docker is not required** and is out of scope for MVP. | Must |
| NFR-01a | Deployment | Install/run documented for **Windows** (primary): JDK, MongoDB service, folders for uploads/logs, optional Windows Service + Task Scheduler for API/purge. | Must |
| NFR-02 | Performance | Prompt list &lt; 2s for 1,000 prompts (pagination). | Must |
| NFR-03 | Storage | Images on **local filesystem** (configured folder; not Docker volumes); metadata in MongoDB. 50 MB image quota. | Must |
| NFR-04 | Security | HTTPS when exposed remotely; OAuth; session security; allowlist. | Must |
| NFR-05 | Security | **Defense in depth for uploads:** frontend restrict + backend allowlist, magic bytes, decode, size limits. Never execute, evaluate, or serve uploads as HTML/script. Serve media only as authenticated binary with correct `Content-Type` and safe headers (`X-Content-Type-Options: nosniff`, avoid inline dangerous types). | Must |
| NFR-05a | Security | Uploaded files stored under non-executable paths; **not** under a static web root that auto-serves by extension. Download only via REST media API. | Must |
| NFR-05b | Security | Reject double extensions / path tricks in original filenames; disk name is server-generated (`{assetId}.ext` from detected type). | Must |
| NFR-06 | Reliability | Soft-delete; backups of **MongoDB** (`mongodump`) **and** the media **folder** recommended in ops notes. | Must |
| NFR-07 | Retention | Job or process to purge unmodified records older than 2 years. | Must |
| NFR-08 | Browser support | Latest Chrome, Edge, Firefox, Safari. | Must |
| NFR-09 | Usability | Keyboard-friendly copy; large editor area. | Should |
| NFR-10 | Observability | **Backend logging** with levels in priority order: **AUDIT, FATAL, ERROR, WARNING, INFO, DEBUG**. | Must |
| NFR-10a | Observability | **Log level is read at service startup.** Only events at the configured level **and higher priority** are stored. Example: level `ERROR` → store **AUDIT, FATAL, ERROR** only. | Must |
| NFR-10b | Observability | **API performance metrics** at high level: duration of each REST call/endpoint logged at **INFO** (method, path, status, durationMs, correlation id). | Must |
| NFR-10c | Observability | **AUDIT** logs for security-relevant actions (auth success/failure/allowlist deny, prompt/media mutations, purge). | Must |
| NFR-11 | Reliability | **Errors handled properly** on the backend: typed failures → stable HTTP status + JSON `{ code, message, correlationId }`. | Must |
| NFR-11a | Security / UX | **Never expose Java exceptions, class names, or stack traces to the UI** (or any API client). Stack traces only in server logs at ERROR (with correlation id). Generic message for unexpected 500s. | Must |
| NFR-11b | UX | Frontend displays API `message` / `code` only; on 500 may show correlation id for support. | Must |

---

## 8. Data model (logical)

```
User (single allowlisted identity via Google/Facebook)
  └── owns Prompt[]
        ├── metadata (title, tags, status, media_type_focus, notes, …)
        ├── MediaAsset[]     ← 0..N result images; each MUST have this prompt (FK required)
        └── PromptVersion[]  ← immutable text snapshots (1, 2, 3, …)
              └── text, negative_prompt, parameters, created_at

Invariant: MediaAsset.promptId is always set. No MediaAsset without Prompt.
```

---

## 9. Primary user flows

### 9.1 Create text-only prompt

1. **New prompt** → title + text → optional tags / media type focus.
2. **Save** → Prompt + version **1**.
3. Land on detail page.

### 9.2 Create prompt with images

1. **New prompt** → title + text.
2. Upload one or more result images (each ≤ 2 MB); each is owned by this prompt; quota check.
3. **Save** → Prompt + version 1 + media children (1:N).

### 9.3 Iterate on prompt text

1. Open prompt → edit text/parameters.
2. **Save** → if text changed, version N+1 (no Commit step).
3. History + diff available.

### 9.4 Replace image (no version bump)

1. Open prompt → replace or remove image.
2. Save/apply media change → gallery updates; version number unchanged.

### 9.5 Find and reuse

1. Search/filter → open → copy text / view images.
2. Optionally restore older **text** version.

---

## 10. UI pages (v1 information architecture)

| Page | Purpose |
|------|---------|
| Login | Google / Facebook OAuth |
| Prompt library | Search, filter, list/grid, quota meter |
| Prompt detail | Text, gallery, actions, version timeline |
| Version detail / compare | Snapshot text view and diff |
| Create / edit prompt | Form + image upload + **Save** |
| Settings | Allowed account info, storage usage, retention note |

---

## 11. API capabilities (logical)

- Auth (OAuth callback, session, logout)
- CRUD prompts; list/search/filter
- Save prompt → automatic version creation when text fields change
- List/compare/restore versions
- Upload/replace/delete images with quota enforcement
- Export text/JSON
- Storage usage endpoint
- Retention purge (admin/cron, local job)

---

## 12. Constraints and assumptions

1. No model inference inside the portal.
2. Prompts may come from any external tool; parameters stay flexible (JSON + free text).
3. **50 MB** total image storage; **2 MB** max per image.
4. Self-hosted; single user; Google/Facebook only.
5. User is solely responsible for stored content.
6. Unmodified data purged after **2 years**.

---

## 13. Acceptance criteria (MVP)

MVP is done when the user can:

1. Sign in with **Google or Facebook** (allowlisted single user).
2. Create a prompt **without** media.
3. Create a prompt **with** one or more result images (≤ 2 MB each, within 50 MB quota), all owned by that prompt.
3a. Cannot create or keep an image without a parent prompt; one prompt can hold multiple images.
3b. Only PNG/JPEG/WebP/GIF accepted; FE blocks bad types/sizes; BE rejects wrong magic bytes / non-decodable “images”; SVG and executables never stored.
4. Edit prompt text and **Save** → new integer version created automatically.
5. Save **draft** status prompts.
6. View history and **diff** two versions.
7. **Restore** an old version as a new version (text only).
8. Replace/remove images **without** creating a new version.
9. Search/filter (including has-media / text-only; media type focus including `video` tag).
10. Copy prompt text to clipboard.
11. See quota usage and a warning at ≥ 90%.
12. Archive a prompt.
13. Reject video uploads cleanly (images only).

---

## 14. Phased delivery

| Phase | Scope |
|-------|--------|
| **MVP** | Self-host, Google/Facebook auth, prompts, auto-version on save, drafts, image upload/replace (not versioned), quota 50 MB / 2 MB, history, diff, restore, search, tags, copy, 2-year retention job |
| **v1.1** | Collections, export zip/JSON polish, more OAuth providers, optional second user |
| **v2** | Teams, public links, video files (if ever needed), branching, generator integrations |

---

## 15. Open questions

**All resolved.** See §15.1.

### 15.1 Decisions (answered)

| # | Decision | Notes |
|---|----------|--------|
| Q1 | **Single user**; auth via **Google or Facebook only** | Other auth methods in later versions |
| Q2 | **Simple numbering: 1, 2, 3, …** | Not semver |
| Q3 | **No explicit Commit**; **draft save allowed** | Save creates next version when versioned fields change |
| Q4 | **No video files**; user may **tag** prompt as video | Avoid codec/bandwidth complexity |
| Q5 | **Max 2 MB / image**; **50 MB total** image quota; **warn at 90%** | Text not counted toward quota |
| Q6 | **Media is not versioned** | New media = attach; update = replace previous |
| Q7 | **No public sharing** | Private self-hosted library only |
| Q8 | **No third-party app integrations** | — |
| Q9 | **Self-hosted** as **native processes** (Java API + MongoDB + local disk folders) | **Docker is not in the stack** (no containers/Compose for MVP) |
| Q10 | **Unmodified data deleted after 2 years**; user solely responsible for content | Retention purge required |
| Q11 | **Every image must have a prompt** that created it; one prompt → many images | No orphan media; cascade on prompt delete |
| Q12 | **Backend: Java + MongoDB**; **frontend: React/Next**; client↔server **REST only** | Supersedes Node/SQLite monolith for backend |

---

## 16. Glossary

| Term | Definition |
|------|------------|
| Prompt | Library document: metadata + current images + versioned text history |
| Version | Immutable integer-numbered snapshot of prompt **text** (and params) |
| Media asset | Image **created by** a prompt; always has exactly one parent prompt; not historical |
| Draft | Prompt status; work can be saved as draft without a separate commit flow |
| Restore | Create a new text version matching an older version’s text |
| Quota | 50 MB total for images; 2 MB per file |
| 1:N ownership | One prompt → many images; image cannot exist without its creating prompt |

---

## 17. Success metrics (post-launch)

- % of prompts with ≥ 2 versions
- % of prompts with at least one image
- Median time to find and copy a past prompt
- Storage usage vs 50 MB cap
- Restore actions used

---

*End of requirements — decisions locked.*
