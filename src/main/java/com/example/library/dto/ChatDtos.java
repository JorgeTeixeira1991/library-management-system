package com.example.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 100) String conversationId,
            @NotBlank @Size(max = 4000) String message
    ) {
    }

    public record ChatResponse(
            String conversationId,
            String answer
    ) {
    }
}
