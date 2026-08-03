package com.example.library.service;

import com.example.library.dto.LateFeeDtos.LateFeeResponse;
import com.example.library.repository.LateFeeRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LateFeeService {

    private final LateFeeRepository repository;
    private final MappingService mappingService;

    public LateFeeService(LateFeeRepository repository, MappingService mappingService) {
        this.repository = repository;
        this.mappingService = mappingService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CLIENT') and #username == authentication.name")
    public List<LateFeeResponse> myFees(String username) {
        return repository.findByLoanBorrowerUsernameOrderByCreatedAtDesc(username).stream()
                .map(mappingService::toLateFeeResponse)
                .toList();
    }
}
