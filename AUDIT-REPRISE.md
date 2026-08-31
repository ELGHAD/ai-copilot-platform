# Document de référence — ALTEN AI Copilot
## Audit de reprise (knowledge transfer) à destination d'un rédacteur sans accès au code

> **Statut de ce document.** Il décrit l'état du dépôt tel qu'exploré le 2026-08-30,
> branche `main`, commit `93e70ad`. Toute affirmation structurante est tracée par
> `(source: chemin — Classe/Fonction)`. Les points non déductibles du code sont
> marqués `[NON DÉTERMINÉ DANS LE CODE — à vérifier]`.
>
> **Avertissement sur l'arbre de travail.** Le dépôt ne contient qu'un seul commit
> (« Initial commit - clean setup ») et de très nombreux fichiers modifiés non
> commités. L'historique Git n'est donc **pas** une source d'information exploitable
> sur l'évolution du projet.

---

## 1. Résumé Exécutif

**Nom du projet.** ALTEN AI Copilot (source: `README.md` ; groupId Maven `com.alten`,
packages `com.alten.*`). ALTEN est décrite dans le code comme « une entreprise
d'ingénierie et de conseil en technologie » (source: `rag-service/app/services/rag_chain.py`
— `SYSTEM_PROMPT`).

**Domaine métier.** Support interne / assistance aux employés. Deux briques
fonctionnelles couplées :

1. **Un assistant conversationnel documentaire (RAG)** qui répond aux questions des
   employés en s'appuyant *exclusivement* sur les documents internes de l'entreprise.
2. **Un système de ticketing (helpdesk IT)** qui prend le relais quand l'assistant ne
   satisfait pas l'utilisateur.

**Objectif principal.** Réduire la sollicitation humaine (responsables, RH, support IT)
en répondant automatiquement, de façon sourcée et non hallucinée, aux questions dont la
réponse existe déjà dans la documentation interne — et router proprement vers un ticket
humain les cas non couverts.

**Problèmes résolus (déduits du code).**

| Problème | Preuve dans le code |
|---|---|
| Information interne dispersée et difficile à retrouver | Pipeline complet parse→chunk→embed→retrieve (`rag-service/app/routers/documents.py`) |
| Risque d'hallucination d'un LLM généraliste | Prompt à règles absolues + phrase de refus imposée (`rag_chain.py` — `SYSTEM_PROMPT`) |
| Cloisonnement de l'information par niveau d'habilitation | Filtre de métadonnées par rôle à la récupération (`embedder.py` — `VectorStoreManager.role_filter`) |
| Absence de traçabilité des réponses IA | Chaque réponse cite ses sources, persistées en base (`Message.sources`) |
| Impasse quand l'IA ne sait pas répondre | Création de ticket depuis la réponse insatisfaisante (`chat.html` — bouton « Pas satisfait ? Créer un ticket ») |

**Utilisateurs cibles.** Employés d'ALTEN, répartis en trois rôles : `ADMIN`,
`EXPERT`, `OPERATIONNEL` (source: `backend/auth-service/.../model/Role.java`).
Les rôles `EXPERT` et `OPERATIONNEL` sont les consommateurs de l'assistant ;
`ADMIN` administre la plateforme (documents, utilisateurs, tickets).

**Type d'architecture, en une phrase.** Application web à architecture
**microservices** : un frontend Angular (SPA) derrière un API Gateway Spring Cloud
qui route vers quatre microservices métier Spring Boot partageant une base
PostgreSQL, plus un service d'IA Python/FastAPI autonome adossé à une base
vectorielle pgvector distincte.

---

## 2. Contexte Fonctionnel

### 2.1 Besoin initial (reconstitué à partir du code, non documenté explicitement)

Le code ne contient aucun cahier des charges. Le besoin suivant est **déduit** des
choix d'implémentation observés, et doit être présenté comme une reconstitution :

- Les employés posent de façon récurrente des questions dont la réponse figure dans
  des documents internes (procédures, notes RH, documentation technique). Les formats
  acceptés à l'ingestion (PDF, DOC, DOCX — source: `DocumentService.ALLOWED_CONTENT_TYPES`)
  et la phrase de repli imposée au LLM (« Veuillez consulter votre responsable ou les
  ressources humaines » — `rag_chain.py`) désignent un corpus de type **procédural et RH**.
- Toute l'IA répond **en français** par contrainte de prompt (règle 6 du `SYSTEM_PROMPT`),
  alors que l'interface est bilingue FR/EN (`translation.service.ts`). Le corpus
  documentaire cible est donc francophone.
- Le ticketing n'est pas générique : ses catégories (installation logicielle, demande
  d'accès, panne matérielle, réseau/VPN, compte utilisateur — `TicketCategorySchemas`)
  décrivent un **helpdesk informatique interne**.

`[NON DÉTERMINÉ DANS LE CODE — à vérifier]` : le contexte de production (PFE, POC,
projet interne réel), le volume documentaire visé, le nombre d'utilisateurs attendu.
Un commentaire de `FileStorageService` mentionne « sufficient for a PFE demo », ce qui
suggère un **Projet de Fin d'Études**, mais ce n'est qu'un indice ponctuel.

### 2.2 Contraintes métier identifiables

Toutes celles ci-dessous sont **codées en dur** et donc vérifiables :

**Contraintes sur la réponse de l'IA** (source: `rag_chain.py` — `SYSTEM_PROMPT`) :
- Répondre **uniquement** à partir du contexte fourni ;
- Si l'information est absente, retourner **exactement** : « Je ne trouve pas cette
  information dans les documents disponibles. Veuillez consulter votre responsable ou
  les ressources humaines. » ;
- Interdiction de mobiliser la connaissance propre du modèle, interdiction de
  supposition ou d'extrapolation ;
- Citation systématique de la source ;
- Réponse toujours en français ;
- `temperature = 0.0` par défaut (`.env.example` — `LLM_TEMPERATURE`), soit un réglage
  déterministe, cohérent avec l'exigence de non-invention.

**Contraintes d'habilitation documentaire** (source: `embedder.py` — `role_filter`) :
- Un document porte un niveau d'accès `roleAccess` ∈ {`COMMUN`, `ADMIN`, `EXPERT`, `OPERATIONNEL`} ;
- Un utilisateur voit les documents `COMMUN` **plus** ceux marqués de son propre rôle ;
- `ADMIN` voit tout (aucun filtre appliqué) ;
- Rôle absent ou vide → seuls les documents `COMMUN` sont visibles (repli restrictif).

**Contraintes sur les fichiers** :

| Contrainte | Valeur | Source |
|---|---|---|
| Types de documents ingérables | PDF, DOC, DOCX | `DocumentService.ALLOWED_CONTENT_TYPES` |
| Taille max document (Spring) | 10 Mo | `DocumentService.MAX_FILE_SIZE` + `application.properties` |
| Types acceptés côté RAG | `.pdf`, `.docx` seulement | `documents.py` — `allowed_extensions` |
| Taille max côté RAG | 50 Mo | `documents.py` — `MAX_SIZE` |
| Pièces jointes de ticket | PNG, JPEG, JPG, GIF, WEBP | `FileStorageService.ALLOWED_TYPES` |
| Taille max pièce jointe | 5 Mo | `FileStorageService.MAX_SIZE_BYTES` |

> **Incohérence à signaler.** Le `.doc` (`application/msword`) est accepté par
> document-service mais **rejeté** par rag-service, qui n'autorise que `.pdf` et
> `.docx`. Un `.doc` est donc stocké et référencé en base, mais son indexation
> échoue ; le document reste alors `indexed=false` (le `catch` de
> `DocumentService.uploadDocument` avale l'exception).

**Contraintes sur les questions** (source: `rag-service/app/routers/chat.py`) :
question non vide, longueur maximale 2000 caractères.

**Contraintes sur les mots de passe** : minimum 8 caractères, validé côté backend
(`RegisterRequest` — `@Size(min = 8)`) et côté frontend (`login.ts`, `register.ts` —
`Validators.minLength(8)`).

### 2.3 Workflows imposés (machines à états)

**Cycle de vie d'un document** (`DocumentStatus`) : `ACTIVE` → `OBSOLETE` → `ARCHIVED`.
`ACTIVE` à la création (`Document.onCreate`). L'archivage est un *soft delete* : le
statut passe à `ARCHIVED` **et** le fichier est supprimé du disque, mais la ligne en
base subsiste (`DocumentService.deleteDocument`).

**Cycle de vie d'un ticket** (`TicketStatus`) : `OPEN` → `IN_PROGRESS` → `CLOSED`.
`OPEN` à la création. Transition **automatique** `OPEN` → `IN_PROGRESS` dès la première
réponse d'un ADMIN (`TicketService.addActivity`). Le passage à `CLOSED` horodate
`closedAt`. Seul un ADMIN peut changer le statut manuellement.

**Cycle de vie d'un compte** : `enabled = true` à la création (`User.onCreate`). La
« suppression » d'un utilisateur est une désactivation (`UserService.disableUser`) ;
aucune suppression physique n'existe. Un compte désactivé ne peut plus se connecter
(`SecurityConfig.userDetailsService` — `.disabled(!user.isEnabled())` → HTTP 403).

### 2.4 Scénarios d'usage typiques

1. **Consultation documentaire.** Un OPERATIONNEL se connecte, pose une question en
   langage naturel, obtient une réponse sourcée avec extraits et numéros de page,
   déplie le panneau des sources pour vérifier, poursuit la conversation.
2. **Escalade vers le support.** La réponse ne satisfait pas ; l'utilisateur clique
   « Pas satisfait ? Créer un ticket », choisit entre un formulaire catégorisé et un
   message libre, soumet. Le ticket est rattaché à la conversation et au message
   déclencheur.
3. **Suivi de ticket.** L'utilisateur ouvre « Mes tickets », consulte la chronologie,
   répond à l'administrateur avec du texte et/ou une capture d'écran.
4. **Alimentation de la base de connaissance.** Un ADMIN téléverse un document, fixe
   son titre et son niveau d'accès ; l'indexation vectorielle est déclenchée
   automatiquement et synchronement.
5. **Administration.** Un ADMIN consulte le tableau de bord (graphiques), gère les
   rôles et l'activation des comptes, traite la file des tickets.

---

## 3. Architecture Générale

### 3.1 Style architectural

**Microservices** avec **API Gateway**, chaque service Spring Boot étant lui-même
organisé **en couches** (Controller → Service → Repository → Entité).

Preuves de l'architecture microservices :
- Cinq applications Spring Boot indépendantes, chacune avec son `pom.xml`, son
  `@SpringBootApplication` et son port dédié (8080–8084) ;
- `backend/pom.xml` est un **agrégateur pur** (`<packaging>pom</packaging>`, aucune
  gestion de dépendances héritée) : chaque module garde
  `spring-boot-starter-parent` comme parent propre et reste buildable seul ;
- Un service supplémentaire en Python/FastAPI (port 8085), techniquement hétérogène ;
- Aucun appel direct de service Spring à service Spring : les seules communications
  inter-services vont vers rag-service.

Preuves de l'organisation en couches, uniforme dans les quatre services métier —
paquets `controller/`, `service/`, `repository/`, `model/`, `dto/`, `security/`,
`config/`, `exception/`.

**Ce que l'architecture n'est pas** (à ne pas surinterpréter dans un rapport) :
- Ce n'est **pas** une architecture hexagonale : aucune interface de port, aucune
  inversion de dépendance ; les services dépendent directement des `JpaRepository`
  concrets de Spring Data.
- Il n'y a **ni service discovery** (pas d'Eureka/Consul), **ni configuration
  centralisée** (pas de Spring Cloud Config), **ni circuit breaker**
  (pas de Resilience4j). Les URLs cibles sont des variables d'environnement avec
  valeurs par défaut en dur (`gateway/application.properties`).
- La « base de données par service » n'est **pas** respectée : les quatre services
  métier partagent la même base `alten_copilot` (voir §3.3).

### 3.2 Répartition des responsabilités

| Composant | Techno | Port | Responsabilité | Base de données |
|---|---|---|---|---|
| `frontend/` | Angular 21 | 4200 | SPA, rendu, état client, garde de routes | — (localStorage) |
| `backend/gateway` | Spring Cloud Gateway (WebFlux) | 8080 | Point d'entrée unique, routage, validation JWT globale, CORS | aucune |
| `backend/auth-service` | Spring Boot MVC | 8081 | Inscription, connexion, hachage, émission des JWT | `alten_copilot` |
| `backend/user-service` | Spring Boot MVC | 8082 | Profils, rôles, activation, statistiques utilisateurs | `alten_copilot` |
| `backend/document-service` | Spring Boot MVC | 8083 | Métadonnées documentaires, stockage disque, déclenchement de l'indexation | `alten_copilot` |
| `backend/chat-service` | Spring Boot MVC + WebFlux | 8084 | Conversations, messages, tickets, pièces jointes, service statique `/uploads` | `alten_copilot` |
| `rag-service/` | FastAPI + LangChain | 8085 | Parsing, chunking, embeddings, recherche vectorielle, génération LLM | `rag_db` (pgvector) |

### 3.3 Bases de données

Deux instances PostgreSQL, définies en conteneurs (source: `compose.yml`) :

| Base | Image | Port hôte | Consommateurs |
|---|---|---|---|
| `alten_copilot` | `postgres:16` | 5433 | auth, user, document, chat |
| `rag_db` | `pgvector/pgvector:pg16` | 5435 | rag-service |

Les schémas ne sont pas versionnés : `spring.jpa.hibernate.ddl-auto=update` dans les
quatre services, et LangChain crée lui-même ses tables vectorielles. Il n'existe **ni
Flyway ni Liquibase**. L'extension `vector` est créée par un script d'init monté dans
le conteneur (`docker/init-vector.sql`) et, en redondance, au démarrage du service
Python (`app/db/vector_store.py` — `init_database`).

### 3.4 Services externes

| Service | Usage | Configuration |
|---|---|---|
| API OpenAI (ou tout endpoint compatible) | Embeddings + génération | `OPENAI_API_KEY`, `OPENAI_BASE_URL`, surcharges `EMBEDDING_BASE_URL` / `LLM_BASE_URL` |
| Web Speech API du navigateur | Dictée vocale de la question | `speech-recognition.service.ts` — 100 % côté client, aucun appel réseau |

