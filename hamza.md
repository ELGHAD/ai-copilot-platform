# AUDIT DE REPRISE — ALTEN AI COPILOT PLATFORM

**Document d'audit exhaustif destiné au knowledge transfer — deuxième LLM redactera un rapport académique complet basé sur ce document.**

---

## 1. Résumé Exécutif

**Nom du projet**: ALTEN AI Copilot  
**Domaine métier**: Plateforme de chat-copilot avec Retrieval-Augmented Generation (RAG)  
**Objectif principal**: Fournir une interface conversationnelle permettant aux utilisateurs internes (administrateurs, experts, opérationnels) de poser des questions sur des documents internes organisés par rôle et d'obtenir des réponses générées par IA sur la base de ces documents.

**Problème résolu**:
- Accès homogénéisé à une base documentaire dispersée sans requérir une recherche manuelle
- Génération d'explications contextuelles plutôt que simples résultats de recherche
- Gestion des tickets (demandes de support) déclenchées depuis les conversations pour escalade

**Bénéfices apportés**:
- Réduction du temps de recherche documentaire
- Traçabilité des conversations et des sources citées
- Gestion d'une base documentaire versionnée avec contrôle d'accès par rôle
- Mécanisme de feedback (tickets) pour améliorer les réponses

**Utilisateurs cibles**:
- Administrateurs (accès complet, gestion documentaire, modération des tickets)
- Experts (chat, création de tickets, accès aux documents sensibles)
- Opérationnels (chat, création de tickets, accès limité aux documents communs)

**Type d'architecture**: Microservices distribuée avec API Gateway, cinq services Spring Boot spécialisés, service RAG Python indépendant, et frontend Angular.  
(source: [README.md](README.md), [compose.yml](compose.yml), [backend/pom.xml](backend/pom.xml))

---

## 2. Contexte Fonctionnel

### Besoin initial supposé du projet

Le projet répond à un besoin interne ALTEN de centraliser et d'exposer par une interface conversationnelle une base documentaire hétérogène (PDF, documents Word). Plutôt que de constituer un simple moteur de recherche, le système génère des réponses explicatives fondées sur le contenu des documents, en s'appuyant sur des modèles d'IA générative (OpenAI GPT-4o-mini par défaut).

### Contraintes métier identifiables

1. **Segmentation par rôle**:
   - Les documents sont taggés d'un niveau d'accès minimal (`COMMUN`, `OPERATIONNEL`, `EXPERT`, `ADMIN`)
   - Le RAG filter les chunks indexés avant la génération pour respecter cette ségrégation
   - La réponse IA s'adapte au rôle de l'utilisateur via une instruction du prompt

2. **Cycle de vie des documents**:
   - Statuts définis: `ACTIVE`, `ARCHIVED`
   - Flag `indexed` pour suivre l'indexation dans le vector store pgvector
   - Support du versioning (`version` column) pour les ré-uploads

3. **Tickets avec workflows**:
   - Statuts de ticket: `OPEN`, `IN_PROGRESS`, `CLOSED`
   - Types: `FREE_TEXT` ou formulaires dynamiques (`FORM_SOFTWARE_INSTALL`, `FORM_ACCESS_REQUEST`, `FORM_HARDWARE_ISSUE`, `FORM_NETWORK_VPN`, `FORM_USER_ACCOUNT`, `FORM_OTHER`)
   - Assignation à un admin, suivi temporel avec timestamp d'archivage

4. **Authentification JWT**:
   - Token stateless signé avec HS256 (JWT_SECRET unique au déploiement)
   - Durée de vie `JWT_EXPIRATION` (900 secondes par défaut)
   - Refresh token optionnel (non implémenté dans le code actuel visible)
   - Validation centralisée au gateway

### Scénarios d'usage typiques

**Scénario 1 — Chat simple (Opérationnel)**:
1. Utilisateur (rôle OPERATIONNEL) se connecte avec email/mot de passe
2. Envoie une question `POST /api/chat/ask`
3. Chat-service crée une `Conversation`, envoie la question au RAG
4. RAG filtre les chunks indexés au rôle OPERATIONNEL, génère une réponse
5. Chat-service persiste la réponse, retourne `conversationId` + réponse + sources
6. Utilisateur consulte l'historique `GET /api/chat/conversations/{id}/messages`

**Scénario 2 — Escalade via ticket (Expert)**:
1. Expert reçoit une réponse insatisfaisante du RAG
2. Crée un ticket via `POST /api/tickets` (type `FREE_TEXT` ou un des formulaires)
3. Admin est notifié (pas de websocket visible — polling ou refresh manuel)
4. Admin répond via `POST /api/tickets/{id}/activities` (texte + image optionnelle)
5. Expert voit la réponse dans la timeline du ticket
6. Admin ferme le ticket `PATCH /api/tickets/{id}/status`

**Scénario 3 — Gestion documentaire (Admin)**:
1. Admin upload un document via `POST /api/documents` (multipart)
2. Document-service valide le type/taille, l'enregistre sur disque
3. Document-service appelle RAG `/documents/upload` pour indexation
4. RAG parse → chunk → embed → store dans pgvector
5. Admin voit le statut d'indexation dans le dashboard
6. Tous les utilisateurs du rôle concerné voient le document dans leurs réponses RAG

