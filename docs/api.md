# API Notes

The frontend communicates through the API Gateway.

| Layer | URL |
| --- | --- |
| Frontend | `http://localhost:4200` |
| API Gateway | `http://localhost:8088` |
| Eureka dashboard | `http://localhost:8761` |
| ML service | `http://localhost:5000` |

## Route families

Exact routes depend on each microservice controller, but the gateway organizes the platform around these families:

| Domain | Typical route family |
| --- | --- |
| Users | `/api/users/**` |
| Courses | `/api/courses/**` |
| Lessons | `/api/lessons/**` |
| Quizzes | `/api/quizzes/**` |
| Enrollment | `/api/enrollments/**` |
| Forum | `/api/forum/**` |
| Events | `/api/events/**` |
| Payments | `/api/payments/**` |
| AI/ML | `/api/ml/**` |

For exact endpoint signatures, inspect the Spring controllers inside each service module.

Recommended folders:

```text
PIDEV-YBrainy-E-leraning-certifications-Platform/YBRAINY/Course/tp-foyer/src/main/java
PIDEV-YBrainy-E-leraning-certifications-Platform/YBRAINY/Lesson/src/main/java
PIDEV-YBrainy-E-leraning-certifications-Platform/YBRAINY/Quiz/quiz-service/src/main/java
PIDEV-YBrainy-E-leraning-certifications-Platform/user/src/main/java
PIDEV-YBrainy-E-leraning-certifications-Platform/forum
PIDEV-YBrainy-E-leraning-certifications-Platform/payment
```