Modèles par défaut : `text-embedding-3-small` (1536 dimensions, contrainte du schéma
vectoriel existant) et `gpt-4o-mini` (source: `.env.example`).

### 3.5 Diagramme textuel des couches

```
┌──────────────────────────────────────────────────────────────────────┐
│  NAVIGATEUR — SPA Angular (localhost:4200)                           │
│  Routes gardées (authGuard, adminGuard) · Services HTTP · Signals     │
│  authInterceptor : injecte "Bearer <jwt>", capte les 401             │
└───────────────────────────────┬──────────────────────────────────────┘
                                │  /api/**, /uploads/**   (proxy dev, même origine)
┌───────────────────────────────▼──────────────────────────────────────┐
│  GATEWAY (8080) — Spring Cloud Gateway                                │
│  JwtAuthenticationFilter (GlobalFilter, order = -1)                   │
│  Vérifie signature + expiration. Whitelist : login, register, /uploads/│
│  Ne vérifie AUCUN rôle — l'autorisation est déléguée aux services     │
└──┬──────────┬──────────────┬───────────────┬─────────────────────────┘
   │          │              │               │
   ▼          ▼              ▼               ▼
 auth      user          document          chat
 (8081)    (8082)        (8083)            (8084)
   │          │              │               │
   │  Chaque service : JwtAuthenticationFilter (OncePerRequestFilter)  │
   │  → SecurityContext → @PreAuthorize / contrôle applicatif          │
   │  → Service (règles métier) → Repository (Spring Data JPA)         │
   │          │              │               │
   └──────────┴──────┬───────┴───────────────┘
                     ▼
        PostgreSQL « alten_copilot » (5433)
        users · documents · conversations · messages
        tickets · ticket_activities
                                 │ (document: RestTemplate, chat: WebClient)
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  RAG-SERVICE (8085) — FastAPI                                         │
│  /documents/upload : parse → chunk → embed → store                    │
│  /chat/           : retrieve (MMR + filtre rôle) → prompt → LLM       │
│  AUCUNE vérification de JWT sur ce service                            │
└──────────────┬──────────────────────────────┬────────────────────────┘
               ▼                              ▼
   PostgreSQL+pgvector « rag_db » (5435)   API OpenAI (ou compatible)
   collection « alten_documents »
```

### 3.6 Flux entre modules majeurs

Deux seuls flux inter-services, tous deux à sens unique vers rag-service :

1. **document-service → rag-service**, à l'indexation. `RestTemplate` synchrone,
   `POST /documents/upload` en `multipart/form-data` (`file`, `role_access`)
   (source: `DocumentService.indexDocumentInRag`).
2. **chat-service → rag-service**, à chaque question. `WebClient` avec timeouts
   (connect 5 s, réponse/lecture 30 s, écriture 5 s), `POST /chat/` en JSON
   (`question`, `role`, `session_id`) (source: `WebClientConfig`, `ChatService.callRagService`).

Le frontend ne joint jamais deux services dans un même appel, sauf agrégation
côté client : `DashboardService.getStats()` combine par `forkJoin`
`/api/users/stats` et `/api/documents/stats`, et le tableau de bord y ajoute
`/api/tickets` (source: `dashboard.service.ts`, `dashboard.ts`).

---

## 4. Stack Technique

### 4.1 Backend Java

| Technologie | Rôle dans le projet | Où | Justification probable |
|---|---|---|---|
| Java 17 (cible de compilation) | Langage backend | `<java.version>17</java.version>` dans les 5 `pom.xml` | LTS. **Note** : le `README.md` exige un **JDK 21** pour compiler |
| Spring Boot 4.0.6 | Socle applicatif des 5 services | `spring-boot-starter-parent` | Standard de l'écosystème ; auto-configuration |
| Spring Cloud Gateway 2025.1.1 | Routage, point d'entrée unique | `backend/gateway` | Gateway réactive de l'écosystème Spring |
| Spring Web MVC | Contrôleurs REST | 4 services métier | Modèle servlet classique, synchrone |
| Spring WebFlux | Gateway + client HTTP de chat-service | `gateway`, `chat-service` | Imposé par Gateway ; `WebClient` pour le RAG |
| Spring Data JPA / Hibernate | ORM, dépôts | `repository/`, `model/` | Dépôts dérivés sans SQL manuel |
| Spring Security | Authentification, autorisation | `config/SecurityConfig`, `security/` | BCrypt, chaîne de filtres, `@PreAuthorize` |
| jjwt 0.11.5 | Signature et lecture des JWT | `JwtUtil` (×4), gateway | Bibliothèque JWT usuelle en Java |
| Lombok | Réduction du code répétitif | Entités et DTO | `@Data`, `@Builder`, `@RequiredArgsConstructor` |
| springdoc-openapi 2.8.8 | Documentation Swagger UI | 4 services métier | Documentation interactive des API |
| PostgreSQL JDBC | Pilote base de données | `runtime` des 4 services | Base cible |
| spring-boot-devtools | Rechargement à chaud | 5 services | Confort de développement |

### 4.2 Service RAG (Python)

| Technologie | Version | Rôle | Fichier |
|---|---|---|---|
| FastAPI | 0.115.6 | Framework HTTP asynchrone | `app/main.py` |
| Uvicorn | 0.32.1 | Serveur ASGI | lancé par `dev.sh` |
| LangChain | 0.3.13 | Orchestration RAG, découpage | `chunker.py`, `rag_chain.py` |
| langchain-openai | 0.2.14 | Clients `ChatOpenAI` / `OpenAIEmbeddings` | `rag_chain.py`, `embedder.py` |
| langchain-postgres | 0.0.12 | Intégration `PGVector` | `embedder.py` |
| PyMuPDF (fitz) | 1.25.1 | Extraction texte PDF page par page | `parser.py` — `_parse_pdf` |
| python-docx | 1.1.2 | Extraction DOCX par titres/sections | `parser.py` — `_parse_docx` |
| psycopg (binary) | 3.2.3 | Pilote PostgreSQL | `db/vector_store.py` |
| pgvector | 0.2.5 | Type vectoriel | extension `vector` |
| SQLAlchemy | 2.0.36 | Couche d'accès sous PGVector | dépendance transitive |
| Pydantic / pydantic-settings | 2.10.4 / 2.7.0 | Validation des schémas, configuration | `models.py`, `config.py` |
| tiktoken | 0.8.0 | Tokenisation | dépendance de langchain-openai |
| python-dotenv | 1.0.1 | Chargement en cascade des `.env` | `config.py` |

### 4.3 Frontend

| Technologie | Version | Rôle | Où |
|---|---|---|---|
| Angular | 21.2 | Framework SPA, composants standalone | `frontend/src/app` |
| Angular Material + CDK | 21.2 | Composants d'interface, icônes, tooltips | tous les composants |
| Chart.js | 4.5.1 | Trois graphiques du tableau de bord | `dashboard.ts` |
| RxJS | 7.8 | Flux HTTP, `forkJoin`, `BehaviorSubject` | services `core/` |
| TypeScript | 5.9 | Langage | tout le frontend |
| Signals Angular | — | État local réactif des composants | `signal()` massivement utilisé |
| Prettier | 3.8.1 | Formatage | `devDependencies` |
| Web Speech API | native | Dictée vocale | `speech-recognition.service.ts` |

Angular Material et les Signals coexistent avec `BehaviorSubject` (`AuthService`) :
l'état d'authentification est en RxJS, l'état des écrans en Signals.

### 4.4 Infrastructure et outillage

| Technologie | Rôle | Fichier |
|---|---|---|
| Docker Compose | Les **deux bases** uniquement, pas les applications | `compose.yml` |
| `postgres:16` | Base métier | `compose.yml` |
| `pgvector/pgvector:pg16` | Base vectorielle | `compose.yml` |
| Script Bash `dev.sh` | Orchestrateur de développement (≈18 ko) : `doctor`, `setup`, `build`, `up`, `down`, `status`, `logs`, `restart` | `dev.sh` |
| Maven Wrapper | Build reproductible | `backend/mvnw`, `backend/*/mvnw` |
| Fichier `.env` racine | Source unique de configuration, exportée à tous les processus | `.env.example` |

