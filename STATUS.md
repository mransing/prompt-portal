# Prompt Portal — Session Status

**Last updated:** 2026-09-17  
**Status:** Local stack running (Mongo + API + UI). Dev login works.  
**Local start:** `deploy\windows\start-all.ps1` (Mongo + API + UI). Stop with `stop-all.ps1`.  
**Dev login email:** `dev@local.test` (pre-filled on the Login page). Any email works while `ALLOWED_EMAILS` is empty.

---

## Project root

`C:\Users\HomePC\Documents\prompt-portal\`

Git: branch **`main`**, initial commit **`21b3718`**  
Message: *Initial Prompt Portal repository: Spring Boot API, Next.js UI, deploy scripts, Eclipse project*

---

## What exists

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot 3.4 API (MongoDB, REST, media, versioning, logging, retention) |
| `frontend/` | Next.js 15 UI (login, library, new/edit/detail, compare, settings) |
| `deploy/windows/` | Build/start/purge scripts only (no auto-deploy) |
| `REQUIREMENTS.md`, `TECHNICAL_DESIGN.md` | Locked product + design |
| `mockups/` | HTML/PNG mockups |
| `README.md`, `.env.example` | How to configure and run |
| Eclipse | `backend/.project`, `.classpath`, `.settings/`, `prompt-portal-api.launch` |

**Verified on this machine earlier:**

- `mvn test package` OK → `backend\target\prompt-portal-api-0.1.0-SNAPSHOT.jar`
- `npm install` OK under `frontend/`
- JDK 26, Maven 3.9.16, MongoDB 8.3 service, Git, Node on PATH (+ `JAVA_HOME`)

---

## Locked stack (do not re-debate)

| Layer | Choice |
|-------|--------|
| Frontend | React / Next.js — REST client only |
| Backend | Java Spring Boot REST |
| DB | MongoDB (native Windows service) |
| Files | Local disk `UPLOAD_DIR` |
| Auth | OAuth Google/Facebook **or** dev header auth |
| Deploy | Native processes only — **Docker not in stack** |

---

## Auth / secrets (where keys go)

**Do not commit secrets.** Set **environment variables** on the API process:

| Variable | Purpose |
|----------|---------|
| `AUTH_GOOGLE_ID` / `AUTH_GOOGLE_SECRET` | Google OAuth client |
| `AUTH_FACEBOOK_ID` / `AUTH_FACEBOOK_SECRET` | Facebook app |
| `ALLOWED_EMAILS` | Comma-separated allowlist |
| `AUTH_SECRET` | App secret (random string) |

Template: **`.env.example`** (root). Wiring: **`backend/src/main/resources/application-oauth.yml`** (profile **`oauth`**).

Redirect URIs (developer consoles → API, not frontend):

- Google: `http://localhost:8080/login/oauth2/code/google`
- Facebook: `http://localhost:8080/login/oauth2/code/facebook`

Without profile `oauth`, default is **dev mode** (`X-Dev-User-Email` / login page email).

---

## Done this session

- [x] Prerequisites verified; PATH / `JAVA_HOME` set  
- [x] Full monorepo scaffolded  
- [x] Backend + frontend code  
- [x] Eclipse Maven project files  
- [x] Deploy scripts (build/start only)  
- [x] Git init + initial commit  
- [x] Backend build + frontend `npm install` verified  
- [x] Fixed `dev@local.test` login: duplicate Mongo `users` rows made `findByEmailIgnoreCase` throw; filter now takes the oldest match and returns JSON on failure  
- [x] Smoke test: API `/api/auth/me` with `X-Dev-User-Email:  dev@local.test` → 200; created prompt “Smoke test prompt” → 201  

## Not done yet

- [ ] Browser smoke test: open http://localhost:3000, log in, upload an image on the new prompt  
- [ ] Real OAuth keys + `oauth` profile  
- [ ] Windows Service / Task Scheduler for API + purge  
- [ ] Production hardening (HTTPS, backups schedule, secrets store)  

---

## How to resume in Grok

From any directory, say:

> Resume Prompt Portal. Read `Documents/prompt-portal/STATUS.md` and continue from there.

Or:

> Continue Prompt Portal. I reviewed the code — next do [smoke test | OAuth | …].

---

## How to open / run later (when ready)

**Eclipse:** Import → Maven → Existing Maven Projects → `backend/`  
Run `PromptPortalApplication` or `prompt-portal-api.launch`.

**One-shot local run (recommended):**

```powershell
cd C:\Users\HomePC\Documents\prompt-portal
.\deploy\windows\start-all.ps1
# stop API + UI later:
.\deploy\windows\stop-all.ps1
```

**Manual local run (not production deploy):**

```powershell
cd C:\Users\HomePC\Documents\prompt-portal
. .\deploy\windows\env.sample.ps1
.\deploy\windows\start-api.ps1
# other terminal:
.\deploy\windows\start-frontend.ps1
```

- API: http://localhost:8080  
- UI: http://localhost:3000  
- Start order: **MongoDB service → API → frontend**

---

## Process picture

```text
MongoDB service     → localhost:27017
Java API            → localhost:8080  (REST)
Frontend (optional) → localhost:3000
Disk                → C:\prompt-portal-data\uploads (or relative data/uploads)
Logs                → C:\prompt-portal-data\logs (or relative logs/)
```

---

*Safe to close Grok. All work is on disk under `Documents\prompt-portal\`. No app was left running as a deployed service.*
