package com.example.library.repository;

import com.example.library.domain.MetadataEnrichmentJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataEnrichmentJobRepository extends JpaRepository<MetadataEnrichmentJob, Long> {
    List<MetadataEnrichmentJob> findByBookIdOrderByCreatedAtDesc(Long bookId);
}
