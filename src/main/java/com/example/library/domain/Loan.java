package com.example.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "loan")
public class Loan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private AppUser borrower;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "returned_late")
    private Boolean returnedLate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.OPEN;

    public boolean isOpen() {
        return status == LoanStatus.OPEN;
    }

    public void markReturned(Instant timestamp) {
        if (!isOpen()) {
            throw new IllegalStateException("Loan is already returned");
        }
        returnedAt = timestamp;
        returnedLate = timestamp.isAfter(dueAt);
        status = LoanStatus.RETURNED;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public AppUser getBorrower() {
        return borrower;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public Boolean getReturnedLate() {
        return returnedLate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public void setBorrower(AppUser borrower) {
        this.borrower = borrower;
    }

    public void setBorrowedAt(Instant borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }
}
