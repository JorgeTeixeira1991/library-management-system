INSERT INTO book (
    isbn, title, authors, category, description,
    total_copies, available_copies, created_by, updated_by
)
VALUES
('9780134685991', 'Effective Java', 'Joshua Bloch', 'Programming',
 'Best practices for Java programming.', 3, 3, 'flyway', 'flyway'),
('9781617294945', 'Spring in Action', 'Craig Walls', 'Programming',
 'Practical Spring development guide.', 2, 2, 'flyway', 'flyway'),
('9781491950357', 'Designing Data-Intensive Applications', 'Martin Kleppmann', 'Architecture',
 'Systems and data design patterns.', 1, 1, 'flyway', 'flyway'),
('9780321125217', 'Domain-Driven Design', 'Eric Evans', 'Architecture',
 'Tackling complexity in the heart of software.', 2, 2, 'flyway', 'flyway'),
('9780132350884', 'Clean Code', 'Robert C. Martin', 'Programming',
 'A handbook of agile software craftsmanship.', 2, 2, 'flyway', 'flyway');
