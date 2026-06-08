# YBrainy — E-Learning & Certifications Platform

A microservices-based e-learning platform built with Spring Boot, Angular, and Keycloak.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java (JDK) | 17+ | All Spring Boot services |
| Maven | 3.8+ | Build Spring Boot services |
| Node.js | 18+ | Angular frontend |
| npm | 9+ | Frontend dependencies |
| Docker Desktop | Latest | All infrastructure containers |

---

## Docker Containers Required

Before starting any service, make sure the following containers are running in Docker Desktop.

### 1. MySQL 8

The primary relational database used by most services (course-service, enrollment-service, user-service, payment, etc.).

```bash
docker run -d \
  --name mysql \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes \
  -p 3306:3306 \
  mysql:8.0
```

> Databases are created automatically on first startup (`createDatabaseIfNotExist=true`).

**Databases used:**
- `ybrainy_courses` — course-service
- `ybrainy_enrollment` — enrollment-service
- `ybrainy_users` — user-service
- `ybrainy_payment` — payment service
- `ybrainy_events` — event service
- `ybrainy_quiz` — quiz service
- `ybrainy_forum` — forum service

---

### 2. Keycloak 24

Identity and access management — handles login, JWT tokens, roles (STUDENT, INSTRUCTOR, ENTERPRISE_USER, ADMIN), and Google OAuth2.

```bash
docker run -d \
  --name keycloak \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -p 9190:8080 \
  quay.io/keycloak/keycloak:24.0.1 \
  start-dev
```

After the container is running, set up the realm:

1. Open http://localhost:9190 and log in as `admin / admin`
2. Create a realm named **`microservices`**
3. Inside that realm, create a client named **`angular-client`**:
   - Client type: OpenID Connect
   - Client authentication: OFF (public client)
   - Valid redirect URIs: `http://localhost:4200/*`
   - Web origins: `http://localhost:4200`
4. Create these realm roles: `STUDENT`, `INSTRUCTOR`, `ENTERPRISE_USER`, `ADMIN`, `USER`
5. *(Optional)* Google login: Identity Providers > Add > Google — paste your Google OAuth2 Client ID and Secret. Set the redirect URI in Google Console to `http://localhost:9190/realms/microservices/broker/google/endpoint`

---

### 3. RabbitMQ 3

Message broker for async events between services (enrollment notifications, event triggers, etc.).

```bash
docker run -d \
  --name rabbitmq \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

Management UI: http://localhost:15672 (guest / guest)

---

### 4. MongoDB

Used by forum service and notification features for unstructured data.

```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  mongo:6.0
```

No credentials required — services connect without authentication by default.

---

### 5. Zipkin

Distributed tracing — visualize request flows across all microservices and debug latency.

```bash
docker run -d \
  --name zipkin \
  -p 9411:9411 \
  openzipkin/zipkin
