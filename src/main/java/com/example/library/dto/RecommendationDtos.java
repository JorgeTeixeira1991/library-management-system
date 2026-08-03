package com.example.library.dto;

import com.example.library.dto.BookDtos.BookResponse;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record RecommendationResponse(
            BookResponse book,
            int score,
            String reason
    ) {
    }
}