---

## 3. Architecture Générale

### Style architectural

**Microservices en couches avec API Gateway** :
- **Entrée unique**: API Gateway (Spring Cloud Gateway) à `localhost:8080` — point d'entrée unique pour le frontend
- **Cinq services métier spécialisés**, chacun avec sa propre base de données JPA/PostgreSQL (partagée au niveau de l'application)
- **Service RAG indépendant**: FastAPI Python gérant les embeddings, le vector store pgvector, et l'invocation OpenAI
- **Frontend détaché**: Angular 21 servi en dev par `ng serve`, proxy-isant les appels API au gateway

### Répartition haute niveau

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND ANGULAR (4200)                   │
│   [Login] [Chat] [Admin Dashboard] [Tickets] [Documents]    │
└────────────────────────────┬────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              API GATEWAY (8080) — Spring Cloud Gateway       │
│  ├─ /api/auth/** → Auth Service                            │
│  ├─ /api/users/** → User Service                           │
│  ├─ /api/documents/** → Document Service                   │
│  ├─ /api/chat/** → Chat Service                            │
│  ├─ /api/tickets/** → Chat Service (même binaire)          │
│  └─ /uploads/** → Chat Service (fichiers statiques)        │
│  [JWT Validation Filter @ port 8080]                       │
└─────────────────────┬─────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
   ┌─────────┐  ┌────────────┐  ┌──────────────┐
   │Auth Svc │  │User Svc    │  │Document Svc  │
   │ (8081)  │  │  (8082)    │  │   (8083)     │
   │ -POST   │  │ -GET /me   │  │ -POST upload │
   │ /register│ │ -GET all   │  │ -GET list    │
   │ /login  │  │ -PUT user  │  │ -PUT update  │
   └─────────┘  │ -DELETE    │  │ -DELETE      │
                └────────────┘  └──────────────┘
                                       │
                ┌──────────────────────┘
                │
        ┌───────────────────────┐
        │   Chat Service        │
        │       (8084)          │
        │ ├─ /api/chat/**       │
        │ ├─ /api/tickets/**    │
        │ └─ /uploads/**        │
        │ [Conversation, Ticket │
        │  Message, Activity]   │
        └───────────────────────┘
                    │
                    ↓
        ┌───────────────────────┐
        │   RAG Service (FastAPI)
        │       (8085)          │
        │ ├─ POST /chat/        │
        │ ├─ POST /documents/   │
        │ └─ GET /health        │
        │ [Vector Store pgvector
        │  Embeddings + LLM]    │
        └───────────────────────┘

DATABASES (Docker Compose):
┌──────────────────────────────────┐
│  postgres-app (5433)              │
│  [users, documents, conversations,│
│   messages, tickets, activities]  │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│  postgres-vector (5435)           │
│  [pgvector + document chunks]     │
└──────────────────────────────────┘

DISK STORAGE:
┌──────────────────────────────────┐
│  ./uploads/documents/             │
│  [uploaded PDF/DOCX files]        │
└──────────────────────────────────┘
```

### Justification du style

- **Gateway unique** → Centralisation du JWT, du CORS, du rate-limiting (si désiré)
- **Services autonomes** → Chacun peut être scalé, déployé, ou maintenance sans bloquer les autres
- **RAG détaché** → Permet changement de LLM, de vector store ou de stratégie d'embedding sans refonte
- **PostgreSQL partagée** → Simplicité du déploiement local ; une vraie architecture microservices utiliserait une DB par service

### Dépendances et flux

1. **Frontend → Gateway**: Toutes les requêtes HTTPS (en prod) ou HTTP (dev) transitent par le gateway avec JWT en header
2. **Gateway → Services métier**: Le gateway route sur la base du préfixe de path
3. **Chat-service → RAG**: Appels HTTP REST au RAG pour `/chat/` et `/documents/upload`
4. **Document-service → RAG**: Appel au RAG lors d'un upload (indexation asynchrone au besoin)
5. **Tous les services → PostgreSQL**: JPA/Hibernate pour persistance
6. **RAG → Vector DB**: Connexion `psycopg` à `postgres-vector` pour pgvector
7. **RAG → OpenAI**: Appel à l'API OpenAI (ou compatible) pour embeddings et chat

---

## 4. Stack Technique

| Technologie | Rôle | Où utilisée | Justification probable |
|---|---|---|---|
| **Java 21** | Langage de programmation backend | 5 services Spring Boot (auth, user, document, chat, gateway) | Support long terme, écosystème Spring mature, performance |
| **Spring Boot 3.4** | Framework web/REST | Tous les services | Standardisation ALTEN, intégration Spring Security, Data JPA |
| **Spring Cloud Gateway** | API Gateway | gateway/ (port 8080) | Routage centralisé, JWT validation, CORS, version réactive |
| **Spring Security** | Authentification/Autorisation | Tous les services | Gestion JWT, extraction de rôles, @PreAuthorize |
| **Spring Data JPA** | ORM | Tous les services | Requêtes DB simplifiées, support multi-DB, Hibernat |
| **Hibernate** | Implémentation JPA | Tous les services | DDL auto (update), eager/lazy loading, jsonb support |
| **PostgreSQL 16** | SGBDR principal | 2 instances (app + vector) | Scalabilité, pgvector extension, transactions ACID |
| **pgvector** | Vector Store | postgres-vector (5435) | Recherche similitude, indexation HNSW, contraintes ACID |
| **Python 3.11+** | Langage RAG Service | rag-service/ | Écosystème ML, LangChain, parsing documentaire |
| **FastAPI 0.115** | Framework RAG Service | rag-service/app/main.py | Performances, Pydantic validation, async-await, OpenAPI auto |
| **LangChain 0.3** | Orchestration RAG | rag-service/services/ | Chaîne vectorstore→retriever→llm, handling tokens, splitters |
| **Langchain-openai** | Intégration OpenAI | rag-service/services/ | Embeddings, Chat via LangChain abstractions |
| **OpenAI Python SDK** | Appels API OpenAI | rag-service/services/ | GPT-4o-mini, text-embedding-3-small par défaut |
| **Pydantic 2.10** | Validation schémas Python | rag-service/models.py | Type hints, sérialisation JSON, BaseSettings |
| **SQLAlchemy 2.0** | ORM Python optionnel | rag-service/services/ | Gestion des sessions, lazy loading, transactions |
| **PyMuPDF 1.25** | Parsing PDF | rag-service/services/parser.py | Extraction texte/images, page metadata |
| **python-docx 1.1** | Parsing Word | rag-service/services/parser.py | Extraction texte, paragraphes, tables |
| **Tiktoken 0.8** | Tokenisation OpenAI | rag-service/services/chunker.py | Décompte tokens exact, respect du context window |
| **Angular 21.2** | Framework frontend | frontend/src/ | Single-page app, composants réutilisables, RxJS, Material Design |
| **Angular Material 21.2** | UI components | frontend/src/app/shared/ | Boutons, modales, listes, charts Material Design |
| **Chart.js 4.5** | Graphiques | frontend/features/admin/dashboard/ | Visualisation statistiques documents/tickets |
| **RxJS 7.8** | Réactivité frontend | frontend/src/app/services/ | Observables, opérateurs (map, filter, switchMap) |
| **TypeScript 5.9** | Typage JavaScript | frontend/src/ | Type safety compile-time, interfaces, enums |
| **Docker** | Conteneurisation DB | compose.yml | PostgreSQL, pgvector dans des conteneurs gérés |
| **Maven** | Build system Java | backend/ et 5 services | Dépendances, versioning, pom.xml hérité |
| **npm 11.12** | Package manager frontend | frontend/package.json | Dépendances Angular, scripts start/build |
| **Lombok** | Réduction boilerplate Java | Toutes les entités/DTOs | @Data, @Builder, @RequiredArgsConstructor |
| **Jakarta EE** | APIs standardisées | Entités @Entity, DTOs @Data | Remplacement legacy javax.* |
| **Swagger/Springdoc** | Documentation API | Services Spring Boot | /v3/api-docs, /swagger-ui/ auto-générés |

---

## 5. Structure Complète du Projet

### Arborescence de premier niveau

```
/home/serhani/ai-copilot-platform/
├── backend/                          # Agrégateur Maven des 5 services
│   ├── pom.xml                       # Déclaration modules (parent)
│   ├── gateway/                      # API Gateway Spring Cloud (port 8080)
│   ├── auth-service/                 # Service d'authentification (port 8081)
│   ├── user-service/                 # Gestion des utilisateurs (port 8082)
│   ├── document-service/             # Gestion des documents (port 8083)
│   └── chat-service/                 # Chat + Tickets + Upload static (port 8084)
├── frontend/                         # Application Angular (ng serve → 4200)
│   ├── package.json
│   ├── angular.json
│   ├── proxy.conf.json               # Proxy /api → localhost:8080, /uploads → localhost:8084
│   └── src/
│       ├── app/
│       │   ├── features/             # Modules métier (chat, admin, auth)
│       │   ├── core/                 # Guards, services globaux
│       │   └── shared/               # Composants réutilisables
│       └── environments/             # Config environment.ts, environment.prod.ts
├── rag-service/                      # Service RAG FastAPI Python (port 8085)
│   ├── app/
│   │   ├── main.py                   # Application FastAPI
│   │   ├── models.py                 # Pydantic models (ChatRequest, ChatResponse, etc.)
│   │   ├── config.py                 # Paramètres Settings (OpenAI key, URLs, chunk size)
│   │   ├── routers/
│   │   │   ├── chat.py               # Endpoint POST /chat/, GET /chat/health
│   │   │   └── documents.py          # Endpoint POST /documents/upload
│   │   ├── services/
│   │   │   ├── embedder.py           # VectorStoreManager (pgvector, LangChain)
│   │   │   ├── rag_chain.py          # RAGChain (retriever + prompt + LLM)
│   │   │   ├── parser.py             # DocumentParser (PDF, DOCX)
│   │   │   └── chunker.py            # DocumentChunker (split par tokens)
│   │   └── db/
│   │       └── vector_store.py       # Connexion pgvector, init, health check
│   ├── requirements.txt               # Dépendances Python
│   └── docker-compose.yml             # Obsolète (remplacé par compose.yml racine)
├── docker/                           # Scripts Docker
│   └── init-vector.sql               # Initialisation pgvector extension
├── uploads/                          # Répertoire de stockage
│   ├── documents/                    # Documents uploadés (PDF/DOCX)
│   └── tickets/                      # Attachments tickets (images)
├── compose.yml                       # Docker Compose (postgres-app + postgres-vector)
├── dev.sh                            # Script orchestration (start/stop/logs/restart)
├── .env.example                      # Template configuration
├── README.md                         # Guide démarrage rapide
├── AUDIT-REPRISE.md                  # [À ignorer pour cet audit]
├── hamza.md                          # [CE DOCUMENT — Audit de reprise exhaustif]
└── ## GitHub Copilot Chat.md         # [Fichier Copilot Chat local]
```

---

## 6. Modules Métier

### 6.1 Module d'Authentification (Auth-Service)

**Objectif**: Identifier les utilisateurs, générer des JWT valides, servir de source de vérité pour les identités et rôles.

**Fonctionnement**:
1. **Inscription** (`POST /api/auth/register`): Valide email unique, hashes mot de passe BCrypt, crée User, génère JWT
2. **Connexion** (`POST /api/auth/login`): Valide email/mot de passe, génère JWT signé HS256
3. **JWT Structure**: Payload contient email + rôle, durée 900s par défaut

**Entités**: `User` (id, email UNIQUE, password bcrypt, fullName, role, enabled, createdAt)

**Valeur métier**: Sécurité centralisée, JWT stateless (scalable), hachage bcrypt (secure)

### 6.2 Module Conversation & Chat RAG (Chat-Service)

**Objectif**: Orchestrer conversations utilisateur-AI, persister historique, invoquer RAG

**Fonctionnement**:
1. POST /api/chat/ask → crée Conversation (si new) ou continue existante
2. Persiste Message(USER)
3. Appelle RAG
4. Persiste Message(ASSISTANT) avec sources et confidence score
5. Retourne ChatResponse

**Entités**: `Conversation` (id, userEmail, userRole, title, messages, timestamps), `Message` (id, conversationId, role, content, sources JSON, confidenceScore, createdAt)

**Valeur métier**: Traçabilité complète, continuité context, escalade via tickets

### 6.3 Module Document & Indexation (Document-Service + RAG)

**Objectif**: Centraliser base documentaire, orchestrer indexation vectorielle, respecter contrôle d'accès

**Fonctionnement**:
1. Admin upload PDF/DOCX via POST /api/documents
2. Document-Service valide type/taille, sauvegarde sur disque, appelle RAG
3. RAG parse → chunk → embed → store dans pgvector
4. Document-Service met à jour indexed=true
5. Au retrieval: RAG filtre chunks par rôle utilisateur

**Entités**: `Document` (id, storedFileName UNIQUE, title, roleAccess, status, indexed, version, timestamps)

**Valeur métier**: Centralisation, accès sémantique, sécurité par rôle, versioning

### 6.4 Module Tickets & Support (Chat-Service)

**Objectif**: Capturer escalades, assurer dialogue user ↔ admin, tracker état

**Fonctionnement**:
1. User crée ticket: POST /api/tickets (FREE_TEXT ou FORM_*)
2. Service valide formData vs schema
3. Ticket.conversationId et messageId optionnels (link à chat)
4. Admin répond: POST /api/tickets/{id}/activities (texte + image)
5. Admin clôt: PATCH /api/tickets/{id}/status

**Entités**: `Ticket` (id, ticketNumber UNIQUE, conversationId?, messageId?, status, ticketType, formData JSONB, timestamps), `TicketActivity` (id, ticketId, senderRole, content, attachmentUrl)

**Valeur métier**: Support structuré, audit, amélioration continue

---

## 7. Utilisateurs et Rôles

### 7.1 Rôles définis

| Rôle | Permissions Chat | Permissions Documents | Permissions Tickets | Permissions Admin |
|---|---|---|---|---|
| **ADMIN** | ✓ Chat libre | ✓ Upload, list, edit, archive, stats | ✓ Create, view all, assign, close, reply | ✓ User mgmt, dashboard, moderation |
| **EXPERT** | ✓ Chat libre | ✓ List (level ≥ EXPERT), see sources | ✓ Create, view own, reply | ✗ |
| **OPERATIONNEL** | ✓ Chat libre | ✓ List (level ≥ OPERATIONNEL), see sources | ✓ Create, view own, reply | ✗ |

### 7.2 Segmentation documentaire par rôle

- Admin: voit tous chunks
- Expert: voit chunks avec roleAccess ∈ {COMMUN, OPERATIONNEL, EXPERT}
- Operationnel: voit chunks avec roleAccess ∈ {COMMUN, OPERATIONNEL}

Hiérarchie: COMMUN < OPERATIONNEL < EXPERT < ADMIN

---

## 8. Fonctionnalités (Pagination 1/2)

### 8.1 Authentification & Comptes

#### F8.1.1 Inscription utilisateur — Créer compte avec email/password/rôle, auto-login
#### F8.1.2 Connexion utilisateur — Email/password → JWT
#### F8.1.3 Consulter profil personnel — GET /api/users/me
#### F8.1.4 Admin : Lister tous utilisateurs — GET /api/users
#### F8.1.5 Admin : Modifier utilisateur — PUT /api/users/{id}
#### F8.1.6 Admin : Désactiver utilisateur — DELETE /api/users/{id}

### 8.2 Chat & Conversation RAG

#### F8.2.1 Poser une question au copilot — POST /api/chat/ask, reçoit answer + sources + confidence
#### F8.2.2 Consulter historique conversation — GET /api/chat/conversations/{id}/messages
#### F8.2.3 Lister ses conversations — GET /api/chat/conversations (avec pagination)
#### F8.2.4 Supprimer une conversation — DELETE /api/chat/conversations/{id}

### 8.3 Documents & Connaissance

#### F8.3.1 Admin : Uploader document — POST /api/documents (multipart: file, title, description, roleAccess)
#### F8.3.2 Consulter liste documents — GET /api/documents (all users)
#### F8.3.3 Admin : Consulter stats documents — GET /api/documents/stats (dashboard)
#### F8.3.4 Admin : Mettre à jour métadonnées — PUT /api/documents/{id}
#### F8.3.5 Admin : Archiver document — DELETE /api/documents/{id} (soft delete, status=ARCHIVED)

### 8.4 Tickets & Support

#### F8.4.1 Récupérer schémas catégories — GET /api/tickets/categories (form schemas JSON)
#### F8.4.2 Créer ticket FREE_TEXT — POST /api/tickets (freeTextContent)
#### F8.4.3 Créer ticket FORM — POST /api/tickets (formData validé vs schema)
#### F8.4.4 Consulter ses tickets — GET /api/tickets (ADMIN voit tous, autres leurs propres)
#### F8.4.5 Consulter détail ticket — GET /api/tickets/{id} (avec timeline TicketActivity)
#### F8.4.6 Ajouter réponse ticket — POST /api/tickets/{id}/activities (multipart: content, attachment?)
#### F8.4.7 Admin : Changer statut ticket — PATCH /api/tickets/{id}/status (OPEN/IN_PROGRESS/CLOSED)

[**Pagination — Sections 9-10 suite, puis sections 11-20**]

---

## 9. Workflow Métier Global

### 9.1 Scenario 1 — Chat simple (Happy path)

User (OPERATIONNEL) → Login → POST /api/chat/ask (question) → Chat-Service crée Conversation, persiste Message(USER), appelle RAG → RAG embeds question, retrieves top-K chunks filtrés par rôle, LLM génère réponse → Chat-Service persiste Message(ASSISTANT), retourne ChatResponse → Frontend affiche answer + sources + confidence score.

### 9.2 Scenario 2 — Escalade via Ticket

Expert insatisfait → POST /api/tickets (conversationId, messageId, ticketType=FREE_TEXT) → Chat-Service crée Ticket, creates TicketActivity(SYSTEM) → Admin voit ticket → POST /api/tickets/{id}/activities (content, attachment) → Timeline mis à jour → Admin PATCH status=CLOSED → Ticket archivé.

### 9.3 Scenario 3 — Upload et indexation

Admin → POST /api/documents (multipart: file, title, roleAccess) → Document-Service valide, sauvegarde disque, appelle RAG /documents/upload → RAG parse PDF/DOCX, chunk (800 tokens, overlap 150), embed via OpenAI, store pgvector → Document-Service indexed=true → RAG retrieval future inclut ces chunks (filtrés par roleAccess).

---

## 10. Base de Données

### 10.1 Schéma postgres-app

**Table users**: id (PK), email (UNIQUE), password (bcrypt), fullName, role (ADMIN|EXPERT|OPERATIONNEL), enabled, createdAt

**Table documents**: id (PK), originalFileName, storedFileName (UNIQUE), filePath, contentType, fileSize, title, description, roleAccess, status (ACTIVE|ARCHIVED), uploadedBy, version, indexed, createdAt, updatedAt

**Table conversations**: id (PK), userEmail, userRole, title, createdAt, updatedAt
Index: (userEmail, updatedAt DESC)

**Table messages**: id (PK), conversationId (FK), role (USER|ASSISTANT), content (TEXT), sources (JSON), confidenceScore, createdAt
Index: (conversationId, createdAt ASC)

**Table tickets**: id (PK), ticketNumber (UNIQUE), conversationId?, messageId?, requesterEmail, requesterName, requesterRole, ticketType, status (OPEN|IN_PROGRESS|CLOSED), subject, freeTextContent, formData (JSONB), assignedAdminEmail, createdAt, updatedAt, closedAt

**Table ticketActivities**: id (PK), ticketId (FK), senderEmail?, senderRole (USER|ADMIN|SYSTEM), content, attachmentUrl, createdAt
Index: (ticketId, createdAt ASC)

### 10.2 Schéma postgres-vector

**Table documents_chunks**: id (PK), document_id (UUID), chunk_text (TEXT), embedding (vector(1536)), metadata (JSONB: {source, page, role_access})
Index: HNSW on embedding (cosine_ops), GIN on metadata

---

## 11. APIs et Communication

### Endpoints Summary

**Auth**: POST /api/auth/register, POST /api/auth/login (public)

**Users**: GET /api/users/me, GET /api/users, GET /api/users/{id}, PUT /api/users/{id}, DELETE /api/users/{id} (ADMIN protected)

**Documents**: POST /api/documents, GET /api/documents, GET /api/documents/{id}, GET /api/documents/stats, PUT /api/documents/{id}, DELETE /api/documents/{id}

**Chat**: POST /api/chat/ask, GET /api/chat/conversations, GET /api/chat/conversations/{id}/messages, DELETE /api/chat/conversations/{id}

**Tickets**: GET /api/tickets/categories, POST /api/tickets, GET /api/tickets, GET /api/tickets/{id}, POST /api/tickets/{id}/activities, PATCH /api/tickets/{id}/status

**Static**: GET /uploads/** (whitelist public)

**RAG** (internal): POST /chat/, POST /documents/upload, GET /health

---

## 12. Sécurité

### 12.1 Authentification JWT

- Type: HS256 (HMAC-SHA256)
- Secret: JWT_SECRET from .env
- Payload: sub=email, role, exp, iat
- Duration: 900s (15 min default)
- Validation: Gateway JwtAuthenticationFilter before all routes
- Extraction: Spring Security Authentication object

### 12.2 Autorisation RBAC

- Levels: Controller (@PreAuthorize), Service (programmatic), RAG (vector filtering)
- Roles: ADMIN, EXPERT, OPERATIONNEL
- Document access: roleAccess hierarchy (COMMUN < OPERATIONNEL < EXPERT < ADMIN)

### 12.3 Validation Entrées

- Frontend: Template validators, Reactive Forms
- Backend: @Valid, @NotBlank, @NotNull
- RAG: Pydantic models
- File uploads: MIME type check, size limit ≤ 10MB

### 12.4 Protections

- CSRF: Disabled (stateless JWT)
- CORS: Whitelist localhost:4200, configurable .env
- SQL Injection: Mitigated by JPA prepared statements
- File Upload: UUID generation, type/size validation
- Password: BCrypt hashing

### 12.5 Chiffrement

- Passwords: BCrypt (constant-time comparison)
- Transit: HTTPS (prod), HTTP (dev localhost)
- At rest: DB plaintext (should migrate to encryption-at-rest)

### 12.6 Exception Handling

- GlobalExceptionHandler centralizes errors
- Returns JSON with status, message, no stack traces exposed
- Specific handlers for ValidationErrors, RagServiceException, AccessDenied

---

## 13. Logique Métier

### 13.1 Algorithme RAG

**Pipeline**:
1. Document chunking: Parser (PyMuPDF/python-docx) → pages → Chunker (Tiktoken, 800 tokens, 150 overlap) → chunks tagged {source, page, role_access}
2. Embedding: OpenAI text-embedding-3-small → 1536D vectors
3. Vector Store: pgvector HNSW index (fast similarity search)
4. Retrieval: embed question → similarity search (filtered by user role) → top-K chunks
5. Prompt: system + context (formatted chunks) + question
6. LLM: ChatOpenAI (gpt-4o-mini, temperature=0.0) → answer
7. Confidence: avg cosine similarity of top-K

### 13.2 Ticket number generation

- Format: TICKET-{uuid or timestamp} (exact pattern not visible, UNIQUE constraint enforced)

### 13.3 Formulaires dynamiques

- Frontend fetches schemas via GET /api/tickets/categories
- TicketCategorySchemas returns Map<TicketType, List<FieldRule>>
- Backend validates formData vs schema before INSERT

### 13.4 Conversation deduplication

- If conversationId provided → reuse; else create new
- All messages appended to same conversation

### 13.5 Timestamps

- @PrePersist sets createdAt = LocalDateTime.now()
- @PreUpdate sets updatedAt = LocalDateTime.now()
- Ticket closedAt set manually on status=CLOSED

### 13.6 Async processing

- **Currently synchronous**: document indexation, RAG invocation (blocking WebClient)
- Risk: slow operations block thread
- Potential: async with Spring @Async, CompletableFuture, message queue

---

## 14. Interfaces Utilisateur

### 14.1 Pages & Routes

| Route | Component | Protected | Fonctions |
|-------|-----------|:---:|---|
| /auth/login | LoginComponent | ✗ | Email/password login |
| /auth/register | RegisterComponent | ✗ | Email/password/role register |
| /chat | ChatComponent | ✓ authGuard | Question UI, history, ticket escalation |
| /admin/dashboard | DashboardComponent | ✓ adminGuard | KPIs, stats |
| /admin/documents | DocumentsComponent | ✓ adminGuard | Upload, list, edit, delete |
| /admin/users | UsersComponent | ✓ adminGuard | List, edit, disable users |
| /admin/tickets | TicketsComponent | ✓ adminGuard | List all tickets, view timeline, reply, change status |

### 14.2 Guards

- **authGuard**: JWT présent? Si non → redirect /auth/login
- **adminGuard**: role == ADMIN? Si non → redirect /chat

### 14.3 Interceptor

- Ajoute Authorization header Bearer JWT
- Gère 401 → logout + redirect

---

## 15. Architecture Logicielle Détaillée

### 15.1 Design Patterns

- **Repository**: Data access abstraction (ConversationRepository, etc.)
- **Dependency Injection**: Spring IoC, @Autowired, @RequiredArgsConstructor
- **DTO**: Separation entity/API contracts (MessageResponse, etc.)
- **Service Layer**: Business logic centralization (ChatService, etc.)
- **Factory**: Object creation (VectorStoreManager.get_vector_store())
- **Singleton**: Spring beans (default scope)
- **Observer/Listener**: JPA lifecycle (@PrePersist, @PreUpdate)
- **Chain of Responsibility**: Filter chain (JWT → CORS → DispatcherServlet)
- **Adapter**: WebClient wrapping HTTP calls
- **Strategy**: RAG fallback vs OpenAI LLM

### 15.2 SOLID Principles

- **S** (Single Responsibility): ✓ Each service has one responsibility
- **O** (Open/Closed): ~ Extensible but some hardcodes (enum TicketType)
- **L** (Liskov Substitution): ✓ Repository interface swappable
- **I** (Interface Segregation): ✓ API well-segmented
- **D** (Dependency Inversion): ✓ DI via Spring, no hardcoded dependencies

---

## 16. Diagrammes UML — Description Textuelle

### 16.1 Use Cases

**Actors**: User (ADMIN/EXPERT/OPERATIONNEL), System (RAG/OpenAI)

**Cases**: UC1 Register, UC2 Login, UC3 Manage Users (ADMIN), UC4 Upload Document, UC5 View Stats, UC6 Ask Copilot, UC7 View Conversations, UC8 Create Ticket, UC9 View Tickets, UC10 Reply Ticket, UC11 Change Status (ADMIN), UC12 Index Document (RAG), UC13 Generate RAG Response

### 16.2 Classes principales

```
[User]: id, email, password, fullName, role, enabled, createdAt
[Conversation]: id, userEmail, userRole, title, messages (1-to-many), timestamps
[Message]: id, conversation, role, content, sources (JSON), confidenceScore, createdAt
[Document]: id, title, storedFileName, roleAccess, status, indexed, version, timestamps
[Ticket]: id, ticketNumber, subject, status, ticketType, formData (JSONB), activities (1-to-many), timestamps
[TicketActivity]: id, ticketId, senderRole, content, attachmentUrl, createdAt
```

### 16.3 Sequence Diagram (Ask Copilot)

User → LoginComponent → POST /auth/login → Auth-Service → JWT → Frontend stores token → ChatComponent.ask() → POST /api/chat/ask → Gateway validates JWT → Chat-Service creates Conversation, saves Message(USER), calls RAG → RAG embeds, retrieves, invokes LLM → returns ChatResponse → Chat-Service saves Message(ASSISTANT) → returns 201 → Frontend displays answer + sources

### 16.4 Components

Frontend (Angular SPA) ↔ Gateway (8080) ↔ Auth (8081), User (8082), Document (8083), Chat (8084) ↔ postgres-app (5433), postgres-vector (5435), RAG Service (8085) ↔ OpenAI API

### 16.5 Deployment

Dev: Frontend@4200, Gateway@8080, 5 services@8081-8085, Docker Compose (postgres-app@5433, postgres-vector@5435), RAG@8085, uploads/ on disk, external OpenAI API

---

## 17. Cycle de Vie d'une Action Utilisateur

### 17.1 Workflow: Login → Chat → Answer (detailed timeline)

T0-T5: User accesses localhost:4200, authGuard redirects /auth/login
T5-T10: Submits email/password, LoginComponent.login() → POST /api/auth/login
T10-T15: Gateway routes to Auth-Service, AuthenticationManager validates, JwtUtil generates HS256 token
T15-T20: Returns AuthResponse (token, email, role), stored in localStorage, redirect /chat
T20-T25: ChatComponent loads, GET /api/chat/conversations (lists existing)
T25-T30: User types question, clicks Send → POST /api/chat/ask
T30-T35: Gateway validates JWT, routes Chat-Service, creates Conversation or continues
T35-T40: Saves Message(USER), calls RAG /chat/ (synchronous WebClient.block())
T40-T50: RAG embeds question, similarity search pgvector, retrieves top-6 chunks filtered by role
T50-T60: Formats context, injects into prompt, calls OpenAI GPT-4o-mini
T60-T65: Returns answer + sources + confidence score
T65-T70: Chat-Service saves Message(ASSISTANT), updates Conversation
T70-T75: Returns ChatResponse 201 CREATED
T75-T80: Frontend displays answer, sources, confidence badge
T80+: User can continue same conversation (conversationId passed), or create ticket (escalate)

---

## 18. Points Forts Techniques

### 18.1 Bonnes pratiques

1. **Separation of concerns**: Controllers, Services, Repositories cleanly separated
2. **Multi-layer validation**: Frontend + Backend + RAG
3. **Centralized error handling**: GlobalExceptionHandler (no stack traces exposed)
4. **Stateless JWT**: Scalable, no session affinity needed
5. **Role-based segmentation**: Enforced at Controller, Service, RAG levels
6. **Database indexing**: On frequently queried columns (user_email, conversation_id, etc.)
7. **Swagger documentation**: Auto-generated per service
8. **Lombok**: Reduced boilerplate
9. **Spring IoC**: Testable, decoupled code
10. **pgvector HNSW**: Sub-linear similarity search

### 18.2 Optimisations

1. **HNSW indexing**: Fast vector retrieval
2. **Lazy loading**: Angular components, JPA entities
3. **Document versioning**: Easy rollback
4. **Conversation context reuse**: Avoid re-indexing

### 18.3 Testabilité

1. **Dependency Injection**: Easy mocking
2. **DTO/Entity separation**: Independent testing
3. **Repository interfaces**: Mockable or in-memory impl

---

## 19. Limites et Perspectives

### 19.1 Limites constatées

**Sécurité**:
- No refresh token (15 min JWT)
- No token revocation (valid until expiry)
- No rate limiting (brute-force risk)
- No encryption-at-rest (DB plaintext)
- CORS whitelist hardcoded

**Performance**:
- Synchronous document indexation (blocking)
- Synchronous RAG calls (thread blocking)
- Single postgres-vector (single point of failure)
- No cache (Redis)
- Local disk storage (not cloud, no replication)

**Évolutivité**:
- Chat-Service handles chat + tickets + uploads (mixed concerns)
- No event sourcing/saga pattern
- No service discovery (hardcoded URLs)
- No circuit breaker (Resilience4j)
- No distributed tracing (Jaeger)

**Features**:
- No real-time notifications (WebSocket)
- No bulk document re-indexation
- No conversation sharing
- No role-specific RAG prompts
- No model choice endpoint

**Data Quality**:
- JSONB formData not validated at DB
- Timestamp precision (second level)
- No API versioning

### 19.2 Risques

| Risque | Probabilité | Impact | Mitigation |
|--------|:---:|:---:|---|
| RAG down (OpenAI unreachable) | Modérée | Élevé | Circuit breaker, fallback |
| DB corruption (JSONB) | Faible | Élevé | Schema validation, backups |
| JWT token leak (XSS) | Modérée | Élevé | CSP, HTTPOnly cookies |
| Brute-force login | Faible | Modéré | Rate limiting, CAPTCHA |
| postgres-app failure | Faible | Élevé | Replication, backup |

### 19.3 Évolutions réalistes

**Court terme**: Health checks, rate limiting, request logging, cost tracking

**Moyen terme**: Async indexation, Redis cache, circuit breaker, distributed tracing, WebSocket notifications

**Long terme**: Event sourcing, vector store sharding, federated search, fine-tuning pipeline, Kubernetes deployment

---

## 20. Glossaire

| Terme | Définition |
|-------|-----------|
| **Admin** | Rôle avec accès complet (CRUD users, documents, tickets) |
| **API Gateway** | Proxy centralisé routant requêtes, validant JWT |
| **Authentication** | Vérification identité (login, JWT generation) |
| **Authorization** | Vérification accès ressource (RBAC, @PreAuthorize) |
| **BCrypt** | Algo hachage mots de passe (salt + coût) |
| **Chunk** | Segment document (200-1000 tokens) + embedding |
| **Confidence Score** | Score 0-1 fiabilité réponse RAG (basé similitude cosinus) |
| **Conversation** | Session chat User-AI, persiste messages |
| **DTO** | Data Transfer Object (requête/réponse API) |
| **Embedding** | Vecteur 1536D représentant texte (via OpenAI) |
| **Entity** | Classe JPA @Entity mappée table DB |
| **Expert** | Rôle utilisateur (chat, tickets, accès EXPERT+) |
| **Fallback** | Mode dégradé (USE_LOCAL_FALLBACK=true) |
| **Form Schema** | Définition JSON champs formulaire ticket |
| **GlobalExceptionHandler** | Classe centralisée @RestControllerAdvice capturant exceptions |
| **Guard** | Classe Angular CanActivate (route access) |
| **HNSW** | Index structure pgvector (similarity search sub-linéaire) |
| **Interceptor** | Classe Spring interceptant requêtes HTTP |
| **JWT** | JSON Web Token HS256 (stateless auth) |
| **LLM** | Large Language Model (ex: GPT-4o-mini) |
| **Message** | Enregistrement conversation (role USER ou ASSISTANT) |
| **Microservices** | Architecture services indépendants deployables |
| **Operationnel** | Rôle utilisateur (chat, tickets, accès OPERATIONNEL+) |
| **pgvector** | Extension PostgreSQL vector similarity (HNSW index) |
| **Prompt Template** | Pattern texte injecté à LLM |
| **RAG** | Retrieval-Augmented Generation (retrieve docs → LLM) |
| **RBAC** | Role-Based Access Control |
| **Repository** | Interface Spring Data JPA (abstraction DB) |
| **Role** | Label utilisateur (ADMIN, EXPERT, OPERATIONNEL) |
| **Service** | Classe @Service (logique métier) |
| **Similarity Search** | Requête vector DB (K-NN par cosine distance) |
| **Source Reference** | Citation réponse RAG (title, page, excerpt) |
| **Ticket** | Demande support (FREE_TEXT ou FORM, statuts OPEN/IN_PROGRESS/CLOSED) |
| **Tokenizer** | Utilitaire Tiktoken (texte → tokens) |
| **Vector Store** | DB spécialisée embeddings (pgvector) |
| **WebClient** | HTTP client Spring non-blocking |

---

**DOCUMENT AUDIT COMPLET — SECTIONS 1-20**

*Généré pour knowledge transfer — Second LLM rédigera rapport académique basé sur ce document.*

