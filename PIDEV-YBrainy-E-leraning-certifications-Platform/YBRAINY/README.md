# YBrainy — AI-Powered E-Learning Platform

YBrainy is a full-stack, cloud-native e-learning platform built on a Spring Cloud microservices architecture. It provides course management, lesson delivery, quiz assessment, AI-driven recommendations, Stripe-based payments, automated certificate generation, and a rich Angular frontend — all routed through a secured API Gateway.

---

## Architecture Overview

```
                             ┌─────────────────────────────────────────────────────┐
                             │              CLIENT LAYER                           │
                             │         Angular SPA  :4200                          │
                             └────────────────────┬────────────────────────────────┘
                                                  │  HTTP/REST (JWT Bearer)
                                                  ▼
                             ┌─────────────────────────────────────────────────────┐
                             │              API GATEWAY  :8088                     │
                             │   Spring Cloud Gateway  •  Keycloak JWT filter      │
                             └──┬──────┬──────┬──────┬──────┬──────┬──────────────┘
                                │      │      │      │      │      │
              ┌─────────────────┘   ┌──┘   ┌──┘   ┌──┘   ┌──┘   └──────────────┐
              ▼                     ▼      ▼      ▼      ▼                       ▼
      ┌───────────────┐  ┌──────────────┐ ┌──────────┐ ┌──────────┐  ┌──────────────────┐
      │  User Service │  │Course Service│ │  Quiz    │ │  Lesson  │  │Enrollment Service│
      │    :8899      │  │    :8082     │ │ Service  │ │ Service  │  │     :8085        │
      │  MySQL        │  │  MySQL       │ │  :8083   │ │  :8084   │  │   MySQL          │
      │ybrainy_users  │  │ybrainy_      │ │  MySQL   │ │  MySQL   │  │ybrainy_          │
      │  + Keycloak   │  │  courses     │ │ybrainy_  │ │ybrainy_  │  │  enrollments     │
      └───────────────┘  └──────┬───────┘ │  quiz    │ │  lessons │  └────────┬─────────┘
                                │         └──────────┘ └──────────┘           │
                                │  REST (Feign)           ▲    ▲               │
                                │◄────────────────────────┘    └───────────────┘
                                │
                    ┌───────────┴──────────────────────────────────────────────┐
                    │                  ASYNC MESSAGING                         │
                    │                  RabbitMQ  :5672                         │
                    │                                                           │
                    │  payment.exchange ──► payment.completed.queue            │
                    │  enrollment.exchange ──► enrollment.completed.queue      │
                    │  course.exchange ──► course.deleted.queue                │
                    └──────────────────────────────────────────────────────────┘

                    ┌──────────────────────────────────────────────────────────┐
                    │              INFRASTRUCTURE                              │
                    │  Eureka Server :8071  •  Config Server :8888             │
                    │  Keycloak IdP  :9190  •  MySQL (multiple DBs)            │
                    └──────────────────────────────────────────────────────────┘

                    ┌──────────────────────────────────────────────────────────┐
                    │              ML SERVICE  :5000  (Python/Flask)           │
                    │  DSO1 Conversion  •  DSO2 Recommendations               │
                    │  DSO3 Quality     •  DSO4 ARIMA Demand Forecast          │
                    └──────────────────────────────────────────────────────────┘
```

---

## Services Reference

| Service | Module Path | Port | Database | Technology |
|---|---|---|---|---|
| **Eureka Server** | `p-r-k/Eureka` | 8071 | — | Spring Cloud Netflix Eureka |
| **Config Server** | `ConfigServer/config-server` | 8888 | — | Spring Cloud Config (native) |
| **API Gateway** | `p-r-k/ApiGateway` | 8088 | — | Spring Cloud Gateway + Keycloak JWT |
| **User Service** | `src/` | 8899 | `ybrainy_users` | Spring Boot, JPA, Keycloak Admin SDK, SMTP |
| **Course Service** | `Course/tp-foyer` | 8082 | `ybrainy_courses` | Spring Boot, JPA, Stripe SDK, OpenRouter AI |
| **Quiz Service** | `Quiz/quiz-service` | 8083 | `ybrainy_quiz` | Spring Boot, JPA |
| **Lesson Service** | `Lesson/lesson-service` | 8084 | `ybrainy_lessons` | Spring Boot, JPA, Multipart upload |
| **Enrollment Service** | `Enrollment/enrollment-service` | 8085 | `ybrainy_enrollments` | Spring Boot, JPA |
| **ML Service** | `ML-Service/` | 5000 | — | Python, Flask, scikit-learn, statsmodels |
| **Angular Frontend** | `angular/angular-app` | 4200 | — | Angular 17, TypeScript |

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | 17 | Required by all Spring Boot services |
| Maven | 3.8+ | Build tool for all Java services |
| MySQL | 8.0+ | Each service creates its own database automatically on first run |
| RabbitMQ | 3.x | Default credentials: `guest / guest`, port `5672` |
| Node.js | 18+ | Required to run the Angular frontend (`npm`) |
| Python | 3.10+ | Required for the ML service |
| Keycloak | 22+ | Identity provider, runs on port `9190`, realm `microservices` |

