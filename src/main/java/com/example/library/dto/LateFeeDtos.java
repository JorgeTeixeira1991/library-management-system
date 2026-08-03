package com.example.library.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class LateFeeDtos {

    private LateFeeDtos() {
    }

    public record LateFeeResponse(
            Long id,
            Long loanId,
            BigDecimal amount,
            String currency,
            int overdueDays,
            String status,
            Instant createdAt
    ) {
    }
}
