package com.example.library.service;

import com.example.library.domain.AppUser;
import com.example.library.domain.Book;
import com.example.library.domain.LateFee;
import com.example.library.domain.LateFeeStatus;
import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.domain.WaitlistEntry;
import com.example.library.domain.WaitlistStatus;
import com.example.library.dto.LoanDtos.LoanResponse;
import com.example.library.event.BookReturnedEvent;
import com.example.library.exception.BusinessRuleException;
import com.example.library.exception.ConflictException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LateFeeRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.WaitlistRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private static final List<WaitlistStatus> ACTIVE_WAITLIST_STATUSES =
            List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED);

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final LateFeeRepository lateFeeRepository;
    private final WaitlistRepository waitlistRepository;
    private final UserService userService;
    private final MappingService mappingService;
    private final FeeCalculator feeCalculator;
    private final ApplicationEventPublisher eventPublisher;
    private final long defaultLoanDays;
    private final BigDecimal lateFeePerDay;
    private final String currency;

    public LoanService(
            BookRepository bookRepository,
            LoanRepository loanRepository,
            LateFeeRepository lateFeeRepository,
            WaitlistRepository waitlistRepository,
            UserService userService,
            MappingService mappingService,
            FeeCalculator feeCalculator,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.borrowing.default-loan-days:14}") long defaultLoanDays,
            @Value("${app.fees.late-fee-per-day:0.50}") BigDecimal lateFeePerDay,
            @Value("${app.fees.currency:EUR}") String currency) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.lateFeeRepository = lateFeeRepository;
        this.waitlistRepository = waitlistRepository;
        this.userService = userService;
        this.mappingService = mappingService;
        this.feeCalculator = feeCalculator;
        this.eventPublisher = eventPublisher;
        this.defaultLoanDays = defaultLoanDays;
        this.lateFeePerDay = lateFeePerDay;
        this.currency = currency;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public LoanResponse borrow(Long bookId, String username) {
        AppUser borrower = userService.requireByUsername(username);
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));

        if (!book.isActive()) {
            throw new BusinessRuleException("Book is inactive");
        }
        if (loanRepository.existsByBookIdAndBorrowerIdAndStatus(bookId, borrower.getId(), LoanStatus.OPEN)) {
            throw new ConflictException("User already has an open loan for this book");
        }

        Instant now = Instant.now();
        expireReservations(bookId, now);
        WaitlistEntry notified = waitlistRepository
                .findFirstByBookIdAndStatusOrderByRequestedAtAsc(bookId, WaitlistStatus.NOTIFIED)
                .orElse(null);
        if (notified != null && !notified.getUser().getId().equals(borrower.getId())) {
            throw new ConflictException("This available copy is reserved for the next waitlisted user");
        }
        if (book.getAvailableCopies() <= 0) {
            throw new ConflictException("Book has no available copies; join the waitlist");
        }

        waitlistRepository.findByBookIdAndUserIdAndStatusIn(
                        bookId, borrower.getId(), ACTIVE_WAITLIST_STATUSES)
                .ifPresent(WaitlistEntry::fulfill);

        book.decrementAvailability();
        Loan loan = new Loan();
        loan.setBook(book);
        loan.setBorrower(borrower);
        loan.setBorrowedAt(now);
        loan.setDueAt(now.plus(defaultLoanDays, ChronoUnit.DAYS));
        Loan saved = loanRepository.save(loan);
        return mappingService.toLoanResponse(saved, null);
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public LoanResponse returnBook(Long loanId, String username) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
        if (!loan.getBorrower().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Loan not found: " + loanId);
        }
        if (!loan.isOpen()) {
            throw new ConflictException("Loan has already been returned");
        }

        Book book = bookRepository.findByIdForUpdate(loan.getBook().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + loan.getBook().getId()));
        Instant returnedAt = Instant.now();
        loan.markReturned(returnedAt);
        book.incrementAvailability();

        LateFee lateFee = null;
        int overdueDays = feeCalculator.overdueDays(loan.getDueAt(), returnedAt);
        if (overdueDays > 0) {
            lateFee = new LateFee();
            lateFee.setLoan(loan);
            lateFee.setOverdueDays(overdueDays);
            lateFee.setAmount(feeCalculator.amount(overdueDays, lateFeePerDay));
            lateFee.setCurrency(currency);
            lateFee.setStatus(LateFeeStatus.OPEN);
            lateFeeRepository.save(lateFee);
        }

        Loan saved = loanRepository.save(loan);
        eventPublisher.publishEvent(new BookReturnedEvent(
                saved.getId(),
                book.getId(),
                saved.getBorrower().getId(),
                returnedAt,
                Boolean.TRUE.equals(saved.getReturnedLate())));
        return mappingService.toLoanResponse(saved, lateFee);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public List<LoanResponse> myLoans(String username) {
        return loanRepository.findByBorrowerUsernameOrderByBorrowedAtDesc(username).stream()
                .map(loan -> mappingService.toLoanResponse(
                        loan,
                        lateFeeRepository.findByLoanId(loan.getId()).orElse(null)))
                .toList();
    }

    private void expireReservations(Long bookId, Instant now) {
        waitlistRepository.findByBookIdAndStatusOrderByRequestedAtAsc(bookId, WaitlistStatus.NOTIFIED)
                .stream()
                .filter(entry -> entry.isExpired(now))
                .forEach(WaitlistEntry::cancel);
    }
}