---

## How to Run

Start services in the following order. Each step must be healthy before proceeding to the next.

### Step 1 — Infrastructure

```bash
# 1a. Start MySQL (ensure it is running on port 3306 with root / no password)

# 1b. Start RabbitMQ
#     Management UI available at http://localhost:15672 (guest/guest)

# 1c. Start Keycloak (realm: microservices, port: 9190)
#     Create a realm named "microservices" and configure clients:
#     - angular-client  (public, used by the SPA)
#     - bb-user-admin   (confidential, used by the User Service)
```

### Step 2 — Spring Cloud Infrastructure

```bash
# 2a. Config Server
cd ConfigServer/config-server
mvn spring-boot:run
# Verify: http://localhost:8888/actuator/health

# 2b. Eureka Server
cd p-r-k/Eureka/Eureka
mvn spring-boot:run
# Dashboard: http://localhost:8071
```

### Step 3 — Business Microservices (order-independent, run in parallel)

```bash
# User Service
cd src
mvn spring-boot:run

# Course Service
cd Course/tp-foyer
mvn spring-boot:run

# Lesson Service
cd Lesson/lesson-service
mvn spring-boot:run

# Enrollment Service
cd Enrollment/enrollment-service
mvn spring-boot:run

# Quiz Service
cd Quiz/quiz-service
mvn spring-boot:run
```

### Step 4 — ML Service

```bash
cd ML-Service
pip install -r requirements.txt
python generate_and_train.py   # generate and train models (first time only)
python app.py
# Health check: http://localhost:5000/health
```

### Step 5 — API Gateway

```bash
cd p-r-k/ApiGateway/ApiGateway
mvn spring-boot:run
# All traffic now routed through http://localhost:8088
```

### Step 6 — Angular Frontend

```bash
cd angular/angular-app
npm install
ng serve
# Open: http://localhost:4200
```

---

## Microservice Communication

### Feign Clients (Synchronous REST)

All inter-service HTTP calls use Spring Cloud OpenFeign with Eureka-based load balancing (`lb://service-name`).

| Caller | Target | Purpose |
|---|---|---|
| **Course Service** | `enrollment-service` | Check/create enrollments, track lesson progress, get student dashboard, update certificate ID |
| **Course Service** | `lesson-service` | CRUD lessons for a course, get lesson count, track progress |
| **Course Service** | `quiz-service` | Delete quizzes on course delete, get student best/avg quiz scores |
| **Course Service** | `user-service` | Fetch user profile by ID (instructor info, student name on certificates) |
| **Enrollment Service** | `course-service` | Check course existence, fetch course details |
| **Enrollment Service** | `lesson-service` | Get lesson count, track lesson progress, get completed lesson count |
| **Lesson Service** | `course-service` | Validate that a course exists before creating or fetching lessons |
| **Quiz Service** | `course-service` | Validate course existence before creating a quiz |
| **Quiz Service** | `enrollment-service` | Check that a student is enrolled before allowing a quiz attempt |
| **Quiz Service** | `user-service` | Fetch student display name for the leaderboard |

---

### RabbitMQ Flows (Asynchronous Event-Driven)

All exchanges are `TopicExchange`. All queues are durable.

#### Flow 1 — Payment Completed → Enrollment Created

