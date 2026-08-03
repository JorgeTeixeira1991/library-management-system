package com.example.library.service;

import com.example.library.domain.Book;
import com.example.library.domain.MetadataEnrichmentJob;
import com.example.library.dto.EnrichmentDtos.EnrichmentJobResponse;
import com.example.library.exception.BusinessRuleException;
import com.example.library.repository.BookRepository;
import com.example.library.repository.MetadataEnrichmentJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Service
public class MetadataEnrichmentService {

    private final BookRepository bookRepository;
    private final MetadataEnrichmentJobRepository jobRepository;
    private final MappingService mappingService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean googleBooksEnabled;
    private final String googleBooksBaseUrl;

    public MetadataEnrichmentService(
            BookRepository bookRepository,
            MetadataEnrichmentJobRepository jobRepository,
            MappingService mappingService,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.metadata.google-books-enabled:false}") boolean googleBooksEnabled,
            @Value("${app.metadata.google-books-base-url:https://www.googleapis.com/books/v1}") String googleBooksBaseUrl) {
        this.bookRepository = bookRepository;
        this.jobRepository = jobRepository;
        this.mappingService = mappingService;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.googleBooksEnabled = googleBooksEnabled;
        this.googleBooksBaseUrl = googleBooksBaseUrl;
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public EnrichmentJobResponse enrich(Long bookId) {
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessRuleException("Book not found: " + bookId));
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            throw new BusinessRuleException("Book must have an ISBN before metadata enrichment");
        }

        MetadataEnrichmentJob job = new MetadataEnrichmentJob();
        job.setBook(book);
        job.setSource(googleBooksEnabled ? "GOOGLE_BOOKS" : "OFFLINE_DEMO_PROVIDER");
        jobRepository.save(job);

        String rawPayload = null;
        try {
            EnrichedMetadata metadata;
            if (googleBooksEnabled) {
                rawPayload = fetchGoogleBooks(book.getIsbn());
                metadata = parseGoogleBooks(rawPayload);
            } else {
                metadata = offlineMetadata(book);
                rawPayload = objectMapper.writeValueAsString(Map.of(
                        "provider", "offline-demo",
                        "isbn", book.getIsbn(),
                        "title", metadata.title(),
                        "authors", metadata.authors(),
                        "category", Objects.toString(metadata.category(), ""),
                        "description", Objects.toString(metadata.description(), "")));
            }

            apply(book, metadata);
            job.succeed(rawPayload, Instant.now());
        } catch (Exception exception) {
            job.fail(exception.getMessage(), rawPayload, Instant.now());
        }
        return mappingService.toEnrichmentResponse(job);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OWNER')")
    public List<EnrichmentJobResponse> history(Long bookId) {
        return jobRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .map(mappingService::toEnrichmentResponse)
                .toList();
    }

    private String fetchGoogleBooks(String isbn) {
        String uri = UriComponentsBuilder.fromUriString(googleBooksBaseUrl + "/volumes")
                .queryParam("q", "isbn:" + isbn)
                .queryParam("maxResults", 1)
                .build()
                .toUriString();
        return restClient.get().uri(uri).retrieve().body(String.class);
    }

    private EnrichedMetadata parseGoogleBooks(String rawPayload) throws Exception {
        GoogleBooksResponse response = objectMapper.readValue(rawPayload, GoogleBooksResponse.class);
        if (response.items() == null || response.items().isEmpty()) {
            throw new BusinessRuleException("Google Books returned no volume for this ISBN");
        }
        VolumeInfo volume = response.items().getFirst().volumeInfo();
        String authors = volume.authors() == null ? null : String.join(", ", volume.authors());
        String category = volume.categories() == null || volume.categories().isEmpty()
                ? null
                : volume.categories().getFirst();
        return new EnrichedMetadata(volume.title(), authors, category, volume.description());
    }

    private EnrichedMetadata offlineMetadata(Book book) {
        return new EnrichedMetadata(
                book.getTitle(),
                book.getAuthors(),
                book.getCategory() == null ? "Uncategorized" : book.getCategory(),
                book.getDescription() == null
                        ? "Offline demo metadata for ISBN " + book.getIsbn() + "."
                        : book.getDescription());
    }

    private void apply(Book book, EnrichedMetadata metadata) {
        if (metadata.title() != null && !metadata.title().isBlank()) {
            book.setTitle(metadata.title());
        }
        if (metadata.authors() != null && !metadata.authors().isBlank()) {
            book.setAuthors(metadata.authors());
        }
        if (metadata.category() != null && !metadata.category().isBlank()) {
            book.setCategory(metadata.category());
        }
        if (metadata.description() != null && !metadata.description().isBlank()) {
            book.setDescription(metadata.description());
        }
    }

    private record EnrichedMetadata(String title, String authors, String category, String description) {
    }

    private record GoogleBooksResponse(List<GoogleBookItem> items) {
    }

    private record GoogleBookItem(VolumeInfo volumeInfo) {
    }

    private record VolumeInfo(
            String title,
            List<String> authors,
            String description,
            List<String> categories) {
    }
}
