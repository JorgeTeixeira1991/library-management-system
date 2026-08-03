# Requirements Traceability

This matrix maps the supplied research blueprint to the implementation in this project.

| Requirement | Implementation |
|---|---|
| Java 25 and Maven | `pom.xml`, multi-stage `Dockerfile` |
| Spring Boot REST API | `LibraryApplication` and controllers under `web/` |
| PostgreSQL | JDBC driver, `compose.yml`, PostgreSQL 17 service |
| Flyway migrations | `V1__core_schema.sql`, `V2__seed_books.sql`, `V3__search_and_optional_features.sql` |
| Spring Data JPA | Entities and repositories under `domain/` and `repository/` |
| Search and view books | `GET /api/v1/books`, `GET /api/v1/books/{id}` |
| Owner book management | Create, update, and soft-delete endpoints in `BookController` |
| Borrow and return | `LoanService` and `LoanController` |
| Personal and book history | `GET /api/v1/loans/me`, `GET /api/v1/books/{id}/history` |
| Late-return tracking | `Loan.returnedLate`, `FeeCalculator`, `LateFee` persistence |
| Transaction safety | `@Transactional` service methods |
| Concurrency safety | Pessimistic locks in `BookRepository` and `LoanRepository` |
| Audit timestamps and usernames | `AuditableEntity`, `JpaAuditConfig` |
| HTTP Basic authentication | `SecurityConfig` |
| Database-backed users and BCrypt | `AppUser`, `SampleDataInitializer`, `PasswordEncoder` |
| CLIENT and OWNER authorization | HTTP authorization rules and `@PreAuthorize` checks |
| Validation and structured errors | Jakarta Validation DTOs and `ApiExceptionHandler` |
| Waitlist | FIFO waitlist entities, service, endpoints, and reservation handling |
| Book-return event | `BookReturnedEvent` and after-commit listener |
| Late fees | Persisted `late_fee` records and client endpoint |
| PostgreSQL full-text and fuzzy search | Generated `tsvector`, GIN indexes, `pg_trgm`, native query |
| Recommendations | Deterministic history-based scoring in `RecommendationService` |
| Metadata enrichment | Offline provider by default; optional Google Books request |
| Chat endpoint | Persistent offline assistant at `POST /api/v1/ai/chat` |
| MCP surface | Optional `mcp` profile with tools, resource, and prompt |
| Integration tests | JUnit, MockMvc, Testcontainers PostgreSQL |
| Demo packaging | `Dockerfile`, `compose.yml`, README, Postman collection |
| Health endpoint | Spring Boot Actuator `/actuator/health` |