```
Stripe Webhook
     │
     ▼
Course Service (PaymentController)
     │  publishes PaymentCompletedEvent
     │  exchange : payment.exchange
     │  routing  : payment.completed
     ▼
payment.completed.queue
     │
     ▼
Enrollment Service (PaymentEventListener)
     └─► Creates Enrollment record(s) with status ACTIVE
         (supports both single-course and learning-path purchases)
```

| Field | Value |
|---|---|
| Exchange | `payment.exchange` |
| Routing key | `payment.completed` |
| Queue | `payment.completed.queue` |
| Publisher | Course Service — `PaymentController` |
| Consumer | Enrollment Service — `PaymentEventListener` |

---

#### Flow 2 — Enrollment Completed → Certificate Generated

```
Enrollment Service (EnrollmentServiceImpl)
     │  triggers when completion percentage reaches 100%
     │  publishes EnrollmentCompletedEvent
     │  exchange : enrollment.exchange
     │  routing  : enrollment.completed
     ▼
enrollment.completed.queue
     │
     ▼
Course Service (EnrollmentEventListener)
     └─► Calls CertificateService.generateCertificate(courseId, studentId)
         Produces a PDF certificate and stores the path in the enrollment record
```

| Field | Value |
|---|---|
| Exchange | `enrollment.exchange` |
| Routing key | `enrollment.completed` |
| Queue | `enrollment.completed.queue` |
| Publisher | Enrollment Service — `EnrollmentServiceImpl` |
| Consumer | Course Service — `EnrollmentEventListener` |

---

#### Flow 3 — Course Deleted → Quizzes Cascade-Deleted

```
Course Service (CourseServiceImpl)
     │  triggered on course deletion
     │  publishes CourseDeletedEvent { courseId, courseTitle }
     │  exchange : course.exchange
     │  routing  : course.deleted
     ▼
course.deleted.queue
     │
     ▼
Quiz Service (CourseEventListener)
     └─► Calls QuizService.deleteQuizzesByCourse(courseId)
         Removes all quizzes and attempts associated with that course
```

| Field | Value |
|---|---|
| Exchange | `course.exchange` |
| Routing key | `course.deleted` |
| Queue | `course.deleted.queue` |
| Publisher | Course Service — `CourseServiceImpl` |
| Consumer | Quiz Service — `CourseEventListener` |

---

## API Documentation

The API Gateway aggregates all services. Interactive Swagger UI is available at:

```
http://localhost:8088/swagger-ui.html
```

Individual service Swagger endpoints (direct, bypassing the gateway):

| Service | Swagger URL |
|---|---|
| Course Service | http://localhost:8082/swagger-ui.html |
| Quiz Service | http://localhost:8083/swagger-ui.html |
| Lesson Service | http://localhost:8084/swagger-ui.html |
| Enrollment Service | http://localhost:8085/swagger-ui.html |
| User Service | http://localhost:8899/swagger-ui.html |

---

## Key Features per Service

### User Service (`:8899`)
- **Authentication**: JWT-based sign-up / sign-in backed by Keycloak (`microservices` realm)
- **Face Biometrics**: Python-backed face verification at sign-up
- **User Management**: Admin CRUD, avatar upload (served via XAMPP `/images`)
- **Behavioral Tracking**: Session tracking events, batch ingestion
- **Personality Profiling**: MBTI-style learning style assessment
- **Moderation**: Warning system, ban/unban, ban appeal workflow
- **Email Notifications**: SMTP (Gmail) — password reset codes, confirmation emails

### Course Service (`:8082`)
- **Course CRUD**: Full lifecycle with thumbnail/certificate file upload
- **Filtering & Pagination**: Search by title, category, level, price range, publish status
- **Learning Paths**: Bundle multiple courses into a discounted path
- **Payments**: Stripe Checkout Session creation + webhook handling
- **Certificates**: PDF certificate generation triggered via RabbitMQ
- **Reviews**: Student course reviews and aggregated rating
- **Instructor Analytics**: Revenue, enrollment trends, per-course stats
- **ML Proxy**: Forwards requests to the ML Service for recommendations, quality scores, conversion analytics, and demand forecasting
- **AI Search**: OpenRouter LLM-powered natural language course search

### Quiz Service (`:8083`)
- **Quiz Management**: Instructor creates quizzes with multiple-choice questions per course
- **Attempt Handling**: Students submit answers; service grades automatically
- **Leaderboard**: Top scores per quiz with student display names (via User Service Feign call)
- **Cascade Delete**: Listens to `course.deleted` queue and removes orphaned quizzes
- **Enrollment Guard**: Validates enrollment via Feign before allowing an attempt

