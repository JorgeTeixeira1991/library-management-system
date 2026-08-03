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
@Table(name = "waitlist_entry")
public class WaitlistEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    public void notifyUser(Instant notifiedAt, Instant expiresAt) {
        status = WaitlistStatus.NOTIFIED;
        this.notifiedAt = notifiedAt;
        reservationExpiresAt = expiresAt;
    }

    public void fulfill() {
        status = WaitlistStatus.FULFILLED;
    }

    public void cancel() {
        status = WaitlistStatus.CANCELLED;
    }

    public boolean isExpired(Instant now) {
        return status == WaitlistStatus.NOTIFIED
                && reservationExpiresAt != null
                && !reservationExpiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public AppUser getUser() {
        return user;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }
}