**Absence de CI/CD.** Aucun `.github/workflows`, `.gitlab-ci.yml`, `Jenkinsfile` ou
équivalent. Aucun `Dockerfile` pour les applications : seules les bases sont
conteneurisées, les sept processus applicatifs tournent nativement (choix assumé dans
`compose.yml` pour préserver devtools et le HMR d'`ng serve`).

---

## 5. Structure Complète du Projet

### 5.1 Racine

| Chemin | Rôle |
|---|---|
| `README.md` | Documentation d'exploitation détaillée : démarrage, prérequis, configuration, flux de requêtes, pièges connus |
| `.env` / `.env.example` | Configuration unique et commentée ; `.env` est gitignoré, `.env.example` est le gabarit |
| `compose.yml` | Définition des deux bases PostgreSQL |
| `dev.sh` | Orchestrateur de développement de tous les processus |
| `docker/init-vector.sql` | `CREATE EXTENSION IF NOT EXISTS vector;` à l'init du volume |
| `backend/` | Cinq modules Maven + POM agrégateur |
| `frontend/` | Application Angular |
| `rag-service/` | Service Python |
| `uploads/` | Racine de stockage : `documents/` et `tickets/<ticketId>/` |
| `.dev/` | Journaux (`logs/`) et PID (`pids/`) produits par `dev.sh` |
| `hamza.md` | Fichier **vide** (0 octet) |
| `## GitHub Copilot Chat.md` | Fichier hors sujet fonctionnel, résidu de session d'outil |

### 5.2 Arborescence d'un service Spring (identique dans les quatre services métier)

| Sous-dossier | Rôle | Exemples |
|---|---|---|
| `controller/` | Points d'entrée REST, mapping HTTP, extraction de l'identité | `AuthController`, `TicketController` |
| `service/` | Règles métier, orchestration, mapping entité↔DTO | `AuthService`, `TicketService` |
| `repository/` | Accès aux données via Spring Data JPA | `UserRepository`, `TicketRepository` |
| `model/` | Entités JPA et énumérations | `User`, `Ticket`, `TicketStatus` |
| `dto/` | Contrats d'entrée/sortie, annotations de validation | `LoginRequest`, `TicketResponse` |
| `security/` | Filtre JWT par requête, utilitaire JWT | `JwtAuthenticationFilter`, `JwtUtil` |
| `config/` | Sécurité, CORS, Swagger, clients HTTP, ressources statiques | `SecurityConfig`, `WebConfig` |
| `exception/` | Gestionnaire global et exceptions métier | `GlobalExceptionHandler` |

`backend/gateway` déroge à ce plan : il ne contient que `config/JwtAuthenticationFilter`
et `exception/GlobalErrorHandler`, sans couche métier ni persistance — cohérent avec un
rôle de pur routeur.

### 5.3 Arborescence du service RAG

| Chemin | Rôle |
|---|---|
| `app/main.py` | Application FastAPI, `lifespan`, CORS, `/health`, `/` |
| `app/config.py` | `Settings` Pydantic, chargement en cascade des `.env`, propriétés dérivées |
| `app/models.py` | Schémas Pydantic d'entrée/sortie |
| `app/routers/documents.py` | `POST /documents/upload`, `DELETE /documents/{id}` |
| `app/routers/chat.py` | `POST /chat/` et `POST /chat`, `GET /chat/health` |
| `app/services/parser.py` | Extraction texte PDF (par page) et DOCX (par section) |
| `app/services/chunker.py` | Découpage récursif et enrichissement des métadonnées |
| `app/services/embedder.py` | `VectorStoreManager` : embeddings, stockage, filtre de rôle, retriever |
| `app/services/rag_chain.py` | Prompt système, chaîne LangChain, mode de secours |
| `app/db/vector_store.py` | Connexion brute psycopg, init de l'extension, health check |
| `docker-compose.yml`, `.env.example`, `README.md` | **Vestiges superseded** par les fichiers racine (avertissement explicite dans le `README.md` racine : conflit de port 5435) |

### 5.4 Arborescence du frontend

| Chemin | Rôle |
|---|---|
| `src/app/app.routes.ts` | Routes, chargement paresseux, application des gardes |
| `src/app/app.config.ts` | Providers racine : routeur, HttpClient + intercepteur, animations |
| `src/app/core/guards/` | `authGuard` (présence de jeton), `adminGuard` (rôle ADMIN) |
| `src/app/core/interceptors/` | `authInterceptor` : en-tête Bearer + gestion centralisée des 401 |
| `src/app/core/models/` | Types partagés : `auth.model.ts`, `user.model.ts`, `ticket.model.ts` |
| `src/app/core/services/` | 9 services : auth, token, chat, document, user, ticket, dashboard, translation, speech |
| `src/app/features/auth/` | Écrans `login`, `register` |
| `src/app/features/chat/` | Écran de conversation + modales de ticket (composant le plus volumineux) |
| `src/app/features/admin/` | `dashboard`, `documents`, `users`, `tickets` |
| `src/app/shared/components/sidebar/` | Navigation latérale sensible au rôle, bascule de langue, déconnexion |
| `src/environments/` | `environment.ts` et `environment.prod.ts` — `apiBaseUrl` vide dans les deux |
| `proxy.conf.json` | Proxy dev : `/api` et `/uploads` → gateway 8080 |

---

## 6. Modules Métier

### 6.1 Module Authentification (auth-service)

**Objectif.** Créer les comptes, vérifier les identifiants, émettre le JWT qui porte
l'identité et le rôle pour toute la plateforme.

**Fonctionnement.**
- *Inscription* (`AuthService.register`) : rejet si l'e-mail existe déjà
  (`existsByEmail`) via `IllegalStateException` → HTTP 409 ; hachage BCrypt ;
  persistance avec `enabled=true` et `createdAt` positionnés par `@PrePersist` ;
  émission immédiate d'un jeton d'accès. Le rôle est **choisi par le demandeur**.
- *Connexion* (`AuthService.login`) : délégation à `AuthenticationManager`
  (`DaoAuthenticationProvider` + `UserDetailsService` + `BCryptPasswordEncoder`) ;
  identifiants faux → `BadCredentialsException` → 401 ; compte désactivé →
  `DisabledException` → 403 ; puis rechargement de l'utilisateur et émission du jeton.

**Données manipulées.** Entité `User` (table `users`) : `id`, `email` (unique),
`password` (haché), `fullName`, `role`, `enabled`, `createdAt`.

**Interactions.** Aucune sortante. Le jeton produit est validé indépendamment par le
gateway et par les quatre services, tous partageant `JWT_SECRET`.

**Valeur métier.** Point unique d'établissement de l'identité et de l'habilitation ;
c'est le rôle inscrit dans le jeton qui pilotera ensuite le filtrage documentaire du RAG.

### 6.2 Module Gestion des utilisateurs (user-service)

**Objectif.** Consultation du profil propre, et administration des comptes.

**Fonctionnement.** Six opérations (`UserService`) : profil courant résolu par l'e-mail
du sujet JWT ; profil par identifiant ; liste de tous les utilisateurs ; mise à jour de
`fullName`, `role`, `enabled` ; désactivation ; statistiques agrégées.

Les statistiques (`getPlatformStats`) sont cinq comptages : `totalUsers`,
`activeUsers`, `adminCount`, `expertCount`, `operationnelCount`, obtenus par
`count()`, `countByEnabled(true)` et `countByRole(role)`.

**Données.** La **même table `users`**, via une entité `User` **redéclarée** dans ce
service — sans le champ `password`, ce qui empêche structurellement l'exposition du
haché par cette voie. `email` est immuable (absent de `UpdateUserRequest`).

**Interactions.** Aucune sortante. Consommé par l'écran d'administration des
utilisateurs et par le tableau de bord.

**Valeur métier.** Confie à l'ADMIN la maîtrise des habilitations : changer le rôle d'un
utilisateur modifie immédiatement le périmètre documentaire qu'il pourra interroger.

### 6.3 Module Gestion documentaire (document-service)

**Objectif.** Constituer et administrer la base de connaissance, et déclencher son
indexation vectorielle.

**Fonctionnement du téléversement** (`DocumentService.uploadDocument`) :
1. Validation : fichier non vide, type MIME dans la liste blanche, taille ≤ 10 Mo ;
2. Génération d'un nom de stockage `UUID + extension` (anti-collision, anti-traversée
   de chemin par la valeur d'origine) ;
3. Écriture sur disque dans `document.upload.path` (`uploads/documents`), création des
   répertoires au besoin ;
4. Persistance des métadonnées, `roleAccess` **normalisé** ;
5. Appel synchrone à rag-service ; succès → `indexed=true`, échec → `indexed=false`
   **sans propager l'erreur** ;
6. Retour du DTO.

**Normalisation du niveau d'accès** (`normalizeRoleAccess`) : accepte `ADMIN`,
`EXPERT`, `OPERATIONNEL`, `COMMUN` et leurs variantes préfixées `ROLE_`, insensibles à
la casse ; **toute autre valeur, ainsi que null ou vide, retombe sur `COMMUN`** — c'est-à-dire
sur le niveau le **plus permissif**. C'est un choix de repli notable pour un
mécanisme d'habilitation.

**Mise à jour** (`updateDocument`) : modifie titre, description, `roleAccess`, statut,
puis **repositionne `indexed=false`**, signalant qu'une réindexation est requise — mais
**aucune réindexation n'est déclenchée**, et aucun mécanisme (tâche planifiée, file)
n'exploite ce drapeau. Le dépôt expose pourtant la requête
`findByIndexedFalseAndStatus` prévue à cet effet, non appelée par ce chemin.

**Archivage** (`deleteDocument`) : statut `ARCHIVED` + suppression du fichier disque.
**Les vecteurs correspondants ne sont pas supprimés** de pgvector : rag-service expose
bien `DELETE /documents/{document_id}`, mais document-service ne l'appelle jamais, et
ne conserve d'ailleurs pas le `document_id` (UUID) généré côté Python. Un document
archivé continue donc d'alimenter les réponses de l'IA.

**Données.** Entité `Document` (table `documents`), 15 colonnes (voir §10).

**Interactions.** Sortante unique vers `POST /documents/upload` de rag-service.

### 6.4 Module Conversation / RAG (chat-service)

**Objectif.** Orchestrer le dialogue, historiser questions et réponses, exposer les
sources.

**Fonctionnement de `ChatService.ask`** :
1. `resolveConversation` : si `conversationId` est fourni, chargement (échec → 404) ;
   sinon création d'une conversation titrée « New conversation », portant l'e-mail et
   le rôle de l'utilisateur ;
2. Persistance du message `USER` ;
3. Appel de rag-service ;
4. Sérialisation des sources en JSON et persistance du message `ASSISTANT` avec
   sources et score de confiance ;
5. `updateConversation` : si le titre vaut encore « New conversation », il devient la
   question tronquée à 60 caractères suffixée de « ... » ; sauvegarde (met à jour
   `updatedAt` via `@PreUpdate`) ;
6. Retour de la réponse et de son contexte.

**Robustesse de l'appel RAG** (`callRagService`) — comportement notable et commenté :
- L'URI est `/chat/` **avec barre oblique finale**, car la route FastAPI est enregistrée
  sous ce chemin et un `POST /chat` renverrait une redirection 307 que `WebClient` ne
  suit pas, produisant un corps vide ;
- Les statuts **3xx**, 4xx et 5xx sont tous traités comme des erreurs
  (`RagServiceException`) ;
- Un corps vide (donc `block()` retournant `null`) lève explicitement une exception,
  au lieu de laisser un `null` provoquer un `NullPointerException` plus loin ;
- Toute exception est convertie en `RagServiceException`, traduite en **HTTP 503** par
  le gestionnaire global — le frontend peut ainsi distinguer « IA indisponible » d'une
  requête invalide. Aucun repli silencieux.

> **Conséquence fonctionnelle importante.** La question `USER` est persistée **avant**
> l'appel au RAG. Si le RAG échoue, la question reste en base sans réponse associée :
> l'historique peut contenir des messages `USER` orphelins.

**Sur le score de confiance.** `RagResponse` attend un champ `confidence_score`
(`@JsonProperty`), et le frontend l'affiche en pourcentage coloré (vert ≥ 80 %, orange
≥ 50 %, rouge en dessous — `chat.ts` — `getConfidenceColor`). Or **le service RAG ne
produit jamais ce champ** : son `ChatResponse` Pydantic ne contient que `answer`,
`sources` et `session_id` (vérifié dans `rag-service/app/models.py`). Le score est
donc toujours `null` côté Java, et le bloc n'est jamais affiché puisque le gabarit
exige `confidenceScore > 0`. **Fonctionnalité prévue de bout en bout mais non
implémentée dans la chaîne.**

**Correspondance des noms de champs des sources.** Le RAG renvoie
`{content, source, page}` tandis que le gabarit Angular lit
`{documentTitle, page, excerpt}`. La réconciliation passe par `@JsonAlias` sur
`SourceReference` (`source`/`document_title` → `documentTitle`, `content` → `excerpt`),
choisi plutôt que `@JsonProperty` afin de ne renommer que l'entrée et de préserver les
noms attendus en sortie par le frontend.

**Isolation.** Consultation et suppression d'une conversation vérifient
`conversation.getUserEmail().equals(userEmail)`. Un ADMIN n'a **aucun** accès aux
conversations d'autrui — il n'existe aucun point d'entrée d'administration des
conversations.

### 6.5 Module Ticketing (chat-service)

**Objectif.** Recueillir les demandes que l'IA n'a pas satisfaites et les faire traiter
par un administrateur, avec un fil de discussion et des pièces jointes.

**Sept types de tickets** (`TicketType`) : `FREE_TEXT` et six formulaires
(`FORM_SOFTWARE_INSTALL`, `FORM_ACCESS_REQUEST`, `FORM_HARDWARE_ISSUE`,
`FORM_NETWORK_VPN`, `FORM_USER_ACCOUNT`, `FORM_OTHER`).

**Modèle de données à schéma dynamique.** Plutôt que neuf colonnes spécialisées (le
commentaire de `Ticket` documente ce remplacement), les champs de formulaire sont
stockés dans une unique colonne `form_data` de type **jsonb**
(`@JdbcTypeCode(SqlTypes.JSON)`). Les schémas de champs sont déclarés côté serveur dans
`TicketCategorySchemas`, sous forme de `record FieldRule(key, label, required, type, placeholder)`,
et exposés par `GET /api/tickets/categories`. Le frontend construit son sélecteur de
catégorie et son formulaire dynamique à partir de cette réponse.

**Validation dynamique** (`TicketService.createTicket`) : pour un type formulaire, le
service parcourt le schéma de la catégorie et lève
`IllegalArgumentException("Champ requis manquant : <label>")` pour tout champ `required`
absent, nul ou vide. La validation n'est donc pas déclarative (pas d'annotations) mais
pilotée par les données — et le même schéma sert au rendu et au contrôle.

> **Incohérence.** `FORM_OTHER` existe dans l'énumération et dans les libellés du
> frontend (`TICKET_CATEGORY_META`) mais **n'a aucun schéma** dans
> `TicketCategorySchemas` (vérifié : la clé est absente de la table `schemas`).
> `getSchema` retournant `List.of()`, un ticket `FORM_OTHER` est accepté sans aucune
> validation et sans aucun champ à afficher.

**Numérotation** (`generateTicketNumber`) : `TCK` + `countAllTickets() + 1` formaté sur
quatre chiffres (`TCK0001`). **Procédé non sûr en concurrence** : deux créations
simultanées calculent le même compteur et produisent le même numéro, que la contrainte
d'unicité sur `ticket_number` fera échouer. Il en va de même après suppression d'un
ticket — un numéro déjà utilisé serait recalculé.

**Chronologie.** Chaque ticket ouvre une `TicketActivity` d'amorce de rôle `SYSTEM`
(« Ticket TCKxxxx created »). Les échanges suivants portent `SenderRole.ADMIN` ou
`USER` selon l'auteur. Une activité exige **du texte, une pièce jointe, ou les deux** —
règle appliquée dans le service (`addActivity`) et non par annotation, précisément
parce qu'une pièce jointe seule est licite.

**Automatisme métier.** Une réponse d'ADMIN sur un ticket `OPEN` le fait passer en
`IN_PROGRESS` sans action explicite.

**Contrôle d'accès** (`checkAccess`) : propriétaire ou ADMIN. La liste est filtrée à la
source — `findAllByOrderByCreatedAtDesc()` pour un ADMIN,
`findByRequesterEmailOrderByCreatedAtDesc(email)` sinon. Le changement de statut est
réservé à l'ADMIN par un test explicite, pas par `@PreAuthorize`.

**Stockage des pièces jointes** (`FileStorageService.store`) : validation du type et de
la taille, nom `UUID + extension`, chemin
`{app.upload.dir}/tickets/{ticketId}/{uuid}.{ext}` absolutisé et normalisé, retour de
l'URL relative `/uploads/tickets/{id}/{fichier}`. Le commentaire de classe indique que
le passage à S3/MinIO ne demanderait de modifier que cette classe.

**Exposition des fichiers.** `WebConfig` publie `/uploads/**` comme ressources
statiques, et cette route est **volontairement ouverte sans authentification** dans
`SecurityConfig` de chat-service *et* dans la liste blanche du gateway. Le motif est
documenté : une balise `<img src="/uploads/...">` chargée par le navigateur ne
transporte pas d'en-tête `Authorization`, et exiger un jeton afficherait toutes les
pièces jointes comme images cassées (voir l'analyse de risque en §12.7).

### 6.6 Module RAG (rag-service)

**Objectif.** Transformer des documents en connaissance interrogeable et produire des
réponses ancrées, filtrées par habilitation.

**Chaîne d'ingestion** (`POST /documents/upload`) :
1. Contrôle de l'extension (`.pdf`, `.docx`) et de la taille (≤ 50 Mo) ;
2. Écriture dans un fichier temporaire, **supprimé dans un `finally`** ;
3. **Parsing** — PDF : page par page via PyMuPDF, pages de moins de 20 caractères
   utiles ignorées ; DOCX : regroupement des paragraphes en sections délimitées par les
   styles `Heading*`, en conservant l'intitulé du titre ;
4. **Nettoyage** (`_clean_text`) : suppression des caractères de contrôle,
   normalisation des espaces, réduction des lignes vides à deux au maximum, et
   **recollage des mots coupés en fin de ligne** (`(\w)-\n(\w)` → `\1\2`), défaut
   classique de l'extraction PDF ;
5. **Découpage** (`RecursiveCharacterTextSplitter`) : 800 de taille, 150 de
   recouvrement, séparateurs hiérarchisés (paragraphes → lignes → ponctuation
   forte → virgules → mots → caractères) ; chunks de moins de 50 caractères écartés ;
6. **Enrichissement des métadonnées** : `document_id`, `role_access`, `chunk_index`,
   `chunk_total`, `chunk_size`, `source`, plus `page`/`total_pages` (PDF) ou
   `section`/`heading` (DOCX) ;
7. **Vectorisation et stockage** dans la collection pgvector `alten_documents`.

> **Note sur les unités.** Le code commente `chunk_size` comme « 800 tokens » alors que
> `length_function=len` mesure des **caractères**. Le découpage est donc caractères, pas
> tokens. À ne pas reprendre tel quel dans un rapport.

**Chaîne de réponse** (`RAGChain.invoke`) :
1. Obtention d'un retriever paramétré par le rôle ;
2. **Une seule** récupération. Le commentaire de `_build_chain` explique que le
   retriever a été délibérément **retiré** de la chaîne LangChain : auparavant, chaque
   question déclenchait **deux** recherches, et les sources retournées à l'utilisateur
   pouvaient différer du contexte réellement vu par le modèle. Correction à la fois de
   performance et de cohérence ;
3. Formatage du contexte (`_format_context`) : chaque chunk préfixé
   `[Source i: fichier (page N)]`, séparé par `\n\n---\n\n` ;
4. Génération par la chaîne `prompt | llm | StrOutputParser` — ou par le mode de secours ;
5. Construction des sources **dédupliquées** sur la clé `source_page`, contenu tronqué
   à 200 caractères.

**Recherche MMR.** `search_type="mmr"` (Maximum Marginal Relevance) avec
`k = max_retrieved_docs` (6), `fetch_k = 3 × k` (18) et `lambda_mult = 0.7` : 18
candidats sont récupérés, puis 6 sont sélectionnés en arbitrant pertinence (70 %) et
diversité (30 %), afin d'éviter six extraits quasi identiques.

**Filtrage par rôle** (`role_filter`) : `ADMIN` → `None` (aucun filtre, accès total) ;
rôle connu → `{"role_access": {"$in": ["COMMUN", <ROLE>]}}` ; rôle absent →
`{"role_access": {"$in": ["COMMUN"]}}`. Le rôle est normalisé en majuscules et le
préfixe `ROLE_` retiré, à la fois dans le routeur et dans le filtre.

**Mode de secours** (`use_local_fallback` ou clé absente) : `FakeEmbeddings(size=1536)`
pour les vecteurs et `_fallback_answer` pour la réponse — restitution du meilleur chunk
tronqué à 600 caractères, préfixé de sa source. Sans document récupéré, la phrase de
refus standard est renvoyée. Ce mode permet de travailler sur les parties non-IA ;
les vecteurs factices rendent toute recherche sémantique inopérante.

**Garde-fou au démarrage** (`main.py` — `lifespan`) : sans clé OpenAI valide et sans
`USE_LOCAL_FALLBACK=true`, le service **refuse de démarrer** avec un message d'erreur
actionnable. Le commentaire précise le défaut corrigé : le service démarrait puis
échouait au premier `/chat/` par un 500 opaque. La validité de la clé est jugée par
`has_openai_credentials`, qui rejette les valeurs vides et les gabarits
(`sk-placeholder`, `your_`, `replace-me`). L'indisponibilité de la **base**, elle, est
tolérée : le service démarre en mode dégradé.

### 6.7 Module Traduction (frontend)

**Objectif.** Interface bilingue français/anglais.

**Fonctionnement.** `TranslationService` expose un signal de langue courante et un
dictionnaire typé par l'interface `Translations` (environ 170 clés). La bascule se fait
depuis la barre latérale (`sidebar.ts` — `toggleLanguage`). Les gabarits lisent
`ts.t().<clé>`.

**Limite.** Les réponses de l'IA sont **toujours en français** (règle 6 du prompt), et
de nombreux messages d'erreur du frontend sont écrits en français en dur (par exemple
« Format non supporté... », « Supprimer cette conversation ? », les libellés de
`TICKET_CATEGORY_META`). Le bilinguisme est donc partiel.

