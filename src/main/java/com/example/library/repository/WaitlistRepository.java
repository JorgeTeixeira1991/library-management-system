package com.example.library.repository;

import com.example.library.domain.WaitlistEntry;
import com.example.library.domain.WaitlistStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByBookIdAndUserIdAndStatusIn(
            Long bookId,
            Long userId,
            Collection<WaitlistStatus> statuses);

    Optional<WaitlistEntry> findByIdAndUserUsername(Long id, String username);

    List<WaitlistEntry> findByUserUsernameOrderByRequestedAtDesc(String username);

    List<WaitlistEntry> findByBookIdAndStatusOrderByRequestedAtAsc(Long bookId, WaitlistStatus status);

    Optional<WaitlistEntry> findFirstByBookIdAndStatusOrderByRequestedAtAsc(
            Long bookId,
            WaitlistStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from WaitlistEntry w
            where w.book.id = :bookId and w.status = :status
            order by w.requestedAt asc
            """)
    List<WaitlistEntry> findNextForUpdate(
            @Param("bookId") Long bookId,
            @Param("status") WaitlistStatus status,
            Pageable pageable);
}
