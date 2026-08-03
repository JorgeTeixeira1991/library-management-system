package com.example.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BookDtos {

    private BookDtos() {
    }

    public record CreateBookRequest(
            @Size(max = 32) String isbn,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 255) String authors,
            @Size(max = 100) String category,
            String description,
            @Min(1) int totalCopies
    ) {
    }

    public record UpdateBookRequest(
            @Size(max = 32) String isbn,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 255) String authors,
            @Size(max = 100) String category,
            String description,
            @Min(1) int totalCopies,
            boolean active
    ) {
    }

    public record BookResponse(
            Long id,
            String isbn,
            String title,
            String authors,
            String category,
            String description,
            int totalCopies,
            int availableCopies,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy
    ) {
    }
}