---

## 7. Utilisateurs et Rôles

### 7.1 Rôles détectés

Trois rôles, définis par une énumération **dupliquée** dans auth-service et
user-service (`model/Role.java`) et reprise côté frontend
(`core/models/auth.model.ts` — `enum Role`).

| Rôle | Responsabilités | Limites | Périmètre documentaire (RAG) |
|---|---|---|---|
| `ADMIN` | Administre documents, utilisateurs et tickets ; consulte le tableau de bord | N'accède pas aux conversations des autres utilisateurs | **Tous** les documents (aucun filtre) |
| `EXPERT` | Interroge l'assistant, crée et suit ses tickets | Aucun accès aux écrans d'administration | `COMMUN` + `EXPERT` |
| `OPERATIONNEL` | Idem `EXPERT` | Idem `EXPERT` | `COMMUN` + `OPERATIONNEL` |

`EXPERT` et `OPERATIONNEL` ont des **droits fonctionnels identiques** ; ils ne diffèrent
que par le périmètre documentaire accessible. Le commentaire de `Role` annonce que le
rôle « determines RAG prompt style and document chunk access » : seul le **second** volet
est implémenté — le prompt est unique pour tous les rôles (`SYSTEM_PROMPT` constant).

Il existe une quatrième valeur d'habilitation documentaire, `COMMUN`, qui n'est **pas**
un rôle utilisateur : c'est le niveau « visible par tous », valeur de repli de
`normalizeRoleAccess` et seul niveau visible sans rôle.

Une énumération distincte, `SenderRole` {`USER`, `ADMIN`, `SYSTEM`}, qualifie l'auteur
d'une activité de ticket ; elle ne participe pas à l'autorisation.

### 7.2 Où les rôles sont appliqués

| Mécanisme | Emplacement | Portée |
|---|---|---|
| Revendication `role` du JWT | `JwtUtil.generateAccessToken` (auth) | Transporte le rôle |
| `SimpleGrantedAuthority("ROLE_" + role)` | `JwtAuthenticationFilter` (×4) | Alimente le `SecurityContext` |
| `@PreAuthorize("hasRole('ADMIN')")` | `UserController`, `DocumentController` | Déclaratif, au niveau méthode |
| Test applicatif `isAdmin(userRole)` | `TicketService` | Impératif, dans le service |
| Filtre de métadonnées | `VectorStoreManager.role_filter` | Cloisonnement documentaire |
| `adminGuard` | `core/guards/admin-guard.ts` | Confort d'affichage seulement |
| Rendu conditionnel | `sidebar.html` (`@if user?.role === Role.ADMIN`) | Navigation |

> **Le gateway ne vérifie aucun rôle** : il ne valide que la signature et l'expiration
> du jeton. Toute l'autorisation est portée par les services en aval.

### 7.3 Matrice rôle → fonctionnalité

| Fonctionnalité | ADMIN | EXPERT | OPERATIONNEL | Anonyme |
|---|:--:|:--:|:--:|:--:|
| S'inscrire | ✓ | ✓ | ✓ | ✓ |
| Se connecter | ✓ | ✓ | ✓ | ✓ |
| Consulter son profil (`/me`) | ✓ | ✓ | ✓ | ✗ |
| Lister tous les utilisateurs | ✓ | ✗ | ✗ | ✗ |
| Consulter un utilisateur par id | ✓ | ✗ | ✗ | ✗ |
| Modifier un utilisateur (nom, rôle, activation) | ✓ | ✗ | ✗ | ✗ |
| Désactiver un utilisateur | ✓ | ✗ | ✗ | ✗ |
| Statistiques utilisateurs | ✓ | ✗ | ✗ | ✗ |
| Téléverser un document | ✓ | ✗ | ✗ | ✗ |
| Lister / consulter les documents | ✓ | ✓ | ✓ | ✗ |
| Filtrer les documents par statut | ✓ | ✗ | ✗ | ✗ |
| Modifier / archiver un document | ✓ | ✗ | ✗ | ✗ |
| Statistiques documents | ✓ | ✗ | ✗ | ✗ |
| Poser une question à l'IA | ✓ | ✓ | ✓ | ✗ |
| Consulter **ses** conversations | ✓ | ✓ | ✓ | ✗ |
| Consulter les conversations d'autrui | ✗ | ✗ | ✗ | ✗ |
| Supprimer **sa** conversation | ✓ | ✓ | ✓ | ✗ |
| Créer un ticket | ✓ | ✓ | ✓ | ✗ |
| Lister **ses** tickets | ✓ | ✓ | ✓ | ✗ |
| Lister **tous** les tickets | ✓ | ✗ | ✗ | ✗ |
| Répondre dans un ticket | ✓ (tous) | ✓ (les siens) | ✓ (les siens) | ✗ |
| Changer le statut d'un ticket | ✓ | ✗ | ✗ | ✗ |
| Consulter les schémas de catégories | ✓ | ✓ | ✓ | ✗ |
| Tableau de bord | ✓ | ✗ | ✗ | ✗ |
| Télécharger une pièce jointe (`/uploads/**`) | ✓ | ✓ | ✓ | **✓** |
| Swagger UI des services | ✓ | ✓ | ✓ | **✓** (accès direct au port) |

Les deux dernières lignes sont des ouvertures **volontaires** dans la configuration ;
elles sont analysées en §12.7.

> **Faille d'élévation de privilèges à signaler explicitement.** `POST /api/auth/register`
> est public et `RegisterRequest` accepte un champ `role` librement choisi
> (`@NotNull` seulement, aucune restriction de valeur). N'importe qui, sans
> authentification, peut donc créer un compte `ADMIN` et obtenir immédiatement un jeton
> administrateur. C'est le défaut de sécurité le plus grave du projet.

---

## 8. Fonctionnalités

Recensement exhaustif. Chaque fiche donne l'objectif, les acteurs, les données, le
déroulé et les cas d'erreur effectivement traités dans le code.

### F1 — Inscription

- **Acteur** : tout visiteur (non authentifié).
- **Objectif** : créer un compte et obtenir un jeton d'accès.
- **Données** : `fullName`, `email`, `password`, `role` → entité `User`.
- **Workflow** : saisie du formulaire (`register.ts`, validateurs `required`, `email`,
  `minLength(8)`) → `POST /api/auth/register` → validation `@Valid` → contrôle d'unicité
  de l'e-mail → hachage BCrypt → persistance (`enabled=true`, `createdAt`) → génération
  du JWT → HTTP 201 → stockage du jeton et de l'utilisateur en `localStorage` →
  redirection selon le rôle (`ADMIN` → `/admin/dashboard`, sinon `/chat`).
- **Erreurs** : champs invalides → 400 avec dictionnaire champ→message ; e-mail déjà
  pris → 409 ; autre → 500 générique sans trace technique.
- **Remarque** : le rôle est déclaratif et non contrôlé (voir §7.3).

### F2 — Connexion

- **Acteur** : tout visiteur.
- **Objectif** : s'authentifier.
- **Workflow** : `POST /api/auth/login` → `AuthenticationManager` → comparaison BCrypt →
  rechargement de l'utilisateur → JWT → 200 → persistance locale → redirection par rôle.
- **Erreurs** : identifiants faux → 401 (« Email ou mot de passe incorrect ») ; compte
  désactivé → 403 (« Compte désactivé. Contactez un administrateur. ») ; serveur
  injoignable → statut 0 (« Impossible de contacter le serveur. ») ; le paramètre
  `?expired=1` affiche « Votre session a expiré. » (`login.ts` — `resolveErrorMessage`,
  `ngOnInit`).

### F3 — Déconnexion

- **Acteur** : tout utilisateur authentifié.
- **Workflow** : purge du `localStorage` (`TokenService.clearAll`), remise à `null` du
  `BehaviorSubject`, navigation vers `/auth/login`. **Purement côté client** : aucun
  appel serveur, aucune invalidation ni liste de révocation — le jeton reste
  cryptographiquement valide jusqu'à son expiration.

### F4 — Consultation de son profil

- **Acteur** : tout utilisateur authentifié.
- **Workflow** : `GET /api/users/me` → e-mail lu dans `Authentication.getName()` (sujet
  du JWT) → recherche par e-mail → `UserResponse` (sans mot de passe).
- **Erreurs** : utilisateur introuvable → 404.

### F5 — Administration des utilisateurs

- **Acteur** : ADMIN.
- **Objectif** : lister, modifier, désactiver les comptes.
- **Workflow** : `/admin/users` → `GET /api/users` → tableau filtrable par rôle
  (`ALL`/`ADMIN`/`EXPERT`/`OPERATIONNEL`, filtrage client) ; « modifier » ouvre une
  modale (nom, rôle, activation) → `PUT /api/users/{id}` → mise à jour optimiste de la
  liste ; « désactiver » demande confirmation → `DELETE /api/users/{id}` → 204.
- **Garde-fou** : impossible de désactiver son propre compte — comparaison de l'e-mail
  courant, message « Vous ne pouvez pas désactiver votre propre compte »
  (`users.ts` — `disableUser`). **Contrôle uniquement côté client** ; l'API l'autorise.
- **Erreurs** : non-ADMIN → 403 ; utilisateur inexistant → 404 ; validation → 400.

### F6 — Téléversement d'un document

- **Acteur** : ADMIN.
- **Objectif** : enrichir la base de connaissance.
- **Données** : fichier + `title`, `description`, `roleAccess` → entité `Document` +
  vecteurs pgvector.
- **Workflow** : modale d'upload (pré-validation client du type et de la taille) →
  `POST /api/documents` en `multipart/form-data` → validations serveur → nom UUID →
  écriture disque → persistance → appel à rag-service
  (parse → chunk → embed → store) → `indexed` positionné → 201.
- **Erreurs** : type ou taille invalide → 400 ; dépassement multipart Spring → 400 ;
  échec d'écriture → `RuntimeException` → 500 ; **échec d'indexation → silencieux**,
  le document est créé avec `indexed=false` et l'ADMIN n'est pas averti autrement que
  par la colonne « indexé » du tableau.
- **Choix de valeurs de l'interface** : `COMMUN`, `OPERATIONNEL`, `EXPERT`
  (`documents.ts` — `roles`). `ADMIN` est **absent** de la liste alors que le backend
  l'accepte : impossible, via l'interface, de créer un document réservé aux ADMIN.

### F7 — Consultation des documents

- **Acteurs** : tout utilisateur authentifié pour la liste et le détail ; ADMIN seul
  pour le filtrage par statut.
- **Workflow** : `GET /api/documents` → liste de `DocumentSummary` ;
  `GET /api/documents/{id}` → `DocumentResponse` ; `GET /api/documents/status/{status}`
  → filtrage serveur (ADMIN).
- **Remarque de conception** : le filtrage par statut de l'écran d'administration est
  fait **côté client** (`documents.ts` — `filteredDocuments`) ; le point d'entrée serveur
  équivalent existe mais n'est pas utilisé par le frontend.
