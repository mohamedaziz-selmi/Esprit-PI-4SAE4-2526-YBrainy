# YBrainy — Manual Startup Guide

This guide explains how to start the full backend stack manually, in the exact
order required. The automated script `start-ybrainy.ps1` does all of this for
you, but follow this guide if you need to start things individually, debug a
failing service, or understand what is happening under the hood.

---

## Port Reference

| Service          | Port  | URL                          |
|------------------|-------|------------------------------|
| Config Server    | 8888  | http://localhost:8888        |
| Eureka           | 8761  | http://localhost:8761        |
| User Service     | 8899  | http://localhost:8899        |
| Course Service   | 8082  | http://localhost:8082        |
| Enrollment       | 8085  | http://localhost:8085        |
| Lesson Service   | 8084  | http://localhost:8084        |
| Quiz Service     | 8083  | http://localhost:8083        |
| Payment Service  | 8095  | http://localhost:8095        |
| Cart Service     | 8954  | http://localhost:8954        |
| API Gateway      | 8088  | http://localhost:8088        |
| Keycloak         | 9190  | http://localhost:9190        |
| RabbitMQ         | 5672  | —                            |
| RabbitMQ UI      | 15672 | http://localhost:15672       |
| MongoDB          | 27017 | —                            |
| Zipkin UI        | 9411  | http://localhost:9411        |

---

## Prerequisites

Before starting anything, make sure the following are installed and available
in your PATH:

- **Java 17+** — run `java -version` to verify
- **Maven 3.8+** — run `mvn -version` to verify
- **Docker Desktop** — must be running

---

## Step 1 — Start Docker Containers

These four containers must be running before any Java service is started.
Start them in Docker Desktop or run them via the terminal.

### RabbitMQ
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```
Check it is up: open http://localhost:15672 (login: `guest` / `guest`)

### MongoDB
```bash
docker run -d --name mongodb -p 27017:27017 mongo:latest
```

### Zipkin
```bash
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin
```
Check it is up: open http://localhost:9411

### Keycloak
```bash
docker run -d --name keycloak -p 9190:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```
Check it is up: open http://localhost:9190

> **Important:** The app (frontend + all services) is configured for Keycloak on port **9190**.
> The container listens on internal port 8080 — `-p 9190:8080` maps it to host 9190.

> If your containers are already created from a previous run, start them with:
> `docker start rabbitmq mongodb zipkin keycloak`

**Do not proceed until all four containers are running.**

---

## Step 2 — Start the Config Server

The Config Server must come up first because every other service tries to
fetch its configuration from it on startup.

**Directory:**
```
YBRAINY\ConfigServer\config-server
```

Open a terminal in that directory and run:
```bash
mvn spring-boot:run
```

**Wait until you see this line in the output:**
```
Started ConfigServerApplication in X.XXX seconds
```

**Verify it is healthy:**
Open http://localhost:8888/actuator/health — you should see `{"status":"UP"}`

> Keep this terminal open. Closing it stops the Config Server.

---

## Step 3 — Start Eureka (Service Registry)

Eureka is the service registry. All other services register themselves here
so they can discover each other. It must be running before any service that
needs to call another service.

**Directory:**
```
user\p-r-k\Eureka\Eureka
```

Open a **new** terminal in that directory and run:
```bash
mvn spring-boot:run
```

**Wait until you see:**
```
Started EurekaApplication in X.XXX seconds
```

**Verify it is healthy:**
Open http://localhost:8761 — you should see the Eureka dashboard.

> Keep this terminal open.

---

## Step 4 — Start Core Services

All seven services below can be started at the same time (open a separate
terminal window for each). They will start in parallel and register themselves
with Eureka once they are ready.

For each service: open a **new terminal**, navigate to its directory, and run:
```bash
mvn spring-boot:run
```

### User Service
```
Directory:  user
Port:       8899
Health:     http://localhost:8899/actuator/health
```

### Course Service
```
Directory:  YBRAINY\Course\tp-foyer
Port:       8082
Health:     http://localhost:8082/actuator/health
```

### Enrollment Service
```
Directory:  YBRAINY\Enrollment\enrollment-service
Port:       8085
Health:     http://localhost:8085/actuator/health
```

### Lesson Service
```
Directory:  YBRAINY\Lesson
Port:       8084
Health:     http://localhost:8084/actuator/health
```

### Quiz Service
```
Directory:  YBRAINY\Quiz\quiz-service
Port:       8083
Health:     http://localhost:8083/actuator/health
```

### Payment Service
```
Directory:  payment\Payment
Port:       8095
Health:     http://localhost:8095/actuator/health
```

### Cart Service
```
Directory:  payment\cart
Port:       8954
Health:     http://localhost:8954/actuator/health
```

**Wait until each one prints "Started ... in X.XXX seconds" in its window.**

> First-time startup can take 2–5 minutes per service because Maven downloads
> dependencies. Subsequent starts are much faster.

---

## Step 5 — Start the API Gateway (Last)

The Gateway routes all incoming Angular requests to the correct service. It
must start last so that the services it routes to are already registered in
Eureka.

**Directory:**
```
user\p-r-k\ApiGateway\ApiGateway
```

Open a **new** terminal and run:
```bash
mvn spring-boot:run
```

**Wait until you see:**
```
Started ApiGatewayApplication in X.XXX seconds
```

**Verify it is healthy:**
Open http://localhost:8088/actuator/health — you should see `{"status":"UP"}`

---

## Step 6 — Verify Everything is Registered in Eureka

Open http://localhost:8761 and confirm you see all services listed as UP:

- BREADANDBUTTERUSER (User Service)
- COURSE-SERVICE
- ENROLLMENT-SERVICE
- LESSON-SERVICE
- QUIZ-SERVICE
- PAYMENT-SERVICE
- CART-SERVICE
- API-GATEWAY
- CONFIG-SERVER

If a service is missing, check its terminal window for startup errors.

---

## Startup Order Summary

```
Docker containers (all at once)
        │
        ▼
