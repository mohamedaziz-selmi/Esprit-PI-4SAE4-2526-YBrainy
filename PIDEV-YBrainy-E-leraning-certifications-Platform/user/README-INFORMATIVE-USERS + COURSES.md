# YBrainy — E-Learning & Certification Platform
### Branch: `user-management-coursesINTEGRATED`
### Author: Mohamed Aziz Selmi — ESPRIT 4th Year Software Engineering

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Keycloak Setup](#keycloak-setup)
5. [Database Setup](#database-setup)
6. [Launching Every Service](#launching-every-service)
7. [ML Service Setup](#ml-service-setup)
8. [Angular Frontend Setup](#angular-frontend-setup)
9. [Full Launch Order](#full-launch-order)
10. [ML Features — DSO1 to DSO4](#ml-features--dso1-to-dso4)
11. [AI Features](#ai-features)
12. [Platform Features Tour](#platform-features-tour)
13. [API Reference](#api-reference)
14. [Troubleshooting](#troubleshooting)

---

## Project Overview

YBrainy is a full-stack e-learning platform built as a 4th-year ESPRIT engineering project. It allows students to discover, enroll in, and complete courses, take quizzes, earn certificates, and receive AI-powered personalized learning experiences.

This branch (`user-management-coursesINTEGRATED`) contains:
- **Courses microservice** — full course lifecycle management
- **Quiz microservice** — extracted from the course service, runs independently
- **ML microservice** — 4 data science objects (DSO1–DSO4) using real ML models
- **Merged Angular frontend** — integrates with the colleague's user management & Keycloak auth
- **Gateway configuration** — updated routes and CORS for all services

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANGULAR FRONTEND                         │
│                    http://localhost:4200                         │
│         (Frontoffice for students + Backoffice for admins)      │
└──────────────────────────┬──────────────────────────────────────┘
                           │ All API calls go through Gateway
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (Spring)                        │
│                    http://localhost:8088                         │
│              Routes + JWT validation + CORS                     │
└──┬──────────┬──────────┬──────────┬──────────┬─────────────────┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────────┐
│Eureka│ │Course│ │Quiz  │ │User  │ │   Keycloak   │
│:8071 │ │:8082 │ │:8083 │ │:8899 │ │   :9190      │
└──────┘ └──┬───┘ └──────┘ └──────┘ └──────────────┘
            │
            ▼
    ┌───────────────┐
    │  ML Service   │
    │  Flask :5000  │
    │  DSO1–DSO4    │
    └───────────────┘
```

### Services Summary

| Service | Port | Technology | Responsibility |
|---------|------|------------|----------------|
| Eureka | 8071 | Spring Boot | Service discovery |
| API Gateway | 8088 | Spring Cloud Gateway | Routing, JWT auth, CORS |
| Course Service | 8082 | Spring Boot + JPA | Courses, lessons, enrollments, reviews, certificates |
| Quiz Service | 8083 | Spring Boot + JPA | Quizzes, questions, attempts, leaderboard |
| User Service | 8899 | Spring Boot | User management (colleague's module) |
| ML Service | 5000 | Flask + scikit-learn | DSO1–DSO4 ML models |
| Keycloak | 9190 | Keycloak 23+ | Authentication, JWT tokens |
| Angular | 4200 | Angular 18 | Frontend (frontoffice + backoffice) |

---

## Prerequisites

Install everything below before attempting to run the project.

### Java & Maven
- **Java 17** (minimum) — download from https://adoptium.net
- **Maven 3.9+** — download from https://maven.apache.org/download.cgi
- Verify: `java -version` and `mvn -version`

### Python
- **Python 3.11** — download from https://python.org/downloads
- Verify: `python --version`

### Node.js & Angular CLI
- **Node.js 18+** — download from https://nodejs.org
- **Angular CLI 18**: `npm install -g @angular/cli@18`
- Verify: `node --version` and `ng version`

### Keycloak
- **Keycloak 23.0.7** — download from https://www.keycloak.org/downloads
- Extract to any folder, e.g. `C:\keycloak`

### MySQL
- **MySQL 8.0+** — download from https://dev.mysql.com/downloads/mysql
- Install MySQL Workbench for easier management
- Default port: 3306

### Git
- **Git** — download from https://git-scm.com/downloads

---

## Keycloak Setup

This is the most important step. Without Keycloak running correctly, no login will work.

### Step 1 — Start Keycloak

Open a terminal in the Keycloak folder and run:

**Windows:**
```cmd
cd C:\keycloak\bin
kc.bat start-dev --http-port=9190
```

**Mac/Linux:**
```bash
cd ~/keycloak/bin
./kc.sh start-dev --http-port=9190
```

Wait until you see: `Keycloak 23.x.x on JVM (powered by Quarkus)`

### Step 2 — Create Admin Account

Open http://localhost:9190 in your browser. On first launch, you'll be asked to create an admin user:
- Username: `admin`
- Password: `admin` (or whatever you choose — remember it)

### Step 3 — Create the Realm

1. Click **"Create Realm"** (top left dropdown)
2. Realm name: **`microservices`** (must be exactly this)
3. Click **Create**

### Step 4 — Create the Client

1. In the `microservices` realm, go to **Clients** → **Create client**
2. Client ID: **`angular-client`** (must be exactly this)
3. Client type: **OpenID Connect**
4. Click **Next**
5. Enable **Standard flow** and **Direct access grants**
6. Click **Next**
7. Valid redirect URIs: `http://localhost:4200/*`
8. Web origins: `http://localhost:4200`
9. Click **Save**

### Step 5 — Configure the Client

1. Open the `angular-client` client
2. Go to **Settings** tab
3. Set **Access Type** to `public`
4. Set **Valid Redirect URIs** to `http://localhost:4200/*`
5. Set **Web Origins** to `*`
6. Click **Save**

### Step 6 — Create Roles

1. Go to **Realm roles** → **Create role**
2. Create these roles one by one:
   - `ADMIN`
   - `INSTRUCTOR`
   - `STUDENT`

### Step 7 — Create Test Users

Go to **Users** → **Add user** for each:

**Admin user:**
- Username: `admin_user`
- Email: any email
- Click **Create** → go to **Credentials** tab → set password → toggle **Temporary** to OFF
- Go to **Role Mappings** → assign role `ADMIN`

**Instructor user:**
- Username: `instructor_user`
- Same process → assign role `INSTRUCTOR`

**Student user:**
- Username: `student_user`
- Same process → assign role `STUDENT`

### Step 8 — Verify Keycloak Works

Open this URL in your browser:
```
http://localhost:9190/realms/microservices/.well-known/openid-configuration
```
You should see a large JSON response. If you do, Keycloak is configured correctly.

---

## Database Setup

### Create Databases

Open MySQL Workbench or run these commands in MySQL terminal:

```sql
CREATE DATABASE ybrainy_courses CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE ybrainy_quiz CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE ybrainy_users CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Database Credentials

The services use these defaults. If your MySQL has a different password, update the `application.properties` for each service.

| Service | Database | Username | Password |
|---------|----------|----------|----------|
| Course Service | ybrainy_courses | root | (your MySQL root password) |
| Quiz Service | ybrainy_quiz | root | (your MySQL root password) |
| User Service | ybrainy_users | root | (your MySQL root password) |

### Update application.properties

For **Course Service**:
```
C:\...\YBRAINY\Course\tp-foyer\src\main\resources\application.properties
```
Change:
```properties
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

For **Quiz Service**:
```
C:\...\YBRAINY\Quiz\quiz-service\src\main\resources\application.properties
```
Same change.

The tables are created automatically by Spring Boot JPA (`spring.jpa.hibernate.ddl-auto=update`).

---

## Launching Every Service

Launch services **in this exact order**. Each one must be fully started before launching the next.

---

### 1. Keycloak (already covered above)
Port: **9190**
Wait for: `Keycloak 23.x.x on JVM`

---

### 2. Eureka (Service Discovery)

```bash
cd "C:\...\PIDEV-YBrainy-E-leraning-certifications-Platform-UserManagelent\p-r-k\Eureka\Eureka"
mvn spring-boot:run
```

Wait for: `Started EurekaApplication` and `Eureka Server is now running`

Open http://localhost:8071 to confirm Eureka dashboard is up.

---

### 3. API Gateway

```bash
cd "C:\...\PIDEV-YBrainy-E-leraning-certifications-Platform-UserManagelent\p-r-k\ApiGateway\ApiGateway"
mvn spring-boot:run
```

Wait for: `Started ApiGatewayApplication`

If you made code changes, rebuild first:
```bash
mvn clean package -DskipTests
java -jar target/api-gateway-0.0.1-SNAPSHOT.jar
```

---

### 4. User Service (Colleague's Module)

```bash
cd "C:\...\PIDEV-YBrainy-E-leraning-certifications-Platform-UserManagelent\p-r-k\UserService\UserService"
mvn spring-boot:run
```

Wait for: `Started UserServiceApplication`
Port: **8899**

---

### 5. Course Service

```bash
cd "C:\...\YBRAINY\Course\tp-foyer"
mvn clean package -DskipTests
java -jar target/courses-service-0.0.1-SNAPSHOT.jar
```

Wait for: `Started TpFoyerApplication`
Port: **8082**

Verify it's running:
```bash
curl http://localhost:8082/api/courses?size=1
```
Should return a JSON response with courses.

---

### 6. Quiz Service

```bash
cd "C:\...\YBRAINY\Quiz\quiz-service"
mvn clean package -DskipTests
java -jar target/quiz-service-0.0.1-SNAPSHOT.jar
```

Wait for: `Started QuizServiceApplication`
Port: **8083**

---

### 7. ML Service (Flask)

#### First time setup — install dependencies:

```bash
cd "C:\...\YBRAINY\ML-Service"
pip install flask==3.0.3 flask-cors==4.0.0 scikit-learn==1.5.1 joblib==1.4.2 numpy==1.26.4 pandas==2.2.2 statsmodels==0.14.2 requests==2.31.0 --break-system-packages
```

Or with conda:
```bash
conda install flask scikit-learn joblib numpy pandas statsmodels requests
pip install flask-cors
```

#### Train the models (first time only):

```bash
cd "C:\...\YBRAINY\ML-Service"
python generate_and_train.py
```

This takes 3–5 minutes and creates all model `.pkl` files in the `models/` folder.
You should see: `ALL MODELS TRAINED AND SAVED ✅`

#### Start Flask:

```bash
cd "C:\...\YBRAINY\ML-Service"
python app.py
```

Wait for: `Running on http://127.0.0.1:5000`

Verify all models loaded:
```bash
curl http://localhost:5000/health
```
Expected response:
```json
{
  "models": {
    "dso1_conversion": true,
    "dso2_recommendations": true,
    "dso3_quality": true,
    "dso4_forecast": true
  },
  "status": "ok"
}
```

---

## Angular Frontend Setup

### Install Dependencies (first time only)

```bash
cd "C:\...\PIDEV-YBrainy-E-leraning-certifications-Platform-UserManagelent\angular\angular-app"
npm install
```

This installs all Node.js packages. Takes 2–5 minutes.

### Start Angular

```bash
cd "C:\...\PIDEV-YBrainy-E-leraning-certifications-Platform-UserManagelent\angular\angular-app"
ng serve --port 4200 --proxy-config proxy.conf.json
```

Wait for: `Application bundle generation complete` and `Local: http://localhost:4200/`

Open http://localhost:4200 in your browser.

### proxy.conf.json

This file routes all `/api` calls from Angular to the Gateway. Do not modify it:
```json
{
  "/api": {
    "target": "http://localhost:8088",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

---

## Full Launch Order

Here is the complete checklist to launch everything from scratch:

```
[ ] 1. Start MySQL server
[ ] 2. Start Keycloak:          kc.bat start-dev --http-port=9190
[ ] 3. Start Eureka:            mvn spring-boot:run (port 8071)
[ ] 4. Start API Gateway:       mvn spring-boot:run (port 8088)
[ ] 5. Start User Service:      mvn spring-boot:run (port 8899)
[ ] 6. Start Course Service:    java -jar target/courses-service-0.0.1-SNAPSHOT.jar (port 8082)
[ ] 7. Start Quiz Service:      java -jar target/quiz-service-0.0.1-SNAPSHOT.jar (port 8083)
[ ] 8. Start ML Service:        python app.py (port 5000)
[ ] 9. Start Angular:           ng serve --port 4200 --proxy-config proxy.conf.json
[ ] 10. Open http://localhost:4200
```

Total startup time: approximately 5–8 minutes.

---

## ML Features — DSO1 to DSO4

This branch implements 4 complete Data Science Objects (DSOs) with real ML models trained on 200,000 synthetic courses and 50,000 synthetic students calibrated from Udemy and OULAD datasets.

---

### DSO1 — Conversion Predictor

**What it does:** Predicts whether a student is likely to convert from free to paid courses based on their behavior on the platform.

**Algorithm:** RandomForestClassifier (300 trees, max_depth=12, class_weight='balanced')

**Features used:**
| Feature | Source | Description |
|---------|--------|-------------|
| timeSpent | LessonProgress | Total minutes spent on lessons |
| completionRate | Enrollment | Average completion % across all courses |
| quizScores | Quiz Service | Average quiz score (default 50 if no attempts) |
| videosWatched | LessonProgress | Number of video lessons completed |
| numLogins | LessonProgress | Total lesson interaction records |
| forumReads | Hardcoded | Always 0 (no forum feature yet) |

**Output:** `conversionLabel` (LOW / MEDIUM / HIGH) + `conversionProbability` (0–1) + `percentage`

**Where to see it:** Log in as a student → go to `http://localhost:4200/my-learning` → "AI Learning Insight" card

**API endpoint:** `GET /api/ml/student/{studentId}/conversion`

---

### DSO2 — Course Recommender

**What it does:** Recommends courses personalized to each student based on their enrollment history, using KNN cosine similarity.

**Algorithm:** NearestNeighbors (cosine metric, n_neighbors=11) trained on 200K synthetic courses × 10 features. Category is weighted ×5 to prioritize subject match over other features.

**How it works:**
1. Angular sends the logged-in student's ID to Spring Boot
2. Spring Boot fetches all the student's enrollments from the DB
3. Finds the dominant category (most frequent across all enrollments)
4. Sends dominant category + level to Flask
5. Flask fetches all real courses from the DB
6. Encodes each course using the same 10 features as training
7. Computes cosine similarity between the query vector and all courses
8. Returns top N courses with real match scores (0–100)
9. Angular filters out courses the student is already enrolled in

**Features used:** category (×5 weighted), level, lessonCount, contentDuration, lessonTypeVariety, pctVideoLessons, offersCertificate, price, rating, isPaid

**Output:** List of recommended courses with real `matchScore` (cosine similarity × 100)

**Where to see it:** Log in as a student → go to `http://localhost:4200/courses` → click the "AI Recommendations" pulsing button at the top → holographic overlay appears with personalized course recommendations

**API endpoint:** `GET /api/ml/recommendations?studentId={id}&topN=5`

---

### DSO3 — Course Quality Scorer

**What it does:** Analyzes the structural quality of a course and gives instructors specific, actionable feedback on how to improve it. Also shows a quality badge on every course card for students.

**Algorithm:** RandomForestClassifier (100 trees, max_depth=15) trained on 200K synthetic courses

**Feature importances (from training):**
| Feature | Importance | What it measures |
|---------|------------|-----------------|
| numLectures | 35.2% | Number of lessons |
| offersCertificate | 25.3% | Whether course offers a certificate |
| rating | 23.9% | Average student rating |
| lessonTypeVariety | 14.1% | Number of distinct content types |
| Others | 1.4% | Duration, video ratio, etc. |

**Output:** `qualityLabel` (HIGH/LOW) + `overallScore` (0–100) + `factors` breakdown + `tips` array

**Where to see it:**
- **Students:** Quality badge ("📋 Standard" or "⚡ Verified Quality") on every course card at `http://localhost:4200/courses`. Hover the badge for confidence %.
- **Instructors/Admins:** Go to `http://localhost:4200/dashboard/courses` → find any course card → click the blue analytics button (📊) → "AI Quality Report" modal shows:
  - Overall quality score (0–100) with color coding
  - Factor breakdown bars (each dimension scored separately)
  - 💬 Student Sentiment Summary (AI-generated from real reviews via OpenRouter)
  - 🚀 AI Improvement Roadmap (3-step personalized action plan via OpenRouter)

**API endpoints:**
- Single course: `GET /api/ml/course/{courseId}/quality`
- Batch: `POST /api/ml/courses/quality-batch` with body `[id1, id2, ...]`

---

### DSO4 — Demand Forecaster

**What it does:** Forecasts future enrollment demand for the next N periods using time series analysis. Shows both global demand and per-category breakdown.

**Algorithm:** SARIMA(1,1,1)(1,1,1,12) — Seasonal ARIMA with 12-month seasonality. Trained on 60-month synthetic enrollment series calibrated to realistic e-learning growth patterns (500 → 1385 enrollments/month upward trend with seasonal variation).

**Data blending:** When real enrollment data has 6+ months, it blends real enrollment counts into the synthetic series for the most recent months. This means the model improves automatically as real usage data accumulates.

**Output:**
- `predictedDemand` — array of predicted enrollment counts (6 periods)
- `confidenceIntervals` — 80% confidence bands for each period
- `trendDirection` — "growing" or "declining"
- `trendPercentage` — percentage change over forecast window
- `modelUsed` — "SARIMA(1,1,1)(1,1,1,12)" or "ARIMA(1,1,1)" fallback
- `realDataPoints` — number of real months used in training
- `categoryForecast` — per-category demand breakdown with market share %

**Where to see it:** Log in as admin or instructor → go to `http://localhost:4200/dashboard/courses` → click the **📈 Forecast (next)** stat card → expands to show:
- Bar chart with 6 forecast periods
- Trend direction and percentage
- Category breakdown sorted by demand (PROGRAMMING → DESIGN → etc.)
- Model info at the bottom (SARIMA + real data points count)

**API endpoint:** `GET /api/ml/forecast?steps=6`

---

## AI Features

Beyond the DSOs, this branch includes 3 additional AI-powered features using OpenRouter (LLM API):

### AI Search
**What:** Natural language course search. Type in plain English like "I want to learn React for building web apps" and the AI extracts the intent, category, and level, then queries the database.

**Model:** stepfun/step-3.5-flash:free via OpenRouter

**Where:** `http://localhost:4200/courses` → the purple search bar below the hero section

**How it works:** Angular → Spring Boot → OpenRouter extracts JSON intent → Spring Boot queries course DB with extracted category/level → returns results with AI explanation

### AI Learning Paths
**What:** Generates a personalized multi-course learning path based on the student's goal (e.g., "I want to become a full-stack developer").

**Where:** `http://localhost:4200/courses` → "Want a personalized course roadmap? Try AI Learning Paths →" banner

### AI Quiz Generation
**What:** Instructors can generate quiz questions automatically for any course using AI.

**Where:** `http://localhost:4200/dashboard/courses` → open any course → Quiz Manager → "Generate with AI" button

---

## Platform Features Tour

### For Students (login with STUDENT role)

1. **Browse courses** at `/courses` — filter by category, level, price, rating
2. **AI Recommendations** — click the pulsing button at top of courses page
3. **AI Search** — type natural language in the purple search bar
4. **Enroll in courses** — click "View Course" → "Enroll" button
5. **Watch lessons** — YouTube videos, PDFs, images all supported
6. **Take quizzes** — after completing lesson content
7. **View progress** — progress bar on enrolled courses
8. **My Learning dashboard** at `/my-learning` — see DSO1 conversion insight card
9. **Leaderboard** — quiz scores ranked against other students
10. **Reviews** — leave star ratings and comments on enrolled courses
11. **AI Learning Paths** — generate personalized roadmap

### For Instructors (login with INSTRUCTOR role)

1. **Dashboard** at `/dashboard/courses` — automatically redirected here
2. **Create courses** — fill form with title, category, level, price, description, thumbnail
3. **Add lessons** — support for YouTube URLs, PDF uploads, image uploads
4. **Generate quiz questions** — AI-powered quiz generation
5. **View analytics** — enrollment stats, completion rates per course
6. **AI Quality Report** — click the 📊 button on any course card
7. **Demand Forecast** — click 📈 Forecast stat card to see SARIMA forecast + category breakdown

### For Admins (login with ADMIN role)

Everything instructors can see, plus:
1. **User management** — manage all platform users
2. **All courses** — see and manage courses from all instructors
3. **Full backoffice** access

---

## API Reference

### Course Service (port 8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/courses | List all courses (paginated) |
| POST | /api/courses | Create course (multipart/form-data) |
| GET | /api/courses/{id} | Get course by ID |
| PUT | /api/courses/{id} | Update course |
| DELETE | /api/courses/{id} | Delete course |
| GET | /api/courses/{id}/lessons | List lessons for course |
| POST | /api/courses/{id}/lessons | Add lesson (multipart/form-data) |
| POST | /api/enrollments | Enroll student in course |
| GET | /api/enrollments/student/{studentId} | Get student's enrollments |
| POST | /api/courses/{id}/reviews | Add review |
| GET | /api/courses/{id}/reviews | Get course reviews |
| GET | /api/ml/student/{id}/conversion | DSO1 conversion prediction |
| GET | /api/ml/recommendations | DSO2 recommendations |
| GET | /api/ml/course/{id}/quality | DSO3 quality score |
| POST | /api/ml/courses/quality-batch | DSO3 batch quality |
| GET | /api/ml/forecast | DSO4 demand forecast |
| POST | /api/courses/search/ai | AI natural language search |
| POST | /api/learning-paths/generate | AI learning path generation |

### Quiz Service (port 8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/quizzes/course/{courseId} | Get quizzes for course |
| POST | /api/quizzes | Create quiz |
| POST | /api/quizzes/{id}/submit | Submit quiz attempt |
| GET | /api/quizzes/leaderboard/{courseId} | Course leaderboard |
| GET | /api/quizzes/student/{studentId}/avg-score | Student average quiz score |

### ML Service (port 5000)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /health | Check all models loaded |
| POST | /predict/conversion | DSO1 raw prediction |
| POST | /recommend | DSO2 raw recommendations |
| POST | /predict/quality | DSO3 raw quality score |
| GET | /forecast/demand | DSO4 raw forecast |

---

## Troubleshooting

### "Failed to fetch" errors in backoffice
**Cause:** Gateway not running or CORS not configured.
**Fix:** Make sure Gateway is running on port 8088. Check that `GatewayCorsConfig.java` has `setAllowedOriginPatterns(Collections.singletonList("*"))`.

### Login redirects back to login page
**Cause:** Keycloak realm or client not configured correctly.
**Fix:** Verify realm name is exactly `microservices` and client ID is `angular-client`. Check that redirect URIs include `http://localhost:4200/*`.

### "Quality response was empty" in AI Quality Report
**Cause:** Flask ML service not running or Course Service not connecting to Flask.
**Fix:** Restart Flask (`python app.py`), then restart Course Service. Verify Flask health: `curl http://localhost:5000/health`.

### ML models not loading
**Cause:** Models not trained yet or `.pkl` files missing.
**Fix:** Run `python generate_and_train.py` in the ML-Service directory. Wait for "ALL MODELS TRAINED AND SAVED".

### Angular won't start
**Cause:** Node modules not installed or Angular CLI not found.
**Fix:** Run `npm install` in the `angular-app` directory. Then `npm install -g @angular/cli@18`.

### Course Service won't start
**Cause:** MySQL not running or wrong database credentials.
**Fix:** Start MySQL. Update `application.properties` with correct MySQL username/password.

### "Eureka: localhost:8071 - retrying" in Gateway logs
**Cause:** Eureka not started before Gateway.
**Fix:** Always start Eureka first, wait for it to fully start, then start Gateway.

### DSO2 recommendations show wrong courses
**Cause:** Not enough real courses in DB with lessons and ratings.
**Fix:** This is expected with few courses. As instructors add more courses with lessons and students leave reviews, recommendations improve automatically. The KNN model uses cosine similarity — courses with no lessons score near zero.

### All courses showing "Standard" quality badge
**Cause:** Courses have fewer than 10 lessons. The DSO3 model was trained on courses with 10–300 lessons.
**Fix:** This is correct behavior — courses genuinely score LOW with few lessons. Add more lessons to courses to improve quality scores.

---

## Model Files

All ML model files are in `YBRAINY/ML-Service/models/`:

| File | Size | Description |
|------|------|-------------|
| dso1_conversion_model.pkl | ~21 MB | RandomForest for conversion prediction |
| dso1_scaler.pkl | ~1 KB | StandardScaler for DSO1 features |
| dso2_knn_model.pkl | ~15 MB | NearestNeighbors trained on 200K courses |
| dso2_course_df.pkl | ~22 MB | Synthetic course catalog (200K rows) |
| dso2_feature_matrix.pkl | ~15 MB | Pre-computed feature matrix |
| dso2_scaler.pkl | ~1 KB | StandardScaler for DSO2 features |
| dso2_le_level.pkl | ~1 KB | LabelEncoder for course levels |
| dso2_le_subject.pkl | ~1 KB | LabelEncoder for course categories |
| dso3_quality_model.pkl | ~10 MB | RandomForest for quality scoring |
| dso3_scaler.pkl | ~1 KB | StandardScaler for DSO3 features |
| dso4_arima_model.pkl | ~106 KB | SARIMA(1,1,1)(1,1,1,12) demand forecaster |

To retrain all models from scratch:
```bash
cd YBRAINY/ML-Service
python generate_and_train.py
```
Then restart Flask.

---

## Technical Notes for Colleagues

- **Do not add JWT/Spring Security to the Course Service** (tp-foyer). Security is handled entirely by the Gateway.
- **Gateway and Eureka are a colleague's modules** — minimize changes to these. The only changes made in this branch are to `SecurityConfig.java` (adding permitted paths) and `GatewayCorsConfig.java` (allowing all origins).
- **Angular uses FrontofficeModule** — no standalone components. All new components must be declared in `FrontofficeModule`.
- **Proxy config** — always start Angular with `--proxy-config proxy.conf.json` or the `/api` routes won't work.
- **localStorage keys** — the session uses `bb_user_session_v1` and `bb_keycloak_tokens_v1`. Do not change these key names.
- **OpenRouter API key** — stored in `application.properties` of the Course Service. The key is also set in `courses.html` at line 42 for client-side AI calls (quiz generation, quality report AI summaries).

---

*README written by Mohamed Aziz Selmi | YBrainy Platform | ESPRIT 4th Year | 2025–2026*
