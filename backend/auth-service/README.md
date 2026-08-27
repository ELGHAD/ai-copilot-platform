# Auth Service

## Purpose

The `auth-service` handles authentication for the platform.

It is responsible for:

- user sign-in and sign-up flows
- credential validation
- JWT generation and validation
- secure access to the rest of the platform

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
cd backend/auth-service
./mvnw.cmd spring-boot:run
```

## Notes

This service is the authentication backbone of the system. It is expected to work together with the `gateway` and `user-service`.
