package com.example.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.library.service.FeeCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    void roundsAnyPartialOverdueDayUp() {
        Instant dueAt = Instant.parse("2026-07-01T10:00:00Z");
        Instant returnedAt = Instant.parse("2026-07-02T10:00:01Z");

        int days = calculator.overdueDays(dueAt, returnedAt);

        assertThat(days).isEqualTo(2);
        assertThat(calculator.amount(days, new BigDecimal("0.50")))
                .isEqualByComparingTo("1.00");
    }
}
