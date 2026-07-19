# Prompt Portal

Self-hosted **prompt version control** for generative **image / video-oriented** text prompts, with result **images** owned by each prompt (1:N).  
Stack: **Java Spring Boot** + **MongoDB** + **Next.js** (REST only). **No Docker.**

Planning docs: `REQUIREMENTS.md`, `TECHNICAL_DESIGN.md`, `STATUS.md`, `mockups/`.

## Repository layout

```text
prompt-portal/
  backend/          Spring Boot API (Eclipse/Maven project)
  frontend/         Next.js UI
  deploy/windows/   Build & start scripts (native; not auto-deployed)
  data/uploads/     Default relative upload dir (or set UPLOAD_DIR)
  logs/             Default relative log dir (or set LOG_DIR)
  mockups/          HTML/PNG UI mockups
```

## Prerequisites (this machine)

| Tool | Role |
|------|------|
| JDK 21+ (you have 26) | API compile/run (`--release 21`) |
| Maven 3.9+ | Build API |
| MongoDB (service) | Database |
| Node.js + npm | Frontend |
| Git | Source control |
| Eclipse (optional) | Open `backend` as Maven project |

## Eclipse

1. **File → Import → Maven → Existing Maven Projects**
2. Root: `Documents\prompt-portal\backend`
3. Finish (m2e resolves dependencies)
4. Run configuration: open `prompt-portal-api.launch` or run `com.promptportal.PromptPortalApplication` with VM arg `-Dspring.profiles.active=dev`
5. Ensure **JavaSE-21** execution environment exists (JDK 26 works if registered as a JRE and compliance is 21)

Project metadata included: `.project`, `.classpath`, `.settings/`, `prompt-portal-api.launch`.

## Quick local run (not production deploy)

```powershell
# Terminal 0 — env + folders
cd C:\Users\HomePC\Documents\prompt-portal
. .\deploy\windows\env.sample.ps1

# Terminal 1 — API
.\deploy\windows\start-api.ps1

# Terminal 2 — UI
.\deploy\windows\start-frontend.ps1
```

- API: http://localhost:8080  
- UI: http://localhost:3000  
- Health: http://localhost:8080/actuator/health  

Dev login: use the Login page email form (`X-Dev-User-Email`).

## Build only

```powershell
.\deploy\windows\build-all.ps1
# or
cd backend; mvn test package
cd ..\frontend; npm.cmd install; npm.cmd run build
```

## Main API surface

| Method | Path |
|--------|------|
| GET | `/api/auth/me`, `/api/auth/providers` |
| GET/POST | `/api/prompts` |
| GET/PATCH/DELETE | `/api/prompts/{id}` |
| POST | `/api/prompts/{id}/archive` |
| GET | `/api/prompts/{id}/versions`, `.../versions/{n}` |
| POST | `/api/prompts/{id}/versions/{n}/restore` |
| GET | `/api/prompts/{id}/compare?left=&right=` |
| POST/PUT/DELETE/GET | `/api/prompts/{id}/media`… |
| GET | `/api/storage` |

Images: PNG/JPEG/WebP/GIF only; max 2 MB/file; 50 MB quota; magic-byte + decode on backend.

## Logging

Levels (priority): **AUDIT → FATAL → ERROR → WARNING → INFO → DEBUG**.  
PERF lines at INFO for each HTTP request. Errors return JSON `{ code, message, correlationId }` only — no stack traces to clients.

## Security notes

- OAuth Google/Facebook when profile `oauth` is active and client ids are set  
- Default **dev mode** for local work without OAuth secrets  
- Allowlist via `ALLOWED_EMAILS`  
- Media never served as static files; only authenticated REST  

## License

Private / self-hosted — all rights reserved by the project owner unless stated otherwise.
