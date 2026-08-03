package com.example.library.repository;

import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l join fetch l.book join fetch l.borrower where l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);

    List<Loan> findByBorrowerUsernameOrderByBorrowedAtDesc(String username);

    List<Loan> findByBorrowerIdOrderByBorrowedAtDesc(Long borrowerId);

    List<Loan> findByBookIdOrderByBorrowedAtDesc(Long bookId);

    boolean existsByBookIdAndBorrowerIdAndStatus(Long bookId, Long borrowerId, LoanStatus status);

    @Query("select l.book.id, count(l) from Loan l group by l.book.id")
    List<Object[]> countLoansByBook();
}
