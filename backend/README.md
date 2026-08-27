# Backend Services

This folder contains the backend microservices for the ALTEN AI Copilot platform.

## Architecture overview

The backend is split into several Spring Boot services:

- `auth-service` — authentication and token management
- `user-service` — user profiles, roles, and account administration
- `document-service` — document metadata, access rules, and document lifecycle
- `chat-service` — conversations, chat state, and chat-related business logic
- `gateway` — single entry point for frontend requests

## Main technologies

- Java 17
- Spring Boot 4.x
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT-based authentication
- OpenAPI / Swagger

## Runtime ports

- Gateway: `http://localhost:8080`
- Auth service: `http://localhost:8081`
- User service: `http://localhost:8082`
- Document service: `http://localhost:8083`
- Chat service: `http://localhost:8084`

## Database

The services use PostgreSQL with the database name `alten_copilot` on `localhost:5433`.

## Run locally

From each service folder, run:

```powershell
./mvnw.cmd spring-boot:run
```

If you use Git Bash or WSL, you can run:

```bash
./mvnw spring-boot:run
```

## Roles used by the platform

- `ADMIN`
- `EXPERT`
- `OPERATIONNEL`

These roles drive access to documents, administration screens, and sensitive features.