- **Observation d'habilitation** : la liste des documents n'est **pas** filtrée par
  `roleAccess`. Un OPERATIONNEL voit les *métadonnées* (titre, description, fichier
  d'origine, déposant) de tous les documents, y compris ceux réservés à d'autres rôles.
  Seul le *contenu* est cloisonné, au niveau du RAG.

### F8 — Modification d'un document

- **Acteur** : ADMIN.
- **Workflow** : modale → `PUT /api/documents/{id}` (titre, description, `roleAccess`,
  statut) → normalisation du rôle → `indexed=false` → sauvegarde.
- **Limite majeure** : les vecteurs déjà stockés conservent l'ancien `role_access`.
  Modifier l'habilitation d'un document **ne change donc pas** le cloisonnement effectif
  des réponses de l'IA, puisque le filtre porte sur les métadonnées des chunks.

### F9 — Archivage d'un document

- **Acteur** : ADMIN.
- **Workflow** : confirmation → `DELETE /api/documents/{id}` → statut `ARCHIVED` →
  suppression du fichier disque → 204.
- **Limite majeure** : les vecteurs subsistent (voir §6.3) ; le document archivé
  continue d'alimenter les réponses.

### F10 — Statistiques (utilisateurs et documents)

- **Acteur** : ADMIN.
- **Workflow** : `GET /api/users/stats` (5 compteurs) et `GET /api/documents/stats`
  (4 compteurs), agrégés par `forkJoin`.
- **Anomalie de calcul** : `indexedDocuments` est renseigné avec
  `findByIndexedFalseAndStatus(ACTIVE).size()`, soit le nombre de documents actifs
  **non** indexés. Le libellé de l'interface (« Indexés ») et le graphique affichent donc
  l'inverse de la grandeur annoncée (source: `DocumentService.getDocumentStats`).
- **Inefficacité** : `activeDocuments` et `archivedDocuments` chargent des listes
  entières pour n'en prendre que la taille, au lieu d'un `countByStatus`.

### F11 — Poser une question à l'IA

- **Acteurs** : ADMIN, EXPERT, OPERATIONNEL.
- **Objectif** : obtenir une réponse sourcée sur les documents accessibles à son rôle.
- **Données** : `Conversation`, `Message` (×2), vecteurs interrogés.
- **Workflow** : saisie (Entrée pour envoyer, Maj+Entrée pour un saut de ligne) →
  affichage optimiste du message utilisateur → `POST /api/chat/ask` →
  résolution/création de la conversation → persistance de la question →
  `POST /chat/` vers le RAG → récupération MMR filtrée par rôle → construction du
  contexte → génération → persistance de la réponse et des sources → titrage de la
  conversation → 201 → affichage de la réponse, du panneau de sources et rafraîchissement
  de la liste des conversations.
- **Erreurs** : question vide → 400 (côté RAG) ; > 2000 caractères → 400 ;
  RAG injoignable, en erreur, en redirection ou renvoyant un corps vide → 503
  (« AI service is currently unavailable. Please try again later. ») ; conversation
  inexistante → 404 ; jeton expiré → 401 intercepté globalement.
- **Faille d'isolation** : `resolveConversation` charge la conversation par identifiant
  **sans vérifier le propriétaire**. Un utilisateur qui devine un `conversationId`
  peut donc **injecter des messages** dans la conversation d'autrui — alors que la
  *lecture* et la *suppression* sont, elles, correctement contrôlées.

### F12 — Historique des conversations

- **Acteur** : propriétaire.
- **Workflow** : `GET /api/chat/conversations` (triées par `updatedAt` décroissant, avec
  le nombre de messages calculé par une requête `countByConversationId` dédiée pour
  éviter le chargement paresseux) ; `GET /api/chat/conversations/{id}/messages` →
  contrôle de propriété → messages triés, sources désérialisées.
- **Erreurs** : conversation inexistante → 404 ; conversation d'autrui → 404 également
  (`IllegalStateException("Access denied to this conversation")` est traduit en 404 par
  le gestionnaire global, non en 403 — ce qui évite incidemment de divulguer l'existence
  de la ressource).

### F13 — Suppression d'une conversation

- **Acteur** : propriétaire.
- **Workflow** : `confirm()` natif → `DELETE /api/chat/conversations/{id}` → contrôle de
  propriété → suppression en cascade des messages
  (`cascade = ALL`, `orphanRemoval = true`) → 204 → retrait de la liste, et retour à
  l'écran vierge si c'était la conversation active.
- **Remarque** : les tickets référencent `conversationId` et `messageId` par de simples
  colonnes `Long` **sans clé étrangère**. Supprimer une conversation laisse donc des
  tickets pointant vers des identifiants inexistants (voir §10.7).

### F14 — Dictée vocale de la question

- **Acteurs** : ADMIN, EXPERT, OPERATIONNEL.
- **Workflow** : bouton micro → `SpeechRecognitionService.start(lang, onResult, onError)`
  → API Web Speech du navigateur, `continuous = true`, `interimResults = true` → la
  transcription est concaténée au texte déjà saisi (préservé dans
  `questionBeforeListening`) → langue déduite de l'interface (`fr-FR` ou `en-US`).
- **Erreurs** : API non supportée → message `chat_mic_not_supported` ; permission
  refusée → rappel d'erreur.
- **Remarque** : 100 % client, aucun trafic réseau, aucun stockage audio. Fonctionne sur
  les navigateurs à base de Chromium.

### F15 — Création d'un ticket

- **Acteurs** : ADMIN, EXPERT, OPERATIONNEL.
- **Objectif** : escalader une demande, typiquement après une réponse insatisfaisante.
- **Workflow** : bouton « Pas satisfait ? Créer un ticket » sous une réponse
  `ASSISTANT` → modale, étape « choice » → soit **message libre** (`freetext`), soit
  **formulaire** : `GET /api/tickets/categories` (chargé une seule fois et mémorisé) →
  étape « categories » → étape « dynamicForm » avec champs générés depuis le schéma →
  `POST /api/tickets` → validation dynamique serveur → numéro `TCKxxxx` → activité
  `SYSTEM` d'amorce → 201 → écran de confirmation affichant le numéro.
- **Rattachement** : `conversationId` (conversation active) et `messageId` (message
  déclencheur) sont joints automatiquement.
- **Erreurs** : type ou sujet ou nom manquant → 400 ; champ requis du schéma absent →
  400 avec le libellé du champ ; échec réseau → message d'erreur dans la modale.
- **Validation client** : `isDynamicFormValid()` vérifie les champs requis avant
  activation du bouton d'envoi.

### F16 — Suivi de ses tickets

- **Acteurs** : EXPERT, OPERATIONNEL (et ADMIN, qui verrait tous les tickets).
- **Workflow** : « Mes tickets » → `GET /api/tickets` (filtré serveur selon le rôle) →
  liste → sélection → `GET /api/tickets/{id}` → chronologie complète → réponse texte
  et/ou image.
- **Erreurs** : ticket inexistant → 404 ; ticket d'autrui → 403 ; échec de chargement du
  détail → retour à la liste.

### F17 — Traitement des tickets (administration)

- **Acteur** : ADMIN.
- **Workflow** : `/admin/tickets` → `GET /api/tickets` (tous) + `GET /api/tickets/categories`
  (pour résoudre les libellés des champs `formData`) → tableau filtrable par statut et
  triable sur `ticketNumber`, `requesterName`, `createdAt` (tri et filtre client) →
  ouverture du détail → chronologie, données de formulaire libellées, réponse,
  changement de statut.
- **Automatisme** : répondre à un ticket `OPEN` le passe en `IN_PROGRESS`, reflété
  immédiatement dans l'interface **et** en base.
- **Erreurs** : non-ADMIN sur le changement de statut → 403 ; échec de chargement des
  schémas → non bloquant, repli sur les clés brutes.

### F18 — Réponse dans un ticket, avec pièce jointe

- **Acteurs** : propriétaire du ticket ou ADMIN.
- **Workflow** : sélection éventuelle d'une image (pré-validation client : type et
  taille, aperçu par `URL.createObjectURL`, libéré par `revokeObjectURL`) →
  `POST /api/tickets/{id}/activities` en `multipart/form-data` → contrôle d'accès →
  règle « texte et/ou pièce jointe » → stockage du fichier → persistance de l'activité →
  éventuelle transition de statut → 201 → ajout à la chronologie affichée.
- **Erreurs** : ni texte ni pièce jointe → 400 (« Un message ou une pièce jointe est
  requis ») ; type non autorisé → 400 ; > 5 Mo → 400 ; ticket inexistant → 404 ;
  accès refusé → 403.

### F19 — Consultation des schémas de catégories

- **Acteur** : tout utilisateur authentifié.
- **Workflow** : `GET /api/tickets/categories` → dictionnaire `TicketType → FieldRule[]`.
- **Usage** : génération du sélecteur de catégorie et du formulaire dynamique côté
  utilisateur ; résolution des libellés côté administration.

### F20 — Tableau de bord

- **Acteur** : ADMIN.
- **Workflow** : `forkJoin` de `DashboardService.getStats()` (lui-même un `forkJoin` de
  deux appels) et de `TicketService.getTickets()` ; les compteurs de tickets par statut
  sont agrégés **côté client** (il n'existe pas de point d'entrée de statistiques sur
  chat-service) ; rendu de trois graphiques Chart.js : anneau des rôles, barres des
  documents, anneau des statuts de tickets.
- **Robustesse d'affichage** : `tryRenderCharts` attend que la vue soit prête puis
  réessaie une fois après 150 ms si les `canvas` ne sont pas encore dans le DOM (le bloc
  `@if` n'est rendu qu'après la fin du chargement) ; les instances précédentes sont
  détruites avant recréation pour éviter les fuites.
- **Erreurs** : échec de l'un des appels → message d'erreur global, aucun graphique.

### F21 — Bascule de langue

- **Acteur** : tout utilisateur authentifié.
- **Workflow** : bouton de la barre latérale → `TranslationService.toggleLanguage()` →
  le signal de langue change → les gabarits se réévaluent.
- **Limite** : la persistance du choix entre deux sessions n'est pas établie
  `[NON DÉTERMINÉ DANS LE CODE — à vérifier]` ; les réponses de l'IA restent en français.

### F22 — Gestion centralisée de l'expiration de session

- **Acteur** : tout utilisateur authentifié.
- **Workflow** : `authInterceptor` capte toute réponse 401 hors `/api/auth/**` → purge du
  stockage local → redirection vers `/auth/login?expired=1` → message explicite ;
  l'erreur est **relancée** pour que la gestion locale du composant s'exécute aussi.
- **Motif documenté** : les jetons vivent 15 minutes et **aucun point d'entrée de
  rafraîchissement n'existe** ; sans ce traitement, chaque composant n'affichait qu'une
  bannière rouge générique sur une page morte.

### F23 — Documentation interactive des API

- **Acteur** : quiconque peut joindre les ports 8081–8084.
- **Workflow** : Swagger UI sur `/swagger-ui/index.html`, spécification sur
  `/v3/api-docs`, « try it out » activé.
- **Remarque** : ces chemins sont `permitAll()` dans les quatre services et ne sont pas
  routés par le gateway — ils ne sont donc joignables qu'en accès direct au port du
  service.

### F24 — Contrôle de santé du service RAG

- **Acteur** : outillage (`dev.sh`).
- **Workflow** : `GET /health` → test de connexion base → `ok` ou `degraded` ;
  `GET /chat/health` → `ok` sans dépendance ; `GET /` → carte d'identité du service.

### F25 — Suppression d'un document du magasin vectoriel

- **Acteur** : appelant direct de rag-service uniquement.
- **Workflow** : `DELETE /documents/{document_id}` → suppression des chunks filtrés par
  `document_id`.
- **Remarque** : point d'entrée **jamais appelé** par la plateforme (voir §6.3) ; il
  n'est ni routé par le gateway ni utilisé par document-service.

---

## 9. Workflow Métier Global

### 9.1 Parcours 1 — De la connexion à une réponse sourcée (parcours principal)

**Flux utilisateur.**
1. L'utilisateur ouvre `http://localhost:4200`, redirigé vers `/auth/login`.
2. Il saisit ses identifiants et valide.
3. Selon son rôle, il arrive sur `/chat` (EXPERT/OPERATIONNEL) ou `/admin/dashboard` (ADMIN).
4. Sur `/chat`, il tape ou dicte sa question et envoie.
5. Son message apparaît immédiatement ; un indicateur de chargement s'affiche.
6. La réponse arrive, suivie d'un compteur de sources dépliable.
7. Il déplie les sources : titre du document, page, extrait.
8. Il poursuit dans la même conversation, ou en démarre une nouvelle.

**Flux de données.**
```
identifiants → JWT{sub: email, role, iat, exp} → localStorage
question + conversationId? → Message(USER) persisté
question + role → RAG → embedding de la question
                      → recherche MMR filtrée par role_access
                      → 6 chunks → contexte formaté
                      → LLM → réponse texte
réponse + sources → Message(ASSISTANT) persisté (sources en JSON)
                 → ChatResponse → rendu
```

**Flux applicatif.**
```
LoginComponent → AuthService → HttpClient → [proxy 4200]
  → Gateway (route auth, chemin en liste blanche)
  → AuthController.login → AuthService.login
      → AuthenticationManager → UserDetailsService → UserRepository → users
      → BCrypt.matches
      → JwtUtil.generateAccessToken
  → TokenService.saveToken/saveUser → BehaviorSubject → redirectByRole

ChatComponent → ChatService.ask → authInterceptor (ajoute Bearer)
  → Gateway.JwtAuthenticationFilter (signature + expiration)
  → chat-service.JwtAuthenticationFilter (→ SecurityContext)
  → ChatController.ask (email du contexte, rôle extrait du jeton)
  → ChatService.ask
      → ConversationRepository (résolution ou création)
      → MessageRepository.save (USER)
      → ragWebClient POST /chat/
          → FastAPI chat router (normalise le rôle)
          → RAGChain.invoke
              → VectorStoreManager.get_retriever(role) → role_filter
              → PGVector recherche MMR → rag_db
              → _format_context → prompt | ChatOpenAI | StrOutputParser
              → API OpenAI
          → ChatResponse{answer, sources, session_id}
      → serializeSources (Jackson) → MessageRepository.save (ASSISTANT)
      → updateConversation (titre + updatedAt)
  → ChatResponse → signal messages → rendu du gabarit
```

### 9.2 Parcours 2 — De l'insatisfaction au ticket traité

