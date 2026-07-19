# Windows native run (not deployed yet)

These scripts **build and start processes on this machine**. They do **not** register Windows services or open firewall ports unless you do that yourself later.

## Prerequisites

- MongoDB Windows service running (`Get-Service MongoDB`)
- JDK 21+ (`java -version`)
- Maven (`mvn -version`)
- Node.js + npm (`node -v`, `npm.cmd -v`)
- Git (optional for source control)

## Suggested data folders

```powershell
New-Item -ItemType Directory -Force -Path C:\prompt-portal-data\uploads, C:\prompt-portal-data\logs
```

## One-time env for a PowerShell session

```powershell
cd C:\Users\HomePC\Documents\prompt-portal
. .\deploy\windows\env.sample.ps1
```

## Build (no start)

```powershell
.\deploy\windows\build-all.ps1
```

## Start (manual, two terminals)

1. Ensure MongoDB is running  
2. Terminal A: `.\deploy\windows\start-api.ps1` → http://localhost:8080  
3. Terminal B: `.\deploy\windows\start-frontend.ps1` → http://localhost:3000  

## Dev authentication

With `APP_DEV_MODE=true` (default without OAuth profile), the API accepts:

```http
X-Dev-User-Email: you@example.com
```

The frontend stores this in `localStorage` (login page / settings).

## OAuth (later)

1. Create Google / Facebook OAuth apps  
2. Redirect URI: `http://localhost:8080/login/oauth2/code/google` (and Facebook equivalent)  
3. Set `AUTH_*` env vars  
4. Start API with `--spring.profiles.active=oauth`  
5. Set `APP_DEV_MODE=false` and `ALLOWED_EMAILS=your@email.com`

## Backup

```powershell
mongodump --db promptportal --out C:\backup\promptportal-mongo
Copy-Item -Recurse C:\prompt-portal-data\uploads C:\backup\promptportal-uploads
```

## Retention purge (optional Task Scheduler later)

```powershell
.\deploy\windows\purge-stale.ps1
```

In-process scheduled purge also runs while the API is up (`app.retention.cron`).
