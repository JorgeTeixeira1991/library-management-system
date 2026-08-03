CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CLIENT', 'OWNER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE TABLE book (
    id BIGSERIAL PRIMARY KEY,
    isbn VARCHAR(32),
    title VARCHAR(255) NOT NULL,
    authors VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    description TEXT,
    total_copies INT NOT NULL CHECK (total_copies >= 0),
    available_copies INT NOT NULL CHECK (available_copies >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT ck_book_available_le_total CHECK (available_copies <= total_copies)
);

CREATE TABLE loan (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES book(id),
    borrower_id BIGINT NOT NULL REFERENCES app_user(id),
    borrowed_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    returned_at TIMESTAMPTZ,
    returned_late BOOLEAN,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'RETURNED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_loan_book_id ON loan(book_id);
CREATE INDEX idx_loan_borrower_id ON loan(borrower_id);
CREATE INDEX idx_loan_status ON loan(status);
CREATE UNIQUE INDEX uk_open_loan_book_borrower
    ON loan(book_id, borrower_id)
    WHERE status = 'OPEN';

CREATE TABLE waitlist_entry (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES book(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    requested_at TIMESTAMPTZ NOT NULL,
    notified_at TIMESTAMPTZ,
    reservation_expires_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('WAITING', 'NOTIFIED', 'FULFILLED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_waitlist_book_status_requested
    ON waitlist_entry(book_id, status, requested_at);
CREATE UNIQUE INDEX uk_active_waitlist_book_user
    ON waitlist_entry(book_id, user_id)
    WHERE status IN ('WAITING', 'NOTIFIED');

CREATE TABLE late_fee (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL UNIQUE REFERENCES loan(id),
    amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL,
    overdue_days INT NOT NULL CHECK (overdue_days >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'WAIVED', 'PAID')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);
