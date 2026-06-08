🎓 YBrainy — E-Learning & Certification Platform

A full-stack AI-assisted e-learning platform built with Angular + Spring Boot Microservices that provides course packs, certifications, financial management, AI content generation, market intelligence scraping, and Stripe-based payments.

The system includes a modern Angular frontoffice/backoffice, a Spring Boot microservices architecture, and AI-powered automation tools.

🚀 Key Features
📚 E-Learning System

Pack categories and administration

🧑‍💼 Backoffice Dashboard

Pack management (CRUD)

Category management

Pack order history

Financial management (income / expenses)

🤖 AI Assisted Tools

AI pack content generation

Course recommendations

Text-to-Speech generation for packs

🧠 Market Intelligence

Python scraper that collects trending courses and certifications and generates datasets used for recommendations.

💳 Payments & Commerce

Stripe checkout integration

Cart system

Payment confirmation

Automatic HTML invoice email

🔐 Authentication

JWT authentication

Angular route guards

Secure API endpoints

🏗️ Architecture

The platform uses a microservices architecture with service discovery.

Angular Frontend (4200)
        │
        ▼
API Gateway (8092)
        │
        ▼
Spring Boot Backend (8091)
        │
 ┌──────┼─────────┐
 ▼      ▼         ▼
Finance  Packs   AI Services
        │
        ▼
Python Scraper (8093)

Service Discovery (Eureka - 8761)
📁 Project Structure
spring/
├── angular-app/
│   ├── src/app/frontoffice
│   ├── src/app/backoffice
│   └── environments
│
└── PIDEV-YBrainy-E-learning-certifications-Platform/
    ├── discovery-server/
    ├── api-gateway/
    ├── Payment and finance/
    ├── scraper/
    ├── Recommendations/
    └── Telegram bot/
🧰 Tech Stack
Frontend

Angular 18

TypeScript

Bootstrap

Backend

Java 17

Spring Boot 3

Spring Data JPA

Spring Security

Spring Mail

Spring Validation

Microservices Infrastructure

Eureka Discovery Server

Spring Cloud Gateway

Database

MySQL

AI / External APIs

RapidAPI AI Content Writer

RapidAPI Text-to-Speech

Stripe API

Python

SeleniumBase

BeautifulSoup

Pandas

Matplotlib

Requests

NumPy

⚙️ Prerequisites

Before running the project install:

Java 17+

Node.js 18+

MySQL

Python 3.x

Maven

Optional Python libraries:

pandas
matplotlib
seaborn
seleniumbase
beautifulsoup4
requests
numpy
⚙️ Configuration

Main backend configuration file:

Payment and finance/src/main/resources/application.properties

Configured services include:

MySQL database

Stripe API keys

SMTP email configuration

JWT authentication

AI content writer

Scraper configuration

Recommendation output directory

▶️ Run the Project Locally

Open a terminal for each service.

1️⃣ Start Discovery Server
cd discovery-server
./mvnw spring-boot:run

Runs on:

http://localhost:8761
2️⃣ Start Backend Service
cd "Payment and finance"
./mvnw spring-boot:run

Runs on:

http://localhost:8091
3️⃣ Start Scraper Service (Optional)
cd scraper
./mvnw spring-boot:run

Runs on:

http://localhost:8093
4️⃣ Start API Gateway
cd api-gateway
./mvnw spring-boot:run

Runs on:

http://localhost:8092
5️⃣ Start Angular Application
cd angular-app
npm install
npm start

Runs on:

http://localhost:4200
🌐 Main URLs
Service	URL
Frontend	http://localhost:4200

Packs Dashboard	http://localhost:4200/dashboard/packs

Finance Dashboard	http://localhost:4200/dashboard/finance

Login	http://localhost:4200/login

Backend API	http://localhost:8091/api

API Gateway	http://localhost:8092

Eureka Dashboard	http://localhost:8761
🔐 Demo Authentication

Demo user configured in backend:

Email: culeks.here@gmail.com
Password: set via AUTH_STATIC_USER_PASSWORD in .env

Login API:

POST /api/auth/login

Returns a JWT token used by Angular authentication guards.

📡 API Overview
Authentication
POST /api/auth/login
GET /api/auth/me
Packs
GET /api/packs
POST /api/admin/packs
PUT /api/admin/packs/{id}
DELETE /api/admin/packs/{id}
Categories
GET /api/categories/active
POST /api/admin/categories
Finance
GET /api/finance/incomes
GET /api/finance/expenses
POST /api/finance/scraper/run
GET /api/finance/scraper/status
Checkout
POST /api/cart
POST /api/cart/checkout
POST /api/cart/confirm
🤖 AI & Scraper Workflow
Scraper

Python script:

scraper/elearning_scraper.py

Can be triggered via:

POST /api/finance/scraper/run
Recommendations

Source folder:

Recommendations/Recommendations output

Used by:

GET /api/recommendations/summary

Displayed inside:

Dashboard → Packs → Recommendations modal
AI Content Generation

Endpoint:

POST /api/admin/packs/content/generate

Used inside the pack creation modal.

🔍 Troubleshooting
Backend endpoint not found

Restart backend service.

./mvnw spring-boot:run
Scraper not running

Check:

Python installation

Script path

Scraper working directory

Email not received

Verify:

SMTP configuration

Gmail App Password

Backend logs

🔒 Security Notes

Current project configuration contains development secrets.

For production:

Move secrets to environment variables

Restrict CORS configuration

Implement full user management

Harden Spring Security policies

👨‍💻 Authors

Developed as part of a Full-Stack E-Learning Platform project using:

Angular

Spring Boot Microservices

AI Integrations

Python Data Processing

📜 License

This project is intended for educational and research purposes.
