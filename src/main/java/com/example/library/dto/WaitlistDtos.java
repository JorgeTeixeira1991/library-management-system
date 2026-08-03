package com.example.library.dto;

import java.time.Instant;

public final class WaitlistDtos {

    private WaitlistDtos() {
    }

    public record WaitlistResponse(
            Long id,
            Long bookId,
            String bookTitle,
            String username,
            String status,
            Instant requestedAt,
            Instant notifiedAt,
            Instant reservationExpiresAt
    ) {
    }
}
