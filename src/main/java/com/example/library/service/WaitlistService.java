package com.example.library.service;

import com.example.library.domain.AppUser;
import com.example.library.domain.Book;
import com.example.library.domain.WaitlistEntry;
import com.example.library.domain.WaitlistStatus;
import com.example.library.dto.WaitlistDtos.WaitlistResponse;
import com.example.library.exception.ConflictException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.repository.BookRepository;
import com.example.library.repository.WaitlistRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitlistService {

    private static final List<WaitlistStatus> ACTIVE_STATUSES =
            List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED);

    private final WaitlistRepository waitlistRepository;
    private final BookRepository bookRepository;
    private final UserService userService;
    private final MappingService mappingService;
    private final long reservationHours;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            BookRepository bookRepository,
            UserService userService,
            MappingService mappingService,
            @Value("${app.waitlist.reservation-hours:24}") long reservationHours) {
        this.waitlistRepository = waitlistRepository;
        this.bookRepository = bookRepository;
        this.userService = userService;
        this.mappingService = mappingService;
        this.reservationHours = reservationHours;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public WaitlistResponse join(Long bookId, String username) {
        AppUser user = userService.requireByUsername(username);
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
        if (!book.isActive()) {
            throw new ConflictException("Book is inactive");
        }
        if (book.getAvailableCopies() > 0) {
            throw new ConflictException("Book is available and can be borrowed directly");
        }
        waitlistRepository.findByBookIdAndUserIdAndStatusIn(bookId, user.getId(), ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new ConflictException("User is already on the active waitlist for this book");
                });

        WaitlistEntry entry = new WaitlistEntry();
        entry.setBook(book);
        entry.setUser(user);
        entry.setRequestedAt(Instant.now());
        return mappingService.toWaitlistResponse(waitlistRepository.save(entry));
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public void cancel(Long entryId, String username) {
        WaitlistEntry entry = waitlistRepository.findByIdAndUserUsername(entryId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry not found: " + entryId));
        if (!ACTIVE_STATUSES.contains(entry.getStatus())) {
            throw new ConflictException("Only active waitlist entries can be cancelled");
        }
        entry.cancel();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public List<WaitlistResponse> myEntries(String username) {
        return waitlistRepository.findByUserUsernameOrderByRequestedAtDesc(username).stream()
                .map(mappingService::toWaitlistResponse)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void promoteNext(Long bookId) {
        Instant now = Instant.now();
        waitlistRepository.findByBookIdAndStatusOrderByRequestedAtAsc(bookId, WaitlistStatus.NOTIFIED)
                .stream()
                .filter(entry -> entry.isExpired(now))
                .forEach(WaitlistEntry::cancel);

        boolean activeReservationExists = waitlistRepository
                .findFirstByBookIdAndStatusOrderByRequestedAtAsc(bookId, WaitlistStatus.NOTIFIED)
                .isPresent();
        if (activeReservationExists) {
            return;
        }

        List<WaitlistEntry> nextEntries = waitlistRepository.findNextForUpdate(
                bookId,
                WaitlistStatus.WAITING,
                PageRequest.of(0, 1));
        if (!nextEntries.isEmpty()) {
            WaitlistEntry next = nextEntries.getFirst();
            next.notifyUser(now, now.plus(reservationHours, ChronoUnit.HOURS));
        }
    }
}