**Flux utilisateur.**
1. L'utilisateur juge la réponse insuffisante et clique « Pas satisfait ? Créer un ticket ».
2. Il choisit « formulaire » ou « message libre ».
3. En formulaire : il choisit une catégorie, remplit les champs générés, soumet.
4. Il reçoit le numéro `TCKxxxx`.
5. Plus tard, il ouvre « Mes tickets » et suit la chronologie.
6. Un ADMIN, depuis `/admin/tickets`, ouvre le ticket, lit les données du formulaire,
   répond (avec capture d'écran éventuelle) — le ticket passe automatiquement en
   `IN_PROGRESS`.
7. L'utilisateur voit la réponse et réplique.
8. L'ADMIN clôt le ticket ; `closedAt` est horodaté.

**Flux de données.**
```
GET /api/tickets/categories → {TicketType: FieldRule[]} → formulaire dynamique
saisies → formData{key: value} → validation contre le schéma
        → Ticket{ticketNumber, requesterEmail, requesterRole, formData(jsonb),
                 conversationId, messageId, status=OPEN}
        → TicketActivity{senderRole=SYSTEM, "Ticket TCKxxxx created"}
réponse ADMIN → fichier → uploads/tickets/{id}/{uuid}.png
              → TicketActivity{senderRole=ADMIN, content, attachmentUrl}
              → si status==OPEN alors status=IN_PROGRESS
clôture → status=CLOSED, closedAt=now()
```

**Flux applicatif.**
```
ChatComponent.openTicketModal → chooseTicketType('form')
  → TicketService.getCategories → GET /api/tickets/categories
  → TicketController.getCategories → TicketCategorySchemas.getAllSchemas
  → selectCategory → génération du formulaire
  → submitDynamicForm → createTicket → POST /api/tickets
  → TicketController.createTicket (email du contexte, rôle du jeton)
  → TicketService.createTicket
      → validation dynamique contre le schéma
      → generateTicketNumber (countAllTickets + 1)
      → TicketRepository.save → tickets
      → TicketActivityRepository.save (SYSTEM) → ticket_activities
  → TicketResponse{ticketNumber, activities[]}

TicketsComponent (admin) → sendReply
  → TicketService.addActivity (FormData multipart)
  → TicketController.addActivity (@ModelAttribute)
  → TicketService.addActivity
      → checkAccess (propriétaire ou ADMIN)
      → règle « texte et/ou pièce jointe »
      → FileStorageService.store → disque
      → TicketActivityRepository.save
      → transition OPEN → IN_PROGRESS
  → affichage ; les images se chargent via /uploads/** (sans jeton)
```

### 9.3 Parcours 3 — De l'ingestion d'un document à son exploitation

**Flux utilisateur.** L'ADMIN ouvre `/admin/documents`, clique « Téléverser »,
sélectionne un PDF, renseigne titre, description et niveau d'accès, valide. Le document
apparaît dans le tableau avec son indicateur d'indexation. Un utilisateur du rôle
correspondant peut ensuite obtenir des réponses issues de ce document.

**Flux de données.**
```
fichier + métadonnées → validation (type, taille)
  → nom UUID → uploads/documents/{uuid}.pdf
  → Document{originalFileName, storedFileName, filePath, contentType, fileSize,
             title, description, roleAccess normalisé, status=ACTIVE,
             uploadedBy, version=1, indexed=false}
  → multipart{file, role_access} → rag-service
      → PyMuPDF : pages → texte nettoyé
      → RecursiveCharacterTextSplitter : chunks (800/150)
      → métadonnées {document_id, role_access, page, source, chunk_index, ...}
      → OpenAIEmbeddings → vecteurs 1536D
      → PGVector → collection alten_documents (rag_db)
  → indexed=true
```

**Flux applicatif.**
```
DocumentsComponent.uploadDocument → DocumentService.upload (FormData)
  → POST /api/documents → Gateway → document-service
  → JwtAuthenticationFilter → @PreAuthorize("hasRole('ADMIN')")
  → DocumentController.uploadDocument (email du déposant)
  → DocumentService.uploadDocument
      → validateFile → generateStoredFileName → saveFileToDisk
      → DocumentRepository.save → documents
      → indexDocumentInRag (RestTemplate, FileSystemResource)
          → FastAPI upload_document
          → DocumentParser.parse → DocumentChunker.chunk
          → VectorStoreManager.add_chunks → PGVector
      → indexed=true → save
  → DocumentResponse → ajout en tête de liste
```

---

## 10. Base de Données

### 10.1 Base `alten_copilot` (PostgreSQL 16, port 5433)

Schéma généré par Hibernate (`ddl-auto=update`). Les types indiqués sont ceux
qu'Hibernate produit pour PostgreSQL à partir des annotations.

#### Table `users`

Déclarée par **deux** entités distinctes visant la même table : auth-service (avec
`password`) et user-service (sans `password`).

| Colonne | Type | Contraintes | Description |
|---|---|---|---|
| `id` | `bigserial` | PK, auto-incrément (`IDENTITY`) | Identifiant |
| `email` | `varchar` | NOT NULL, **UNIQUE** | Identifiant de connexion, sujet du JWT |
| `password` | `varchar` | NOT NULL | Haché BCrypt ; **jamais exposé** |
| `full_name` | `varchar` | NOT NULL | Nom complet affiché |
| `role` | `varchar` | NOT NULL, `EnumType.STRING` | `ADMIN` / `OPERATIONNEL` / `EXPERT` |
| `enabled` | `boolean` | NOT NULL | Compte actif ; `false` interdit la connexion |
| `created_at` | `timestamp` | NOT NULL, non modifiable | Fixé par `@PrePersist` |

Relations : **aucune clé étrangère**. Les liens vers les autres tables se font par
**e-mail dénormalisé** (`conversations.user_email`, `documents.uploaded_by`,
`tickets.requester_email`, `ticket_activities.sender_email`) — cohérent avec une
architecture microservices, mais sans intégrité référentielle garantie par le SGBD.

#### Table `documents`

| Colonne | Type | Contraintes | Description |
|---|---|---|---|
| `id` | `bigserial` | PK | Identifiant |
| `original_file_name` | `varchar` | NOT NULL | Nom fourni au dépôt |
| `stored_file_name` | `varchar` | NOT NULL, **UNIQUE** | `UUID + extension` |
| `file_path` | `varchar` | NOT NULL | Chemin disque ; **non exposé** dans les DTO |
| `content_type` | `varchar` | NOT NULL | Type MIME |
| `file_size` | `bigint` | NOT NULL | Taille en octets |
| `title` | `varchar` | NOT NULL | Titre affiché |
| `description` | `text` | nullable | Description libre |
| `role_access` | `varchar` | NOT NULL | `COMMUN`/`ADMIN`/`EXPERT`/`OPERATIONNEL` (normalisé) |
| `status` | `varchar` | NOT NULL, enum chaîne | `ACTIVE`/`OBSOLETE`/`ARCHIVED` |
| `uploaded_by` | `varchar` | NOT NULL | E-mail du déposant |
| `version` | `integer` | NOT NULL | Initialisé à 1 ; **jamais incrémenté** |
| `indexed` | `boolean` | NOT NULL | Indexation réussie |
| `created_at` | `timestamp` | NOT NULL, non modifiable | `@PrePersist` |
| `updated_at` | `timestamp` | NOT NULL | `@PrePersist` / `@PreUpdate` |

Le commentaire de `version` annonce « incremented on re-upload », mais **aucun chemin
de code ne l'incrémente** : il n'existe pas de fonction de re-téléversement.

#### Table `conversations`

| Colonne | Type | Contraintes | Description |
|---|---|---|---|
| `id` | `bigserial` | PK | Identifiant |
| `user_email` | `varchar` | NOT NULL | Propriétaire ; base du contrôle d'accès |
| `user_role` | `varchar` | NOT NULL | Rôle **au moment** de la conversation (photographie) |
| `title` | `varchar` | nullable | « New conversation » puis question tronquée à 60 caractères |
| `created_at` | `timestamp` | NOT NULL, non modifiable | `@PrePersist` |
| `updated_at` | `timestamp` | NOT NULL | Mis à jour à chaque échange ; clé du tri de l'historique |

Relation : `1 — N` vers `messages`, `cascade = ALL`, `orphanRemoval = true`,
`@OrderBy("createdAt ASC")`.

#### Table `messages`

| Colonne | Type | Contraintes | Description |
|---|---|---|---|
| `id` | `bigserial` | PK | Identifiant |
| `conversation_id` | `bigint` | **FK → conversations.id**, NOT NULL, `LAZY` | Conversation porteuse |
| `role` | `varchar` | NOT NULL, enum chaîne | `USER` ou `ASSISTANT` |
| `content` | `text` | NOT NULL | Question ou réponse |
| `sources` | `text` | nullable | **Tableau JSON sérialisé** de `SourceReference` ; nul pour `USER` |
| `confidence_score` | `double precision` | nullable | **Toujours nul en pratique** (voir §6.4) |
| `created_at` | `timestamp` | NOT NULL, non modifiable | `@PrePersist` |

`sources` est du JSON stocké en `text` (sérialisation Jackson applicative), et non en
`jsonb` : il n'est pas interrogeable en SQL. À comparer avec `tickets.form_data`, qui
utilise bien `jsonb`.

#### Table `tickets`

| Colonne | Type | Contraintes | Description |
|---|---|---|---|
| `id` | `bigserial` | PK | Identifiant |
| `ticket_number` | `varchar` | NOT NULL, **UNIQUE** | `TCK0001`, ... |
| `conversation_id` | `bigint` | nullable, **sans FK** | Conversation d'origine |
| `message_id` | `bigint` | nullable, **sans FK** | Message déclencheur |
| `requester_email` | `varchar` | NOT NULL | Demandeur ; base du contrôle d'accès |
| `requester_name` | `varchar` | NOT NULL | Fourni par le client |
| `requester_role` | `varchar` | NOT NULL | Rôle au moment de la demande |
| `ticket_type` | `varchar` | NOT NULL, enum chaîne | Un des 7 `TicketType` |
| `status` | `varchar` | NOT NULL, enum chaîne | `OPEN`/`IN_PROGRESS`/`CLOSED`, défaut `OPEN` |
| `subject` | `varchar` | NOT NULL | Objet |
| `free_text_content` | `text` | nullable | Renseigné pour `FREE_TEXT` |
| `form_data` | **`jsonb`** | nullable | Champs de formulaire, schéma dynamique |
| `assigned_admin_email` | `varchar` | nullable | **Jamais renseigné par aucun code** |
| `created_at` | `timestamp` | NOT NULL | `@PrePersist` |
| `updated_at` | `timestamp` | NOT NULL | `@PreUpdate` |
| `closed_at` | `timestamp` | nullable | Horodaté au passage à `CLOSED` |

`assigned_admin_email` est porté par l'entité, le DTO et le modèle TypeScript, mais
aucun point d'entrée ne permet d'affecter un ticket : **l'affectation n'est pas
implémentée**.

#### Table `ticket_activities`

| Colonne | Type | Contraintes | Description |
|---|---|---|---|
| `id` | `bigserial` | PK | Identifiant |
| `ticket_id` | `bigint` | NOT NULL, **sans FK** | Ticket porteur |
| `sender_email` | `varchar` | NOT NULL | Auteur |
| `sender_role` | `varchar` | NOT NULL, enum chaîne | `USER`/`ADMIN`/`SYSTEM` |
| `content` | `text` | **nullable** | Rendu nullable : une pièce jointe seule est licite |
| `attachment_url` | `varchar` | nullable | URL relative `/uploads/tickets/{id}/{fichier}` |
| `created_at` | `timestamp` | NOT NULL, non modifiable | `@PrePersist` |

### 10.2 Base `rag_db` (pgvector, port 5435)

Schéma **non déclaré dans le projet** : il est créé par `langchain-postgres`. Le code du
projet ne fixe que la collection (`alten_documents`), le mode `use_jsonb=True`, et
l'extension `vector` (`docker/init-vector.sql`, `init_database`).

D'après le fonctionnement documenté de `PGVector`, deux tables sont créées
(`langchain_pg_collection` et `langchain_pg_embedding`), la seconde portant l'identifiant,
le vecteur, le contenu textuel et les métadonnées `jsonb`.
`[NON DÉTERMINÉ DANS LE CODE — à vérifier]` : noms exacts, colonnes et index effectifs
créés par cette version de la bibliothèque.

**Dimension imposée : 1536**, contrainte par la collection existante et par le repli
`FakeEmbeddings(size=1536)`. Le `README.md` avertit que changer de modèle d'embedding
pour une autre dimension impose de **réindexer tous les documents**.

**Métadonnées portées par chaque chunk** (source: `chunker.chunk`) : `source`,
`document_id`, `role_access`, `chunk_index`, `chunk_total`, `chunk_size`, `type`, plus
`page` et `total_pages` (PDF) ou `section` et `heading` (DOCX). `role_access` est la clé
du cloisonnement.

### 10.3 Description verbale du schéma (équivalent d'un MCD)

Un **utilisateur** (`users`) est identifié de façon unique par son e-mail, porte
exactement un rôle et un état d'activation. Il n'est relié aux autres entités par
**aucune clé étrangère** : la liaison est logique, via l'e-mail recopié dans les tables
concernées.

Un utilisateur **possède** zéro à plusieurs **conversations** (relation 1—N logique par
`conversations.user_email`). Chaque conversation **contient** un à plusieurs **messages**
(1—N, seule vraie clé étrangère du schéma : `messages.conversation_id`), ordonnés
chronologiquement et supprimés en cascade avec leur conversation. Un message est soit
une question de l'utilisateur, soit une réponse de l'assistant ; dans ce second cas il
embarque ses sources sous forme de JSON textuel.

Un utilisateur **dépose** zéro à plusieurs **documents** (1—N logique par
`documents.uploaded_by`). Chaque document porte un niveau d'habilitation, un statut de
cycle de vie et un indicateur d'indexation. Le contenu du document n'est **pas** en
base : le fichier est sur disque, et sa représentation vectorielle vit dans la base
`rag_db`, reliée au monde relationnel seulement par la métadonnée `document_id` — un
UUID généré côté Python et **non conservé** côté Java. Le lien entre une ligne de
`documents` et ses vecteurs est donc **rompu** : il n'existe aucun moyen programmatique
de retrouver les chunks d'un document donné depuis la base métier.

Un utilisateur **soumet** zéro à plusieurs **tickets** (1—N logique par
`tickets.requester_email`). Un ticket **peut** référencer la conversation et le message
qui l'ont motivé (0..1 vers chacun, par colonnes `Long` **sans contrainte**). Chaque
ticket **agrège** une à plusieurs **activités** (1—N logique par
`ticket_activities.ticket_id`, sans clé étrangère), formant la chronologie ; la première
est toujours une activité `SYSTEM` d'amorce. Une activité peut porter du texte, une
image, ou les deux.

**Cardinalités de synthèse :**
```
users 1 ──── 0..N conversations 1 ──── 1..N messages
users 1 ──── 0..N documents
users 1 ──── 0..N tickets 1 ──── 1..N ticket_activities
tickets 0..1 ──── 0..1 conversations   (référence faible, non contrainte)
tickets 0..1 ──── 0..1 messages        (référence faible, non contrainte)
documents ····· vecteurs (rag_db)      (lien perdu : document_id non conservé)
```

### 10.4 Contraintes métier au niveau des données

- Unicité : `users.email`, `documents.stored_file_name`, `tickets.ticket_number` ;
- Non-modifiabilité : tous les `created_at` (`updatable = false`) ;
- Valeurs par défaut posées par `@PrePersist` : `enabled=true`, `status=ACTIVE`,
  `version=1`, `indexed=false` ;
- Énumérations stockées en clair (`EnumType.STRING`), donc lisibles et robustes à la
  réorganisation du code ;
- **Aucune contrainte `CHECK`**, aucune contrainte d'intégrité référentielle hors
  `messages.conversation_id`, aucun index métier explicite. Les colonnes très
  sollicitées en lecture (`conversations.user_email`, `tickets.requester_email`,
  `ticket_activities.ticket_id`) **ne sont pas indexées**, à l'exception des index
  implicites des clés primaires et des contraintes d'unicité.

---

## 11. APIs et Communication

### 11.1 Routage du gateway

| Ordre | Identifiant | Prédicat de chemin | Cible |
|---|---|---|---|
| 0 | `auth-service` | `/api/auth/**` | `http://localhost:8081` |
| 1 | `user-service` | `/api/users/**` | `http://localhost:8082` |
| 2 | `document-service` | `/api/documents/**` | `http://localhost:8083` |
| 3 | `chat-service` | `/api/chat/**` | `http://localhost:8084` |
| 4 | `chat-service-tickets` | `/api/tickets/**` | `http://localhost:8084` |
| 5 | `chat-service-uploads` | `/uploads/**` | `http://localhost:8084` |

Chaque cible est surchargeable par variable d'environnement (`AUTH_SERVICE_URL`, etc.).
Les commentaires de configuration précisent que les routes 4 et 5 **manquaient** : les
appels `/api/tickets/**` renvoyaient 404 au gateway et ne fonctionnaient que parce que
l'application Angular appelait chat-service directement sur le port 8084.

**rag-service n'est pas routé par le gateway** : il n'est joignable que par appel
interne depuis document-service et chat-service.

### 11.2 auth-service (port 8081)

| Méthode | Route | Contrôleur | Entrée | Sortie | Auth | Rôles | Cas d'usage |
|---|---|---|---|---|:--:|---|---|
| POST | `/api/auth/register` | `AuthController.register` | `RegisterRequest{fullName, email, password≥8, role}` | 201 `AuthResponse` | **Non** | — | Créer un compte (F1) |
| POST | `/api/auth/login` | `AuthController.login` | `LoginRequest{email, password}` | 200 `AuthResponse` | **Non** | — | S'authentifier (F2) |

`AuthResponse{accessToken, tokenType="Bearer", email, fullName, role}`.
Erreurs : 400 (validation), 401 (identifiants), 403 (compte désactivé), 409 (e-mail pris), 500.

### 11.3 user-service (port 8082)

| Méthode | Route | Contrôleur | Entrée | Sortie | Auth | Rôles | Cas d'usage |
|---|---|---|---|---|:--:|---|---|
| GET | `/api/users/me` | `getCurrentUser` | — (sujet JWT) | 200 `UserResponse` | Oui | tous | Profil propre (F4) |
| GET | `/api/users` | `getAllUsers` | — | 200 `UserSummary[]` | Oui | **ADMIN** | Lister (F5) |
| GET | `/api/users/{id}` | `getUserById` | `id` | 200 `UserResponse` | Oui | **ADMIN** | Consulter |
| GET | `/api/users/stats` | `getStats` | — | 200 `Map<String,Long>` | Oui | **ADMIN** | Tableau de bord (F10) |
| PUT | `/api/users/{id}` | `updateUser` | `UpdateUserRequest{fullName, role, enabled}` | 200 `UserResponse` | Oui | **ADMIN** | Modifier (F5) |
| DELETE | `/api/users/{id}` | `disableUser` | `id` | **204** | Oui | **ADMIN** | Désactiver (F5) |

Autorisation par `@PreAuthorize("hasRole('ADMIN')")`.
Erreurs : 400, 403 (« Access denied — insufficient permissions »), 404, 500.

> **Note de conception REST.** `GET /api/users/stats` est déclaré **après**
> `GET /api/users/{id}` dans le contrôleur. Spring privilégiant les motifs littéraux sur
> les variables de chemin, `stats` est bien résolu par la bonne méthode ; l'ordre de
> déclaration ne crée pas de conflit ici, mais la cohabitation reste fragile.

### 11.4 document-service (port 8083)

| Méthode | Route | Contrôleur | Entrée | Sortie | Auth | Rôles | Cas d'usage |
|---|---|---|---|---|:--:|---|---|
| POST | `/api/documents` | `uploadDocument` | multipart : `file`, `title`, `description?`, `roleAccess` | 201 `DocumentResponse` | Oui | **ADMIN** | Téléverser (F6) |
| GET | `/api/documents` | `getAllDocuments` | — | 200 `DocumentSummary[]` | Oui | tous | Lister (F7) |
| GET | `/api/documents/{id}` | `getDocumentById` | `id` | 200 `DocumentResponse` | Oui | tous | Détail (F7) |
| GET | `/api/documents/stats` | `getStats` | — | 200 `Map<String,Long>` | Oui | **ADMIN** | Tableau de bord (F10) |
| GET | `/api/documents/status/{status}` | `getDocumentsByStatus` | `DocumentStatus` | 200 `DocumentSummary[]` | Oui | **ADMIN** | Filtrer (F7) |
| PUT | `/api/documents/{id}` | `updateDocument` | `DocumentUpdateRequest{title, description, roleAccess, status}` | 200 `DocumentResponse` | Oui | **ADMIN** | Modifier (F8) |
| DELETE | `/api/documents/{id}` | `deleteDocument` | `id` | **204** | Oui | **ADMIN** | Archiver (F9) |

`roleAccess` est validé par `@Pattern(regexp = "ADMIN|EXPERT|OPERATIONNEL")` sur le DTO
de mise à jour — **motif qui exclut `COMMUN`**, alors que `COMMUN` est la valeur de repli
de la normalisation et la valeur proposée par défaut à l'interface d'upload. Une mise à
jour tentant de fixer `COMMUN` est donc rejetée en 400, alors que la création l'accepte.
Le téléversement, lui, reçoit `roleAccess` en `@RequestPart` **sans** validation
déclarative : le motif n'y est pas appliqué, seule la normalisation opère.

Erreurs : 400 (validation, type, taille, dépassement multipart), 403, 404, 500.

### 11.5 chat-service (port 8084) — conversations

| Méthode | Route | Contrôleur | Entrée | Sortie | Auth | Rôles | Cas d'usage |
|---|---|---|---|---|:--:|---|---|
| POST | `/api/chat/ask` | `ChatController.ask` | `ChatRequest{question, conversationId?}` + en-tête `Authorization` | **201** `ChatResponse` | Oui | tous | Poser une question (F11) |
| GET | `/api/chat/conversations` | `getUserConversations` | — | 200 `ConversationSummary[]` | Oui | propriétaire | Historique (F12) |
| GET | `/api/chat/conversations/{id}/messages` | `getConversationMessages` | `id` | 200 `MessageResponse[]` | Oui | propriétaire | Messages (F12) |
| DELETE | `/api/chat/conversations/{id}` | `deleteConversation` | `id` | **204** | Oui | propriétaire | Supprimer (F13) |

Le contrôleur lit l'en-tête `Authorization` en plus de l'objet `Authentication`, parce
que le **rôle** n'est pas porté par le `SecurityContext` sous une forme directement
réutilisable pour le RAG : il est extrait du jeton par `jwtUtil.extractRole(token)`.

Erreurs : 400, 404 (conversation inexistante **ou** appartenant à autrui),
**503** (RAG indisponible), 500.

### 11.6 chat-service (port 8084) — tickets

| Méthode | Route | Contrôleur | Entrée | Sortie | Auth | Rôles | Cas d'usage |
|---|---|---|---|---|:--:|---|---|
| GET | `/api/tickets/categories` | `getCategories` | — | 200 `Map<TicketType, FieldRule[]>` | Oui | tous | Schémas (F19) |
| POST | `/api/tickets` | `createTicket` | `TicketRequest{ticketType, subject, requesterName, conversationId?, messageId?, freeTextContent?, formData?}` | **201** `TicketResponse` | Oui | tous | Créer (F15) |
| GET | `/api/tickets` | `getTickets` | — | 200 `TicketSummary[]` | Oui | ADMIN : tous / autres : les leurs | Lister (F16, F17) |
| GET | `/api/tickets/{id}` | `getTicketDetail` | `id` | 200 `TicketResponse` (avec chronologie) | Oui | propriétaire ou ADMIN | Détail (F16, F17) |
| POST | `/api/tickets/{id}/activities` | `addActivity` | multipart : `content?`, `attachment?` | **201** `TicketActivityResponse` | Oui | propriétaire ou ADMIN | Répondre (F18) |
| PATCH | `/api/tickets/{id}/status` | `updateStatus` | `TicketStatusUpdateRequest{status}` | 200 `TicketResponse` | Oui | **ADMIN** | Changer le statut (F17) |

L'autorisation des tickets est **impérative** (`isAdmin`, `checkAccess` dans
`TicketService`) et non déclarative — à la différence de user-service et document-service.

