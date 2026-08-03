package com.example.library.event;

import com.example.library.service.WaitlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookReturnedListener {

    private static final Logger log = LoggerFactory.getLogger(BookReturnedListener.class);

    private final WaitlistService waitlistService;

    public BookReturnedListener(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookReturned(BookReturnedEvent event) {
        log.info(
                "Book returned: loanId={}, bookId={}, borrowerId={}, returnedLate={}",
                event.loanId(),
                event.bookId(),
                event.borrowerId(),
                event.returnedLate());
        waitlistService.promoteNext(event.bookId());
    }
}
