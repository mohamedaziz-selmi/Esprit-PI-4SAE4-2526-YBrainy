# Setup Guide

This guide is written for an external evaluator who has not worked on YBrainy before.

## 1. Clone

```bash
git clone https://github.com/USERNAME/Esprit-PI-4SAE4-2526-YBrainy.git
cd Esprit-PI-4SAE4-2526-YBrainy
```

## 2. Prepare environment variables

Windows:

```bash
copy .env.example PIDEV-YBrainy-E-leraning-certifications-Platform\.env
```

macOS/Linux:

```bash
cp .env.example PIDEV-YBrainy-E-leraning-certifications-Platform/.env
```

The placeholders are enough to boot most local services. External paid/API features need real keys.

## 3. Start infrastructure and backend services

```bash
cd PIDEV-YBrainy-E-leraning-certifications-Platform
docker compose up --build
```

## 4. Start the frontend

Open a second terminal:

```bash
cd PIDEV-YBrainy-E-leraning-certifications-Platform/user/angular/angular-app
npm install
npm start
```

Open:

```text
http://localhost:4200
```

## 5. Optional Keycloak setup

Some authentication flows expect a local Keycloak realm:

```bash
docker run -d --name keycloak -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin -p 9190:8080 quay.io/keycloak/keycloak:24.0.1 start-dev
```

Then create:

| Item | Value |
| --- | --- |
| Realm | `microservices` |
| Client | `angular-client` |
| Redirect URI | `http://localhost:4200/*` |
| Web origin | `http://localhost:4200` |
| Roles | `STUDENT`, `INSTRUCTOR`, `ENTERPRISE_USER`, `ADMIN`, `USER` |

## Troubleshooting

| Problem | Fix |
| --- | --- |
| A port is already used | Stop the local service using that port or edit the service port in `docker-compose.yml`. |
| Services do not register in Eureka | Wait for Eureka and Config Server to finish startup, then restart the failing service. |
| Frontend API calls fail | Make sure the frontend is opened at `http://localhost:4200` and the API Gateway is running at `http://localhost:8088`. |
| AI features fail | Add provider keys in `.env` or use the non-AI flows. |