Erreurs : 400 (validation, champ requis manquant, ni texte ni pièce jointe, type ou
taille de fichier), 403 (`UnauthorizedTicketAccessException`),
404 (`TicketNotFoundException`), 500.

### 11.7 Ressources statiques

| Méthode | Route | Servi par | Auth | Cas d'usage |
|---|---|---|:--:|---|
| GET | `/uploads/**` | `WebConfig` de chat-service | **Non** | Affichage des pièces jointes (F18) |

Ouvert à la fois dans la liste blanche du gateway et dans `SecurityConfig` de
chat-service. Motif documenté : `<img src>` ne transmet pas d'en-tête `Authorization`.

### 11.8 rag-service (port 8085) — API interne

| Méthode | Route | Handler | Entrée | Sortie | Auth | Appelant |
|---|---|---|---|---|:--:|---|
| POST | `/documents/upload` | `upload_document` | multipart : `file`, `role_access` (défaut `COMMUN`) | `DocumentUploadResponse{document_id, filename, chunks_count, message}` | **Aucune** | document-service |
| DELETE | `/documents/{document_id}` | `delete_document` | `document_id` | `DeleteResponse` | **Aucune** | **personne** |
| POST | `/chat/` et `/chat` | `chat` | `ChatRequest{question, role?, session_id?}` | `ChatResponse{answer, sources[], session_id}` | **Aucune** | chat-service |
| GET | `/chat/health` | `chat_health` | — | `{status, router}` | **Aucune** | outillage |
| GET | `/health` | `health_check` | — | `HealthResponse{status, service, version}` | **Aucune** | `dev.sh` |
| GET | `/` | `root` | — | carte d'identité du service | **Aucune** | — |
| GET | `/docs` | FastAPI | — | Swagger UI | **Aucune** | — |

**Aucun point d'entrée de rag-service n'est authentifié.** Le `role` transmis est une
simple chaîne, acceptée telle quelle : la sécurité du cloisonnement documentaire repose
entièrement sur le fait que ce service n'est pas exposé et que l'appelant est de bonne
foi (voir §12.7).

Les deux formes `/chat/` et `/chat` sont enregistrées délibérément, la seconde masquée
du schéma : sans elle, un `POST /chat` renvoie une redirection 307 que la plupart des
clients HTTP ne suivent pas pour un POST, et l'appelant reçoit un corps vide.

### 11.9 Documentation des API

| Route | Services | Auth |
|---|---|:--:|
| `/swagger-ui/index.html` | 8081, 8082, 8083, 8084 | Non (`permitAll`) |
| `/v3/api-docs` | 8081, 8082, 8083, 8084 | Non (`permitAll`) |
| `/docs` | 8085 | Non |

### 11.10 Récapitulatif des protocoles

| Émetteur | Récepteur | Protocole | Client | Format |
|---|---|---|---|---|
| Navigateur | Gateway (via proxy 4200) | HTTP/1.1 | `HttpClient` Angular | JSON, multipart |
| Gateway | Services Spring | HTTP/1.1 | Spring Cloud Gateway (Netty) | transparent |
| document-service | rag-service | HTTP/1.1 | `RestTemplate` (bloquant) | multipart |
| chat-service | rag-service | HTTP/1.1 | `WebClient` (Reactor Netty, timeouts) | JSON |
| rag-service | OpenAI | HTTPS | SDK OpenAI via LangChain | JSON |
| Services Spring | PostgreSQL 5433 | protocole PostgreSQL | JDBC (HikariCP) | SQL |
| rag-service | PostgreSQL 5435 | protocole PostgreSQL | psycopg3 / SQLAlchemy | SQL + vecteurs |

---

## 12. Sécurité

### 12.1 Authentification

**Mécanisme : JWT auto-porté (stateless), signé en HMAC-SHA256 (HS256).**

Émission (source: `auth-service/security/JwtUtil.generateAccessToken`) :
- `sub` : e-mail de l'utilisateur ;
- revendication personnalisée `role` : nom du rôle ;
- `iat` : instant d'émission ; `exp` : `iat + jwt.expiration` ;
- signature HS256 avec une clé dérivée de `JWT_SECRET` décodé en Base64
  (`Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey))`).

Le secret est **partagé par les cinq services** via `JWT_SECRET` du `.env` racine, ce
qui permet à chacun de valider indépendamment sans appeler auth-service.

