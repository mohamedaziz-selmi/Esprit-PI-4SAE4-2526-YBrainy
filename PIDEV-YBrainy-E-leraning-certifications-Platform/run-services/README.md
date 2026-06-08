# Module Runners

Use the `run-module-*.ps1` scripts for normal work. They start the shared Eureka first, then start only the missing services for that module.

Shared Eureka:

```powershell
.\run-services\run-eureka.ps1
```

Run one module:

```powershell
.\run-services\run-module-user.ps1
.\run-services\run-module-forum.ps1
.\run-services\run-module-parteneriat.ps1
.\run-services\run-module-payment.ps1
.\run-services\run-module-courses.ps1
.\run-services\run-module-events.ps1
.\run-services\run-module-feedback.ps1
```

Run forum + partenariat together:

```powershell
.\run-services\run-forum-parteneriat.ps1
.\run-services\run-forum-parteneriat.ps1 -SkipPredict
.\run-services\run-forum-parteneriat.ps1 -WaitServices
```

The courses runner also starts the local AI model services used by quizzes and lessons:

```powershell
.\run-services\run-ai-gaze.ps1
.\run-services\run-ai-talking-head.ps1
.\run-services\run-module-courses.ps1 -SkipAiModels
```

Run everything:

```powershell
.\run-services\run-module-all.ps1
```

Useful flags:

```powershell
.\run-services\run-module-forum.ps1 -SkipWait
.\run-services\run-module-parteneriat.ps1 -SkipFrontend
.\run-services\run-module-all.ps1 -SkipFrontends -SkipMl -SkipPredict
.\run-services\run-module-all.ps1 -EurekaPort 18761 -SkipFrontends
.\run-services\run-module-user.ps1 -DryRun
```

How it works:

- `run-eureka.ps1` starts the unified Eureka on `http://localhost:8761`.
- `run-module-all.ps1` starts Eureka once, then calls each module with `-SkipEureka` so modules reuse the same registry.
- Spring service launchers automatically point to `http://localhost:8761/eureka/`.
- If you pass a custom `-EurekaPort`, module runners point child Spring services to that same Eureka URL.
- Each service checks its port first. If it is already listening, startup is skipped.
- Maven uses your normal shared Maven cache by default, so repeated runs do not re-download dependencies per service.
- Set `YBRAINY_USE_ISOLATED_MAVEN_REPO=1` only if you intentionally want isolated per-service Maven caches.
- Individual `run-*.ps1` scripts are still available for debugging one service directly.

Module defaults:

- User: service `8899`, gateway `8088`, Angular `4200`
- Forum: gateway `8090`, user `8191`, category `8082`, thread `8083`, post `8084`, comment `8085`, messaging `8086`, predict `5001`
- Parteneriat: partnership `8181`, job offers `8182`, gateway `8096`, React `5173`
- Payment: gateway `8091`, cart `8954`, payment `8095`, finance `8995`, scraper `8093`, Angular `4201`
- Courses: course `8172`, quiz `8173`, ML `5000`, gaze `5001`, talking-head `8765`, gateway `8170`
- Events: event `9001`, inscription `9002`, user `9003`, feedback `9004`
- Events ML: FastAPI inference service `9010`

Some modules share default ports. If a port is busy, pass another one:

```powershell
.\run-services\run-feedback-service.ps1 -Port 8183
```
