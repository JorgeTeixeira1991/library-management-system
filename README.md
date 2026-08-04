# Library Management System

A Spring Boot REST API for managing a library catalogue, loans, waitlists, late fees, and recommendations.

## Technology

- Java 25 and Spring Boot 4
- PostgreSQL 17
- Spring Data JPA and Hibernate
- Flyway database migrations
- Spring Security with HTTP Basic authentication
- Docker Compose
- JUnit and Testcontainers
- Optional HashiCorp Vault database authentication
- Optional Spring AI MCP server

## How the application works

Every API request follows the same path:

`HTTP request → Spring Security → Controller → Service → Repository/JPA → PostgreSQL`

1. **Spring Security** authenticates the user from the database and checks the user's role.
2. **Controllers** receive HTTP requests, validate request bodies, and return JSON responses.
3. **Services** contain the business rules and transaction boundaries.
4. **Repositories** read and write entities through Spring Data JPA.
5. **PostgreSQL** stores users, books, loans, waitlists, late fees, enrichment jobs, and chat history.
6. **ApiExceptionHandler** converts validation and business errors into consistent `ProblemDetail` responses.

## What happens during startup

### Default profile

1. Spring reads `application.yml`.
2. The application connects to PostgreSQL.
3. Flyway applies pending migrations from `src/main/resources/db/migration`.
4. Hibernate validates that the JPA entities match the migrated schema. It does not create or alter tables.
5. Missing demo users are created with BCrypt password hashes.
6. The API starts on port `8080`.

### Remote database profile

The `remote` profile connects to the Raspberry Pi database with temporary credentials from HashiCorp Vault:

1. A Spring `EnvironmentPostProcessor` runs before the datasource is created.
2. The application asks Vault for an OIDC login URL.
3. A browser opens for passwordless email authentication.
4. A temporary HTTP listener on port `8250` receives the OIDC callback.
5. The authorization code is exchanged for a Vault token.
6. The token is exchanged for short-lived PostgreSQL credentials.
7. Those credentials are injected into Spring's datasource configuration.
8. Flyway is disabled for this connection so the remote schema is not migrated automatically.

No permanent database password is stored in the application.

## Main business flows

### Borrowing a book

The authenticated user must have the `CLIENT` role.

1. The book row is locked with a pessimistic write lock.
2. The service verifies that the book is active.
3. It rejects a second open loan for the same user and book.
4. Expired waitlist reservations are cancelled.
5. If a copy is reserved, only the notified client may borrow it.
6. The available-copy count is reduced.
7. An open loan is created with a due date 14 days in the future.

The lock and database constraints prevent concurrent requests from lending more copies than are available.

### Returning a book

1. The loan and book rows are locked.
2. The service verifies that the loan belongs to the authenticated client and is still open.
3. The loan is marked as returned and the available-copy count is increased.
4. If the return is late, a fee is stored. Every partial overdue day counts as a full day.
5. A `BookReturnedEvent` is published.
6. After the transaction commits, the event listener promotes the first waiting client.
7. The promoted client receives a 24-hour reservation.

### Searching and recommendations

Book search uses PostgreSQL full-text search and trigram similarity, so it can match titles, authors, categories, descriptions, and small spelling mistakes.

Recommendations are deterministic. Books are scored using the client's borrowing history, preferred authors and categories, overall popularity, and current availability.

### Metadata enrichment and chat

Owners can enrich a book by ISBN. The default provider is an offline demo provider; Google Books can be enabled with an environment variable.

The chat endpoint is also offline. It recognizes search, recommendation, borrow, and return requests and stores the complete conversation in PostgreSQL. It does not call an AI model.

## Package structure

| Package | Responsibility |
|---|---|
| `bootstrap` | Creates demo users and prepares remote Vault database access |
| `config` | Security, JPA auditing, and HTTP client configuration |
| `domain` | JPA entities and enums |
| `dto` | API request and response records |
| `web` | REST controllers and exception handling |
| `service` | Business rules and transactions |
| `repository` | Spring Data JPA queries and database locks |
| `event` | Return events and waitlist promotion |
| `mcp` | Optional MCP tools, resource, and prompt |

## API

Health endpoints are public. All `/api/v1/**` endpoints use HTTP Basic authentication.

| Method | Endpoint | Role | Purpose |
|---|---|---|---|
| GET | `/api/v1/books?query=` | CLIENT, OWNER | List or search active books |
| GET | `/api/v1/books/{id}` | CLIENT, OWNER | Get a book |
| POST | `/api/v1/books` | OWNER | Create a book |
| PUT | `/api/v1/books/{id}` | OWNER | Update a book and its inventory |
| DELETE | `/api/v1/books/{id}` | OWNER | Soft-delete a book |
| GET | `/api/v1/books/{id}/history` | OWNER | View the book's loan history |
| POST | `/api/v1/books/{id}/borrow` | CLIENT | Borrow a copy |
| POST | `/api/v1/loans/{id}/return` | CLIENT | Return the client's loan |
| GET | `/api/v1/loans/me` | CLIENT | View the client's loan history |
| POST | `/api/v1/books/{id}/waitlist` | CLIENT | Join a book's waitlist |
| DELETE | `/api/v1/waitlist/{id}` | CLIENT | Cancel a waitlist entry |
| GET | `/api/v1/waitlist/me` | CLIENT | View the client's waitlist entries |
| GET | `/api/v1/late-fees/me` | CLIENT | View the client's late fees |
| GET | `/api/v1/recommendations/me` | CLIENT | Get book recommendations |
| POST | `/api/v1/books/{id}/enrich` | OWNER | Enrich book metadata |
| GET | `/api/v1/books/{id}/enrichment-jobs` | OWNER | View enrichment attempts |
| POST | `/api/v1/ai/chat` | CLIENT, OWNER | Use the offline library assistant |

## Run locally with Docker

Docker Compose starts both PostgreSQL and the API:

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`.

Check it with:

```bash
curl http://localhost:8080/actuator/health
```

The PostgreSQL volume is retained between restarts. To delete all local data and rebuild the database:

```bash
docker compose down --volumes
docker compose up --build
```

## Demo users

| Role | Username | Password |
|---|---|---|
| Owner | `owner` | `owner123` |
| Client | `client` | `client123` |
| Client | `client2` | `client2123` |

These accounts are demo data and must not be used in production.

Example request:

```bash
curl --user client:client123 "http://localhost:8080/api/v1/books?query=java"
```

A ready-to-use collection is available at `postman/Library-System.postman_collection.json`.

## Optional profiles

Connect through Vault to the remote PostgreSQL database:

```bash
SPRING_PROFILES_ACTIVE=remote mvn spring-boot:run
```

Enable the MCP server:

```bash
SPRING_PROFILES_ACTIVE=mcp docker compose up --build
```

The MCP profile exposes the `searchBooks`, `borrowBook`, `returnBook`, and `joinWaitlist` tools, the `library://books/{id}` resource, and the `recommend-book` prompt.

Enable the real Google Books metadata provider:

```bash
APP_METADATA_GOOGLE_BOOKS_ENABLED=true docker compose up --build
```

## Tests

Java 25, Maven, and Docker are required. Run the normal suite without the Raspberry Pi tests:

```bash
mvn clean test -Dgroups='!raspberry-pi'
```

Run only the remote Raspberry Pi integration tests:

```bash
mvn test -Dgroups=raspberry-pi
```

The remote tests activate the `remote` profile and therefore require Vault OIDC authentication.
