package com.example.library.dto;

import java.time.Instant;

public final class EnrichmentDtos {

    private EnrichmentDtos() {
    }

    public record EnrichmentJobResponse(
            Long id,
            Long bookId,
            String source,
            String status,
            String errorMessage,
            Instant completedAt,
            Instant createdAt
    ) {
    }
}