### Lesson Service (`:8084`)
- **Lesson CRUD**: Text, video, and file-based lesson types per course
- **File Storage**: Multipart upload for lesson content (up to 500 MB), stored on local filesystem
- **Progress Tracking**: Per-student lesson completion status and time-spent tracking
- **Sequence Ordering**: Ordered lesson navigation within a course

### Enrollment Service (`:8085`)
- **Enrollment Management**: Create, list, and manage student-course relationships
- **Progress Aggregation**: Tracks lesson completion percentage per enrollment
- **Payment-Driven Enrollment**: Listens to `payment.completed.queue` for automatic enrollment after Stripe payment
- **Certificate Trigger**: Publishes `enrollment.completed` event when a student finishes 100% of lessons
- **Student Dashboard**: Aggregated stats — active courses, completed lessons, time spent
- **Monthly Counts**: Enrollment trend data consumed by the instructor analytics dashboard

### ML Service (`:5000`) — Python/Flask
- **DSO1 — Conversion Prediction**: Logistic regression model predicts whether a free student will convert to a paid enrollment based on engagement features (sessions, quiz attempts, time spent)
- **DSO2 — Course Recommendations**: KNN collaborative-content hybrid model; recommends top-N courses based on category, level, enrolled history, and ratings
- **DSO3 — Quality Scoring**: Regression model estimates a course's quality score from instructor activity, content density, and student feedback signals
- **DSO4 — Demand Forecasting**: ARIMA time-series model projects future enrollment demand over a configurable horizon
- **Model Retraining**: `generate_and_train.py` seeds synthetic data and retrains all four models; persisted to `ML-Service/models/`

### API Gateway (`:8088`)
- **Unified Entry Point**: All frontend traffic enters through a single host
- **JWT Validation**: Validates Keycloak-issued tokens on every request
- **Route Table**:

  | Path Prefix | Upstream Service |
  |---|---|
  | `/api/auth/**` | User Service |
  | `/api/users/**` | User Service |
  | `/api/tracking/**` | User Service |
  | `/api/warnings/**` | User Service |
  | `/api/ban-appeals/**` | User Service |
  | `/api/courses/**` | Course Service |
  | `/api/payments/**` | Course Service |
  | `/api/learning-paths/**` | Course Service |
  | `/api/certificates/**` | Course Service |
  | `/api/instructor/**` | Course Service |
  | `/api/students/**` | Course Service |
  | `/api/ml/**` | Course Service |
  | `/api/enrollments/**` | Enrollment Service |
  | `/api/quizzes/**` | Quiz Service |

### Angular Frontend (`:4200`)
- **Auth Pages**: Login, sign-up (with personality challenge + optional face biometric)
- **Frontoffice**: Course catalog, course detail, lesson player, quiz runner, progress tracker, student dashboard, payment flow (Stripe redirect)
- **Backoffice**: Admin dashboard — user management, course management, analytics charts (conversion, enrollment trends, revenue)
- **Role-Based Views**: Distinct layouts and feature sets for Students, Instructors, and Admins

---

## Environment Variables

Sensitive values should be provided via environment variables rather than hardcoded in `application.properties`:

| Variable | Used By | Description |
|---|---|---|
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | User Service | Keycloak admin client secret |
| `KEYCLOAK_AUTH_CLIENT_SECRET` | User Service, Gateway | Angular client secret |
| `APP_SMTP_APP_PASSWORD` | User Service | Gmail app password for SMTP |
| `JWT_SECRET` | Course Service | JWT signing secret (default: `ybrainy-dev-secret-key`) |

---

## Project Structure

```
YBRAINY/
├── ConfigServer/          # Spring Cloud Config Server
├── p-r-k/
│   ├── Eureka/            # Netflix Eureka Service Registry
│   └── ApiGateway/        # Spring Cloud Gateway
├── src/                   # User Service (breadandbutteruser)
├── Course/tp-foyer/       # Course Service
├── Enrollment/            # Enrollment Service
├── Lesson/                # Lesson Service
├── Quiz/                  # Quiz Service
├── ML-Service/            # Python Flask ML microservice
└── angular/angular-app/   # Angular 17 frontend
```
