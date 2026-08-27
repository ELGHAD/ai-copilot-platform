# User Service

## Purpose

The `user-service` manages the user domain of the application.

It is responsible for:

- user profiles and account information
- role management
- administrative access to user data
- supporting the role-based behavior of the platform

## Supported roles

- `ADMIN`
- `EXPERT`
- `OPERATIONNEL`

## Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT
- OpenAPI / Swagger

## How to run

```powershell
cd backend/user-service
./mvnw.cmd spring-boot:run
```

## Notes

This service provides the identity and authorization model used by the frontend and other backend services.
