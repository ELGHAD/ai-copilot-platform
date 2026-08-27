# Frontend

This frontend is the Angular web client for the ALTEN AI Copilot platform.

## Purpose

The application provides the user interface for:

- authentication and account access
- role-based navigation
- document administration
- user administration
- ticket management
- conversational AI chat with document-grounded answers

## Main technologies

- Angular 21
- TypeScript
- Angular Material
- RxJS
- Chart.js

## Main routes

- `/auth/login` and `/auth/register`
- `/admin/dashboard`
- `/admin/documents`
- `/admin/users`
- `/admin/tickets`
- `/chat`

## Run locally

From the `frontend` folder, install dependencies and start the dev server:

```powershell
cd frontend
npm install
npm start
```

The app will be available at:

```text
http://localhost:4200/
```

## Build

```powershell
npm run build
```

## Notes

The frontend communicates with:

- the backend gateway
- the chat flow
- the document management screens
- the RAG-powered assistant experience