Config Server  ──► wait for healthy
        │
        ▼
Eureka         ──► wait for healthy
        │
        ▼
User + Course + Enrollment + Lesson + Quiz + Payment + Cart  (all at once)
        │
        ▼  (wait for all to print "Started")
API Gateway    ──► wait for healthy
        │
        ▼
Start Angular frontend (if needed)
```

---

## Stopping Everything

To stop a service, go to its terminal window and press `Ctrl+C`.

To stop all services at once, run the stop script from the project root:
```powershell
powershell -ExecutionPolicy Bypass -File .\stop-ybrainy.ps1
```

To stop Docker containers:
```bash
docker stop rabbitmq mongodb zipkin keycloak mysql
```

---

## Troubleshooting

**"Port already in use" error**
Another process is already listening on that port. Run the stop script first,
or find and kill the process manually:
```powershell
# Find what is on port 8082 (replace with your port)
netstat -ano | findstr :8082
# Kill it (replace 12345 with the PID from the output above)
taskkill /PID 12345 /F
```

**Service starts but immediately stops**
Check the terminal output for a stack trace. Common causes:
- MySQL is not running (services need MySQL on port 3306)
- RabbitMQ container is not up yet
- Another service it depends on is not reachable

**Service never registers in Eureka**
- Confirm Eureka is running on port 8761
- Check that `YBRAINY_EUREKA_URL` is not set to a wrong value in your environment
- Look for `com.netflix.eureka` errors in the service terminal

**First startup is very slow**
Normal. Maven downloads all dependencies on the first run. Once cached
(in your local `.m2` folder), subsequent starts take 30–60 seconds each.

**Config Server connection refused warnings**
Services print a warning if Config Server is not reachable but still start
thanks to `optional:configserver:` in their config import. Start Config Server
first to avoid these warnings.
