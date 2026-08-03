package com.example.library.event;

import java.time.Instant;

public record BookReturnedEvent(
        Long loanId,
        Long bookId,
        Long borrowerId,
        Instant returnedAt,
        boolean returnedLate) {
}
