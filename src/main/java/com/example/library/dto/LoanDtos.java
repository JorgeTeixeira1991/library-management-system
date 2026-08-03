package com.example.library.dto;

import com.example.library.dto.LateFeeDtos.LateFeeResponse;
import java.time.Instant;

public final class LoanDtos {

    private LoanDtos() {
    }

    public record LoanResponse(
            Long id,
            Long bookId,
            String bookTitle,
            String borrowerUsername,
            Instant borrowedAt,
            Instant dueAt,
            Instant returnedAt,
            boolean returnedLate,
            String status,
            LateFeeResponse lateFee
    ) {
    }
}
