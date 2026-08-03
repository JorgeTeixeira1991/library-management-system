package com.example.library.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class FeeCalculator {

    public int overdueDays(Instant dueAt, Instant returnedAt) {
        if (!returnedAt.isAfter(dueAt)) {
            return 0;
        }
        long seconds = Duration.between(dueAt, returnedAt).getSeconds();
        return Math.toIntExact((seconds + 86_399L) / 86_400L);
    }

    public BigDecimal amount(int overdueDays, BigDecimal dailyRate) {
        return dailyRate.multiply(BigDecimal.valueOf(overdueDays)).setScale(2, RoundingMode.HALF_UP);
    }
}
