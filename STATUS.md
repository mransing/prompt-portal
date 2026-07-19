# Prompt Portal — Session Status

**Last updated:** 2026-07-19  
**Status:** **Repository scaffolded.** Implementation code present. **Not deployed / not running as a service.**

---

## Toolchain (verified earlier)

| Tool | Status |
|------|--------|
| JDK | Oracle 26.0.1 (`JAVA_HOME` set) |
| Maven | 3.9.16 on user PATH |
| MongoDB | 8.3.4 service Running :27017 |
| Git | 2.55.0 |
| Node / npm | 26.5.0 / 11.17.0 |
| Eclipse | Installed by user — backend has `.project` / m2e metadata |

---

## Repository structure

```text
Documents/prompt-portal/
  backend/                 Spring Boot 3.4 API + Eclipse Maven project
  frontend/                Next.js 15 TypeScript UI
  deploy/windows/          Build/start/purge scripts (manual; not auto-deploy)
  data/uploads/, logs/     Default relative storage
  REQUIREMENTS.md, TECHNICAL_DESIGN.md, mockups/
  README.md, .env.example, .gitignore
```

---

## Locked stack

| Layer | Choice |
|-------|--------|
| Frontend | React / Next.js — REST client |
| Backend | Java Spring Boot REST |
| DB | MongoDB |
| Files | Local disk `UPLOAD_DIR` |
| Auth | OAuth profile optional; **dev mode** header auth by default |
| Deploy | Native Windows processes — **not deployed yet** |
| Docker | Not in stack |

---

## Done

- [x] Requirements + technical design + mockups  
- [x] Host tooling install + PATH  
- [x] Backend domain, repos, services, REST controllers  
- [x] Image validation (magic + decode), quota, media scoped to prompts  
- [x] Versioning, restore, compare API  
- [x] Logging (AUDIT/PERF), correlation id, safe error JSON  
- [x] Retention service + CLI `--purge-stale`  
- [x] Frontend pages: login, library, new/edit/detail, compare, settings  
- [x] Eclipse project files + run launch config  
- [x] Deploy scripts (build/start only; no service install)  

## Not done / later

- [ ] Run end-to-end smoke test on this machine  
- [ ] Configure real Google/Facebook OAuth (`oauth` profile)  
- [ ] Windows Service / Task Scheduler for API + purge  
- [ ] Production hardening (HTTPS, secrets, backups schedule)  
- [ ] Broader automated tests  

---

## How to open in Eclipse

**Import → Maven → Existing Maven Projects** → select `backend/`.  
Run `com.promptportal.PromptPortalApplication` or `prompt-portal-api.launch`.

## How to run locally (when ready)

```powershell
cd C:\Users\HomePC\Documents\prompt-portal
. .\deploy\windows\env.sample.ps1
.\deploy\windows\start-api.ps1
# other terminal:
.\deploy\windows\start-frontend.ps1
```

---

*Code is on disk under `Documents\prompt-portal\`. Do not treat as production-deployed.*
