# Architecture

YBrainy is a modular microservices platform. The system separates user management, learning, community, events, payments, and AI/ML services so each domain can evolve independently.

```mermaid
flowchart LR
    Browser["Angular Frontend"] --> Gateway["API Gateway"]
    Gateway --> User["User Service"]
    Gateway --> Course["Course Service"]
    Gateway --> Lesson["Lesson Service"]
    Gateway --> Quiz["Quiz Service"]
    Gateway --> Enroll["Enrollment Service"]
    Gateway --> Forum["Forum Services"]
    Gateway --> Events["Events Services"]
    Gateway --> Payment["Payment Services"]
    Course --> ML["ML Service"]
    User --> MySQL["MySQL"]
    Course --> MySQL
    Lesson --> MySQL
    Quiz --> MySQL
    Enroll --> MySQL
    Payment --> MySQL
    Forum --> Mongo["MongoDB"]
    Events --> MySQL
    Gateway --> Eureka["Eureka Discovery"]
    User --> Rabbit["RabbitMQ"]
    Course --> Rabbit
    Enroll --> Rabbit
    Events --> Rabbit
    User --> Zipkin["Zipkin Tracing"]
    Course --> Zipkin
```

## Main modules

| Module | Role |
| --- | --- |
| `user/` | Accounts, roles, authentication-adjacent services, frontend |
| `YBRAINY/` | Courses, lessons, quizzes, enrollment, ML service |
| `forum/` | Community, posts, comments, messaging |
| `ybrainy events/` | Events, inscriptions, feedback, event AI services |
| `payment/` | Cart, checkout, finance/payment workflows |
| `run-services/` | Local helper scripts for running individual modules |

## Infrastructure

| Component | Purpose |
| --- | --- |
| Docker Compose | Local orchestration |
| Eureka | Service discovery |
| Config Server | Shared Spring service configuration |
| RabbitMQ | Asynchronous communication |
| MySQL | Relational storage |
| MongoDB | Forum/community document storage |
| Zipkin | Distributed tracing |
