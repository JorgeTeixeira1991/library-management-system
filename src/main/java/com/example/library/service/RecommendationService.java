package com.example.library.service;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.dto.RecommendationDtos.RecommendationResponse;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final UserService userService;
    private final MappingService mappingService;

    public RecommendationService(
            BookRepository bookRepository,
            LoanRepository loanRepository,
            UserService userService,
            MappingService mappingService) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.userService = userService;
        this.mappingService = mappingService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("#username == authentication.name and hasAnyRole('CLIENT', 'OWNER')")
    public List<RecommendationResponse> recommend(String username) {
        Long userId = userService.requireByUsername(username).getId();
        List<Loan> history = loanRepository.findByBorrowerIdOrderByBorrowedAtDesc(userId);

        Map<String, Integer> categoryFrequency = new HashMap<>();
        Map<String, Integer> authorFrequency = new HashMap<>();
        Set<Long> borrowedBookIds = new HashSet<>();
        for (Loan loan : history) {
            Book book = loan.getBook();
            borrowedBookIds.add(book.getId());
            if (book.getCategory() != null) {
                categoryFrequency.merge(book.getCategory().toLowerCase(), 1, Integer::sum);
            }
            authorFrequency.merge(book.getAuthors().toLowerCase(), 1, Integer::sum);
        }

        Map<Long, Long> popularity = new HashMap<>();
        for (Object[] row : loanRepository.countLoansByBook()) {
            popularity.put((Long) row[0], (Long) row[1]);
        }

        List<ScoredBook> scored = new ArrayList<>();
        for (Book book : bookRepository.findByActiveTrueOrderByTitleAsc()) {
            if (borrowedBookIds.contains(book.getId())) {
                continue;
            }
            int categoryScore = book.getCategory() == null
                    ? 0
                    : categoryFrequency.getOrDefault(book.getCategory().toLowerCase(), 0) * 5;
            int authorScore = authorFrequency.getOrDefault(book.getAuthors().toLowerCase(), 0) * 7;
            int popularityScore = Math.toIntExact(Math.min(5L, popularity.getOrDefault(book.getId(), 0L)));
            int availabilityScore = book.getAvailableCopies() > 0 ? 2 : 0;
            int total = categoryScore + authorScore + popularityScore + availabilityScore;

            String reason;
            if (authorScore > 0) {
                reason = "Matches an author from your borrowing history";
            } else if (categoryScore > 0) {
                reason = "Matches a category from your borrowing history";
            } else if (popularityScore > 0) {
                reason = "Popular with library clients";
            } else {
                reason = "Available title you have not borrowed yet";
            }
            scored.add(new ScoredBook(book, total, reason));
        }

        return scored.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(5)
                .map(item -> new RecommendationResponse(
                        mappingService.toBookResponse(item.book()),
                        item.score(),
                        item.reason()))
                .toList();
    }

    private record ScoredBook(Book book, int score, String reason) {
    }
}
