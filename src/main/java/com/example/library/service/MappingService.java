package com.example.library.service;

import com.example.library.domain.Book;
import com.example.library.domain.LateFee;
import com.example.library.domain.Loan;
import com.example.library.domain.MetadataEnrichmentJob;
import com.example.library.domain.WaitlistEntry;
import com.example.library.dto.BookDtos.BookResponse;
import com.example.library.dto.EnrichmentDtos.EnrichmentJobResponse;
import com.example.library.dto.LateFeeDtos.LateFeeResponse;
import com.example.library.dto.LoanDtos.LoanResponse;
import com.example.library.dto.WaitlistDtos.WaitlistResponse;
import org.springframework.stereotype.Component;

@Component
public class MappingService {

    public BookResponse toBookResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthors(),
                book.getCategory(),
                book.getDescription(),
                book.getTotalCopies(),
                book.getAvailableCopies(),
                book.isActive(),
                book.getCreatedAt(),
                book.getUpdatedAt(),
                book.getCreatedBy(),
                book.getUpdatedBy());
    }

    public LoanResponse toLoanResponse(Loan loan, LateFee fee) {
        return new LoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getBorrower().getUsername(),
                loan.getBorrowedAt(),
                loan.getDueAt(),
                loan.getReturnedAt(),
                Boolean.TRUE.equals(loan.getReturnedLate()),
                loan.getStatus().name(),
                fee == null ? null : toLateFeeResponse(fee));
    }

    public LateFeeResponse toLateFeeResponse(LateFee fee) {
        return new LateFeeResponse(
                fee.getId(),
                fee.getLoan().getId(),
                fee.getAmount(),
                fee.getCurrency(),
                fee.getOverdueDays(),
                fee.getStatus().name(),
                fee.getCreatedAt());
    }

    public WaitlistResponse toWaitlistResponse(WaitlistEntry entry) {
        return new WaitlistResponse(
                entry.getId(),
                entry.getBook().getId(),
                entry.getBook().getTitle(),
                entry.getUser().getUsername(),
                entry.getStatus().name(),
                entry.getRequestedAt(),
                entry.getNotifiedAt(),
                entry.getReservationExpiresAt());
    }

    public EnrichmentJobResponse toEnrichmentResponse(MetadataEnrichmentJob job) {
        return new EnrichmentJobResponse(
                job.getId(),
                job.getBook().getId(),
                job.getSource(),
                job.getStatus().name(),
                job.getErrorMessage(),
                job.getCompletedAt(),
                job.getCreatedAt());
    }
}
