# Chat Service

## Purpose

The `chat-service` manages the conversational part of the platform.

It is responsible for:

- conversations and message history
- chat session handling
- support for user interactions with the AI assistant
- integration with the RAG layer for question answering

## Stack

- Java 17
- Spring Boot
- Spring WebFlux
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT
- OpenAPI / Swagger

## How to run

```powershell
cd backend/chat-service
./mvnw.cmd spring-boot:run
```

## Notes

This service is the backend bridge between the Angular chat UI and the AI/RAG logic.
