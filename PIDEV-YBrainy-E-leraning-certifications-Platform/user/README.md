# 🧠 YBrainy — E-Learning Certifications Platform

> A full-stack e-learning platform with Keycloak authentication, face biometrics, AI-driven behavior analytics, and a moderation system — built with Spring Boot + Angular 18.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.2-green?style=flat-square&logo=springboot)
![Angular](https://img.shields.io/badge/Angular-18-red?style=flat-square&logo=angular)
![Keycloak](https://img.shields.io/badge/Keycloak-Auth-blue?style=flat-square&logo=keycloak)
![MySQL](https://img.shields.io/badge/MySQL-8-blue?style=flat-square&logo=mysql)

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Repository Structure](#-repository-structure)
- [Local Setup](#-local-setup)
- [Configuration Reference](#-configuration-reference)
- [API Reference](#-api-reference)
- [Key Features](#-key-features)
- [Frontend Routes](#-frontend-routes)
- [Testing](#-testing)
- [Troubleshooting](#-troubleshooting)

---

## 📖 Overview

YBrainy is a monorepo containing a **Spring Boot user/auth microservice** and an **Angular 18 frontend**, covering:

- 🔐 Keycloak-backed authentication (password, Google SSO, face biometrics)
- 🛡️ User moderation (bans, warnings, appeals)
- 📊 Behavioral analytics and interaction tracking
- 🧩 Anti-bot signup challenges
- 👤 Admin backoffice and user frontoffice UIs

> ⚠️ **Current repo state:** `pom.xml`, `mvnw`, and `mvnw.cmd` are missing from root. The backend was previously built successfully — restore these files before running the backend.

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Angular App    │────▶│   API Gateway    │────▶│  User Service   │
│  :4200          │     │   :8088          │     │  :8899          │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                                         │
                              ┌──────────────────────────┼──────────────────────┐
                              ▼                          ▼                      ▼
                      ┌──────────────┐         ┌──────────────┐       ┌──────────────┐
                      │   Keycloak   │         │    MySQL     │       │    Eureka    │
                      │   :9190      │         │    :3306     │       │    :8071     │
                      └──────────────┘         └──────────────┘       └──────────────┘
```

| Service | URL |
|---|---|
| Angular Frontend | `http://localhost:4200` |
| API Gateway | `http://localhost:8088` |
| User Service | `http://localhost:8899` |
| Keycloak | `http://localhost:9190` |
| Eureka | `http://localhost:8071/eureka/` |
| MySQL | `localhost:3306` · DB: `ybrainy_users` |

---

## 🛠️ Tech Stack

### Backend
| Technology | Purpose |
|---|---|
| Java 17 + Spring Boot 3.3.2 | Core framework |
| Spring Security OAuth2 (JWT) | Resource server / token validation |
| Spring Data JPA + Hibernate | ORM / persistence |
| MySQL 8 | Primary database |
| Flyway | Database migrations |
| Eureka Client | Service discovery |
| WebClient | Keycloak Admin REST calls |
| Swagger / OpenAPI | API documentation |

### Frontend
| Technology | Purpose |
|---|---|
| Angular 18 | SPA framework |
| keycloak-js | Auth integration |
| RxJS | Reactive streams |
| Karma / Jasmine | Unit testing |

### Auxiliary
| Technology | Purpose |
|---|---|
| Python 3 + OpenCV | Face biometric matching |
| PowerShell | Keycloak Google IdP automation |

---

## 📁 Repository Structure

```
.
├── src/
│   └── main/
│       ├── java/esprit/tn/breadandbutteruser/
│       │   ├── config/
│       │   ├── controllers/
│       │   ├── dto/
│       │   ├── entities/
│       │   ├── repositories/
│       │   └── services/
│       └── resources/
│           ├── application.properties
│           ├── biometrics/haarcascade_frontalface_alt.xml
│           └── mail/
├── angular/angular-app/
│   ├── src/app/
│   ├── src/environments/
│   └── package.json
├── scripts/
│   ├── face_biometric.py
│   └── configure-keycloak-google-idp.ps1
├── p-r-k/
│   ├── ApiGateway/          # HELP.md only — no source present
│   └── Eureka/              # HELP.md only — no source present
└── README.md
```

---

## 🚀 Local Setup

### Prerequisites

| Requirement | Version |
|---|---|
| Java | 17 |
| Maven | 3.9+ |
| Node.js + npm | 18+ |
| Python | 3.x + `opencv-python` |
| MySQL | 8+ |
| Keycloak | Running on `:9190` |
| Eureka | Running on `:8071` (or disable in config) |

### Step 1 — Start Dependencies

1. Start **MySQL** and confirm database `ybrainy_users` is accessible
2. Start **Keycloak** and create realm `microservices`
3. Configure Keycloak clients:
   - `angular-client` — for user login flows
   - `bb-user-admin` — for admin service account operations
4. Ensure realm roles exist: `ADMIN`, `INSTRUCTOR`, `STUDENT`, `ENTERPRISE_USER`
5. Start **Eureka** on `:8071` — or set `eureka.client.enabled=false` to skip it

### Step 2 — Run the Backend

> Restore `pom.xml` and `mvnw` first if missing.

```bash
mvn spring-boot:run
```

### Step 3 — Run the Frontend

```bash
cd angular/angular-app
npm install
npm start
```

Open [http://localhost:4200](http://localhost:4200)

### Step 4 — (Optional) Configure Google Login

```powershell
$env:KEYCLOAK_GOOGLE_CLIENT_ID     = "your-client-id"
$env:KEYCLOAK_GOOGLE_CLIENT_SECRET = "your-client-secret"

.\scripts\configure-keycloak-google-idp.ps1
```

Additional optional overrides:

| Variable | Default |
|---|---|
| `KEYCLOAK_BASE_URL` | `http://localhost:9190` |
| `KEYCLOAK_ADMIN_REALM` | `master` |
| `KEYCLOAK_ADMIN_USERNAME` | `admin` |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` |
| `KEYCLOAK_REALM` | `microservices` |
| `KEYCLOAK_GOOGLE_ALIAS` | `google` |

---

## ⚙️ Configuration Reference

### Core App & Database

| Property | Default |
|---|---|
| `spring.application.name` | `breadandbutteruser` |
| `server.port` | `8899` |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/ybrainy_users?createDatabaseIfNotExist=true` |
| `spring.datasource.username` | `root` |
| `spring.datasource.password` | _(empty)_ |
| `spring.jpa.hibernate.ddl-auto` | `update` |
| `eureka.client.service-url.defaultZone` | `http://localhost:8071/eureka/` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:9190/realms/microservices` |

### Keycloak Admin

| Env Variable | Default |
|---|---|
| `keycloak.base-url` | `http://localhost:9190` |
| `keycloak.realm` | `microservices` |
| `keycloak.admin.client-id` | `bb-user-admin` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | **required** |
| `KEYCLOAK_ADMIN_USERNAME` | `admin` |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` |
| `KEYCLOAK_AUTH_CLIENT_ID` | `angular-client` |
| `KEYCLOAK_AUTH_CLIENT_SECRET` | _(empty for public client)_ |

### SMTP / Mail

| Env Variable | Default |
|---|---|
| `APP_SMTP_USERNAME` | `ybrainycontact@gmail.com` |
| `APP_SMTP_APP_PASSWORD` | **must be set** |
| `APP_MAIL_ENABLED` | `true` |
| `APP_MAIL_FROM_NAME` | `ybrainy` |
| `APP_MAIL_FORGOT_PASSWORD_CODE_LENGTH` | `6` |
| `APP_MAIL_FORGOT_PASSWORD_CODE_TTL_MINUTES` | `10` |
| `APP_MAIL_DEV_FALLBACK_ON_FAILURE` | `true` |
| `APP_MAIL_DEV_EXPOSE_FALLBACK_CODE` | `true` |

### File Upload & Face Biometrics

| Env Variable | Default |
|---|---|
| `APP_UPLOAD_IMAGES_DIR` | `C:/xampp/htdocs/images` |
| `APP_UPLOAD_IMAGES_PUBLIC_BASE_URL` | `http://localhost/images` |
| `APP_FACE_BIOMETRIC_PYTHON_COMMAND` | `py` |
| `APP_FACE_BIOMETRIC_SCRIPT_PATH` | `scripts/face_biometric.py` |
| `APP_FACE_BIOMETRIC_STORAGE_DIR` | `tmp/face-biometric` |

### Frontend Environment

```ts
// environment.ts (development)
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8088',
  googleIdpHint: 'google',
};

// environment.prod.ts (production)
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.ybrainy.com',
  googleIdpHint: 'google',
};
```

---

## 📡 API Reference

### 🔑 Auth — `/api/auth`

| Method | Endpoint | Access |
|---|---|---|
| POST | `/signup/challenge` | Public |
| POST | `/signup` | Public |
| POST | `/signin` | Public |
| POST | `/login` | Public |
| POST | `/face/signin` _(multipart)_ | Public |
| POST | `/refresh` | Public |
| POST | `/forgot-password` | Public |
| POST | `/forgot-password/reset-with-code` | Public |

### 👤 Users — `/api/users`

| Method | Endpoint | Access |
|---|---|---|
| GET | `/{id}` | Admin or self |
| GET | `/username/{username}` | Admin or self |
| GET | `/email/{email}` | Admin or self |
| GET | `/` | Admin |
| POST | `/uploads/avatar` _(multipart)_ | Public |
| GET | `/admin/overview` | Admin |
| GET | `/{id}/activities` | Admin |
| PUT | `/{id}/ban` | Admin |
| PUT | `/{id}` | Admin or self |
| DELETE | `/{id}` | Admin |
| POST | `/{id}/face-biometric` _(multipart)_ | Admin or self |
| DELETE | `/{id}/face-biometric` | Admin or self |

### 🔧 Admin Keycloak — `/api/admin/keycloak`

| Method | Endpoint | Access |
|---|---|---|
| GET | `/users` | Admin |
| PUT | `/users/{userId}/enabled` | Admin |
| PUT | `/users/{userId}/role` | Admin |
| PUT | `/users/{userId}/banned` | Admin |

### ⚠️ Warnings — `/api/warnings`

| Method | Endpoint | Access |
|---|---|---|
| POST | `/` | Admin |
| GET | `/{id}` | Admin or owner |
| GET | `/` | Admin |
| GET | `/user/{userId}` | Admin or self |
| GET | `/user/{userId}/count` | Admin or self |
| DELETE | `/{id}` | Admin |

### 🚫 Ban Appeals — `/api/ban-appeals`

| Method | Endpoint | Access |
|---|---|---|
| POST | `/` | Authenticated |
| GET | `/{id}` | Admin or owner |
| GET | `/` | Admin |
| GET | `/user/{userId}` | Admin or self |
| GET | `/status/{status}` | Admin |
| POST | `/{id}/approve` | Admin |
| POST | `/{id}/reject` | Admin |
| DELETE | `/{id}` | Admin or owner |

### 🧠 Personality & Behavior

Both `/api/personalities` and `/api/behaviors` share the same shape:

| Method | Endpoint | Access |
|---|---|---|
| POST | `/` | Admin |
| GET | `/{id}` | Admin or owner |
| GET | `/` | Admin |
| PUT | `/{id}` | Admin or owner |
| DELETE | `/{id}` | Admin |

### 📊 Tracking — `/api/tracking`

| Method | Endpoint | Access |
|---|---|---|
| POST | `/events` | Authenticated JWT |

---

## ✨ Key Features

### 🔐 Authentication
- Email/password login via Keycloak password grant
- Google SSO via `idpHint`
- Face biometric sign-in — backend matches face, impersonates user in Keycloak, sets cookies
- Refresh token flow via `/api/auth/refresh`
- Login throttle: **3 failed attempts → 50-second cooldown**

### 🧩 Signup Anti-Bot Challenges
- In-memory tokenized challenges with **5-minute TTL**
- Modes: `jigsaw`, `history`, `math`, `logic`
- Country jigsaw supported for: 🇫🇷 🇹🇳 🇳🇬 🇩🇪 🇮🇹 🇺🇸 🇲🇦 🇪🇬 🇪🇸 🇩🇿
- Challenge recommendation based on `age` and `country` profile hints

### 🛡️ Moderation
- Issue warnings and track warning counts
- Ban / unban users with reason and duration
- Ban appeals with approve/reject flows
- Integrity statuses: `SECURE` · `WARNED` · `SUSPENDED` · `FLAGGED`

### 📈 Behavior Analytics
- Frontend emits click/route/engagement batches to `/api/tracking/events`
- Scheduler runs **every 60 seconds**, analyzing the last 5 minutes of events
- Engagement index uses exponential smoothing (`α = 0.3`)

### 🔑 Password Reset
- 6-digit verification code sent via email, **10-minute TTL**
- In-memory code store (lost on restart)
- Password requirements: 8+ chars, uppercase, lowercase, number

---

## 🗺️ Frontend Routes

### Public
| Route | Description |
|---|---|
| `/` | Frontoffice home |
| `/login` | Login page |
| `/signup` | Registration |
| `/forgot-password` | Password reset |

### Admin Only
| Route | Description |
|---|---|
| `/dashboard` | Admin dashboard |
| `/dashboard/users` | User moderation |
| `/dashboard/courses` | Course management |
| `/dashboard/lessons` | Lesson management |
| `/dashboard/calendar` | Calendar |
| `/dashboard/profile` | Admin profile |

### Frontoffice (User Guard)
| Route | Note |
|---|---|
| `/profile` | Redirects admins to `/dashboard` |

---

## 🧪 Testing

### Backend

```bash
mvn test
```

Tests present:
- `BreadandbutteruserApplicationTests`
- `KeycloakClientClaimValidatorTest`
- `KeycloakAdminServiceErrorMappingTest`
- `SignUpChallengeServiceTest`

> All tests show as passing in last available surefire report.

### Frontend

```bash
cd angular/angular-app
npm test
```

Tests present:
- `app.component.spec.ts`
- `signup.component.spec.ts`
- `auth/keycloak.service.spec.ts`
- `frontoffice/services/user.service.spec.ts`
- `frontoffice/home/home.component.spec.ts`

---



## 🔧 Troubleshooting

<details>
<summary><strong>❌ "Keycloak auth client configuration is invalid"</strong></summary>

- Check `KEYCLOAK_AUTH_CLIENT_ID` is correct
- For **public clients**: leave `KEYCLOAK_AUTH_CLIENT_SECRET` empty
- For **confidential clients**: set `KEYCLOAK_AUTH_CLIENT_SECRET`

</details>

<details>
<summary><strong>❌ "Client not allowed for direct access grants"</strong></summary>

Enable **Direct Access Grants** on the Keycloak auth client if password grant login is intended.

</details>

<details>
<summary><strong>❌ Face login / enrollment not working</strong></summary>

1. Confirm Python 3 + OpenCV are installed: `pip install opencv-python`
2. Verify cascade file exists: `src/main/resources/biometrics/haarcascade_frontalface_alt.xml`
3. Confirm `tmp/face-biometric` directory is writable
4. Use a clear, front-facing, well-lit image

</details>

<details>
<summary><strong>❌ Forgot-password returns a fallback code instead of emailing</strong></summary>

- SMTP likely failed — check `APP_SMTP_APP_PASSWORD`
- Ensure a valid Gmail app password is set (not your account password)
- Confirm `APP_MAIL_ENABLED=true`

</details>

<details>
<summary><strong>❌ Avatar upload URL not reachable</strong></summary>

- Check `APP_UPLOAD_IMAGES_DIR` points to a real, writable directory
- Confirm `APP_UPLOAD_IMAGES_PUBLIC_BASE_URL` correctly serves files from that directory

</details>

---

## 🧰 Additional Resources (Available on Request)

- `.env.example` — from all current application properties
- `docker-compose.yml` — for MySQL + Keycloak + optional MailHog
- Postman Collection — covering full API surface above