**Double validation, à deux niveaux :**
1. *Au gateway* (`gateway/config/JwtAuthenticationFilter`, `GlobalFilter`,
   `getOrder() = -1`, donc exécuté en premier) : liste blanche
   (`/api/auth/register`, `/api/auth/login`, `/uploads/`) ; sinon exigence d'un en-tête
   `Bearer`, analyse du jeton, vérification de l'expiration. Toute anomalie → **401 sans
   corps**. **Aucune vérification de rôle.**
2. *Dans chaque service* (`security/JwtAuthenticationFilter`, `OncePerRequestFilter`) :
   si un `Bearer` est présent, extraction du sujet et du rôle, validation, puis
   alimentation du `SecurityContext` avec un `UsernamePasswordAuthenticationToken`
   portant `ROLE_<rôle>`.

> **Faiblesse de conception du filtre applicatif.** En l'absence d'en-tête `Bearer`, le
> filtre laisse passer sans authentifier (`filterChain.doFilter` puis `return`) ; c'est
> ensuite `.anyRequest().authenticated()` qui rejette. La sécurité repose donc sur la
> configuration de la chaîne, non sur le filtre. Surtout, `jwtUtil.extractEmail(jwt)`
> est appelé **hors bloc `try`** : un jeton malformé lève une exception non capturée qui
> remonte au `GlobalExceptionHandler` et produit un **500** au lieu d'un 401. En
> exploitation derrière le gateway ce cas est filtré en amont, mais un appel direct au
> port du service l'expose.

**Validation logiquement redondante.** `JwtUtil.isTokenValid(token, email)` compare le
sujet extrait à un e-mail lui-même extrait du même jeton : la comparaison est toujours
vraie. Seul le contrôle d'expiration a un effet réel — la signature étant, elle,
vérifiée par `parseClaimsJws`.

### 12.2 Autorisation

**Modèle : RBAC (contrôle d'accès à base de rôles)**, à un seul rôle par utilisateur,
appliqué à trois endroits et de trois manières :

| Niveau | Technique | Emplacement |
|---|---|---|
| Déclaratif, par méthode | `@PreAuthorize("hasRole('ADMIN')")` avec `@EnableMethodSecurity` | `UserController`, `DocumentController` |
| Impératif, dans le service | `isAdmin(role)`, `checkAccess(ticket, email, role)` | `TicketService` |
| Propriété de la ressource | comparaison d'e-mail | `ChatService`, `TicketService` |

**Cloisonnement des données par rôle** (`VectorStoreManager.role_filter`) : appliqué au
niveau de la **récupération vectorielle**, donc sur le contenu exploité par le LLM.

Côté frontend, `adminGuard` et le rendu conditionnel de la barre latérale ne sont que du
**confort d'affichage** : contourner la route ne donne aucun accès, puisque les API
recontrôlent.

### 12.3 Gestion des jetons

| Aspect | Valeur | Source |
|---|---|---|
| Durée de vie du jeton d'accès | **900 000 ms = 15 minutes** | `JWT_EXPIRATION` |
| Durée de vie du jeton de rafraîchissement | 604 800 000 ms = 7 jours | `JWT_REFRESH_EXPIRATION` |
| Stockage côté client | `localStorage`, clés `alten_access_token` et `alten_user` | `TokenService` |
| Transport | En-tête `Authorization: Bearer <jwt>` | `authInterceptor` |
| Révocation | **Aucune** (pas de liste noire, pas d'état serveur) | — |

> **Le rafraîchissement n'est pas implémenté.** `JwtUtil.generateRefreshToken` existe
> dans auth-service mais **n'est appelé nulle part**, `AuthResponse` ne transporte aucun
> jeton de rafraîchissement, et aucun point d'entrée `/refresh` n'existe. Le commentaire
> de `authInterceptor` le confirme explicitement. Conséquence utilisateur : **toutes les
> 15 minutes, reconnexion obligatoire**, atténuée seulement par la redirection avec
> message « Votre session a expiré ».

**Stockage en `localStorage`** : accessible par tout script de la page, donc vulnérable à
l'exfiltration en cas de XSS — contrairement à un cookie `HttpOnly`. Le choix rend en
revanche l'application immune au CSRF, puisque le jeton n'est pas envoyé
automatiquement par le navigateur.

### 12.4 Chiffrement et hachage

| Donnée | Traitement | Où |
|---|---|---|
| Mots de passe | **BCrypt** (`BCryptPasswordEncoder`, force par défaut 10, sel aléatoire par mot de passe) | `SecurityConfig.passwordEncoder` |
| Jetons | Signés **HS256**, non chiffrés — la charge utile est lisible par quiconque | `JwtUtil` |
| Transport | **HTTP en clair** en développement (aucune configuration TLS dans le dépôt) | — |
| Documents sur disque | **Aucun chiffrement au repos** | `DocumentService`, `FileStorageService` |
| Secrets de configuration | En clair dans le `.env` (gitignoré) | `.env` |

> **Secret compromis, documenté dans le `README.md`.** Un `JWT_SECRET` était auparavant
> codé en dur dans les cinq fichiers `application.properties` ; il est **présent dans
> l'historique Git** et doit être considéré comme public. Le `README.md` interdit
> explicitement de le remettre. `./dev.sh setup` génère désormais un secret neuf.

### 12.5 Validation des entrées

**Côté backend — validation déclarative (Bean Validation) :**

| DTO | Contraintes |
|---|---|
| `RegisterRequest` | `@NotBlank` (fullName), `@Email` + `@NotBlank` (email), `@NotBlank` + `@Size(min=8)` (password), `@NotNull` (role) |
| `LoginRequest` | `@Email` + `@NotBlank`, `@NotBlank` |
| `UpdateUserRequest` | `@NotBlank`, `@NotNull` (role), `@NotNull` (enabled) |
| `DocumentUploadRequest` | `@NotBlank` (title), `@NotBlank` + `@Pattern` (roleAccess) |
| `DocumentUpdateRequest` | `@NotBlank`, `@Pattern`, `@NotNull` (status) |
| `ChatRequest` | `@NotBlank` (question) |
| `TicketRequest` | `@NotNull` (ticketType), `@NotBlank` (subject, requesterName) |
| `TicketStatusUpdateRequest` | `@NotNull` (status) |
| `TicketActivityRequest` | **aucune** — règle portée par le service (pièce jointe seule licite) |

**Validation impérative :** type MIME et taille des fichiers
(`DocumentService.validateFile`, `FileStorageService.store`), champs requis des
formulaires de tickets contre leur schéma (`TicketService.createTicket`), longueur et
non-vacuité de la question (`rag-service/routers/chat.py`), extension et taille du
document (`rag-service/routers/documents.py`).

**Angles morts identifiés :**
- `DocumentUploadRequest` est construit **manuellement** dans le contrôleur à partir de
  `@RequestPart` : l'objet n'est jamais annoté `@Valid`, donc **ni `@NotBlank` ni
  `@Pattern` ne s'appliquent au téléversement**. Seule la normalisation protège.
- `TicketRequest.formData` est un `Map<String, Object>` libre : hors champs requis du
  schéma, **aucune contrainte de type, de taille ou de nombre de clés**. Rien n'empêche
  d'y placer des clés hors schéma, qui seront stockées en `jsonb` et affichées à l'ADMIN
  avec leur clé brute.
- Le `role` reçu par rag-service n'est pas contrôlé contre une liste de valeurs admises.

**Côté frontend :** `Validators.required`, `Validators.email`, `Validators.minLength(8)`
sur les formulaires réactifs d'authentification ; pré-validation des fichiers ;
`isDynamicFormValid()` pour les formulaires de tickets. Toutes ces validations sont
**redoublées côté serveur**, sauf l'interdiction de se désactiver soi-même (F5), qui
n'existe **que** côté client.

### 12.6 Protections en place

| Protection | État | Détail |
|---|---|---|
| Hachage des mots de passe | ✅ | BCrypt, salé |
| Sessions sans état | ✅ | `SessionCreationPolicy.STATELESS` dans les 4 services |
| Injection SQL | ✅ | Requêtes dérivées Spring Data + JPQL paramétré ; **aucune concaténation SQL** |
| Traversée de chemin à l'upload | ✅ | Nom d'origine remplacé par un UUID ; `cleanPath` puis `normalize()` |
| Fuite de traces techniques | ✅ | Gestionnaires globaux renvoyant des messages neutres |
| Exposition du haché de mot de passe | ✅ | Absent des DTO ; absent de l'entité de user-service |
| Exposition du chemin disque | ✅ | `filePath` absent de `DocumentResponse` |
| CSRF | ✅ (non applicable) | `csrf().disable()` assumé : jeton porté par en-tête, non par cookie |
| CORS | ✅ | Gateway : origine `http://localhost:4200`, méthodes `GET,POST,PUT,PATCH,DELETE,OPTIONS`, `allow-credentials=true` ; chaque service a en plus son `CorsConfig`. En développement le proxy rend tout de même l'échange same-origin. |
| Compte désactivé | ✅ | `.disabled(!user.isEnabled())` → 403 |
| Timeouts sur appel externe | ✅ | `WebClient` : 5 s / 30 s / 5 s |

Note : la configuration CORS du gateway autorise `PATCH`, ajout signalé comme nécessaire
pour `PATCH /api/tickets/{id}/status` et auparavant absent.

### 12.7 Absences et faiblesses constatées

Présentées par gravité décroissante. Toutes sont vérifiées dans le code.

**1. Élévation de privilèges par l'inscription publique — critique.**
`POST /api/auth/register` est public et accepte un `role` arbitraire. N'importe qui crée
un compte `ADMIN` et obtient un jeton administrateur immédiat.
*Source :* `RegisterRequest.role`, `SecurityConfig` (`/api/auth/**` en `permitAll`).

**2. rag-service totalement non authentifié — critique si exposé.**
Aucun contrôle sur les six points d'entrée. Quiconque atteint le port 8085 peut
interroger le corpus **avec `role=ADMIN`**, contournant intégralement le cloisonnement
documentaire, injecter des documents arbitraires dans la base de connaissance, ou
supprimer des vecteurs. La protection est purement topologique (service non routé par le
gateway).
*Source :* `rag-service/app/main.py` (aucun middleware d'authentification), routeurs.

**3. Injection de messages dans la conversation d'autrui — élevée.**
`ChatService.resolveConversation` charge la conversation par identifiant **sans vérifier
le propriétaire**, alors que la lecture et la suppression le vérifient. Un utilisateur
énumérant les identifiants peut faire persister ses questions et les réponses associées
dans la conversation d'un tiers.
*Source :* `ChatService.resolveConversation` comparé à `getConversationMessages`.

**4. Pièces jointes de tickets accessibles sans authentification — élevée.**
`/uploads/**` est ouvert au gateway *et* dans chat-service. Les noms étant des UUID
(v4, non devinables en pratique), il s'agit d'une sécurité par obscurité : toute fuite
d'URL — journal, historique, référent, capture partagée — rend la pièce jointe
publiquement lisible, y compris à un utilisateur non connecté. Or ces pièces jointes
sont typiquement des captures d'écran d'incidents internes.
*Source :* `gateway` `PUBLIC_ENDPOINTS`, `chat-service/SecurityConfig`, `WebConfig`.

**5. Aucune limitation de débit — élevée.**
Aucun *rate limiting* nulle part : ni sur `/api/auth/login` (donc attaque par force
brute possible), ni sur `/api/chat/ask` — qui déclenche à chaque appel un embedding et
une génération LLM **facturés**. Un utilisateur authentifié peut provoquer une dépense
non bornée.
*Source :* absence de `RequestRateLimiter` dans la configuration du gateway, absence de
Bucket4j ou équivalent.

**6. Swagger UI public sur les quatre services — moyenne.**
`permitAll` sur `/swagger-ui/**` et `/v3/api-docs/**`, avec `try-it-out` activé :
cartographie complète de l'API offerte à qui atteint le port. Non exposé via le gateway.

**7. Aucun mécanisme de révocation ni de rafraîchissement — moyenne.**
La déconnexion est purement locale ; un jeton volé reste valide jusqu'à 15 minutes. La
désactivation d'un compte ne prend effet qu'à l'expiration du jeton en cours.

**8. Journalisation de données potentiellement sensibles — moyenne.**
`rag-service/routers/chat.py` journalise les 100 premiers caractères de chaque question
avec le rôle et l'identifiant de session ; `documents.py` journalise noms de fichiers et
tailles. Les questions posées à un assistant RH peuvent être personnelles. Par ailleurs
`spring.jpa.show-sql=true` **dans les quatre services** déverse toutes les requêtes SQL
dans les journaux — réglage de développement inadapté à la production.

**9. Absence de TLS — moyenne.**
Aucune configuration HTTPS dans le dépôt. Jetons et identifiants circulent en clair.
Acceptable en développement local, bloquant en production.
`[NON DÉTERMINÉ DANS LE CODE — à vérifier]` : présence d'un terminateur TLS en amont.

**10. Repli permissif de la normalisation d'habilitation — moyenne.**
`normalizeRoleAccess` fait retomber toute valeur inconnue, nulle ou vide sur `COMMUN`,
soit le niveau **le plus largement visible**. Un mécanisme de sécurité devrait échouer
vers le plus restrictif. À noter que `role_filter` fait, lui, le choix inverse et
correct (rôle inconnu → `COMMUN` seulement, donc restriction).

**11. Métadonnées documentaires visibles de tous — faible.**
`GET /api/documents` n'applique aucun filtre de `roleAccess` : titres, descriptions et
noms de fichiers de tous les documents sont lisibles par tout utilisateur authentifié.

**12. Contrôle d'auto-désactivation seulement côté client — faible.**
Un ADMIN peut se désactiver lui-même par appel direct à `DELETE /api/users/{id}`.

**13. Absence d'en-têtes de sécurité et de politique CSP — faible.**
Aucune configuration explicite de `Content-Security-Policy`, `X-Frame-Options`,
`Strict-Transport-Security` au-delà des valeurs par défaut de Spring Security.

**14. Numérotation des tickets non sûre en concurrence — faible.**
`countAllTickets() + 1` peut produire des collisions ; la contrainte d'unicité
transforme le problème en échec de création plutôt qu'en doublon.
