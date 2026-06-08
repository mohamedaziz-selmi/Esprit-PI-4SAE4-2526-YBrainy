# YBrainy_MEvents — Microservices Architecture

Refactored from a Spring Boot monolith into **3 independent microservices** sharing a common parent Maven project.

---

## Project Structure

```
YBrainy_MEvents/
├── pom.xml                     ← Parent POM (modules: event, inscription, user)
│
├── event-service/              ← Port 8081 | DB: event_db
│   └── src/main/java/tn/esprit/eventservice/
│       ├── entity/             Event, EventStatut, EventType
│       ├── repository/         EventRepository
│       ├── service/            IEventServices, EventServicesImp
│       ├── controller/         EventRestControllers
│       ├── client/             UserClient (Feign), InscriptionClient (Feign)
│       ├── dto/                UserDto, InscriptionCreateDto
│       └── config/             CorsConfig
│
├── inscription-service/        ← Port 8082 | DB: inscription_db
│   └── src/main/java/tn/esprit/inscriptionservice/
│       ├── entity/             Inscription, InscriptionStatut
│       ├── repository/         InscriptionRepository
│       ├── service/            IInscriptionServices, InscriptionServicesImp
│       ├── controller/         InscriptionRestControllers
│       ├── client/             UserClient (Feign), EventClient (Feign)
│       ├── dto/                InscriptionCreateDto, EventDto
│       └── config/             CorsConfig
│
└── user-service/               ← Port 8083 | DB: user_db
    └── src/main/java/tn/esprit/userservice/
        ├── entity/             User, Role
        ├── repository/         UserRepository
        ├── service/            IUserServices, UserServicesImp
        ├── controller/         UserRestControllers
        └── config/             CorsConfig
```

---

## Key Design Decisions

### 1. No shared JPA relationships across services
Each service owns its own database and entities. Cross-service references use plain `long` ID columns:

| Original (monolith)                   | Microservice equivalent          |
|---------------------------------------|----------------------------------|
| `@ManyToOne User admin`               | `long adminId`                   |
| `@ManyToOne User student`             | `long studentId`                 |
| `@ManyToOne Event event`              | `long eventId`                   |
| `@OneToMany List<Inscription>`        | removed (owned by inscription-service) |
| `@OneToMany List<Event> eventsCrees`  | removed (owned by event-service) |

### 2. Cross-service communication via OpenFeign
| Caller              | Client             | Target              | Purpose                              |
|---------------------|--------------------|---------------------|--------------------------------------|
| event-service       | `UserClient`       | user-service        | Resolve student/admin by ID or role  |
| event-service       | `InscriptionClient`| inscription-service | Create inscription, check duplicates, get count |
| inscription-service | `UserClient`       | user-service        | Get student IDs for listing          |
| inscription-service | `EventClient`      | event-service       | Enrich pending inscriptions with event name |

### 3. Separate databases per service
- `event_db`       — owned by event-service
- `inscription_db` — owned by inscription-service
- `user_db`        — owned by user-service

---

## Prerequisites

- Java 17+
- Maven 3.8+
- MySQL running on `localhost:3306`
- A **Eureka Server** running on `localhost:8761`  
  (Add a separate `eureka-server` Spring Boot project with `@EnableEurekaServer`)

---

## Running the Services

```bash
# 1. Start Eureka Server (separate project)

# 2. Build all modules from the parent
mvn clean install -DskipTests

# 3. Start each service (in separate terminals)
cd event-service       && mvn spring-boot:run
cd inscription-service && mvn spring-boot:run
cd user-service        && mvn spring-boot:run
```

---

## API Endpoints

### event-service (port 8081)
| Method | URL                                  | Description                  |
|--------|--------------------------------------|------------------------------|
| POST   | `/Event/add`                         | Create event                 |
| PUT    | `/Event/update`                      | Update event                 |
| GET    | `/Event/all`                         | List all events               |
| GET    | `/Event/get/{idEvent}`               | Get event by ID              |
| DELETE | `/Event/delete/{idEvent}`            | Delete event                 |
| POST   | `/Event/{idEvent}/assign/{idStudent}`| Register student to event    |

### inscription-service (port 8082)
| Method | URL                                          | Description                        |
|--------|----------------------------------------------|------------------------------------|
| POST   | `/Inscription`                               | Create inscription (called by event-service) |
| GET    | `/Inscription/exists?studentId=&eventId=`    | Check duplicate (called by event-service) |
| GET    | `/Inscription/count?eventId=`                | Confirmed count (called by event-service) |
| GET    | `/Inscription/students-ids`                  | List student IDs                   |
| GET    | `/Inscription/student/{id}/event-ids`        | Events a student is registered for |
| GET    | `/Inscription/student/{id}/event-statuses`   | Status per event for a student     |
| GET    | `/Inscription/pending`                       | All pending inscriptions           |
| PUT    | `/Inscription/{id}/status/{status}`          | Confirm or cancel inscription      |

### user-service (port 8083)
| Method | URL                               | Description                        |
|--------|-----------------------------------|------------------------------------|
| POST   | `/User/add`                       | Create user                        |
| PUT    | `/User/update`                    | Update user                        |
| GET    | `/User/all`                       | List all users                     |
| GET    | `/User/{id}`                      | Get user by ID                     |
| DELETE | `/User/delete/{id}`               | Delete user                        |
| GET    | `/User/first-by-role?role=`       | First user by role (used by Feign) |
| GET    | `/User/ids-by-role?role=`         | User IDs by role (used by Feign)   |
| GET    | `/User/all-ids`                   | All user IDs (used by Feign)       |
