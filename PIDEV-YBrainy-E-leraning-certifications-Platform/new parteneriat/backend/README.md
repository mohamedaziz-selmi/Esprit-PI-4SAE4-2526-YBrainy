# Backend Spring Boot Microservices

Ce dossier contient un backend complet et separe du front Angular.

## Architecture

- `discovery-server` (Eureka): registre des microservices
- `api-gateway` (Spring Cloud Gateway): point d'entree unique
- `partnership-service`: CRUD des partenariats
- `job-offer-service`: CRUD des offres d'emploi, lie aux partenariats

## Ports

- Discovery: `8761`
- Gateway: `8080`
- Partnership Service: `8081`
- Job Offer Service: `8082`

## Endpoints via Gateway

- Partenariats: `http://localhost:8080/api/partnerships`
- Offres: `http://localhost:8080/api/offers`
- Candidatures: `http://localhost:8080/api/applications`
- AI CV + lettre: `POST http://localhost:8080/api/generate-application`

Payload example:

```json
{
  "cv": "raw CV text",
  "jobDescription": "job description text"
}
```

## Demarrage local

1. Lancer `discovery-server`
2. Lancer `partnership-service`
3. Lancer `job-offer-service`
4. Lancer `api-gateway`

## Import IntelliJ

- Ouvrir le dossier `backend` comme projet Maven
- IntelliJ detecte les 4 modules automatiquement

## Notes techniques

- Base locale MySQL pour chaque service metier (`partnershipdb`, `jobofferdb`)
- Variables d'environnement supportees:
  - `PARTNERSHIP_DB_URL`, `PARTNERSHIP_DB_USERNAME`, `PARTNERSHIP_DB_PASSWORD`
  - `JOB_OFFER_DB_URL`, `JOB_OFFER_DB_USERNAME`, `JOB_OFFER_DB_PASSWORD`
  - SMTP job applications:
    `JOB_MAIL_HOST`, `JOB_MAIL_PORT`, `JOB_MAIL_USERNAME`, `JOB_MAIL_PASSWORD`,
    `JOB_MAIL_SMTP_AUTH`, `JOB_MAIL_SMTP_STARTTLS`,
    `JOB_APPLICATION_NOTIFICATION_FROM`,
    `JOB_APPLICATION_ACCEPTED_NOTIFICATION_ENABLED`
  - Gemini:
    `GEMINI_API_KEY`, `GEMINI_API_URL`, `GEMINI_API_MODEL`
- Validation Bean Validation (javax/jakarta)
- Gestion d'erreurs centralisee
- Decouverte service-to-service via Eureka
- Appel inter-service depuis `job-offer-service` vers `partnership-service` avec OpenFeign
