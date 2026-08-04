# Library Management System

A Spring Boot library API.

## Implemented requirements

### Mandatory

- Java 25 and Maven
- Spring Boot 4.0.5
- PostgreSQL persistence
- Spring Data JPA entities and repositories
- Flyway-controlled schema creation and evolution
- HTTP Basic authentication with BCrypt database users
- `CLIENT` and `OWNER` role separation
- Search and view books
- Owner create, update, and soft-delete operations
- Client borrow and return operations
- Personal loan history and owner book history
- Late-return tracking
- Transactional borrow/return flows
- Pessimistic locking to prevent over-borrowing
- Audit timestamps and usernames
- RFC-style `ProblemDetail` validation and error responses

### Optional

- Testcontainers + JUnit integration tests
- FIFO waitlist with notification reservations
- Persisted late fees
- Transaction-bound `BookReturnedEvent`
- PostgreSQL full-text and trigram search
- Rule-based recommendations
- Metadata enrichment with an offline demo provider and optional Google Books access
- Persistent offline chat assistant endpoint
- Optional Spring AI MCP server profile exposing tools, a resource, and a prompt
- Docker Compose demo packaging
- Postman collection

## Architecture

```text
Docker Compose
├── library-api (Spring Boot)
└── postgres (PostgreSQL 17 + persistent volume)
```

Flyway creates the schema and seed books when the API first connects. Hibernate uses `ddl-auto: validate`, so it validates but never invents schema changes.

## Run the demo

Only Docker with Docker Compose is required:

```bash
docker compose up --build
```

The API is then available at:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Demo users

| Role | Username | Password |
|---|---|---|
| Owner | `owner` | `owner123` |
| Client | `client` | `client123` |
| Client | `client2` | `client2123` |

These credentials are only demo data.

## Core API

| Method | Endpoint | Role | Purpose |
|---|---|---|---|
| GET | `/api/v1/books?query=` | CLIENT, OWNER | List/search books |
| GET | `/api/v1/books/{id}` | CLIENT, OWNER | Get one active book |
| POST | `/api/v1/books` | OWNER | Create book |
| PUT | `/api/v1/books/{id}` | OWNER | Update book and inventory |
| DELETE | `/api/v1/books/{id}` | OWNER | Soft-delete book |
| POST | `/api/v1/books/{id}/borrow` | CLIENT | Borrow an available copy |
| POST | `/api/v1/loans/{id}/return` | CLIENT | Return own open loan |
| GET | `/api/v1/loans/me` | CLIENT | View own loans/history |
| GET | `/api/v1/books/{id}/history` | OWNER | View complete book history |
| POST | `/api/v1/books/{id}/waitlist` | CLIENT | Join unavailable-book queue |
| DELETE | `/api/v1/waitlist/{id}` | CLIENT | Cancel own queue entry |
| GET | `/api/v1/waitlist/me` | CLIENT | View own queue entries |
| GET | `/api/v1/late-fees/me` | CLIENT | View own fees |
| GET | `/api/v1/recommendations/me` | CLIENT | Rule-based recommendations |
| POST | `/api/v1/books/{id}/enrich` | OWNER | Run metadata enrichment |
| GET | `/api/v1/books/{id}/enrichment-jobs` | OWNER | View enrichment history |
| POST | `/api/v1/ai/chat` | CLIENT, OWNER | Offline persistent assistant |

## Demo commands

List books:

```bash
curl --user client:client123 \
  "http://localhost:8080/api/v1/books?query=java"
```

Create a book:

```bash
curl --request POST \
  --user owner:owner123 \
  --header "Content-Type: application/json" \
  --data '{
    "isbn": "9780201633610",
    "title": "Design Patterns",
    "authors": "Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides",
    "category": "Architecture",
    "description": "Elements of reusable object-oriented software.",
    "totalCopies": 2
  }' \
  http://localhost:8080/api/v1/books
```

Borrow book 1:

```bash
curl --request POST \
  --user client:client123 \
  http://localhost:8080/api/v1/books/1/borrow
```

Return loan 1:

```bash
curl --request POST \
  --user client:client123 \
  http://localhost:8080/api/v1/loans/1/return
```

Chat/search assistant:

```bash
curl --request POST \
  --user client:client123 \
  --header "Content-Type: application/json" \
  --data '{
    "conversationId": "demo-chat-1",
    "message": "Find architecture books"
  }' \
  http://localhost:8080/api/v1/ai/chat
```

## Metadata enrichment

The default is an offline deterministic provider so the demo works without internet access:

```bash
curl --request POST \
  --user owner:owner123 \
  http://localhost:8080/api/v1/books/1/enrich
```

To call the real Google Books public API instead:

```bash
APP_METADATA_GOOGLE_BOOKS_ENABLED=true docker compose up --build
```

## MCP profile

The MCP server is intentionally disabled in the normal demo. Enable the `mcp` profile:

```bash
SPRING_PROFILES_ACTIVE=mcp docker compose up --build
```

The profile configures Spring AI MCP with Streamable HTTP and exposes:

- tools: `searchBooks`, `borrowBook`, `returnBook`, `joinWaitlist`
- resource: `library://books/{id}`
- prompt: `recommend-book`

The HTTP MCP endpoint is protected by HTTP Basic authentication.

## Database and Flyway

Migration files:

```text
src/main/resources/db/migration/
├── V1__core_schema.sql
├── V2__seed_books.sql
└── V3__search_and_optional_features.sql
```

Normal schema-change workflow:

1. Change or add the JPA entity.
2. Add a new migration such as `V4__add_publisher.sql`.
3. Start the application.
4. Flyway applies the new migration.
5. Hibernate validates that the entities match the resulting schema.

Never edit an already-applied migration in a shared environment.

## Reset the demo database

Stop without deleting data:

```bash
docker compose down
```

Delete all demo data and rerun Flyway from an empty database:

```bash
docker compose down --volumes
docker compose up --build
```

## Run tests

Tests require Java 25, Maven, and Docker because the integration suite starts a real PostgreSQL 17 container:

```bash
mvn clean test
```

The suite verifies security, Flyway migrations, create-book authorization, borrow/return, waitlist promotion, and PostgreSQL fuzzy search.

## Notes

- PostgreSQL is a separate container because application and database lifecycles should remain independent.
- The database volume persists between restarts.
- Borrow and return use pessimistic locks on the book row.
- A user cannot hold two open loans for the same title.
- A returned copy is reserved for the first waiting user for a configurable period.
- Any partial overdue day is rounded up for fee calculation.
# library-management-system
