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
@Table(name = "metadata_enrichment_job")
public class MetadataEnrichmentJob extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 50)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrichmentStatus status = EnrichmentStatus.PENDING;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getSource() {
        return source;
    }

    public EnrichmentStatus getStatus() {
        return status;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void succeed(String rawPayload, Instant completedAt) {
        this.status = EnrichmentStatus.SUCCESS;
        this.rawPayload = rawPayload;
        this.errorMessage = null;
        this.completedAt = completedAt;
    }

    public void fail(String errorMessage, String rawPayload, Instant completedAt) {
        this.status = EnrichmentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.rawPayload = rawPayload;
        this.completedAt = completedAt;
    }
}
