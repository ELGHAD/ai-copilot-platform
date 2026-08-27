# Document Service

## Purpose

The `document-service` manages the document domain of the platform.

It is responsible for:

- document records and metadata
- document lifecycle states
- role-based document visibility
- administrative operations on documents

## Key concepts

Documents can be managed with metadata such as:

- title
- description
- role access
- status

Typical document statuses used by the UI are:

- `ACTIVE`
- `OBSOLETE`
- `ARCHIVED`

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
cd backend/document-service
./mvnw.cmd spring-boot:run
```

## Notes

This service works closely with the document management screens in the Angular frontend and with the RAG pipeline for document-based AI answers.
