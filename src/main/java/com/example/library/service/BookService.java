package com.example.library.service;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.dto.BookDtos.BookResponse;
import com.example.library.dto.BookDtos.CreateBookRequest;
import com.example.library.dto.BookDtos.UpdateBookRequest;
import com.example.library.dto.LoanDtos.LoanResponse;
import com.example.library.exception.BusinessRuleException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LateFeeRepository;
import com.example.library.repository.LoanRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final LateFeeRepository lateFeeRepository;
    private final MappingService mappingService;

    public BookService(
            BookRepository bookRepository,
            LoanRepository loanRepository,
            LateFeeRepository lateFeeRepository,
            MappingService mappingService) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.lateFeeRepository = lateFeeRepository;
        this.mappingService = mappingService;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> search(String query) {
        List<Book> books = query == null || query.isBlank()
                ? bookRepository.findByActiveTrueOrderByTitleAsc()
                : bookRepository.search(query.trim());
        return books.stream().map(mappingService::toBookResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        Book book = bookRepository.findById(id)
                .filter(Book::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        return mappingService.toBookResponse(book);
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public BookResponse create(CreateBookRequest request) {
        Book book = new Book();
        book.setIsbn(blankToNull(request.isbn()));
        book.setTitle(request.title().trim());
        book.setAuthors(request.authors().trim());
        book.setCategory(blankToNull(request.category()));
        book.setDescription(blankToNull(request.description()));
        book.setTotalCopies(request.totalCopies());
        book.setAvailableCopies(request.totalCopies());
        book.setActive(true);
        return mappingService.toBookResponse(bookRepository.save(book));
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public BookResponse update(Long id, UpdateBookRequest request) {
        Book book = requireLocked(id);
        int borrowedCopies = book.getTotalCopies() - book.getAvailableCopies();
        if (request.totalCopies() < borrowedCopies) {
            throw new BusinessRuleException(
                    "totalCopies cannot be lower than the number of currently borrowed copies: " + borrowedCopies);
        }

        book.setIsbn(blankToNull(request.isbn()));
        book.setTitle(request.title().trim());
        book.setAuthors(request.authors().trim());
        book.setCategory(blankToNull(request.category()));
        book.setDescription(blankToNull(request.description()));
        book.setTotalCopies(request.totalCopies());
        book.setAvailableCopies(request.totalCopies() - borrowedCopies);
        book.setActive(request.active());
        return mappingService.toBookResponse(book);
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void softDelete(Long id) {
        Book book = requireLocked(id);
        book.setActive(false);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OWNER')")
    public List<LoanResponse> history(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException("Book not found: " + bookId);
        }
        return loanRepository.findByBookIdOrderByBorrowedAtDesc(bookId).stream()
                .map(loan -> mappingService.toLoanResponse(
                        loan,
                        lateFeeRepository.findByLoanId(loan.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Book requireEntity(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
    }

    @Transactional
    public Book requireLocked(Long id) {
        return bookRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
