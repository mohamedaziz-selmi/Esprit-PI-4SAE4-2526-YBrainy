# Service Group Scripts

One script per logical group. Each script opens every service in its own PowerShell window (same behaviour as the individual scripts in the parent folder).

## Before running anything

Start these three containers from Docker Desktop:

| Container | Purpose |
|-----------|---------|
| **Keycloak** | Authentication / JWT |
| **RabbitMQ** | Async messaging between services |
| **MongoDB** | Forum threads/posts, Notes service |

MySQL is assumed to be running locally (or containerised — just make sure it's up before you start any Java service that uses JPA).

---

## Scripts

| Script | What it starts | Key ports |
|--------|---------------|-----------|
| `start-user.ps1` | Eureka · API Gateway · User Service · Personality · Warning-Ban-Appeal · Notes | 8761, 8088, 8899, 8084, 8086, 8087 |
| `start-courses.ps1` | Eureka · Config Server · Course · Quiz · Lesson · Enrollment · ML | 8761, 8888, 8082-8085, 8183, 5000 |
| `start-forum.ps1` | Eureka · Forum Config · Predict · Category · Thread · Post · Comment · Messaging · Forum Gateway | 8761, 8888, 8082-8086, 8090, 5001 |
| `start-payment.ps1` | Eureka · Cart · Payment · Finance · Scraper · Payment Gateway (+ Angular) | 8761, 8091, 8093-8095, 8954, 8995, 4201 |
| `start-events.ps1` | Eureka · Event · Inscription · Events-User · Feedback | 8761, 9001-9004 |
| `start-parteneriat.ps1` | Eureka · Partnership · Job Offer · Gateway (+ React) | 8761, 8096, 8181-8182, 5173 |
| `start-ai-gaze.ps1` | AI Gaze Tracking only (heavy — start on demand) | 5002 |
| `start-ai-talking-head.ps1` | AI Talking Head / Voice Cloning only (heavy — start on demand) | 8765 |

---

## Common patterns

```powershell
# Run the user stack (includes Eureka — start this first when testing courses)
.\start-user.ps1

# Run courses without waiting for each service to be ready
.\start-courses.ps1 -SkipWait

# Run courses when Eureka is already up (from start-user.ps1)
.\start-courses.ps1 -SkipEureka

# Run the forum, reusing an already-running Eureka
.\start-forum.ps1 -SkipEureka

# Run payment with no frontend
.\start-payment.ps1 -SkipFrontend

# Start voice cloning with CPU (no GPU)
.\start-ai-talking-head.ps1 -Cpu

# Dry-run — prints what would launch without actually opening windows
.\start-courses.ps1 -DryRun
```

---

## Port conflict notes

- **Notes Service** is started on **:8087** (not :8084) in `start-user.ps1` because :8084 is reserved for the Personality Behavior Service.
- **Forum** and **Courses** both use :8082-8085 for different services. Do not run both stacks at the same time unless you pass custom port overrides.
- All scripts accept `-EurekaPort`, `-GatewayPort`, and per-service port parameters if you need to override the defaults.
