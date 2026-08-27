# Gateway

## Purpose

The `gateway` is the entry point for the platform.

It is responsible for:

- routing incoming requests to the correct backend service
- centralizing access to the platform
- enforcing security and authentication rules
- protecting internal services behind a single front door

## Stack

- Java 17
- Spring Boot
- Spring Cloud Gateway
- Spring WebFlux
- JWT

## How to run

```powershell
cd backend/gateway
./mvnw.cmd spring-boot:run
```

## Notes

The gateway runs on port `8080` and is the main access point used by the Angular frontend.
