package com.example.library.repository;

import com.example.library.domain.LateFee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LateFeeRepository extends JpaRepository<LateFee, Long> {
    Optional<LateFee> findByLoanId(Long loanId);
    List<LateFee> findByLoanBorrowerUsernameOrderByCreatedAtDesc(String username);
}