```

UI: http://localhost:9411

---

## Service Startup Order

Start services **in this exact order** to avoid registration and dependency failures:

| Order | Service | Port | Directory |
|-------|---------|------|-----------|
| 1 | Config Server | 8888 | `YBRAINY/ConfigServer/config-server` |
| 2 | Eureka Discovery | 8761 | `YBRAINY/Eureka/` |
| 3 | API Gateway | 8088 | `user/p-r-k/ApiGateway/ApiGateway` |
| 4 | User Service | 8089 | `user/` |
| 5 | Course Service | 8082 | `YBRAINY/Course/tp-foyer` |
| 6 | Lesson Service | 8086 | `YBRAINY/Lesson/` |
| 7 | Enrollment Service | 8085 | `YBRAINY/Enrollment/enrollment-service` |
| 8 | Quiz Service | 8083 | `YBRAINY/Quiz/quiz-service` |
| 9 | Event Service | 8090 | `YBRAINY/Events/` |
| 10 | Forum Service | varies | `YBRAINY/Forum/` |
| 11 | Payment Service | 8095 | `payment/Payment` |
| 12 | ML Service (Python) | 5000 | `YBRAINY/ML-Service/` |
| 13 | Angular Frontend | 4200 | `user/angular/angular-app` |

### Build and run a Spring Boot service

```bash
cd <service-directory>
mvn spring-boot:run
```

### Run the Angular frontend

```bash
cd user/angular/angular-app
npm install
ng serve
```

### Run the ML service

```bash
cd YBRAINY/ML-Service
pip install -r requirements.txt
python app.py
```

---

## Port Reference

| Port | Service |
|------|---------|
| 3306 | MySQL |
| 5000 | ML Service (Python/Flask) |
| 5672 | RabbitMQ (AMQP) |
| 8082 | Course Service |
| 8083 | Quiz Service |
| 8085 | Enrollment Service |
| 8086 | Lesson Service |
| 8088 | API Gateway |
| 8089 | User Service |
| 8090 | Event Service |
| 8095 | Payment Service |
| 8761 | Eureka Discovery |
| 8888 | Config Server |
| 9190 | Keycloak |
| 9411 | Zipkin |
| 15672 | RabbitMQ Management UI |
| 27017 | MongoDB |
| 4200 | Angular Frontend |

---

## How the Frontend Connects

The Angular app uses a **dev proxy** in `proxy.conf.json`. Every `/api/**` request is forwarded to the API Gateway at `http://localhost:8088`.

- Always open the app at **http://localhost:4200** — not port 8088 directly.
- All API calls use relative paths (`/api/courses/...`). The proxy handles routing.
- PDF, video, and image files served by the backend are proxied the same way, which prevents browser `X-Frame-Options` blocks inside iframes.

---

## Environment Variables

Defaults work for local dev with the Docker containers listed above. Override as needed:

| Variable | Default | Used By |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/...` | All Spring services |
| `YBRAINY_EUREKA_URL` | `http://localhost:8761/eureka/` | All Spring services |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | All Spring services |
| `KEYCLOAK_URL` | `http://localhost:9190` | User service, Gateway |
| `SPRING_RABBITMQ_HOST` | `localhost` | Course, Enrollment, Event |
| `ZIPKIN_URL` | `http://localhost:9411` | All Spring services |
| `STRIPE_SECRET_KEY` | test key in properties | Payment, Course |
| `OPENROUTER_API_KEY` | key in properties | Course (AI features) |
| `ML_SERVICE_URL` | `http://localhost:5000` | Course service |

---

## Troubleshooting

**Service won't appear in Eureka:**
Config Server (8888) and Eureka (8761) must be fully started before anything else. Verify at http://localhost:8761.

**"Realm not found" or 404 on Keycloak role lookup:**
The `microservices` realm and all roles must be created manually after Keycloak starts. See the Keycloak setup steps above — this is not automatic.

**MySQL "Access denied" or connection refused:**
The root password is empty by default. If your local MySQL has a password, use the Docker container instead, or update `spring.datasource.password` in the service's `application.properties`.

**PDF or video not loading in lesson viewer:**
Always access the app through http://localhost:4200. Direct requests to port 8088 bypass the Angular proxy, which causes `X-Frame-Options` violations in iframes.

**Learning path generation returns 500:**
The course service needs an OpenRouter API key. A default key is baked into `application.properties`. If it stopped working, get a new key at https://openrouter.ai and set `OPENROUTER_API_KEY` as an environment variable before starting course-service.

**RabbitMQ connection refused on startup:**
Start RabbitMQ before the services that depend on it (Course, Enrollment, Event). If a service crashes instead of retrying, just restart it after RabbitMQ is up.

**Port already in use:**
Windows: `netstat -ano | findstr :<port>` then `taskkill /PID <pid> /F`
Mac/Linux: `lsof -i :<port>` then `kill <pid>`
Or change the port in the service's `application.properties`.
