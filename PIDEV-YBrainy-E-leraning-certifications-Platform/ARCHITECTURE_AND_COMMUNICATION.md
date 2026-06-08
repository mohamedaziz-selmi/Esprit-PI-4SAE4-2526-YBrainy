# YBrainy Platform — Architecture & Communication Guide

---

## 1. OVERALL PLATFORM ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────┐
│                     ANGULAR FRONTEND                            │
│                   localhost:4200                                 │
│   proxy.conf.json intercepts /api/* and forwards to Gateway     │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP (via proxy)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                     API GATEWAY                                 │
│                   localhost:8088                                 │
│   Reads routes from application.properties                       │
│   Resolves lb://service-name via Eureka                         │
│   Handles JWT validation (Keycloak)                             │
└──────┬──────────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │          │
       ▼          ▼          ▼          ▼          ▼
  Course      Lesson      Quiz    Enrollment   Other
  :8082       :8084       :8083     :8085     services
       │          │          │          │
       └──────────┴──────────┴──────────┘
                            │
              ┌─────────────┴──────────────┐
              │                            │
              ▼                            ▼
         RabbitMQ                     OpenFeign
      (async events)            (sync HTTP calls)
         :5672                    (via Eureka lb://)
              │
              ▼
┌──────────────────────────────────────────────────┐
│             Eureka Discovery Server              │
│                  localhost:8761                  │
│  All services register here at startup           │
│  Gateway uses it to resolve lb://service-name    │
└──────────────────────────────────────────────────┘
```

### Every service in the platform:
- Registers itself with **Eureka** at startup
- Is reachable through the **API Gateway** (the frontend never talks directly to a microservice)
- Authenticates users via **Keycloak** JWT tokens
- Has its own dedicated **MySQL database** (database-per-service pattern)

---

## 2. WHAT IS THE DIFFERENCE: RabbitMQ vs OpenFeign vs FeignClient?

These are two completely different communication patterns. Think of them like this:

### OpenFeign / FeignClient — Synchronous (immediate, blocking)

**What it is:** A library that lets one Spring Boot service call another Spring Boot service's REST API **as if it were a normal Java method call**. You write an interface, annotate it with `@FeignClient`, and Spring generates the HTTP client code automatically.

**"OpenFeign" and "FeignClient" are the same thing.** "OpenFeign" is the library name; `@FeignClient` is the annotation you use in code. They refer to the same mechanism.

**When to use it:** When Service A needs an **immediate answer** from Service B before it can continue.

**Example in this project:**
- Quiz Service needs to verify a student is enrolled before letting them take a quiz.
  It calls Enrollment Service via FeignClient and waits for YES/NO.
- If Enrollment Service is down → the call fails immediately (tight coupling).

```
Course Service   ──── HTTP GET ──→   Enrollment Service
                 ←── response ────
     (waits here, cannot continue until response arrives)
```

---

### RabbitMQ — Asynchronous (fire and forget, message queue)

**What it is:** A **message broker** that sits between services. Service A drops a message into a queue and immediately moves on. Service B picks up that message whenever it's ready and processes it independently.

**When to use it:** When Service A does NOT need an immediate response, and both services should remain **independent** of each other.

**Example in this project:**
- When a student pays for a course, the Course Service publishes a "payment completed" event to RabbitMQ and immediately returns a response to the user.
- The Enrollment Service is listening on that queue. It picks up the message and creates the enrollment. If it crashes and restarts, the message is still in the queue — nothing is lost.

```
Course Service  ──→  [RabbitMQ Queue]  ──→  Enrollment Service
    (moves on                                (processes when ready,
     immediately)                             independently)
```

### Summary Table

| Feature          | OpenFeign / FeignClient          | RabbitMQ                        |
|------------------|----------------------------------|---------------------------------|
| Type             | Synchronous HTTP                 | Asynchronous message queue      |
| Service B down?  | Caller fails immediately         | Message stays in queue, safe    |
| Caller waits?    | YES — blocks until response      | NO — fires and forgets          |
| Use when         | Need immediate answer            | Fire-and-forget / event-driven  |
| Code annotation  | `@FeignClient` + interface       | `@RabbitListener` / `rabbitTemplate.convertAndSend()` |
| Same thing as    | "Feign Client" = same thing      | AMQP, message broker            |

---

## 3. RABBITMQ — ALL SCENARIOS IN THIS PROJECT

### Scenario 1: Payment Completed → Create Enrollment
**Publisher:** Course Service  
**Consumer:** Enrollment Service

**What happens:** When Stripe confirms a payment, the Course Service publishes a `PaymentCompletedEvent`. The Enrollment Service listens and automatically creates the student's enrollment record.

**Publisher code:**
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Controllers/PaymentController.java
Lines 177-190

rabbitTemplate.convertAndSend(
    "payment.exchange",         // exchange name
    "payment.completed",        // routing key
    new PaymentCompletedEvent(studentId, courseId, paymentIntentId, pathId)
);
```

**Consumer code:**
```
File: YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/messaging/PaymentEventListener.java
Lines 25-61

@RabbitListener(queues = "payment.completed.queue")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // Creates Enrollment with status ACTIVE
    // Handles single course OR full learning path
}
```

**RabbitMQ Config files:**
```
Publisher config: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Config/RabbitMQConfig.java
Consumer config:  YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/config/RabbitMQConfig.java
```

---

### Scenario 2: Enrollment 100% Complete → Generate Certificate
**Publisher:** Enrollment Service  
**Consumer:** Course Service

**What happens:** When a student finishes all lessons in a course (100% progress), the Enrollment Service publishes an `EnrollmentCompletedEvent`. The Course Service listens, generates a PDF certificate, and writes the certificate ID back to the enrollment via FeignClient.

**Publisher code:**
```
File: YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/services/EnrollmentServiceImpl.java
Lines 178-186

rabbitTemplate.convertAndSend(
    "enrollment.exchange",
    "enrollment.completed",
    new EnrollmentCompletedEvent(enrollmentId, studentId, courseId)
);
```

**Consumer code:**
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Messaging/EnrollmentEventListener.java
Lines 19-38

@RabbitListener(queues = "enrollment.completed.queue")
public void handleEnrollmentCompleted(EnrollmentCompletedEvent event) {
    String certId = certificateService.generateCertificate(...);
    enrollmentClient.updateCertificate(event.getEnrollmentId(), certId); // FeignClient call
}
```

---

### Scenario 3: Course Deleted → Delete All Its Quizzes
**Publisher:** Course Service  
**Consumer:** Quiz Service

**What happens:** When an instructor deletes a course, the Course Service publishes a `CourseDeletedEvent`. The Quiz Service listens and deletes all quizzes that belonged to that course — automatic cleanup, no tight coupling.

**Publisher code:**
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Services/CourseServiceImpl.java
Lines 221-228

rabbitTemplate.convertAndSend(
    "course.exchange",
    "course.deleted",
    new CourseDeletedEvent(courseId, courseTitle)
);
```

**Consumer code:**
```
File: YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/messaging/CourseEventListener.java
Lines 18-31

@RabbitListener(queues = RabbitMQConfig.COURSE_DELETED_QUEUE)
public void handleCourseDeleted(CourseDeletedEvent event) {
    quizService.deleteQuizzesByCourse(event.getCourseId());
}
```

---

## 4. OPENFEIGN / FEIGNCLIENT — ALL SCENARIOS IN THIS PROJECT

### How FeignClient is enabled
Each service that uses FeignClient has `@EnableFeignClients` on its main application class:
```
Course Service:     YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/TpFoyerApplication.java (line 10)
Enrollment Service: YBRAINY/Enrollment/enrollment-service/.../EnrollmentServiceApplication.java (line 10)
Lesson Service:     YBRAINY/Lesson/lesson-service/.../LessonServiceApplication.java (line 10)
Quiz Service:       YBRAINY/Quiz/quiz-service/.../QuizServiceApplication.java (line 10)
```

---

### Course Service FeignClients (4 clients)

#### Client 1: EnrollmentClient
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/EnrollmentClient.java

@FeignClient(name = "enrollment-service")
public interface EnrollmentClient {
    GET  /api/enrollments                              → get all enrollments
    POST /api/enrollments                              → create enrollment
    GET  /api/enrollments/student/{studentId}          → get student's enrollments
    GET  /api/enrollments/exists                       → check if enrolled
    DELETE /api/enrollments/course/{courseId}          → delete course enrollments
    POST /api/enrollments/course/{courseId}/lessons/{lessonId}/complete → mark lesson done
    POST /api/enrollments/{enrollmentId}/certificate   → write certificate ID back
}
```
Used for: checking enrollment before payment, writing certificate ID after generation.

#### Client 2: LessonClient
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/LessonClient.java

@FeignClient(name = "lesson-service")
public interface LessonClient {
    GET    /api/lessons/course/{courseId}              → list lessons
    POST   /api/lessons/course/{courseId}              → create lesson
    PUT    /api/lessons/course/{courseId}/lesson/{id}  → update lesson
    GET    /api/lessons/course/{courseId}/count        → lesson count
    DELETE /api/lessons/course/{courseId}/lesson/{id}  → delete lesson
    POST   /api/lessons/progress                       → track progress
    DELETE /api/lessons/course/{courseId}/all          → delete all lessons (on course delete)
}
```
Used for: managing lessons as part of course management, cascading delete when course is deleted.

#### Client 3: QuizClient
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/QuizClient.java

@FeignClient(name = "quiz-service")
public interface QuizClient {
    DELETE /api/quizzes/course/{courseId}              → delete quizzes by course
    GET    /api/quizzes/best-score                     → get best quiz score
    GET    /api/quizzes/student/{studentId}/avg-score  → get average score
}
```
Used for: cascading delete when course is deleted, showing quiz performance on dashboards.

#### Client 4: UserClient
```
File: YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/UserClient.java

@FeignClient(name = "user-service")
public interface UserClient {
    GET /api/users/internal/{id}  → get user profile by ID
}
```
Used for: fetching user details (name, email) when needed inside course logic.

---

### Enrollment Service FeignClients (2 clients)

#### Client 1: CourseClient
```
File: YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/clients/CourseClient.java

@FeignClient(name = "course-service")
public interface CourseClient {
    GET /api/courses/{id}/exists  → verify course exists before enrolling
    GET /api/courses/{id}         → get course details
}
```

#### Client 2: LessonClient
```
File: YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/clients/LessonClient.java

@FeignClient(name = "lesson-service")
public interface LessonClient {
    GET  /api/lessons/course/{courseId}                           → list all lessons
    GET  /api/lessons/course/{courseId}/count                     → total lesson count
    POST /api/lessons/progress                                    → record lesson completion
    POST /api/lessons/progress/time                               → record time spent
    GET  /api/lessons/progress/enrollment/{enrollmentId}          → get full progress
    GET  /api/lessons/progress/enrollment/{enrollmentId}/completed-count → count completed
}
```
Used for: calculating percentage progress (completed lessons / total lessons = completion %).

---

### Lesson Service FeignClients (1 client)

#### Client 1: CourseClient
```
File: YBRAINY/Lesson/lesson-service/src/main/java/tn/esprit/lessonservice/clients/CourseClient.java

@FeignClient(name = "course-service")
public interface CourseClient {
    GET /api/courses/{id}/exists  → verify course exists before creating a lesson
}
```
Used for: validation — cannot create a lesson for a course that does not exist.

---

### Quiz Service FeignClients (3 clients)

#### Client 1: UserClient
```
File: YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/clients/UserClient.java

@FeignClient(name = "user-service")
```

#### Client 2: EnrollmentClient
```
File: YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/clients/EnrollmentClient.java

@FeignClient(name = "enrollment-service")
public interface EnrollmentClient {
    GET /api/enrollments/exists  → check student is enrolled before allowing quiz attempt
}
```

#### Client 3: CourseClient
```
File: YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/clients/CourseClient.java

@FeignClient(name = "course-service")
public interface CourseClient {
    GET /api/courses/{id}/exists  → verify course exists
}
```

---

## 5. HOW MICROSERVICE COMMUNICATION WORKS END-TO-END

### Step 1: Service Registration (Eureka)
When any service starts up, it registers itself with Eureka at `http://localhost:8761/eureka/`.
It says: "I am `course-service`, I'm running on this host and port."

### Step 2: Service Discovery (OpenFeign)
When Course Service wants to call Enrollment Service via FeignClient:
- The `@FeignClient(name = "enrollment-service")` annotation tells Feign to look up `enrollment-service` in Eureka
- Eureka returns the actual IP:port
- Feign makes the HTTP call to that address
- The `lb://` prefix in gateway routes does the same thing for frontend calls

### Step 3: Two communication patterns run in parallel

**Synchronous (OpenFeign) — used for validations and queries:**
```
Request comes in → need immediate data from another service → FeignClient call → wait → continue
```
Example: Student clicks "Take Quiz" → Quiz Service asks Enrollment Service "is this student enrolled?" → YES → allow quiz

**Asynchronous (RabbitMQ) — used for events that trigger workflows:**
```
Something important happens → publish event to RabbitMQ queue → move on
                                         ↓
                              (other service picks it up independently)
```
Example: Stripe payment confirmed → publish event → return response to user immediately → Enrollment Service creates enrollment in background

---

## 6. FRONTEND ↔ API GATEWAY — COMPLETE EXPLANATION

### The two key files

#### File 1: proxy.conf.json
```
Location: user/angular/angular-app/proxy.conf.json
```
This is an Angular **development proxy**. When the Angular dev server runs on `localhost:4200`, every HTTP request that starts with `/api/courses` would normally fail due to CORS (different origins). The proxy intercepts it and forwards to the Gateway.

**How it works (the 4 main services):**
```json
"/api/courses":     → "target": "http://localhost:8088"  (API Gateway)
"/api/quizzes":     → "target": "http://localhost:8088"  (API Gateway)
"/api/enrollments": → "target": "http://localhost:8088"  (API Gateway)
"/api/learning-paths": → "target": "http://localhost:8088"  (API Gateway)
"/api/ml":          → "target": "http://localhost:8088"  (API Gateway)
"/api/certificates": → "target": "http://localhost:8088"  (API Gateway)
```

**Full request flow (example: load courses page):**
```
1. Angular component calls:  this.http.get('/api/courses')
2. Browser sends to:         http://localhost:4200/api/courses
3. proxy.conf.json catches:  /api/courses → forward to http://localhost:8088
4. API Gateway receives:     GET http://localhost:8088/api/courses
5. Gateway reads routes:     routes[5]: Path=/api/courses/** → lb://course-service
6. Eureka resolves:          course-service = localhost:8082
7. Request reaches:          http://localhost:8082/api/courses
8. Response travels back:    8082 → 8088 → 4200 → browser
```

#### File 2: environment.ts
```
Location: user/angular/angular-app/src/environments/environment.ts
```
This sets the base URL used in Angular services. In development, `apiBaseUrl` is empty string `''` — meaning all API calls go to a relative path like `/api/courses` which the proxy handles. The `apiUrl: 'http://localhost:8095/api'` is used only for some older parts of the project.

### Code to show the professor (Angular → Gateway connection)

**1. Show the proxy config (Angular side):**
```
user/angular/angular-app/proxy.conf.json  (lines 68-103)
```
Show lines 68-103 specifically — that's `/api/courses`, `/api/quizzes`, `/api/enrollments`, all pointing to `http://localhost:8088` (the Gateway).

**2. Show the Angular project config that loads the proxy:**
```
user/angular/angular-app/angular.json
```
Look for `"proxyConfig": "proxy.conf.json"` — this tells the Angular CLI to use the proxy when running `ng serve`.

**3. Show the Gateway routes (Spring side):**
```
user/p-r-k/ApiGateway/ApiGateway/src/main/resources/application.properties
Lines 52-95
```
Show lines 52-95: these are the route definitions for course-service, quiz-service, enrollment-service, lesson-service.

**4. Show SecurityConfig (what is allowed without login):**
```
user/p-r-k/ApiGateway/ApiGateway/src/main/java/tn/esprit/apigateway/SecurityConfig.java
Lines 31-56
```

---

## 7. WHERE IS THE RABBBITMQ AND FEIGN CODE — FOR THE PROFESSOR

### Show RabbitMQ:

| What to show | File | Lines |
|---|---|---|
| RabbitMQ config (exchanges, queues) | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Config/RabbitMQConfig.java` | All |
| Publishing payment event | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Controllers/PaymentController.java` | 177-190 |
| Publishing course deleted event | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Services/CourseServiceImpl.java` | 221-228 |
| Listening for payment (creates enrollment) | `YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/messaging/PaymentEventListener.java` | All |
| Publishing enrollment completed | `YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/services/EnrollmentServiceImpl.java` | 178-186 |
| Listening for enrollment → certificate | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Messaging/EnrollmentEventListener.java` | All |
| Listening for course deleted → cleanup quizzes | `YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/messaging/CourseEventListener.java` | All |

### Show OpenFeign / FeignClient:

| What to show | File |
|---|---|
| `@EnableFeignClients` on main class | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/TpFoyerApplication.java` |
| FeignClient calling Enrollment | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/EnrollmentClient.java` |
| FeignClient calling Lesson | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/LessonClient.java` |
| FeignClient calling Quiz | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/QuizClient.java` |
| FeignClient calling User | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Clients/UserClient.java` |
| Quiz checks enrollment via Feign | `YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/clients/EnrollmentClient.java` |
| Enrollment checks course via Feign | `YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/clients/CourseClient.java` |
| Enrollment tracks lessons via Feign | `YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/clients/LessonClient.java` |
| Lesson validates course via Feign | `YBRAINY/Lesson/lesson-service/src/main/java/tn/esprit/lessonservice/clients/CourseClient.java` |

### Count Summary:

| Technology | Scenarios / Usages |
|---|---|
| **RabbitMQ (publish)** | 3 (payment, enrollment-complete, course-deleted) |
| **RabbitMQ (consume/listen)** | 3 (one per publisher above, in different services) |
| **FeignClient interfaces** | 9 total (4 in Course, 2 in Enrollment, 1 in Lesson, 3 in Quiz) |
| **@EnableFeignClients** | 4 services (Course, Enrollment, Lesson, Quiz) |

---

## 8. SWAGGER DOCUMENTATION — ALL 4 SERVICES

All 4 services have Swagger UI enabled. Each service has:
- An `OpenApiConfig.java` configuration class
- `springdoc-openapi-starter-webmvc-ui` v2.1.0 in pom.xml
- Swagger enabled in `application.properties`

### URLs to show the professor (services must be running):

| Service | Swagger UI URL | API Docs JSON |
|---|---|---|
| **Course Service** | `http://localhost:8082/swagger-ui.html` | `http://localhost:8082/v3/api-docs` |
| **Quiz Service** | `http://localhost:8083/swagger-ui.html` | `http://localhost:8083/v3/api-docs` |
| **Lesson Service** | `http://localhost:8084/swagger-ui.html` | `http://localhost:8084/v3/api-docs` |
| **Enrollment Service** | `http://localhost:8085/swagger-ui.html` | `http://localhost:8085/v3/api-docs` |

### Config file locations:

| Service | OpenApiConfig.java | application.properties lines |
|---|---|---|
| Course | `YBRAINY/Course/tp-foyer/src/main/java/tn/esprit/tpfoyer/Config/OpenApiConfig.java` | Lines 46-49 |
| Quiz | `YBRAINY/Quiz/quiz-service/src/main/java/tn/esprit/quizservice/config/OpenApiConfig.java` | Lines 21-24 |
| Lesson | `YBRAINY/Lesson/lesson-service/src/main/java/tn/esprit/lessonservice/config/OpenApiConfig.java` | Lines 32-35 |
| Enrollment | `YBRAINY/Enrollment/enrollment-service/src/main/java/tn/esprit/enrollmentservice/config/OpenApiConfig.java` | Lines 35-38 |

### How to show Swagger to the professor:
1. Start the service (e.g. Course Service on port 8082)
2. Open browser → `http://localhost:8082/swagger-ui.html`
3. You will see a full interactive UI with all REST endpoints
4. You can expand any endpoint, fill in parameters, click "Try it out" → "Execute" to test live
5. This proves the API is documented and testable without Postman

---

## 9. COMPLETE SERVICE PORT REFERENCE

| Service | Port | Technology | DB |
|---|---|---|---|
| Angular Frontend | 4200 | Angular 17 | — |
| API Gateway | 8088 | Spring Cloud Gateway | — |
| Eureka Server | 8761 | Spring Cloud Netflix | — |
| Keycloak (Auth) | 9190 | Keycloak | — |
| RabbitMQ | 5672 (AMQP) / 15672 (UI) | RabbitMQ | — |
| Course Service | 8082 | Spring Boot 3.3.1 | ybrainy_courses (MySQL) |
| Quiz Service | 8083 | Spring Boot 3.3.1 | ybrainy_quiz (MySQL) |
| Lesson Service | 8084 | Spring Boot 3.3.1 | ybrainy_lessons (MySQL) |
| Enrollment Service | 8085 | Spring Boot 3.3.1 | ybrainy_enrollments (MySQL) |
| Notes Service | 8084 (Python) | FastAPI | ybrainy_notes (PostgreSQL) |
| User Service | 8899 | Spring Boot | ybrainy_users (MySQL) |

> **RabbitMQ Management UI:** Open `http://localhost:15672` (user: guest / pass: guest) to see queues, exchanges, and live message flow while the app is running.
